# account-hr — Ön Muhasebe Fatura Toplama & Eksik Fatura Yönetim Platformu
## Genel Bakış & Yol Haritası

> Bu klasör, E-Commint ön muhasebe otomasyon projesinin **geliştirmeye hazır iş listesidir**.
> Her `.md` dosyası tek bir YouTrack issue'suna karşılık gelir. Bu dosya genel resmi, epic yapısını,
> sprint planını ve dosya indeksini verir.

---

## 1. Problem & Amaç

E-Commint çok sayıda yurtdışı abonelik/hizmet alıyor (Claude/Anthropic, OpenAI, Zoom, Contabo, domain'ler vb.). Faturalar ya tanımlı bir e-postaya geliyor ya da servis panelinden indiriliyor; çoğu zaman kaçıyor. Her ay muhasebe (Talha + dış mali müşavir) "faturalar eksik" diyor ama hangisinin eksik olduğunu söylemiyor; Selman Çelebi tek tek araştırıyor.

Bugün bu iş, Kaan Bingöl'ün gönderdiği kredi kartı ekstreleri (Word/Excel) üzerinden Claude Code + Google Drive + Excel ile **manuel** yürüyor. Herkes erişemiyor, tek kişiye bağımlı.

**Hedef:** Banka ekstresinden değil **servislerden** (ödenen tüm servislerin master listesi) giderek her ay hangi faturanın eksik olduğunu otomatik tespit eden; mümkünse faturayı kendi bulan/indiren; bulamazsa ilgili kişiye/firmaya hatırlatma maili atan; sonra faturaları Paraşüt'e gider kaydı olarak gönderen ve mali müşavirle paylaşan bir **web uygulaması + API** (ileride mobil). Çalışma adı: **account-hr**.

**Temel ilke (Kaan):** Tek seferde büyük proje değil — her sprint en az bir süreci otomatize eden, az kod bilen ekip üyelerinin de anlayacağı, agile/adım-adım yaklaşım.

---

## 2. Teknoloji Yığını

| Katman | Teknoloji |
|--------|-----------|
| Backend / API | **Java + Spring Boot** (REST, Spring Security/JWT; mobil için de aynı API) |
| Frontend | **Angular** (hazır bir admin arayüz teması) |
| Veritabanı | **PostgreSQL** |
| Dosya depolama | Dosya sistemi (mevcut `faturalar/` ay-klasör yapısı korunur) + DB'de metadata/path |
| Ekstre/Excel okuma | Apache POI (Word/Excel) |
| Fatura okuma/OCR | PDF/XML parse + Tesseract (tess4j); gerekirse ayrı OCR mikroservisi |
| Panel scraping | Selenium/Playwright (Java worker); zorunlu hâllerde Python mikroservis |
| Mail | Gmail API / IMAP (`accounting@e-commint.com`) |
| Drive | rclone (mevcut akış) veya Google Drive API |
| Paraşüt | Paraşüt API / geliştirilmekte olan Paraşüt MCP |

> Frontend kararı: kullanıcı tercihiyle **Angular**. Hangi hazır admin arayüz teması/UI kit kullanılacağı E1-01'de netleşir (kullanıcı tercihine bağlı).

---

## 3. Roller

- **Yönetici / Selman** — genel görünüm, tüm aylar, raporlar.
- **Muhasebe / Talha** — fatura durumlarını görür, eksikleri takip eder, Paraşüt'e gönderimi izler.
- **Ekip üyesi** — kendi satın aldığı servis/ayı seçip tutar girer ve fatura yükler.

---

## 4. Epic Yapısı

| Epic | Ad | Kapsam | Faz |
|------|-----|--------|-----|
| **E1** | Temel Altyapı & Veri Modeli | Spring Boot + Angular + PostgreSQL iskele, service-first şema, auth/rol, dosya depolama, config/secret, CI/CD, API standartları | Temel |
| **E2** | Veri Migrasyonu | Drive/Excel (ay sheet'leri + Servisler master) + `faturalar/` dosyaları → PostgreSQL + dosya sistemi | Temel |
| **E3** | Fatura Yönetim Web Uygulaması | Dashboard, servisler, aylık harcamalar, **eksik fatura ekranı**, fatura yükleme, durum yönetimi, roller | **MVP** |
| **E4** | Banka Ekstresi İşleme | Word/Excel ekstre parse, işlem↔servis/fatura eşleştirme, dönem-içi döküm, uyarılar | MVP+ |
| **E5** | Otomatik Fatura Toplama | accounting@ mailbox, Drive waiting pull, belge okuma/OCR, otomatik eşleştirme, panel login/indirme, orkestrasyon | Otomasyon |
| **E6** | Hatırlatma & Bildirim | Eksik fatura → kişi/sağlayıcı maili, şablonlar, eskalasyon, in-app bildirim | MVP+ |
| **E7** | Paraşüt Entegrasyonu | IK-167 Excel şablonu, API/MCP bağlantısı, fatura→gider dönüştürücü, otomatik gönderim, mükerrer kontrolü | Otomasyon |
| **E8** | Mali Müşavir Paylaşımı & Raporlama | Aylık paket export, izlenebilirlik raporları, otomatik özet, audit/hata analizi | Otomasyon |
| **E9** | Gelecek Vizyon (outline) | Personel masraf OCR, İK konsolidasyon, banka mutabakatı, satış/tahsilat, Coffeetropic multi-tenant | Sonraki çeyrek |

---

## 5. Sprint Planı (öneri)

| Sprint | Odak | Ana görevler |
|--------|------|--------------|
| **Sprint 1** | Temel | E1-01, E1-02, E1-07 |
| **Sprint 2** | Temel + MVP başlangıcı | E1-03, E1-04, E1-05, E1-06, E2-01, E2-02, E3-02, E3-03, E3-07 |
| **Sprint 3** | Migrasyon tamam + MVP çekirdek | E2-03, E2-04, E2-05, E2-06, **E3-04**, **E3-05**, E3-01 |
| **Sprint 4** | MVP tamam + ekstre + bildirim | E3-06, E3-08, E3-09, E4-01, E4-02, E4-05, E6-01, E6-03 |
| **Sprint 5** | Ekstre + bildirim + otomasyon başlangıcı | E4-03, E4-04, E6-02, E6-04, E6-05, E5-01, E5-02 |
| **Sprint 6** | Otomatik toplama + Paraşüt | E5-03, E5-04, E5-06, E7-01, E7-02 |
| **Sprint 7** | Otomasyon derinleşme + raporlama | E5-05, E7-03, E7-04, E8-01, E8-02 |
| **Sprint 8** | Paraşüt tamam + raporlama | E7-05, E8-03, E8-04 |
| **Backlog** | Sonraki çeyrek | E9-01 … E9-05 |

> **MVP tanımı:** Sprint 1–3 sonunda ekip, **mevcut acı noktayı** çözmüş olur — uygulamada o ayın eksik faturaları görünür (E3-04), ekip üyeleri fatura yükleyebilir (E3-05), tüm geçmiş veri migrate edilmiştir (E2). Otomatik toplama ve Paraşüt sonraki fazlardır.

---

## 6. En Kritik 4 Görev (acil acı nokta)

1. **E1-02** — Service-first veri modeli (her şeyin temeli).
2. **E3-04** — Eksik fatura ekranı: Servisler ↔ ay çapraz doğrulama. *Projenin kalbi.*
3. **E3-05** — Fatura yükleme UI (ekip üyesi self-servis).
4. **E6-01** — Eksik fatura → ilgili kişiye otomatik hatırlatma maili.

---

## 7. Dosya İndeksi

**E1 — Temel Altyapı & Veri Modeli**
`E1-01` Proje iskeleti (Spring Boot + Angular + PostgreSQL + Docker) · `E1-02` Veritabanı şeması/domain (service-first) · `E1-03` Kimlik doğrulama & yetkilendirme · `E1-04` Dosya depolama servisi · `E1-05` Config/secret/loglama/audit · `E1-06` CI/CD & dağıtım · `E1-07` API tasarım standartları

**E2 — Veri Migrasyonu**
`E2-01` Excel ay sheet importer · `E2-02` Servisler master importer · `E2-03` faturalar/ klasör tarama & eşleştirme · `E2-04` Durum/renk → enum migrasyonu · `E2-05` Migrasyon doğrulama/mutabakat · `E2-06` Drive waiting senkron köprüsü

**E3 — Fatura Yönetim Web Uygulaması (MVP)**
`E3-01` Dashboard · `E3-02` Servisler ekranı · `E3-03` Aylık harcamalar ekranı · **`E3-04` Eksik fatura ekranı (KİLİT)** · **`E3-05` Fatura yükleme UI** · `E3-06` Manuel harcama girişi · `E3-07` Fatura durum state machine · `E3-08` Rol bazlı görünümler · `E3-09` Fatura detay/önizleme

**E4 — Banka Ekstresi İşleme**
`E4-01` Ekstre yükleme & parse · `E4-02` Eşleştirme motoru · `E4-03` Dönem-içi hareket dökümü · `E4-04` Eşleşmeyen işlem uyarıları · `E4-05` Banka/kart normalizasyonu

**E5 — Otomatik Fatura Toplama**
`E5-01` accounting@ mail entegrasyonu · `E5-02` Drive waiting pull · `E5-03` Fatura belge okuma/OCR · `E5-04` Servis-fatura eşleştirme · `E5-05` Panel login & indirme worker · `E5-06` Toplama orkestrasyonu

**E6 — Hatırlatma & Bildirim**
`E6-01` Eksik fatura hatırlatma maili · `E6-02` Sağlayıcı fatura talep maili · `E6-03` Bildirim şablonları & gönderim takibi · `E6-04` Hatırlatma zamanlama/eskalasyon · `E6-05` In-app bildirim & tercihler

**E7 — Paraşüt Entegrasyonu**
`E7-01` Paraşüt Excel şablonu (IK-167) · `E7-02` Paraşüt API/MCP bağlantı · `E7-03` Fatura→gider dönüştürücü · `E7-04` Otomatik gönderim · `E7-05` Mükerrer/çakışma kontrolü

**E8 — Mali Müşavir Paylaşımı & Raporlama**
`E8-01` Aylık fatura paketi export · `E8-02` İzlenebilirlik raporları · `E8-03` Otomatik aylık özet e-posta · `E8-04` Audit log & hata analizi

**E9 — Gelecek Vizyon (outline)**
`E9-01` Personel masraf OCR · `E9-02` İK konsolidasyon & ödeme tablosu · `E9-03` Banka mutabakatı · `E9-04` Satış faturaları & tahsilat · `E9-05` Coffeetropic multi-tenant

---

## 8. YouTrack'e Aktarım Notu

Her dosya bir issue. Önerilen alan eşlemesi: **Başlık** = `# [Exx-yy] …` satırı · **Description** = dosyanın kalan içeriği · **DOD** = "Kabul Kriterleri" bölümü · **Subtask** = "Alt Görevler" · **Estimation** = "Tahmini Efor" · **Depends on** = "Bağımlılıklar" · **Tags** = "Etiketler". IK-167 ile E7-01 ilişkilendirildi.

---
*Toplam: 9 epic, 52 görev. Kaynak: `CLAUDE.md` + Kaan'ın 12 maddelik otomasyon vizyonu + mevcut Drive/Excel yapısı.*
