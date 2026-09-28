// Production bundles are what the Yandex ZIP ships: they carry no source maps (never uploaded,
// and reading the 25 MB of Kotlin maps is slow), and the JavaScript fallback is minified in one
// light Terser pass. Terser's default compression spends half an hour on this bundle for about
// 3 % of its size; mangling plus a light pass finishes in well under a minute.
if (config.mode === "production") {
    config.devtool = false;
    config.module.rules = config.module.rules.filter(
        (rule) => !(Array.isArray(rule.use) && rule.use.includes("source-map-loader")),
    );
    try {
        const TerserPlugin = require("terser-webpack-plugin");
        config.optimization = config.optimization || {};
        config.optimization.minimizer = [
            new TerserPlugin({
                extractComments: false,
                terserOptions: {
                    mangle: true,
                    compress: {
                        passes: 1,
                        reduce_vars: false,
                        reduce_funcs: false,
                        inline: false,
                        collapse_vars: false,
                    },
                },
            }),
        ];
    } catch (e) {
        // Without the plugin webpack keeps its default minimizer.
    }
}
