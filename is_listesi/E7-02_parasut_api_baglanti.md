# [E7-02] Paraşüt API / MCP bağlantısı (kimlik & gider endpoint'leri)

| Alan | Değer |
|------|-------|
| Epic | E7 — Paraşüt Entegrasyonu |
| Sprint | Sprint 7 |
| Öncelik | Yüksek |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E1-05 (secret yönetimi), E7-01 (şablon/alan netliği) |
| Etiketler | paraşüt, api, mcp, entegrasyon, oauth |

## Amaç
Paraşüt'e programatik bağlantı kurmak: kimlik doğrulama (OAuth2) ve gider kaydı oluşturma/sorgulama endpoint'lerini Java tarafında saran bir istemci yazmak. Excel köprüsünden (E7-01) tam otomatik gönderime geçişin altyapısı.

## Açıklama / Bağlam
Kaan'ın vizyonunda faturalar Paraşüt'e gider kaydı olarak gidecek (Paraşüt API veya geliştirilmekte olan Paraşüt MCP). Bu görev, Paraşüt REST API'sine (veya MCP varsa ona) bağlanan, OAuth2 token yönetimi yapan ve gider/tedarikçi/kategori uç noktalarını kapsayan bir client kütüphanesi üretir. Veri dönüştürme (E7-03) ve gönderim/senkron (E7-04) ayrı görevlerdir.

## Kabul Kriterleri (DOD)
- [ ] Paraşüt OAuth2 kimlik akışı çalışıyor; token yenileme otomatik
- [ ] Client gider (expense/purchase invoice), tedarikçi, kategori uç noktalarını çağırabiliyor
- [ ] Kimlik bilgileri secret store'dan (E1-05) okunuyor
- [ ] Hata/oran sınırı (rate limit) durumları yönetiliyor (retry/backoff)
- [ ] Sandbox/test ortamında uçtan uca çağrı doğrulandı
- [ ] Paraşüt MCP varsa, API vs MCP kararı dokümante edildi

## Alt Görevler
- [ ] Paraşüt API dokümantasyonu incele + MCP durumunu araştır (API mı MCP mi)
- [ ] OAuth2 token yönetimi (alma + yenileme) servisi
- [ ] Gider / tedarikçi / kategori endpoint sarıcıları (client)
- [ ] Rate limit + hata yönetimi
- [ ] Sandbox testleri

## Teknik Notlar
- Java HTTP client (Spring WebClient / RestClient).
- Token'lar secret store'da; client_id/secret kod dışında.
- Paraşüt MCP geliştirme aşamasında — hazır değilse REST API ile ilerle, MCP gelince adapter eklenebilir.
- Tedarikçi (sağlayıcı) kaydı Paraşüt'te yoksa otomatik oluşturma gerekebilir (E7-03 ile koordine).

## Açık Sorular / Riskler
- Paraşüt MCP ne zaman hazır olacak? Bekleme yerine REST API ile başlanmalı.
- API rate limit ve kota sınırları bilinmiyor.
- Sandbox/test hesabı erişimi gerekli.
