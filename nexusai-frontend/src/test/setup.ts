import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

// Clean up React DOM after every test so they don't leak across files.
afterEach(() => {
  cleanup()
})

// jsdom-style polyfills missing in happy-dom that components occasionally hit.
if (!globalThis.URL.createObjectURL) {
  globalThis.URL.createObjectURL = () => 'blob:mock'
}
if (!globalThis.URL.revokeObjectURL) {
  globalThis.URL.revokeObjectURL = () => {}
}
