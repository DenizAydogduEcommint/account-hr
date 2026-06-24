# [E4-04] Eşleşmeyen işlem ve "ödenmiş ama faturasız" uyarıları

| Alan | Değer |
|------|-------|
| Epic | E4 — Banka Ekstresi İşleme |
| Sprint | Sprint 5 |
| Öncelik | Orta |
| Bağımlılıklar | E3-04, E4-02 |
| Tahmini Efor | 5 puan (~3 gün) |
| Etiketler | backend, frontend, uyari |

## Amaç
İki risk noktasını ekrana taşır: (1) servise bağlanamayan işlemler, (2) ödendiği halde faturası olmayan servisler. Bunlar muhasebe açığının asıl kaynağıdır.

## Açıklama / Bağlam
E4-02 eşleştirmesinden geçemeyen işlemler (bilinmeyen işyeri, yeni servis vb.) bir "eşleşmeyen işlemler" listesinde toplanır; kullanıcı bunları elle bir servise bağlar veya yeni servis oluşturur. Ayrıca ekstrede ödeme görünen ama hiç faturası yüklenmemiş servisler "ödenmiş ama faturasız" uyarısı olur — bu, E3-04 eksik fatura mantığını ekstre verisiyle güçlendirir (sadece "beklenen" değil, "kesinlikle ödenmiş ve faturası yok").

## Kabul Kriterleri (DOD)
- [ ] Eşleşmeyen işlemler listesi gösterilir (tarih, tutar, işyeri, kart)
- [ ] Kullanıcı eşleşmeyen işlemi mevcut servise bağlayabilir
- [ ] Kullanıcı eşleşmeyen işlemden yeni servis oluşturabilir (E3-02'ye yönlendirme)
- [ ] "Ödenmiş ama faturasız" uyarı listesi gösterilir (ekstrede çekim var, fatura yok)
- [ ] Bu uyarılar eksik fatura ekranı (E3-04) ile tutarlı/entegre
- [ ] Uyarı sayısı dashboard'a (E3-01) yansıtılabilir
- [ ] İşlem çözüldüğünde (bağlandı/fatura geldi) listeden düşer

## Alt Görevler
- [ ] Backend: eşleşmeyen işlemler endpoint'i
- [ ] Backend: "ödenmiş ama faturasız" hesaplama (işlem var + fatura yok)
- [ ] Backend: işlemi servise manuel bağlama endpoint'i
- [ ] Angular: eşleşmeyen işlemler ekranı + bağlama aksiyonu
- [ ] Angular: "ödenmiş ama faturasız" uyarı bileşeni

## Teknik Notlar
- "Ödenmiş ama faturasız" = net pozitif çekimi olan servis-ay AND fatura durumu Bulundu/e-Fatura değil
- Bu, E3-04'ün "beklenen ama yok" mantığını gerçek ödeme kanıtıyla zenginleştirir
- Eşleşmeyen işlem servise bağlanınca E4-02 kural tablosu öğrenebilir (opsiyonel iyileştirme)

## Açık Sorular / Riskler
- "Ödenmiş ama faturasız" ile E3-04 "eksik" listesi tek ekranda mı birleşsin yoksa ayrı mı? — Öneri: E3-04 içinde "ödeme kanıtı var" rozetiyle vurgu
- Eşleşmeyen işlem hiç çözülmezse ne olur? (yaşlanma/eskalasyon — E6 ile koordine)
