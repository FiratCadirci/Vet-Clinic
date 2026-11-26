# VetClinic – AUTH API Contract

Bu doküman **mevcut backend kodunun** analizine dayanır (`auth/`, `common/api/GlobalExceptionHandler`, `application.yml`, Flyway V2/V7/V12). Henüz olmayan özellikler (§9) **"❌"** olarak işaretlenmiştir; frontend bunlara bağımlı kodlanmamalıdır.

## 0. Özet

| Özellik | Durum |
|---|---|
| `POST /api/auth/login`, `/refresh`, `GET /api/auth/me`, `POST /api/auth/change-password` | ✅ Var |
| **`POST /api/auth/logout`** | ✅ Var (204) |
| Access token sunucu tarafı iptali (jti denylist) | ✅ Var — **in-memory** (restart'ta sıfırlanır, çoklu instance'ta paylaşılmaz) |
| `POST /api/platform/auth/login`, `/refresh`, `/logout`, `GET /me` | ✅ Var (Flyway V16: `vet.platform_refresh_tokens`) |
| Login/refresh/me yanıtında `roles` ve `clinics` (id, code, name) | ✅ Var |
| CORS | ✅ Var (`CORS_ALLOWED_ORIGINS`) |
| Hesap kilitleme / rate limit / brute-force koruması | ❌ Yok (kapsam dışı bırakıldı) |
| **`POST /api/auth/sign-in`** — tek giriş ekranı, otomatik rol/panel tespiti (Flyway V17) | ✅ Var |

## 0.1 TEK GİRİŞ EKRANI — `POST /api/auth/sign-in` (önerilen giriş)

Kullanıcı giriş ekranında **organizasyon combobox'ından** kurumunu seçer, ardından e-posta + şifre girer. Combobox listesi `GET /api/auth/organizations` ile gelir. Backend seçilen organizasyonda kullanıcıyı doğrular, rolünden **panel** belirler. Platform yöneticileri için combobox'ta organizasyon yerine ilk seçenek olarak "Platform yönetimi" gösterilir (`organizationCode` gönderilmez). Eski `/api/auth/login` ve `/api/platform/auth/login` aynen çalışır.

### Organizasyon listesi — `GET /api/auth/organizations`
**Auth:** Gerekmez (public) · Yalnızca aktif organizasyonlar, ada göre sıralı.
```json
[ { "code": 1001, "name": "Acme Veteriner" }, { "code": 1002, "name": "Pati Klinik" } ]
```
Combobox: görünen metin `name`, değer `code`. Not: liste herkese açıktır (yalnızca kod ve ad).

**Auth:** Gerekmez (public) · **Headers:** `Content-Type: application/json`

### Request
```json
{ "email": "vet@test.com", "password": "secret", "organizationCode": 1001 }
```
| Alan | Kural |
|---|---|
| `email` | `@NotBlank`, `@Email` |
| `password` | `@NotBlank` |
| `organizationCode` | Combobox'ta seçilen organizasyon kodu (integer). Platform yöneticisi girişinde gönderilmez (null). Gönderilirse platform denemesi atlanır. |

### Response — `200 OK`
```json
{
  "accountType": "TENANT",
  "panel": "CLINIC_ADMIN",
  "availablePanels": ["CLINIC_ADMIN", "PET_OWNER"],
  "tenant": { "tokenType": "Bearer", "accessToken": "...", "refreshToken": "...", "expiresInSeconds": 900,
              "organizationId": "...", "userId": "...", "displayName": "...", "clinicIds": [], "permissions": [],
              "mustChangePassword": false, "roles": [{"id":"...","code":"clinic_admin","name":"..."}], "clinics": [] },
  "platform": null
}
```
- `accountType`: `TENANT` → `tenant` dolu, `platform` null. `PLATFORM` → `platform` dolu (`PlatformAuthResponse`: accessToken, refreshToken, adminId, displayName, email, role), `tenant` null.
- `tenant` ve `platform` içeriği, mevcut login yanıtlarıyla birebir aynıdır (§2 ve §5).

