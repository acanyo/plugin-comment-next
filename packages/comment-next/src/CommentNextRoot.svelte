<script lang="ts">
  import { onMount } from "svelte";
  import CommentNextBox from "./CommentNextBox.svelte";
  import CommentNextCommentList from "./CommentNextCommentList.svelte";
  import { fetchPluginConfig, type CommentNextPluginConfig } from "./services/config";

  let {
    baseUrl = "",
    group = "",
    kind = "",
    version = "v1alpha1",
    name = "",
    loggedIn = false,
    allowAnonymous = true,
    showCaptcha = false,
    enablePrivate = false,
    loading = false,
    submitting = false,
    commandMenuOpen = false,
    inlineSuggestion = false,
    selectionTools = false,
    placeholder = "写下你的评论...",
    showComments = true,
    demoData = false,
    pageSize = 20,
    replySize = 10,
    withReplies = true,
  }: {
    baseUrl?: string;
    group?: string;
    kind?: string;
    version?: string;
    name?: string;
    loggedIn?: boolean;
    allowAnonymous?: boolean;
    showCaptcha?: boolean;
    enablePrivate?: boolean;
    loading?: boolean;
    submitting?: boolean;
    commandMenuOpen?: boolean;
    inlineSuggestion?: boolean;
    selectionTools?: boolean;
    placeholder?: string;
    showComments?: boolean;
    demoData?: boolean;
    pageSize?: number;
    replySize?: number;
    withReplies?: boolean;
  } = $props();

  let pluginConfig = $state<CommentNextPluginConfig | undefined>();
  let configLoaded = $state(false);
  let configFailed = $state(false);

  const configReady = $derived(demoData || configLoaded || configFailed);
  const basicConfig = $derived(pluginConfig?.basic);
  const securityConfig = $derived(pluginConfig?.security);
  const editorConfig = $derived(pluginConfig?.editor);
  const badgeConfig = $derived(pluginConfig?.badge);
  const resolvedShowCaptcha = $derived(securityConfig?.captcha?.anonymousCommentCaptcha ?? showCaptcha);
  const resolvedEnablePrivate = $derived(basicConfig?.enablePrivateComment ?? enablePrivate);
  const resolvedPlaceholder = $derived(editorConfig?.placeholder?.trim() || placeholder);
  const resolvedWithReplies = $derived(basicConfig?.withReplies ?? withReplies);
  const resolvedPageSize = $derived(resolvePositiveNumber(basicConfig?.size, pageSize));
  const resolvedReplySize = $derived(
    resolvePositiveNumber(resolvedWithReplies ? basicConfig?.withReplySize || basicConfig?.replySize : basicConfig?.replySize, replySize)
  );

  onMount(() => {
    if (demoData) {
      configLoaded = true;
      return;
    }

    let cancelled = false;

    fetchPluginConfig(baseUrl)
      .then((config) => {
        if (!cancelled) {
          pluginConfig = config;
          configLoaded = true;
        }
      })
      .catch((error) => {
        console.error(error);
        if (!cancelled) {
          configFailed = true;
        }
      });

    return () => {
      cancelled = true;
    };
  });

  function resolvePositiveNumber(value: number | undefined, fallback: number): number {
    const normalizedValue = Number(value);

    if (!Number.isFinite(normalizedValue) || normalizedValue <= 0) {
      return fallback;
    }

    return normalizedValue;
  }
</script>

<div class="comment-next-root">
  <CommentNextBox
    {baseUrl}
    {group}
    {kind}
    {version}
    {name}
    {loggedIn}
    {allowAnonymous}
    showCaptcha={resolvedShowCaptcha}
    enablePrivate={resolvedEnablePrivate}
    loading={loading || !configReady}
    {submitting}
    {commandMenuOpen}
    {inlineSuggestion}
    {selectionTools}
    placeholder={resolvedPlaceholder}
  />

  {#if showComments && configReady}
    <CommentNextCommentList
      {baseUrl}
      {group}
      {kind}
      {version}
      {name}
      {demoData}
      pageSize={resolvedPageSize}
      replySize={resolvedReplySize}
      withReplies={resolvedWithReplies}
      {badgeConfig}
    />
  {/if}
</div>

<style>
  .comment-next-root {
    display: grid;
    width: 100%;
    gap: 1rem;
  }
</style>
