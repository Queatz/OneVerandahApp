// Webpack config extension for dev server with history fallback (SPA routing) + chunking/optimizations
config.devServer = config.devServer || {};
config.devServer.historyApiFallback = true;
config.devServer.port = 7070;

// Chunking / optimizations.
//
// NOTE: The Kotlin/JS Gradle plugin does NOT use html-webpack-plugin, so the
// hand-written static index.html (src/jsMain/resources/index.html) is copied
// verbatim and webpack never injects <script> tags into it. Therefore EVERY
// chunk emitted here must be referenced manually in index.html, in the right
// load order: runtime -> vendors -> main (webApp.js).
//
// With name: 'vendors' and outputFileName = "webApp.js", the vendor chunk is
// emitted as "webApp-vendors.js" (chunk pattern is "webApp-[name].js"), and
// the runtime chunk as "webApp-runtime.js". Both are already <script>'d in
// index.html, so adding/keeping this split is safe.
config.optimization = config.optimization || {};
config.optimization.splitChunks = {
    chunks: 'all',
    cacheGroups: {
        vendor: {
            test: /[\\/]node_modules[\\/]/,
            name: 'vendors',
            chunks: 'all',
        }
    }
};
config.optimization.runtimeChunk = 'single';
