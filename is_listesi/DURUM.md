# account-hr — İlerleme Durumu (Yerel Pano)

> Bu dosya **yerel ilerleme takibidir**. Tek doğru kaynak **YouTrack** (IK-225 … IK-276).
> Her görev bitince: ilgili `E*.md` dosyasına "Tamamlanma Kaydı" eklenir + bu tablo güncellenir + YouTrack'e yorum/durum işlenir.
> **Durum simgeleri:** ✅ Tamamlandı · 🔄 Devam ediyor · ⬜ Bekliyor · ⏸️ Bloklu

**Son güncelleme:** 2026-06-24
**Özet:** 1 / 52 tamamlandı · **MVP = E1 + E2 + E3**

---

## E1 — Temel Altyapı & Veri Modeli
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E1-01 | Proje iskeleti (Spring Boot + Angular + PostgreSQL + Docker) | ✅ | IK-225. İki repo. Docker `up` doğrulaması kaldı |
| E1-02 | Veritabanı şeması & domain modeli | ⬜ | Sıradaki |
| E1-03 | Kimlik doğrulama & yetkilendirme (JWT) | ⬜ | |
| E1-04 | Dosya depolama servisi | ⬜ | |
| E1-05 | Config, secret, loglama, audit | ⬜ | |
| E1-06 | CI/CD & dağıtım | ⬜ | |
| E1-07 | API tasarım standartları | ⬜ | |

## E2 — Veri Migrasyonu
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E2-01 | Excel ay-sheet importer | ⬜ | |
| E2-02 | Servisler master importer | ⬜ | |
| E2-03 | Faturalar klasör tarama & eşleştirme | ⬜ | |
| E2-04 | Durum/renk enum migrasyonu | ⬜ | |
| E2-05 | Migrasyon doğrulama & mutabakat | ⬜ | |
| E2-06 | Drive waiting senkron köprüsü | ⬜ | |

## E3 — Web Uygulaması (MVP)
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E3-01 | Dashboard / aylık özet | ⬜ | |
| E3-02 | Servisler ekranı | ⬜ | |
| E3-03 | Aylık harcamalar ekranı | ⬜ | |
| E3-04 | Eksik fatura ekranı | ⬜ | MVP çekirdeği |
| E3-05 | Fatura yükleme UI | ⬜ | MVP çekirdeği |
| E3-06 | Manuel harcama girişi | ⬜ | |
| E3-07 | Fatura durum state machine | ⬜ | |
| E3-08 | Rol bazlı görünümler | ⬜ | |
| E3-09 | Fatura detay / önizleme | ⬜ | |

## E4 — Banka Ekstresi
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E4-01 | Ekstre yükleme & parse | ⬜ | |
| E4-02 | Eşleştirme motoru | ⬜ | |
| E4-03 | Dönem-içi hareket dökümü | ⬜ | |
| E4-04 | Eşleşmeyen işlem uyarıları | ⬜ | |
| E4-05 | Banka/kart normalizasyonu | ⬜ | |

## E5 — Otomatik Toplama
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E5-01 | Accounting mail entegrasyonu | ⬜ | |
| E5-02 | Drive waiting pull | ⬜ | |
| E5-03 | Fatura belge okuma (OCR/parse) | ⬜ | |
| E5-04 | Servis-fatura eşleştirme | ⬜ | |
| E5-05 | Servis paneli indirme worker | ⬜ | |
| E5-06 | Toplama orkestrasyonu | ⬜ | |

## E6 — Bildirim
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E6-01 | Eksik fatura hatırlatma maili | ⬜ | |
| E6-02 | Sağlayıcı fatura talep maili | ⬜ | |
| E6-03 | Bildirim şablonları & gönderim takibi | ⬜ | |
| E6-04 | Hatırlatma zamanlama & eskalasyon | ⬜ | |
| E6-05 | In-app bildirim tercihleri | ⬜ | |

## E7 — Paraşüt Entegrasyonu
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E7-01 | Paraşüt Excel şablonu | ⬜ | |
| E7-02 | Paraşüt API bağlantı | ⬜ | |
| E7-03 | Fatura → gider dönüştürücü | ⬜ | |
| E7-04 | Paraşüt otomatik gönderim | ⬜ | |
| E7-05 | Mükerrer/çakışma kontrolü | ⬜ | |

## E8 — Raporlama
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E8-01 | Aylık fatura paketi export | ⬜ | |
| E8-02 | İzlenebilirlik raporları | ⬜ | |
| E8-03 | Otomatik aylık özet e-posta | ⬜ | |
| E8-04 | Audit log & hata analizi | ⬜ | |

## E9 — Gelecek
| Görev | Başlık | Durum | Not |
|-------|--------|-------|-----|
| E9-01 | Personel masraf OCR | ⬜ | |
| E9-02 | İK konsolidasyon & ödeme tablosu | ⬜ | |
| E9-03 | Banka mutabakat | ⬜ | |
| E9-04 | Satış faturaları & tahsilat | ⬜ | |
| E9-05 | CoffeeTropic multitenant | ⬜ | |
