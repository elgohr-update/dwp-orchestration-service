import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "2.2.5.RELEASE"
	id("io.spring.dependency-management") version "1.0.9.RELEASE"
	kotlin("jvm") version "1.3.61"
	kotlin("plugin.spring") version "1.3.61"
}

group = "uk.gov.dwp.dataworks"

repositories {
	mavenCentral()
	jcenter()
	maven(url = "https://jitpack.io")
}

configurations.all {
	exclude(group="org.slf4j", module="slf4j-log4j12")
}

dependencies {
	// Kotlin things
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	// AWS
	implementation(platform("software.amazon.awssdk:bom:2.13.4"))
	implementation("software.amazon.awssdk:regions")
	implementation("software.amazon.awssdk:codegen")
	implementation("software.amazon.awssdk:elasticloadbalancingv2")
	implementation("software.amazon.awssdk:iam")
	implementation("software.amazon.awssdk:dynamodb")

	// Spring
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springdoc:springdoc-openapi-core:1.1.49")
	// Monitoring
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-core:1.4.1")
	implementation("io.micrometer:micrometer-registry-prometheus:1.4.1")
	// JWT
	implementation ("com.auth0:java-jwt:3.10.0")
	implementation ("com.auth0:jwks-rsa:0.11.0")
	// Logging things
	implementation("com.github.dwp:dataworks-common-logging:0.0.5")
	runtimeOnly("ch.qos.logback:logback-classic:1.2.3")
	runtimeOnly("ch.qos.logback:logback-core:1.2.3")

	// Testing
	implementation ("com.fasterxml.jackson.core:jackson-annotations:2.10.2")
	implementation ("com.fasterxml.jackson.core:jackson-core:2.10.2")
	implementation ("com.fasterxml.jackson.core:jackson-databind:2.10.2")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation ("software.amazon.awssdk:ecs")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.batch:spring-batch-test")
	testImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.3")
	testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "1.8"
	}
}
