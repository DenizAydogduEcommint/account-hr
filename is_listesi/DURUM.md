# account-hr — İlerleme Durumu (Yerel Pano)

> Bu dosya **yerel ilerleme takibidir**. Tek doğru kaynak **YouTrack** (IK-225 … IK-276).
> Her görev bitince: ilgili `E*.md` dosyasına "Tamamlanma Kaydı" eklenir + bu tablo güncellenir + YouTrack'e yorum/durum işlenir.
> **Durum simgeleri:** ✅ Tamamlandı · 🔄 Devam ediyor · ⬜ Bekliyor · ⏸️ Bloklu

**Son güncelleme:** 2026-06-25
**Özet:** 7 / 52 tamamlandı (+ E1-06 CI kısmı) · **E1 esas olarak bitti**, **E2 başladı** (E2-01 ✓) · **MVP = E1 + E2 + E3**

**Gerçek Postgres doğrulaması (2026-06-25):** E1-01…E1-04 lokal PostgreSQL 14'te uçtan uca doğrulandı (Docker gerekmedi). Flyway V1–V4 başarıyla uygulandı, `ddl-auto=validate` entity↔migration uyumu geçti, seed (3 kart + 6 period + admin) yüklendi, `/api/health` UP, login + `/api/auth/me` çalıştı, yanlış parola 401, storage root + waiting/trash oluştu. Ayrıca **frontend tarayıcı (Playwright) testi**: authGuard yönlendirme → admin login → dashboard "API: UP" (Angular→backend uçtan uca) → Çıkış → login; console 0 hata. Not: hedef PG 16; 14'te doğrulandı, sürüme özgü migration yok.

---

## E1 — Temel Altyapı & Veri Modeli
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E1-01 | Proje iskeleti (Spring Boot + Angular + PostgreSQL + Docker) | ✅ | IK-225. İki repo. PG14'te health doğrulandı (06-25) |
| E1-02 | Veritabanı şeması & domain modeli | ✅ | IK-226. SERVICE-FIRST. 12 entity/8 enum. Flyway V1/V2 + validate PG14'te doğrulandı |
| E1-03 | Kimlik doğrulama & yetkilendirme (JWT) | ✅ | IK-227. JWT+BCrypt+refresh, Angular login. Login+/me PG14'te doğrulandı (API) |
| E1-04 | Dosya depolama servisi | ✅ | IK-228. StorageService, slugify, dedup, waiting/trash. V4+storage root PG14'te doğrulandı |
| E1-05 | Config, secret, loglama, audit | ✅ | IK-229. AES-GCM credential şifreleme, JSON log, Hibernate audit, maskeleme. PG14 V5+validate doğrulandı |
| E1-06 | CI/CD & dağıtım | 🔄 | IK-230. CI tamam (GitHub Actions test/build + GHCR image, actionlint 0 hata). CD/staging ertelendi (deploy hedefi bekliyor) |
| E1-07 | API tasarım standartları | ✅ | IK-231. /api/v1 + ErrorResponse(traceId) + Swagger + PagedResponse + örnek /services. PG14'te canlı doğrulandı |

## E2 — Veri Migrasyonu
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E2-01 | Excel ay-sheet importer | ✅ | IK-232. POI importer + admin endpoint. PG14'te gerçek veri: 101 expense, idempotent. |
| E2-02 | Servisler master importer | ⬜ | |
| E2-03 | Faturalar klasör tarama & eşleştirme | ⬜ | |
| E2-04 | Durum/renk enum migrasyonu | ⬜ | |
| E2-05 | Migrasyon doğrulama & mutabakat | ⬜ | |
| E2-06 | Drive waiting senkron köprüsü | ⬜ | |

## E3 — Web Uygulaması (MVP)
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E3-01 | Dashboard / aylık özet | ⬜ | |
| E3-02 | Servisler ekranı | ⬜ | |
| E3-03 | Aylık harcamalar ekranı | ⬜ | |
| E3-04 | Eksik fatura ekranı | ⬜ | MVP çekirdeği |
| E3-05 | Fatura yükleme UI | ⬜ | MVP çekirdeği |
| E3-06 | Manuel harcama girişi | ⬜ | |
| E3-07 | Fatura durum state machine | ⬜ | |
| E3-08 | Rol bazlı görünümler | ⬜ | |
| E3-09 | Fatura detay / önizleme | ⬜ | |

## E4 — Banka Ekstresi
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E4-01 | Ekstre yükleme & parse | ⬜ | |
| E4-02 | Eşleştirme motoru | ⬜ | |
| E4-03 | Dönem-içi hareket dökümü | ⬜ | |
| E4-04 | Eşleşmeyen işlem uyarıları | ⬜ | |
| E4-05 | Banka/kart normalizasyonu | ⬜ | |

## E5 — Otomatik Toplama
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E5-01 | Accounting mail entegrasyonu | ⬜ | |
| E5-02 | Drive waiting pull | ⬜ | |
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

\* B2 prod'a çıkmadan kapatılacak; MVP/dev'de kabul.
