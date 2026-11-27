<script lang="ts" setup>
import type { EmoteLocalizationOptions } from '../api/emotes';

const props = withDefaults(
  defineProps<{
    options?: EmoteLocalizationOptions;
    loading?: boolean;
    disabled?: boolean;
    policyName: string;
    attachmentGroupName: string;
    useAbsoluteUrl: boolean;
  }>(),
  {
    options: undefined,
    loading: false,
    disabled: false,
  }
);

const emit = defineEmits<{
  'update:policyName': [value: string];
  'update:attachmentGroupName': [value: string];
  'update:useAbsoluteUrl': [value: boolean];
  refreshOptions: [];
}>();

function handlePolicyChange(event: Event) {
  emit('update:policyName', (event.target as HTMLSelectElement).value);
}

function handleGroupChange(event: Event) {
  emit('update:attachmentGroupName', (event.target as HTMLSelectElement).value);
}

function handleAbsoluteUrlChange(event: Event) {
  emit('update:useAbsoluteUrl', (event.target as HTMLInputElement).checked);
}
</script>

<template>
  <div class=":uno: space-y-3 rounded-lg border border-solid border-gray-200 bg-white p-3.5">
    <div v-if="loading" class=":uno: text-xs text-gray-500">
      正在加载 Halo 附件存储策略与分组…
    </div>

    <template v-else>
      <div class=":uno: grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div>
          <div class=":uno: flex items-center justify-between gap-2">
            <label class=":uno: text-xs font-medium text-gray-700">
              存储策略 <span class=":uno: text-red-500">*</span>
            </label>
            <button
              type="button"
              :disabled="props.disabled"
              class=":uno: cursor-pointer border-0 bg-transparent p-0 text-[11px] font-medium text-blue-600 transition hover:text-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
              @click="emit('refreshOptions')"
            >
              刷新
            </button>
          </div>
          <div class=":uno: relative mt-1.5">
            <select
              :value="props.policyName"
              :disabled="props.disabled || !(props.options?.policies.length)"
              class=":uno: w-full appearance-none rounded-lg border border-solid border-gray-200 bg-white py-2 pl-3 pr-8 text-sm text-gray-900 outline-none transition hover:border-gray-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-gray-50 disabled:text-gray-400"
              @change="handlePolicyChange"
            >
              <option v-if="!(props.options?.policies.length)" value="">
                暂无可用存储策略
              </option>
              <option
                v-for="policy in props.options?.policies ?? []"
                :key="policy.name"
                :value="policy.name"
              >
                {{ policy.displayName }}
              </option>
            </select>
            <svg
              aria-hidden="true"
              class=":uno: pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400"
              viewBox="0 0 20 20"
              fill="currentColor"
            >
              <path
                fill-rule="evenodd"
                d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 11.168l3.71-3.938a.75.75 0 1 1 1.08 1.04l-4.25 4.5a.75.75 0 0 1-1.08 0l-4.25-4.5a.75.75 0 0 1 .02-1.06Z"
                clip-rule="evenodd"
              />
            </svg>
          </div>
        </div>

        <div>
          <label class=":uno: block text-xs font-medium text-gray-700">
            附件分组（可选）
          </label>
          <div class=":uno: relative mt-1.5">
            <select
              :value="props.attachmentGroupName"
              :disabled="props.disabled"
              class=":uno: w-full appearance-none rounded-lg border border-solid border-gray-200 bg-white py-2 pl-3 pr-8 text-sm text-gray-900 outline-none transition hover:border-gray-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-gray-50 disabled:text-gray-400"
              @change="handleGroupChange"
            >
              <option value="">不指定分组（默认）</option>
              <option
                v-for="group in props.options?.attachmentGroups ?? []"
                :key="group.name"
                :value="group.name"
              >
                {{ group.displayName }}
              </option>
            </select>
            <svg
              aria-hidden="true"
              class=":uno: pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400"
              viewBox="0 0 20 20"
              fill="currentColor"
            >
              <path
                fill-rule="evenodd"
                d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 11.168l3.71-3.938a.75.75 0 1 1 1.08 1.04l-4.25 4.5a.75.75 0 0 1-1.08 0l-4.25-4.5a.75.75 0 0 1 .02-1.06Z"
                clip-rule="evenodd"
              />
            </svg>
          </div>
        </div>
      </div>

      <label class=":uno: flex cursor-pointer items-start gap-2 pt-0.5 text-xs text-gray-700">
        <input
          :checked="props.useAbsoluteUrl"
          :disabled="props.disabled"
          type="checkbox"
          class=":uno: mt-0.5"
          @change="handleAbsoluteUrlChange"
        />
        <span class=":uno: leading-5">
          <span class=":uno: font-medium text-gray-800">
            补全为站点完整外部地址（推荐，避免邮件通知裂图）
          </span>
          <span class=":uno: mt-0.5 block text-gray-500">
            <template v-if="props.options?.externalBaseUrl">
              相对路径将自动补全为
              <code class=":uno: rounded bg-gray-100 px-1 py-0.5 font-mono text-[11px] text-gray-700">
                {{ props.options.externalBaseUrl }}
              </code>
              开头的完整地址<template v-if="!props.options.externalUrlConfigured">（当前使用访问域名兜底）</template>。
            </template>
            <template v-else>
              相对路径将自动拼接站点外部地址；对象存储自带完整链接时保持原样。
            </template>
          </span>
        </span>
      </label>

      <p class=":uno: m-0 text-[11px] leading-4 text-gray-400">
        仅处理外链图片（优先存原图并自动去重）；删除分类不会删除附件库中的图片。
      </p>
    </template>
  </div>
</template>
