# [E3-09] Fatura detay / önizleme (PDF/XML, çoklu dosya)

| Alan | Değer |
|------|-------|
| Epic | E3 — Fatura Yönetim Web Uygulaması |
| Sprint | Sprint 3 |
| Öncelik | Orta |
| Bağımlılıklar | E1-04, E1-07, E3-03, E3-05 |
| Tahmini Efor | 3 puan (~2 gün) |
| Etiketler | frontend, backend, ui, dosya-onizleme |

## Amaç
Yüklenen faturalar uygulama içinde görüntülenebilsin; muhasebe ve ekip dosyayı indirmeden önizleyip doğrulayabilsin. Bir satıra bağlı tüm dosyalar (fatura + statement + XML) tek yerde görülür.

## Açıklama / Bağlam
Bir harcama satırına tıklanınca açılan detay panelinde: satır bilgileri + bağlı fatura dosyalarının listesi. PDF'ler gömülü önizleyicide (iframe/PDF viewer) gösterilir; XML için ham içerik/indirme; görseller (JPG/PNG) önizlenir. Her dosya indirilebilir. Bir satıra birden çok dosya bağlı olabileceğinden dosyalar listelenir ve seçilerek önizlenir. Yetki kontrolü E3-08 ile uyumlu (muhasebe indirme/önizleme erişimi).

## Kabul Kriterleri (DOD)
- [ ] Harcama satırından fatura detay paneli açılır
- [ ] Bir satıra bağlı tüm dosyalar listelenir
- [ ] PDF dosyalar gömülü önizleyicide görüntülenir
- [ ] XML dosyalar görüntülenebilir/indirilebilir
- [ ] Görsel (JPG/PNG) faturalar önizlenir
- [ ] Her dosya tek tek indirilebilir
- [ ] Dosya erişimi yetkiye tabidir (E3-08); yetkisiz erişim engellenir
- [ ] Dosya bulunamazsa/silinmişse anlaşılır hata gösterilir

## Alt Görevler
- [ ] Backend: `GET /api/invoices/{id}/file` güvenli dosya servis endpoint'i (yetki kontrollü)
- [ ] Backend: bir satıra bağlı dosya listesi endpoint'i
- [ ] Angular: detay paneli/modalı
- [ ] Angular: PDF önizleyici entegrasyonu
- [ ] Angular: dosya listesi + indirme aksiyonu

## Teknik Notlar
- Dosya servis edilirken doğrudan path ifşa edilmemeli; ID üzerinden yetki kontrollü stream
- Büyük PDF'ler için streaming/range request desteği iyi olur
- Dosya metadata (tip, boyut, yüklenme tarihi, yükleyen) gösterilebilir
- E1-04 dosya depolama soyutlamasıyla uyumlu

## Açık Sorular / Riskler
- Tarayıcıda XML render mi yoksa sadece indirme mi? (Öneri: ham metin gösterimi + indirme)
- PDF önizleyici tarayıcı yerleşik mi yoksa kütüphane mi (pdf.js)? — performans/uyumluluk değerlendir
