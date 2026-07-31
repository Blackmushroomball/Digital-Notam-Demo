<script setup>
defineProps({
  view: {
    type: String,
    required: true,
  },
});

defineEmits(["show-list", "create"]);
</script>

<template>
  <aside>
    <div class="brand">
      <span class="mark">DN</span>
      <div>
        <b>Digital NOTAM</b>
        <small>AIXM 5.1.1</small>
      </div>
    </div>

    <nav>
      <button
        :class="{ active: view === 'list' || view === 'detail' }"
        @click="$emit('show-list')"
      >
        ▦ 通告管理
      </button>
      <button :class="{ active: view === 'create' }" @click="$emit('create')">
        ＋ 创建通告
      </button>
    </nav>

    <div class="standard">
      <small>数据标准</small>
      <strong>AIXM 5.1.1</strong>
      <span>演示环境 · 北京时间</span>
    </div>
  </aside>
</template>
