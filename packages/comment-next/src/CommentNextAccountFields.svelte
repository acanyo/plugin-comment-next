<script lang="ts">
const {
  displayName = '',
  email = '',
  website = '',
  disabled = false,
  onChange = () => {},
  onEmailBlur = () => {},
}: {
  displayName?: string;
  email?: string;
  website?: string;
  disabled?: boolean;
  onChange?: (values: {
    displayName: string;
    email: string;
    website: string;
  }) => void;
  onEmailBlur?: (email: string) => void;
} = $props();

function updateField(
  field: 'displayName' | 'email' | 'website',
  value: string
) {
  onChange({
    displayName,
    email,
    website,
    [field]: value,
  });
}
</script>

<fieldset class="comment-next-account-fields" {disabled}>
  <legend class="comment-next-account-legend">访客资料</legend>
  <label class="comment-next-field">
    <span>昵称</span>
    <input
      name="displayName"
      type="text"
      autocomplete="nickname"
      value={displayName}
      required
      pattern=".*\S.*"
      oninput={(event) => updateField('displayName', event.currentTarget.value)}
    />
  </label>
  <label class="comment-next-field">
    <span>邮箱</span>
    <input
      name="email"
      type="email"
      autocomplete="email"
      value={email}
      required
      oninput={(event) => updateField('email', event.currentTarget.value)}
      onblur={(event) => onEmailBlur(event.currentTarget.value)}
    />
  </label>
  <label class="comment-next-field comment-next-field-website">
    <span>网站 <small>选填</small></span>
    <input
      name="website"
      type="url"
      autocomplete="url"
      value={website}
      oninput={(event) => updateField('website', event.currentTarget.value)}
    />
  </label>
</fieldset>

<style>
  .comment-next-account-fields {
    display: grid;
    grid-template-columns: var(--comment-next-guest-fields-columns, repeat(auto-fit, minmax(min(100%, 9rem), 1fr)));
    gap: 0.625rem 0.75rem;
    box-sizing: border-box;
    min-inline-size: 0;
    margin: 0;
    padding: 0;
    border: 0;
    background: var(--comment-next-account-fields-bg, transparent);
  }

  .comment-next-account-legend {
    --at-apply: sr-only;
  }

  .comment-next-field {
    display: grid;
    min-width: 0;
    gap: 0.3125rem;
    color: var(--comment-next-text-color, #172033);
    font-size: 0.8125rem;
    font-weight: 620;
    line-height: 1.35;
  }

  .comment-next-field-website {
    grid-column: var(--comment-next-guest-website-column, auto);
  }

  .comment-next-field small {
    margin-inline-start: 0.125rem;
    color: var(--comment-next-muted-color, #667085);
    font-size: 0.75rem;
    font-weight: 400;
  }

  .comment-next-field input {
    box-sizing: border-box;
    width: 100%;
    min-width: 0;
    min-height: var(--comment-next-composer-control-height);
    padding: 0.375rem 0.625rem;
    border: 1px solid var(--comment-next-field-border-color, var(--comment-next-border-color, #d5dde7));
    border-radius: var(--comment-next-composer-control-radius);
    background: var(--comment-next-field-bg-color, #fff);
    color: inherit;
    font: inherit;
    font-size: 1rem;
    font-weight: 450;
    transition: border-color 140ms ease;
  }

  .comment-next-field input:focus {
    border-color: var(--comment-next-composer-accent);
    outline: 1px solid var(--comment-next-composer-accent);
    background: var(--comment-next-field-focus-bg-color, #fff);
  }

  .comment-next-field input:disabled {
    opacity: 0.65;
  }

  @media (prefers-reduced-motion: reduce) {
    .comment-next-field input {
      transition: none;
    }
  }
</style>
