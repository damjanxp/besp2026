package com.bsep.pki;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestMailConfig.class)
class PkiApplicationTests {

	@Test
	void contextLoads() {
	}

}
