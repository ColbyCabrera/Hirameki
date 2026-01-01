# Remove HelpActivity - Step-by-Step Guide (Using Navigation 3)

**Goal**: Remove `HelpActivity.kt` and show `HelpScreen` as a full-page screen inside `DeckPicker` using the recommended **Navigation 3** library, instead of starting a new Activity.

---

## Conceptual Overview

Navigation 3 is a new paradigm. Instead of a `NavController`, we manage a state object (`Navigator`). Instead of a `NavHost`, we use a `NavDisplay` which observes that state.

**Target State (What We Want)**
```kotlin
// 1. Define serializable destinations (keys)
@Serializable object DeckPickerScreen
@Serializable object HelpScreen

// 2. Manage state in a custom Navigator class
class Navigator(initialKey: Any) { ... }

// 3. User clicks Help → navigator.goTo(HelpScreen) → NavDisplay shows the correct screen
```

---

### Step 1: Add Navigation 3 Dependencies

1.  **Add the dependencies** in your `build.gradle.kts` (or `libs.versions.toml`). Note that the artifacts are different from previous navigation libraries.
    ```kotlin
    implementation("androidx.navigation3:navigation3-runtime:1.0.0")
    implementation("androidx.navigation3:navigation3-ui:1.0.0")
    // For using ViewModel with Navigation 3
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.10.0")
    ```

2.  **Ensure the Kotlin Serialization plugin is enabled**. In your app-level `build.gradle.kts`:
    ```kotlin
    plugins {
        id("org.jetbrains.kotlin.plugin.serialization") // Ensure this is present
    }
    ```

---

## Step-by-Step Instructions

### Step 2: Define Your Navigation Destinations

This step is the same. Create a file like `AppNavigation.kt` for your type-safe destination keys.

```kotlin
import kotlinx.serialization.Serializable

@Serializable
object DeckPickerScreen

@Serializable
object HelpScreen
```

### Step 3: Create a Simple Navigator Class

Create a new class to manage your navigation state. This replaces the old `NavController`.

```kotlin
import androidx.compose.runtime.*

// A simple navigator class to manage the back stack
class Navigator(initialKey: Any) {
    var backStack by mutableStateOf(listOf(initialKey))
        private set

    fun goTo(key: Any) {
        backStack = backStack + key
    }

    fun goBack() {
        backStack = backStack.dropLast(1)
    }
}

@Composable
fun rememberNavigator(initialKey: Any): Navigator {
    return remember { Navigator(initialKey) }
}
```

### Step 4: Set up NavDisplay

**File**: `DeckPicker.kt`
**Location**: Inside the `setContent { }` block.

Replace your main layout with `NavDisplay`.

```kotlin
// Inside setContent
val navigator = rememberNavigator(initialKey = DeckPickerScreen)

NavDisplay(navigator) { key ->
    // This is the entryProvider lambda. It maps a key to a screen.
    scene {
        when (key) {
            is DeckPickerScreen -> {
                // Your existing DeckPicker UI goes here.
                // Pass the navigator down to handle the help click.
                // e.g., DeckPickerContent(onHelpClicked = { navigator.goTo(HelpScreen) })
            }
            is HelpScreen -> {
                HelpScreen(onNavigateUp = { navigator.goBack() })
            }
        }
    }
}
```

### Step 5: Update Help Navigation Action

**File**: `DeckPicker.kt` (or wherever your help button is)

Pass the `navigator` down to your drawer content.

**Find this code**:
```kotlin
AppNavigationItem.Help -> {
    startActivity(Intent(this@DeckPicker, HelpActivity::class.java))
}
```

**Replace with a call to the new navigator**:
```kotlin
AppNavigationItem.Help -> {
    navigator.goTo(HelpScreen)
}
```

### Step 6: Update HelpScreen for Navigation

**File**: `ui/compose/help/HelpScreen.kt`

This is the same as before. Ensure the `onNavigateUp` parameter and back button are present.

```kotlin
// Function signature
@Composable
fun HelpScreen(onNavigateUp: () -> Unit) { ... }

// Inside the LargeTopAppBar
navigationIcon = {
    IconButton(onClick = onNavigateUp) {
        Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = "Back")
    }
}
```

---

### Step 7: Remove HelpActivity (After Testing)

Once everything works:

1.  **Delete file**: `HelpActivity.kt`
2.  **Remove from `AndroidManifest.xml`**.
3.  **Clean up imports**.

---

## Testing Checklist

- [ ] Build the app successfully.
- [ ] Click Help from DeckPicker drawer → `HelpScreen` shows full-screen.
- [ ] Click the back arrow on `HelpScreen` → Navigates back to `DeckPicker`.
- [ ] Pressing the system back button on `HelpScreen` also navigates back (handled automatically by `NavDisplay`).
