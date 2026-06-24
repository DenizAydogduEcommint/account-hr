# [E5-05] Servis paneli login & fatura indirme worker (Selenium/Playwright)

| Alan | Değer |
|------|-------|
| Epic | E5 — Otomatik Fatura Toplama |
| Sprint | Sprint 6 |
| Öncelik | Yüksek |
| Tahmini Efor | 8 puan (~5 gün) |
| Bağımlılıklar | E1-05 (secret yönetimi), E5-03 (belge okuma), E5-04 (eşleştirme) |
| Etiketler | otomasyon, scraping, selenium, playwright, secret |

## Amaç
Faturası maile/Drive'a gelmeyen servisler için, sistemin servis paneline otomatik login olup faturayı kendisinin indirmesi. Kaçan faturaların ana kaynağı bu — Contabo, Zoom, OpenAI gibi panelden indirilen servisler.

## Açıklama / Bağlam
Bazı servislerde fatura yalnızca panele giriş yapıp manuel indirilerek alınıyor (Servisler master'da Fatura Kaynağı = "Servis paneli"). Bu görev, her panel için bir "indirme adaptörü" (login → faturalar sayfasına git → ilgili dönemin faturasını indir) yazılmasını ve bunların güvenli credential ile çalışan bir worker üzerinde koşturulmasını kapsar.

Her servisin paneli farklı olduğundan adaptör başına ayrı geliştirme gerekir; bu görev framework + ilk 2-3 yüksek öncelikli adaptörü (Contabo, OpenAI, Zoom) kapsar. Diğer adaptörler ayrı küçük görevlere bölünebilir.

## Kabul Kriterleri (DOD)
- [ ] Panel adaptörü arayüzü tanımlı (login, faturaları listele, dönem faturasını indir)
- [ ] Credential'lar secret store'dan (E1-05) okunuyor; kod/DB'de düz metin yok
- [ ] Contabo, OpenAI, Zoom için çalışan adaptörler (en az bu üçü)
- [ ] İndirilen dosya dosya deposuna kaydedilip E5-03 parse + E5-04 eşleştirmeye veriliyor
- [ ] Login başarısız / 2FA / CAPTCHA durumları yakalanıp loglanıyor ve ilgili kişiye bildirim/mail tetikleniyor
- [ ] Worker headless çalışabiliyor (sunucu ortamı); her çalıştırma izlenebilir (audit log E8-04)

## Alt Görevler
- [ ] PanelAdapter arayüzü + ortak login/indirme iskeleti
- [ ] Teknoloji kararı: Selenium/Playwright (Java) vs Python worker mikroservisi
- [ ] Secret yönetimi entegrasyonu (servis başına credential)
- [ ] Contabo adaptörü
- [ ] OpenAI adaptörü (ay içinde çoklu fatura — hepsini indir)
- [ ] Zoom adaptörü
- [ ] 2FA/CAPTCHA/oturum hatası yakalama + bildirim tetikleme (eksik fatura → ilgili kişiye mail)
- [ ] Headless çalışma + retry + screenshot-on-failure (debug)

## Teknik Notlar
- Çekirdek Java/Spring; scraping için Playwright-Java veya Selenium-Java tercih. Gerekirse ayrı Python (Playwright) worker — Java ana servis kuyrukla tetikler (E5-06).
- 2FA olan paneller otomasyona dirençli — bu servisler için fallback: ilgili kişiye otomatik mail (Servisler master'daki ilgili kişi e-postası: Zoom→zoomowner2@, OpenAI→accounting@, Contabo ilgilisi).
- Bot tespiti riskine karşı: insan benzeri gecikme, sabit user-agent, oturum cookie saklama.
- Headless tarayıcı sunucuda kaynak tüketir — worker'ı ayrı container'da çalıştır.

## Açık Sorular / Riskler
- 2FA/CAPTCHA → tam otomasyon mümkün olmayabilir; fallback mail akışı netleşmeli.
- Panel UI değişirse adaptör kırılır — bakım yükü yüksek. İzleme/alarm gerekli.
- Credential saklama ve hesap güvenliği (kurumsal hesaplara otomatik login) — güvenlik onayı şart.
- Java vs Python worker kararı diğer ekip görevlerini etkiler (E5-06 orkestrasyon).
