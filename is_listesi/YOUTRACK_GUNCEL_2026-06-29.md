# YouTrack — Güncel & Detaylı İş Listesi (2026-06-29)

> Gerçek IK numaraları (Excel'den teyit) + güncel durumlar + yeni açılan işler + "ne işe yarar" açıklaması.
> **Durum:** Done = tamamlandı/CI yeşil · In Progress = devam/kısmi · Open = başlanmadı · **Bloklu** = dış girdi bekliyor.
> **Dev:** "Ben" = geliştirme · "Selman" = yazılım müdürü (koordinasyon / dış-girdi temini / onay). Atama önerisidir, netleştirilecek.

## EPIC 1 — Platform ve Teknik Altyapı (IK-291) · Durum: In Progress
| Task ID | Task | Durum | Açıklama (ne işe yarar) | Dev |
|---------|------|-------|--------------------------|-----|
| IK-225 | E1-01 Proje iskeleti (Spring Boot+Angular+PostgreSQL+Docker) | Done | Tüm sistemin çalıştığı temel iskelet — backend, frontend, veritabanı ayağa kalkar | Ben |
| IK-226 | E1-02 Veritabanı şeması & domain modeli (SERVICE-FIRST) | Done | Servis/harcama/fatura verisinin saklandığı yapı; her şeyin temeli | Ben |
| IK-227 | E1-03 Kimlik doğrulama & yetkilendirme (JWT+roller) | Done | Kullanıcı girişi + rol bazlı erişim (admin/muhasebe/ekip) | Ben |
| IK-228 | E1-04 Fatura dosya depolama servisi | Done | Faturaların güvenli saklanması, klasör kuralları, mükerrer önleme | Ben |
| IK-229 | E1-05 Config, secret, loglama, audit | Done | Şifre/sır yönetimi + her değişikliğin denetim kaydı (audit) | Ben |
| IK-230 | E1-06 CI/CD ve dağıtım (build/test/deploy+staging) | In Progress | Otomatik test+derleme (CI yeşil); canlıya otomatik dağıtım sunucu bekliyor | Ben / Selman |
| IK-231 | E1-07 API tasarım standartları (REST, hata formatı, OpenAPI) | Done | Tüm API'lerin tutarlı sözleşmesi + Swagger dokümanı | Ben |
| **YENİ** | **E1-08 Kullanıcı Yönetimi (Backoffice)** | **Done** | **Admin'in kullanıcı ekleyip rol/şifre yönetmesi (son-admin koruması, şifre çift-giriş+göz). YouTrack issue açılacak** | Ben |

## EPIC 2 — Veri Migrasyonu ve Dosya Senkronizasyonu (IK-292) · Durum: In Progress
| Task ID | Task | Durum | Açıklama (ne işe yarar) | Dev |
|---------|------|-------|--------------------------|-----|
| IK-232 | E2-01 Excel ay sheet'leri → expenses importer | Done | Mevcut Excel takip dosyasındaki harcamaları sisteme aktarır | Ben |
| IK-233 | E2-02 Servisler master sheet → services importer | Done | Abonelik/servis listesini sisteme aktarır (eksik fatura kontrolünün kaynağı) | Ben |
| IK-234 | E2-03 faturalar/ klasör tarama + dosya eşleştirme | Done | Diskteki fatura dosyalarını ilgili kayıtlarla eşler | Ben |
| IK-235 | E2-04 Fatura durumu & renk enum migrasyonu | Done | Fatura durumlarını (Bulundu/Bekleniyor vb.) standart hale getirir | Ben |
| IK-236 | E2-05 Migrasyon doğrulama / mutabakat | Done | Excel toplamları ↔ DB tutuyor mu kontrolü + mükerrer önleme | Ben |
| IK-237 | E2-06 Google Drive waiting/ senkron köprüsü | Done | Drive ile yerel klasör arası rclone senkron altyapısı | Ben |

## EPIC 3 — Muhasebe Operasyonları ve Kullanıcı Arayüzleri (IK-293) · Durum: In Progress
| Task ID | Task | Durum | Açıklama (ne işe yarar) | Dev |
|---------|------|-------|--------------------------|-----|
| IK-238 | E3-01 Dashboard: aylık özet + eksik fatura sayacı | Done | Ayın genel durumu tek ekranda (toplam gider, eksik fatura) | Ben |
| IK-239 | E3-02 Servisler ekranı (master liste yönetimi) | Done | Abonelikleri görüntüleme/ekleme/düzenleme | Ben |
| IK-240 | E3-03 Aylık harcamalar ekranı (12 kolon tablo) | Done | Her kart çekiminin satır satır listesi | Ben |
| IK-241 | E3-04 Eksik fatura ekranı (servis↔ay çapraz doğrulama) | Done | "Hangi faturayı bekliyoruz" — kilit MVP özelliği | Ben |
| IK-242 | E3-05 Fatura yükleme UI | Done | Servis+ay seçip fatura dosyası yükleme | Ben |
| IK-243 | E3-06 Manuel harcama/satır girişi | Done | Ekstre gelmeden elle harcama ekleme | Ben |
| IK-244 | E3-07 Fatura durum yönetimi (state machine + renk) | Done | Fatura durumunu güncelleme, renk kuralları, denetim izi | Ben |
| IK-245 | E3-08 Rol bazlı görünümler | Done | Her rolün (admin/muhasebe/ekip) doğru ekranı görmesi | Ben |
| IK-246 | E3-09 Fatura detay / önizleme (PDF/XML) | Done | Faturayı indirmeden uygulama içinde görüntüleme | Ben |
| IK-287 | E3-10 Eksik fatura tutar özeti | Done | "Kaç TL belgesiz gider bekliyor" görünürlüğü | Ben |
| IK-288 | E3-11 KDV alanları (fatura KDV ayrımı) | Done | Faturadan KDV matrahı/oranı/tutarı ayrımı (muhasebe için) | Ben |
| **YENİ** | **UI Bilgi Kartları (6 sayfa)** | **Done** | **Her sayfada "ne işe yarar + nasıl kullanılır" açıklayıcı kart (kapatılabilir). YouTrack issue açılacak** | Ben |

## EPIC 4 — Finansal Veri İşleme ve Eşleştirme Motoru (IK-294) · Durum: In Progress
| Task ID | Task | Durum | Açıklama (ne işe yarar) | Dev |
|---------|------|-------|--------------------------|-----|
| IK-247 | E4-01 Banka ekstresi yükleme & parse | In Progress | Banka kart ekstresini okuyup işlemleri otomatik çıkarma. **Altyapı bitti; gerçek banka ekstresi (Selman) bekleniyor — Bloklu** | Ben / Selman |
| IK-248 | E4-02 İşlem↔servis/fatura eşleştirme motoru | Open · Bloklu | Ekstre işlemlerini servislere bağlama + manuel↔ekstre mükerrer önleme (muhasebe kritik) | Ben |
| IK-249 | E4-03 Dönem-içi hareket dökümü işleme | Open · Bloklu | Ekstre kesim sonrası işlemlerin işlenmesi | Ben |
| IK-250 | E4-04 Eşleşmeyen işlem & faturasız uyarıları | Open | Eşleşmeyen/belgesiz işlemleri işaretleme | Ben |
| IK-251 | E4-05 Çoklu banka/kart normalizasyonu + TL karşılığı | Open · Bloklu | Farklı banka formatlarını standartlaştırma | Ben |

## EPIC 5 — Otomatik Fatura Toplama ve Doküman İşleme (IK-295) · Durum: In Progress
| Task ID | Task | Durum | Açıklama (ne işe yarar) | Dev |
|---------|------|-------|--------------------------|-----|
| IK-252 | E5-01 accounting@ posta kutusu entegrasyonu (IMAP/Gmail) | Open · Bloklu | Maile gelen faturaları otomatik çekme. **accounting@ grup; mailbox'a dönüştürülmeli (Fatma/IT)** | Ben / Selman |
| IK-253 | E5-02 Drive waiting/ otomatik pull & işleme | Done | Drive'a atılan faturaları otomatik çekme (sadece kopya, güvenli) | Ben |
| IK-254 | E5-03 Fatura belge okuma & veri çıkarımı (PDF/OCR) | In Progress | Fatura PDF'inden no/tarih/tutar/KDV/sağlayıcı otomatik çıkarma. **PDF bitti (13/13 gerçek fatura); JPG→OCR kaldı** | Ben |
| IK-255 | E5-04 Otomatik servis-fatura eşleştirme & duplicate | Open | Okunan faturayı doğru harcamaya bağlama + mükerrer tespiti | Ben |
| IK-256 | E5-05 Servis paneli login & fatura indirme worker | Open | Panelden manuel indirilen faturaları otomatikleştirme (Selenium/Playwright) | Ben / Selman |
| IK-257 | E5-06 Toplama orkestrasyonu & zamanlanmış iş | Open | Tüm toplama akışını zamanlı/kuyruklu çalıştırma | Ben |

## EPIC 6 — Bildirim ve Hatırlatma Yönetimi (IK-296) · Durum: Open
| Task ID | Task | Durum | Açıklama (ne işe yarar) | Dev |
|---------|------|-------|--------------------------|-----|
| IK-258 | E6-01 Eksik fatura → hatırlatma maili | Open | İlgili kişiye otomatik fatura hatırlatması | Ben |
| IK-259 | E6-02 Sağlayıcıya fatura talep maili (şablon) | Open | Firmadan fatura isteme şablonu | Ben |
| IK-260 | E6-03 Bildirim şablonları (TR) + gönderim takibi | Open | Mail şablonları + gönderildi takibi | Ben |
| IK-261 | E6-04 Hatırlatma zamanlama & eskalasyon | Open | Tekrarlayan hatırlatma + üst kademeye taşıma | Ben |
| IK-262 | E6-05 In-app bildirim & tercihler | Open | Uygulama içi bildirim + kullanıcı tercihleri | Ben |

## EPIC 7 — Muhasebe Sistemi Entegrasyonları (IK-297) · Durum: Open
| Task ID | Task | Durum | Açıklama (ne işe yarar) | Dev |
|---------|------|-------|--------------------------|-----|
| IK-263 | E7-01 Paraşüt Excel şablonu (IK-167) | Open | Faturaları Paraşüt'e Excel ile aktarma şablonu | Ben / Selman |
| IK-264 | E7-02 Paraşüt API / MCP bağlantısı | Open · Bloklu | Paraşüt'e programatik bağlantı (API erişimi Selman/muhasebe) | Ben / Selman |
| IK-265 | E7-03 Fatura → Paraşüt gider dönüştürücü | Open | Faturayı Paraşüt gider kaydına çevirme (KDV/döviz eşleme) | Ben |
| IK-266 | E7-04 Paraşüt otomatik gönderim & senkron | Open | Giderleri otomatik Paraşüt'e gönderme + hata yönetimi | Ben |
| IK-267 | E7-05 Mükerrer / çakışma kontrolü | Open | Aynı faturanın iki kez gitmesini önleme | Ben |

## EPIC 8 — Raporlama, İzlenebilirlik ve Denetim (IK-298) · Durum: Open
| Task ID | Task | Durum | Açıklama (ne işe yarar) | Dev |
|---------|------|-------|--------------------------|-----|
| IK-268 | E8-01 Aylık fatura paketi dışa aktarımı (zip/Excel) | Open | Muhasebeciye gönderilecek aylık paket | Ben |
| IK-269 | E8-02 İzlenebilirlik raporları | Open | Eşlenen/eksik/durum dağılımı raporları | Ben |
| IK-270 | E8-03 Otomatik aylık özet & e-posta | Open | Ay sonu otomatik özet maili | Ben |
| IK-271 | E8-04 Denetim/audit log görünümü | Open | Değişiklik geçmişi + hata-nedeni analizi ekranı | Ben |

## EPIC 9 — Multi-Tenant ve Ölçeklenebilir Mimari (IK-299) · Durum: Open (gelecek vizyon)
| Task ID | Task | Durum | Açıklama (ne işe yarar) | Dev |
|---------|------|-------|--------------------------|-----|
| IK-272 | E9-01 Personel masraf raporları + OCR | Open | Çalışan masraflarını OCR ile işleme | Ben |
| IK-273 | E9-02 İK konsolidasyon & ödeme tablosu → Paraşüt | Open | Kişi bazlı ödemeleri Paraşüt giderine bağlama | Ben |
| IK-274 | E9-03 Banka hesap ekstreleri tam mutabakat | Open | Tüm banka hareketleriyle tam denkleştirme | Ben |
| IK-275 | E9-04 Satış faturaları & tahsilat eşleştirme | Open | Gelir tarafı (satış/tahsilat) eşleştirme | Ben |
| IK-276 | E9-05 Coffeetropic uyarlaması (multi-tenant) | Open | Sistemi başka şirkete uyarlama (çok kiracılı mimari) | Ben |

---

## Yeni açılacak issue'lar (Excel'de yok — eklenecek)
| Öneri ID | Epic | Task | Durum | Açıklama |
|----------|------|------|-------|----------|
| (yeni) | Epic 1 (IK-291) | **E1-08 Kullanıcı Yönetimi (Backoffice)** | Done | Admin kullanıcı ekleme/şifre/rol yönetimi |
| (yeni) | Epic 3 (IK-293) | **UI Bilgi Kartları (6 sayfa)** | Done | Sayfaların kendini açıklaması (ne işe yarar + nasıl kullanılır) |

## YouTrack'te yapılacak durum güncellemeleri (özet)
- **Done yap:** IK-287 (E3-10), IK-288 (E3-11), IK-253 (E5-02). Ayrıca Excel'de "Open/In Progress" görünen ama tamamlanmış olan E1-05/E3-01/E3-04/E3-08 → Done.
- **In Progress yap:** IK-254 (E5-03 — PDF bitti, OCR kaldı), IK-247 (E4-01 — altyapı bitti, ekstre bekliyor).
- **Bloklu işaretle (dış girdi):** IK-247/248/249/251 (banka ekstresi), IK-252 (mailbox).
- **Başlık düzelt:** IK-288 → "Başlık:" fazlalığını çıkar → "[E3-11] KDV alanları (fatura KDV ayrımı)".
