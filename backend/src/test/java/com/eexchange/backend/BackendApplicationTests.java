package com.eexchange.backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Disabled("Needs live Postgres/Redis")
class BackendApplicationTests {

	@Test
	void contextLoads() {
		// Disabled: this context-load test needs live Postgres/Redis.
	}

}
