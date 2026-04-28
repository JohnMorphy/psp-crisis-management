/**
 * Custom rollup config for the GisMap widget.
 *
 * The pluggable-widgets-tools rollup pipeline uses acorn (plain JS parser)
 * in the commonjs plugin's resolver phase. This runs BEFORE the TypeScript
 * plugin transforms files, which means raw .ts/.tsx source from @psp/shared
 * (resolved via npm workspace symlink) causes parse errors.
 *
 * Fix: inject a babel transform plugin early in each config bundle's plugin
 * list to strip TypeScript/JSX syntax from @psp/shared files before the
 * commonjs resolver sees them.
 */

const { getBabelInputPlugin } = require('@rollup/plugin-babel')

/** Babel plugin that strips TypeScript+JSX from @psp/shared source files */
function sharedTsPlugin() {
  return getBabelInputPlugin({
    babelrc: false,
    babelHelpers: 'bundled',
    extensions: ['.ts', '.tsx'],
    include: ['**/shared/src/**'],
    presets: [
      ['@babel/preset-typescript', { allExtensions: true, isTSX: true }],
      ['@babel/preset-react', { runtime: 'automatic' }],
    ],
  })
}

module.exports = function (args) {
  const defaultConfig = args.configDefaultConfig

  return defaultConfig.map(config => ({
    ...config,
    plugins: [sharedTsPlugin(), ...(config.plugins || [])],
  }))
}
