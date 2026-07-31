<script setup>
defineProps({
  form: { type: Object, required: true },
  groups: { type: Array, required: true },
  airports: { type: Array, required: true },
  selectedAirportCodes: { type: Array, required: true },
  preview: { type: Object, default: null },
  previewError: { type: String, default: "" },
  previewLoading: { type: Boolean, default: false },
  viewMode: { type: String, required: true },
  tab: { type: String, required: true },
  cursor: { type: Number, required: true },
  cursorTime: { type: String, default: "" },
  cursorStates: { type: Array, required: true },
  visibleAirspaces: { type: Array, required: true },
  formatDateTime: { type: Function, required: true },
  intervalStyle: { type: Function, required: true },
});

defineEmits([
  "group-change",
  "activation-change",
  "toggle-airport",
  "toggle-airspace",
  "move-view",
  "change-mode",
  "load-preview",
  "update:tab",
  "update:cursor",
]);
</script>

<template>
  <div class="field-group wide">
    <h3>ATSA.ACT 空域目标</h3>
    <div class="group-grid structured-e">
      <label>
        逻辑空域分组 *
        <select v-model="form.airspaceGroupId" @change="$emit('group-change')">
          <option v-for="group in groups" :key="group.id" :value="group.id">
            {{ group.name }}
          </option>
        </select>
      </label>
      <label>
        状态 *
        <select
          v-model="form.activationStatus"
          @change="$emit('activation-change')"
        >
          <option>ACTIVE</option>
          <option>INACTIVE</option>
        </select>
      </label>
    </div>

    <div class="limit-section compact-check-list">
      <b>受影响机场（可多选）</b>
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
      <small>
        未选择机场时生成Scope E；选择机场后生成首件Scope
        AE，其余机场分别生成Scope A。
      </small>
    </div>

    <div class="limit-section compact-check-list">
      <b>分组成员</b>
      <label
        v-for="airspace in groups.find(
          (group) => group.id === form.airspaceGroupId,
        )?.members || []"
        :key="airspace.uuid"
        class="inline-check"
      >
        <input
          type="checkbox"
          :checked="form.selectedAirspaces.split(',').includes(airspace.uuid)"
          @change="
            $emit('toggle-airspace', airspace.uuid, $event.target.checked)
          "
        />
        <span>
          {{ airspace.name }} {{ airspace.designator }} · {{ airspace.type }}
          <template v-if="airspace.classification">
            · CLASS {{ airspace.classification }}
          </template>
        </span>
      </label>
    </div>

    <div class="limit-section activation-calendar">
      <div class="calendar-toolbar">
        <div>
          <b>基线与事件时间 / 高度状态</b>
          <small>UTC · 预览与发布使用相同的后端合成结果</small>
        </div>
        <div class="calendar-actions">
          <button
            type="button"
            @click="$emit('move-view', viewMode === 'DAY' ? -1 : -7)"
          >
            ←
          </button>
          <button
            type="button"
            :class="{ active: viewMode === 'DAY' }"
            @click="$emit('change-mode', 'DAY')"
          >
            日
          </button>
          <button
            type="button"
            :class="{ active: viewMode === 'WEEK' }"
            @click="$emit('change-mode', 'WEEK')"
          >
            周
          </button>
          <button
            type="button"
            @click="$emit('move-view', viewMode === 'DAY' ? 1 : 7)"
          >
            →
          </button>
          <button type="button" @click="$emit('load-preview')">刷新</button>
        </div>
      </div>

      <div v-if="preview" class="calendar-tabs">
        <button
          type="button"
          :class="{ active: tab === 'ALL' }"
          @click="$emit('update:tab', 'ALL')"
        >
          全部扇区
        </button>
        <button
          v-for="airspace in preview.airspaces"
          :key="airspace.uuid"
          type="button"
          :class="{ active: tab === airspace.uuid }"
          @click="$emit('update:tab', airspace.uuid)"
        >
          {{ airspace.label }}
        </button>
      </div>

      <div v-if="previewLoading" class="calendar-message">
        正在根据基线和事件时间表计算状态…
      </div>
      <div v-else-if="previewError" class="calendar-message error">
        {{ previewError }}
      </div>
      <template v-else-if="preview">
        <div class="calendar-period">
          {{ formatDateTime(preview.viewStart) }} —
          {{ formatDateTime(preview.viewEnd) }} UTC
          <code>{{ preview.compositionHash.slice(0, 12) }}</code>
        </div>
        <div
          v-for="airspace in visibleAirspaces"
          :key="airspace.uuid"
          class="airspace-timeline"
        >
          <div class="timeline-label">
            <b>{{ airspace.label }}</b>
            <span>FL{{ String(airspace.upperFl).padStart(3, "0") }}</span>
            <span>FL{{ String(airspace.lowerFl).padStart(3, "0") }}</span>
          </div>
          <div class="timeline-track">
            <div
              v-for="interval in airspace.intervals"
              :key="interval.start + interval.source"
              class="timeline-block"
              :class="[
                interval.source.toLowerCase(),
                interval.status.toLowerCase(),
              ]"
              :style="intervalStyle(interval)"
              :title="`${interval.status} · ${interval.source}\n${interval.start} — ${interval.end}\nFL${interval.lowerFl}–FL${interval.upperFl}`"
            >
              <span>{{ interval.status }}</span>
            </div>
            <i class="timeline-cursor" :style="{ left: `${cursor / 10}%` }"></i>
          </div>
        </div>
        <div class="calendar-legend">
          <span><i class="event"></i>事件状态</span>
          <span><i class="baseline"></i>基线状态</span>
          <span><i class="gap"></i>未知/空档</span>
          <span><i class="conflict"></i>冲突</span>
        </div>
        <label class="time-cursor">
          检查时刻：{{ cursorTime }}
          <input
            :value="cursor"
            type="range"
            min="0"
            max="1000"
            step="1"
            @input="$emit('update:cursor', Number($event.target.value))"
          />
        </label>
        <div class="cursor-state">
          <span v-for="state in cursorStates" :key="state.label">
            <b>{{ state.label }}</b>
            {{ state.interval.status }} · {{ state.interval.source }} · FL{{
              state.interval.lowerFl
            }}–FL{{ state.interval.upperFl }}
          </span>
        </div>
        <div v-if="preview.issues.length" class="calendar-issues">
          <b>需要处理的时间状态问题</b>
          <p
            v-for="issue in preview.issues"
            :key="issue.code + issue.airspaceUuid + issue.start"
            :class="{ blocking: issue.blocking }"
          >
            {{ issue.blocking ? "阻断" : "提示" }} · {{ issue.message }} ·
            {{ formatDateTime(issue.start) }}–{{
              formatDateTime(issue.end)
            }}
            UTC
          </p>
        </div>
      </template>
    </div>
  </div>
</template>
