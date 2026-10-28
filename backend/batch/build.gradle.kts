dependencies {
	implementation("org.springframework.boot:spring-boot-starter-batch")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-batch-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway")
	testImplementation("org.flywaydb:flyway-database-postgresql")
	testImplementation("org.testcontainers:testcontainers-postgresql")
}

// 테스트 DB 를 만들 때만 API 서버의 마이그레이션을 가져다 쓴다.
// 스키마는 API 서버가 관리하므로 배치는 그 밖에서 마이그레이션을 돌리지 않는다.
tasks.processTestResources {
	from("../external-api/src/main/resources/db/migration") { into("db/migration") }
}
