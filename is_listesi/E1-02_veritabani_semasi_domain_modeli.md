# [E1-02] Veritabanı şeması ve domain modelini tasarla (SERVICE-FIRST)

| Alan | Değer |
|------|-------|
| Epic | E1 — Temel Altyapı & Veri Modeli |
| Sprint | Sprint 1 |
| Öncelik | Yüksek |
| Tahmini Efor | 8 puan (~4-5 gün) |
| Bağımlılıklar | E1-01 |
| Etiketler | backend, db, domain, schema |

## Amaç
Tüm sistemin kalbi olan, "servisten (master liste) yola çıkıp eksik faturayı tespit etmeyi" mümkün kılan SERVICE-FIRST veri modelini ve PostgreSQL şemasını kurmak.

## Açıklama / Bağlam
Mevcut manuel sistemde banka ekstresinden hareket ediliyordu; yeni sistemde tersine dönüyoruz: önce **ödenen tüm servislerin master listesi** (Servisler sheet'i) kayıt altına alınır, sonra her ay her servis için bir harcama/işlem (transaction) ve buna bağlı fatura(lar) beklenir. Böylece "bu ay X servisinin faturası eksik" sorusunu sistem otomatik yanıtlayabilir.

Modellenecek ana varlıklar:
- **services** (çekirdek master varlık): hizmet adı, sağlayıcı, varsayılan kart, frekans, aktiflik, yaklaşık tutar, fatura kaynağı, notlar
- **providers**: fatura kesen firmalar (AWS, Anthropic, Contabo...)
- **cards**: kredi kartları (Akbank Axess ****3800, YKB ****3909, Ziraat ****9164)
- **expenses** (transactions): ay sheet'indeki her satır = bir işlem (tarih, tutar, para birimi, TL karşılığı, kart, kullanan takım, amaç)
- **invoices**: bir işleme bağlı fatura(lar); birden çok dosya olabilir
- **invoice_status** (enum): Bulundu / e-Fatura / Bekleniyor / Araştırılacak / Ignored
- **periods**: aylar (2026-01, 2026-02...)
- **users**, **teams**: kullanan takım ve sistem kullanıcıları
- **service_contacts**: bir servisin fatura iletişim bilgileri (mail adresi, panel login referansı, kaynak)
- **files**: fatura dosyaları (PDF/XML/statement) metadata + path
- **audit_log**: değişiklik geçmişi

## Kabul Kriterleri (DOD)
- [ ] ER diyagramı (görsel veya dbml/mermaid) çizildi, tüm tablolar ve ilişkiler gösterildi
- [ ] services, providers, cards, expenses, invoices, periods, users, teams, service_contacts, files, audit_log tabloları migration olarak tanımlı
- [ ] Enum'lar tanımlı: `invoice_status` (Bulundu/e-Fatura/Bekleniyor/Araştırılacak/Ignored), `frequency` (Aylık/Yıllık/Kullanım bazlı/Ad-hoc), `currency` (USD/EUR/TRY...), `active_state` (Evet/Hayır/Belirsiz)
- [ ] İlişkiler doğru: service → provider (N:1), service → card (N:1 varsayılan), expense → service (N:1), expense → period (N:1), expense → card (N:1), invoice → expense (N:1), file → invoice (N:1, bir faturaya çok dosya), service_contact → service (N:1)
- [ ] JPA entity'leri ve repository interface'leri oluşturuldu, uygulama hatasız ayağa kalkıyor
- [ ] Para tutarları `NUMERIC(15,2)` olarak; tarih alanları `DATE`/`TIMESTAMP`
- [ ] Servisler master'ı çekirdek varlık: bir servisin "her ay beklenen" olup olmadığı (frekans + aktiflik) sorgulanabilir

## Alt Görevler
- [ ] ER modelini çıkar (dbml/mermaid), kullanıcı ile gözden geçir
- [ ] Migration aracı seç (Flyway öneri) ve baseline kur
- [ ] Tabloları ve enum'ları migration script'leri olarak yaz
- [ ] JPA entity + repository sınıflarını oluştur
- [ ] Seed/lookup verisi: kartlar (3 adet), takımlar, ilk periods kayıtları
- [ ] Index'ler: expense(period_id, service_id), invoice(status), unique kısıtları (ör. invoice no)

## Teknik Notlar
- Enum'lar DB'de string olarak saklanmalı (`@Enumerated(EnumType.STRING)`) — okunabilirlik ve migration güvenliği
- Renk bilgisi enum'a değil, frontend tarafına ait (status→renk eşlemesi UI sabiti); ancak CLAUDE.md renk kodları dokümante edilmeli (4CAF50/8BC34A/FF4444/FF9800)
- expense = "transaction" kavramı; isimlendirmede tutarlı olun (DB'de `expenses`, koddaki domain `Expense`)
- Bir işleme birden çok fatura/dosya: invoice(1) → files(N) ya da expense(1) → invoices(N) → files(N) kararı net verilmeli (öneri: expense→invoice 1:N esnekliği koru çünkü iade/duplicate senaryoları var)
- Duplicate tespiti için invoice no alanı + (provider, invoice_no) unique partial index
- "Aktif Aylar" Servisler sheet'inde virgüllü string; modelde service ↔ period ilişkisinden türetilebilir (denormalize tutmak yerine sorgu ile)

## Açık Sorular / Riskler
- expense→invoice 1:N mi, invoice→expense 1:N mi? İade ve "Invoice+Receipt aynı işlem" senaryoları netleştirilmeli
- Çoklu para birimi + TL karşılığı: kur tarihi ayrı saklanacak mı yoksa sadece ekstredeki TL mi yeterli? (MVP: sadece ekstre TL'si yeter)
- Servisler master ile expense satırı arasında otomatik eşleme anahtarı ne olacak? (Hizmet adı + kart kombinasyonu kırılgan — service_id referansı şart)
