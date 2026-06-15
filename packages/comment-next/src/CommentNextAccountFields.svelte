<script lang="ts">
  import CommentNextAvatar from "./CommentNextAvatar.svelte";
  import CommentNextIcon from "./CommentNextIcon.svelte";

  let {
    visible = true,
    loggedIn = false,
    avatarUrl = "",
    avatarAlt = "",
    userDisplayName = "",
    displayName = "",
    email = "",
    website = "",
    onChange = () => {},
  }: {
    visible?: boolean;
    loggedIn?: boolean;
    avatarUrl?: string;
    avatarAlt?: string;
    userDisplayName?: string;
    displayName?: string;
    email?: string;
    website?: string;
    onChange?: (values: { displayName: string; email: string; website: string }) => void;
  } = $props();

  function updateField(field: "displayName" | "email" | "website", value: string) {
    onChange({
      displayName,
      email,
      website,
      [field]: value,
    });
  }
</script>

{#if visible}
  <div class:comment-next-account-fields-logged-in={loggedIn} class="comment-next-account-fields" aria-label="评论身份">
    <div class="comment-next-account-avatar">
      <CommentNextAvatar src={avatarUrl} alt={avatarAlt || displayName || userDisplayName} size={34} />
    </div>

    {#if loggedIn}
      <div class="comment-next-account-current-user">
        <span class="comment-next-account-current-label">当前账号</span>
        <span class="comment-next-account-current-name">{userDisplayName || "已登录用户"}</span>
      </div>
    {:else}
      <label class="comment-next-field">
        <span class="comment-next-field-icon" aria-hidden="true">
          <CommentNextIcon name="user" size={16} />
        </span>
        <input
          name="displayName"
          type="text"
          placeholder="昵称"
          autocomplete="name"
          value={displayName}
          required
          oninput={(event) => updateField("displayName", event.currentTarget.value)}
        />
      </label>
      <label class="comment-next-field">
        <span class="comment-next-field-icon" aria-hidden="true">
          <CommentNextIcon name="mail" size={16} />
        </span>
        <input
          name="email"
          type="email"
          placeholder="邮箱"
          autocomplete="email"
          value={email}
          required
          oninput={(event) => updateField("email", event.currentTarget.value)}
        />
      </label>
      <label class="comment-next-field">
        <span class="comment-next-field-icon" aria-hidden="true">
          <CommentNextIcon name="globe" size={16} />
        </span>
        <input
          name="website"
          type="url"
          placeholder="网站（可选）"
          autocomplete="url"
          value={website}
          oninput={(event) => updateField("website", event.currentTarget.value)}
        />
      </label>
    {/if}
  </div>
{/if}

<style>
  .comment-next-account-fields {
    display: grid;
    grid-template-columns: auto repeat(3, minmax(0, 1fr));
    align-items: center;
    gap: 0;
    box-sizing: border-box;
    padding: 0.625rem 0.875rem;
    border-bottom: 1px solid var(--comment-next-border-subtle-color, #e0e6ee);
    border-radius: var(--comment-next-radius-lg, 0.875rem) var(--comment-next-radius-lg, 0.875rem) 0 0;
    background: var(
      --comment-next-account-fields-bg,
      linear-gradient(180deg, rgb(252 253 253 / 0.98), rgb(249 251 251 / 0.98))
    );
  }

  .comment-next-account-fields-logged-in {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .comment-next-account-avatar {
    display: flex;
    align-items: center;
    justify-content: center;
    padding-right: 0.75rem;
  }

  .comment-next-account-current-user {
    display: flex;
    min-width: 0;
    align-items: baseline;
    gap: 0.5rem;
  }

  .comment-next-account-current-label {
    color: var(--comment-next-muted-color, #6b7687);
    font-size: 0.78125rem;
    font-weight: 620;
    white-space: nowrap;
  }

  .comment-next-account-current-name {
    min-width: 0;
    overflow: hidden;
    color: var(--comment-next-text-color, #172033);
    font-size: 0.9375rem;
    font-weight: 760;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .comment-next-field {
    position: relative;
    display: flex;
    align-items: center;
    min-width: 0;
    height: 2.25rem;
    gap: 0.625rem;
    box-sizing: border-box;
    padding: 0 0.875rem;
    border: 0;
    border-radius: 0.625rem;
    background: transparent;
    color: var(--comment-next-muted-color, #6b7687);
    transition:
      box-shadow 140ms ease,
      background-color 140ms ease,
      color 140ms ease;
  }

  .comment-next-field + .comment-next-field::before {
    position: absolute;
    top: 0.45rem;
    bottom: 0.45rem;
    left: 0;
    width: 1px;
    background: var(--comment-next-field-divider-color, #dbe4ed);
    content: "";
    transition: opacity 140ms ease;
  }

  .comment-next-field:focus-within {
    background: var(--comment-next-field-focus-bg-color, #ffffff);
    box-shadow:
      0 6px 14px rgb(15 23 42 / 0.05),
      0 0 0 1px var(--comment-next-primary-color, rgb(59, 130, 246)) inset;
    color: var(--comment-next-primary-color, rgb(59, 130, 246));
  }

  .comment-next-field:focus-within::before,
  .comment-next-field:focus-within + .comment-next-field::before {
    opacity: 0;
  }

  .comment-next-field-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 1.125rem;
    color: currentColor;
    opacity: 0.92;
  }

  .comment-next-field input {
    min-width: 0;
    width: 100%;
    border: 0;
    outline: 0;
    background: transparent;
    color: var(--comment-next-text-color, #172033);
    font: inherit;
    font-size: 0.90625rem;
    font-weight: 500;
  }

  .comment-next-field input::placeholder {
    color: var(--comment-next-placeholder-color, #8a95a5);
    font-weight: 500;
  }

  @media (max-width: 720px) {
    .comment-next-account-fields {
      grid-template-columns: auto minmax(0, 1fr);
      gap: 0.125rem;
      padding: 0.625rem;
    }

    .comment-next-account-fields:not(.comment-next-account-fields-logged-in) .comment-next-account-avatar {
      grid-row: span 3;
      align-self: start;
      padding-top: 0.25rem;
    }

    .comment-next-account-fields:not(.comment-next-account-fields-logged-in) .comment-next-field {
      grid-column: 2;
    }

    .comment-next-field + .comment-next-field::before {
      top: -0.0625rem;
      right: 0.75rem;
      bottom: auto;
      left: 0.75rem;
      width: auto;
      height: 1px;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-field {
      transition: none;
    }
  }
</style>
