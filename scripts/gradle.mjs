/**
 * apps/api (Spring Boot) için Gradle çalıştırıcı. apps/api/.env değişkenlerini yükler.
 *   node scripts/gradle.mjs bootRun        → API (npm run api)
 *   node scripts/gradle.mjs test           → backend testleri; IT'ler yerel Docker PostgreSQL'i kullanır
 * JDK 21 gerekir (JAVA_HOME veya PATH).
 */
import { spawn } from 'node:child_process';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const apiDir = join(dirname(fileURLToPath(import.meta.url)), '..', 'apps', 'api');
const env = { ...process.env };
for (const file of ['.env.example', '.env']) {
  const p = join(apiDir, file);
  if (!existsSync(p)) continue;
  for (const line of readFileSync(p, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
    if (m && (file === '.env' || env[m[1]] === undefined)) env[m[1]] = m[2];
  }
}
// Entegrasyon testleri: her koşuda geçici vet_it_* veritabanı oluşturulur ve sonunda silinir.
env.VET_IT_URL ??= 'jdbc:postgresql://localhost:5432';
env.VET_IT_ADMIN_USER ??= env.FLYWAY_USER ?? 'vet';
env.VET_IT_ADMIN_PASSWORD ??= env.FLYWAY_PASSWORD ?? 'vet';

const win = process.platform === 'win32';
// JAVA_HOME tanımlı değilse Windows'taki yaygın JDK 21 kurulum klasörlerine bakılır.
if (!env.JAVA_HOME && win) {
  for (const base of ['C:/Program Files/Microsoft', 'C:/Program Files/Eclipse Adoptium', 'C:/Program Files/Java']) {
    const jdk = existsSync(base) && readdirSync(base).find((d) => /^jdk-?21/.test(d));
    if (jdk) {
      env.JAVA_HOME = join(base, jdk);
      break;
    }
  }
}
// Windows'ta .bat dosyası doğrudan başlatılamaz; cmd.exe ile ve göreli adla (klasör yolunda boşluk olabilir) çalıştırılır.
const [cmd, args] = win ? ['cmd.exe', ['/d', '/c', '.\\gradlew.bat', ...process.argv.slice(2)]] : ['./gradlew', process.argv.slice(2)];
const child = spawn(cmd, args, { cwd: apiDir, env, stdio: 'inherit' });
child.on('exit', (code) => process.exit(code ?? 1));
