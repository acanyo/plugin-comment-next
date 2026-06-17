import { rsbuildConfig } from '@halo-dev/ui-plugin-bundler-kit';
import { pluginVue } from '@rsbuild/plugin-vue';
import { UnoCSSRspackPlugin } from '@unocss/webpack/rspack';

const MANIFEST_PATH = '../../src/main/resources/plugin.yaml';
const OUT_DIR_PROD = '../../src/main/resources/console';
const OUT_DIR_DEV = '../../build/resources/main/console';

export default rsbuildConfig({
  manifestPath: MANIFEST_PATH,
  rsbuild: ({ envMode }) => {
    const outDir = envMode === 'production' ? OUT_DIR_PROD : OUT_DIR_DEV;

    return {
      resolve: {
        alias: {
          '@': './src',
        },
      },
      plugins: [pluginVue()],
      tools: {
        rspack: {
          plugins: [UnoCSSRspackPlugin()],
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
