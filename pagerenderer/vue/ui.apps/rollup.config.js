import babel from "@rollup/plugin-babel";

export default {
	input: 'src/main/js/peregrineApp.js',
	output: {
			file: 'target/classes/etc/felibs/pagerendervue/js/perview.js',
			format: 'iife',
			name: '$peregrineApp',
	},
  plugins: [
    babel({
      babelHelpers: 'bundled',
      exclude: 'node_modules/**',
      extensions: [
        '.js',
        '.jsx',
        '.vue',
      ]
    }),
  ],
}
