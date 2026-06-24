# [E3-07] Fatura durum yönetimi ve renk kuralları (state machine)

| Alan | Değer |
|------|-------|
| Epic | E3 — Fatura Yönetim Web Uygulaması |
| Sprint | Sprint 2 |
| Öncelik | Yüksek |
| Bağımlılıklar | E1-02, E1-07 |
| Tahmini Efor | 3 puan (~2 gün) |
| Etiketler | frontend, backend, core, durum |

## Amaç
Fatura durumlarını ve renk kurallarını tek merkezde, tutarlı bir state machine olarak tanımla. Durum değişince rengin de değişmesini garanti eder; tüm ekranlar aynı kuralı kullanır.

## Açıklama / Bağlam
Excel'deki "Fatura Durumu" kolonunun web karşılığı. Durumlar ve renkleri:
- **Bulundu** — yeşil `4CAF50`, beyaz bold
- **e-Fatura** — açık yeşil `8BC34A`, beyaz bold (LeasePlan gibi e-Fatura sistemiyle gelen)
- **Bekleniyor** — kırmızı `FF4444`, beyaz bold
- **Araştırılacak** — turuncu `FF9800`, beyaz bold
- **Ignored** — turuncu `FF9800`, beyaz bold (bilgi amaçlı/operasyonel olmayan)

Bu görev; durum enum'ı, izinli geçişler (Bekleniyor↔Bulundu↔Araştırılacak↔Ignored↔e-Fatura), renk eşleşmesi ve durum değiştirme endpoint/bileşenini sağlar. Renk kodu durumdan türetilir, ayrı saklanmaz — böylece durum-renk tutarsızlığı imkânsız olur.

## Kabul Kriterleri (DOD)
- [ ] 5 durum enum olarak tanımlı (Bekleniyor, Bulundu, e-Fatura, Araştırılacak, Ignored)
- [ ] Her durumun renk + font kuralı merkezi sabitte tanımlı
- [ ] Durum rozeti bileşeni renk kurallarını birebir uygular
- [ ] Durum değiştirme aksiyonu (satır üzerinden) çalışır
- [ ] İzin verilen geçişler tanımlı; geçersiz geçiş engellenir veya kuralsız serbest geçişe izin verilir (karar netleştirilir)
- [ ] Durum değişince renk otomatik güncellenir (manuel renk girişi yok)
- [ ] Durum geçişi audit/log kaydı bırakır (kim, ne zaman)

## Alt Görevler
- [ ] Backend: durum enum + renk metadata
- [ ] Backend: `PATCH /api/expenses/{id}/status` durum güncelleme
- [ ] Backend: geçiş kuralları (izinli geçiş matrisi) ve audit log
- [ ] Angular: ortak durum rozeti bileşeni (tüm ekranlarda kullanılır)
- [ ] Angular: durum değiştirme dropdown/aksiyon
- [ ] Renk sabitlerini frontend ve backend'de tek kaynaktan beslemek (config/enum)

## Teknik Notlar
- Renk değeri durumdan türetilir; DB'de renk saklanmaz
- Fatura yükleme (E3-05) durumu otomatik "Bulundu/e-Fatura" yapar — bu state machine üzerinden
- Ignored = operasyonel toplama dahil değil; bu flag ile toplam hesapları (E3-01/E3-03) uyumlu olmalı
- Aynı renk sabitleri dashboard grafiğinde de kullanılır

## Açık Sorular / Riskler
- Geçiş kısıtlaması katı mı olsun (örn. Ignored'dan Bulundu'ya geçiş yasak mı)? — Öneri: MVP'de serbest geçiş + audit log, ileride kısıt
- e-Fatura ile Bulundu ayrımı kullanıcı seçimi mi yoksa fatura kaynağından otomatik mi?
