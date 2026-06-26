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
- Tarayıcıda XML render mi yoksa sadece indirme mi? — **Karar: ham metin (`<pre>`) gösterimi + indirme.**
- PDF önizleyici tarayıcı yerleşik mi yoksa kütüphane mi (pdf.js)? — **Karar: tarayıcı yerleşik (iframe, blob URL)** — hafif, pdf.js'siz.

## Tamamlanma Kaydı
- Durum: Tamamlandı — 2026-06-26
- YouTrack: IK-246 (sıralı varsayım — teyit edilecek)
- Repo: account-hr (backend) + account-hr-frontend
- **Backend:** `GET /api/v1/expenses/{id}/files` (expense→invoice'lar→FileAsset'ler, file-id dedup; `ExpenseFileResponse`: id, fileName, fileType, mimeType, sizeBytes, uploadedAt, invoiceId, invoiceStatus, previewable) + `GET /api/v1/files/{id}/preview` (`Content-Disposition: inline`, stored mimeType, RFC5987 dosya adı, path ifşası yok — `resolveUnderRoot`). Üç-rol yetki (E3-08). Eksik dosya → 404 (download da 404'e hizalandı). `loadAsResource(FileAsset)` overload ile tek DB okuması.
- **Frontend:** Detay modalında "Faturalar" bölümü — dosya listesi (tip badge, boyut, tarih) + ayrı önizleme modalı. **Önizleme auth-taşıyan blob** (`responseType:'blob'` → interceptor Bearer ekler; raw iframe URL DEĞİL). PDF → iframe (DomSanitizer blob URL), görsel → img, XML → ham `<pre>`. Render dalları **mimeType bazlı** (fileType değil — RECEIPT/STATEMENT görsel önizleme çalışır). Object-URL revoke (yeni önizleme/kapanış/OnDestroy). Her dosya indirilebilir.
- **Gerçek doğrulama (lokal PG14):** `GET /expenses/17/files` → 1 dosya (google_workspace_mart.pdf, previewable); `GET /files/44/preview` → 200 `Content-Disposition: inline` + `application/pdf` + nosniff; bilinmeyen id preview/download → 404; bilinmeyen expense → 404.
- Test: backend 278/278 (+12 ExpenseFilePreviewIT); frontend tsc+ng build temiz. Bağımsız review: güvenlik (yetki/path-traversal/RFC5987/XSS-sanitizer/blob-auth/lifecycle) CLEAN; 2 HIGH + 1 MEDIUM düzeltildi (mimeType-preview dalı, download 404 tutarlılığı, double-DB-read).
