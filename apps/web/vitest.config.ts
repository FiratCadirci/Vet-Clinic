import { defineConfig } from 'vitest/config'

/** Web birim testleri: saf yardımcılar (biçimlendirme, fiyat önizlemesi). DOM gerekmez → node ortamı. */
export default defineConfig({
  test: {
    include: ['src/**/*.test.ts'],
    environment: 'node',
    coverage: {
      provider: 'v8',
      include: ['src/lib/format.ts', 'src/components/admin/pricing.ts'],
      reporter: ['text-summary', 'html', 'json-summary'],
      reportsDirectory: './coverage',
    },
  },
})
