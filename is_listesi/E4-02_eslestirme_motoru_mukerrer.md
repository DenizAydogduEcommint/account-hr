# [E4-02 detay] Manuel ↔ ekstre mükerrer-eşleştirme (Eşleştirme motorunun kritik yönü) (KRİTİK MUHASEBE RİSKİ)

| Alan | Değer |
|------|-------|
| Epic | E4 — Banka Ekstresi Eşleştirme |
| Sprint | (planlanacak) |
| Öncelik | **Yüksek** |
| Bağımlılıklar | E2-01 (Excel importer), E3-06 (manuel giriş), E2-03 (fatura eşleştirme) |
| Tahmini Efor | 8 puan (~5 gün) — büyük, ayrı tasarım gerekir |
| Etiketler | backend, muhasebe, eşleştirme, mükerrer-önleme |

## Amaç
Manuel girilen bir harcama satırı, sonradan banka ekstresiyle geldiğinde **çift kayıt (mükerrer)** olmasını önle. Muhasebede mükerrer kayıt en pahalı hata kaynağıdır.

## Bağlam / Neden açıldı
Ön-muhasebe domain incelemesinde (2026-06-26) tespit edildi: E3-06 manuel giriş (`source=MANUAL`) ile E2-01 ekstre importu (`source=STATEMENT`) farklı `source_row_hash`'e sahip (manuel'de null). Şu an aynı işlem iki kez girilirse **otomatik tespit edilmiyor** — bilinçli olarak bu eşleştirme motoruna (E4) ertelendi. CLAUDE.md'de de "Manuel satır ile sonradan gelen ekstre satırı çakışırsa nasıl ayırt edilecek? → E4 eşleştirme motoruyla" notu var.

## Kabul Kriterleri (DOD) — taslak (tasarımda netleşecek)
- [ ] Ekstre importu sırasında, aynı (servis/kart + tarih ± tolerans + tutar ± tolerans) manuel satır var mı kontrol et
- [ ] Eşleşme bulunursa: ekstre satırını yeni kayıt olarak EKLEME → mevcut manuel satırı "ekstreyle doğrulandı" olarak işaretle (`source` MANUAL→STATEMENT veya bir `matched` flag)
- [ ] Eşleşme belirsizse (yakın ama kesin değil) → kullanıcıya "olası mükerrer" uyarısı, manuel onay
- [ ] Eşleşme yoksa → ekstre satırı normal eklenir
- [ ] Eşleştirme kararları audit'lenir
- [ ] Mevcut idempotency (source_row_hash) korunur; bu katman onun üstüne çalışır

## Teknik Notlar / Tasarım Soruları (E4 başlarken netleştir)
- Eşleştirme anahtarı: (kart son4 + tarih ± gün + tutar ± kuruş) mı, (servis + ay + tutar) mı?
- Tolerans: tarih kaç gün, tutar kaç kuruş (döviz kur farkı)?
- Çok-aday durumunda (aynı servis aynı ay çoklu çekim — OpenAI 7 satır) hangi manuel hangi ekstreyle? → kullanıcı onayı
- Banka ekstresi otomatik parse (PDF/XLS) E4'ün diğer görevleri; bu görev eşleştirme mantığına odaklı

## Açık Sorular / Riskler
- Bu görev E4 epic'inin çekirdeği; tek başına büyük. Ekstre parse formatı + eşleştirme algoritması ayrı tasarım turu gerektirir.
- MVP'de (E1+E2+E3) manuel giriş tek kullanıcılı + dikkatli yapıldığından risk yönetilebilir; otomasyon E4'te.
