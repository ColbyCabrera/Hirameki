---
name: android-testing
description: Comprehensive testing strategy involving Unit, Integration, Hilt, and Screenshot tests.
upstream: android/skills testing/testing-setup (last-updated 2026-09-23)
---

# Android Testing Strategies

This skill provides expert guidance on testing modern Android applications, inspired by "Now in Android". It covers **Unit Tests**, **Hilt Integration Tests**, and **Screenshot Testing**.

Aligned with upstream `testing-setup`: analyze the existing stack first and respect it; prefer fakes over mocks; keep behavior UI tests in the `test` sourceset with Robolectric unless device execution is requested; cover navigation (back handling, deep links, multi-backstack exit-through-home) and window-size/font-scale variations.

## 0. Analyze Before Installing (upstream Step 1)

Before adding dependencies, inventory `libs.versions.toml` / build files for:

1. DI framework (Hilt, Koin, Anvil, vanilla Dagger).
2. Unit framework (JUnit4/JUnit5), mocking framework (MockK/Mockito) — only add MockK if clearly necessary.
3. Robolectric usage: platform fakes vs behavior UI tests vs Roborazzi screenshots.
4. Compose vs Views vs hybrid; Compose Test APIs vs Espresso (+ wrappers like Kaspresso).
5. Screenshot approach: device-based (e.g. Dropshots), Robolectric-based (Roborazzi), or LayoutLib-based (Paparazzi / Compose Preview Screenshot Testing).
6. E2E frameworks (UI Automator / Appium) — keep E2E to ~5% of tests, covering big user journeys.

Emit a short Markdown report of findings before changing the strategy.

## Testing Pyramid

1.  **Unit Tests**: Fast, isolate logic (ViewModels, Repositories).
2.  **Integration Tests**: Test interactions (Room DAOs, Retrofit vs MockWebServer).
3.  **UI/Screenshot Tests**: Verify UI correctness (Compose).

## Dependencies (`libs.versions.toml`)

Ensure you have the right testing dependencies.

```toml
[libraries]
junit4 = { module = "junit:junit", version = "4.13.2" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version = "1.1.5" }
espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version = "3.5.1" }
compose-ui-test = { group = "androidx.compose.ui", name = "ui-test-junit4" }
hilt-android-testing = { group = "com.google.dagger", name = "hilt-android-testing", version.ref = "hilt" }
roborazzi = { group = "io.github.takahirom.roborazzi", name = "roborazzi", version.ref = "roborazzi" }
```

## Screenshot Testing

Screenshot tests ensure your UI doesn't regress visually. This repo uses **Roborazzi** because it runs on the JVM (fast) without needing an emulator.

Upstream note (2026-09-23): for *new* screenshot infrastructure, Google now recommends the Compose Preview Screenshot Testing tool via AGP test suites on AGP 9.5.0-alpha03+ (`testOptions { screenshotTests.create(...) }`), with the standalone `com.android.compose.screenshot` plugin as deprecated legacy. Don't combine both setups in one module and don't upgrade AGP just to adopt test suites. Existing Roborazzi coverage below stays valid — treat a migration as a separate decision.

### Setup

1.  Add the plugin to `libs.versions.toml`:
    ```toml
    [plugins]
    roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
    ```
2.  Apply it in your module's `build.gradle.kts`:
    ```kotlin
    plugins {
        alias(libs.plugins.roborazzi)
    }
    ```

### Writing a Screenshot Test

```kotlin
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class MyScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureMyScreen() {
        composeTestRule.setContent {
            MyTheme {
                MyScreen()
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage()
    }
}
```

## Hilt Testing

Use `HiltAndroidRule` to inject dependencies in tests.

```kotlin
@HiltAndroidTest
class MyDaoTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: MyDatabase
    private lateinit var dao: MyDao

    @Before
    fun init() {
        hiltRule.inject()
        dao = database.myDao()
    }
    
    // ... tests
}
```

## Running Tests

*   **Unit**: `./gradlew test`
*   **Screenshots**: `./gradlew recordRoborazziDebug` (to record) / `./gradlew verifyRoborazziDebug` (to verify)

## Prefer Fakes, Cover Navigation + Configurations (upstream)

*   **Fakes first**: for unit and UI tests, introduce an interface + `Default` implementation and a `Fake` in the test sourceset; reach for mocks only when the class can't be faked. Use DI runtime fakes to simulate no-network / bad-JSON / permission-denied / no-disk-space scenarios and to swap slow deps (real DB → in-memory).
*   **Navigation tests**: verify back handling, deep links, and multi-backstack "exit through home" behavior.
*   **Window sizes / settings**: cover compact/medium/expanded widths and heights plus font-scale 1.5 and alternate themes for screenshot tests; use `DeviceConfigurationOverride` for Compose behavior tests. Always verify state restoration in behavior tests.
*   **Databases**: for Room/SQLite, add instrumented tests against an in-memory database so the on-device SQLite engine is exercised.
*   **Device-only cases**: edge-to-edge rendering, notifications, picture-in-picture need a device — use Dropshots-style instrumented screenshots there.

## References

- Upstream skill: `android/skills` — `testing/testing-setup`
