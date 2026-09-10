plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "11.0.8"
  kotlin("plugin.spring") version "2.4.10"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:3.0.1")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
  implementation("com.squareup.retrofit2:retrofit:2.12.0")
  implementation("com.squareup.retrofit2:converter-jaxb:2.12.0")
  implementation("com.google.code.gson:gson")
  implementation("com.squareup.okhttp3:okhttp")
  implementation("javax.xml.bind:jaxb-api:2.3.1")
  implementation("org.glassfish.jaxb:jaxb-runtime:2.3.5")
  implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:7.4.1")

  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")
  implementation("com.h2database:h2")
  testImplementation("com.h2database:h2")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:3.0.1")
  testImplementation("org.springframework.boot:spring-boot-webtestclient")
  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.41") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("org.testcontainers:testcontainers-localstack")
  testImplementation("org.awaitility:awaitility-kotlin")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

kotlin {
  jvmToolchain(25)
}

tasks.register<Exec>("generateSSLCertificate") {
  commandLine("bash", "./generateSSLCertificate.sh")
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
  }
  test {
    dependsOn("generateSSLCertificate")
    environment("PFX_FILE_PASSWORD", "changeit")
    environment("UK_PRN", "TEST")
    environment("ORG_PASSWORD", "TEST")
    environment("VENDOR_ID", "TEST")
  }
}
