<script setup>
const props = defineProps({
  view: {
    type: String,
    required: true,
  },
  editing: {
    type: Boolean,
    default: false,
  },
});

defineEmits(["import-xml", "create"]);

function pageTitle() {
  if (props.view === "create") {
    return props.editing ? "编辑数字通告" : "创建数字通告";
  }
  return props.view === "detail" ? "通告详情" : "数字通告管理";
}
</script>

<template>
  <header>
    <div>
      <h1>{{ pageTitle() }}</h1>
      <p>航空情报数字化发布与管理演示平台</p>
    </div>
    <div class="summary-actions">
      <label class="danger">
        导入XML
        <input
          type="file"
          accept=".xml,application/xml"
          hidden
          @change="$emit('import-xml', $event)"
        />
      </label>
      <button class="primary" @click="$emit('create')">＋ 新建通告</button>
    </div>
  </header>
</template>
