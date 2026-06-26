# [E3-06] Manuel harcama/satır girişi (ekstre yoksa)

| Alan | Değer |
|------|-------|
| Epic | E3 — Fatura Yönetim Web Uygulaması |
| Sprint | Sprint 3 |
| Öncelik | Orta |
| Bağımlılıklar | E1-02, E1-03, E1-07, E3-02, E3-03 |
| Tahmini Efor | 3 puan (~2 gün) |
| Etiketler | frontend, backend, ui, harcamalar |

## Amaç
Banka ekstresi henüz işlenmemiş ya da gelmemişken, ekip üyesi bir harcama satırını manuel olarak ekleyebilsin (servis bazlı). Böylece fatura yükleme ve eksik takip ekstreyi beklemeden başlar.

## Açıklama / Bağlam
Aylık harcamalar ekranından (E3-03) "Yeni Satır Ekle" ile açılan bir form. Kullanıcı servisi seçer (master listeden — E3-02), 12 kolonun manuel girilebilir alanlarını doldurur: Tarih, Tutar, Para Birimi, TL Karşılığı, Kart, Kullanan Takım, Amaç, Muhasebe E-posta. Servis seçilince Hizmet/Sağlayıcı/Kart ön-doldurulur. Fatura Durumu varsayılan "Bekleniyor" olur. Örnek senaryo: Axess ****3800 ile çekilen bir abonelik ekstre gelmeden satır olarak girilir.

## Kabul Kriterleri (DOD)
- [ ] "Yeni Satır Ekle" formu E3-03 ekranından açılır
- [ ] Servis seçilince Hizmet, Sağlayıcı, Kart alanları ön-doldurulur (düzenlenebilir)
- [ ] Tarih, Tutar, Para Birimi, TL Karşılığı zorunlu/doğrulanır
- [ ] Kart tanımlı kartlardan seçilir
- [ ] Yeni satır varsayılan "Bekleniyor" durumuyla oluşur
- [ ] Kaydedilen satır anında listede ve toplamda görünür (operasyonelse)
- [ ] Servis master listede yoksa "yeni servis ekle" yönlendirmesi yapılır

## Alt Görevler
- [ ] Backend: `POST /api/expenses` satır oluşturma endpoint'i
- [ ] Backend: para/sayı validasyonu, varsayılan durum atama
- [ ] Angular: yeni satır formu/modalı
- [ ] Angular: servis seçince alan ön-doldurma mantığı
- [ ] Bilgi amaçlı/operasyonel işaretleme seçeneği

## Teknik Notlar
- Form, E3-05 fatura yükleme ile birleşik akışta da kullanılabilir (önce satır, sonra fatura)
- Tutar gerçek sayı olarak saklanır; TL kolonu format `#,##0.00 ₺`
- Durum atama E3-07 state machine ile uyumlu olmalı

## Açık Sorular / Riskler
- Manuel satır ile sonradan gelen ekstre satırı çakışırsa (duplicate) nasıl ayırt edilecek? — E4 eşleştirme motoruyla koordine, satıra "kaynak: manuel/ekstre" alanı önerilir → **çözüldü: `Expense.source` (STATEMENT/MANUAL) eklendi (V12).**

## Tamamlanma Kaydı
- Durum: Tamamlandı — 2026-06-26
- YouTrack: IK-243 (sıralı varsayım — teyit edilecek)
- Repo: account-hr (backend) + account-hr-frontend
- **Backend:** `POST /api/v1/expenses` (`@PreAuthorize isAuthenticated` — ekip üyesi girebilir). `ExpenseCommandService.create`: servis çözümle (404), period find-or-create (ay bazlı, importer pattern), kart (request `cardLast4` veya servis varsayılanı; bilinmeyen → 400), takım (bilinmeyen → 400), Expense(`source=MANUAL`, `source_row_hash=null`) + EXPECTED ("Bekleniyor") Invoice oluştur; yanıt GET ile birebir `ExpenseRow` (`source` alanı eklendi). `ExpenseSource` enum + **V12** migration (`source VARCHAR(16) NOT NULL DEFAULT 'STATEMENT'`). Yardımcı: `GET /api/v1/teams` (dropdown kaynağı) + `ServiceResponse.usingTeamId` (pre-select).
- **Frontend:** E3-03 ekranına "Yeni Satır Ekle" butonu + modal (`expense-create-modal`): servis seç → Hizmet/Sağlayıcı/Kart/Takım ön-dolu (düzenlenebilir), girilebilir alanlar + "Bilgi amaçlı" checkbox, client validasyon, başarıda liste+toplam yenilenir. Takım `<select>` (GET /teams, servisten pre-select), muhasebe e-posta read-only (servis contact'tan).
- **Gerçek doğrulama (lokal PG14, V12):** POST manuel satır → 201, `source=MANUAL`, durum EXPECTED (Bekleniyor kırmızı), GET /expenses Mart'ta görünüyor; geri alma ile DB temiz (101). Manuel EXPECTED satır eksik fatura listesinden DÜŞMEZ (yalnız FOUND/E_INVOICE düşürür — E3-04 ile tutarlı, IT ile korunuyor).
- Test: backend 221/221 (3 surefire sırasında; +13). Frontend `tsc`/`ng build` temiz.
- **İki bağımsız review turu:** (1) cross-repo sözleşme kayması → `usingTeam` (string) ↔ `usingTeamId` (Long) Jackson sessiz drop + `accountingEmail` sessiz kayıp; uzlaştırıldı (team dropdown + `usingTeamId` + e-posta read-only). (2) create logic / missing-invoice semantiği / güvenlik / lifecycle temiz.
- **Borç E3-06-DR-1:** Excel "Kullanan Takım" kolonu Team entity'sine import EDİLMİYOR → `teams` tablosu boş → manuel satır team dropdown'u boş liste. `usingTeamId` opsiyonel olduğundan E3-06 çalışır; team importer eklenince dropdown dolacak (E2-01/E1-02 ile koordine).
