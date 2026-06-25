package com.ecommint.accounthr.domain.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.ecommint.accounthr.config.CredentialEncryptionProperties;
import com.ecommint.accounthr.config.JpaAuditingConfig;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.Service;
import com.ecommint.accounthr.domain.ServiceCredential;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.CredentialType;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceCredentialRepository;
import com.ecommint.accounthr.repository.ServiceRepository;
import com.ecommint.accounthr.service.crypto.EncryptionService;

import jakarta.persistence.EntityManager;

/**
 * E1-05: {@link EncryptedStringConverter}'ın ServiceCredential.secret üzerinde uçtan uca
 * doğrulaması. Entity'den okurken düz metin görünür; DB kolonunun ham değeri ise
 * şifrelidir (at-rest encryption kanıtı).
 */
@DataJpaTest
@Import({ JpaAuditingConfig.class, EncryptedStringConverterTest.EncryptionTestConfig.class })
@ActiveProfiles("test")
class EncryptedStringConverterTest {

    @TestConfiguration
    static class EncryptionTestConfig {
        @Bean
        CredentialEncryptionProperties credentialEncryptionProperties() {
            CredentialEncryptionProperties props = new CredentialEncryptionProperties();
            props.setMasterKey("dGVzdC1maXhlZC1tYXN0ZXIta2V5LTMyYnl0ZSEhISE=");
            return props;
        }

        @Bean
        EncryptionService encryptionService(CredentialEncryptionProperties props) {
            return new EncryptionService(props);
        }

        // Holder'ı bean olarak yükleyince statik instance set olur → converter çalışır.
        @Bean
        EncryptionServiceHolder encryptionServiceHolder(EncryptionService service) {
            return new EncryptionServiceHolder(service);
        }
    }

    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private ServiceCredentialRepository credentialRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void secretIsPlaintextThroughEntityButEncryptedAtRest() {
        Provider provider = new Provider();
        provider.setName("Contabo");
        provider = providerRepository.save(provider);

        Service service = new Service();
        service.setName("Contabo VPS");
        service.setProvider(provider);
        service.setFrequency(Frequency.MONTHLY);
        service.setActiveState(ActiveState.YES);
        service = serviceRepository.save(service);

        String plaintextSecret = "panel-parolasi-super-gizli-42";

        ServiceCredential cred = new ServiceCredential();
        cred.setService(service);
        cred.setCredType(CredentialType.PANEL_LOGIN);
        cred.setUsername("admin");
        cred.setSecret(plaintextSecret);
        cred.setLabel("Contabo panel");
        cred = credentialRepository.save(cred);
        Long credId = cred.getId();

        entityManager.flush();
        entityManager.clear();

        // (1) Entity üzerinden okurken düz metin görünür (converter çözer).
        ServiceCredential loaded = credentialRepository.findById(credId).orElseThrow();
        assertThat(loaded.getSecret()).isEqualTo(plaintextSecret);
        assertThat(loaded.getUsername()).isEqualTo("admin");

        // (2) Ham kolon değeri (native query) düz metin DEĞİL — at-rest şifreli.
        Object rawColumn = entityManager
                .createNativeQuery("SELECT secret FROM service_credentials WHERE id = :id")
                .setParameter("id", credId)
                .getSingleResult();

        assertThat(rawColumn).isInstanceOf(String.class);
        String rawSecret = (String) rawColumn;
        assertThat(rawSecret).isNotEqualTo(plaintextSecret);
        assertThat(rawSecret).doesNotContain(plaintextSecret);
        assertThat(rawSecret).doesNotContain("panel-parolasi");
    }
}
