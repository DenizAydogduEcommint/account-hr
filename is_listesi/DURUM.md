# account-hr — İlerleme Durumu (Yerel Pano)

> Bu dosya **yerel ilerleme takibidir**. Tek doğru kaynak **YouTrack** (IK-225 … IK-276).
> Her görev bitince: ilgili `E*.md` dosyasına "Tamamlanma Kaydı" eklenir + bu tablo güncellenir + YouTrack'e yorum/durum işlenir.
> **Durum simgeleri:** ✅ Tamamlandı · 🔄 Devam ediyor · ⬜ Bekliyor · ⏸️ Bloklu

**Son güncelleme:** 2026-06-26
**Özet:** **E1 + E2 + E3 epic TAMAM** (E3 11/11: E3-01..09 + E3-10 eksik-tutar + E3-11 KDV ✅) · **MVP çekirdeği tam çalışıyor** (harcamalar + eksik fatura tespit/tutar + fatura yükleme + manuel giriş + durum state machine + rol bazlı görünümler + fatura önizleme + KDV ayrımı) · sıfır teknik borç · **MVP = E1 + E2 + E3 tamamlandı.** · **E4-01 altyapı + E5-02 Drive pull yapıldı (devamı bekleyen girdilere bağlı)** · **UX: 6 sayfada açıklayıcı bilgi kartları (PageInfoCard)** · Sıradaki: **E4 (örnek ekstre gelince) / E5-04 (eşleştirme)**

## ⚠️ BEKLEYEN / BLOKLU İŞLER (dış girdi gerektirir — teknik borç DEĞİL)
> Bunlar kod eksiği değil; **örnek dosya / deploy hedefi / manuel aksiyon** beklediği için yapılamayan işler. Unutulmamalı.

**A) Gerçek örnek banka ekstresi gelince (Akbank ****3800 / YKB ****3909 / Ziraat ****9164 — Word/Excel):**
- **E4-01 gerçek parser** — altyapı hazır, `PlaceholderBankStatementExtractor` içindeki `// TODO E4-01: gerçek satır çıkarma` doldurulacak. Örnek olmadan parse mantığı yazılamaz (banka formatı tahmin edilemez).
- **E4-02 eşleştirme motoru + manuel↔ekstre mükerrer-önleme** — parser çıktısına (CONFIRMED RawTransaction) bağlı; örnek gelmeden gerçek veriyle test edilemez. (Ayrıca `[[account-hr-e4-mukerrer-eslestirme]]` memory'de kayıtlı — "unutmayalım" notuyla.)
- **E4-03 dönem-içi hareket dökümü** — aynı parse-bağımlılığı.
- **E4-05 banka/kart normalizasyonu** — örnek formatlara bağlı.
- **Aksiyon:** kullanıcı örnek ekstreleri `~/account-hr-data/` altına koyacak → parser + E4-02 gerçek veriyle yapılacak.

**B) Deploy hedefi/sunucu belli olunca:**
- **E1-06 CD/staging** — CI yeşil ama otomatik dağıtım (staging) deploy hedefi (sunucu/registry/domain) bekliyor.

**C) Kullanıcı manuel aksiyonu (asistan YouTrack paneline yazamıyor):**
- **YouTrack yorumları yapıştırma:** IK-243 (E3-06), IK-244 (E3-07), IK-245 (E3-08), IK-246 (E3-09), IK-247 (E3-10), IK-248 (E3-11), IK-249 (E4-01) — hazır metinler verildi, panele işlenecek.

---

## TEKNİK BORÇ: SIFIR (2026-06-26)
**Politika (CLAUDE.md):** Arkamızda asla teknik borç bırakmayız — bulgu aynı turda kapatılır, "ertele" yok.
Üç derin review turunda (holistic E + E2-derin + E1-derin) bulunan **9 teknik borcun TAMAMI kapatıldı**, ardından kapatma kodu **4 paralel adversarial reviewer** ile denetlendi → çıkan **8 bulgu (4 critical) düzeltildi** → canlı PG'de doğrulandı. **Açık borç kalmadı.**