### Panel eşleme tablosu
| `panel` | Kim | Tespit |
|---|---|---|
| `PLATFORM_ADMIN` | Platform yöneticisi | `platform_admins` kaydı |
| `CLINIC_ADMIN` | Klinik yöneticisi | rol kodu `clinic_admin` |
| `VETERINARIAN` | Veteriner hekim | rol kodu `veterinarian` |
| `RECEPTION` | Resepsiyon | rol kodu `receptionist` |
| `PET_OWNER` | Hayvan sahibi | `clients.app_user_id` ile bağlı kullanıcı |
| `UNASSIGNED` | Rolü/paneli olmayan | Hiçbiri yok → "Yetkiniz tanımlı değil" ekranı |

`panel` birincil panel; öncelik `CLINIC_ADMIN > VETERINARIAN > RECEPTION > PET_OWNER`. Birden fazla rol varsa `availablePanels` hepsini içerir (panel değiştirme menüsü için). Backend yalnızca panel **kodunu** döner; rota eşlemesi frontend'dedir:
```js
const ROUTES = { PLATFORM_ADMIN:'/platform', CLINIC_ADMIN:'/clinic-admin', VETERINARIAN:'/doctor',
                 RECEPTION:'/reception', PET_OWNER:'/owner', UNASSIGNED:'/no-access' };
```
Not: Panel, **güvenlik sınırı değildir**; yetki her API'de `permissions` ile backend'de denetlenir. Hayvan sahibinin `permissions`/`clinicIds` listesi boş olabilir.

### Hatalar
| Durum | HTTP | Body |
|---|---|---|
| Yanlış e-posta/şifre, kullanıcı yok, pasif hesap | 401 | `code: "UNAUTHORIZED"` (hepsi aynı — bilgi sızdırmaz) |
| Validation | 400 | `code: "VALIDATION_ERROR"` |
| Aynı e-posta+şifre birden fazla kurumda eşleşti | **409** | aşağıda |

```json
{ "timestamp": "2026-11-07 13:40", "status": 409, "code": "AMBIGUOUS_ORGANIZATION",
  "message": "Bu hesap birden fazla organizasyonda kayıtlı; organizationCode belirtin.",
  "path": "/api/auth/sign-in",
  "details": { "organizations": [ { "id": "...", "code": 1001, "name": "Örnek Klinik" } ] } }
```
409'a combobox kullanıldığında normalde düşülmez (yalnızca `organizationCode` gönderilmezse olur); yine de gelirse `details.organizations` ile combobox'ı doldurup kullanıcıya seçtirin ve isteği `organizationCode` ile tekrarlayın.

### Sign-in sonrası frontend akışı
1. `accountType`'a göre token'ları sakla (`tenant.*` veya `platform.*`) ve **`accountType` + `panel` + `availablePanels`'i** sakla. (`/auth/me` `panel` döndürmez; panel sign-in'de saklanmalıdır.)
2. TENANT ise önce `mustChangePassword` kontrolü (§8), sonra `ROUTES[panel]`; çok şubeli ise şube seçimi (§8).
3. **Refresh/logout endpoint'i accountType'a bağlıdır:** TENANT → `/api/auth/refresh`, `/api/auth/logout`; PLATFORM → `/api/platform/auth/refresh`, `/api/platform/auth/logout`.

## 1. Genel Kurallar

- Base URL: `http://<host>:8080` (`server.port=${SERVER_PORT:8080}`).
- `Content-Type: application/json; charset=UTF-8`.
- Auth endpoint'leri başarı yanıtlarını **sarmalamadan (raw JSON)** döner. (`/api/v1/*` bazı endpoint'ler `ApiResponse` sarmalayıcısı kullanır; auth kullanmaz.)
- Tarih-saat alanları: `"yyyy-MM-dd HH:mm"`, saat dilimi `Europe/Istanbul` (örn. `"2026-11-07 09:48"`).
- Tüm mesajlar Türkçedir.
- Multi-tenant: önerilen giriş `POST /api/auth/sign-in` (yalnızca e-posta + şifre, §0.1). Eski `/api/auth/login` kurum kodu (`organizationCode`) + e-posta + şifre ister.

