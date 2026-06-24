package com.ecommint.accounthr;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test") // use in-memory H2; no PostgreSQL needed to boot the context
class AccountHrApplicationTests {

	@Test
	void contextLoads() {
	}

}
