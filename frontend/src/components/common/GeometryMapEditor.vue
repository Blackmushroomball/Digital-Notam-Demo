<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import "@geoman-io/leaflet-geoman-free";
import "@geoman-io/leaflet-geoman-free/dist/leaflet-geoman.css";

const props = defineProps({
  modelValue: { type: String, default: "" },
  airports: { type: Array, default: () => [] },
  airspaces: { type: Array, default: () => [] },
});
const emit = defineEmits(["update:modelValue", "error"]);

const container = ref(null);
const geometryType = ref("");
const editorKind = ref("");
const radiusValue = ref("");
const radiusUom = ref("NM");
const corridorWidth = ref("10");
const corridorWidthUom = ref("NM");
const sectorInnerRadius = ref("0");
const sectorOuterRadius = ref("10");
const sectorRadiusUom = ref("NM");
const sectorStartAngle = ref("0");
const sectorEndAngle = ref("90");
const cursorText = ref("经度 — / 纬度 —");
const onlineBaseMap = ref(true);
const showFirs = ref(true);
const showAirports = ref(true);
const showAirspaces = ref(true);
const snapEnabled = ref(true);
const historyPast = ref([]);
const historyFuture = ref([]);
const nodeRows = ref([]);
const selectedNode = ref(-1);

let map;
let activeLayer;
let controlLayer;
let airportLayer;
let firLayer;
let airspaceLayer;
let tileLayer;
let selectionMarker;
let currentGmlId = "";
let pendingDrawType = "";
let rendering = false;
let lastValue = props.modelValue || "";

const metresPerUnit = { M: 1, KM: 1000, NM: 1852, FT: 0.3048 };
const toMetres = (value, uom) => Number(value) * (metresPerUnit[uom] || 1);
const fromMetres = (value, uom) => value / (metresPerUnit[uom] || 1);
const newId = () => `atsa-new-${crypto.randomUUID()}`;
const position = (latlng) => ({ x: Number(latlng.lng.toFixed(9)), y: Number(latlng.lat.toFixed(9)) });
const latlng = (point) => L.latLng(Number(point.y), Number(point.x));
const samePosition = (a, b) => a.x === b.x && a.y === b.y;
const canUndo = computed(() => historyPast.value.length > 0);
const canRedo = computed(() => historyFuture.value.length > 0);

function geometryDocument(geometry) {
  return JSON.stringify({ schemaVersion: "1.0", geometry });
}

function remember(stack, value) {
  stack.push(value);
  if (stack.length > 30) stack.shift();
}

function commit(value) {
  if (rendering || value === lastValue) return;
  remember(historyPast.value, lastValue);
  historyFuture.value = [];
  lastValue = value;
  emit("update:modelValue", value);
  emit("error", "");
}

function layerPositions(layer) {
  const raw = layer.getLatLngs();
  const values = layer instanceof L.Polygon ? raw[0] : raw;
  return values.map(position);
}

function controlPositions() {
  return controlLayer ? controlLayer.getLatLngs().map(position) : [];
}

function refreshNodes() {
  let values = [];
  if (editorKind.value === "CIRCLE" && activeLayer) values = [position(activeLayer.getLatLng())];
  else if (["ARC_BY_EDGE", "CIRCLE_SECTOR"].includes(editorKind.value)) values = controlPositions();
  else if (activeLayer) values = layerPositions(activeLayer);
  nodeRows.value = values.map((point, index) => ({ index, x: point.x, y: point.y }));
  if (selectedNode.value >= values.length) selectedNode.value = -1;
}

function destination(center, bearingDegrees, metres) {
  const distance = metres / 6371008.8;
  const bearing = bearingDegrees * Math.PI / 180;
  const latitude = center.lat * Math.PI / 180;
  const longitude = center.lng * Math.PI / 180;
  const targetLatitude = Math.asin(Math.sin(latitude) * Math.cos(distance) + Math.cos(latitude) * Math.sin(distance) * Math.cos(bearing));
  const targetLongitude = longitude + Math.atan2(Math.sin(bearing) * Math.sin(distance) * Math.cos(latitude), Math.cos(distance) - Math.sin(latitude) * Math.sin(targetLatitude));
  return L.latLng(targetLatitude * 180 / Math.PI, ((targetLongitude * 180 / Math.PI + 540) % 360) - 180);
}

