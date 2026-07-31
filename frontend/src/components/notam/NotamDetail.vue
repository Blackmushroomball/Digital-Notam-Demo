<script setup>
defineProps({
  notam: {
    type: Object,
    required: true,
  },
  xml: {
    type: String,
    default: "",
  },
  cnotam: {
    type: String,
    default: "",
  },
  qLine: {
    type: Function,
    required: true,
  },
  compactDateTime: {
    type: Function,
    required: true,
  },
  scheduleText: {
    type: Function,
    required: true,
  },
  itemF: {
    type: Function,
    required: true,
  },
  itemG: {
    type: Function,
    required: true,
  },
  formatDateTime: {
    type: Function,
    required: true,
  },
});

defineEmits(["remove", "copy", "edit", "publish"]);
</script>

<template>
  <section class="detail">
    <div class="panel summary">
      <div>
        <span class="pill" :class="notam.status.toLowerCase()">
          {{ notam.status === "DRAFT" ? "草稿" : "已发布" }}
        </span>
        <h2>{{ notam.title }}</h2>
        <p>{{ notam.number }} · {{ notam.airport }}</p>
      </div>
      <div class="summary-actions">
        <button class="danger" @click="$emit('remove', notam)">删除通告</button>
        <button v-if="notam.status === 'DRAFT'" @click="$emit('copy', notam)">
          复制草稿
        </button>
        <button v-if="notam.status === 'DRAFT'" @click="$emit('edit', notam)">
          继续编辑
        </button>
        <button
          v-if="notam.status === 'DRAFT'"
          class="primary"
          @click="$emit('publish', notam)"
        >
          发布通告
        </button>
      </div>
    </div>

    <section class="panel notam-card">
      <h3>NOTAM</h3>
      <div class="notam-fields">
        <div class="field-row q">
          <b>Q)</b><span>{{ qLine(notam) }}</span>
        </div>
        <div class="field-row">
          <b>A)</b><span>{{ notam.airport }}</span>
        </div>
        <div class="field-row">
          <b>B)</b><span>{{ compactDateTime(notam.effectiveStart) }} UTC</span>
        </div>
        <div class="field-row">
          <b>C)</b><span>{{ compactDateTime(notam.effectiveEnd) }} UTC</span>
        </div>
        <div class="field-row">
          <b>D)</b><span>{{ scheduleText(notam) }}</span>
        </div>
        <div class="field-row">
          <b>E)</b><span>{{ notam.condition }}</span>
        </div>
        <div class="field-row">
          <b>F)</b><span>{{ itemF(notam) }}</span>
        </div>
        <div class="field-row">
          <b>G)</b><span>{{ itemG(notam) }}</span>
        </div>
      </div>
    </section>

    <div class="cols">
      <section class="panel info">
        <h3>通告信息</h3>
        <dl>
          <div>
            <dt>场景 / 航空要素</dt>
            <dd>{{ notam.scenario }} / {{ notam.featureType }}</dd>
          </div>
          <div>
            <dt>有效期</dt>
            <dd>
              {{ formatDateTime(notam.effectiveStart) }} 至<br />
              {{ formatDateTime(notam.effectiveEnd) }}
            </dd>
          </div>
          <div>
            <dt>坐标</dt>
            <dd>
              {{ notam.latitude }}, {{ notam.longitude }} /
              {{ notam.radiusNm }} NM
            </dd>
          </div>
        </dl>
        <h3>CNOTAM转换结果</h3>
        <pre class="cnotam">{{ cnotam }}</pre>
      </section>
      <section class="panel xml">
        <h3>AIXM 5.1.1 XML预览</h3>
        <pre>{{ xml }}</pre>
      </section>
    </div>
  </section>
</template>
