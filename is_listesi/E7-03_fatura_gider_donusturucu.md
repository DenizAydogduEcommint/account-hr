# [E7-03] Fatura → Paraşüt gider kaydı dönüştürücü (alan eşleme, döviz/TL, KDV)

| Alan | Değer |
|------|-------|
| Epic | E7 — Paraşüt Entegrasyonu |
| Sprint | Sprint 7 |
| Öncelik | Yüksek |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E7-01 (kolon eşleme), E7-02 (API client), E5-04 (eşleştirme) |
| Etiketler | paraşüt, dönüşüm, döviz, kdv, mapping |

## Amaç
Sistemdeki fatura/harcama kaydını Paraşüt'ün gider kaydı modeline çeviren dönüştürücüyü yazmak: alan eşleme, döviz→TL, KDV hesabı, tedarikçi ve kategori bağlama. E7-04 bu çıktıyı API'ye gönderir.

## Açıklama / Bağlam
Her fatura: tarih, orijinal tutar + para birimi, TL karşılığı, sağlayıcı, hizmet, kart. Paraşüt gider kaydı: tedarikçi, kategori, tarih, net/KDV/brüt tutar, döviz, açıklama vb. Bu görev iki model arasında deterministik bir dönüşüm tanımlar. E7-01'de netleşen kolon eşlemesi spesifikasyon kaynağıdır.

## Kabul Kriterleri (DOD)
- [ ] Fatura kaydından Paraşüt gider DTO'su üretiliyor (E7-02 client'ın beklediği şekil)
- [ ] Döviz (USD/EUR) tutarı + kur ile TL karşılığı doğru hesaplanıyor (kart ekstresi TL'si esas)
- [ ] KDV ayrımı yapılıyor (yurtdışı/sorumlu sıfatıyla KDV dahil kurallar muhasebeyle netleşmiş haliyle)
- [ ] Sağlayıcı → Paraşüt tedarikçisi eşleniyor; yoksa oluşturma akışı tetikleniyor
- [ ] Hizmet/servis → Paraşüt gider kategorisi eşleniyor (eşleme tablosu)
- [ ] Eksik/eşlenemeyen alanlar net hata veriyor, sessiz kayıp yok

## Alt Görevler
- [ ] Fatura → Paraşüt gider DTO mapper
- [ ] Döviz/TL ve KDV hesap mantığı
- [ ] Sağlayıcı→tedarikçi eşleme tablosu + eksikse oluşturma çağrısı (E7-02)
- [ ] Hizmet→kategori eşleme tablosu (Servisler master temelli)
- [ ] Doğrulama + hata raporlama
- [ ] Birim testleri (USD, EUR, TL; KDV'li/KDV'siz; iade)

## Teknik Notlar
- TL karşılığı kart ekstresinden gelir; kur farkı/Paraşüt'ün kendi kuru ile tutarlılık kontrol edilmeli.
- Eşleme tabloları (sağlayıcı→tedarikçi, hizmet→kategori) konfigürasyon/DB'de tutulmalı, kod içinde sabit değil.
- Refund/iade kayıtları negatif gider veya alacak olarak işlenmeli (muhasebe kararı).

## Açık Sorular / Riskler
- KDV muamelesi (yurtdışı hizmet, sorumlu sıfatıyla KDV) — mali müşavir/muhasebe netleştirmeli.
- Paraşüt kategori taksonomisi ile sistemdeki hizmet listesi birebir örtüşmeyebilir.
- Kur kaynağı: kart TL'si mi, Paraşüt kuru mu, TCMB mi?
