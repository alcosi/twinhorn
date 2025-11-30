 import com.google.protobuf.gradle.id

plugins {
    id("java")
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.4"
    id("com.google.protobuf") version "0.9.4"
}

group = "org.twins"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val grpcVersion = "1.76.0"
val protobufVersion = "4.33.1"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation ("org.postgresql:postgresql:42.7.3")
    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
    testCompileOnly("org.projectlombok:lombok:1.18.32")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.32")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // Spring & Mockito testing utilities
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-inline:5.2.0")

    // gRPC & Protobuf
    implementation("io.grpc:grpc-netty-shaded:${grpcVersion}")
    implementation("io.grpc:grpc-protobuf:${grpcVersion}")
    implementation("io.grpc:grpc-stub:${grpcVersion}")
    testImplementation("io.grpc:grpc-inprocess:${grpcVersion}")
    implementation("com.google.protobuf:protobuf-java:${protobufVersion}")    // For Java 9+ compatibility
    implementation("io.grpc:protoc-gen-grpc-java:${grpcVersion}")
    implementation("org.apache.tomcat:annotations-api:6.0.53")
    implementation("org.springframework.boot:spring-boot-starter-web") // For RestTemplate and @Value
    // Resilience4j circuit breaker
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.3.0")
    // twins DTO lib
    implementation("com.alcosi.twins:twins-core-dto:1.4.25")
}

// gRPC/protobuf plugin
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc-java") {
            path = "/usr/local/bin/protoc-gen-grpc-java"
        }
    }

    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc-java")
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}