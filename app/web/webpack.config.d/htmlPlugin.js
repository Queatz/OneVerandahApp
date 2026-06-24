// Auto-inject <script> tags into index.html for every emitted chunk.
//
// Why this exists:
// The Kotlin/JS Gradle plugin does NOT register html-webpack-plugin by default
// (the generated webpack.config.js starts with `plugins: []`). It simply copies
// src/jsMain/resources/index.html verbatim into the output. That means any chunk
// webpack emits beyond the ones hard-coded in index.html (for example a
// "webApp-vendors.js" produced by optimization.splitChunks) is NEVER referenced,
// so its code (and therefore Compose) never loads -> blank white page.
//
// With this plugin, index.html is treated as a TEMPLATE and webpack regenerates
// it at build time, injecting <script> tags for ALL chunks in the correct order.
// This is what makes splitChunks safe to keep.
const HtmlWebpackPlugin = require('html-webpack-plugin');

config.plugins = config.plugins || [];
config.plugins.push(new HtmlWebpackPlugin({
    // The processed resource template is copied next to the compiled JS, in the
    // package's "kotlin" directory, before webpack runs. It is named
    // "index.template.html" (NOT "index.html") on purpose: the distribution task
    // also copies processed resources, so if the source were named "index.html"
    // it would collide with the "index.html" webpack emits here (Gradle "duplicate
    // entry" error). Webpack generates the final "index.html" from this template.
    template: require('path').resolve(__dirname, 'kotlin/index.template.html'),
    filename: 'index.html',
    publicPath: '/',
    inject: 'body',
}));
