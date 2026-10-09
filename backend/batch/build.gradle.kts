dependencies {
	implementation("org.springframework.boot:spring-boot-starter-batch")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	runtimeOnly("com.h2database:h2")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-batch-test")
}

// 스키마의 기준은 external-api 의 schema.sql 하나다. 배치는 코드로 의존하지 않고 이 파일만 받아
// local·test 의 인메모리 DB 를 같은 구조로 만든다. 두 곳에 DDL 을 두면 한쪽만 고쳐졌을 때 어긋난다.
tasks.processResources {
	from("../external-api/src/main/resources/schema.sql")
}
