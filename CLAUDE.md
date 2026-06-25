# account-hr — E-Commint Ön Muhasebe & Fatura Yönetim Platformu

> **Not (kimlik/atıf):** Bu proje **E-Commint Yazılım Teknolojileri A.Ş.**'ye aittir (e-commint.com). Sorumlu geliştirici: **deniz.aydogdu@e-commint.com**. Araca bağlı hesabın profil e-postası farklı bir domain (velzon.tr) olabilir; bu **ayrı ve ilgisiz bir şirkettir** — tema, marka veya şirket tahmini için KULLANMA. Ön yüzde herhangi bir hazır temaya (Velzon vb.) bağlılık yoktur.

## İletişim Dili (ÖNEMLİ)
Kullanıcı ile **her zaman Türkçe** konuş. Tüm açıklamalar, özetler, sorular ve yorumlar Türkçe olmalı; İngilizce yanıt verme. Teknik terimler (Spring Boot, endpoint, commit, Docker vb.) olduğu gibi kalabilir ama cümleler Türkçe kurulur.

## Depo & Git Kimliği (ÖNEMLİ)
Bu kod **E-Commint** firmasına yazılıyor; özel/kişisel bir proje değildir. **İki ayrı repo** kullanılır (monorepo DEĞİL):
- **Backend + altyapı (API):** https://github.com/DenizAydogduEcommint/account-hr — Spring Boot, `docker-compose.yml`, dökümanlar, `is_listesi/` burada. Yerel dizin: `/Users/felece/Dev/account-hr`.
- **Frontend (Angular):** https://github.com/DenizAydogduEcommint/account-hr-frontend — Angular UI. Yerel dizin: `/Users/felece/Dev/account-hr-frontend`.
- Her iki repo: `origin`, branch `main`, public.
- **GitHub kullanıcı:** DenizAydogduEcommint
- **Commit kimliği (her iki repoda kullan):** Deniz Aydoğdu `<deniz.aydogdu@e-commint.com>` — kişisel `@gmail.com` adresi DEĞİL. Yeni klonlarda repo-yerel ayarla:
  `git config user.email deniz.aydogdu@e-commint.com` ve `git config user.name "Deniz Aydoğdu"`.
- **YouTrack:** https://e-commint.youtrack.cloud — commit mesajı issue koduyla başlar (ör. `IK-225: ...`) → otomatik bağlanır. Aynı issue koduyla iki repoya da commit atılabilir.

## Amaç
E-Commint'in çok sayıda yurtdışı abonelik/hizmet faturasını (Claude/Anthropic, OpenAI, Zoom, Contabo, domain'ler vb.) her ay eksiksiz toplamak ve ön muhasebeye (Paraşüt) iletmek. Bugün manuel (kredi kartı ekstresi → Excel → Drive) yürüyen süreci, ekibin kullanacağı bir **web uygulaması + REST API**'ye (ileride mobil) dönüştürüyoruz.

**Temel ilke:** Banka ekstresinden değil **servislerden** (ödenen tüm servislerin master listesi) giderek her ay hangi faturanın eksik olduğunu otomatik tespit et; mümkünse faturayı kendi bul/indir; bulamazsa ilgili kişiye/firmaya hatırlatma maili at; sonra Paraşüt'e gider kaydı gönder ve mali müşavirle paylaş.

## Teknoloji Yığını
| Katman | Teknoloji |
|--------|-----------|
| Backend / API | Java 21 + Spring Boot 3 (REST, Spring Security + JWT). Build: Maven (`./mvnw`) |
| Frontend | Angular (hazır bir admin arayüz teması) + TypeScript, HttpClient |
| Veritabanı | PostgreSQL (şema migrasyonu: Flyway veya Liquibase) |
| Dosya depolama | Dosya sistemi (`faturalar/` ay-klasör yapısı) + DB'de metadata/path |
| Ekstre/Excel okuma | Apache POI |
| Fatura okuma / OCR | PDF/XML parse + Tesseract (tess4j); gerekirse ayrı OCR mikroservisi |
| Panel scraping | Selenium / Playwright (Java worker); zorunlu hâllerde Python mikroservis |
| Mail | Gmail API / IMAP (`accounting@e-commint.com`) |
| Drive | Google Drive API veya rclone |
| Paraşüt | Paraşüt API / geliştirilmekte olan Paraşüt MCP |
| Çalıştırma | Docker Compose (api + db + frontend) |

