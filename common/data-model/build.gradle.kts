plugins {
    `java-library`
    id("com.google.protobuf")
}

group = "com.beacon.common"

val protobufVersion = "3.25.3"

java {
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    api("com.google.protobuf:protobuf-java:$protobufVersion")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
}
