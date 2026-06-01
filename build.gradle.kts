plugins {
	java
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "nbc"
version = "0.0.1-SNAPSHOT"
description = "c1oud_mall"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

val querydslVersion = "5.1.0"
val querydslGeneratedDir = layout.buildDirectory.dir("generated/sources/annotationProcessor/java/main")

dependencies {
	implementation("org.springframework.boot:spring-boot-h2console")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")

	// Lombok
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")

	// QueryDSL (Jakarta)
	implementation("com.querydsl:querydsl-jpa:${querydslVersion}:jakarta")
	annotationProcessor("com.querydsl:querydsl-apt:${querydslVersion}:jakarta")
	annotationProcessor("jakarta.annotation:jakarta.annotation-api")
	annotationProcessor("jakarta.persistence:jakarta.persistence-api")

	// SpringDoc OpenAPI (Swagger UI) — Spring Boot 4 호환 버전 출시 후 재도입 (TypeInformation 제거 이슈)

	runtimeOnly("com.h2database:h2")
	runtimeOnly("com.mysql:mysql-connector-j")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("com.fasterxml.jackson.core:jackson-databind")
	testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
	main {
		java.srcDirs(querydslGeneratedDir)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.generatedSourceOutputDirectory.set(querydslGeneratedDir)
}

tasks.named<Delete>("clean") {
	delete(querydslGeneratedDir)
}

tasks.withType<Test> {
	useJUnitPlatform()
}
