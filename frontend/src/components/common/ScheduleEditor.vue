<script setup>
defineProps({
  form: { type: Object, required: true },
  entries: { type: Array, required: true },
  excludedDates: { type: Array, required: true },
  note: { type: String, default: "" },
  draftText: { type: String, default: "" },
  weekdayRangeStarts: { type: Function, required: true },
  weekdayRangeEnds: { type: Function, required: true },
});

defineEmits([
  "mode-change",
  "weekday-range-change",
  "weekday-start-change",
  "add-entry",
  "remove-entry",
  "add-excluded-date",
  "remove-excluded-date",
  "update:note",
]);
</script>

<template>
  <div class="field-group wide schedule-editor">
    <h3>D 项：时间计划（UTC）</h3>
    <div class="group-grid schedule">
      <label>
        时间表类型
        <select v-model="form.scheduleMode" @change="$emit('mode-change')">
          <option value="CONTINUOUS">整个 B–C 有效期连续生效</option>
          <option value="DAILY">Daily</option>
          <option value="WEEKDAYS">Weekdays</option>
          <option value="DATES">Dates</option>
        </select>
      </label>
    </div>

    <template v-if="form.scheduleMode !== 'CONTINUOUS'">
      <article
        v-for="(entry, index) in entries"
        :key="index"
        class="schedule-row"
      >
        <header>
          <b>Timesheet {{ index + 1 }}</b>
          <button
            type="button"
            class="link delete"
            :disabled="entries.length === 1"
            @click="$emit('remove-entry', index)"
          >
            删除
          </button>
        </header>

        <div class="group-grid schedule-entry">
          <template v-if="form.scheduleMode === 'DATES'">
            <label
              >开始日期 *<input v-model="entry.startDate" type="date"
            /></label>
            <label>
              结束日期
              <input
                v-model="entry.endDate"
                type="date"
                :placeholder="entry.startDate"
              />
            </label>
          </template>

          <template v-if="form.scheduleMode === 'WEEKDAYS'">
            <label class="inline-check">
              <input
                v-model="entry.weekdayContinuous"
                type="checkbox"
                @change="$emit('weekday-range-change', entry)"
              />
              <span>连续</span>
            </label>
            <label>
              {{ entry.weekdayContinuous ? "开始星期" : "星期" }} *
              <select
                v-model="entry.day"
                @change="$emit('weekday-start-change', entry)"
              >
                <option
                  v-for="day in weekdayRangeStarts(entry)"
                  :key="day[0]"
                  :value="day[0]"
                >
                  {{ day[1] }}
                </option>
              </select>
            </label>
            <label v-if="entry.weekdayContinuous">
              结束星期 *
              <select v-model="entry.dayTil">
                <option
                  v-for="day in weekdayRangeEnds(entry)"
                  :key="day[0]"
                  :value="day[0]"
                >
                  {{ day[1] }}
                </option>
              </select>
            </label>
          </template>

          <label
            >开始时间 *<input v-model="entry.startTime" type="time"
          /></label>
          <label>
            结束时间 *
            <input
              v-model="entry.endTime"
              type="time"
              :disabled="entry.endOfDay"
            />
          </label>
          <label class="inline-check">
            <input v-model="entry.endOfDay" type="checkbox" />
            <span>结束于当天 24:00</span>
          </label>
        </div>
      </article>

      <div class="add-restriction">
        <button type="button" class="primary" @click="$emit('add-entry')">
          ＋ 添加 Timesheet
        </button>
      </div>

      <div v-if="form.scheduleMode !== 'DATES'" class="limit-section">
        <b>排除日期</b>
        <div
          v-for="(date, index) in excludedDates"
          :key="index"
          class="excluded-date"
        >
          <input v-model="excludedDates[index]" type="date" />
          <button
            type="button"
            class="link delete"
            @click="$emit('remove-excluded-date', index)"
          >
            删除
          </button>
        </div>
        <button type="button" class="link" @click="$emit('add-excluded-date')">
          ＋ 添加排除日期
        </button>
      </div>

      <label class="schedule-note">
        Schedule note
        <input
          :value="note"
          placeholder="可选；编码为 propertyName=timeInterval"
          @input="$emit('update:note', $event.target.value)"
        />
      </label>
      <div class="schedule-item-d" :class="{ error: draftText.length > 204 }">
        <b>Item D 预览</b>
        <code>{{ draftText }}</code>
        <span>{{ Math.max(0, draftText.length - 4) }} / 200</span>
      </div>
      <small>
        仅支持明确的绝对 startTime/endTime；不支持 SR/SS、Sunrise/Sunset
        或相对时间。
      </small>
    </template>
    <small v-else>连续模式不会生成 Timesheet。</small>
  </div>
</template>
