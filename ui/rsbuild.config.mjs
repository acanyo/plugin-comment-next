import { rsbuildConfig } from '@halo-dev/ui-plugin-bundler-kit/rsbuild';
import { UnoCSSRspackPlugin } from '@unocss/webpack/rspack';

const MANIFEST_PATH = '../src/main/resources/plugin.yaml';
const OUT_DIR_PROD = '../src/main/resources/ui';
const OUT_DIR_DEV = '../build/resources/main/ui';
const COMMENT_ELEMENTS = new Set([
  'comment-next',
  'comment-next-content',
  'comment-widget',
  'comment-ranking',
  'comment-ranking-sidebar',
]);

export default rsbuildConfig({
  manifestPath: MANIFEST_PATH,
  vue: {
    vueLoaderOptions: {
      compilerOptions: {
        isCustomElement: (tag) => COMMENT_ELEMENTS.has(tag),
      },
    },
  },
  rsbuild: ({ envMode }) => {
    const outDir = envMode === 'production' ? OUT_DIR_PROD : OUT_DIR_DEV;

    return {
      resolve: {
        alias: {
          '@': './src',
        },
      },
      tools: {
        rspack: {
          plugins: [UnoCSSRspackPlugin()],
          optimization: {
            // Keep Svelte's DOM globals separate from Halo editor exports such as Node.
            concatenateModules: false,
          },
        },
      },
      output: {
        distPath: {
          root: outDir,
        },
      },
    };
  },
});
