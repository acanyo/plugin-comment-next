<script lang="ts">
  import { onMount } from "svelte";
  import { getAnonymousAvatarUrl, getDefaultAnonymousAvatarUrl } from "./avatar/weavatar";
  import CommentNextAccountFields from "./CommentNextAccountFields.svelte";
  import CommentNextEditor from "./CommentNextEditor.svelte";
  import CommentNextFooter from "./CommentNextFooter.svelte";
  import CommentNextSkeleton from "./CommentNextSkeleton.svelte";
  import { fetchCurrentUser, type CurrentUser } from "./services/current-user";

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
  } = $props();

  let isAiPanelOpen = $state(false);
  let showInlineSuggestion = $state(false);
  let showSelectionTools = $state(false);
  let aiMode = $state("polish");
  let anonymousDisplayName = $state("");
  let anonymousEmail = $state("");
  let anonymousWebsite = $state("");
  let captchaCode = $state("");
  let currentUser = $state<CurrentUser | undefined>();
  let anonymousAvatarUrl = $state(getDefaultAnonymousAvatarUrl());

  const subject = $derived([group, version, kind, name].filter(Boolean).join("/"));
  const isLoggedIn = $derived(Boolean(currentUser) || loggedIn);
  const showAccountBar = $derived(isLoggedIn || allowAnonymous);
  const currentUserDisplayName = $derived(currentUser?.displayName || (loggedIn ? "已登录用户" : ""));
  const currentUserAvatar = $derived(currentUser?.avatar || "");
  const anonymousMissingRequired = $derived(
    !isLoggedIn && allowAnonymous && (!anonymousDisplayName.trim() || !anonymousEmail.trim())
  );
  const captchaRequired = $derived(!isLoggedIn && allowAnonymous && showCaptcha);
  const captchaMissingRequired = $derived(captchaRequired && !captchaCode.trim());
  const submitDisabledReason = $derived(
    anonymousMissingRequired ? "请先填写昵称和邮箱" : captchaMissingRequired ? "请先填写验证码" : ""
  );

  $effect(() => {
    isAiPanelOpen = commandMenuOpen;
    showInlineSuggestion = inlineSuggestion;
    showSelectionTools = selectionTools;
  });

  $effect(() => {
    if (!captchaRequired) {
      captchaCode = "";
    }
  });

  $effect(() => {
    const email = anonymousEmail;
    let cancelled = false;

    getAnonymousAvatarUrl(email).then((avatarUrl) => {
      if (!cancelled) {
        anonymousAvatarUrl = avatarUrl;
      }
    }).catch(() => {
      if (!cancelled) {
        anonymousAvatarUrl = getDefaultAnonymousAvatarUrl();
      }
    });

    return () => {
      cancelled = true;
    };
  });

  onMount(() => {
    let cancelled = false;

    fetchCurrentUser(baseUrl)
      .then((user) => {
        if (!cancelled) {
          currentUser = user;
        }
      })
      .catch(() => {
        if (!cancelled) {
          currentUser = undefined;
        }
      });

    return () => {
      cancelled = true;
    };
  });

  function handleLogin() {
    window.location.href = `/login?redirect_uri=${encodeURIComponent(window.location.pathname + getSubjectDomId())}`;
  }

  function getSubjectDomId() {
    const subjectDomId = [group.replaceAll(".", "-"), kind, name].filter(Boolean).join("-").replaceAll(/-+/g, "-");

    return subjectDomId ? `#comment-${subjectDomId}` : window.location.hash;
  }
</script>

<form
  class:comment-next-box-loading={loading}
  class:comment-next-box-ai-active={isAiPanelOpen || showInlineSuggestion || showSelectionTools}
  class="comment-next-box"
  data-base-url={baseUrl}
  data-subject={subject}
>
  {#if loading}
    <CommentNextSkeleton showAccountFields={showAccountBar} loggedIn={isLoggedIn} {showCaptcha} {enablePrivate} />
  {:else}
    <CommentNextAccountFields
      visible={showAccountBar}
      loggedIn={isLoggedIn}
      avatarUrl={isLoggedIn ? currentUserAvatar : anonymousAvatarUrl}
      avatarAlt={isLoggedIn ? currentUserDisplayName : anonymousDisplayName}
      userDisplayName={currentUserDisplayName}
      displayName={anonymousDisplayName}
      email={anonymousEmail}
      website={anonymousWebsite}
      onChange={(values) => {
        anonymousDisplayName = values.displayName;
        anonymousEmail = values.email;
        anonymousWebsite = values.website;
      }}
    />
    <CommentNextEditor
      {placeholder}
      aiOpen={isAiPanelOpen}
      inlineSuggestion={showInlineSuggestion}
      selectionTools={showSelectionTools}
      {aiMode}
      topRounded={!showAccountBar}
      onCloseAiPanel={() => (isAiPanelOpen = false)}
      onModeSelect={(mode) => {
        aiMode = mode;
        isAiPanelOpen = false;
        showInlineSuggestion = true;
        showSelectionTools = false;
      }}
    />
    <CommentNextFooter
      {baseUrl}
      commandMenuOpen={isAiPanelOpen}
      loggedIn={isLoggedIn}
      {allowAnonymous}
      {enablePrivate}
      {showCaptcha}
      {captchaCode}
      {submitting}
      submitDisabled={anonymousMissingRequired || captchaMissingRequired}
      submitDisabledReason={submitDisabledReason}
      onCaptchaChange={(value) => {
        captchaCode = value;
      }}
      onLogin={handleLogin}
      onToggleCommandMenu={() => {
        isAiPanelOpen = !isAiPanelOpen;
        showInlineSuggestion = false;
        showSelectionTools = false;
      }}
    />
  {/if}
</form>

<style>
  .comment-next-box {
    position: relative;
    width: 100%;
    overflow: hidden;
    box-sizing: border-box;
    border: 1px solid var(--comment-next-border-color, #d5dde7);
    border-radius: var(--comment-next-radius-lg, 0.875rem);
    background: var(
      --comment-next-box-bg,
      linear-gradient(180deg, rgb(255 255 255 / 0.98), rgb(250 252 252 / 0.98)),
      var(--comment-next-bg-color, #ffffff)
    );
    color: var(--comment-next-text-color, #172033);
    font-family: var(
      --comment-next-font-family,
      ui-sans-serif,
      system-ui,
      -apple-system,
      BlinkMacSystemFont,
      "Segoe UI",
      sans-serif
    );
    box-shadow:
      0 18px 38px rgb(15 23 42 / 0.06),
      0 1px 0 rgb(255 255 255 / 0.85) inset;
    transition:
      border-color 140ms ease,
      box-shadow 140ms ease,
      transform 140ms ease;
  }

  .comment-next-box:focus-within {
    border-color: var(--comment-next-focus-border-color, #aeb9c6);
    box-shadow:
      0 18px 42px rgb(15 23 42 / 0.08),
      0 0 0 3px var(--comment-next-focus-shadow-color, rgb(59 130 246 / 0.14));
  }

  .comment-next-box-ai-active {
    border-color: var(--comment-next-ai-border-color, rgb(191 219 254));
  }

  .comment-next-box-loading {
    overflow: hidden;
    pointer-events: none;
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-box {
      transition: none;
    }
  }
</style>
