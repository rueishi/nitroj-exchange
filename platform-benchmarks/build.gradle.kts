/*
 * JMH benchmark module for V12, V13, and V14 hot-path allocation and latency evidence.
 *
 * The module is intentionally separate from production code. Benchmarks allocate
 * setup fixtures before measurement, then exercise gateway/cluster hot paths
 * with the JMH GC profiler so non-zero B/op is visible before any production
 * zero-allocation claim is made.
 */

plugins {
    java
}

val jmhVersion = "1.37"
val jmhReportsDir = layout.buildDirectory.dir("reports/jmh")
val jmhAllocationJson = jmhReportsDir.map { it.file("jmh-allocation-results.json") }
val jmhLatencyJson = jmhReportsDir.map { it.file("jmh-latency-results.json") }
val jmhInclude = providers.gradleProperty("jmhInclude")
val jmhJvmArgs = listOf(
    "--enable-preview",
    "-XX:-RestrictContended",
    "--add-exports=java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
    "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
    "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
    "--enable-native-access=ALL-UNNAMED",
)

sourceSets {
    create("jmh") {
        java.srcDir("src/jmh/java")
        compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }
}

dependencies {
    implementation(project(":platform-common"))
    implementation(project(":platform-gateway"))
    implementation(project(":platform-cluster"))
    implementation(project(":platform-tooling"))
    implementation("org.openjdk.jmh:jmh-core:$jmhVersion")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion")

    "jmhImplementation"(project(":platform-common"))
    "jmhImplementation"(project(":platform-gateway"))
    "jmhImplementation"(project(":platform-cluster"))
    "jmhImplementation"(project(":platform-tooling"))
    "jmhImplementation"("org.openjdk.jmh:jmh-core:$jmhVersion")
    "jmhAnnotationProcessor"("org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion")
}

tasks.register<JavaExec>("jmh") {
    description = "Runs V12/V13/V14 hot-path JMH benchmarks with GC allocation profiling and JSON output."
    group = "verification"
    dependsOn("jmhClasses")
    mainClass.set("org.openjdk.jmh.Main")
    classpath = sourceSets["jmh"].runtimeClasspath
    jvmArgs(jmhJvmArgs)
    outputs.file(jmhAllocationJson)
    doFirst {
        jmhReportsDir.get().asFile.mkdirs()
        jmhInclude.orNull?.let { args(it) }
    }
    args(
        "-prof", "gc",
        "-jvmArgsAppend", jmhJvmArgs.joinToString(" "),
        "-rf", "json",
        "-rff", jmhAllocationJson.get().asFile.absolutePath,
    )
}

tasks.register<JavaExec>("jmhLatencyReport") {
    description = "Runs V12/V13/V14 hot-path JMH sample-time benchmarks with latency percentile JSON output."
    group = "verification"
    dependsOn("jmhClasses")
    mainClass.set("org.openjdk.jmh.Main")
    classpath = sourceSets["jmh"].runtimeClasspath
    jvmArgs(jmhJvmArgs)
    outputs.file(jmhLatencyJson)
    doFirst {
        jmhReportsDir.get().asFile.mkdirs()
        jmhInclude.orNull?.let { args(it) }
    }
    args(
        "-bm", "sample",
        "-tu", "ns",
        "-jvmArgsAppend", jmhJvmArgs.joinToString(" "),
        "-rf", "json",
        "-rff", jmhLatencyJson.get().asFile.absolutePath,
    )
}

tasks.register("verifyJmhReports") {
    description = "Verifies that allocation and latency JMH evidence artifacts were produced."
    group = "verification"
    dependsOn("jmh", "jmhLatencyReport")
    doLast {
        listOf(jmhAllocationJson.get().asFile, jmhLatencyJson.get().asFile).forEach { report ->
            require(report.isFile && report.length() > 0L) {
                "Missing or empty JMH evidence report: ${report.absolutePath}"
            }
            val content = report.readText()
            require(!content.contains("\"error\"")) {
                "JMH evidence report contains failed benchmark entries: ${report.absolutePath}"
            }
        }
    }
}
