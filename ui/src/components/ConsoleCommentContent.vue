<script lang="ts" setup>
import { axiosInstance } from '@halo-dev/api-client';
import '@xhhao/comment-next';
import '@xhhao/comment-next/comment-next.css';
import { onMounted, ref } from 'vue';

type AllowedLottieHost = {
  host?: string;
};

type CommentNextConfig = {
  emote?: {
    allowedHosts?: AllowedLottieHost[];
  };
};

const CONFIG_ENDPOINT = '/apis/api.commentnext.xhhao.com/v1alpha1/config';

let allowedLottieHostsRequest: Promise<AllowedLottieHost[]> | undefined;

defineProps<{
  content: string;
}>();

const allowedLottieHosts = ref<AllowedLottieHost[]>([]);

onMounted(() => {
  void loadAllowedLottieHosts()
    .then((hosts) => {
      allowedLottieHosts.value = hosts;
    })
    .catch((error: unknown) => {
      console.error('Failed to load allowed Lottie hosts', error);
    });
});

function loadAllowedLottieHosts(): Promise<AllowedLottieHost[]> {
  if (!allowedLottieHostsRequest) {
    allowedLottieHostsRequest = axiosInstance
      .get<CommentNextConfig>(CONFIG_ENDPOINT)
      .then(({ data }) => data.emote?.allowedHosts ?? [])
      .catch((error: unknown) => {
        allowedLottieHostsRequest = undefined;
        throw error;
      });
  }

  return allowedLottieHostsRequest;
}
</script>

<template>
  <div class="comment-next-console-comment-content">
    <comment-next-content
      :content="content"
      :allowImages="false"
      :allowedLottieHosts="allowedLottieHosts"
    ></comment-next-content>
  </div>
</template>

<style scoped>
.comment-next-console-comment-content {
  display: block;
  min-width: 0;
  max-width: 100%;
}

.comment-next-console-comment-content comment-next-content {
  display: block;
  min-width: 0;
  max-width: 100%;
}
</style>
