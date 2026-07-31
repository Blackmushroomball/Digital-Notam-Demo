<script setup>
defineProps({
  form: { type: Object, required: true },
  navaidTypes: { type: Array, required: true },
  navaids: { type: Array, required: true },
  selectedNavaid: { type: Object, default: null },
  airports: { type: Array, required: true },
  selectedAirportCodes: { type: Array, required: true },
  preview: { type: Object, default: null },
  previewError: { type: String, default: "" },
  previewLoading: { type: Boolean, default: false },
  formatDateTime: { type: Function, required: true },
  intervalStyle: { type: Function, required: true },
});

defineEmits([
  "navaid-type-change",
  "navaid-change",
  "impact-mode-change",
  "queue-preview",
  "toggle-airport",
  "load-preview",
]);
</script>

<template>
  <div class="field-group wide nav-uns">
    <h3>NAV.UNS 导航设施状态</h3>
    <div class="group-grid structured-e">
      <label>
        Type *
        <select v-model="form.navaidType" @change="$emit('navaid-type-change')">
          <option v-for="type in navaidTypes" :key="type" :value="type">
            {{ type }}
          </option>
        </select>
      </label>
      <label>
        导航设施 *
        <select v-model="form.navaidUuid" @change="$emit('navaid-change')">
          <option
            v-for="navaid in navaids"
            :key="navaid.uuid"
            :value="navaid.uuid"
          >
            {{ navaid.name || navaid.designator
            }}{{
              navaid.name && navaid.designator ? ` (${navaid.designator})` : ""
            }}
          </option>
        </select>
        <small v-if="!navaids.length">当前基线没有该类型的完整导航设施</small>
      </label>
      <label>
        影响范围 *
        <select v-model="form.impactMode" @change="$emit('impact-mode-change')">
          <option value="ALL_PRIMARY">全部主要组成设备</option>
          <option value="COMPONENT">单个组成设备</option>
          <option value="SIGNAL">TACAN 单个信号</option>
        </select>
      </label>
      <label v-if="form.impactMode !== 'ALL_PRIMARY'">
        组成设备 *
        <select v-model="form.equipmentUuid" @change="$emit('queue-preview')">
          <option
            v-for="component in selectedNavaid?.components || []"
            :key="component.uuid"
            :value="component.uuid"
          >
            {{ component.type }} {{ component.designator }}
            {{ component.frequency }}{{ component.frequencyUom }}
            {{ component.channel }}
          </option>
        </select>
      </label>
      <label v-if="form.impactMode === 'SIGNAL'">
        信号 *
        <select v-model="form.signalType">
          <option>AZIMUTH</option>
          <option>DISTANCE</option>
        </select>
      </label>
      <label>
        事件运行状态 *
        <select v-model="form.operationalStatus">
          <option>UNSERVICEABLE</option>
          <option>ONTEST</option>
          <option>INTERRUPT</option>
          <option>FALSE_INDICATION</option>
          <option>IN_CONSTRUCTION</option>
          <option>OTHER</option>
        </select>
      </label>
      <label v-if="form.operationalStatus === 'OTHER'">
        OTHER 具体值 *
        <input v-model="form.operationalStatusOther" />
      </label>
      <label v-if="form.operationalStatus === 'ONTEST'" class="inline-check">
        <input v-model="form.signalStillEmitted" type="checkbox" />
        <span>确认测试期间仍发射信号 *</span>
      </label>
    </div>

    <div v-if="selectedNavaid" class="limit-section navaid-baseline">
      <b>基线状态</b>
      <div class="nav-status-row">
        <span>{{ selectedNavaid.type }} {{ selectedNavaid.designator }}</span>
        <code>{{ selectedNavaid.baselineStatuses.join(", ") }}</code>
      </div>
      <div
        v-for="component in selectedNavaid.components"
        :key="component.uuid"
        class="nav-status-row"
      >
        <span>
          {{ component.primary ? "主要" : "辅助" }} · {{ component.type }}
          {{ component.frequency }}{{ component.frequencyUom }}
          {{ component.channel }}
        </span>
        <code>{{ component.baselineStatuses.join(", ") }}</code>
      </div>
    </div>

    <div class="limit-section compact-check-list">
      <b>关联机场（自动勾选，可取消或增加）</b>
      <div class="check-grid">
        <label
          v-for="airport in airports"
          :key="airport.designator"
          class="inline-check"
        >
          <input
            type="checkbox"
            :checked="selectedAirportCodes.includes(airport.designator)"
            @change="
              $emit('toggle-airport', airport.designator, $event.target.checked)
            "
          />
          <span>{{ airport.designator }} — {{ airport.name }}</span>
        </label>
      </div>
    </div>

    <div class="limit-section activation-calendar">
      <div class="calendar-toolbar">
        <div>
          <b>基线与事件时间状态</b>
          <small>UTC · 后端使用与 XML 编码相同的状态合成规则</small>
        </div>
        <button type="button" @click="$emit('load-preview')">刷新</button>
      </div>
      <div v-if="previewLoading" class="calendar-message">
        正在计算导航设施状态…
      </div>
      <div v-else-if="previewError" class="calendar-message error">
        {{ previewError }}
      </div>
      <template v-else-if="preview">
        <div class="calendar-period">
          {{ formatDateTime(preview.viewStart) }} —
          {{ formatDateTime(preview.viewEnd) }} UTC ·
          {{ preview.eventCount }} 个 Event
        </div>
        <div
          v-for="row in preview.rows"
          :key="row.id"
          class="airspace-timeline"
        >
          <div class="timeline-label">
            <b>{{ row.label }}</b>
          </div>
          <div class="timeline-track">
            <div
              v-for="interval in row.intervals"
              :key="interval.start + interval.end + interval.status"
              class="timeline-block"
              :class="interval.source.toLowerCase()"
              :style="intervalStyle(interval)"
              :title="`${interval.status} · ${interval.source}\n${interval.start} — ${interval.end}`"
            >
              <span>{{ interval.status }}</span>
            </div>
          </div>
        </div>
        <div class="calendar-legend">
          <span><i class="event"></i>事件状态</span>
          <span><i class="baseline"></i>基线状态/补齐</span>
        </div>
      </template>
    </div>
  </div>
</template>
