# [E4-02] İşlem ↔ servis/fatura eşleştirme motoru

| Alan | Değer |
|------|-------|
| Epic | E4 — Banka Ekstresi İşleme |
| Sprint | Sprint 4 |
| Öncelik | Yüksek |
| Bağımlılıklar | E1-02, E1-07, E3-02, E4-01 |
| Tahmini Efor | 8 puan (~5 gün) |
| Etiketler | backend, eslestirme, core |

## Amaç
Ekstreden çıkan ham işlemleri otomatik olarak doğru servise ve harcama satırına eşle (tarih + tutar + işyeri/hizmet). Çoklu çekim ve iade/refund senaryolarını doğru ele alır.

> **KRİTİK NOT (ön-muhasebe domain incelemesi, 2026-06-26):** Bu görevin en kritik yönü **manuel (E3-06) ↔ ekstre mükerrer-önleme**. Şu an manuel satır (`source=MANUAL`) ile ekstre satırı (`source=STATEMENT`) farklı `source_row_hash`'e sahip → aynı işlem iki kez girilirse **çift kayıt** otomatik tespit edilmiyor. Muhasebede mükerrer kayıt en pahalı hata türüdür. Kullanıcı özellikle "unutmayalım" dedi. Eşleştirme anahtarı (kart son4 + tarih±tolerans + tutar±kuruş), tolerans değerleri ve çok-aday durumu (aynı servis aynı ay çoklu çekim) bu görev başlarken netleştirilecek; belirsiz eşleşmede kullanıcı onayı.

## Açıklama / Bağlam
E4-01'in ürettiği ham işlemler bu motorla servislere bağlanır. Eşleştirme ipuçları: işyeri/açıklama metni → servis adı (kural/anahtar kelime tablosu), tutar, tarih, kart. Zorluklar:
- **Çoklu çekim**: OpenAI API gibi servisler ay içinde birden çok kez çekilebilir → her çekim ayrı işlem, hepsi aynı servise bağlanır
- **İade/refund**: Claude AI gibi servislerde negatif/iade işlemleri olur → refund olarak işaretlenir, net tutar hesaplanır
- Eşleşen işlem ilgili ay sheet'inde bir harcama satırı oluşturur/günceller (durum "Bekleniyor" başlar, fatura gelince E3-05 ile "Bulundu")
- Eşleşmeyen işlemler E4-04'e devredilir

## Kabul Kriterleri (DOD)
- [ ] İşyeri/açıklama → servis eşleme kural tablosu (anahtar kelime) çalışır
- [ ] Tutar + tarih + kart bilgisi eşleştirmede kullanılır
- [ ] Aynı servisin ay içinde çoklu çekimi ayrı işlem olarak korunur, aynı servise bağlanır
- [ ] İade/refund işlemleri tespit edilir ve işaretlenir (net tutar hesabı)
- [ ] Eşleşen işlemler harcama satırı oluşturur/günceller (varsayılan durum Bekleniyor)
- [ ] Eşleşmeyen işlemler "eşleşmedi" olarak işaretlenir (E4-04'e gider)
- [ ] Eşleştirme güven skoru/önerisi varsa kullanıcı onaylayabilir (belirsiz eşleşmeler)
- [ ] Mükerrer harcama satırı yaratılmaz (manuel E3-06 satırıyla çakışma kontrolü)

## Alt Görevler
- [ ] Backend: işyeri/açıklama → servis eşleme kural tablosu ve yönetimi
- [ ] Backend: eşleştirme algoritması (tarih/tutar/kart/anahtar kelime)
- [ ] Backend: çoklu çekim ele alma
- [ ] Backend: refund/iade tespiti ve işaretleme
- [ ] Backend: eşleşmeden harcama satırı üretme
- [ ] Angular: belirsiz eşleşmeler için onay/düzeltme ekranı

## Teknik Notlar
- Anahtar kelime tablosu servis bazlı düzenlenebilir olmalı (öğrenen/genişleyen)
- Refund tespiti: negatif tutar veya açıklamada "iade/refund" anahtarı
- Net tutar = çekimler - iadeler; eksik fatura mantığı (E3-04) net pozitif çekime bakmalı
- Manuel satır ile ekstre işlemi çakışmasını önlemek için "kaynak" alanı (E3-06 notu)
- Eşleştirme deterministik değilse kullanıcı onayı şart

## Açık Sorular / Riskler
- İşyeri metinleri ne kadar tutarlı? (örn "ANTHROPIC", "Claude.ai", "OpenAI *ChatGPT") — kural tablosu kalitesi buna bağlı
- Belirsiz eşleşmelerde otomatik mi yoksa hep onay mı? — Öneri: yüksek güven otomatik, düşük güven onay
- Refund ile orijinal çekimin eşleşmesi (hangi çekimin iadesi) gerekli mi? — MVP'de servis-ay net yeterli olabilir
