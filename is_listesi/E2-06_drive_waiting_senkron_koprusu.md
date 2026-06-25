# [E2-06] Google Drive (rclone / Drive API) waiting/ senkron köprüsü

| Alan | Değer |
|------|-------|
| Epic | E2 — Veri Migrasyonu |
| Sprint | Sprint 3 |
| Öncelik | Orta |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E1-02, E1-04, E1-05 |
| Etiketler | backend, integration, drive, sync |

## Amaç
Ekibin/kullanıcının Drive üzerinden bıraktığı faturaların (`faturalar/waiting/`) yeni sisteme otomatik akmasını sağlayan, mevcut rclone akışını koruyan bir senkron köprüsü kurmak.

## Açıklama / Bağlam
Mevcut akışta `gdrive-ecommint` remote'u (rclone) ile `2026_Harcamalar.xlsx` ve `faturalar/` Drive ile senkronize ediliyor. Önemli kural: **Drive'a lokal dosya push edilmez** — `waiting/`'e sadece kullanıcı/ekip Drive üzerinden ekler. Sistem bu dosyaları **pull** eder, işler (eşleştirme E2-03 / ileride otomatik), ay klasörüne taşır ve hem lokal hem Drive'daki orijinali siler.

Bu görev köprüyü kurar: Drive'dan waiting'i çekme, yeni dosyaları algılama, işleme kuyruğuna alma. rclone subprocess mi yoksa Java Google Drive API mi kararı verilecek.

## Kabul Kriterleri (DOD)
- [x] `waiting/` Drive'dan lokale çekiliyor (`rclone copy`, subprocess) — manuel tetik `POST /api/v1/admin/drive/pull-waiting`. **Gerçek Drive'da kanıtlandı: 17 dosya pull edildi.**
- [x] Yeni gelen dosyalar algılanıp newFiles listesinde dönüyor (DrivePullResult)
- [x] İşlenince Drive waiting'den silme: `deleteFromWaiting` (internal, idempotent, `rclone deletefile`). Implementasyon + mock test var; gerçek silme bilinçli olarak çalıştırılmadı (kullanıcı kararı: silme mock)
- [x] **Lokal asla Drive'a push edilmiyor** — yapısal imkansız (servis sadece pull+delete metodu, reflection testiyle korunuyor). **Gerçek veride copy-only kanıtlandı** (backend log: sadece `rclone copy`, hiç delete; Drive sayısı düşüşü dış kaynaklı, backend kapalıyken de sürdü)
- [x] Drive credential rclone.conf'ta (backend token yönetmez) — dokümante
- [x] Sync loglama + güvenli başarısızlık: DriveSyncException → 502; bad input → 400; enabled=false → no-op
- [x] Root folder ID + remote adı + binary config'ten (`app.drive.*`, hiçbiri gömülü değil)

## Tamamlanma Kaydı
- Durum: ✅ Tamamlandı — 2026-06-25 · **E2 EPİC TAMAMLANDI (6/6)**
- YouTrack: IK-237 (sıralı varsayım — teyit edilecek)
- Repo: account-hr (backend)
- Üretilenler: DriveSyncService (pullWaiting + deleteFromWaiting), CommandRunner soyutlama + ProcessBuilderCommandRunner, DriveSyncProperties (`app.drive.*`), AdminDriveController `POST /api/v1/admin/drive/pull-waiting` (ADMIN), DriveSyncResult, DriveSyncException→502 + DriveSyncValidationException→400
- **GERÇEK DRIVE DOĞRULAMASI (rclone v1.74.3, gdrive-ecommint remote)**: pull endpoint gerçek Drive'a bağlandı → 17 dosya storage root/waiting'e indi (Mayıs kart ekstreleri, GoDaddy/Pipedrive/OpenAI faturaları vb.). **Push/silme YOK** (log: yalnız `rclone copy`); Drive waiting sayı değişimi dış kaynaklı (backend kapalıyken de değişti = bizim sistemimiz değil).
- Test: `./mvnw test` 116/116 (3 surefire sırasında; +23). Tüm testler FakeCommandRunner/mock — gerçek subprocess test'te çalışmaz.
- **Üç bağımsız review turu (agent 2 + parent 1):** subprocess stdout/stderr drain deadlock, timeout enforcement, validation sırası (agent); CRITICAL timeout-yakın başarılı pull'un yanlışlıkla fail raporlanması + bad-filename 502→400 (parent) → hepsi düzeltildi.
- expenses/ Drive aynasına push YOK (yalnız pull); storage root kuralı korundu.

## Alt Görevler
- [ ] Entegrasyon yöntemi kararı: rclone subprocess vs Google Drive API (Java client)
- [ ] waiting/ pull operasyonu + yeni dosya tespiti (hash/isim)
- [ ] İşlenen dosyayı Drive waiting'den silme operasyonu
- [ ] "Drive'a push yok" kuralının güvenli implementasyonu
- [ ] Drive credential/token yönetimi (E1-05)
- [ ] Zamanlanmış/manuel tetikleme + loglama + hata yönetimi

## Teknik Notlar
- rclone remote: `gdrive-ecommint` (selman.celebi@e-commint.com), root folder ID: `1vQWwqbozxyb9fsFjpKlk6XZ5cMlURLKm`
- rclone subprocess en hızlı yol (mevcut akış aynen korunur); Drive API daha kontrollü ama OAuth kurulumu gerektirir — MVP için rclone öneri
- Pull → işle → ay klasörüne taşı → waiting'den sil sırası asla bozulmamalı
- Bu görev "köprü"; otomatik fatura indirme/eşleştirme ileri epic'lerde — burada sadece waiting akışı
- Ana Excel sync'i (xlsx push/pull) bu sistemde uzun vadede gereksizleşecek (DB ana kaynak olacak); migrasyon döneminde paralel tutulabilir

## Açık Sorular / Riskler
- rclone mı Drive API mı? rclone sunucuda kurulu olmalı (deploy bağımlılığı, E1-06)
- Çakışma: aynı dosya hem işlenmiş hem Drive'da kalmışsa? İdempotent silme + hash kontrolü gerek
- Drive erişim hesabı (selman.celebi) token süresi/yenileme yönetimi
- Migrasyon döneminde Excel hem Drive'da hem DB'de — hangisi "gerçek" kaynak? (Geçiş planı netleştirilmeli)
