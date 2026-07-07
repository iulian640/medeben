import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import vueTsConfig from '@vue/eslint-config-typescript'

export default [
  { ignores: ['dist/**', 'dev-dist/**', 'node_modules/**'] },
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  ...vueTsConfig(),
]
