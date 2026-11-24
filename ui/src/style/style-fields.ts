import type { CommentNextStyle } from './style-api';

const fields = [
  { key: 'lightAccent', label: '浅色主色' },
  { key: 'darkAccent', label: '深色主色' },
  { key: 'radius', label: '圆角' },
  { key: 'density', label: '评论间距' },
  { key: 'listStyle', label: '列表样式' },
] as const;

export const densityOptions = [
  { value: null, label: '标准' },
  { value: 'compact', label: '紧凑' },
  { value: 'spacious', label: '宽松' },
] as const;

export const listStyleOptions = [
  { value: 'flat', label: '分隔线' },
  { value: 'cards', label: '卡片' },
] as const;

export function changedStyleFields(
  before: CommentNextStyle,
  after: CommentNextStyle
) {
  return fields
    .filter(({ key }) => value(before, key) !== value(after, key))
    .map(({ key, label }) => ({
      key,
      label,
      before: format(key, before),
      after: format(key, after),
    }));
}

function value(style: CommentNextStyle, key: keyof CommentNextStyle) {
  return style[key] ?? (key === 'listStyle' ? 'flat' : null);
}

function format(key: keyof CommentNextStyle, style: CommentNextStyle): string {
  if (key === 'density')
    return (
      densityOptions.find((option) => option.value === (style.density ?? null))
        ?.label ?? '标准'
    );
  if (key === 'listStyle')
    return style.listStyle === 'cards' ? '卡片' : '分隔线';
  if (key === 'radius' && style.radius != null) return `${style.radius} px`;
  return String(style[key] ?? '跟随主题');
}
