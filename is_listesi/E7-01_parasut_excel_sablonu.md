# [E7-01] Paraşüt Excel şablonu oluşturma & test (IK-167)

| Alan | Değer |
|------|-------|
| Epic | E7 — Paraşüt Entegrasyonu |
| Sprint | Sprint 6 |
| Öncelik | Yüksek |
| Bağlı YouTrack | IK-167 — "Paraşüt için Excel şablonu oluşturma ve test" |
| Tahmini Efor | 3 puan (~2 gün) |
| Bağımlılıklar | E1-02 (veri modeli), E5-04 (eşleştirme) |
| Etiketler | paraşüt, excel, export, muhasebe |

## Amaç
Toplanan faturaları Paraşüt'e (ön muhasebe yazılımı) içe aktarılabilecek bir Excel şablonuna dönüştürmek ve Paraşüt'te içe aktararak şablonu doğrulamak. API entegrasyonundan (E7-02) önce hızlı, çalışan bir köprü sağlar.

## Açıklama / Bağlam
Bu görev doğrudan YouTrack **IK-167** ("Paraşüt için Excel şablonu oluşturma ve test") ile ilişkilidir. Faturaların Excel dışa aktarımıyla Paraşüt'te gider işlemleri test edilecek, hangi kolonların gerektiği (gider tipi, tarih, tutar, KDV, döviz, kategori, tedarikçi) netleştirilecek ve nihai şablon belgelenecek. Çıkan kolon eşlemesi E7-03 (dönüştürücü) için spesifikasyon görevi görür.

## Kabul Kriterleri (DOD)
- [ ] Paraşüt'ün gider içe aktarma için beklediği Excel kolonları araştırılıp belgelendi
- [ ] Sistemdeki fatura verisinden bu şablona uygun Excel üretiliyor
- [ ] Üretilen Excel Paraşüt'e test olarak içe aktarıldı ve işlemler doğrulandı
- [ ] Döviz (USD/EUR → TL), KDV ve gider kategorisi kolonları doğru dolduruluyor
- [ ] Nihai kolon eşlemesi (sistem alanı → Paraşüt kolonu) dokümante edildi (E7-03 girdisi)
- [ ] IK-167 bu görevle ilişkilendirildi / kapatma kriteri buraya bağlandı

## Alt Görevler
- [ ] Paraşüt gider içe aktarma şablonu formatını araştır (örnek dosya / dokümantasyon)
- [ ] Fatura verisi → Excel üretici (openpyxl benzeri Java kütüphanesi: Apache POI)
- [ ] Örnek ay verisiyle Excel üret, Paraşüt'e içe aktar, sonucu doğrula
- [ ] Hatalı/eksik kolonları düzelt, tekrar test
- [ ] Kolon eşleme dokümanı yaz

## Teknik Notlar
- Excel üretimi Java tarafında Apache POI ile.
- Bu manuel/yarı-otomatik köprü, E7-02 API hazır olana kadar production'da kullanılabilir.
- Döviz dönüşümü: kart ekstresindeki TL karşılığı esas; orijinal döviz tutarı not olarak.
- KDV: yurtdışı abonelikler genelde KDV'siz / sorumlu sıfatıyla KDV — muhasebeyle netleşmeli.

## Açık Sorular / Riskler
- Paraşüt'ün resmi içe aktarma şablonu var mı, yoksa serbest mi? (Araştırılacak.)
- Yurtdışı giderlerde KDV ve döviz kuru muamelesi muhasebe kararı gerektirir.
- Şablon Paraşüt sürümüne göre değişebilir.
