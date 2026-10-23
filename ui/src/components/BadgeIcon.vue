<script lang="ts" setup>
import { Icon } from '@iconify/vue';
import { computed } from 'vue';
import { badgeIconImage } from '../../../shared/badge-icon-image';

const props = defineProps<{ icon?: string; color?: string }>();
const src = computed(() => badgeIconImage(props.icon, props.color));
</script>

<template>
  <img v-if="src" :src="src" alt="" aria-hidden="true" style="object-fit: contain" />
  <Icon v-else-if="icon" :icon="icon" aria-hidden="true" />
</template>
