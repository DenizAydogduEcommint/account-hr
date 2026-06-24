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
- [ ] `waiting/` Drive'dan lokale çekilebiliyor (`rclone copy` veya Drive API) — tetikleme manuel/zamanlanmış
- [ ] Yeni gelen dosyalar algılanıp `files`/işleme kuyruğuna alınıyor (waiting durumunda)
- [ ] İşlenip ay klasörüne taşınan dosya hem lokal hem Drive'daki waiting'den siliniyor (`rclone delete` / Drive API)
- [ ] **Lokal dosya asla Drive'a push edilmiyor** (sadece waiting silme istisnası); kural koda gömülü
- [ ] Drive kimlik bilgileri/token E1-05 secret yönetiminden okunuyor
- [ ] Sync işlemleri loglanıyor ve hata durumunda (Drive erişilemez) güvenli başarısızlık
- [ ] Root folder ID ve remote adı yapılandırmadan geliyor (kod içine gömülü değil)

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
