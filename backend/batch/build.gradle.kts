dependencies {
	implementation("org.springframework.boot:spring-boot-starter-batch")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-batch-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway")
	testImplementation("org.flywaydb:flyway-database-postgresql")
	testImplementation("org.testcontainers:testcontainers-postgresql")
}

// 스키마의 기준은 external-api 의 마이그레이션이다. 배치는 코드로 의존하지 않고 테스트 때만 이 스크립트를
// 받아 테스트 DB 를 만든다. local·dev·prod 에서는 API 서버가 만든 스키마를 그대로 쓴다.
// 배치가 마이그레이션을 직접 돌리지 않는 것은 같은 DB 의 스키마를 두 애플리케이션이 나눠 갖지 않기 위해서다.
tasks.processTestResources {
	from("../external-api/src/main/resources/db/migration") { into("db/migration") }
}
