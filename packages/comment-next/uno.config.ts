import { defineConfig, presetIcons, presetWind3 } from "unocss";

export default defineConfig({
  presets: [
    presetWind3(),
    presetIcons({
      warn: true,
    }),
  ],
  shortcuts: {
    "bg-comment-next": "bg-[var(--comment-next-bg-color,#fff)]",
    "border-comment-next": "border-[var(--comment-next-border-color,#d8dee7)]",
    "text-comment-next": "text-[var(--comment-next-text-color,#172033)]",
    "text-comment-next-muted": "text-[var(--comment-next-muted-color,#6b7687)]",
  },
});
