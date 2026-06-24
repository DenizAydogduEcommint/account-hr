# [E7-05] Mükerrer / çakışma kontrolü (aynı fatura iki kez gitmesin)

| Alan | Değer |
|------|-------|
| Epic | E7 — Paraşüt Entegrasyonu |
| Sprint | Sprint 8 |
| Öncelik | Orta |
| Tahmini Efor | 3 puan (~2 gün) |
| Bağımlılıklar | E7-04 (gönderim), E5-04 (duplicate tespiti) |
| Etiketler | paraşüt, mükerrer, idempotency, doğrulama |

## Amaç
Aynı faturanın Paraşüt'e birden fazla kez gider olarak işlenmesini kesin olarak engellemek. Mükerrer gider kaydı muhasebede ciddi hata oluşturur.

## Açıklama / Bağlam
Fatura birden çok kaynaktan gelebilir (mail + Drive + panel), gönderim retry edilebilir, sistem yeniden başlatılabilir. E5-04 belge düzeyinde duplicate tespiti yapar; bu görev Paraşüt gönderim düzeyinde idempotency garantisi sağlar: bir fatura (invoice no + sağlayıcı + dönem) zaten Paraşüt'e gönderilmişse tekrar gönderilmez. Çakışma (Paraşüt'te zaten benzer kayıt) durumları tespit edilip raporlanır.

## Kabul Kriterleri (DOD)
- [ ] Her gönderim öncesi idempotency anahtarı (invoice no + sağlayıcı + dönem/tutar) kontrol ediliyor
- [ ] Daha önce başarıyla gönderilmiş fatura tekrar gönderilmiyor (Paraşüt ID'siyle doğrulanır)
- [ ] Retry/yeniden başlatma sonrası mükerrer kayıt oluşmuyor
- [ ] Paraşüt tarafında çakışan/benzer kayıt tespiti (varsa) raporlanıyor
- [ ] Şüpheli mükerrer durumlar ekranda/raporda işaretleniyor (E8-02)

## Alt Görevler
- [ ] Idempotency anahtarı tanımı + benzersizlik kısıtı (DB)
- [ ] Gönderim öncesi mükerrer kontrol mantığı (E7-04 ile entegre)
- [ ] Paraşüt tarafı çakışma tespiti (mevcut kayıt sorgusu)
- [ ] Mükerrer/çakışma raporlama (E8-02 besle)
- [ ] Testler (retry, çoklu kaynak, restart)

## Teknik Notlar
- DB benzersizlik kısıtı + uygulama düzeyi kontrol birlikte (savunma derinliği).
- Invoice no her zaman güvenilir olmayabilir (OCR hatası) — yedek anahtar: sağlayıcı+tutar+tarih.
- E5-04 belge-duplicate ile bu görev gönderim-duplicate farklı katmanlar; ikisi de gerekli.

## Açık Sorular / Riskler
- OCR ile yanlış okunan invoice no mükerrer tespitini atlatabilir — yedek anahtar şart.
- Meşru tekrar gönderim (örn. düzeltme) ile mükerreri ayırma — manuel override gerekebilir.
