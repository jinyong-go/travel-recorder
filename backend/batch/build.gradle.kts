dependencies {
	implementation("org.springframework.boot:spring-boot-starter-batch-jdbc")
	runtimeOnly("com.h2database:h2")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-batch-test")
}