## Klasör Yapısı (iki ayrı repo)
**Repo 1 — `account-hr` (backend + altyapı):**
```
account-hr/
├── backend/            # Spring Boot uygulaması
│   └── src/main/java/com/ecommint/accounthr/
│       ├── controller/ # REST uçları
│       ├── service/    # iş mantığı
│       ├── repository/ # JPA repository'leri
│       ├── domain/     # entity'ler
│       ├── dto/        # istek/yanıt modelleri
│       └── config/     # security, beans, vb.
├── docker-compose.yml  # db + api (+ adminer); frontend ayrı repoda
├── docs/               # mimari notlar
├── is_listesi/         # YouTrack görev tanımları
└── CLAUDE.md
```
**Repo 2 — `account-hr-frontend` (Angular):** kendi başına çalışan Angular projesi (`src/app/{core,layout,pages}`, kendi `.gitignore`, `package.json`). API'ye `environment.apiBaseUrl` üzerinden bağlanır.

## Domain Modeli (SERVICE-FIRST — en kritik tasarım)
Çekirdek varlık **Service** (servis/abonelik). Ana tablolar:
`services`, `providers`, `cards`, `expenses` (kart işlemleri), `invoices`, `invoice_status` (enum), `periods` (aylar), `users`, `teams`, `service_contacts` (servis → fatura sorumlusu e-posta), `files` (fatura dosyaları), `audit_log`.

`invoice_status` değerleri ve UI renkleri (mevcut sistemden korunur):
- **Bulundu** → yeşil (`4CAF50`)
- **e-Fatura** → açık yeşil (`8BC34A`)
- **Bekleniyor** → kırmızı (`FF4444`)
- **Araştırılacak** → turuncu (`FF9800`)
- **Ignored** → turuncu (`FF9800`)

Kartlar: Akbank Axess `****3800`, YKB Ticari `****3909`, Ziraat Bankkart `****9164`.

## Roller
- **Yönetici** — genel görünüm, tüm aylar, raporlar
- **Muhasebe** — fatura durumlarını izler, eksikleri takip eder, Paraşüt gönderimini görür
- **Ekip üyesi** — kendi satın aldığı servis/ayı seçip tutar girer, fatura yükler

