<script setup>
import { defineAsyncComponent } from "vue";

const GeometryMapEditor = defineAsyncComponent(
  () => import("../common/GeometryMapEditor.vue"),
);

defineProps({
  form: { type: Object, required: true },
  airports: { type: Array, required: true },
  baselineAirspaces: { type: Array, required: true },
  filteredBaselineAirspaces: { type: Array, required: true },
  atsaNewAssociations: { type: Object, default: null },
  atsaNewAssociationError: { type: String, default: "" },
  atsaNewAssociationLoading: { type: Boolean, default: false },
  selectedAirportCodes: { type: Function, required: true },
  selectedExcludedAirspaces: { type: Function, required: true },
  analyseAtsaNewGeometry: { type: Function, required: true },
  changeGeometryMode: { type: Function, required: true },
  toggleAffectedAirport: { type: Function, required: true },
  toggleExcludedAirspace: { type: Function, required: true },
});
</script>

<template>
  <div v-if="form.scenario === 'ATSA.NEW'" class="field-group wide atsa-new">
    <h3>ATSA.NEW 临时空域</h3>
    <div class="group-grid structured-e">
      <label
        >空域类型 *<select v-model="form.atsaNewType">
          <option
            v-for="type in [
              'CTR',
              'CLASS',
              'ATZ',
              'HTZ',
              'ADIZ',
              'CTA',
              'UTA',
              'OCA',
              'OTA',
              'AWY',
              'SECTOR',
              'SECTOR_C',
              'RAS',
              'TMA',
              'ADV',
              'UADV',
              'FIR',
              'OTHER:TMZ',
              'OTHER:RMZ',
            ]"
            :key="type"
          >
            {{ type }}
          </option>
        </select></label
      >
      <label
        >空域等级 *<select v-model="form.atsaNewClass">
          <option
            v-for="value in ['A', 'B', 'C', 'D', 'E', 'F', 'G']"
            :key="value"
          >
            {{ value }}
          </option>
        </select></label
      >
      <label
        >启用状态 *<select v-model="form.atsaNewActivationStatus">
          <option>ACTIVE</option>
          <option>INTERMITTENT</option>
        </select></label
      >
      <label
        >Designator<input
          v-model="form.atsaNewDesignator"
          maxlength="16"
          placeholder="例如 EADD028826"
        /><small>只填写实际编码；不要复制官方说明中的括号内容。</small></label
      >
      <label
        >Name<input
          v-model="form.atsaNewName"
          maxlength="60"
          placeholder="例如 DONLON HTZ"
      /></label>
      <label
        >附近机场阈值（NM） *<input
          v-model="form.nearbyAirportThresholdNm"
          type="number"
          min="0"
          max="100"
          step="0.1"
      /></label>
    </div>
    <section class="limit-section">
      <b>垂直范围</b>
      <div class="atsa-vertical">
        <label
          >下限 *<input
            v-model="form.lowerValue"
            placeholder="GND / SFC / 数值"
        /></label>
        <label
          >下限单位<select
            v-model="form.lowerUom"
            :disabled="
              ['GND', 'SFC', 'UNL'].includes(form.lowerValue.toUpperCase())
            "
          >
            <option value=""></option>
            <option>FL</option>
            <option>FT</option>
            <option>M</option>
          </select></label
        >
        <label
          >下限基准<select
            v-model="form.lowerReference"
            :disabled="
              ['GND', 'SFC', 'UNL'].includes(form.lowerValue.toUpperCase())
            "
          >
            <option value=""></option>
            <option>STD</option>
            <option>MSL</option>
            <option>HEI</option>
          </select></label
        >
        <label
          >上限 *<input v-model="form.upperValue" placeholder="UNL / 数值"
        /></label>
        <label
          >上限单位<select
            v-model="form.upperUom"
            :disabled="
              ['GND', 'SFC', 'UNL'].includes(form.upperValue.toUpperCase())
            "
          >
            <option value=""></option>
            <option>FL</option>
            <option>FT</option>
            <option>M</option>
          </select></label
        >
        <label
          >上限基准<select
            v-model="form.upperReference"
            :disabled="
              ['GND', 'SFC', 'UNL'].includes(form.upperValue.toUpperCase())
            "
          >
            <option value=""></option>
            <option>STD</option>
            <option>MSL</option>
            <option>HEI</option>
          </select></label
        >
      </div>
    </section>
    <section class="limit-section geometry-editor">
      <div class="geometry-toolbar">
        <b>水平几何（WGS-84，统一 x=经度 / y=纬度）</b>
        <div>
          <button
            type="button"
            :class="{ active: form.geometryMode === 'STRUCTURED' }"
            @click="changeGeometryMode('STRUCTURED')"
          >
            结构化</button
          ><button
            type="button"
            :class="{ active: form.geometryMode === 'MAP' }"
            @click="changeGeometryMode('MAP')"
          >
            图形绘制</button
          ><button
            type="button"
            :class="{ active: form.geometryMode === 'JSON' }"
            @click="changeGeometryMode('JSON')"
          >
            高级 JSON
          </button>
        </div>
      </div>
      <template v-if="form.geometryMode === 'STRUCTURED'">
        <div class="group-grid structured-e">
          <label
            >几何类型 *<select v-model="form.geometryType">
              <option>CIRCLE</option>
              <option>POLYGON</option>
              <option>CORRIDOR</option>
            </select></label
          >
          <template v-if="form.geometryType === 'CIRCLE'">
            <label
              >中心 x / 经度 *<input
                v-model="form.circleX"
                placeholder="-27.399 或 0272357.3W"
            /></label>
            <label
              >中心 y / 纬度 *<input
                v-model="form.circleY"
                placeholder="53.620 或 533711.3N"
            /></label>
            <label
              >半径 *<input
                v-model="form.circleRadius"
                type="number"
                min="0"
                step="any"
            /></label>
            <label
              >半径单位 *<select v-model="form.circleRadiusUom">
                <option>NM</option>
                <option>KM</option>
                <option>M</option>
                <option>FT</option>
              </select></label
            >
          </template>
          <template v-else-if="form.geometryType === 'CORRIDOR'">
            <label
              >总宽度 *<input
                v-model="form.corridorWidth"
                type="number"
                min="0"
                step="any"
            /></label>
            <label
              >宽度单位 *<select v-model="form.corridorWidthUom">
                <option>NM</option>
                <option>KM</option>
                <option>M</option>
                <option>FT</option>
              </select></label
            >
          </template>
        </div>
        <label v-if="form.geometryType !== 'CIRCLE'" class="geometry-points"
          >坐标点，每行 x,y *
          <textarea v-model="form.geometryPoints" spellcheck="false"></textarea>
          <small
            >每个 x/y 可分别输入十进制度或度分秒，例如 -27.399,53.620 或
            0272357.3W,533711.3N。Polygon 将自动闭合；ArcByEdge、ArcByCenter
            等高级片段请使用 JSON 模式。</small
          >
        </label>
      </template>
      <GeometryMapEditor
        v-else-if="form.geometryMode === 'MAP'"
        v-model="form.geometryJson"
        :airports="airports"
        :airspaces="baselineAirspaces"
        @error="form.geometryModeError = $event"
      />
      <label v-else class="geometry-json"
        >AIXM 几何 JSON *<textarea
          v-model="form.geometryJson"
          spellcheck="false"
          placeholder='{"schemaVersion":"1.0","geometry":{...}}'
        ></textarea
        ><small
          >支持共享几何工具的 Polygon、Circle、Corridor 完整
          JSON，包括混合弧线片段。</small
        ></label
      >
      <p v-if="form.geometryModeError" class="geometry-mode-error">
        {{ form.geometryModeError }}
      </p>
      <div class="geometry-actions">
        <button
          type="button"
          class="primary"
          :disabled="atsaNewAssociationLoading"
          @click="analyseAtsaNewGeometry(true)"
        >
          {{
            atsaNewAssociationLoading ? "正在分析…" : "分析相交 FIR 与附近机场"
          }}</button
        ><span v-if="atsaNewAssociationError" class="error">{{
          atsaNewAssociationError
        }}</span>
      </div>
    </section>
    <section
      v-if="atsaNewAssociations"
      class="limit-section association-result"
    >
      <b>空间分析建议</b>
      <p>
        中心 {{ atsaNewAssociations.centre.latitude.toFixed(5) }},
        {{ atsaNewAssociations.centre.longitude.toFixed(5) }} · Q 行半径
        {{ atsaNewAssociations.radiusNm }} NM
      </p>
      <p>
        相交 FIR：<strong>{{
          atsaNewAssociations.firs
            .map((x) => `${x.designator} ${x.name}`)
            .join("；") || "无"
        }}</strong>
      </p>
      <small
        >FIR 由发布时的基线几何强制重新计算；下方机场是
        {{ form.nearbyAirportThresholdNm }} NM 建议，操作员可取消或增加。</small
      >
    </section>
    <section class="limit-section compact-check-list">
      <b>受影响机场（建议已自动勾选，可人工调整）</b>
      <div class="check-grid">
        <label v-for="a in airports" :key="a.designator" class="inline-check"
          ><input
            type="checkbox"
            :checked="selectedAirportCodes().includes(a.designator)"
            @change="
              (e) => toggleAffectedAirport(a.designator, e.target.checked)
            "
          /><span
            >{{ a.designator }} — {{ a.name
            }}<template
              v-if="
                atsaNewAssociations?.airports.find(
                  (x) => x.designator === a.designator,
                )
              "
            >
              ·
              {{
                atsaNewAssociations.airports.find(
                  (x) => x.designator === a.designator,
                ).distanceNm
              }}
              NM</template
            ></span
          ></label
        >
      </div>
    </section>
    <section class="limit-section compact-check-list">
      <b>排除的基线空域（SUBTR，可选；不支持内环）</b
      ><input
        v-model="excludedAirspaceQuery"
        class="airspace-search"
        placeholder="搜索名称、designator 或类型，例如 DONLON CTR"
      />
      <div class="check-grid">
        <label
          v-for="a in filteredBaselineAirspaces"
          :key="a.uuid"
          class="inline-check"
          ><input
            type="checkbox"
            :checked="selectedExcludedAirspaces().has(a.uuid)"
            @change="(e) => toggleExcludedAirspace(a.uuid, e.target.checked)"
          /><span
            >{{ a.designator || "—" }} — {{ a.name || "未命名" }} ·
            {{ a.type }}</span
          ></label
        >
      </div>
      <small
        >基线包含官方示例使用的 EADD — DONLON CTR 和 EADA — AKVIN
        CTR；只有操作员明确选择后才编码为 SUBTR。</small
      >
    </section>
    <div class="group-grid structured-e atsa-notes">
      <label>几何位置说明<input v-model="form.atsaNewLocationNote" /></label
      ><label>管制单位说明<input v-model="form.controllingUnitNote" /></label
      ><label>空域备注<input v-model="form.atsaNewNote" /></label>
    </div>
  </div>
</template>
