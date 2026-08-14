<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import "@geoman-io/leaflet-geoman-free";
import "@geoman-io/leaflet-geoman-free/dist/leaflet-geoman.css";

const props = defineProps({
  modelValue: { type: String, default: "" },
  airports: { type: Array, default: () => [] },
});
const emit = defineEmits(["update:modelValue", "error"]);

const container = ref(null);
const geometryType = ref("");
const radiusValue = ref("");
const radiusUom = ref("NM");
const corridorWidth = ref("10");
const corridorWidthUom = ref("NM");
const cursorText = ref("经度 — / 纬度 —");
const onlineBaseMap = ref(false);
const history = ref([]);
let map;
let activeLayer;
let airportLayer;
let tileLayer;
let currentGmlId = "";
let rendering = false;

const metresPerUnit = { M: 1, KM: 1000, NM: 1852, FT: 0.3048 };
const toMetres = (value, uom) => Number(value) * (metresPerUnit[uom] || 1);
const fromMetres = (value, uom) => value / (metresPerUnit[uom] || 1);
const newId = () => `atsa-new-${crypto.randomUUID()}`;
const position = (latlng) => ({ x: latlng.lng, y: latlng.lat });
const samePosition = (a, b) => a.x === b.x && a.y === b.y;

function geometryDocument(geometry) {
  return JSON.stringify({ schemaVersion: "1.0", geometry });
}

function commit(value) {
  if (rendering || value === props.modelValue) return;
  if (props.modelValue && history.value.at(-1) !== props.modelValue)
    history.value.push(props.modelValue);
  if (history.value.length > 30) history.value.shift();
  emit("update:modelValue", value);
  emit("error", "");
}

function layerPositions(layer) {
  const raw = layer.getLatLngs();
  const latlngs = layer instanceof L.Polygon ? raw[0] : raw;
  return latlngs.map(position);
}

function syncLayer() {
  if (!activeLayer || !geometryType.value) return;
  const base = {
    type: geometryType.value,
    gmlId: currentGmlId || newId(),
    crs: "EPSG:4326",
  };
  currentGmlId = base.gmlId;
  if (geometryType.value === "CIRCLE") {
    const center = activeLayer.getLatLng();
    const uom = radiusUom.value || "M";
    radiusValue.value = Number(fromMetres(activeLayer.getRadius(), uom).toFixed(6));
    Object.assign(base, {
      center: position(center),
      radius: { value: Number(radiusValue.value), uom },
    });
  } else if (geometryType.value === "POLYGON") {
    const positions = layerPositions(activeLayer);
    if (positions.length && !samePosition(positions[0], positions.at(-1)))
      positions.push({ ...positions[0] });
    base.segments = [{ type: "GEODESIC", positions }];
  } else {
    Object.assign(base, {
      centreline: {
        gmlId: `${base.gmlId}-centreline`,
        crs: "EPSG:4326",
        segments: [{ type: "GEODESIC", positions: layerPositions(activeLayer) }],
      },
      width: {
        value: Number(corridorWidth.value),
        uom: corridorWidthUom.value,
      },
    });
  }
  commit(geometryDocument(base));
}

function attachLayer(layer) {
  layer.on("pm:edit pm:dragend", syncLayer);
  layer.pm.enable({ allowSelfIntersection: false, draggable: true });
}

function removeActiveLayer() {
  if (activeLayer && map?.hasLayer(activeLayer)) map.removeLayer(activeLayer);
  activeLayer = undefined;
  geometryType.value = "";
}

function fitActiveLayer() {
  if (!activeLayer) return;
  const bounds = activeLayer.getBounds?.();
  if (bounds?.isValid()) map.fitBounds(bounds.pad(0.35), { maxZoom: 12 });
}

function renderJson(value, fit = false) {
  if (!map || !value) return;
  try {
    const document = JSON.parse(value);
    const geometry = document.geometry;
    if (!geometry || !["CIRCLE", "POLYGON", "CORRIDOR"].includes(geometry.type))
      throw new Error("图形模式仅支持 CIRCLE、POLYGON 和 CORRIDOR");
    rendering = true;
    removeActiveLayer();
    currentGmlId = geometry.gmlId || newId();
    geometryType.value = geometry.type;
    if (geometry.type === "CIRCLE") {
      radiusValue.value = geometry.radius?.value ?? "";
      radiusUom.value = geometry.radius?.uom || "M";
      activeLayer = L.circle([geometry.center.y, geometry.center.x], {
        radius: toMetres(radiusValue.value, radiusUom.value),
      }).addTo(map);
    } else {
      const source =
        geometry.type === "POLYGON" ? geometry.segments : geometry.centreline?.segments;
      if (source?.length !== 1 || source[0].type !== "GEODESIC")
        throw new Error("包含弧线或混合片段的 JSON 只能在高级 JSON 模式中编辑");
      const points = (source[0].positions || []).map((p) => [p.y, p.x]);
      if (geometry.type === "POLYGON") activeLayer = L.polygon(points).addTo(map);
      else {
        corridorWidth.value = geometry.width?.value ?? "10";
        corridorWidthUom.value = geometry.width?.uom || "NM";
        activeLayer = L.polyline(points, { weight: 5 }).addTo(map);
      }
    }
    attachLayer(activeLayer);
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
  const shape = type === "CIRCLE" ? "Circle" : type === "POLYGON" ? "Polygon" : "Line";
  map.pm.enableDraw(shape, {
    snappable: false,
    allowSelfIntersection: false,
    continueDrawing: false,
    finishOn: type === "CIRCLE" ? undefined : "dblclick",
  });
}

function clearGeometry() {
  if (props.modelValue) history.value.push(props.modelValue);
  removeActiveLayer();
  currentGmlId = "";
  emit("update:modelValue", "");
  emit("error", "请在地图上绘制水平几何");
}

function undo() {
  const previous = history.value.pop();
  if (!previous) return;
  emit("update:modelValue", previous);
  nextTick(() => renderJson(previous, true));
}

function updateCircleRadius() {
  if (geometryType.value !== "CIRCLE" || !activeLayer) return;
  activeLayer.setRadius(toMetres(radiusValue.value, radiusUom.value));
  syncLayer();
}

function updateCorridorWidth() {
  if (geometryType.value === "CORRIDOR") syncLayer();
}

function toggleOnlineBaseMap() {
  onlineBaseMap.value = !onlineBaseMap.value;
  if (onlineBaseMap.value) {
    tileLayer ||= L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: "© OpenStreetMap contributors",
    });
    tileLayer.addTo(map);
  } else if (tileLayer) map.removeLayer(tileLayer);
  airportLayer?.bringToFront();
  activeLayer?.bringToFront();
}

