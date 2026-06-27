package com.ecommint.accounthr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommint.accounthr.domain.RawTransaction;
import com.ecommint.accounthr.domain.enums.RawTxnStatus;

/**
 * {@link RawTransaction} (E4-01) deposu. batchRef = source_file_sha256; bir batch
 * o sha256'ya sahip satırlar kümesidir.
 */
public interface RawTransactionRepository extends JpaRepository<RawTransaction, Long> {

    /** Bir batch'in (sha256) tüm satırları. */
    List<RawTransaction> findBySourceFileSha256(String sourceFileSha256);

    /**
     * Idempotency önizlemesi için kart+dönem kapsamlı satırlar: aynı sha256 farklı
     * kart/dönem altında da bulunabilir; "zaten yüklendi" önizlemesi yalnızca eşleşen
     * batch'in satırlarını göstermelidir (çapraz-kart sızıntısı yok).
     */
    List<RawTransaction> findBySourceFileSha256AndCardIdAndPeriodId(
            String sourceFileSha256, Long cardId, Long periodId);

    /** Bir batch'in belirli durumdaki satırları (confirm/discard için). */
    List<RawTransaction> findBySourceFileSha256AndStatus(String sourceFileSha256, RawTxnStatus status);

    /**
     * Idempotency: aynı dosya (sha256) + kart + dönem için belirli durumda satır var mı?
     * CONFIRMED bir batch zaten varsa yeniden parse/persist edilmez.
     */
    boolean existsBySourceFileSha256AndCardIdAndPeriodIdAndStatus(
            String sourceFileSha256, Long cardId, Long periodId, RawTxnStatus status);
}
