import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
plugins {
	id("org.springframework.boot") version "2.3.4.RELEASE"
	id("io.spring.dependency-management") version "1.0.9.RELEASE"
	kotlin("jvm") version "1.4.10"
	kotlin("plugin.spring") version "1.4.10"
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
	implementation("software.amazon.awssdk:dynamodb")
	implementation("software.amazon.awssdk:iam")
	implementation("software.amazon.awssdk:kms")
	// Spring
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springdoc:springdoc-openapi-core:1.1.49")
	// Monitoring
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-core:1.5.1")
	implementation("io.micrometer:micrometer-registry-prometheus:1.5.1")
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
	testImplementation( "io.kotest:kotest-runner-junit5:4.3.0") // for kotest framework
	testImplementation( "io.kotest:kotest-assertions-core:4.3.0") // for kotest core jvm assertions
	testImplementation( "io.kotest:kotest-property:4.3.0") // for kotest property test
    testImplementation( "io.kotest:kotest-extensions-spring:4.3.0")
	testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
	testImplementation("org.mockito:mockito-inline:2.23.0")
	testImplementation( "au.com.dius:pact-jvm-provider-spring_2.12:3.6.15")
	testImplementation("cloud.localstack:localstack-utils:0.2.1")
}
// Exclude the integration tests from the main test class, as they require a localstack container to be running
tasks{
	test{
		exclude("**/integration/**")
	}
}
tasks.withType<Test> {
	useJUnitPlatform()
}
sourceSets {
	create("integration") {
		java.srcDir(file("src/integration/kotlin"))
		compileClasspath += sourceSets.getByName("main").output + configurations.testRuntimeClasspath
		runtimeClasspath += output + compileClasspath
	}
}
tasks.register<Test>("integration") {
	description = "Runs the integration tests"
	group = "verification"
	testClassesDirs = sourceSets["integration"].output.classesDirs
	classpath = sourceSets["integration"].runtimeClasspath
	useJUnitPlatform { }
	testLogging {
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
		events = setOf(
				org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
				org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
				org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
				org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT
		)
		outputs.upToDateWhen {false}
		showStandardStreams = true
	}
}
tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "1.8"
	}
}
