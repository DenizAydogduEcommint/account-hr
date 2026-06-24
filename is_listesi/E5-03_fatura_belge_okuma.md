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
