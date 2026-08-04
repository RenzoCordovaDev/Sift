package runner

import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * JUnit Platform Suite entry point for the Sift E2E test suite.
 *
 * This class is the bridge between Gradle's `useJUnitPlatform()` test runner and
 * the Cucumber engine. When Gradle discovers this class via JUnit Platform, it delegates
 * to the `cucumber` engine ([io.cucumber.junit.platform.engine.CucumberTestEngine]), which
 * in turn finds all `*.feature` files under `classpath:features/` and executes each
 * scenario against the step definitions in the `steps` and `support` packages.
 *
 * **Run command**:
 * ```
 * ./gradlew :e2e:e2eTest
 * ```
 * Add `-Dappium.url=http://127.0.0.1:4723` and `-Dadb.serial=emulator-5554` as needed.
 *
 * **Tag filtering** (Cucumber's standard tag expression syntax):
 * ```
 * ./gradlew :e2e:e2eTest -Dcucumber.filter.tags="@KnownContact or @FirstAttempt"
 * ```
 *
 * Scenarios tagged with [@PendingUI] will appear in the report as PENDING rather than
 * FAILED or PASSED — they are intentionally incomplete until the F4 Settings UI is built.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(
    key = GLUE_PROPERTY_NAME,
    value = "steps,support"
)
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty,html:build/reports/cucumber/f1-call-screening.html,json:build/reports/cucumber/f1-call-screening.json"
)
class CucumberE2ERunner
