<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import AppHeader from "./components/layout/AppHeader.vue";
import AppSidebar from "./components/layout/AppSidebar.vue";
import ScheduleEditor from "./components/common/ScheduleEditor.vue";
import RestrictionEditor from "./components/common/RestrictionEditor.vue";
import AtsaActFields from "./components/scenarios/AtsaActFields.vue";
import AtsaNewFields from "./components/scenarios/AtsaNewFields.vue";
import NavUnsFields from "./components/scenarios/NavUnsFields.vue";
import NotamDetail from "./components/notam/NotamDetail.vue";
import NotamList from "./components/notam/NotamList.vue";
import {
  getAirports,
  getAirspaces,
  getNavaids,
  getRunways,
} from "./services/baselineApi.js";
import {
  deleteNotam,
  getAixm,
  getCnotam,
  getNotams,
  importNotamXml,
  publishNotam,
  saveNotam,
} from "./services/notamApi.js";
import {
  analyseAtsaNewAssociations,
  getActivationPreview,
  getNavPreview,
} from "./services/scenarioApi.js";
const localInput = (d) =>
  new Date(d.getTime() - d.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
const initialStart = new Date(Date.now() + 30 * 60000),
  initialEnd = new Date(initialStart.getTime() + 4 * 60 * 60000);
const items = ref([]),
  airports = ref([]),
  runways = ref([]),
  airspaceGroups = ref([]),
  navaids = ref([]),
  checkedIds = ref([]),
  query = ref(""),
  status = ref("ALL"),
  view = ref("list"),
  selected = ref(null),
  xml = ref(""),
  cnotam = ref(""),
  notice = ref(""),
  editingId = ref(null);

// 通告列表分页，从第一页开始展示，每页显示5条
const currentPage = ref(1);
const pageSize = 5;

const form = reactive({
  scenario: "AD.CLS",
  fir: "EAAD",
  numberSeries: "A",
  numberDigits: "",
  title: "DONLON/INTL. OPERATIONAL EVENT",
  airport: "EADD",
  featureType: "AIRPORT_HELIPORT",
  condition: "AD closed.",
  selectedRunways: "",
  selectedTaxiways: "",
  eventDescription: "CLSD",
  reason: "SURFACE MAINTENANCE",
  remarks: "",
  effectiveStart: localInput(initialStart),
  effectiveEnd: localInput(initialEnd),
  latitude: "52.37166667",
  latitudeHemisphere: "N",
  longitude: "31.94944444",
  longitudeHemisphere: "W",
  radiusNm: "5",
  qCode: "QFALC",
  traffic: "IV",
  purpose: "NBO",
  scope: "A",
  lowerRestricted: false,
  upperRestricted: false,
  lowerMeters: "",
  upperMeters: "",
  scheduleMode: "CONTINUOUS",
  scheduleDays: ["MON", "TUE", "WED", "THU", "FRI"],
  scheduleStart: localInput(initialStart).slice(11),
  scheduleEnd: localInput(initialEnd).slice(11),
  scheduleStartDate: localInput(initialStart).slice(0, 10),
  scheduleEndDate: localInput(initialEnd).slice(0, 10),
  qOverrideReason: "",
  qOverrideOperator: "",
  qOverrideAt: "",
  limitationType: "RESERV",
  operation: "ALL",
  operationOther: "",
  includeAircraft: false,
  includeFlight: false,
  includePpr: false,
  flightType: "",
  flightTypeOther: "",
  flightRule: "",
  flightRuleOther: "",
  flightStatus: "",
  flightStatusOther: "",
  flightMilitary: "",
  flightMilitaryOther: "",
  flightOrigin: "",
  flightOriginOther: "",
  flightPurpose: "",
  flightPurposeOther: "",
  aircraftType: "",
  aircraftTypeOther: "",
  aircraftEngine: "",
  aircraftEngineOther: "",
  aircraftWingSpan: "",
  aircraftWingSpanUom: "M",
  aircraftWingSpanInterpretation: "",
  aircraftWeight: "",
  aircraftWeightUom: "T",
  aircraftWeightInterpretation: "",
  pprValue: "",
  pprUnit: "HR",
  pprDetails: "",
  rwyTargetType: "RUNWAY",
  runwayUuid: "",
  runwayDirectionUuid: "",
  runwaySurfaceComposition: "",
  airspaceGroupId: "MAGNETO_TMA",
  selectedAirspaces:
    "0df377fe-dd53-4d60-b6c4-6546ef31d26b,010d8451-d751-4abb-9c71-f48ad024045b",
  activationStatus: "ACTIVE",
  affectedAirports: "",
  additionalFirs: "",
  navaidType: "VOR",
  navaidUuid: "",
  impactMode: "ALL_PRIMARY",
  equipmentUuid: "",
  signalType: "",
  operationalStatus: "UNSERVICEABLE",
  operationalStatusOther: "",
  signalStillEmitted: false,
  atsaNewType: "CLASS",
  atsaNewClass: "A",
  atsaNewDesignator: "",
  atsaNewName: "",
  atsaNewActivationStatus: "ACTIVE",
  atsaNewLocationNote: "",
  atsaNewNote: "",
  controllingUnitNote: "",
  geometryMode: "STRUCTURED",
  geometryType: "CIRCLE",
  geometryJson: "",
  circleX: "-27.3992573509",
  circleY: "53.6197929845",
  circleRadius: "10",
  circleRadiusUom: "NM",
  geometryPoints: "-27.55,53.55\n-27.25,53.55\n-27.25,53.75\n-27.55,53.55",
  corridorWidth: "10",
  corridorWidthUom: "NM",
  lowerValue: "GND",
  lowerUom: "",
  lowerReference: "",
  upperValue: "50",
  upperUom: "FL",
  upperReference: "STD",
  excludedAirspaces: "",
  nearbyAirportThresholdNm: "5",
});
const newRestriction = () => ({
  operation: "ALL",
  operationOther: "",
  includeAircraft: false,
  includeFlight: false,
  includePpr: false,
  flightType: "",
  flightTypeOther: "",
  flightRule: "",
  flightRuleOther: "",
  flightStatus: "",
  flightStatusOther: "",
  flightMilitary: "",
  flightMilitaryOther: "",
  flightOrigin: "",
  flightOriginOther: "",
  flightPurpose: "",
  flightPurposeOther: "",
  aircraftType: "",
  aircraftTypeOther: "",
  aircraftEngine: "",
  aircraftEngineOther: "",
  aircraftWingSpan: "",
  aircraftWingSpanUom: "M",
  aircraftWingSpanInterpretation: "",
  aircraftWeight: "",
  aircraftWeightUom: "T",
  aircraftWeightInterpretation: "",
  pprValue: "",
  pprUnit: "HR",
  pprDetails: "",
});
const restrictions = ref([newRestriction()]);
const newScheduleEntry = (mode) => ({
  startDate: mode === "DATES" ? form.scheduleStartDate : "",
  endDate: mode === "DATES" ? form.scheduleEndDate : "",
  day: mode === "WEEKDAYS" ? "MON" : "ANY",
  dayTil: "",
  weekdayContinuous: false,
  startTime: form.scheduleStart,
  endTime: form.scheduleEnd,
  endOfDay: false,
});
const scheduleEntries = ref([newScheduleEntry("DAILY")]),
  excludedDates = ref([]),
  scheduleNote = ref("");
const activationPreview = ref(null),
  activationPreviewError = ref(""),
  activationPreviewLoading = ref(false),
  activationViewMode = ref("WEEK"),
  activationViewOffset = ref(0),
  activationTab = ref("ALL"),
  activationCursor = ref(0);
const navPreview = ref(null),
  navPreviewError = ref(""),
  navPreviewLoading = ref(false);
const atsaNewAssociations = ref(null),
  atsaNewAssociationError = ref(""),
  atsaNewAssociationLoading = ref(false),
  excludedAirspaceQuery = ref("");
const addRestriction = () => restrictions.value.push(newRestriction());
const removeRestriction = (index) => {
  if (restrictions.value.length > 1) restrictions.value.splice(index, 1);
};
const moveRestriction = (index, offset) => {
  const target = index + offset;
  if (target < 0 || target >= restrictions.value.length) return;
  const [item] = restrictions.value.splice(index, 1);
  restrictions.value.splice(target, 0, item);
};
const navaidTypes = [
  "VOR",
  "DME",
  "NDB",
  "TACAN",
  "MKR",
  "ILS",
  "ILS_DME",
  "MLS",
  "MLS_DME",
  "VORTAC",
  "VOR_DME",
  "NDB_DME",
  "TLS",
  "LOC",
  "LOC_DME",
  "NDB_MKR",
  "DF",
  "SDF",
  "OTHER",
];
const standardNavaidTypes = new Set(navaidTypes.filter((x) => x !== "OTHER"));
const navaidTypeOf = (n) =>
  standardNavaidTypes.has(n?.type) ? n.type : "OTHER";
const filteredNavaids = computed(() =>
  navaids.value.filter((n) => navaidTypeOf(n) === form.navaidType),
);
const scenarios = [
  ["AD.CLS", "机场关闭"],
  ["AD.LIM", "机场受限"],
  ["RWY.CLS", "跑道关闭"],
  ["RWY.LIM", "跑道受限"],
  ["ATSA.ACT", "ATS空域启用/停用"],
  ["ATSA.NEW", "新建临时ATS空域"],
  ["NAV.UNS", "导航设施不可用"],
];
function scenarioChanged() {
  if (form.scenario === "ATSA.ACT") {
    form.featureType = "AIRSPACE";
    form.eventDescription = "";
    form.airport = "";
    form.fir = "EAAD";
    form.qCode = "QATCA";
    form.traffic = "IV";
    form.purpose = "BO";
    form.scope = "E";
    airspaceGroupChanged();
    return;
  }
  if (form.scenario === "ATSA.NEW") {
    form.featureType = "AIRSPACE";
    form.eventDescription = "";
    form.airport = "";
    form.fir = "EAAD";
    form.qCode = "QXXXX";
    form.traffic = "IV";
    form.purpose = "NBO";
    form.scope = "E";
    form.affectedAirports = [];
    form.title = "TEMPORARY ATS AIRSPACE ESTABLISHED";
    atsaNewAssociations.value = null;
    return;
  }
  if (form.scenario === "NAV.UNS") {
    form.featureType = "NAVAID";
    form.eventDescription = "";
    form.airport = "";
    form.fir = "EAAD";
    form.qCode = "QXXXX";
    form.traffic = "IV";
    form.purpose = "BO";
    form.scope = "E";
    navaidChanged();
    return;
  }
  form.featureType = form.scenario.startsWith("AD.")
    ? "AIRPORT_HELIPORT"
    : "RUNWAY";
  form.eventDescription = form.scenario.endsWith(".CLS") ? "CLSD" : "LIMITED";
  form.qCode =
    form.scenario === "AD.CLS"
      ? "QFALC"
      : form.scenario === "AD.LIM"
        ? "QFALT"
        : form.scenario === "RWY.CLS"
          ? "QMRLC"
          : "QMRLT";
  if (["RWY.CLS", "RWY.LIM"].includes(form.scenario)) {
    form.airport = "EADD";
    form.rwyTargetType = "RUNWAY";
    form.qOverrideReason = "";
    form.qOverrideOperator = "";
    form.qOverrideAt = "";
    restrictions.value = [newRestriction()];
    airportChanged();
  }
}
async function loadAirspaces() {
  airspaceGroups.value = await getAirspaces().catch(() => []);
  if (!airspaceGroups.value.some((g) => g.id === form.airspaceGroupId))
    form.airspaceGroupId = airspaceGroups.value[0]?.id || "";
  airspaceGroupChanged();
}
function airspaceGroupChanged() {
  const g = airspaceGroups.value.find((x) => x.id === form.airspaceGroupId);
  form.selectedAirspaces = (g?.members || []).map((x) => x.uuid).join(",");
}
function activationChanged() {
  form.qCode = form.activationStatus === "ACTIVE" ? "QATCA" : "QATCD";
  form.traffic = "IV";
  form.purpose = form.activationStatus === "ACTIVE" ? "BO" : "NBO";
}
async function loadNavaids() {
  navaids.value = await getNavaids().catch(() => []);
  const current = navaids.value.find((x) => x.uuid === form.navaidUuid);
  if (current) form.navaidType = navaidTypeOf(current);
  else navaidTypeChanged();
}
function navaidTypeChanged() {
  if (!filteredNavaids.value.some((x) => x.uuid === form.navaidUuid))
    form.navaidUuid = filteredNavaids.value[0]?.uuid || "";
  navaidChanged();
}
const selectedNavaid = () =>
  navaids.value.find((x) => x.uuid === form.navaidUuid);
function navaidChanged() {
  const n = selectedNavaid();
  if (!n) return;
  form.equipmentUuid =
    n.components.find((x) => x.primary)?.uuid || n.components[0]?.uuid || "";
  form.latitude = String(Math.abs(Number(n.latitude)));
  form.latitudeHemisphere = Number(n.latitude) < 0 ? "S" : "N";
  form.longitude = String(Math.abs(Number(n.longitude)));
  form.longitudeHemisphere = Number(n.longitude) < 0 ? "W" : "E";
  form.radiusNm = "25";
  form.affectedAirports = n.servedAirport ? [n.servedAirport] : [];
  impactModeChanged();
}
function impactModeChanged() {
  const n = selectedNavaid();
  if (!n) return;
  if (form.impactMode === "ALL_PRIMARY") form.equipmentUuid = "";
  else if (!n.components.some((x) => x.uuid === form.equipmentUuid))
    form.equipmentUuid = n.components[0]?.uuid || "";
  if (form.impactMode !== "SIGNAL") form.signalType = "";
  else {
    const tacan = n.components.find((x) => x.type === "TACAN");
    form.equipmentUuid = tacan?.uuid || "";
    form.signalType = form.signalType || "AZIMUTH";
  }
  queueNavPreview();
}
const selectedAirportCodes = () =>
  Array.isArray(form.affectedAirports)
    ? form.affectedAirports
    : (form.affectedAirports || "").split(",").filter(Boolean);
function toggleAffectedAirport(code, checked) {
  const selected = new Set(selectedAirportCodes());
  checked ? selected.add(code) : selected.delete(code);
  form.affectedAirports = [...selected];
}
function toggleSelectedAirspace(uuid, checked) {
  const selected = new Set(form.selectedAirspaces.split(",").filter(Boolean));
  checked ? selected.add(uuid) : selected.delete(uuid);
  form.selectedAirspaces = [...selected].join(",");
}
const baselineAirspaces = computed(() => {
  const values = new Map();
  for (const group of airspaceGroups.value)
    for (const airspace of group.members || [])
      values.set(airspace.uuid, airspace);
  return [...values.values()].sort((a, b) =>
    (a.designator || a.name).localeCompare(b.designator || b.name),
  );
});
const filteredBaselineAirspaces = computed(() => {
  const query = excludedAirspaceQuery.value.trim().toLowerCase();
  return query
    ? baselineAirspaces.value.filter((a) =>
      `${a.designator} ${a.name} ${a.type}`.toLowerCase().includes(query),
    )
    : baselineAirspaces.value;
});
const selectedExcludedAirspaces = () =>
  new Set((form.excludedAirspaces || "").split(",").filter(Boolean));
function toggleExcludedAirspace(uuid, checked) {
  const selected = selectedExcludedAirspaces();
  checked ? selected.add(uuid) : selected.delete(uuid);
  form.excludedAirspaces = [...selected].join(",");
}
function parseCoordinate(source, axis) {
  let value = String(source ?? "")
    .trim()
    .toUpperCase();
  if (!value) throw new Error(`${axis === "x" ? "经度" : "纬度"}不能为空`);
  const hemispheres = value.match(/[NSEW]/g) || [],
    hemisphere = hemispheres[0];
  if (new Set(hemispheres).size > 1) throw new Error("坐标包含冲突的半球标识");
  if (
    hemisphere &&
    !(
      (axis === "x" && "EW".includes(hemisphere)) ||
      (axis === "y" && "NS".includes(hemisphere))
    )
  )
    throw new Error(axis === "x" ? "经度只能使用 E/W" : "纬度只能使用 N/S");
  value = value
    .replace(/[NSEW]/g, "")
    .trim()
    .replace(/[°º′’']/g, " ")
    .replace(/[″”"]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  let negative = value.startsWith("-");
  const unsigned = /^[+-]/.test(value) ? value.slice(1) : value,
    parts = unsigned.split(" ");
  let degrees;
  const integerDigits = parts[0]?.split(".")[0].length,
    compact =
      !!hemisphere &&
      parts.length === 1 &&
      /^\d+(?:\.\d+)?$/.test(parts[0]) &&
      integerDigits === (axis === "y" ? 6 : 7);
  if (compact) {
    const d = axis === "y" ? 2 : 3;
    degrees =
      Number(parts[0].slice(0, d)) +
      Number(parts[0].slice(d, d + 2)) / 60 +
      Number(parts[0].slice(d + 2)) / 3600;
  } else if (parts.length === 1) degrees = Number(parts[0]);
  else if (parts.length === 2 || parts.length === 3) {
    const minutes = Number(parts[1]),
      seconds = parts.length === 3 ? Number(parts[2]) : 0;
    if (minutes < 0 || minutes >= 60 || seconds < 0 || seconds >= 60)
      throw new Error("分和秒必须在 0 到 60 之间");
    degrees = Number(parts[0]) + minutes / 60 + seconds / 3600;
  } else throw new Error("无法识别坐标格式");
  if (hemisphere) {
    const hemisphereNegative = "WS".includes(hemisphere);
    if (negative && !hemisphereNegative) throw new Error("负号与半球标识冲突");
    negative = hemisphereNegative;
  }
  degrees = negative ? -degrees : degrees;
  const limit = axis === "y" ? 90 : 180;
  if (!Number.isFinite(degrees) || Math.abs(degrees) > limit)
    throw new Error(`${axis === "y" ? "纬度" : "经度"}超出范围`);
  return degrees;
}
function structuredPositions() {
  const positions = form.geometryPoints.split(/\r?\n/).map((line, index) => {
    const pair = line.split(",");
    if (pair.length !== 2) throw new Error(`坐标第 ${index + 1} 行必须为 x,y`);
    try {
      return {
        x: parseCoordinate(pair[0], "x"),
        y: parseCoordinate(pair[1], "y"),
      };
    } catch (e) {
      throw new Error(`坐标第 ${index + 1} 行：${e.message}`);
    }
  });
  const minimum = form.geometryType === "POLYGON" ? 3 : 2;
  if (positions.length < minimum)
    throw new Error(`${form.geometryType} 至少需要 ${minimum} 个坐标点`);
  if (
    form.geometryType === "POLYGON" &&
    (positions[0].x !== positions.at(-1).x ||
      positions[0].y !== positions.at(-1).y)
  )
    positions.push({ ...positions[0] });
  return positions;
}
/** Both editor modes produce the same reusable geometry JSON contract. */
function atsaNewGeometryJson() {
  if (form.geometryMode === "JSON") {
    JSON.parse(form.geometryJson);
    return form.geometryJson;
  }
  const base = {
    schemaVersion: "1.0",
    geometry: {
      type: form.geometryType,
      gmlId: `atsa-new-${crypto.randomUUID()}`,
      crs: "EPSG:4326",
    },
  };
  if (form.geometryType === "CIRCLE")
    Object.assign(base.geometry, {
      center: {
        x: parseCoordinate(form.circleX, "x"),
        y: parseCoordinate(form.circleY, "y"),
      },
      radius: { value: Number(form.circleRadius), uom: form.circleRadiusUom },
    });
  else if (form.geometryType === "POLYGON")
    base.geometry.segments = [
      { type: "GEODESIC", positions: structuredPositions() },
    ];
  else
    Object.assign(base.geometry, {
      centreline: {
        gmlId: `centreline-${crypto.randomUUID()}`,
        segments: [{ type: "GEODESIC", positions: structuredPositions() }],
      },
      width: { value: Number(form.corridorWidth), uom: form.corridorWidthUom },
    });
  return JSON.stringify(base);
}
async function analyseAtsaNewGeometry(applyAirports = true) {
  atsaNewAssociationLoading.value = true;
  atsaNewAssociationError.value = "";
  try {
    const geometryJson = atsaNewGeometryJson();
    const payload = {
      geometryJson,
      nearbyAirportThresholdNm: form.nearbyAirportThresholdNm,
      effectiveStart: new Date(form.effectiveStart).toISOString(),
      effectiveEnd: new Date(form.effectiveEnd).toISOString(),
      lowerValue: form.lowerValue,
      lowerUom: form.lowerUom,
    };
    const value = await analyseAtsaNewAssociations(payload);
    atsaNewAssociations.value = value;
    if (value.firs.length) form.fir = value.firs[0].designator;
    if (applyAirports)
      form.affectedAirports = value.airports.map((x) => x.designator);
    form.latitude = String(Math.abs(value.centre.latitude));
    form.latitudeHemisphere = value.centre.latitude < 0 ? "S" : "N";
    form.longitude = String(Math.abs(value.centre.longitude));
    form.longitudeHemisphere = value.centre.longitude < 0 ? "W" : "E";
    form.radiusNm = String(value.radiusNm);
  } catch (e) {
    atsaNewAssociations.value = null;
    atsaNewAssociationError.value = e.message;
  } finally {
    atsaNewAssociationLoading.value = false;
  }
}
async function loadRunways() {
  runways.value = await getRunways(form.airport).catch(() => []);
  if (["RWY.CLS", "RWY.LIM"].includes(form.scenario)) {
    if (!runways.value.some((x) => x.uuid === form.runwayUuid)) {
      form.runwayUuid = runways.value[0]?.uuid || "";
      runwayChanged();
    } else {
      const runway = runways.value.find((x) => x.uuid === form.runwayUuid);
      if (!runway?.surfaceCompositions?.includes(form.runwaySurfaceComposition))
        form.runwaySurfaceComposition = runway?.surfaceCompositions?.[0] || "";
    }
  }
}
function runwayChanged() {
  const r = runways.value.find((x) => x.uuid === form.runwayUuid);
  form.selectedRunways = r?.designator || "";
  form.runwayDirectionUuid = r?.directions?.[0]?.uuid || "";
  form.runwaySurfaceComposition = r?.surfaceCompositions?.[0] || "";
  form.qCode = r?.type === "FATO" ? "QFHLC" : "QMRLC";
  form.traffic = "IV";
  form.purpose = "NBO";
  form.scope = "A";
  form.radiusNm = "5";
}
async function loadAirports() {
  airports.value = await getAirports().catch(() => []);
  airportChanged();
}
function airportChanged() {
  const a = airports.value.find((x) => x.designator === form.airport);
  if (!a) return;
  form.fir = a.fir;
  form.qCode =
    form.scenario === "AD.LIM"
      ? a.type === "HP"
        ? "QFPLT"
        : "QFALT"
      : form.scenario === "RWY.LIM"
        ? "QMRLT"
        : form.scenario === "RWY.CLS"
          ? "QMRLC"
          : a.type === "HP"
            ? "QFPLC"
            : "QFALC";
  form.traffic = "IV";
  form.purpose = a.type === "HP" && form.scenario === "AD.LIM" ? "BO" : "NBO";
  form.scope = "A";
  form.lowerRestricted = false;
  form.upperRestricted = false;
  form.latitude = String(Math.abs(Number(a.latitude)));
  form.latitudeHemisphere = Number(a.latitude) < 0 ? "S" : "N";
  form.longitude = String(Math.abs(Number(a.longitude)));
  form.longitudeHemisphere = Number(a.longitude) < 0 ? "W" : "E";
  form.radiusNm = "5";
  loadAirportFeatures();
}
async function loadAirportFeatures() {
  await loadRunways();
}
const eText = () => {
  const subject = form.scenario.startsWith("AD.")
    ? "AD"
    : `RWY ${form.selectedRunways || "—"}`;
  const schedule =
    form.scheduleMode === "SCHEDULED" && form.scheduleDays.length
      ? ` ${form.scheduleDays.join(" ")} ${form.scheduleStart.replace(":", "")}-${form.scheduleEnd.replace(":", "")} UTC`
      : "";
  return `${subject} ${form.eventDescription}${schedule}${form.reason ? " DUE TO " + form.reason : ""}${form.remarks ? ". " + form.remarks : ""}`.trim();
};
const filtered = computed(() =>
  items.value.filter(
    (n) =>
      (status.value === "ALL" || n.status === status.value) &&
      `${n.title}${n.airport}${n.number}`
        .toLowerCase()
        .includes(query.value.toLowerCase()),
  ),
);

// 通告列表分页计算：计算一共需要多少页
const totalPages = computed(() =>
  Math.max(1, Math.ceil(filtered.value.length / pageSize)),
);

// 通告列表分页计算：每页的内容
const paginatedItems = computed(() => {
  const start = (currentPage.value - 1) * pageSize;
  return filtered.value.slice(start, start + pageSize);
});

// 切换页码函数
function changePage(page) {
  const target = Math.min(Math.max(page, 1), totalPages.value);
  currentPage.value = target;
}

// 判断当前页的通告是否已经全部选中
const allFilteredSelected = computed(
  () =>
    paginatedItems.value.length > 0 &&
    paginatedItems.value.every((n) => checkedIds.value.includes(n.id)),
);
const stats = computed(() => ({
  all: items.value.length,
  draft: items.value.filter((x) => x.status === "DRAFT").length,
  published: items.value.filter((x) => x.status === "PUBLISHED").length,
}));
async function load() {
  items.value = await getNotams();
  checkedIds.value = checkedIds.value.filter((id) =>
    items.value.some((n) => n.id === id),
  );

  // 删除或刷新数据后，避免停留在已经不存在的页码
  if (currentPage.value > totalPages.value) {
    currentPage.value = totalPages.value;
  }
}
const otherValue = (value, other) =>
  value === "OTHER"
    ? other.trim()
      ? `OTHER:${other.trim().toUpperCase().replaceAll(" ", "_")}`
      : ""
    : value;
const decodedValue = (value) =>
  value?.startsWith("OTHER:")
    ? ["OTHER", value.slice(6).replaceAll("_", " ")]
    : [value || "", ""];
function restrictionFrom(value) {
  const r = { ...newRestriction(), ...value };
  for (const name of [
    "operation",
    "aircraftType",
    "aircraftEngine",
    "flightType",
    "flightRule",
    "flightStatus",
    "flightMilitary",
    "flightOrigin",
    "flightPurpose",
  ]) {
    const [code, other] = decodedValue(value[name]);
    r[name] = code;
    r[`${name}Other`] = other;
  }
  r.includeAircraft = !![
    r.aircraftType,
    r.aircraftEngine,
    r.aircraftWingSpan,
    r.aircraftWeight,
  ].some(Boolean);
  r.includeFlight = !![
    r.flightType,
    r.flightRule,
    r.flightStatus,
    r.flightMilitary,
    r.flightOrigin,
    r.flightPurpose,
  ].some(Boolean);
  r.includePpr = !!r.pprValue;
  return r;
}
function startNew() {
  editingId.value = null;
  restrictions.value = [newRestriction()];
  scheduleEntries.value =
    form.scheduleMode === "CONTINUOUS"
      ? []
      : [newScheduleEntry(form.scheduleMode)];
  excludedDates.value = [];
  scheduleNote.value = "";
  view.value = "create";
}
function copyDraft(n) {
  editDraft(n);
  editingId.value = null;
  form.numberDigits = "";
  form.title = `${n.title}（副本）`;
}
function editDraft(n) {
  if (n.status !== "DRAFT") return;
  editingId.value = n.id;
  const nav = n.navUnsData || {},
    atsa = n.atsaNewData || {};
  const [navStatus, navOther] = decodedValue(nav.operationalStatus);
  Object.assign(form, n, nav, {
    operationalStatus: navStatus,
    operationalStatusOther: navOther,
    numberSeries: n.number.slice(0, 1),
    numberDigits: n.number.match(/^[A-Z](\d+)\//)?.[1] || "",
    effectiveStart: localInput(new Date(n.effectiveStart)),
    effectiveEnd: localInput(new Date(n.effectiveEnd)),
    lowerRestricted: !!n.lowerMeters,
    upperRestricted: !!n.upperMeters,
    scheduleDays: (n.scheduleDay || "").split(",").filter(Boolean),
    affectedAirports: ["ATSA.ACT", "ATSA.NEW", "NAV.UNS"].includes(n.scenario)
      ? (n.affectedAirports || "").split(",").filter(Boolean)
      : n.affectedAirports,
    limitationType:
      n.limitationType || n.restrictions?.[0]?.limitationType || "RESERV",
  });
  if (n.scenario === "ATSA.NEW")
    Object.assign(form, {
      atsaNewType: atsa.type,
      atsaNewClass: atsa.classification,
      atsaNewDesignator: atsa.designator,
      atsaNewName: atsa.name,
      atsaNewActivationStatus: atsa.activationStatus,
      atsaNewLocationNote: atsa.locationNote,
      atsaNewNote: atsa.note,
      controllingUnitNote: atsa.controllingUnitNote,
      geometryMode: "JSON",
      geometryJson: atsa.geometryJson,
      lowerValue: atsa.lowerValue,
      lowerUom: atsa.lowerUom,
      lowerReference: atsa.lowerReference,
      upperValue: atsa.upperValue,
      upperUom: atsa.upperUom,
      upperReference: atsa.upperReference,
      excludedAirspaces: atsa.excludedAirspaces,
      nearbyAirportThresholdNm: atsa.nearbyAirportThresholdNm || "5",
    });
  const sd = n.scheduleData || {};
  const saved = sd.entries?.length
    ? sd.entries.map((x) => ({ ...x, weekdayContinuous: false }))
    : n.scheduleMode === "CONTINUOUS"
      ? []
      : [newScheduleEntry(n.scheduleMode)];
  scheduleEntries.value =
    n.scheduleMode === "WEEKDAYS" ? compactWeekdayEntries(saved) : saved;
  excludedDates.value = [...(sd.excludedDates || [])];
  scheduleNote.value = sd.note || "";
  restrictions.value = n.restrictions?.length
    ? n.restrictions.map(restrictionFrom)
    : [restrictionFrom(n)];
  loadAirportFeatures();
  view.value = "create";
}
function encodedRestriction(source, index) {
  if (source.operation === "OTHER" && !source.operationOther.trim())
    throw new Error(`限制条件 ${index + 1}：OTHER Operation 必须填写具体字符`);
  if (
    source.includeAircraft &&
    ![
      source.aircraftType,
      source.aircraftEngine,
      source.aircraftWingSpan,
      source.aircraftWeight,
    ].some(Boolean)
  )
    throw new Error(`限制条件 ${index + 1}：Aircraft 至少填写一项限制`);
  if (
    source.includeFlight &&
    ![
      source.flightType,
      source.flightRule,
      source.flightStatus,
      source.flightMilitary,
      source.flightOrigin,
      source.flightPurpose,
    ].some(Boolean)
  )
    throw new Error(`限制条件 ${index + 1}：Flight 至少填写一项限制`);
  for (const name of [
    "aircraftType",
    "aircraftEngine",
    "flightType",
    "flightRule",
    "flightStatus",
    "flightMilitary",
    "flightOrigin",
    "flightPurpose",
  ])
    if (source[name] === "OTHER" && !source[`${name}Other`].trim())
      throw new Error(
        `限制条件 ${index + 1}：${name} 的 OTHER 必须填写具体字符`,
      );
  if (source.includePpr && !source.pprValue)
    throw new Error(`限制条件 ${index + 1}：PPR 必须填写时间`);
  const result = {
    ...source,
    limitationType: form.limitationType,
    operation: otherValue(source.operation, source.operationOther),
  };
  for (const name of [
    "aircraftType",
    "aircraftEngine",
    "flightType",
    "flightRule",
    "flightStatus",
    "flightMilitary",
    "flightOrigin",
    "flightPurpose",
  ])
    result[name] = otherValue(source[name], source[`${name}Other`]);
  if (!source.includeAircraft)
    for (const name of [
      "aircraftType",
      "aircraftEngine",
      "aircraftWingSpan",
      "aircraftWingSpanUom",
      "aircraftWingSpanInterpretation",
      "aircraftWeight",
      "aircraftWeightUom",
      "aircraftWeightInterpretation",
    ])
      result[name] = "";
  if (!source.includeFlight)
    for (const name of [
      "flightType",
      "flightRule",
      "flightStatus",
      "flightMilitary",
      "flightOrigin",
      "flightPurpose",
    ])
      result[name] = "";
  if (!source.includePpr)
    for (const name of ["pprValue", "pprUnit", "pprDetails"]) result[name] = "";
  return result;
}
async function create() {
  const payload = {
    ...form,
    scheduleDay: form.scheduleDays.join(","),
    affectedAirports: Array.isArray(form.affectedAirports)
      ? form.affectedAirports.join(",")
      : form.affectedAirports,
    qCode: form.qCode.toUpperCase(),
    qOverrideAt: form.qOverrideReason ? new Date().toISOString() : "",
    lowerMeters: form.lowerRestricted ? form.lowerMeters : "",
    upperMeters: form.upperRestricted ? form.upperMeters : "",
    effectiveStart: new Date(form.effectiveStart).toISOString(),
    effectiveEnd: new Date(form.effectiveEnd).toISOString(),
  };
  if (form.scenario === "ATSA.ACT")
    Object.assign(payload, {
      qCode: form.activationStatus === "ACTIVE" ? "QATCA" : "QATCD",
      traffic: "IV",
      purpose: form.activationStatus === "ACTIVE" ? "BO" : "NBO",
      scope: payload.affectedAirports ? "AE" : "E",
    });
  if (form.scenario === "ATSA.NEW")
    try {
      payload.atsaNewDesignator = form.atsaNewDesignator.trim().toUpperCase();
      payload.atsaNewName = form.atsaNewName.trim().toUpperCase();
      if (payload.atsaNewDesignator.length > 16)
        throw new Error(
          "Designator 最长16字符；官方示例应只填写 EADD028826，不要包含括号内的字段说明",
        );
      if (payload.atsaNewName.length > 60) throw new Error("Name 最长60字符");
      payload.geometryJson = atsaNewGeometryJson();
      Object.assign(payload, {
        qCode: "QXXXX",
        traffic: "IV",
        purpose: "NBO",
        scope: payload.affectedAirports ? "AE" : "E",
      });
    } catch (e) {
      notice.value = e.message;
      return;
    }
  if (form.scenario === "NAV.UNS") {
    if (
      form.operationalStatus === "OTHER" &&
      !form.operationalStatusOther.trim()
    ) {
      notice.value = "OTHER operational status 必须填写具体值";
      return;
    }
    payload.operationalStatus = otherValue(
      form.operationalStatus,
      form.operationalStatusOther,
    );
    Object.assign(payload, {
      qCode: "QXXXX",
      traffic: "IV",
      purpose: "BO",
      scope: payload.affectedAirports ? "AE" : "E",
    });
  }
  if (["AD.LIM", "RWY.LIM"].includes(form.scenario))
    try {
      payload.restrictions = restrictions.value.map(encodedRestriction);
    } catch (e) {
      notice.value = e.message;
      return;
    }
  const updating = !!editingId.value;
  try {
    await saveNotam(payload, editingId.value);
  } catch (error) {
    notice.value = error.message;
    return;
  }
  notice.value = updating ? "草稿已更新" : "草稿创建成功";
  editingId.value = null;
  view.value = "list";
  await load();
}
const expandedScheduleEntries = () =>
  form.scheduleMode !== "WEEKDAYS"
    ? scheduleEntries.value.map((x) => ({ ...x }))
    : scheduleEntries.value.flatMap((entry) => {
      if (!entry.weekdayContinuous)
        return [{ ...entry, dayTil: "", weekdayContinuous: undefined }];
      const from = weekdays.findIndex((x) => x[0] === entry.day),
        to = weekdays.findIndex((x) => x[0] === entry.dayTil);
      if (from < 0 || to <= from)
        throw new Error("连续星期的结束日必须晚于开始日，例如星期二至星期四");
      return weekdays.slice(from, to + 1).map(([day]) => ({
        ...entry,
        day,
        dayTil: "",
        weekdayContinuous: undefined,
      }));
    });
const currentScheduleData = () => ({
  type: form.scheduleMode,
  entries: form.scheduleMode === "CONTINUOUS" ? [] : expandedScheduleEntries(),
  excludedDates: excludedDates.value.filter(Boolean),
  note: scheduleNote.value,
});
async function prepareScheduleAndCreate() {
  let expanded;
  try {
    expanded = expandedScheduleEntries();
  } catch (e) {
    notice.value = e.message;
    return;
  }
  const first = expanded[0] || {};
  form.scheduleDay =
    form.scheduleMode === "WEEKDAYS"
      ? expanded.map((x) => x.day).join(",")
      : "ANY";
  form.scheduleStart = first.startTime || form.scheduleStart;
  form.scheduleEnd = first.endTime || form.scheduleEnd;
  form.scheduleStartDate = first.startDate || form.scheduleStartDate;
  form.scheduleEndDate =
    first.endDate || first.startDate || form.scheduleEndDate;
  form.scheduleData = {
    type: form.scheduleMode,
    entries: form.scheduleMode === "CONTINUOUS" ? [] : expanded,
    excludedDates: excludedDates.value.filter(Boolean),
    note: scheduleNote.value,
  };
  form.scheduleNote = scheduleNote.value;
  await create();
}
async function publish(n) {
  try {
    await publishNotam(n.id);
    notice.value = "通告发布成功";
    await load();
    selected.value = items.value.find((x) => x.id === n.id);
  } catch (e) {
    notice.value = `发布失败：${e.message}`;
  }
}
async function remove(n) {
  if (!window.confirm(`确定删除通告 ${n.number} 吗？此操作不可撤销。`)) return;
  try {
    await deleteNotam(n.id);
  } catch (error) {
    notice.value = error.message;
    return;
  }
  notice.value = `通告 ${n.number} 已删除`;
  selected.value = null;
  view.value = "list";
  await load();
}
function toggleAllFiltered(e) {
  // 表头复选框只选中或取消当前页的通告
  const visible = paginatedItems.value.map((n) => n.id);
  checkedIds.value = e.target.checked
    ? [...new Set([...checkedIds.value, ...visible])]
    : checkedIds.value.filter((id) => !visible.includes(id));
}
async function removeSelected() {
  const targets = items.value.filter((n) => checkedIds.value.includes(n.id));
  if (!targets.length) return;
  if (
    !window.confirm(
      `确定批量删除选中的 ${targets.length} 条通告吗？已发布通告的本地 XML 也会删除，此操作不可撤销。`,
    )
  )
    return;
  const failed = [];
  for (const n of targets) {
    try {
      await deleteNotam(n.id);
    } catch (e) {
      failed.push(`${n.number}: ${e.message}`);
    }
  }
  checkedIds.value = [];
  await load();
  notice.value = failed.length
    ? `批量删除完成，失败 ${failed.length} 条：${failed.join("；")}`
    : `已删除 ${targets.length} 条通告`;
}
async function detail(n) {
  try {
    const aixm = await getAixm(n.id);
    const converted =
      n.status === "PUBLISHED" ? await getCnotam(n.id) : "发布后生成";
    selected.value = n;
    xml.value = aixm;
    cnotam.value = converted;
    view.value = "detail";
  } catch (error) {
    notice.value = `查看通告失败：${error.message}`;
  }
}
async function importXml(e) {
  const file = e.target.files?.[0];
  if (!file) return;
  try {
    await importNotamXml(await file.text());
  } catch (error) {
    notice.value = "导入失败：" + error.message;
    return;
  }
  notice.value = "Digital NOTAM XML导入并发布成功";
  await load();
  e.target.value = "";
}
const dt = (s) =>
  s ? new Date(s).toLocaleString("zh-CN", { hour12: false }) : "—";
const compact = (s) =>
  s ? new Date(s).toISOString().slice(2, 16).replace(/[-T:]/g, "") : "—";
const fl = (meters, upper) =>
  meters !== undefined && meters !== null && String(meters).trim() !== ""
    ? String(Math.round((Number(meters) * 3.28) / 100)).padStart(3, "0")
    : upper
      ? "999"
      : "000";
const hasFg = (n) =>
  /^Q[WR]/.test((n?.qCode || form.qCode || "").toUpperCase());
const itemF = (n) =>
  hasFg(n)
    ? `FL${n?.minimumFl || fl(form.lowerRestricted ? form.lowerMeters : "", false)}`
    : "-";
const itemG = (n) =>
  hasFg(n)
    ? `FL${n?.maximumFl || fl(form.upperRestricted ? form.upperMeters : "", true)}`
    : "-";
const coordinate = (value, hem, width) => {
  let v = Number(value);
  let d = Math.floor(v),
    m = Math.round((v - d) * 60);
  if (m === 60) {
    d++;
    m = 0;
  }
  return String(d).padStart(width, "0") + String(m).padStart(2, "0") + hem;
};
const qLine = (n) => {
  const x = n || form;
  return `${x.fir || "EAAD"}/${x.qCode || "QXXXX"}/${x.traffic || "IV"}/${x.purpose || "NBO"}/${x.scope || "A"}/${x.minimumFl || fl(x.lowerRestricted ? x.lowerMeters : "", false)}/${x.maximumFl || fl(x.upperRestricted ? x.upperMeters : "", true)}/${coordinate(x.latitude || 0, x.latitudeHemisphere || "N", 2)}${coordinate(x.longitude || 0, x.longitudeHemisphere || "E", 3)}/${String(x.radiusNm || 0).padStart(3, "0")}`;
};
const weekdays = [
  ["MON", "星期一"],
  ["TUE", "星期二"],
  ["WED", "星期三"],
  ["THU", "星期四"],
  ["FRI", "星期五"],
  ["SAT", "星期六"],
  ["SUN", "星期日"],
];
const weekdayIndex = (day) => weekdays.findIndex((x) => x[0] === day);
const sameWeekdaySchedule = (a, b) =>
  a.startTime === b.startTime &&
  a.endTime === b.endTime &&
  !!a.endOfDay === !!b.endOfDay &&
  !a.startDate &&
  !b.startDate &&
  !a.endDate &&
  !b.endDate;
function compactWeekdayEntries(entries) {
  const result = [];
  for (let i = 0; i < entries.length;) {
    let j = i;
    while (
      j + 1 < entries.length &&
      weekdayIndex(entries[j + 1].day) === weekdayIndex(entries[j].day) + 1 &&
      sameWeekdaySchedule(entries[i], entries[j + 1])
    )
      j++;
    const row = {
      ...entries[i],
      weekdayContinuous: j > i,
      dayTil: j > i ? entries[j].day : "",
    };
    result.push(row);
    i = j + 1;
  }
  return result;
}
function weekdayRangeChanged(entry) {
  if (entry.weekdayContinuous) {
    const index = weekdayIndex(entry.day);
    entry.dayTil = weekdays[Math.min(index + 1, weekdays.length - 1)][0];
    if (index === weekdays.length - 1) {
      entry.day = "SAT";
      entry.dayTil = "SUN";
    }
  } else entry.dayTil = "";
}
function weekdayStartChanged(entry) {
  if (
    entry.weekdayContinuous &&
    weekdayIndex(entry.dayTil) <= weekdayIndex(entry.day)
  )
    entry.dayTil =
      weekdays[Math.min(weekdayIndex(entry.day) + 1, weekdays.length - 1)][0];
}
const weekdayRangeEnds = (entry) =>
  weekdays.filter((_, index) => index > weekdayIndex(entry.day));
const weekdayRangeStarts = (entry) =>
  entry.weekdayContinuous ? weekdays.slice(0, -1) : weekdays;
const scheduleText = (n) => {
  const mode = n?.scheduleMode || form.scheduleMode;
  if (mode === "CONTINUOUS") return "-";
  let data = n?.scheduleData;
  try {
    data = data || {
      entries: expandedScheduleEntries(),
      excludedDates: excludedDates.value,
    };
  } catch {
    return "连续星期范围无效 UTC";
  }
  const parts = (data.entries || []).map((x) => {
    const span = `${x.startTime.replace(":", "")}-${x.endOfDay ? "2400" : x.endTime.replace(":", "")}`;
    if (mode === "DAILY") return `DAILY ${span}`;
    if (mode === "DATES")
      return `${x.startDate}${x.endDate && x.endDate !== x.startDate ? " - " + x.endDate : ""} ${span}`;
    return `${x.day} ${span}`;
  });
  if (data.excludedDates?.length)
    parts.push(`EXC ${data.excludedDates.join(" ")}`);
  return `${parts.join(" ")} UTC`;
};
const scheduleDraftText = computed(() => scheduleText());
function scheduleModeChanged() {
  scheduleEntries.value =
    form.scheduleMode === "CONTINUOUS"
      ? []
      : [newScheduleEntry(form.scheduleMode)];
  if (form.scheduleMode === "DATES") excludedDates.value = [];
}
const addScheduleEntry = () =>
  scheduleEntries.value.push(newScheduleEntry(form.scheduleMode));
const removeScheduleEntry = (index) => {
  if (scheduleEntries.value.length > 1) scheduleEntries.value.splice(index, 1);
};
const addExcludedDate = () =>
  excludedDates.value.push(form.effectiveStart.slice(0, 10));
const removeExcludedDate = (index) => excludedDates.value.splice(index, 1);
const activationAirspaces = computed(() => {
  const values = activationPreview.value?.airspaces || [];
  return activationTab.value === "ALL"
    ? values
    : values.filter((x) => x.uuid === activationTab.value);
});
const activationCursorTime = computed(() => {
  if (!activationPreview.value) return "";
  const a = new Date(activationPreview.value.viewStart).getTime(),
    b = new Date(activationPreview.value.viewEnd).getTime();
  return (
    new Date(a + ((b - a) * activationCursor.value) / 1000).toLocaleString(
      "zh-CN",
      { hour12: false, timeZone: "UTC" },
    ) + " UTC"
  );
});
const cursorStates = computed(() => {
  if (!activationPreview.value) return [];
  const instant =
    new Date(activationPreview.value.viewStart).getTime() +
    ((new Date(activationPreview.value.viewEnd) -
      new Date(activationPreview.value.viewStart)) *
      activationCursor.value) /
    1000;
  return activationAirspaces.value
    .map((a) => ({
      label: a.label,
      interval: a.intervals.find(
        (x) =>
          new Date(x.start).getTime() <= instant &&
          instant < new Date(x.end).getTime(),
      ),
    }))
    .filter((x) => x.interval);
});
function activationRange() {
  const start = new Date(form.effectiveStart);
  start.setUTCDate(start.getUTCDate() + activationViewOffset.value);
  const days = activationViewMode.value === "DAY" ? 1 : 7;
  const end = new Date(start.getTime() + days * 86400000);
  const eventEnd = new Date(form.effectiveEnd);
  return [start, end < eventEnd ? end : eventEnd];
}
function intervalStyle(interval) {
  if (!activationPreview.value) return {};
  const a = new Date(activationPreview.value.viewStart).getTime(),
    b = new Date(activationPreview.value.viewEnd).getTime(),
    start = Math.max(a, new Date(interval.start).getTime()),
    end = Math.min(b, new Date(interval.end).getTime());
  return {
    left: `${((start - a) / (b - a)) * 100}%`,
    width: `${Math.max(0.25, ((end - start) / (b - a)) * 100)}%`,
  };
}
function navIntervalStyle(interval) {
  if (!navPreview.value) return {};
  const a = new Date(navPreview.value.viewStart).getTime(),
    b = new Date(navPreview.value.viewEnd).getTime(),
    start = Math.max(a, new Date(interval.start).getTime()),
    end = Math.min(b, new Date(interval.end).getTime());
  return {
    left: `${((start - a) / (b - a)) * 100}%`,
    width: `${Math.max(0.25, ((end - start) / (b - a)) * 100)}%`,
  };
}
function previewPayload() {
  const [viewStart, viewEnd] = activationRange();
  return {
    ...form,
    scheduleData: currentScheduleData(),
    scheduleNote: scheduleNote.value,
    scheduleDay: form.scheduleDays.join(","),
    effectiveStart: new Date(form.effectiveStart).toISOString(),
    effectiveEnd: new Date(form.effectiveEnd).toISOString(),
    viewStart: viewStart.toISOString(),
    viewEnd: viewEnd.toISOString(),
  };
}
let previewTimer;
async function loadActivationPreview() {
  if (
    form.scenario !== "ATSA.ACT" ||
    !form.selectedAirspaces ||
    !form.effectiveStart ||
    !form.effectiveEnd
  )
    return;
  activationPreviewLoading.value = true;
  activationPreviewError.value = "";
  try {
    const value = await getActivationPreview(previewPayload());
    activationPreview.value = value;
    if (
      activationTab.value !== "ALL" &&
      !value.airspaces.some((x) => x.uuid === activationTab.value)
    )
      activationTab.value = "ALL";
  } catch (e) {
    activationPreview.value = null;
    activationPreviewError.value = e.message;
  } finally {
    activationPreviewLoading.value = false;
  }
}
function queueActivationPreview() {
  clearTimeout(previewTimer);
  previewTimer = setTimeout(loadActivationPreview, 250);
}
function moveActivationView(days) {
  activationViewOffset.value += days;
  loadActivationPreview();
}
function changeActivationMode(mode) {
  activationViewMode.value = mode;
  activationViewOffset.value = 0;
  loadActivationPreview();
}
watch(
  () => [
    form.scenario,
    form.airspaceGroupId,
    form.selectedAirspaces,
    form.activationStatus,
    form.effectiveStart,
    form.effectiveEnd,
    form.scheduleMode,
    form.scheduleDays.join(","),
    form.scheduleStart,
    form.scheduleEnd,
    form.scheduleStartDate,
    form.scheduleEndDate,
  ],
  queueActivationPreview,
);

// 筛选条件变化时回到第一页
watch([query, status], () => {
  currentPage.value = 1;
});

function navPreviewPayload() {
  const status =
    form.operationalStatus === "OTHER" && form.operationalStatusOther.trim()
      ? otherValue(form.operationalStatus, form.operationalStatusOther)
      : form.operationalStatus;
  return {
    ...form,
    scheduleData: currentScheduleData(),
    scheduleNote: scheduleNote.value,
    operationalStatus: status,
    scheduleDay: form.scheduleDays.join(","),
    effectiveStart: new Date(form.effectiveStart).toISOString(),
    effectiveEnd: new Date(form.effectiveEnd).toISOString(),
    viewStart: new Date(form.effectiveStart).toISOString(),
    viewEnd: new Date(form.effectiveEnd).toISOString(),
  };
}
let navPreviewTimer;
async function loadNavPreview() {
  if (form.scenario !== "NAV.UNS" || !form.navaidUuid) return;
  navPreviewLoading.value = true;
  navPreviewError.value = "";
  try {
    const value = await getNavPreview(navPreviewPayload());
    navPreview.value = value;
  } catch (e) {
    navPreview.value = null;
    navPreviewError.value = e.message;
  } finally {
    navPreviewLoading.value = false;
  }
}
function queueNavPreview() {
  clearTimeout(navPreviewTimer);
  navPreviewTimer = setTimeout(loadNavPreview, 250);
}
watch(
  () => [
    form.scenario,
    form.navaidUuid,
    form.impactMode,
    form.equipmentUuid,
    form.signalType,
    form.operationalStatus,
    form.operationalStatusOther,
    form.signalStillEmitted,
    form.effectiveStart,
    form.effectiveEnd,
    form.scheduleMode,
    form.scheduleDays.join(","),
    form.scheduleStart,
    form.scheduleEnd,
    form.scheduleStartDate,
    form.scheduleEndDate,
  ],
  queueNavPreview,
);
watch(
  () => form.navaidUuid,
  (uuid) => {
    const n = navaids.value.find((x) => x.uuid === uuid);
    if (n) form.navaidType = navaidTypeOf(n);
  },
);
watch(
  [scheduleEntries, excludedDates, scheduleNote],
  () => {
    queueActivationPreview();
    queueNavPreview();
  },
  { deep: true },
);
onMounted(() => {
  load();
  loadAirports();
  loadAirspaces();
  loadNavaids();
});
</script>

<template>
  <div class="shell">
    <AppSidebar :view="view" @show-list="view = 'list'" @create="startNew" />
    <main>
      <AppHeader :view="view" :editing="!!editingId" @import-xml="importXml" @create="startNew" />
      <div v-if="notice" class="notice" @click="notice = ''">
        {{ notice }} <span>×</span>
      </div>
      <NotamList v-if="view === 'list'" :stats="stats" :items="paginatedItems" :query="query" :status="status"
        :checked-ids="checkedIds" :all-filtered-selected="allFilteredSelected" :format-date-time="dt"
        :current-page="currentPage" :total-pages="totalPages" :total-items="filtered.length"
        @update:query="query = $event" @update:status="status = $event" @update:checked-ids="checkedIds = $event"
        @toggle-all="toggleAllFiltered" @remove-selected="removeSelected" @detail="detail" @publish="publish"
        @remove="remove" @change-page="changePage" />
      <section v-else-if="view === 'create'" class="panel form">
        <div class="section-title">
          <b>Digital NOTAM</b><span>按ICAO NOTAM的Q行及A–G项填写和预览</span>
        </div>
        <div class="grid">
          <div class="field-group wide">
            <h3>通告业务设置</h3>
            <div class="group-grid business-grid">
              <label>业务场景 *<select v-model="form.scenario" @change="scenarioChanged">
                  <option v-for="s in scenarios" :key="s[0]" :value="s[0]">
                    {{ s[0] }} — {{ s[1] }}
                  </option>
                </select></label><label>编号系列 *<select v-model="form.numberSeries" :disabled="!!editingId">
                  <option value="A">A — 国际分发</option>
                  <option value="C">C — 国内分发</option>
                  <option value="D">D — 地区内分发</option>
                </select></label><label>编号数字<input v-model="form.numberDigits" maxlength="4" placeholder="留空自动分配"
                  :disabled="!!editingId" /></label><label>通告标题 *<input v-model="form.title" /></label>
            </div>
          </div>
          <div class="field-group wide">
            <h3>Q 行</h3>
            <div class="group-grid q-grid">
              <label>FIR *<select v-model="form.fir">
                  <option value="EAAD">EAAD</option>
                </select></label><label>Q-CODE *<input v-model="form.qCode" maxlength="5" placeholder="例如 QFALC"
                  :readonly="['AD.CLS', 'AD.LIM', 'RWY.CLS', 'RWY.LIM'].includes(
                    form.scenario,
                  )
                    " :title="['AD.CLS', 'AD.LIM', 'RWY.CLS', 'RWY.LIM'].includes(
                    form.scenario,
                  )
                      ? '发布时依据 XML 自动生成'
                      : ''
                    " /></label><label>TRAFFIC *<select v-model="form.traffic" :disabled="['AD.LIM', 'RWY.CLS', 'RWY.LIM'].includes(form.scenario)
                    ">
                  <option>I</option>
                  <option>V</option>
                  <option>IV</option>
                </select></label><label>PURPOSE *<select v-model="form.purpose" :disabled="['AD.LIM', 'RWY.CLS', 'RWY.LIM'].includes(form.scenario)
                  ">
                  <option>N</option>
                  <option>B</option>
                  <option>O</option>
                  <option>NB</option>
                  <option>NO</option>
                  <option>BO</option>
                  <option>NBO</option>
                </select></label><label>SCOPE *<select v-model="form.scope" :disabled="['AD.LIM', 'RWY.CLS', 'RWY.LIM'].includes(form.scenario)
                  ">
                  <option>A</option>
                  <option>E</option>
                  <option>AE</option>
                </select></label><label>纬度（十进制度）
                <div class="inline-input">
                  <input v-model="form.latitude" type="number" min="0" max="90" step="any" /><select
                    v-model="form.latitudeHemisphere">
                    <option>N</option>
                    <option>S</option>
                  </select>
                </div>
              </label><label>经度（十进制度）
                <div class="inline-input">
                  <input v-model="form.longitude" type="number" min="0" max="180" step="any" /><select
                    v-model="form.longitudeHemisphere">
                    <option>E</option>
                    <option>W</option>
                  </select>
                </div>
              </label><label>影响半径（NM）<input v-model="form.radiusNm" type="number" min="0" max="999" /></label>
            </div>
            <div v-if="form.scenario !== 'ATSA.NEW'" class="limit-grid">
              <label>下限类型<select v-model="form.lowerRestricted">
                  <option :value="false">无限制（000）</option>
                  <option :value="true">指定米数</option>
                </select></label><label>下限高度（米）<input v-model="form.lowerMeters" type="number" min="0" max="16000"
                  :disabled="!form.lowerRestricted" /><small>换算结果：FL{{
                    fl(form.lowerRestricted ? form.lowerMeters : "", false)
                  }}</small></label><label>上限类型<select v-model="form.upperRestricted">
                  <option :value="false">无限制（999）</option>
                  <option :value="true">指定米数</option>
                </select></label><label>上限高度（米）<input v-model="form.upperMeters" type="number" min="0" max="16000"
                  :disabled="!form.upperRestricted" /><small>换算结果：FL{{
                    fl(form.upperRestricted ? form.upperMeters : "", true)
                  }}</small></label>
            </div>
          </div>
          <div class="field-group wide abc-fields">
            <label v-if="
              !['ATSA.ACT', 'ATSA.NEW', 'NAV.UNS'].includes(form.scenario)
            ">A 项：机场<select v-model="form.airport" :disabled="['RWY.CLS', 'RWY.LIM'].includes(form.scenario)"
                @change="airportChanged">
                <option v-for="a in airports" :key="a.designator" :value="a.designator">
                  {{ a.designator }} — {{ a.name }} ({{ a.type }})
                </option>
              </select><small v-if="['RWY.CLS', 'RWY.LIM'].includes(form.scenario)">{{ form.scenario }} 第一版仅支持具备完整跑道基线的
                EADD</small><small v-else-if="airports.find((a) => a.designator === form.airport)">FIR {{ form.fir }} ·
                {{
                  airports.find((a) => a.designator === form.airport).firSource
                }}</small></label><label v-else-if="form.scenario === 'NAV.UNS'">A 项：自动解析结果<input
                :value="selectedAirportCodes().join(', ') || 'EAAD（Scope E）'" readonly /></label><label
              v-else-if="form.scenario === 'ATSA.NEW'">A 项：空间分析结果<input :value="atsaNewAssociations?.firs
                  .map((x) => x.designator)
                  .join(', ') || '请先分析几何'
                " readonly /></label><label v-else>A 项：自动解析结果<input value="EAAD（受影响机场为空时Scope E）"
                readonly /></label><label>B 项：开始时间 *<input v-model="form.effectiveStart"
                type="datetime-local" /></label><label>C 项：结束时间 *<input v-model="form.effectiveEnd"
                type="datetime-local" /></label>
          </div>
          <AtsaActFields v-if="form.scenario === 'ATSA.ACT'" :form="form" :groups="airspaceGroups" :airports="airports"
            :selected-airport-codes="selectedAirportCodes()" :preview="activationPreview"
            :preview-error="activationPreviewError" :preview-loading="activationPreviewLoading"
            :view-mode="activationViewMode" :tab="activationTab" :cursor="activationCursor"
            :cursor-time="activationCursorTime" :cursor-states="cursorStates" :visible-airspaces="activationAirspaces"
            :format-date-time="dt" :interval-style="intervalStyle" @group-change="airspaceGroupChanged"
            @activation-change="activationChanged" @toggle-airport="toggleAffectedAirport"
            @toggle-airspace="toggleSelectedAirspace" @move-view="moveActivationView"
            @change-mode="changeActivationMode" @load-preview="loadActivationPreview"
            @update:tab="activationTab = $event" @update:cursor="activationCursor = $event" />
          <AtsaNewFields v-if="form.scenario === 'ATSA.NEW'" :form="form" :airports="airports"
            :filtered-baseline-airspaces="filteredBaselineAirspaces" :atsa-new-associations="atsaNewAssociations"
            :atsa-new-association-error="atsaNewAssociationError"
            :atsa-new-association-loading="atsaNewAssociationLoading" :selected-airport-codes="selectedAirportCodes"
            :selected-excluded-airspaces="selectedExcludedAirspaces" :analyse-atsa-new-geometry="analyseAtsaNewGeometry"
            :toggle-affected-airport="toggleAffectedAirport" :toggle-excluded-airspace="toggleExcludedAirspace" />
          <NavUnsFields v-if="form.scenario === 'NAV.UNS'" :form="form" :navaid-types="navaidTypes"
            :navaids="filteredNavaids" :selected-navaid="selectedNavaid()" :airports="airports"
            :selected-airport-codes="selectedAirportCodes()" :preview="navPreview" :preview-error="navPreviewError"
            :preview-loading="navPreviewLoading" :format-date-time="dt" :interval-style="navIntervalStyle"
            @navaid-type-change="navaidTypeChanged" @navaid-change="navaidChanged"
            @impact-mode-change="impactModeChanged" @queue-preview="queueNavPreview"
            @toggle-airport="toggleAffectedAirport" @load-preview="loadNavPreview" />
          <div v-if="form.scenario === 'AD.CLS'" class="field-group wide">
            <h3>Q 行人工修正（可选）</h3>
            <div class="group-grid">
              <label>修改原因<input v-model="form.qOverrideReason" placeholder="留空则采用系统自动值" /></label><label>操作员<input
                  v-model="form.qOverrideOperator" :disabled="!form.qOverrideReason" /></label><label>修正 Q-code<input
                  v-model="form.qCode" maxlength="5" :disabled="!form.qOverrideReason" /></label><label>修正
                Traffic<select v-model="form.traffic" :disabled="!form.qOverrideReason">
                  <option>I</option>
                  <option>V</option>
                  <option>IV</option>
                </select></label><label>修正 Purpose<select v-model="form.purpose" :disabled="!form.qOverrideReason">
                  <option>N</option>
                  <option>B</option>
                  <option>O</option>
                  <option>NB</option>
                  <option>NO</option>
                  <option>BO</option>
                  <option>NBO</option>
                </select></label><label>修正 Scope<select v-model="form.scope" :disabled="!form.qOverrideReason">
                  <option>A</option>
                  <option>E</option>
                  <option>AE</option>
                </select></label>
            </div>
            <small>人工修正只影响 NOTAM Q 行，不改变 Event/TEMPDELTA。</small>
          </div>
          <div v-if="['RWY.CLS', 'RWY.LIM'].includes(form.scenario)" class="field-group wide">
            <h3>{{ form.scenario }} 跑道目标</h3>
            <div class="group-grid structured-e">
              <label>目标对象 *<select v-model="form.rwyTargetType">
                  <option value="RUNWAY">整条跑道（全部方向）</option>
                  <option value="RUNWAY_DIRECTION">单一跑道方向</option>
                </select></label><label>跑道 / FATO *<select v-model="form.runwayUuid" @change="runwayChanged">
                  <option v-for="r in runways" :key="r.uuid" :value="r.uuid">
                    {{ r.designator }}
                  </option>
                </select></label><label>Runway surface composition *<select v-model="form.runwaySurfaceComposition">
                  <option v-for="surface in runways.find(
                    (x) => x.uuid === form.runwayUuid,
                  )?.surfaceCompositions || []" :key="surface" :value="surface">
                    {{ surface }}
                  </option>
                </select></label><label v-if="form.rwyTargetType === 'RUNWAY_DIRECTION'">跑道方向 *<select
                  v-model="form.runwayDirectionUuid">
                  <option v-for="d in runways.find((x) => x.uuid === form.runwayUuid)
                    ?.directions || []" :key="d.uuid" :value="d.uuid">
                    {{ d.designator }}
                  </option>
                </select></label>
            </div>
            <small>选择整条跑道时将为所有关联 RunwayDirection 分别生成
              TEMPDELTA。</small>
          </div>
          <RestrictionEditor v-if="['AD.LIM', 'RWY.LIM'].includes(form.scenario)" :form="form"
            :restrictions="restrictions" :add-restriction="addRestriction" :remove-restriction="removeRestriction"
            :move-restriction="moveRestriction" />
          <ScheduleEditor :form="form" :entries="scheduleEntries" :excluded-dates="excludedDates" :note="scheduleNote"
            :draft-text="scheduleDraftText" :weekday-range-starts="weekdayRangeStarts"
            :weekday-range-ends="weekdayRangeEnds" @mode-change="scheduleModeChanged"
            @weekday-range-change="weekdayRangeChanged" @weekday-start-change="weekdayStartChanged"
            @add-entry="addScheduleEntry" @remove-entry="removeScheduleEntry" @add-excluded-date="addExcludedDate"
            @remove-excluded-date="removeExcludedDate" @update:note="scheduleNote = $event" />
          <div class="field-group wide">
            <h3>E 项：结构化事件数据</h3>
            <div class="group-grid structured-e">
              <label>原因{{
                [
                  "AD.CLS",
                  "AD.LIM",
                  "RWY.LIM",
                  "ATSA.ACT",
                  "ATSA.NEW",
                  "NAV.UNS",
                ].includes(form.scenario)
                  ? "（可选）"
                  : " *"
              }}<input v-model="form.reason" placeholder="例如 SURFACE MAINTENANCE" /></label><label>备注<input
                  v-model="form.remarks" placeholder="可选，例如 RUBBER REMOVAL" /></label>
            </div>
            <small>事件状态由场景规则和上方业务输入自动确定，不需要人工重复选择。</small>
          </div>
          <label>F 项：下限（仅QW/QR）<input :value="itemF()" readonly /></label><label>G 项：上限（仅QW/QR）<input :value="itemG()"
              readonly /></label>
        </div>
        <div class="actions">
          <button @click="
            view = 'list';
          editingId = null;
          ">
            取消</button><button class="primary" @click="prepareScheduleAndCreate">
            {{ editingId ? "保存修改" : "保存为草稿" }}
          </button>
        </div>
      </section>
      <NotamDetail v-else :notam="selected" :xml="xml" :cnotam="cnotam" :q-line="qLine" :compact-date-time="compact"
        :schedule-text="scheduleText" :item-f="itemF" :item-g="itemG" :format-date-time="dt" @remove="remove"
        @copy="copyDraft" @edit="editDraft" @publish="publish" />
    </main>
  </div>
</template>
