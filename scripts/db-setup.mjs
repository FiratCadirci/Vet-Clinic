/** Docker'daki PostgreSQL'de veterinery veritabanını ve vet_app rolünü hazırlar (tekrar çalıştırılabilir). */
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

const sql = readFileSync(new URL('../apps/api/db/dev-setup.sql', import.meta.url), 'utf8');
const r = spawnSync('docker', ['compose', 'exec', '-T', 'db', 'psql', '-U', 'vet', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1', '-q'], {
  input: sql,
  stdio: ['pipe', 'inherit', 'inherit'],
});
if (r.status !== 0) {
  console.error('Veritabanı hazırlanamadı. Docker açık mı? (npm run db:up)');
  process.exit(r.status ?? 1);
}
console.log('veterinery veritabanı ve vet_app rolü hazır.');
