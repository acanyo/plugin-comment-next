<svelte:options
  customElement={{
    tag: "comment-widget",
    props: {
      baseUrl: { reflect: true, type: "String", attribute: "base-url" },
      group: { reflect: true, type: "String", attribute: "group" },
      kind: { reflect: true, type: "String", attribute: "kind" },
      version: { reflect: true, type: "String", attribute: "version" },
      name: { reflect: true, type: "String", attribute: "name" },
      loggedIn: { reflect: true, type: "Boolean", attribute: "logged-in" },
      allowAnonymous: { reflect: true, type: "Boolean", attribute: "allow-anonymous" },
      showCaptcha: { reflect: true, type: "Boolean", attribute: "show-captcha" },
      enablePrivate: { reflect: true, type: "Boolean", attribute: "enable-private" },
      loading: { reflect: true, type: "Boolean", attribute: "loading" },
      submitting: { reflect: true, type: "Boolean", attribute: "submitting" },
      commandMenuOpen: { reflect: true, type: "Boolean", attribute: "command-menu-open" },
      inlineSuggestion: { reflect: true, type: "Boolean", attribute: "inline-suggestion" },
      selectionTools: { reflect: true, type: "Boolean", attribute: "selection-tools" },
      placeholder: { reflect: true, type: "String", attribute: "placeholder" },
      showComments: { reflect: true, type: "Boolean", attribute: "show-comments" },
      demoData: { reflect: true, type: "Boolean", attribute: "demo-data" },
      pageSize: { reflect: true, type: "Number", attribute: "page-size" },
      replySize: { reflect: true, type: "Number", attribute: "reply-size" },
      withReplies: { reflect: true, type: "Boolean", attribute: "with-replies" },
    },
  }}
/>

<script lang="ts">
import CommentNextRoot from './CommentNextRoot.svelte';

const {
  baseUrl = '',
  group = '',
  kind = '',
  version = 'v1alpha1',
  name = '',
  loggedIn = false,
  allowAnonymous = true,
  showCaptcha = false,
  enablePrivate = false,
  loading = false,
  submitting = false,
  commandMenuOpen = false,
  inlineSuggestion = false,
  selectionTools = false,
  placeholder = '写下你的评论...',
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
</script>

<CommentNextRoot
  {baseUrl}
  {group}
  {kind}
  {version}
  {name}
  {loggedIn}
  {allowAnonymous}
  {showCaptcha}
  {enablePrivate}
  {loading}
  {submitting}
  {commandMenuOpen}
  {inlineSuggestion}
  {selectionTools}
  {placeholder}
  {showComments}
  {demoData}
  {pageSize}
  {replySize}
  {withReplies}
/>

<style>
  :host {
    --at-apply: block w-full;
  }
</style>