### Token özellikleri

| | Access Token | Refresh Token |
|---|---|---|
| Tür | JWT (HS256, imzalı) | Opak string: `<organizationId>.<32 hex>` |
| Süre | **900 sn (15 dk)** — `JWT_ACCESS_TOKEN_TTL_SECONDS` | **1 209 600 sn (14 gün)** — `JWT_REFRESH_TOKEN_TTL_SECONDS` |
| Saklama (sunucu) | Saklanmaz | DB'de SHA-256 hash olarak (`vet.refresh_tokens`) |
| Kullanım | Her API çağrısında `Authorization` header | Sadece `/api/auth/refresh` |

Access token claim'leri (frontend'in decode etmesi **gerekmez**, bilgi amaçlı): `sub` (userId), `org`, `clinics[]`, `perms[]`, `mcp` (mustChangePassword), `jti`, `typ=TENANT_ACCESS`, `iat`, `exp`.

---

## 2. LOGIN

**Method:** `POST`
**URL:** `/api/auth/login`
**Auth:** Gerekmez (public)
**Headers:** `Content-Type: application/json`

### Request

```json
{
  "organizationCode": 1001,
  "email": "ayse@acme.com",
  "password": "Sifre123!"
}
```

| Alan | Tip | Kural |
|---|---|---|
| `organizationCode` | integer | `@NotNull` |
| `email` | string | `@NotBlank`, `@Email` (büyük/küçük harf duyarsız eşleşir) |
| `password` | string | `@NotBlank` |

### Response — `200 OK`

```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "3f2b6c1e-8a4d-4e0a-9c55-0d6f1b2a7e11.9b1c0f3a4d5e47c1a8e2f6b7c3d4e5f6",
  "expiresInSeconds": 900,
  "organizationId": "3f2b6c1e-8a4d-4e0a-9c55-0d6f1b2a7e11",
  "userId": "7a1d2c3b-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
  "displayName": "Ayşe Yılmaz",
  "clinicIds": [
    "11111111-1111-1111-1111-111111111111",
    "22222222-2222-2222-2222-222222222222"
  ],
  "permissions": ["clients.read", "clients.write", "appointments.manage", "clinical.read"],
  "mustChangePassword": false,
  "roles": [
    { "id": "5d0c...-uuid", "code": "VET", "name": "Veteriner" }
  ],
  "clinics": [
    { "id": "11111111-1111-1111-1111-111111111111", "code": "KDK", "name": "Kadıköy Şube" },
    { "id": "22222222-2222-2222-2222-222222222222", "code": "BSK", "name": "Beşiktaş Şube" }
  ]
}
```

