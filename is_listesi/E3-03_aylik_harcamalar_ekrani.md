# [E3-03] Aylık harcamalar ekranı: 12 kolonlu tablo

| Alan | Değer |
|------|-------|
| Epic | E3 — Fatura Yönetim Web Uygulaması |
| Sprint | Sprint 2 |
| Öncelik | Yüksek |
| Bağımlılıklar | E1-02, E1-03, E1-07, E2-01 |
| Tahmini Efor | 5 puan (~3 gün) |
| Etiketler | frontend, backend, ui, harcamalar |

## Amaç
Bir ayın tüm harcama satırlarını Excel'deki gibi tablo halinde göster; ekip ve muhasebe ay bazında tüm giderleri ve fatura durumlarını görebilsin.

## Açıklama / Bağlam
Excel ay sheet'inin web karşılığı. Seçili ay için harcama satırları 12 kolonlu tabloda listelenir: Tarih, Hizmet, Sağlayıcı, Tutar, Para Birimi, TL Karşılığı, Kart, Kullanan Takım, Amaç, Muhasebe E-posta, Fatura Durumu, Fatura Notu. Fatura Durumu kolonu renkli rozet (badge) olarak gösterilir (renk kuralları E3-07). Ay seçici, kart filtresi, durum filtresi ve serbest metin arama bulunur. Tablo altında operasyonel toplam (TL) gösterilir; Ignored/bilgi amaçlı satırlar toplama dahil edilmez ama listede ayrı bir bölümde görünür.

## Kabul Kriterleri (DOD)
- [x] 12 kolon tabloda (Tarih, Hizmet, Sağlayıcı, Tutar, Para Birimi, TL Karşılığı, Kart, Kullanan Takım, Amaç, Muhasebe E-posta, Fatura Durumu, Fatura Notu) — tarayıcıda doğrulandı
- [x] Ay seçici ile farklı aylar; varsayılan dolu ay (Mart 2026), dropdown=sorgu senkron
- [x] Fatura Durumu renkli rozet (ortak status-badge bileşeni, StatusColors)
- [x] Kart/durum/serbest-metin filtreleri (status filtre temsilci invoice'a göre — badge ile tutarlı)
- [x] Operasyonel toplam (TL) tablo altında (informational hariç)
- [x] Bilgi amaçlı satırlar (Multinet/sigorta) AYRI bölümde, operasyonel toplama dahil değil
- [x] Satıra tıklayınca salt-okunur detay modalı (durum değiştir/fatura ekle E3-05/E3-07)
- [x] Tutar/TL `#.##0,00` tr-TR formatı; tarih DD.MM.YYYY

## Alt Görevler
- [ ] Backend: `GET /api/expenses?month=YYYY-MM&card=&status=&q=` listeleme endpoint'i
- [ ] Backend: operasyonel/bilgi amaçlı ayrımı (is_informational flag)
- [ ] Angular: 12 kolonlu veri tablosu (sıralama + sayfalama)
- [ ] Angular: filtre çubuğu (ay/kart/durum/arama)
- [ ] Angular: durum rozeti bileşeni (ortak)
- [ ] Toplam satırı hesaplama (backend'den gelen toplam)

## Teknik Notlar
- Para birimi bazında değerler ham sayı olarak saklanır; format ön yüzde uygulanır
- Tarih formatı `DD.MM.YYYY`
- Bilgi amaçlı satırlar için satır seviyesinde flag, Excel'deki gri başlıklı bölümün karşılığı
- Aynı durum rozeti bileşeni dashboard ve eksik fatura ekranında da kullanılır

## Açık Sorular / Riskler
- ~~Inline mi modal mı?~~ → Salt-okunur detay modalı (düzenleme E3-05/E3-07).
- ~~Sayfalama mı sonsuz kaydırma mı?~~ → Sayfalama (önceki/sonraki).

## Tamamlanma Kaydı
- Durum: ✅ Tamamlandı — 2026-06-26
- YouTrack: IK-240 (sıralı varsayım — teyit edilecek)
- Repo: account-hr (backend) + account-hr-frontend
- **Backend:** `GET /api/v1/expenses?month=&card=&status=&q=&page=&size=` (ExpenseQueryService, Specification). ExpenseRow (12 kolon), operasyonel toplam (informational hariç, E2-05 ile birebir) + bilgi-amaçlı satırlar AYRI + alt toplam. month/status validation → 400. Temsilci invoice (max-id) status; status filtre de temsilci invoice'a göre (badge tutarlı).
- **Frontend:** Harcamalar ekranı (12 kolonlu tablo + filtre çubuğu + sayfalama + salt-okunur detay modal), ortak `status-badge` bileşeni, ortak `DEFAULT_MONTH` sabiti (2026-03), sidebar "Harcamalar" aktif. Ayrıca login parola **göster/gizle (göz) butonu** eklendi.
- **Gerçek doğrulama (lokal PG14 + tarayıcı):** Şubat: 28 operasyonel (112.390,98 ₺) + 23 bilgi-amaçlı (353.020,43 ₺) ayrı — E2-05 ile birebir. Filtreler (card→9, FOUND→24), invalid status/month→400. Tarayıcıda Mart 2026 dolu açılıyor (dropdown=sorgu senkron), 12 kolon + badge + göz butonu görsel onaylı.
- Test: backend `./mvnw test` 146/146 (3 surefire sırasında); frontend `ng build` temiz.
- **Bağımsız review + tarayıcı testi 4 sorun buldu:** (1) status filtre/badge tutarsızlığı → temsilci invoice; (2) cards subscription leak → ngOnDestroy; (3) varsayılan ay boş + dropdown senkronsuz → ortak DEFAULT_MONTH + `<option [selected]>` (native select binding fix); (4) N+1 → **teknik borç** (aşağıda).
- **Not (kurtarma):** ilk fix agent N+1 düzeltmesinin ortasında takıldı, backend derlenmez kaldı; `list()` çalışır haline geri alındı, kalan fix'ler dar kapsamlı ikinci agentla + elle tamamlandı.

## Teknik Borç
- **N+1 (ExpenseQueryService ana satırlar):** `findAll(spec, pageable)` lazy association'lar + satır başına `serviceContactRepository` sorgusu → sayfa başına ~250 ek sorgu. MVP'de küçük veri (≤50 satır/sayfa) ile kabul edilebilir; sonra `@EntityGraph`/fetch-join + batch e-posta ile optimize edilecek.
