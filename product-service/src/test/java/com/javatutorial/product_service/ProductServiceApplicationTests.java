package com.javatutorial.product_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class) // starts a throwaway Postgres for this test's context
class ProductServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