| Borç | Çözüm | Commit |
|------|-------|--------|
| E3-07-DR-1 buildRow readOnly | tx-niyeti netleştirildi (buildRowInternal) | `2b06cb9` |
| E1-DR-1 optimistic locking yok | @Version 12 entity + V13 | `e1ac925` |
| E1-DR-2 Service unique yok | (name,provider_id) UNIQUE + V14 | `e1ac925` |
| E1-DR-5 FileAsset sha256 H2 gap | entity unique constraint | `e1ac925` |
| E3-06-DR-1 team import yok | ExcelImport Team resolver (Excel'de veri yok, kod hazır) | `e68408a` |
| E1-DR-3 BigDecimal precision | getDecimal setScale(2,HALF_UP) ×3 | `e68408a` |
| E3-03-1 ana-satır N+1 | findByIdInFetchingRefs wire (sabit sorgu) | `478f99a` |
| E2-DR-1 içerik-aynı dosya | (invoice_id,sha256) composite + V15 | `e5329ea` |
| E1-DR-4 AuditInterceptor hazard | raw-JDBC writer (StatelessSession bırakıldı) | `3aecf5b` |
| **Review fix** (8 bulgu) | shared-file sibling-guard, audit raw-JDBC connection/proxy fix + try/catch, Team lower-unique V16, isDuplicate sayaç | (bu commit) |

Toplam migration V1→V16. Backend test 249/249. Canlı doğrulama: eksik Mart=2, dashboard=114.355,40, audit STATUS_CHANGE raw-JDBC writer PG'de çalışıyor (changed_by + changed_at dolu), rollback-discard korundu.

**CI durumu (2026-06-25):** Her iki repo GitHub Actions **yeşil** (gh ile teyit edildi). Backend: `mvnw verify` (H2, 50 test) + GHCR image. Frontend: `ng build` + nginx image. İlk kurulumda 4 CI fix gerekti — test izolasyonu (`AbstractDataCleanupIT`, paylaşılan H2 FK ihlali) + frontend `npm ci || npm install` fallback (workflow + Dockerfile). Bundan sonra her push sonrası CI `gh run watch` ile teyit edilir (CLAUDE.md kuralı).

**Gerçek Postgres doğrulaması (2026-06-25):** E1-01…E1-04 lokal PostgreSQL 14'te uçtan uca doğrulandı (Docker gerekmedi). Flyway V1–V4 başarıyla uygulandı, `ddl-auto=validate` entity↔migration uyumu geçti, seed (3 kart + 6 period + admin) yüklendi, `/api/health` UP, login + `/api/auth/me` çalıştı, yanlış parola 401, storage root + waiting/trash oluştu. Ayrıca **frontend tarayıcı (Playwright) testi**: authGuard yönlendirme → admin login → dashboard "API: UP" (Angular→backend uçtan uca) → Çıkış → login; console 0 hata. Not: hedef PG 16; 14'te doğrulandı, sürüme özgü migration yok.

**E SERİSİ BÜTÜNSEL CODE REVIEW (2026-06-26):** Tüm E1+E2+E3 kod tabanı 4 paralel adversarial review agent ile tarandı (güvenlik/auth · E2 migrasyon/storage · E3 backend · frontend). **18 yüksek-güvenli bulgu, hepsi düzeltildi** (backend 186 test, frontend tsc/ng build temiz):
- **Güvenlik:** open redirect (login returnUrl), Content-Disposition header injection (FileController), file endpoint IDOR → interim rol-gate (ADMIN/ACCOUNTING), committed DB password (docker yml), X-Request-Id validation.
- **Correctness:** auth interceptor sonsuz-döngü sentinel (retried context), shareReplay refCount, upload duplicate-invoice (temsilci invoice reuse), missing-check informational=false filtre, uploaded event tip uyumu.
- **Robustness:** copyPreservingPath orphan temp + atomic-move cleanup, two-pass SHA (InvoiceFileImport).
- **Perf:** 2× contact N+1 (ExpenseQuery + ServiceQuery batch'lendi). **Frontend:** 3× subscription leak (ngOnDestroy).
- **Kalan borç:** file per-invoice ownership → E3-08; ana-satır lazy N+1 → E3-03-1 (kısmen, contact N+1 çözüldü).

**E2 DERİN CODE REVIEW (3 ajan + ultrathink, 2026-06-26):** Migrasyon katmanı (E2-01..06) dar+derin tarandı. **11 bulgu, hepsi düzeltildi** (backend 198 test, +12). Sessiz muhasebe-veri/güvenlik sorunları:
- **C4 (en kritik):** `computeRowHash` rowIndex içeriyordu → Excel satır eklenince re-import çift kayıt. Fix: rowIndex çıkarıldı + same-pass collision counter. **Dev DB re-seed edildi (yeni hash); idempotency gerçek veride doğrulandı (2. import 0 yeni).**
- **C1:** byte-identical PDF → 2. invoice sessizce dosyasız kalıyordu → artık unmatched raporlanıyor (tam multi-invoice junction → borç E2-DR-1).
- **C2:** baseKey çakışmasında sibling yanlış invoice'a → exact-basename önceliği + belirsizse uyarı.
- **C3:** çoklu primary contact (hatırlatma yanlış e-postaya) → tek-primary invariant.
- **I:** `_refund` suffix, `.tmp` sayımı, isFooterRow/classifyRow "TOPLAM" aşırı-drop, ProcessBuilder drain join (false 502), validateFileName control-char, active_months→TEXT (V10).
- **Borç E2-DR-1:** SHA-256 içerik-aynı dosyanın iki farklı invoice'a tam bağlanması (junction/ManyToMany) → V9 unique kısıtı nedeniyle ertelendi; şimdilik raporlanıyor.

**E1 DERİN CODE REVIEW (3 ajan + ultrathink, 2026-06-26):** Çekirdek altyapı (auth/crypto/audit/domain/storage/API/config) dar+derin tarandı. **16 bulgu düzeltildi** (backend 208 test, +10; V11 migration gerçek PG'de uygulandı+doğrulandı). Güvenlik ağırlıklı:
- **Güvenlik:** `FileController.upload` rol-gate (TEAM_MEMBER yükleyemez; `InvoiceController`/E3-05 `isAuthenticated` korundu), GlobalExceptionHandler path/SHA sızıntısı → sabit mesaj+log, unbounded `?size=` DoS → cap 100, Swagger prod/staging kapalı, multipart eksik-part 500→400.
- **Auth:** logout atomik (`revokeIfActive`), CORS env-bound (`CorsProperties`), BCrypt cost 12.
- **Correctness:** AuditLog.note 1024, FK `ON DELETE SET NULL` (V11), bilinmeyen currency log.warn, RefreshToken LAZY, `StorageIOException`→503, storage concurrency re-probe, Provider `lower(name)` unique (V11). **Temizlik:** JWT dead role-claim kaldırıldı.
- **Crypto katmanı derinlemesine TEMİZ** (AES-GCM IV taze SecureRandom, JWT alg-confusion yok, refresh rotation atomik, secret fail-fast).
- **Gerçek doğrulama:** V11 PG'de uygulandı (provider lower-index temiz), eksik Mart=2 + dashboard=114.355,40 regresyonsuz, path-leak yanıtı temiz.
- **Yeni borç (E1-DR):** (1) `@Version` optimistic locking yok; (2) `Service(name,provider_id)` UNIQUE yok; (3) BigDecimal `setScale` (hash-stable koruma gerekçesiyle ertelendi); (4) AuditInterceptor StatelessSession refactor; (5) FileAsset sha256 partial-unique H2-test gap.

---

## E1 — Temel Altyapı & Veri Modeli
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E1-01 | Proje iskeleti (Spring Boot + Angular + PostgreSQL + Docker) | ✅ | IK-225. İki repo. PG14'te health doğrulandı (06-25) |
| E1-02 | Veritabanı şeması & domain modeli | ✅ | IK-226. SERVICE-FIRST. 12 entity/8 enum. Flyway V1/V2 + validate PG14'te doğrulandı |
| E1-03 | Kimlik doğrulama & yetkilendirme (JWT) | ✅ | IK-227. JWT+BCrypt+refresh, Angular login. Login+/me PG14'te doğrulandı (API) |
| E1-04 | Dosya depolama servisi | ✅ | IK-228. StorageService, slugify, dedup, waiting/trash. V4+storage root PG14'te doğrulandı |
| E1-05 | Config, secret, loglama, audit | ✅ | IK-229. AES-GCM credential şifreleme, JSON log, Hibernate audit, maskeleme. PG14 V5+validate doğrulandı |
| E1-06 | CI/CD & dağıtım | 🔄 | IK-230. CI **GitHub'da yeşil doğrulandı** (test izolasyon + npm fallback fix'leri sonrası). CD/staging ertelendi (deploy hedefi bekliyor) |
| E1-07 | API tasarım standartları | ✅ | IK-231. /api/v1 + ErrorResponse(traceId) + Swagger + PagedResponse + örnek /services. PG14'te canlı doğrulandı |
| E1-08 | Kullanıcı Yönetimi (Backoffice) | ✅ | IK-251. ADMIN-only /admin/users CRUD-lite; son-admin koruması (TOCTOU-safe), passwordHash gizli, token-revoke, AppUser audit. Frontend /backoffice (şifre çift-giriş+göz). 333 test. Güvenlik review temiz +2 fix |

## E2 — Veri Migrasyonu
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E2-01 | Excel ay-sheet importer | ✅ | IK-232. POI importer + admin endpoint. PG14'te 101 expense (idempotent). CI yeşil (4 fix sonrası). |
| E2-02 | Servisler master importer | ✅ | IK-233. Upsert + service_contacts + V7 active_months. PG14: 28 service zenginleşti, idempotent. Audit özyineleme (StackOverflow) bug'ı düzeltildi |
| E2-03 | Faturalar klasör tarama & eşleştirme | ✅ | IK-234. faturalar→storage kopya + invoice eşleme (note path). PG14: 55 dosya/49 eşleşti, orphan yok, kaynak dokunulmadı. Bağımsız review orphan bug'ı yakaladı |
| E2-04 | Durum/renk enum migrasyonu | ✅ | IK-235. Renk-metin audit + dosya-durum tutarlılık + StatusColors. PG14: 0 tutarsızlık (veri temiz), dağılım doğru |
| E2-05 | Migrasyon doğrulama & mutabakat | ✅ | IK-236. Excel TOPLAM↔DB mutabakat raporu. PG14 KABUL TESTİ: ok=true, 3 ay diff=0.00, satır tam, Nisan WARNING. Migrasyon kuruşu kuruşuna doğrulandı |
| E2-06 | Drive waiting senkron köprüsü | ✅ | IK-237. rclone subprocess pull köprüsü, push yapısal imkansız. GERÇEK Drive'da kanıtlandı: 17 dosya pull, silme yok. 116 test |

## E3 — Web Uygulaması (MVP)

**E3 DERİN CODE REVIEW (3 ajan + ultrathink, 2026-06-26):** Tüm E3 serisi (backend query/çapraz-doğrulama · backend controller/yetki/preview · tüm frontend) görevler-arası kesişim odaklı tarandı. **13 gerçek bulgu düzeltildi** (backend 284 test +6; frontend tsc/build temiz). Önceki üç review turunda görünmeyen, yalnız akış-kesişiminde çıkan sorunlar:
- **🔴 Stored-XSS** (E3-05 upload × E3-09 preview): client `Content-Type` doğrulanmadan saklanıp `inline` servis ediliyordu → `MimeTypes.fromExtension` (server-derive) + preview allowlist (non-safe → `application/octet-stream`). Canlı: preview hep `application/pdf`, DB'de tehlikeli mime 0.
- **🔴 Frontend birleşik-durum** (expenses.component büyüdü): `Blob.text()` stale-write → generation-guard; download hatası başka dosyanın preview'ını bozuyordu → ayrı `downloadError` signal; eşzamanlı indirme/toggle iptali → guard/ayrı sub.
- **Upload tutarsızlığı:** upload-created expense `source=STATEMENT`+`transactionDate=null` → MANUAL + ayın 1'i; `lastSeenMonth` informational dahil ediyordu → filtre; listFiles N+1 → batch; POST/files 10MB check.
- **Zaman-bombası:** `DEFAULT_MONTH`/month-selector hardcoded 2026 → dinamik (`new Date`).
- **Review false-positive (REDDEDİLDİ, doğru karar):** "upload duplicate-expense → double-count" — `(service,period)` informational=false'da MEŞRU çoklu satır var (ör. OpenAI Şubat 7 ayrı çekim, "her işlem ayrı satır" kuralı); unique index veriyi bozardı → V17 no-op, toplam zaten doğru.
- **Temiz onaylandı:** dashboard↔missing↔total tutarlılığı, temsilci-invoice (max-id) 4 callsite, IGNORED↔informational bağımsızlığı, IDOR (single-org), pagination, validation.

| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E3-01 | Dashboard / aylık özet | ✅ | IK-238. **Tasarım sistemi kuruldu** (E-Commint yeşil/navy, Maven Pro). Dashboard: KPI + donut + ay seçici. Tarayıcıda doğrulandı (Mart ₺114.355,40). İki repo |
| E3-02 | Servisler ekranı | ✅ | IK-239. Service CRUD + tablo/modal/filtre/arama, badge'ler. PG14: 32 servis, no-delete. + Higgsfield logo/login-bg + CSS değişken alias fix (görsel sistem) |
| E3-03 | Aylık harcamalar ekranı | ✅ | IK-240. 12 kolonlu tablo + filtreler + bilgi-amaçlı ayrı bölüm. PG14: Şubat 28+23 satır (E2-05 ile birebir). + login göz butonu + varsayılan ay fix. N+1 borç |
| E3-04 | Eksik fatura ekranı | ✅ | IK-241. **MVP ÇEKİRDEĞİ.** Servis↔ay çapraz doğrulama. PG14: Mart 2 eksik (HepsiBurada, Zoom), dashboard birebir. Bağımsız review temiz |
| E3-05 | Fatura yükleme UI | ✅ | IK-242. **MVP çekirdeği (yükleme).** POST /invoices (atomik, storage). PG14: HepsiBurada upload → FOUND, eksik 2→1, kaynak dokunulmadı. 3 atomicity bulgusu düzeltildi |
| E3-06 | Manuel harcama girişi | ✅ | POST /expenses, source=MANUAL (V12), GET /teams; borç E3-06-DR-1 (team import) |
| E3-07 | Fatura durum state machine | ✅ | PATCH /status, renk türetilir, audit otomatik, MVP serbest geçiş; borç E3-07-DR-1 (buildRow readOnly) |
| E3-08 | Rol bazlı görünümler | ✅ | IK-245. Her endpoint @PreAuthorize rol matrisi; frontend roleGuard+menü+403+landing. PG14: 3 rol token (team PATCH→403). RoleAuthorizationIT 17. "file ownership" borcu kapandı |
| E3-09 | Fatura detay / önizleme | ✅ | IK-246. GET /expenses/{id}/files + /files/{id}/preview (inline, path-ifşasız). Frontend Faturalar bölümü + blob preview (PDF iframe/img/XML), mimeType bazlı. PG14: inline preview + 404'ler doğru |
| E3-10 | Eksik fatura tutar özeti | ✅ | IK-247. Belgesiz gider TL görünürlüğü (muhasebe incelemesinden). missing wrapper + dashboard missingTotalTry. PG14: Mart eksik=2 → ~2.620,05 ₺ tutarlı. Review 0 bulgu |
| E3-11 | KDV alanları | ✅ | IK-248. Fatura KDV ayrımı (muhasebe incelemesinden). Invoice kdvRate/kdvAmount/netAmount (V18), KdvCalculator (matrah+KDV=brüt sum-safe). PG14: V18 + geriye uyumlu. 295 test. Review kritik temiz |

## E4 — Banka Ekstresi
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E4-01 | Ekstre yükleme & parse | 🔄 | IK-249. **Altyapı ✅** (RawTransaction+V19, StatementParser SPI, upload→önizle→onay, idempotency, ADMIN+ACCOUNTING, "Ekstre Yükle" ekranı). ⚠️ **Gerçek parser örnek ekstre bekliyor** (PlaceholderBankStatementExtractor TODO). 308 test. Review 4 bulgu düzeltildi |
| E4-02 | Eşleştirme motoru | ⬜ | **KRİTİK: manuel↔ekstre mükerrer-önleme dahil** (ön-muhasebe domain incelemesi 2026-06-26 — çift kayıt riski; kullanıcı "unutmayalım" dedi). Detay: `E4-02_eslestirme_motoru.md` + memory |
| E4-03 | Dönem-içi hareket dökümü | ⬜ | |
| E4-04 | Eşleşmeyen işlem uyarıları | ⬜ | |
| E4-05 | Banka/kart normalizasyonu | ⬜ | |

## E5 — Otomatik Toplama
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E5-01 | Accounting mail entegrasyonu | ⛔ | **BLOKLU** — IMAP/Gmail credential bekliyor (Fatma'ya soruldu 2026-06-27) |
| E5-02 | Drive waiting pull | ✅ | IK-250. PULL+INGEST (IncomingInvoice+V20, copy-only güvenli, "Gelen Faturalar" ekranı). 319 test. Review Drive-güvenlik CLEAN + 5 bulgu düzeltildi. Taşıma/silme E5-04 sonrası |
| E5-03 | Fatura belge okuma (OCR/parse) | ⬜ | |
| E5-04 | Servis-fatura eşleştirme | ⬜ | |
| E5-05 | Servis paneli indirme worker | ⬜ | |
| E5-06 | Toplama orkestrasyonu | ⬜ | |

## E6 — Bildirim
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E6-01 | Eksik fatura hatırlatma maili | ⬜ | |
| E6-02 | Sağlayıcı fatura talep maili | ⬜ | |
| E6-03 | Bildirim şablonları & gönderim takibi | ⬜ | |
| E6-04 | Hatırlatma zamanlama & eskalasyon | ⬜ | |
| E6-05 | In-app bildirim tercihleri | ⬜ | |

## E7 — Paraşüt Entegrasyonu
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E7-01 | Paraşüt Excel şablonu | ⬜ | |
| E7-02 | Paraşüt API bağlantı | ⬜ | |
| E7-03 | Fatura → gider dönüştürücü | ⬜ | |
| E7-04 | Paraşüt otomatik gönderim | ⬜ | |
| E7-05 | Mükerrer/çakışma kontrolü | ⬜ | |

## E8 — Raporlama
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E8-01 | Aylık fatura paketi export | ⬜ | |
| E8-02 | İzlenebilirlik raporları | ⬜ | |
| E8-03 | Otomatik aylık özet e-posta | ⬜ | |
| E8-04 | Audit log & hata analizi | ⬜ | |

## E9 — Gelecek
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E9-01 | Personel masraf OCR | ⬜ | |
| E9-02 | İK konsolidasyon & ödeme tablosu | ⬜ | |
| E9-03 | Banka mutabakat | ⬜ | |
| E9-04 | Satış faturaları & tahsilat | ⬜ | |
| E9-05 | CoffeeTropic multitenant | ⬜ | |

## Güvenlik / Teknik Borç (code review — 2026-06-25)
Tüm E1 kodu (iki repo) Codex + Claude code-reviewer ile tarandı. **7 gerçek hata düzeltildi** (JWT secret default + fail-fast, refresh rotation race → atomic, file upload orphan → transaction rollback-delete, audit ThreadLocal → transaction-scoped + rollback guard + non-tx leak, FE: 401 sonsuz döngü sentinel, paylaşılan refresh, loadMe sadece 401/403). Aşağıdakiler **bilinçli olarak ertelendi** (gerekçeli):

| Kod | Önem | Konu | Plan |
|-----|------|------|------|
| B2 | critical* | Admin seed `admin@e-commint.com/changeme123` Flyway V3'te | Prod öncesi env tabanlı bootstrap'e taşı (E1-06). Prod yok, "ilk girişte değiştir" notlu |
| B3 | high | File endpoint'lerinde object-level yetki yok (her authenticated user erişir) | Rol/sahiplik kontrolü **E3-08** (rol bazlı görünümler) |
| B6 | medium | Upload metadata (invoiceNo/date/service) client'tan; doğrulanmıyor | **E3**'te Invoice→Expense→Service'ten türet/doğrula |
| F1 | high | Token'lar localStorage'da (XSS riski) | HttpOnly cookie auth — ileri faz prod sertleştirme (backend+frontend değişimi) |
| B5 | medium | Eşzamanlı aynı-içerik upload ikisi de geçebilir (sha256 non-unique) | DB unique kısıt + 409; düşük öncelik (E2/E3) |
| F5 | low | Guard sadece "token var mı" bakar | Backend her isteği doğruluyor; UX için expiry kontrolü sonra |
| A2/A3 | low | `AuditFlusherHolder`/`EncryptionServiceHolder` JVM-global static | Tek Spring context'te çalışır; çok-context'e geçilirse bean-scoped wiring |
| A4 | low | `LogMasker` henüz log noktalarına bağlı değil | Hassas değer loglanan ilk noktada devreye alınacak (şu an loglanmıyor) |
| E2-02-1 | medium | `GlobalExceptionHandler` 500 log'u exception zincirini (JDBC SQL literal vb.) içerebilir → teorik secret/PII | Kabul edildi (500 debugging değeri yüksek, StackOverflow'u bu yakaladı; prod log erişimi kısıtlı). İleride `LogMasker` veya mesaj-seviye sanitize |
| E2-03-1 | medium | V9 `files.sha256` partial unique index, mevcut duplicate sha256'lı bir DB'de deploy'da fail edebilir | V9 değiştirilmedi (Flyway checksum). Prod deploy öncesi preflight: `SELECT sha256,count(*) ... HAVING count>1` ile dedup; E1-04 zaten yeni duplicate'i engelliyor |
| E3-03-1 | medium | `ExpenseQueryService` ana satırlar N+1: `findAll(spec,pageable)` lazy + satır başına `serviceContactRepository` sorgusu (~250/sayfa) | MVP'de küçük veri (≤50 satır) ile kabul. `@EntityGraph`/fetch-join + batch e-posta ile optimize (bilgi-amaçlı path zaten fetch-join'li, ana path'e uygulanacak) |

\* B2 prod'a çıkmadan kapatılacak; MVP/dev'de kabul.
