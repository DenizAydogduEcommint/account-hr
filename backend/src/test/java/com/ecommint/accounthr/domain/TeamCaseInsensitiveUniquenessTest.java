package com.ecommint.accounthr.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.ecommint.accounthr.config.JpaAuditingConfig;
import com.ecommint.accounthr.repository.TeamRepository;

import org.springframework.context.annotation.Import;

/**
 * E2/HIGH — {@code teams.name} büyük/küçük-harf DUYARSIZ tekillik (providers V11 / services V14
 * deseniyle tutarlı). Postgres'te V16 fonksiyonel index {@code uq_teams_name_lower (lower(name))}
 * bunu DB düzeyinde garanti eder; uygulama (importer find-or-create) {@code findByNameIgnoreCase}
 * ile aynı niyeti taşır → case-variant duplicate ("DevOps" + "devops") OLUŞMAZ.
 *
 * <p>Testler H2 üzerinde (Flyway kapalı; şema entity'lerden) {@code findByNameIgnoreCase}'in
 * dedup sözleşmesini doğrular: farklı büyük/küçük harfle yazılan aynı isim TEK takıma çözülür.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class TeamCaseInsensitiveUniquenessTest {

    @Autowired private TeamRepository teamRepository;

    @Test
    void findByNameIgnoreCase_resolvesDifferentCaseToSameTeam() {
        Team team = new Team();
        team.setName("DevOps");
        teamRepository.save(team);

        // Farklı büyük/küçük harf varyasyonlarının HEPSİ aynı satıra çözülmeli (find-or-create
        // dedup'ı bu sayede ikinci bir takım OLUŞTURMAZ).
        assertThat(teamRepository.findByNameIgnoreCase("devops")).isPresent();
        assertThat(teamRepository.findByNameIgnoreCase("DEVOPS")).isPresent();
        assertThat(teamRepository.findByNameIgnoreCase("DevOps")).isPresent();

        Optional<Team> found = teamRepository.findByNameIgnoreCase("dEvOpS");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(team.getId());
    }

    @Test
    void findOrCreatePattern_doesNotDuplicateOnCaseVariant() {
        // Importer'ların find-or-create deseninin simülasyonu: önce IgnoreCase ara, yoksa oluştur.
        String first = "Platform";
        Team t1 = teamRepository.findByNameIgnoreCase(first).orElseGet(() -> {
            Team t = new Team();
            t.setName(first);
            return teamRepository.save(t);
        });

        String caseVariant = "PLATFORM";
        Team t2 = teamRepository.findByNameIgnoreCase(caseVariant).orElseGet(() -> {
            Team t = new Team();
            t.setName(caseVariant);
            return teamRepository.save(t);
        });

        // İkinci çağrı YENİ takım oluşturmadı → case-variant duplicate YOK.
        assertThat(t2.getId()).isEqualTo(t1.getId());
        assertThat(teamRepository.count()).isEqualTo(1);
    }
}