## İş Kuralları (mevcut süreçten taşınacak — değiştirme)
- **Fatura tarihine göre klasörleme** (Nisan 2026'dan itibaren): dosya, fatura üzerindeki tarihin ayı klasörüne; Excel/DB satırı ödeme tarihine göre kalır.
- **Dosya isimlendirme:** `{hizmet_kisa}_{ay}.pdf` (lowercase, boşluksuz). Birden fazla → `_1`, `_2`. Bir satıra birden çok dosya olabilir (PDF + XML + statement).
- **Duplicate tespiti:** aynı invoice number → mükerrer; sadece bir kopya tutulur, diğeri trash'e.
- **waiting / trash akışı:** işlenmemiş faturalar `waiting/`; eşleşmeyen/duplicate `trash/` (silinmez).
- **Servisler çapraz doğrulama:** Aktif + Aylık her servis için o ay bir satır olmalı; eksikse "Bekleniyor" eklenir.
- Para: `BigDecimal`, tarih: `DD.MM.YYYY`. Bilgi-amaçlı kalemler (Multinet, sigorta, vergi) operasyonel TOPLAM'a dahil değil.
- **Dosya depolama kökü (storage root):** `STORAGE_ROOT` env değişkeninden (varsayılan `~/account-hr-data/faturalar`). **Repo dışı**, repoya commit edilmez. Uygulama başlangıçta kök + `waiting/` + `trash/` dizinlerini oluşturur.
- **`expenses/faturalar` Drive aynasıdır — ASLA dokunulmaz** (kaynak/yedek). account-hr bu dizine yazmaz/silmez/taşımaz.
- **Veri aktarımı = kopyalama** (E2-03'te): kaynak dosyalar storage root'a **kopyalanır**, orijinaller yerinde kalır. Silme/taşıma yok. `trash/` bile silme değil, taşımadır.

## Veri Migrasyonu
Kaynak: Google Drive `2026_Harcamalar.xlsx` (her ay bir sheet + Servisler master) + `faturalar/` klasörleri. Migrasyon işleri E2 epic'inde.

## Çalıştırma Komutları
```bash
docker compose up -d            # db + servisler
cd backend && ./mvnw spring-boot:run
cd frontend && npm install && npm start    # ng serve
# testler:
cd backend && ./mvnw test
cd frontend && npm test
```

## Kodlama Konvansiyonları
- Backend paket kökü: `com.ecommint.accounthr`. Entity ↔ DTO ayrımı net; iş mantığı `service` katmanında.
- REST sözleşmesi: `/api/v1/...`, tutarlı hata formatı (`{ "error": ..., "message": ... }`), OpenAPI/Swagger.
- JWT: `HttpInterceptor` ile token; 401'de logout.
- DB değişiklikleri Flyway/Liquibase migration olarak; elle şema değiştirme yok.
- Sırlar (Paraşüt/mail/Drive/panel login) **repoya commit edilmez** — `.env` / secret yönetimi. `.gitignore` ile koru.

## İş Takibi (YouTrack) — ÖNEMLİ
- Görev/backlog **YouTrack**'te: proje **IK / "Ön Muhasebe"** alt-projesi, issue **IK-225 … IK-276** (52 görev).
- Görev detayları (açıklama + kabul kriterleri/DOD) `expenses/is_listesi/E*.md` dosyalarında.
- **Commit mesajına issue kodunu yaz** (ör. `IK-241: eksik fatura ekranı API`) → commit otomatik olarak o YouTrack görevine bağlanır.
- Kod **git deposunda** (Bitbucket/GitHub/GitLab); YouTrack sadece takip aracıdır, kod oraya gitmez.

## Yol Haritası (özet)
E1 Altyapı/veri modeli → E2 Migrasyon → **E3 Web uygulaması (MVP: eksik fatura ekranı + yükleme)** → E4 Banka ekstresi → E6 Bildirim → E5 Otomatik toplama → E7 Paraşüt → E8 Raporlama → E9 Gelecek.
**MVP = E1 + E2 + E3.** İlk görev: **IK-225 / E1-01** (Spring Boot + Angular + PostgreSQL + Docker iskelet).

## İş Akışı: Code Review Loop (ZORUNLU — commit'ten ÖNCE)
Her iş/görev tamamlandıktan sonra, **commit ve push'tan ÖNCE** code review döngüsü çalıştırılır:
1. Değişen kod üzerinde code review çalıştır (Codex CLI: `codex exec --full-auto -s read-only 'Review this diff for bugs and security issues'`; alternatif `/code-review`).
2. Bulunan gerçek hataları/sorunları düzelt (yanlış pozitifleri gerekçesiyle ele; gerekirse testleri tekrar çalıştır).
3. Review'ı **tekrar** çalıştır.
4. Hata sayısı **0 olana kadar** 1–3 adımlarını döngüle.
5. **Yalnızca review temiz (0 hata) olunca** commit + push yapılır.
6. **Commit + push'tan SONRA**, ilgili YouTrack issue'su için **yapıştırılmaya hazır bir tamamlanma yorumu** üret ve kullanıcıya ver — her iş bitiminde, istenmese bile. İçerik: DOD checklist (işaretli), üretilenler, doğrulama sonuçları, kalan iş/borç. **Yorumlarda EMOJİ KULLANILMAZ** (düz metin; markdown checkbox `[x]` serbest). Hangi IK issue'suna yapıştırılacağı belirtilir.
- Önemli/gerçek bulgular düzeltilmeden commit YAPILMAZ. Düzeltme sonrası ilgili testler yeşil kalmalı.
- Doğrulama borcu kuralıyla birlikte: review temiz + testler geçer + (mümkünse) gerçek Postgres doğrulaması → sonra commit/push → sonra YouTrack yorumu.
- **Genel:** kullanıcıya verilen YouTrack/JIRA yorumlarında emoji kullanma.
7. **Push'tan SONRA CI'ı doğrula (ZORUNLU):** GitHub Actions olan repolarda push sonrası `gh run watch <id> --exit-status` ile run'ın YEŞİL olduğunu teyit et. Kırmızıysa `gh run view <id> --log-failed` ile gerçek hatayı çek, düzelt, tekrar push — **CI yeşil olana kadar.** CI kırmızı bırakılıp yeni göreve geçilmez. (`gh` ile teyit edilemiyorsa kullanıcıdan fail log'u iste.)
- **CI ortam farkı dersleri:** lokalde geçen testler CI'da patlayabilir → CI'ı taklit et (`-Dsurefire.runOrder=alphabetical` test sırası; `TZ=UTC`/`LANG=C` locale). Paylaşılan H2'de @SpringBootTest'ler veriyi sızdırır → testler FK-safe temizlenmeli (bkz. `AbstractDataCleanupIT`). `npm ci` lokal/CI npm sürüm-drift'inde lock-senkron hatası verebilir → workflow ve Dockerfile'da `npm ci || npm install` fallback.

## Çalışma Tarzı
Her sprint en az bir süreci bitiren, küçük adımlarla ilerleyen yaklaşım. Az kod bilen ekip üyeleri de takip edebilmeli; kod ve commitler açıklayıcı olmalı.
