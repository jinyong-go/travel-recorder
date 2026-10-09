plugins {
	kotlin("jvm") version "2.3.21" apply false
	kotlin("plugin.spring") version "2.3.21" apply false
	kotlin("plugin.jpa") version "2.3.21" apply false
	id("org.springframework.boot") version "4.1.1" apply false
	id("io.spring.dependency-management") version "1.1.7" apply false
}

// 두 모듈 모두 Kotlin·Spring Boot 애플리케이션이라 공통 설정을 여기 둔다.
// subprojects 블록에서는 타입 안전 접근자를 쓸 수 없어 설정 이름을 문자열로 적는다.
subprojects {
	group = "com.yong"
	version = "0.0.1-SNAPSHOT"

	apply(plugin = "org.jetbrains.kotlin.jvm")
	apply(plugin = "org.jetbrains.kotlin.plugin.spring")
	apply(plugin = "org.springframework.boot")
	apply(plugin = "io.spring.dependency-management")

	repositories {
		mavenCentral()
	}

	extensions.configure<JavaPluginExtension> {
		toolchain {
			languageVersion = JavaLanguageVersion.of(17)
		}
	}

	extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
		compilerOptions {
			freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
		}
	}

	dependencies {
		"implementation"("org.jetbrains.kotlin:kotlin-reflect")
		"testImplementation"("org.jetbrains.kotlin:kotlin-test-junit5")
		"testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
	}

	tasks.withType<Test> {
		useJUnitPlatform()
		// 기본 프로파일(local)은 초기 데이터를 넣으므로 테스트는 전용 프로파일로 띄운다.
		systemProperty("spring.profiles.active", "test")
	}
}
