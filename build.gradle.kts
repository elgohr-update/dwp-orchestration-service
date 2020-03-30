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
}

dependencies {
	// Kotlin things
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	// AWS
	implementation(platform("software.amazon.awssdk:bom:2.10.89"))
	implementation("software.amazon.awssdk:regions")
	// Spring
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springdoc:springdoc-openapi-core:1.1.49")
	// Monitoring
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-core:1.3.2")
	implementation("io.micrometer:micrometer-registry-prometheus:1.3.2")
	// JWT
	implementation ("com.auth0:java-jwt:3.10.0")
	implementation ("com.auth0:jwks-rsa:0.11.0")
	// Testing
	testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
	}
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

