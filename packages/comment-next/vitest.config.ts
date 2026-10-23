import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    projects: [
      {
        extends: './vite.config.ts',
        test: {
          name: 'unit',
          include: ['src/**/*.test.ts'],
          exclude: ['src/**/*.svelte.test.ts'],
        },
      },
      {
        extends: './vite.config.ts',
        resolve: { conditions: ['browser'] },
        test: {
          name: 'component',
          environment: 'jsdom',
          include: ['src/**/*.svelte.test.ts'],
        },
      },
    ],
  },
});
