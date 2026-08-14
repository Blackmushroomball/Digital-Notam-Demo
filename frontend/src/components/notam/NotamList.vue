<script setup>
defineProps({
  stats: {
    type: Object,
    required: true,
  },
  items: {
    type: Array,
    required: true,
  },
  query: {
    type: String,
    required: true,
  },
  status: {
    type: String,
    required: true,
  },
  checkedIds: {
    type: Array,
    required: true,
  },
  allFilteredSelected: {
    type: Boolean,
    required: true,
  },
  formatDateTime: {
    type: Function,
    required: true,
  },
  currentPage: {
    type: Number,
    required: true,
  },
  totalPages: {
    type: Number,
    required: true,
  },
  totalItems: {
    type: Number,
    required: true,
  },
});

defineEmits([
  "update:query",
  "update:status",
  "update:checked-ids",
  "toggle-all",
  "remove-selected",
  "detail",
  "publish",
  "remove",
  "change-page",
]);

function toggleItem(checkedIds, id, checked) {
  const next = new Set(checkedIds);
  checked ? next.add(id) : next.delete(id);
  return [...next];
}
</script>

<template>
  <section class="stats">
    <article>
      <span>全部通告</span>
      <b>{{ stats.all }}</b>
      <i class="blue">总</i>
    </article>
    <article>
      <span>待发布</span>
      <b>{{ stats.draft }}</b>
      <i class="amber">草</i>
    </article>
    <article>
      <span>已发布</span>
      <b>{{ stats.published }}</b>
      <i class="green">发</i>
    </article>
  </section>

  <section class="panel">
    <div class="toolbar">
      <div class="search">
        ⌕
        <input
          :value="query"
          placeholder="搜索编号、机场或标题"
          @input="$emit('update:query', $event.target.value)"
        />
      </div>
      <select
        :value="status"
        @change="$emit('update:status', $event.target.value)"
      >
        <option value="ALL">全部状态</option>
        <option value="DRAFT">草稿</option>
        <option value="PUBLISHED">已发布</option>
      </select>
      <button
        class="danger batch-delete"
        :disabled="!checkedIds.length"
        @click="$emit('remove-selected')"
      >
        批量删除（{{ checkedIds.length }}）
      </button>
    </div>

    <table>
      <thead>
        <tr>
          <th class="select-col">
            <input
              type="checkbox"
              :checked="allFilteredSelected"
              :disabled="!items.length"
              title="全选当前页"
              @change="$emit('toggle-all', $event)"
            />
          </th>
          <th>通告编号</th>
          <th>标题 / 机场</th>
          <th>要素类型</th>
          <th>生效时间（UTC）</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.id">
          <td class="select-col">
            <input
              type="checkbox"
              :checked="checkedIds.includes(item.id)"
              :aria-label="`选择通告 ${item.number}`"
              @change="
                $emit(
                  'update:checked-ids',
                  toggleItem(checkedIds, item.id, $event.target.checked),
                )
              "
            />
          </td>
          <td>
            <b>{{ item.number }}</b>
          </td>
          <td>
            <strong>{{ item.title }}</strong>
            <small>{{ item.airport }} · {{ item.condition }}</small>
          </td>
          <td>{{ item.featureType }}</td>
          <td>
            {{ formatDateTime(item.effectiveStart) }}
            <small>至 {{ formatDateTime(item.effectiveEnd) }}</small>
          </td>
          <td>
            <span class="pill" :class="item.status.toLowerCase()">
              {{ item.status === "DRAFT" ? "草稿" : "已发布" }}
            </span>
          </td>
          <td>
            <button class="link" @click="$emit('detail', item)">查看</button>
            <button
              v-if="item.status === 'DRAFT'"
              class="link publish"
              @click="$emit('publish', item)"
            >
              发布
            </button>
            <button class="link delete" @click="$emit('remove', item)">
              删除
            </button>
          </td>
        </tr>
      </tbody>
    </table>

    <!-- 通告列表分页：显示总数、当前页码以及翻页按钮 -->
    <div class="pagination">
      <span
        >共 {{ totalItems }} 条，第 {{ currentPage }} /
        {{ totalPages }} 页</span
      >

      <div class="pagination-actions">
        <button
          type="button"
          :disabled="currentPage === 1"
          @click="$emit('change-page', currentPage - 1)"
        >
          上一页
        </button>

        <button
          v-for="page in totalPages"
          :key="page"
          type="button"
          :class="{ active: page === currentPage }"
          @click="$emit('change-page', page)"
        >
          {{ page }}
        </button>

        <button
          type="button"
          :disabled="currentPage === totalPages"
          @click="$emit('change-page', currentPage + 1)"
        >
          下一页
        </button>
      </div>
    </div>
  </section>
</template>
