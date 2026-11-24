# VetClinic

Veteriner klinikleri için çok kiracılı (multi-tenant) yönetim platformu: hasta hayvan, muayene, aşı takvimi, randevu, stok, satış/fatura, abonelik ve komisyon. YM 341 Yazılım Tasarımı ve Mimarisi, Grup 16.

**Ekip:** Fırat · Berra · Özge

Bu repo, ekibin önceki proje çalışmasının (backend temeli) üzerine kurulmaktadır. Modüller, ekip üyelerince Issue → branch → Pull Request akışıyla adım adım eklenir.

## Yapı (monorepo)

| Bileşen | Klasör | Teknoloji |
|---|---|---|
| Backend (REST API) | `apps/api` | Java 21, Spring Boot 3, PostgreSQL 17, Flyway |
| Klinik web paneli + platform paneli | `apps/web` | React 19, TypeScript, Vite, Mantine |
| Hayvan sahibi mobil uygulaması | `apps/mobile` | Expo (React Native), TypeScript |
| Dokümanlar | `docs/` | Mimari, UML, ER, ADR, test raporu |

## Gereksinimler

- Node.js 24+, JDK 21, Docker Desktop, Git

## Geliştirme ortamı

```bash
npm install          # web ve mobil bağımlılıkları (npm workspaces)
docker compose up -d # PostgreSQL + Mailpit (e-posta test sunucusu: http://localhost:8025)
```

Uygulamaların çalıştırma ve test komutları ilgili modüller eklendikçe bu dosyaya eklenecektir.

## Katkı

Çalışma akışı (Issue → branch → PR → review), branch adları ve kurallar: [CONTRIBUTING.md](CONTRIBUTING.md).
