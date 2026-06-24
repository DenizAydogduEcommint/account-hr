# [E5-04] Otomatik servis-fatura eşleştirme & duplicate tespiti

| Alan | Değer |
|------|-------|
| Epic | E5 — Otomatik Fatura Toplama |
| Sprint | Sprint 6 |
| Öncelik | Yüksek |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E1-02 (veri modeli), E4-02 (eşleştirme motoru), E5-03 (belge okuma) |
| Etiketler | otomasyon, eşleştirme, duplicate, fatura-toplama |

## Amaç
Parse edilmiş faturayı (E5-03) doğru servise ve doğru harcama satırına otomatik bağlamak; aynı faturanın iki kez işlenmesini (mail + Drive'dan gelme) invoice no ile engellemek. Eşleşen satırın durumu "Bulundu" olur.

## Açıklama / Bağlam
Bir fatura geldiğinde sistem: (1) hangi servise ait (sağlayıcı/tutar/para birimi ile), (2) kart ekstresindeki hangi işlem satırına denk geliyor (tarih + tutar + hizmet) bulmalı. Eşleşme bulunursa harcama satırının Fatura Durumu "Bulundu"/"e-Fatura" yapılır, dosya yolu Fatura Notu'na yazılır. Duplicate tespiti: aynı invoice number'a sahip belge zaten işlendiyse yenisi trash'e gider.

E4-02 genel eşleştirme motorunu sağlar; bu görev onu fatura→servis bağlamına uyarlar ve duplicate kuralını ekler.

## Kabul Kriterleri (DOD)
- [ ] Parse edilmiş fatura, sağlayıcı + tutar + para birimi + tarih kombinasyonuyla harcama satırına eşleniyor
- [ ] Eşleşme bulunan satırın Fatura Durumu otomatik güncelleniyor (Bulundu / e-Fatura)
- [ ] Fatura dosya yolu Fatura Notu alanına yazılıyor; bir satıra birden çok dosya bağlanabiliyor (PDF+XML+statement)
- [ ] Aynı invoice no'lu duplicate belge tespit edilip işlenmiyor (trash'e taşınıyor / işaretleniyor)
- [ ] Eşleşme bulunamayan fatura "Araştırılacak" / eksik fatura ekranına (E3-04) düşüyor
- [ ] Birden çok aday eşleşme olduğunda en yüksek skorlu seçiliyor, belirsizse manuel onaya bırakılıyor
- [ ] OpenAI gibi ay içinde çoklu çekim yapan servislerde her çekim ayrı eşleşebiliyor

## Alt Görevler
- [ ] E4-02 motorunu fatura→satır bağlamına uyarlayan adapter
- [ ] Eşleştirme skorlama kuralları (tarih toleransı, tutar/döviz toleransı, sağlayıcı normalizasyonu)
- [ ] Invoice no bazlı duplicate index + duplicate → trash akışı
- [ ] Çoklu dosya bağlama (bir satır, çok dosya) ilişki modeli
- [ ] Eşleşmeyenleri E3-04 eksik fatura ekranına gönderme
- [ ] Test: refund/iade patternli faturalar (Claude AI iade patterni)

## Teknik Notlar
- Sağlayıcı adı normalizasyonu kritik (Anthropic=Claude AI, vb.) — Servisler master'daki Sağlayıcı alanı referans.
- Döviz toleransı: kart ekstresi TL karşılığını gösterir, fatura USD/EUR olabilir — tutar eşleşmesi para birimi + tarih kuruyla yapılmalı.
- Tarih toleransı: fatura tarihi ile ödeme/çekim tarihi farklı olabilir (örn. Şubat faturası Mart çekimi).
- Refund: iade dosyaları iadenin yapıldığı ayın klasörüne, adında `refund`.

## Açık Sorular / Riskler
- Çoklu aday eşleşmede yanlış pozitif riski — belirsizlik eşiği ayarlanmalı.
- e-Fatura (LeasePlan) ile kart çekimi eşleşmesi: e-Fatura tarihi ile çekim tarihi farkı.
- Aynı tutar + aynı gün iki farklı servis (örn. iki domain yenileme) — ayırt etme zor.
