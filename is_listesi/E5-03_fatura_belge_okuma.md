# [E5-03] Fatura belge okuma & veri çıkarımı (PDF/XML/e-Fatura + OCR)

| Alan | Değer |
|------|-------|
| Epic | E5 — Otomatik Fatura Toplama |
| Sprint | Sprint 5 |
| Öncelik | Yüksek |
| Tahmini Efor | 8 puan (~5 gün) |
| Bağımlılıklar | E1-02 (veri modeli), E1-04 (dosya depolama) |
| Etiketler | otomasyon, ocr, pdf, xml, e-fatura, parse |

## Amaç
Toplanan fatura belgelerinden (PDF, XML, e-Fatura, taranmış görüntü) yapısal veri çıkarmak: fatura tarihi, tutar, para birimi, sağlayıcı, fatura numarası. Bu yapısal veri eşleştirmenin (E5-04) ve Paraşüt aktarımının (E7) temelidir.

## Açıklama / Bağlam
Faturalar çok farklı formatlarda geliyor: dijital PDF (metin katmanı var), taranmış PDF/görüntü (OCR gerekir), e-Fatura XML (UBL-TR), servis panelinden indirilen statement PDF'leri. Sistem her belge tipini tanıyıp ilgili çıkarıcıya yönlendirmeli ve standart bir `ParsedInvoice` nesnesi üretmeli (tarih, tutar, para birimi, sağlayıcı, fatura no, KDV varsa).

Çıkarım güveni (confidence) önemli: düşük güvenli çıkarımlar "Araştırılacak" durumuna düşmeli, ekrana (E3-04) gelmeli.

## Kabul Kriterleri (DOD)
- [ ] Belge tipi otomatik tespit ediliyor (metinli PDF / taranmış PDF / XML / görüntü)
- [ ] Metinli PDF'ten tarih, tutar, para birimi, sağlayıcı, fatura no çıkarılıyor
- [ ] e-Fatura XML (UBL-TR) yapısal olarak parse ediliyor (LeasePlan gibi)
- [ ] Taranmış belge/görüntü için OCR devreye giriyor (Tesseract)
- [ ] Standart `ParsedInvoice` çıktısı + her alan için confidence skoru üretiliyor
- [ ] Düşük güvenli/eksik çıkarımlar "Araştırılacak" olarak işaretleniyor
- [ ] Para birimi (USD/EUR/TL) ve sayı/tarih format varyasyonları doğru yorumlanıyor

## Alt Görevler
- [ ] Belge tipi tespit (MIME + içerik sniff)
- [ ] PDF metin çıkarıcı (Apache PDFBox)
- [ ] e-Fatura/UBL-TR XML parser
- [ ] OCR entegrasyonu (Tesseract — Java tess4j veya Python mikroservis worker)
- [ ] Alan çıkarım kuralları (regex/heuristic + sağlayıcı bazlı şablonlar)
- [ ] Confidence skorlama + "Araştırılacak" yönlendirme
- [ ] Örnek faturalarla test seti (Claude, AWS, Contabo, OpenAI, LeasePlan e-Fatura)

## Teknik Notlar
- Çekirdek Java/Spring kalmalı. PDF: Apache PDFBox. XML: JAXB/StAX.
- OCR için tess4j (Java Tesseract binding) tercih; performans/dil sorunları olursa ayrı Python OCR mikroservisi (worker) — bu durumda Java ana servis worker'ı kuyrukla çağırır.
- Sağlayıcı bazlı şablonlar (template) çıkarımı çok iyileştirir — Servisler master listesindeki sağlayıcılar başlangıç şablon seti.
- OpenAI API ay içinde birden çok çekim yapıyor; aynı sağlayıcıdan çoklu fatura normal — duplicate sadece invoice no ile (E5-04).

## Açık Sorular / Riskler
- OCR doğruluğu düşük olabilir (taranmış, kötü kalite) — manuel düzeltme akışı (E3-04) şart.
- LLM tabanlı çıkarım (örn. belge → JSON) daha esnek olabilir ama maliyet/gizlilik — ileride değerlendirilecek, bu sprintte heuristic + OCR.
- Çok dilli faturalar (EN/TR) — Tesseract dil paketleri.

## Tamamlanma Kaydı
- Durum: **PDF okuma tamamlandı — 2026-06-29** · ⚠️ JPG/görüntü OCR sonraki iş (Tesseract/Tess4J)
- YouTrack: IK-254 (YouTrack'ten teyit edildi 2026-06-29)
- Repo: account-hr (backend)
- **Kaynak:** Selman'ın paylaştığı 13 gerçek servis faturası (OpenAI/ChatGPT/Kapwing/Lucidchart/OpenRouter/Wondershare, 2025-09..12). Gerçek dosyalardan pattern türetildi (Kural #5).
- **Backend:** Apache **PDFBox 3.0.5**. `InvoicePdfParser` (PDF metni → `invoiceNumber, issueDate, totalAmount, currency, vatAmount, vatRate, providerName`; tüm alanlar nullable, eşleşmezse uyarı, asla throw etmez). 4 format: İngilizce/Türkçe Stripe, Lucidchart (kısa ay), Wondershare (ISO tarih, işaretsiz tutar, "VAT NO" decoy ayıklama). Para `1,234.56`/`1.234,56`/`1,234`(binlik)/işaretsiz; tarih `Month D, YYYY`/`MMM D`/ISO/Türkçe ay. `POST /api/v1/invoices/parse` (multipart, ADMIN+ACCOUNTING; **dosya saklanmaz**; >10MB/non-pdf→400, bozuk→200+uyarı). **`rawText` response'ta YOK** (gizli veri sızıntısı önlendi).
- **Güvenlik/gizlilik:** Gerçek faturalar **repoya konmadı**; testler sentetik PDF (`SyntheticInvoicePdf`).
- **Gerçek doğrulama (lokal PG14, 13 gerçek fatura):** **13/13** no+tarih+tutar+para birimi+sağlayıcı doğru; KDV olanlarda doğru (6/10/2/0.0/2.72), yoksa null. Para birimi parantez-içi ₺'ye kanmadı; KDV decoy ayıklandı.
- Test: backend **350/350** (+17). Bağımsız review: rawText/decoy/currency/validation CLEAN; 2 muhasebe-bulgusu düzeltildi — `1,234` binlik-ayraç misparse (kritik), TOTAL satır-başı anchor + definitive-payable.
- **Kalan:** JPG→OCR (Tesseract); parse sonucunu yükleme/eşleştirme akışına bağlama (E3-05/E5-04); frontend "yükle→otomatik doldur".
