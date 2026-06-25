package com.ecommint.accounthr.service.importer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.InvoiceSource;

/**
 * E2-02 — {@link ServiceMasterImportService} enum eşleyicilerinin saf birim testi.
 * Spring context veya DB gerektirmez.
 */
class ServiceMasterImportMapperTest {

    @Test
    void frekansMapping() {
        assertThat(ServiceMasterImportService.parseFrequency("Aylık")).isEqualTo(Frequency.MONTHLY);
        assertThat(ServiceMasterImportService.parseFrequency("Aylık (bilgi amaçlı)"))
                .isEqualTo(Frequency.MONTHLY);
        assertThat(ServiceMasterImportService.parseFrequency("Yıllık")).isEqualTo(Frequency.YEARLY);
        assertThat(ServiceMasterImportService.parseFrequency("Kullanım bazlı"))
                .isEqualTo(Frequency.USAGE_BASED);
        assertThat(ServiceMasterImportService.parseFrequency("Ad-hoc / Tek sefer"))
                .isEqualTo(Frequency.AD_HOC);
        // Bilinmeyen/boş/null → AD_HOC (güvenli varsayılan).
        assertThat(ServiceMasterImportService.parseFrequency("")).isEqualTo(Frequency.AD_HOC);
        assertThat(ServiceMasterImportService.parseFrequency(null)).isEqualTo(Frequency.AD_HOC);
        assertThat(ServiceMasterImportService.parseFrequency("Garip")).isEqualTo(Frequency.AD_HOC);
    }

    @Test
    void informationalFrekansFlag() {
        assertThat(ServiceMasterImportService.isInformationalFrekans("Aylık (bilgi amaçlı)")).isTrue();
        assertThat(ServiceMasterImportService.isInformationalFrekans("Aylık")).isFalse();
        assertThat(ServiceMasterImportService.isInformationalFrekans("Yıllık")).isFalse();
        assertThat(ServiceMasterImportService.isInformationalFrekans(null)).isFalse();
    }

    @Test
    void aktifMapping() {
        assertThat(ServiceMasterImportService.parseActiveState("Evet")).isEqualTo(ActiveState.YES);
        assertThat(ServiceMasterImportService.parseActiveState("Evet (yıllık)"))
                .isEqualTo(ActiveState.YES);
        assertThat(ServiceMasterImportService.parseActiveState("Hayır")).isEqualTo(ActiveState.NO);
        assertThat(ServiceMasterImportService.parseActiveState("Hayır (iptal)"))
                .isEqualTo(ActiveState.NO);
        assertThat(ServiceMasterImportService.parseActiveState("Belirsiz"))
                .isEqualTo(ActiveState.UNCERTAIN);
        assertThat(ServiceMasterImportService.parseActiveState("")).isEqualTo(ActiveState.UNCERTAIN);
        assertThat(ServiceMasterImportService.parseActiveState(null))
                .isEqualTo(ActiveState.UNCERTAIN);
    }

    @Test
    void faturaKaynagiMapping() {
        assertThat(ServiceMasterImportService.parseInvoiceSource("Servis paneli"))
                .isEqualTo(InvoiceSource.SERVICE_PANEL);
        assertThat(ServiceMasterImportService.parseInvoiceSource("E-posta"))
                .isEqualTo(InvoiceSource.EMAIL);
        assertThat(ServiceMasterImportService.parseInvoiceSource("e-Fatura"))
                .isEqualTo(InvoiceSource.E_INVOICE);
        assertThat(ServiceMasterImportService.parseInvoiceSource("Drive waiting"))
                .isEqualTo(InvoiceSource.DRIVE_WAITING);
        // Boş/null → null (kaynak set edilmez).
        assertThat(ServiceMasterImportService.parseInvoiceSource("")).isNull();
        assertThat(ServiceMasterImportService.parseInvoiceSource(null)).isNull();
    }
}