function bearing(center, target) {
  const a = center.lat * Math.PI / 180;
  const b = target.lat * Math.PI / 180;
  const delta = (target.lng - center.lng) * Math.PI / 180;
  return (Math.atan2(Math.sin(delta) * Math.cos(b), Math.cos(a) * Math.sin(b) - Math.sin(a) * Math.cos(b) * Math.cos(delta)) * 180 / Math.PI + 360) % 360;
}

function sampleSector(center, innerMetres, outerMetres, start, end) {
  while (end <= start) end += 360;
  const count = Math.max(12, Math.ceil((end - start) / 4));
  const outer = Array.from({ length: count + 1 }, (_, index) => destination(center, start + (end - start) * index / count, outerMetres));
  if (innerMetres <= 0) return [...outer, center];
  const inner = Array.from({ length: count + 1 }, (_, index) => destination(center, end - (end - start) * index / count, innerMetres));
  return [...outer, ...inner];
}

function sampleArcByEdge(points) {
  if (points.length < 3) return points.map(latlng);
  const [start, through, end] = points;
  const latitude = (start.y + through.y + end.y) / 3;
  const longitude = (start.x + through.x + end.x) / 3;
  const scaleX = 111320 * Math.cos(latitude * Math.PI / 180);
  const scaleY = 111320;
  const xy = (point) => [(point.x - longitude) * scaleX, (point.y - latitude) * scaleY];
  const [a, b, c] = [xy(start), xy(through), xy(end)];
  const divisor = 2 * (a[0] * (b[1] - c[1]) + b[0] * (c[1] - a[1]) + c[0] * (a[1] - b[1]));
  if (Math.abs(divisor) < 1e-6) return points.map(latlng);
  const aa = a[0] ** 2 + a[1] ** 2, bb = b[0] ** 2 + b[1] ** 2, cc = c[0] ** 2 + c[1] ** 2;
  const cx = (aa * (b[1] - c[1]) + bb * (c[1] - a[1]) + cc * (a[1] - b[1])) / divisor;
  const cy = (aa * (c[0] - b[0]) + bb * (a[0] - c[0]) + cc * (b[0] - a[0])) / divisor;
  const angle = (point) => Math.atan2(point[1] - cy, point[0] - cx);
  const first = angle(a), middle = angle(b), last = angle(c);
  const positive = (value) => (value % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
  const counterClockwise = positive(last - first);
  const sweep = positive(middle - first) <= counterClockwise + 1e-9 ? counterClockwise : counterClockwise - Math.PI * 2;
  const count = Math.max(12, Math.ceil(Math.abs(sweep) / (Math.PI / 90)));
  const radius = Math.hypot(a[0] - cx, a[1] - cy);
  return Array.from({ length: count + 1 }, (_, index) => {
    const current = first + sweep * index / count;
    return L.latLng(latitude + (cy + radius * Math.sin(current)) / scaleY, longitude + (cx + radius * Math.cos(current)) / scaleX);
  });
}

function redrawSpecialLayer() {
  if (editorKind.value === "ARC_BY_EDGE") {
    const controls = controlPositions();
    activeLayer?.setLatLngs(sampleArcByEdge(controls));
  } else if (editorKind.value === "CIRCLE_SECTOR") {
    const controls = controlLayer?.getLatLngs() || [];
    if (controls.length < 3) return;
    const center = controls[0];
    activeLayer?.setLatLngs(sampleSector(center, toMetres(sectorInnerRadius.value, sectorRadiusUom.value), toMetres(sectorOuterRadius.value, sectorRadiusUom.value), Number(sectorStartAngle.value), Number(sectorEndAngle.value)));
  }
}

function syncLayer() {
  if ((!activeLayer && !controlLayer) || !geometryType.value) return;
  const base = { type: geometryType.value, gmlId: currentGmlId || newId(), crs: "EPSG:4326" };
  currentGmlId = base.gmlId;
  if (editorKind.value === "CIRCLE") {
    const center = activeLayer.getLatLng();
    const uom = radiusUom.value || "M";
    radiusValue.value = Number(fromMetres(activeLayer.getRadius(), uom).toFixed(6));
    Object.assign(base, { center: position(center), radius: { value: Number(radiusValue.value), uom } });
  } else if (editorKind.value === "ARC_BY_EDGE") {
    const controls = controlPositions();
    if (controls.length !== 3) return;
    base.segments = [
      { type: "ARC_BY_EDGE", start: controls[0], through: controls[1], end: controls[2] },
      { type: "GEODESIC", positions: [controls[2], controls[0]] },
    ];
  } else if (editorKind.value === "CIRCLE_SECTOR") {
    const controls = controlLayer.getLatLngs();
    if (controls.length !== 3) return;
    const center = controls[0];
    sectorOuterRadius.value = Number(fromMetres((map.distance(center, controls[1]) + map.distance(center, controls[2])) / 2, sectorRadiusUom.value).toFixed(6));
    sectorStartAngle.value = Number(bearing(center, controls[1]).toFixed(3));
    sectorEndAngle.value = Number(bearing(center, controls[2]).toFixed(3));
    Object.assign(base, {
      center: position(center),
      innerRadius: Number(sectorInnerRadius.value) > 0 ? { value: Number(sectorInnerRadius.value), uom: sectorRadiusUom.value } : null,
      outerRadius: { value: Number(sectorOuterRadius.value), uom: sectorRadiusUom.value },
      startAngle: Number(sectorStartAngle.value),
      endAngle: Number(sectorEndAngle.value),
      angleReference: "TRUE_NORTH",
    });
  } else if (geometryType.value === "POLYGON") {
    const positions = layerPositions(activeLayer);
    if (positions.length && !samePosition(positions[0], positions.at(-1))) positions.push({ ...positions[0] });
    base.segments = [{ type: "GEODESIC", positions }];
  } else {
    Object.assign(base, {
      centreline: { gmlId: `${base.gmlId}-centreline`, crs: "EPSG:4326", segments: [{ type: "GEODESIC", positions: layerPositions(activeLayer) }] },
      width: { value: Number(corridorWidth.value), uom: corridorWidthUom.value },
    });
  }
  redrawSpecialLayer();
  refreshNodes();
  commit(geometryDocument(base));
}

function editOptions() {
  return { allowSelfIntersection: false, draggable: true, snappable: snapEnabled.value, snapDistance: 25 };
}

function attachEditableLayer(layer) {
  layer.on("pm:edit pm:dragend pm:markerdragend pm:vertexadded pm:vertexremoved", syncLayer);
  layer.pm.enable(editOptions());
}

function attachControlLayer(layer) {
  layer.on("pm:edit pm:dragend pm:markerdragend", syncLayer);
  layer.pm.enable(editOptions());
}

function removeActiveLayer() {
  for (const layer of [activeLayer, controlLayer, selectionMarker]) if (layer && map?.hasLayer(layer)) map.removeLayer(layer);
  activeLayer = undefined;
  controlLayer = undefined;
  selectionMarker = undefined;
  geometryType.value = "";
  editorKind.value = "";
  nodeRows.value = [];
  selectedNode.value = -1;
}

function fitActiveLayer() {
  const layer = activeLayer || controlLayer;
  const bounds = layer?.getBounds?.();
  if (bounds?.isValid()) map.fitBounds(bounds.pad(0.35), { maxZoom: 13 });
}

function createArcArea(controls) {
  geometryType.value = "POLYGON";
  editorKind.value = "ARC_BY_EDGE";
  activeLayer = L.polygon(sampleArcByEdge(controls), { className: "event-geometry", pmIgnore: true }).addTo(map);
  controlLayer = L.polyline(controls.map(latlng), { className: "geometry-controls", pmIgnore: false }).addTo(map);
  attachControlLayer(controlLayer);
}

function createSector(center, startPoint, endPoint) {
  geometryType.value = "CIRCLE_SECTOR";
  editorKind.value = "CIRCLE_SECTOR";
  const outerMetres = (map.distance(center, startPoint) + map.distance(center, endPoint)) / 2;
  sectorOuterRadius.value = Number(fromMetres(outerMetres, sectorRadiusUom.value).toFixed(6));
  sectorStartAngle.value = Number(bearing(center, startPoint).toFixed(3));
  sectorEndAngle.value = Number(bearing(center, endPoint).toFixed(3));
  activeLayer = L.polygon(sampleSector(center, toMetres(sectorInnerRadius.value, sectorRadiusUom.value), outerMetres, Number(sectorStartAngle.value), Number(sectorEndAngle.value)), { className: "event-geometry", pmIgnore: true }).addTo(map);
  controlLayer = L.polyline([center, startPoint, endPoint], { className: "geometry-controls", pmIgnore: false }).addTo(map);
  attachControlLayer(controlLayer);
}

function renderJson(value, fit = false) {
  if (!map) return;
  if (!value) { removeActiveLayer(); return; }
  try {
    const document = JSON.parse(value);
    const geometry = document.geometry;
    if (!geometry || !["CIRCLE", "CIRCLE_SECTOR", "POLYGON", "CORRIDOR"].includes(geometry.type)) throw new Error("图形模式仅支持圆、扇区、多边形和走廊");
    rendering = true;
    removeActiveLayer();
    currentGmlId = geometry.gmlId || newId();
    geometryType.value = geometry.type;
    if (geometry.type === "CIRCLE") {
      editorKind.value = "CIRCLE";
      radiusValue.value = geometry.radius?.value ?? "";
      radiusUom.value = geometry.radius?.uom || "M";
      activeLayer = L.circle([geometry.center.y, geometry.center.x], { radius: toMetres(radiusValue.value, radiusUom.value), className: "event-geometry", pmIgnore: false }).addTo(map);
      attachEditableLayer(activeLayer);
    } else if (geometry.type === "CIRCLE_SECTOR") {
      sectorInnerRadius.value = geometry.innerRadius?.value ?? "0";
      sectorOuterRadius.value = geometry.outerRadius?.value ?? "10";
      sectorRadiusUom.value = geometry.outerRadius?.uom || "NM";
      sectorStartAngle.value = geometry.startAngle ?? "0";
      sectorEndAngle.value = geometry.endAngle ?? "90";
      const center = latlng(geometry.center);
      const metres = toMetres(sectorOuterRadius.value, sectorRadiusUom.value);
      createSector(center, destination(center, Number(sectorStartAngle.value), metres), destination(center, Number(sectorEndAngle.value), metres));
    } else if (geometry.type === "POLYGON" && geometry.segments?.[0]?.type === "ARC_BY_EDGE") {
      const arc = geometry.segments[0];
      createArcArea([arc.start, arc.through, arc.end]);
    } else {
      const source = geometry.type === "POLYGON" ? geometry.segments : geometry.centreline?.segments;
      if (source?.length !== 1 || source[0].type !== "GEODESIC") throw new Error("该混合片段组合只能在高级 JSON 模式中编辑");
      const points = (source[0].positions || []).map(latlng);
      if (geometry.type === "POLYGON" && points.length > 1 && points[0].equals(points.at(-1))) points.pop();
      editorKind.value = geometry.type;
      if (geometry.type === "POLYGON") activeLayer = L.polygon(points, { className: "event-geometry", pmIgnore: false }).addTo(map);
      else {
        corridorWidth.value = geometry.width?.value ?? "10";
        corridorWidthUom.value = geometry.width?.uom || "NM";
        activeLayer = L.polyline(points, { weight: 5, className: "event-geometry", pmIgnore: false }).addTo(map);
      }
      attachEditableLayer(activeLayer);
    }
    refreshNodes();
    if (fit) fitActiveLayer();
    emit("error", "");
  } catch (error) {
    emit("error", error.message);
  } finally {
    rendering = false;
  }
}

function startDraw(type) {
  map.pm.disableDraw();
  pendingDrawType = type;
  const shape = type === "CIRCLE" ? "Circle" : type === "POLYGON" ? "Polygon" : "Line";
  map.pm.enableDraw(shape, { snappable: snapEnabled.value, snapDistance: 25, allowSelfIntersection: false, continueDrawing: false, finishOn: type === "CIRCLE" ? undefined : "dblclick" });
}

function clearGeometry() {
  if (lastValue === "") return;
  remember(historyPast.value, lastValue);
  historyFuture.value = [];
  lastValue = "";
  removeActiveLayer();
  emit("update:modelValue", "");
  emit("error", "请在地图中绘制水平几何");
}

function restoreHistory(source, target) {
  if (!source.length) return;
  remember(target, lastValue);
  const value = source.pop();
  lastValue = value;
  emit("update:modelValue", value);
  nextTick(() => renderJson(value, true));
}

const undo = () => restoreHistory(historyPast.value, historyFuture.value);
const redo = () => restoreHistory(historyFuture.value, historyPast.value);

function updateCircleRadius() {
  if (editorKind.value !== "CIRCLE" || !activeLayer) return;
  activeLayer.setRadius(toMetres(radiusValue.value, radiusUom.value));
  syncLayer();
}

function updateCorridorWidth() {
  if (geometryType.value === "CORRIDOR") syncLayer();
}

function updateSectorProperties() {
  if (editorKind.value !== "CIRCLE_SECTOR" || !controlLayer) return;
  const center = controlLayer.getLatLngs()[0];
  const metres = toMetres(sectorOuterRadius.value, sectorRadiusUom.value);
  controlLayer.setLatLngs([center, destination(center, Number(sectorStartAngle.value), metres), destination(center, Number(sectorEndAngle.value), metres)]);
  redrawSpecialLayer();
  syncLayer();
}

function updateSnap() {
  for (const layer of [activeLayer, controlLayer]) if (layer?.pm?.enabled()) { layer.pm.disable(); layer.pm.enable(editOptions()); }
}

function toggleLayer(layer, visible) {
  if (!layer || !map) return;
  if (visible && !map.hasLayer(layer)) layer.addTo(map);
  if (!visible && map.hasLayer(layer)) map.removeLayer(layer);
}

function toggleOnlineBaseMap() {
  onlineBaseMap.value = !onlineBaseMap.value;
  toggleLayer(tileLayer, onlineBaseMap.value);
}

function renderAirports() {
  airportLayer?.remove();
  airportLayer = L.layerGroup(props.airports.filter((airport) => Number.isFinite(Number(airport.latitude)) && Number.isFinite(Number(airport.longitude))).map((airport) => L.circleMarker([Number(airport.latitude), Number(airport.longitude)], { radius: 5, color: "#344054", fillColor: "#fff", fillOpacity: 1, weight: 2, pmIgnore: false }).bindTooltip(`${airport.designator} — ${airport.name}`)));
  toggleLayer(airportLayer, showAirports.value);
}

function renderAirspaces() {
  firLayer?.remove();
  airspaceLayer?.remove();
  const makeLayers = (isFir) => props.airspaces.filter((airspace) => airspace.type?.startsWith("FIR") === isFir && airspace.points?.length >= 3).map((airspace) => L.polygon(airspace.points.map((point) => [Number(point.latitude), Number(point.longitude)]), {
    color: isFir ? "#7a5af8" : "#12b76a", weight: isFir ? 2 : 1.5, dashArray: isFir ? "8 5" : "4 4", fillOpacity: isFir ? 0.035 : 0.025, className: isFir ? "fir-baseline" : "airspace-baseline", pmIgnore: false,
  }).bindTooltip(`${airspace.designator || airspace.name} · ${airspace.type}`));
  firLayer = L.layerGroup(makeLayers(true));
  airspaceLayer = L.layerGroup(makeLayers(false));
  toggleLayer(firLayer, showFirs.value);
  toggleLayer(airspaceLayer, showAirspaces.value);
}

function nodeLatLng(index) {
  const node = nodeRows.value[index];
  return node ? L.latLng(Number(node.y), Number(node.x)) : null;
}

function selectNode(index) {
  const point = nodeLatLng(index);
  if (!point) return;
  selectedNode.value = index;
  selectionMarker?.remove();
  selectionMarker = L.circleMarker(point, { radius: 9, color: "#f79009", weight: 3, fillOpacity: 0, pmIgnore: true }).addTo(map);
  map.panTo(point);
}

function updateNode(index, axis, source) {
  const value = Number(source);
  if (!Number.isFinite(value) || (axis === "x" && Math.abs(value) > 180) || (axis === "y" && Math.abs(value) > 90)) return;
  const current = nodeRows.value[index];
  const changed = L.latLng(axis === "y" ? value : current.y, axis === "x" ? value : current.x);
  if (editorKind.value === "CIRCLE") activeLayer.setLatLng(changed);
  else if (["ARC_BY_EDGE", "CIRCLE_SECTOR"].includes(editorKind.value)) {
    const values = controlLayer.getLatLngs(); values[index] = changed; controlLayer.setLatLngs(values); redrawSpecialLayer();
  } else {
    const values = activeLayer.getLatLngs();
    if (activeLayer instanceof L.Polygon) { values[0][index] = changed; activeLayer.setLatLngs(values[0]); }
    else { values[index] = changed; activeLayer.setLatLngs(values); }
  }
  syncLayer();
  selectNode(index);
}

onMounted(async () => {
  await nextTick();
  map = L.map(container.value, { zoomControl: true }).setView([53.62, -27.4], 7);
  L.control.scale({ imperial: false }).addTo(map);
  tileLayer = L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", { maxZoom: 19, attribution: "© OpenStreetMap contributors" }).addTo(map);
  map.on("mousemove", ({ latlng: point }) => { cursorText.value = `经度 ${point.lng.toFixed(6)} / 纬度 ${point.lat.toFixed(6)}`; });
  map.on("click", ({ latlng: clicked }) => {
    let best = -1, distance = Infinity;
    nodeRows.value.forEach((_, index) => { const pixels = map.latLngToContainerPoint(nodeLatLng(index)).distanceTo(map.latLngToContainerPoint(clicked)); if (pixels < distance) { distance = pixels; best = index; } });
    if (distance <= 22) selectNode(best);
  });
  map.on("pm:create", ({ layer, shape }) => {
    const requested = pendingDrawType;
    pendingDrawType = "";
    if (["ARC_BY_EDGE", "CIRCLE_SECTOR"].includes(requested)) {
      const controls = layer.getLatLngs();
      map.removeLayer(layer);
      if (controls.length !== 3) { emit("error", requested === "ARC_BY_EDGE" ? "弧边区域需要依次绘制起点、弧上点和终点" : "扇区需要依次绘制圆心、起始方向点和终止方向点"); return; }
      removeActiveLayer();
      currentGmlId = newId();
      if (requested === "ARC_BY_EDGE") createArcArea(controls.map(position)); else createSector(controls[0], controls[1], controls[2]);
      syncLayer();
      fitActiveLayer();
      return;
    }
    removeActiveLayer();
    activeLayer = layer;
    geometryType.value = requested || (shape === "Circle" ? "CIRCLE" : shape === "Polygon" ? "POLYGON" : "CORRIDOR");
    editorKind.value = geometryType.value;
    currentGmlId = newId();
    if (geometryType.value === "CIRCLE") { radiusUom.value = "NM"; radiusValue.value = Number(fromMetres(layer.getRadius(), "NM").toFixed(3)); }
    attachEditableLayer(layer);
    syncLayer();
  });
  renderAirports();
  renderAirspaces();
  renderJson(props.modelValue, true);
  setTimeout(() => map?.invalidateSize(), 0);
});

watch(() => props.modelValue, (value) => {
  const normalized = value || "";
  if (normalized === lastValue) return;
  lastValue = normalized;
  renderJson(normalized);
});
watch(() => props.airports, renderAirports, { deep: true });
watch(() => props.airspaces, renderAirspaces, { deep: true });

onBeforeUnmount(() => { map?.remove(); map = undefined; });
</script>

<template>
  <div class="geometry-map-editor">
    <div class="map-drawing-toolbar">
      <button type="button" @click="startDraw('CIRCLE')">绘制圆形</button>
      <button type="button" @click="startDraw('POLYGON')">绘制多边形</button>
      <button type="button" @click="startDraw('CORRIDOR')">绘制走廊中心线</button>
      <button type="button" @click="startDraw('ARC_BY_EDGE')">绘制弧边区域</button>
      <button type="button" @click="startDraw('CIRCLE_SECTOR')">绘制扇区</button>
      <button type="button" :disabled="!canUndo" @click="undo">撤销</button>
      <button type="button" :disabled="!canRedo" @click="redo">恢复</button>
      <button type="button" :disabled="!geometryType" @click="fitActiveLayer">定位图形</button>
      <button type="button" class="map-clear" :disabled="!geometryType" @click="clearGeometry">清空</button>
    </div>
    <div class="map-layer-toolbar">
      <label><input v-model="onlineBaseMap" type="checkbox" @change="toggleLayer(tileLayer, onlineBaseMap)" />真实地图</label>
      <label><input v-model="showFirs" type="checkbox" @change="toggleLayer(firLayer, showFirs)" />FIR</label>
      <label><input v-model="showAirports" type="checkbox" @change="toggleLayer(airportLayer, showAirports)" />机场</label>
      <label><input v-model="showAirspaces" type="checkbox" @change="toggleLayer(airspaceLayer, showAirspaces)" />基线空域</label>
      <label><input v-model="snapEnabled" type="checkbox" @change="updateSnap" />坐标吸附</label>
    </div>
    <div ref="container" class="geometry-map" aria-label="水平几何绘制地图"></div>
    <div class="map-status-row">
      <span>{{ cursorText }}</span>
      <span>当前类型：{{ editorKind || geometryType || "尚未绘制" }}</span>
      <span>双击结束折线绘制；节点可吸附到机场、FIR 和基线空域边界</span>
    </div>
    <div v-if="editorKind === 'CIRCLE'" class="map-property-row">
      <label>半径<input v-model="radiusValue" type="number" min="0" step="any" @input="updateCircleRadius" /></label>
      <label>单位<select v-model="radiusUom" @change="updateCircleRadius"><option>NM</option><option>KM</option><option>M</option><option>FT</option></select></label>
    </div>
    <div v-else-if="geometryType === 'CORRIDOR'" class="map-property-row">
      <label>总宽度<input v-model="corridorWidth" type="number" min="0" step="any" @input="updateCorridorWidth" /></label>
      <label>单位<select v-model="corridorWidthUom" @change="updateCorridorWidth"><option>NM</option><option>KM</option><option>M</option><option>FT</option></select></label>
      <small>地图显示中心线；发布边界由后端按 WGS-84 精确计算。</small>
    </div>
    <div v-else-if="editorKind === 'CIRCLE_SECTOR'" class="map-property-row sector-properties">
      <label>内半径<input v-model="sectorInnerRadius" type="number" min="0" step="any" @input="updateSectorProperties" /></label>
      <label>外半径<input v-model="sectorOuterRadius" type="number" min="0" step="any" @input="updateSectorProperties" /></label>
      <label>单位<select v-model="sectorRadiusUom" @change="updateSectorProperties"><option>NM</option><option>KM</option><option>M</option><option>FT</option></select></label>
      <label>起始真方位<input v-model="sectorStartAngle" type="number" step="any" @input="updateSectorProperties" /></label>
      <label>终止真方位<input v-model="sectorEndAngle" type="number" step="any" @input="updateSectorProperties" /></label>
    </div>
    <section v-if="nodeRows.length" class="geometry-node-panel">
      <div><b>节点表格</b><small>点击行可在地图定位；修改坐标会同步更新图形。地图中点击节点也会选中对应行。</small></div>
      <div class="geometry-node-table-wrap">
        <table class="geometry-node-table">
          <thead><tr><th>节点</th><th>经度 x</th><th>纬度 y</th><th></th></tr></thead>
          <tbody>
            <tr v-for="node in nodeRows" :key="node.index" :class="{ selected: selectedNode === node.index }" @click="selectNode(node.index)">
              <td>{{ node.index + 1 }}</td>
              <td><input :value="node.x" type="number" step="any" @change="updateNode(node.index, 'x', $event.target.value)" /></td>
              <td><input :value="node.y" type="number" step="any" @change="updateNode(node.index, 'y', $event.target.value)" /></td>
              <td><button type="button" @click.stop="selectNode(node.index)">定位</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
