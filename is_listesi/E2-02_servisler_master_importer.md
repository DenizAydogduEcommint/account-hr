# [E2-02] Servisler master sheet'ini services + service_contacts tablolarına aktar

| Alan | Değer |
|------|-------|
| Epic | E2 — Veri Migrasyonu |
| Sprint | Sprint 2 |
| Öncelik | Yüksek |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E1-02 |
| Etiketler | backend, migration, excel, import, service-first |

## Amaç
SERVICE-FIRST modelin çekirdeği olan "Servisler" master sheet'ini `services` (ve fatura iletişim bilgilerini `service_contacts`) tablolarına aktararak, eksik fatura tespitinin temelini oluşturmak.

## Açıklama / Bağlam
Yeni sistemin tüm mantığı bu master listeden başlar: "ödenen her servis için her ay fatura bekle". Excel'deki Servisler sheet'i kolonları: Hizmet, Sağlayıcı, Kart, Frekans (Aylık/Yıllık/Kullanım bazlı/Ad-hoc), Aktif (Evet/Hayır/Belirsiz), Aktif Aylar, Yaklaşık Tutar (TL), Fatura E-posta, Fatura Kaynağı (Servis paneli/E-posta/e-Fatura/Drive waiting), Notlar.

Örnek servisler: AWS, Atlassian, Claude AI (iade patterni), Contabo VPS 10/20/30, Google Workspace/One, HepsiBurada, LeasePlan (e-Fatura), Mailjet, Microsoft 365, Ngrok, OpenAI ChatGPT/API, OpenRouter, Pipedrive, Zoom, JetBrains YouTrack (yıllık), Godaddy (yıllık), Allianz/Multinet/Sağlık Sigortası (Ignored).

## Kabul Kriterleri (DOD)
- [ ] Servisler sheet'i okunup her satır bir `service` kaydına dönüşüyor
- [ ] Frekans → `frequency` enum (Aylık/Yıllık/Kullanım bazlı/Ad-hoc) doğru eşleniyor
- [ ] Aktif → `active_state` enum (Evet/Hayır/Belirsiz) eşleniyor
- [ ] Kart → ilgili `card` kaydına bağlanıyor (varsayılan kart)
- [ ] Sağlayıcı → `provider` kaydına bağlanıyor (yoksa oluşturuluyor)
- [ ] Fatura E-posta + Fatura Kaynağı → `service_contacts` kaydı oluşturuyor
- [ ] Yaklaşık Tutar (TL) ve Notlar alanları aktarılıyor
- [ ] "Aktif Aylar" (virgüllü string, ör. "2026-01, 2026-02") parse edilip ilgili period'larla ilişkilendiriliyor veya saklanıyor
- [ ] Ignored servisler (Allianz/Multinet/Sağlık Sigortası) doğru işaretleniyor
- [ ] E2-01'deki expense satırlarındaki hizmetler bu master'a bağlanabiliyor (eşleşmeyen servisler raporlanıyor)
- [ ] Özet rapor: aktarılan servis sayısı, oluşturulan provider/contact sayısı, eşleşmeyenler

## Alt Görevler
- [ ] Servisler sheet okuma + satır parser
- [ ] Frekans/aktiflik/kart/sağlayıcı eşleyiciler
- [ ] `service_contacts` üretimi (mail + kaynak)
- [ ] "Aktif Aylar" parse + period ilişkilendirme
- [ ] Provider deduplikasyonu (aynı firma tek kayıt)
- [ ] E2-01 expense'leri ↔ service eşleme bağlama (foreign key güncelle)
- [ ] Özet rapor

## Teknik Notlar
- Bu görev E2-01 ile birlikte çalışmalı: önce service master, sonra expense'ler service_id'ye bağlanır (veya tersine, sonradan eşleme). Sıra: E2-02 service master → E2-01 expense eşleme önerilir
- Contabo VPS 10/20/30 gibi varyantlar ayrı service kayıtları (farklı tutar/abonelik)
- Claude AI "iade patterni" notu service.notes alanında korunmalı (ileride iade algoritması için ipucu)
- LeasePlan e-Fatura kaynağı, Godaddy/YouTrack yıllık frekans örnekleri doğru eşlenmeli
- "Aktif Aylar" denormalize string; modelde service↔period ilişkisi varsa oradan türetilir, yoksa not olarak saklanır

## Açık Sorular / Riskler
- Hizmet adlarının E2-01'deki ay sheet'leriyle bire bir eşleşmesi garanti değil (yazım farkları) — alias/eşleme tablosu gerekebilir
- "Belirsiz" aktiflik durumundaki servisler eksik-fatura tespitine dahil mi? (İş kuralı netleştirilmeli)
- Yaklaşık Tutar sadece son ayın toplamı; tarihsel değil — bilgi amaçlı sakla