| Alan | Açıklama |
|---|---|
| `tokenType` | Her zaman `"Bearer"` |
| `accessToken` | JWT |
| `refreshToken` | Opak refresh token (her refresh'te değişir) |
| `expiresInSeconds` | **Access token** ömrü (refresh token ömrü yanıtta dönmez; 14 gün sabit kabul edilmeli) |
| `organizationId` / `userId` | UUID |
| `displayName` | Görünen ad |
| `clinicIds` | Kullanıcının **şu an geçerli** (`valid_from <= bugün <= valid_to/null`) rol atamasına sahip olduğu şubelerin UUID listesi (sıralı) |
| `permissions` | Kullanıcının tüm aktif şube rollerinden birleştirilmiş benzersiz yetki kodları (şubeye göre ayrıştırılmış **değildir**) |
| `mustChangePassword` | `true` ise yalnızca `/api/auth/change-password` ve `/api/auth/me` çalışır |

### Rol ve şube bilgisi
- `roles`: kullanıcının geçerli atamalarındaki **benzersiz** roller (`id, code, name`).
- `clinics`: `clinicIds` ile aynı şubelerin `id, code, name` detayı (aynı sırada). `/me`'de aktif şube varsa yalnızca o şube döner.
- Roller şubeye göre ayrıştırılmaz; `permissions` birleşik listedir.
- Kullanıcı e-postası yanıtta dönmez.

### Status kodları
| Kod | Durum |
|---|---|
| 200 | Başarılı |
| 400 | Validation hatası (`VALIDATION_ERROR` / `MALFORMED_REQUEST`) |
| 401 | Kurum kodu / e-posta / şifre hatalı, kullanıcı yok veya pasif (hepsi aynı yanıt) |

> ⚠️ Kullanıcının hiç aktif şube ataması yoksa login **başarılı** olur (`clinicIds: []`) fakat sonraki tüm korumalı isteklerde `403 FORBIDDEN – "Klinik erişimi bulunamadı."` alınır.

---

## 3. TOKEN REFRESH

**Method:** `POST` — **URL:** `/api/auth/refresh` — **Auth:** Gerekmez (public) — **Headers:** `Content-Type: application/json`

### Request
```json
{ "refreshToken": "3f2b6c1e-....9b1c0f3a..." }
```
`refreshToken`: `@NotBlank`.

### Response — `200 OK`
Login ile **aynı şema**; `accessToken` yeni, `refreshToken` **yeni** (eski token artık geçersiz), `clinicIds`/`permissions` DB'den **yeniden okunur** (rol değişiklikleri yansır).

### Mantık (rotation)
1. Token `organizationId.` önekinden tenant çözülür.
2. Hash ile DB'de aranır; yoksa → 401.
3. Token daha önce kullanılmış/iptal edilmişse (**reuse tespiti**) → aynı `token_family` içindeki **tüm aktif tokenlar iptal edilir** → 401.
4. Süresi dolmuşsa (14 gün) → 401.
5. Yeni refresh token üretilir (aynı family, yeni 14 gün), eski token `revoked_at` ile kapatılır.
6. Kullanıcı pasifse → 401.

### Refresh token geçersizleşme kuralları
- Her kullanımdan sonra (tek kullanımlık).
- 14 gün sonunda (sliding: her refresh'te yeni 14 gün).
- Reuse tespitinde tüm family iptal.
- Şifre değiştirilince (`change-password`) kullanıcının **tüm** refresh tokenları iptal.
- Admin kullanıcıyı pasife alınca (`POST /api/v1/users/{id}/deactivate`) kullanıcının tüm refresh tokenları iptal.
- Logout ile: ✅ (`POST /api/auth/logout` gönderilen refresh token'ın tüm family'sini iptal eder).

### Status kodları
`200`, `400` (boş body/alan), `401` (geçersiz/süresi dolmuş/iptal edilmiş token, pasif kullanıcı).

> ⚠️ **Eş zamanlı refresh yarışı:** İki istek aynı refresh token ile aynı anda giderse ikincisi reuse sayılır ve tüm oturum (family) iptal olur. Frontend refresh çağrısını **tek seferde (single-flight)** yapmalıdır.

---

## 4. LOGOUT ✅

**Method:** `POST` — **URL:** `/api/auth/logout` — **Auth:** gerekli (`Authorization: Bearer <accessToken>`) — **Headers:** `Content-Type: application/json`. **Request body opsiyonel:** `{ "refreshToken": "..." }`. **Response:** `204 No Content` (body yok).

**Mekanizma:** (1) Access token'ın `jti`'si denylist'e `exp`'ine kadar eklenir; aynı token ile sonraki istekler `401 UNAUTHORIZED` döner. (2) `refreshToken` gönderilirse (ve bu kullanıcıya aitse) tüm family iptal edilir; geçersiz/bilinmeyen token sessizce yok sayılır (idempotent, yine 204). (3) `mustChangePassword=true` iken de çağrılabilir.

**Durum kodları:** `204` başarılı; `401` access token yok/geçersiz/zaten iptal edilmiş.

**Logout sonrası:** Frontend tüm token/state'i siler ve login'e yönlenir; eski access ve refresh token çalışmaz.

> ⚠️ Denylist **bellek içidir**: backend restart'ında sıfırlanır, çoklu instance'ta paylaşılmaz (access token ömrü ≤15 dk ile sınırlı). Refresh token iptali DB'dedir, kalıcıdır.

### Logout notu
Refresh token iptali kalıcıdır; access token denylist'i yukarıdaki sınırlamalara tabidir.
---

## 5. ME ve CHANGE PASSWORD

### GET `/api/auth/me`
- Header: `Authorization: Bearer <accessToken>` (+ opsiyonel `X-Clinic-Id`).
- `200` — login ile aynı şema fakat **`accessToken: null`, `refreshToken: null`**. `clinicIds`: `X-Clinic-Id` verilmişse/çözülmüşse **yalnızca aktif şube** (`[clinicId]`), aksi halde tüm şubeler. (Filtre header yoksa `clinicIds[0]`'ı aktif şube sayar; dolayısıyla header'sız `/me` yanıtında `clinicIds` tek elemanlı döner.)
- `expiresInSeconds` burada sabit TTL'dir (kalan süre **değildir**). Yanıt `roles` ve `clinics` alanlarını da içerir.

### POST `/api/auth/change-password`
- Header: `Authorization: Bearer <accessToken>`
- Request:
```json
{ "currentPassword": "Eski123!", "newPassword": "YeniSifre123!" }
```
`newPassword`: `@NotBlank`, 10–100 karakter. Başka karmaşıklık kuralı yok.
- `204 No Content` (body yok). `mustChangePassword=false` olur ve **tüm refresh tokenlar iptal edilir** (mevcut access token ≤15 dk geçerli kalır). Frontend başarıdan sonra **yeniden login** akışına yönlendirmelidir (mevcut refresh token artık çalışmaz).
- Hatalar: `401` mevcut şifre yanlış (⚠️ bu oturum bitti anlamına **gelmez**; frontend bu 401'de refresh/logout tetiklememelidir), `400` yeni şifre eskisiyle aynı (`BAD_REQUEST`, "Yeni şifre mevcut şifreyle aynı olamaz.") veya validation.

### Platform yöneticisi — `/api/platform/auth/*`
- `POST /api/platform/auth/login` body `{ "email", "password" }` → `{ tokenType, accessToken, refreshToken, expiresInSeconds, adminId, displayName, email, role: "PLATFORM_ADMIN" }`.
- `POST /api/platform/auth/refresh` body `{ "refreshToken" }` → login ile aynı şema, **yeni** refresh token (rotation; iptal edilmiş token tekrar kullanılırsa tüm family iptal, 401). Süresi dolmuş/bilinmeyen token veya pasif admin → 401. Refresh token `p.<64 hex>` formatındadır, DB'de SHA-256 hash olarak tutulur (`vet.platform_refresh_tokens`, Flyway V16).
- `POST /api/platform/auth/logout` body `{ "refreshToken" }` (opsiyonel) → `204`; refresh family iptal edilir. Platform access token'ında `jti` yoktur, bu yüzden access token doğal ömrüne (≤15 dk) kadar geçerli kalır.
- `GET /api/platform/auth/me` `Authorization: Bearer <platformToken>`.
- Platform token (`typ=PLATFORM_ACCESS`) tenant endpoint'lerinde **kabul edilmez** (401).
- Bu yollar `permitAll` + filtre atlamalıdır; kimlik kontrolü servis içinde yapılır.

---

## 6. AUTHENTICATION / AUTHORIZATION

### Bearer token
```
Authorization: Bearer <accessToken>
X-Clinic-Id: <şube UUID>        (opsiyonel)
```
- `X-Clinic-Id` verilmezse aktif şube = token'daki `clinicIds[0]`.
- Verilirse `clinicIds` içinde olmalı; değilse `403` ("Bu klinik için yetkiniz yok."), UUID formatı bozuksa `403` ("Geçersiz klinik kimliği.").
- Şube seçimi tenant/RLS bağlamını (`app.clinic_id`) belirler; çok şubeli kullanıcılarda frontend her istekte seçili şubeyi bu header ile göndermelidir.

### Public endpoint'ler (token gerekmez)
`POST /api/auth/login`, `POST /api/auth/refresh`, `/api/platform/auth/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `GET /actuator/health`, ve tüm `OPTIONS` istekleri (filtre seviyesinde).
**Diğer her şey** (`anyRequest().authenticated()`) geçerli tenant access token ister: `/api/auth/me`, `/api/auth/change-password`, `/api/v1/**` vb.

### Role/permission tabanlı yetkilendirme
- Spring Security **authority = permission kodu** (örn. `clients.read`). Rol adı authority değildir; `hasRole` kullanılmaz.
- Controller'larda `@PreAuthorize("hasAuthority('...')")`, `@EnableMethodSecurity` ile.
- Permission kodları (`Permission.java`): `clients.read`, `clients.write`, `appointments.manage`, `clinical.read`, `clinical.write`, `clinical.delete`, `diagnostics.manage`, `inventory.read`, `inventory.write`, `billing.manage`, `users.manage`, `reports.read`, `audit.read`, `clinic_roles.manage`, `vaccine_protocols.manage`, `subscriptions.read`, `commissions.read`.
- Örnek eşleşmeler: müşteri/hayvan/tür → `clients.read|write`; muayene/ek/aşı → `clinical.*`, `vaccine_protocols.manage`; `/api/v1/users`, `/api/v1/roles` → `users.manage`. `GET /api/v1/clinics` yalnızca authenticated gerektirir.
- Frontend menü/buton görünürlüğünü `permissions` dizisine göre yönetmeli; asıl yetki kontrolü backend'dedir.
- ⚠️ `permissions` şube bazlı değil, tüm şubelerin birleşimidir; bir şubede olmayan yetki başka şubeden gelebilir.

### 401 / 403
- **401** `UNAUTHORIZED`: Authorization header yok, `Bearer ` ile başlamıyor, imza/format geçersiz, token süresi dolmuş, `typ` farklı (platform token), vb. **Süresi dolmuş token ile geçersiz token arasında yanıt farkı yoktur.**
- **403** `FORBIDDEN`: yetki (permission) eksik, şube erişimi yok/yanlış `X-Clinic-Id`.
- **403** `PASSWORD_CHANGE_REQUIRED`: `mustChangePassword=true` iken `/api/auth/change-password` ve `/api/auth/me` dışındaki istekler.

---

## 7. ERROR RESPONSES

Hata şeması (`ApiError`):
```json
{
  "timestamp": "2026-11-07 09:48",
  "status": 401,
  "code": "UNAUTHORIZED",
  "message": "Kimlik doğrulama başarısız.",
  "path": "/api/auth/login",
  "details": {}
}
```
> Not: JWT filtresinin (token kontrolü) ürettiği hatalarda **`details` alanı yoktur**; controller/`GlobalExceptionHandler` kaynaklı hatalarda `details` vardır (boş `{}` olabilir). Frontend `details`'i opsiyonel okumalıdır.

### 7.1 Yanlış e-posta / şifre (login) — `401`
```json
{
  "timestamp": "2026-11-07 09:48",
  "status": 401,
  "code": "UNAUTHORIZED",
  "message": "Kimlik doğrulama başarısız.",
  "path": "/api/auth/login",
  "details": {}
}
```
(Servis içindeki "Kimlik bilgileri hatalı." metni `GlobalExceptionHandler` tarafından **ezilir**; istemciye hep "Kimlik doğrulama başarısız." gider.)

### 7.2 Kullanıcı bulunamadı — `401` (7.1 ile birebir aynı)
Kurum kodu yok, e-posta yok → kullanıcı enumeration'ı önlemek için **ayırt edilemez**. 404 dönmez.

### 7.3 Hesap pasif — `401` (7.1 ile birebir aynı)
Login'de pasif kullanıcı ile yanlış şifre ayırt edilemez. Refresh'te de pasif kullanıcı `401` alır. `ACCOUNT_DISABLED` benzeri ayrı bir kod **yoktur**.

### 7.4 Token süresi dolmuş / 7.5 Geçersiz token — `401` (filtre çıktısı, `details` yok)
```json
{
  "timestamp": "2026-11-07 09:48",
  "status": 401,
  "code": "UNAUTHORIZED",
  "message": "Kimlik doğrulama başarısız.",
  "path": "/api/v1/clients"
}
```
Header eksik / `Bearer` yok / bozuk JWT / expired JWT / yanlış `typ` için aynı yanıt. Expired'i ayırt eden `TOKEN_EXPIRED` kodu yoktur; frontend 401 alınca refresh denemelidir.

Geçersiz/iptal/süresi dolmuş **refresh token** (`/api/auth/refresh`): `401`, `UNAUTHORIZED`, aynı mesaj, `details: {}`. Refresh'te 401 = oturum bitti → login.

### 7.6 Yetkisiz erişim (permission yok) — `403`
```json
{
  "timestamp": "2026-11-07 09:48",
  "status": 403,
  "code": "FORBIDDEN",
  "message": "Bu işlem için yetkiniz yok.",
  "path": "/api/v1/users",
  "details": { "reason": "Access Denied" }
}
```
Şube kaynaklı 403 (JWT filtresi, `details` yok):
```json
{
  "timestamp": "2026-11-07 09:48",
  "status": 403,
  "code": "FORBIDDEN",
  "message": "Bu klinik için yetkiniz yok.",
  "path": "/api/v1/clients"
}
```
Diğer olası `message`: "Geçersiz klinik kimliği.", "Klinik erişimi bulunamadı.".

Şifre değişikliği zorunlu:
```json
{
  "timestamp": "2026-11-07 09:48",
  "status": 403,
  "code": "PASSWORD_CHANGE_REQUIRED",
  "message": "İlk girişte şifrenizi değiştirmeniz gerekir.",
  "path": "/api/v1/clients"
}
```

### 7.7 Validation hataları — `400`
Örn. `POST /api/auth/login` ile `{"organizationCode":null,"email":"abc","password":""}`:
```json
{
  "timestamp": "2026-11-07 09:48",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Geçersiz istek.",
  "path": "/api/auth/login",
  "details": {
    "reason": "Validation failed for argument [0] in public ...AuthLoginResponse ...AuthController.login(...AuthLoginRequest): [Field error in object 'authLoginRequest' on field 'email': rejected value [abc]; ... must be a well-formed email address] ..."
  }
}
```
- ⚠️ **Alan bazlı hata listesi yoktur**; `details.reason` Spring'in ham (uzun, İngilizce) mesajıdır. Frontend kullanıcıya bunu göstermemeli; kendi form validasyonunu yapmalıdır.
- Bozuk/okunamayan JSON gövdesi: `400`, `code: "MALFORMED_REQUEST"`, `message: "İstek gövdesi okunamadı; alan biçimlerini (tarih-saat için yyyy-MM-dd HH:mm) kontrol edin."`, `details: {}`.
- Yeni şifre eskisiyle aynı: `400`, `code: "BAD_REQUEST"`, `message: "Yeni şifre mevcut şifreyle aynı olamaz."`.
- Beklenmeyen: `500 INTERNAL_ERROR` ("Beklenmeyen bir hata oluştu.").

---

## 8. FRONTEND INTEGRATION

### Saklanması gereken bilgiler
| Bilgi | Not |
|---|---|
| `accessToken` | Mümkünse bellekte (memory). |
| `refreshToken` | Yanıt gövdesinde döner (HttpOnly cookie **değil**). localStorage XSS riski taşır; mümkünse güvenli depolama/BFF düşünün. |
| `accessTokenExpiresAt` | `Date.now() + expiresInSeconds*1000` (login/refresh anında hesapla) |
| `organizationId`, `userId`, `displayName` | UI için |
| `clinicIds` + seçili `activeClinicId` | `X-Clinic-Id` için |
| `permissions` | Menü/buton kontrolü |
| `mustChangePassword` | Yönlendirme için |
| `organizationCode` (+ e-posta, opsiyonel) | "Beni hatırla"/login formu için |

### Login sonrası yönlendirme
1. `mustChangePassword === true` → şifre değiştirme ekranı (diğer tüm API'ler 403 `PASSWORD_CHANGE_REQUIRED`).
2. `clinics.length === 0` → "Şubeye atanmamışsınız" ekranı (API'ler 403 verecektir).
3. `clinics.length === 1` → `activeClinicId` = o şube, ana sayfa.
4. `clinics.length > 1` → şube seçim ekranı (adlar `clinics[].name` içinde), sonra ana sayfa.
5. Ana sayfa/menü `permissions`'a göre.

### Token gönderimi
Her korumalı istekte `Authorization: Bearer <accessToken>` ve (çok şubeli ise) `X-Clinic-Id: <activeClinicId>`. Login/refresh isteklerine Authorization eklemeyin.

### Token yenileme akışı
1. İstek `401` alırsa (login/refresh/change-password hariç) → **tek bir** refresh çağrısı yap (diğer istekler bu sonucu beklesin).
2. `200` → yeni `accessToken` + **yeni `refreshToken`**'ı kaydet (eskisi artık geçersiz), `permissions`/`clinicIds`'i güncelle, orijinal isteği bir kez tekrarla.
3. Refresh `401` → oturum bitti: state'i temizle, login'e yönlendir.
4. İsteğe bağlı proaktif yenileme: `accessTokenExpiresAt`'den ~60 sn önce.
5. `403` yanıtlarında refresh denemeyin.

### Logout akışı
1. `POST /api/auth/logout` (Bearer + `{ refreshToken }`) çağır; hata dönse bile devam et.
2. Token'ları ve state'i sil → login sayfasına git.

### Oturum süresi dolduğunda
- Access token dolduysa: sessiz refresh (yukarıdaki akış).
- Refresh de başarısızsa (veya 14 gün geçtiyse): state'i sil, "Oturumunuz sona erdi" mesajıyla login'e yönlendir, mevcut rota/şubeyi login sonrası dönmek için sakla.
- Şifre değişikliği veya admin tarafından pasife alma sonrası refresh `401` döner → aynı akış.

### CORS
`SecurityConfig` içinde CORS etkindir. İzinli origin'ler `CORS_ALLOWED_ORIGINS` (virgülle ayrık; varsayılan `http://localhost:3000,http://localhost:5173`) ile verilir. Metodlar: GET, POST, PUT, PATCH, DELETE, OPTIONS. İzinli header'lar: `Authorization`, `Content-Type`, `X-Clinic-Id`. Açık header: `Content-Disposition`. Preflight cache: 3600 sn. Credentials (cookie) **kapalıdır**. Prod origin'i ortam değişkeniyle ekleyin.

---

## 9. EKSİK / KALAN NOKTALAR

Logout, access-token iptali, platform refresh/logout, rol ve şube bilgisi ile CORS **uygulanmıştır**. Kalanlar:

| # | Konu | Not |
|---|------|-----|
| 1 | Brute-force / rate limit / hesap kilitleme | Kapsam dışı bırakıldı, yok |
| 2 | Access token denylist bellek içi | Restart'ta sıfırlanır, instance'lar arası paylaşılmaz; Redis/DB önerilir |
| 3 | Platform access token'ında `jti` yok | Logout sonrası ≤15 dk geçerli kalır |
| 4 | Refresh reuse tespitinde family iptali | Transaction içinde exception nedeniyle rollback olabilir |
| 5 | Şube bazlı permission yok | Yetkiler kullanıcı geneli |
| 6 | 401 hata kodları ayrışmıyor | Süresi dolmuş / geçersiz token aynı koddur |
| 7 | Filtre hatalarında `details` ve alan bazlı validation çıktısı tutarsız | |
| 8 | `refreshExpiresInSeconds` yok | Refresh ömrü 14 gün (sabit) varsayılmalı |
| 9 | Eşzamanlı refresh için grace window yok | Frontend tek refresh isteği kuyruklamalı |
| 10 | Varsayılan JWT secret | Prod'da `JWT_SECRET` ile mutlaka değiştirilmeli |

> Bu sürümde logout, denylist, platform refresh (Flyway V16), rol/şube bilgisi ve CORS backend'e eklenmiştir.

