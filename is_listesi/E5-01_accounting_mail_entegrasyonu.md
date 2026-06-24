# [E5-01] accounting@e-commint.com posta kutusu entegrasyonu (IMAP/Gmail API)

| Alan | Değer |
|------|-------|
| Epic | E5 — Otomatik Fatura Toplama |
| Sprint | Sprint 5 |
| Öncelik | Yüksek |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E1-02 (veri modeli), E1-04 (dosya depolama), E1-05 (secret yönetimi) |
| Etiketler | otomasyon, mail, imap, fatura-toplama |

## Amaç
Kurumsal accounting@e-commint.com posta kutusunu otomatik tarayarak faturaları (PDF/XML ekleri) sisteme çekmek. Böylece ekibin faturaları manuel indirip Drive'a atma zorunluluğu ortadan kalkar.

## Açıklama / Bağlam
Ekip üyelerinin faturaları accounting@e-commint.com adresine yönlendirmesi bekleniyor ama çoğu zaman yapılmıyor. Sistem arka planda bu posta kutusuna bağlanıp gelen e-postaları tarayacak, fatura ekini (PDF, XML, e-Fatura) çıkarıp dosya deposuna kaydedecek ve ham bir "gelen fatura" kaydı oluşturacak. Bu görev sadece toplama (ingest) katmanını kapsar; belge okuma/parse (E5-03) ve eşleştirme (E5-04) ayrı görevlerde yapılır.

Bağlantı için IMAP veya Gmail API kullanılabilir. E-Commint Google Workspace kullandığından Gmail API (OAuth2 service account / domain-wide delegation) tercih edilir; alternatif olarak IMAP + uygulama şifresi. Seçim secret yönetimiyle (E1-05) ilişkilidir.

## Kabul Kriterleri (DOD)
- [ ] Sistem accounting@e-commint.com kutusuna güvenli şekilde bağlanabiliyor (Gmail API veya IMAP)
- [ ] Yeni/okunmamış e-postalar taranıyor; daha önce işlenenler tekrar işlenmiyor (UID/messageId takibi)
- [ ] PDF, XML, e-Fatura ekleri çıkarılıp dosya deposuna (E1-04) kaydediliyor
- [ ] Her ek için bir "ham fatura" kaydı (gönderen, konu, alınma tarihi, dosya yolu) DB'ye yazılıyor
- [ ] E-posta gövdesinde link olarak gelen faturalar (panel indirme linki) ayrı işaretleniyor (en azından loglanıyor)
- [ ] Bağlantı bilgileri kod içine gömülü değil; secret yönetiminden okunuyor

## Alt Görevler
- [ ] Gmail API vs IMAP karar dokümanı (kısa) + seçimin secret/yetki gereksinimleri
- [ ] OAuth2 / IMAP kimlik akışını E1-05 secret store ile entegre et
- [ ] Posta tarama servisi (okunmamış filtreleme, sayfalama)
- [ ] Ek çıkarma (MIME parse) + dosya deposuna yazma
- [ ] İşlenen mesajları takip eden idempotency tablosu/kolonu (messageId)
- [ ] Birim + entegrasyon testleri (mock posta kutusuyla)

## Teknik Notlar
- Backend Java + Spring Boot. Gmail API için google-api-client / IMAP için jakarta.mail.
- Service account ile domain-wide delegation kullanılırsa Workspace admin onayı gerekir.
- Ekleri ham haliyle sakla; parse etme E5-03'te. Dosya isimlendirme nihai değil; orijinal ad + messageId yeterli.
- Idempotency kritik: aynı maili iki kez işleyip duplicate fatura üretme.

## Açık Sorular / Riskler
- Gmail API mı IMAP mı? Workspace admin domain-wide delegation onayı verir mi?
- accounting@ kutusunda fatura dışı çok sayıda mail olabilir — tarama performansı ve gürültü filtreleme (E5-03'e devredilebilir).
- Bazı faturalar gövdede link olarak gelir (ek yok) — bu görevde sadece tespit, indirme E5-05 panel worker kapsamında olabilir.