function renderAirports() {
  if (!map) return;
  airportLayer?.remove();
  airportLayer = L.layerGroup(
    props.airports
      .filter((airport) => Number.isFinite(Number(airport.latitude)) && Number.isFinite(Number(airport.longitude)))
      .map((airport) =>
        L.circleMarker([Number(airport.latitude), Number(airport.longitude)], {
          radius: 4,
          color: "#344054",
          fillColor: "#fff",
          fillOpacity: 1,
          weight: 2,
        }).bindTooltip(`${airport.designator} — ${airport.name}`),
      ),
  ).addTo(map);
}

onMounted(async () => {
  await nextTick();
  map = L.map(container.value, { zoomControl: true }).setView([53.62, -27.4], 7);
  L.control.scale({ imperial: false }).addTo(map);
  map.on("mousemove", ({ latlng }) => {
    cursorText.value = `经度 ${latlng.lng.toFixed(6)} / 纬度 ${latlng.lat.toFixed(6)}`;
  });
  map.on("pm:create", ({ layer, shape }) => {
    removeActiveLayer();
    activeLayer = layer;
    geometryType.value = shape === "Circle" ? "CIRCLE" : shape === "Polygon" ? "POLYGON" : "CORRIDOR";
    currentGmlId = newId();
    if (geometryType.value === "CIRCLE") {
      radiusUom.value = "NM";
      radiusValue.value = Number(fromMetres(layer.getRadius(), "NM").toFixed(3));
    }
    attachLayer(layer);
    syncLayer();
  });
  renderAirports();
  renderJson(props.modelValue, true);
  setTimeout(() => map?.invalidateSize(), 0);
});

watch(() => props.modelValue, (value) => {
  if (value) renderJson(value);
});
watch(() => props.airports, renderAirports, { deep: true });

onBeforeUnmount(() => {
  map?.remove();
  map = undefined;
});
</script>

<template>
  <div class="geometry-map-editor">
    <div class="map-drawing-toolbar">
      <button type="button" @click="startDraw('CIRCLE')">绘制圆形</button>
      <button type="button" @click="startDraw('POLYGON')">绘制多边形</button>
      <button type="button" @click="startDraw('CORRIDOR')">绘制走廊中心线</button>
      <button type="button" :disabled="!history.length" @click="undo">撤销</button>
      <button type="button" :disabled="!geometryType" @click="fitActiveLayer">定位图形</button>
      <button type="button" @click="toggleOnlineBaseMap">
        {{ onlineBaseMap ? "关闭在线底图" : "启用在线底图" }}
      </button>
      <button type="button" class="map-clear" :disabled="!geometryType" @click="clearGeometry">清空</button>
    </div>
    <div ref="container" class="geometry-map" aria-label="水平几何绘制地图"></div>
    <div class="map-status-row">
      <span>{{ cursorText }}</span>
      <span>当前类型：{{ geometryType || "尚未绘制" }}</span>
      <span>机场点来自项目基线；在线底图默认关闭</span>
    </div>
    <div v-if="geometryType === 'CIRCLE'" class="map-property-row">
      <label>半径<input v-model="radiusValue" type="number" min="0" step="any" @input="updateCircleRadius" /></label>
      <label>单位<select v-model="radiusUom" @change="updateCircleRadius"><option>NM</option><option>KM</option><option>M</option><option>FT</option></select></label>
    </div>
    <div v-else-if="geometryType === 'CORRIDOR'" class="map-property-row">
      <label>总宽度<input v-model="corridorWidth" type="number" min="0" step="any" @input="updateCorridorWidth" /></label>
      <label>单位<select v-model="corridorWidthUom" @change="updateCorridorWidth"><option>NM</option><option>KM</option><option>M</option><option>FT</option></select></label>
      <small>地图显示中心线；发布边界由后端按 WGS-84 精确计算。</small>
    </div>
  </div>
</template>
