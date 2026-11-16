plugins {
    alias(libs.plugins.kotlin.jvm)
    // Publishes the in-memory fakes, FakeDayClock and the QuranIndex fixture to :data and :app tests:
    // testImplementation(testFixtures(project(":domain")))
    `java-test-fixtures`
}

kotlin { jvmToolchain(17) }

dependencies {
    // api, not implementation: Flow and @Inject appear in the public signatures of ports and services.
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)

    testFixturesApi(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
