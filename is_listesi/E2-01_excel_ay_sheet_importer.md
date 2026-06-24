# [E2-01] Excel ay sheet'lerini (12 kolon) expenses tablosuna aktaran importer

| Alan | Değer |
|------|-------|
| Epic | E2 — Veri Migrasyonu |
| Sprint | Sprint 2 |
| Öncelik | Yüksek |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E1-02 |
| Etiketler | backend, migration, excel, import |

## Amaç
Mevcut `2026_Harcamalar.xlsx` dosyasındaki her ay sheet'inin (Ocak/Şubat/Mart/Nisan) 12 kolonlu harcama satırlarını yeni sistemin `expenses` tablosuna sadık şekilde aktarmak.

## Açıklama / Bağlam
Bugüne kadar tüm veri Excel'de tutuldu. Yeni sisteme geçişte bu geçmiş kaybolmamalı. Her ay bir sheet ve her satır bir harcama (transaction). 12 kolon: Tarih, Hizmet, Sağlayıcı, Tutar, Para Birimi, TL Karşılığı, Kart, Kullanan Takım, Amaç, Muhasebe E-posta, Fatura Durumu, Fatura Notu.

Bu importer sheet'leri tarar, satırları okur, ilgili period/card/service/provider kayıtlarına bağlar ve `expenses` (+ taslak `invoices`) oluşturur. Bilgi amaçlı bölümler (Multinet, Allianz sağlık sigortası — "Ignored") ve TOPLAM satırları doğru ele alınmalı.

## Kabul Kriterleri (DOD)
- [ ] Excel dosyası okunup her ay sheet'i ayrı ayrı işleniyor (openpyxl/Apache POI)
- [ ] Her harcama satırı bir `expense` kaydına dönüşüyor; 12 kolon doğru eşleniyor
- [ ] Tarih `DD.MM.YYYY` parse ediliyor; tutar ve TL karşılığı sayısal (string değil)
- [ ] Kart son-4 hane (****3800/****3909/****9164) ilgili `card` kaydına bağlanıyor
- [ ] Para birimi `currency` enum'a eşleniyor (USD/EUR/TRY)
- [ ] Hizmet + Sağlayıcı, mümkünse mevcut service/provider'a eşlenir; yoksa işaretlenir (E2-02 ile uyum)
- [ ] **TOPLAM satırları import edilmez** (atlanır)
- [ ] Bilgi amaçlı bölümler (Multinet, Allianz sağlık) "Ignored" durumuyla ve doğru bölüm işaretiyle aktarılır
- [ ] period kayıtları yoksa oluşturuluyor (2026-01..2026-04)
- [ ] İşlem sonunda özet rapor: sheet başına okunan/aktarılan/atlanan satır sayısı

## Alt Görevler
- [ ] Excel okuma (Apache POI öneri — Java tarafı) ve sheet keşfi
- [ ] Satır parser: 12 kolon → DTO; TOPLAM ve boş satır tespiti
- [ ] Tarih/para/kart/para-birimi dönüştürücüler
- [ ] Service/provider eşleme (isim normalizasyonu)
- [ ] Bilgi bölümü (Ignored) tespiti ve işaretleme
- [ ] `expense` (+ taslak invoice) kayıtlarını yaz
- [ ] Özet rapor üretimi

## Teknik Notlar
- Java tarafında Apache POI; alternatif olarak ayrı bir Python script (openpyxl) ile bir kerelik import de olabilir — karar verilmeli (öneri: Java importer, tekrar çalıştırılabilir olsun)
- Hizmet adları sheet'te serbest metin; service eşlemesi için normalizasyon (trim, lowercase, bilinen alias'lar)
- Fatura Durumu ve Fatura Notu bu görevde okunur ama renk→enum eşlemesi E2-04'te, dosya path eşlemesi E2-03'te netleşir
- Idempotency E2-05'te ele alınacak; bu görevde en azından (period, satır-hash) ile çift kayıt önlenmeli

## Açık Sorular / Riskler
- TOPLAM ve alt-toplam satırları her sheet'te aynı formatta mı? Tespit sezgisi kırılgan olabilir — manuel doğrulama gerek
- Sheet adları Türkçe ay isimleri (Ocak/Şubat...) → period (2026-01...) eşlemesi sabit tablo ile yapılmalı
- Bazı satırlarda sağlayıcı/hizmet boş olabilir — eksik veri politikası netleştirilmeli
