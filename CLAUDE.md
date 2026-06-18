# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Legado (开源阅读) is an open-source Android e-book reader supporting custom book sources, RSS, local TXT/EPUB, read-aloud (TTS), and a built-in web server for remote management. Language is primarily **Kotlin** with a small amount of Java (e.g. `QueryTTF.java`).

## Build Commands

```bash
# Debug build
./gradlew assembleAppDebug

# Kotlin compile only (fast check, no packaging)
./gradlew :app:compileAppDebugKotlin

# Release build (what CI uses)
./gradlew assembleAppRelease --build-cache --parallel --daemon --warning-mode all

# Clean
./gradlew clean

# Download Cronet native library
./gradlew app:downloadCronet

# Build the web frontend (Vue.js, separate from Gradle)
cd modules/web && pnpm i && pnpm build
```

The web module output is copied into `app/src/main/assets/web/vue/`.

## Modules

| Module | Purpose |
|---|---|
| `:app` | Main Android application (`io.legado.app`) |
| `:modules:book` | EPUB/UMD parsing (`me.ag2s`) |
| `:modules:rhino` | Rhino JS engine wrapper (`com.script`) |
| `modules/web` | Vue 3 + Element Plus web frontend (not a Gradle module) |

## Architecture

**MVVM** with AndroidViewModel + ViewBinding, coexisting View (XML) and Jetpack Compose screens. No DI framework — ViewModels obtained via standard `viewModels()` delegates. Compose is the forward path: new screens (Bookshelf, BookInfo, ReadRecord) are Compose; legacy screens remain XML. Navigation uses `startActivity<T>()` extensions; no Navigation Compose.

### Key base classes (`io.legado.app.base`)
- `BaseViewModel` — extends `AndroidViewModel`, provides `execute()`/`executeLazy()`/`submit()` coroutine helpers wrapping `Coroutine.async()` (custom coroutine wrapper in `help/coroutine/`)
- `BaseActivity<VB>` — handles theming, system bars, background images, menu tinting. Constructor params: `fullScreen`, `theme` (Theme enum), `toolBarTheme`, `transparent`, `imageBg`. Subclasses implement `onActivityCreated()` instead of `onCreate()`
- `VMBaseActivity<VB, VM>` — extends `BaseActivity<VB>`, mandates a `viewModel` property
- `BaseFragment(layoutID)` — implements `onFragmentCreated()` instead of `onViewCreated()`. Provides `setSupportToolbar()` for fragment-level toolbars
- `VMBaseFragment<VM>` — extends `BaseFragment`, mandates a `viewModel` property
- `BaseDialogFragment(layoutID, adaptationSoftKeyboard)` — sets `filletBackground` on dialog, handles E-Ink mode, provides `execute()` coroutine helper
- `BasePrefDialogFragment` — simpler dialog base for preference-style dialogs
- `BaseService` — extends `LifecycleService`, handles foreground notification and permission checks

### Package layout (`io.legado.app`)
- `data/` — Room database (v76, 22 entities), DAOs, entities, migrations
- `model/` — Business logic: `analyzeRule/` (Jsoup/XPath/JsonPath/Regex parsers), `localBook/`, `webBook/`, `rss/`
- `ui/` — Activities/Fragments/ViewModels organized by feature
- `help/` — Business helpers: `config/`, `coroutine/`, `http/`, `rhino/`, `source/`, `storage/` (backup/restore)
- `lib/` — Third-party wrappers: `theme/`, `webdav/`, `permission/`, `prefs/`
- `service/` — Background services (AudioPlay, CacheBook, Download, WebService, TTS)
- `web/` — Built-in NanoHTTPD + WebSocket server
- `utils/` — 80+ Kotlin extension function files

### Important patterns
- **LiveEventBus** for cross-component event communication
- **Coroutines** everywhere via a custom `Coroutine` wrapper in `help/coroutine/`
- **ViewBinding delegates** for binding inflation (see UI Patterns section below)
- **Rule engine** in `model/analyzeRule/` — parses book source rules using Jsoup, XPath, JsonPath, Regex
- **Rhino JS** — book sources can include JavaScript rules evaluated via embedded Rhino (`help/rhino/`)
- Database access is via the global `appDb` instance in `data/AppDatabase.kt`

### Entry points
- `App.kt` — Application class (Cronet, Rhino, notifications, theme init)
- `ui/welcome/WelcomeActivity.kt` — LAUNCHER activity
- `ui/main/MainActivity.kt` — Main screen with bottom nav (bookshelf, explore, RSS, my)
- `api/ReaderProvider.kt` — Content Provider API for external access
- `web/HttpServer.kt` — Built-in web server

## UI Patterns

### ViewBinding Delegates (`utils/viewbindingdelegate/`)

The app uses a custom property-delegate-based ViewBinding system:

- **Activities**: `override val binding by viewBinding(ActivityFooBinding::inflate)` — uses a lazy delegate, auto-calls `setContentView`
- **Fragments/Dialogs**: `private val binding by viewBinding(DialogFooBinding::bind)` — lifecycle-aware delegate that binds to `viewLifecycleOwner` and auto-clears on destroy
- Import: `io.legado.app.utils.viewbindingdelegate.viewBinding`
- Two fragment forms: `viewBinding(MyBinding::bind)` (binds to fragment view) or `viewBinding(vbFactory, viewBindingRootId)` (binds to sub-view by ID)

### Theme System

Themes are applied at runtime via SharedPreferences. Architecture: user prefs → computation → memory cache → UI.

**Storage**: User colors stored in default SharedPreferences under `PreferKey` keys (`cPrimary`/`cNPrimary`, `cAccent`/`cNAccent`, etc. — day/night pairs). Computed output persisted in `"app_themes"` SharedPreferences via ThemeStore.

**Computation (`help/config/ThemeConfig.kt`)**: `applyTheme()` reads user prefs → validates light/dark background → calls `M3ColorHelper.computeTokens()` to derive 10 Material 3 tokens → writes all 20+ colors to ThemeStore SP and caches in memory.

**Memory cache (`lib/theme/ThemeColors.kt`)**: Immutable data class holding all 20+ runtime colors. `ThemeStore.readAllColors()` reads SP once; `ThemeStore.getColors()` returns cached instance. `invalidateColors()` called on theme change. All `ThemeStore.xxx()` companion getters delegate to cache instead of reading SP every time.

**Reactive source (`lib/theme/ThemeManager.kt`)**: `StateFlow<ThemeColors>` for Compose. `ThemeManager.refresh()` invalidates cache + pushes new snapshot. Called from `ThemeConfig.applyDayNightInit()` (startup) and `applyDayNight()` (user changes).

**View consumption (`lib/theme/MaterialValueHelper.kt`)**: Extension properties on `Context`/`Fragment` — `primaryColor`, `accentColor`, `backgroundColor`, `bottomBackground`, `cardBackgroundColor`, `popupBackgroundColor`, M3 tokens, plus `isDarkTheme`, `elevation`, `filletBackground`. All delegate to `ThemeStore` getters (now cached).

**Compose consumption (`ui/common/compose/LegadoTheme.kt`)**: `rememberLegadoColorScheme()` reads from `ThemeManager.colors.collectAsState()`. Three special composables bypass ColorScheme for user-customizable overrides: `legadoCardBackgroundColor()`, `legadoPopupBackgroundColor()`, `legadoPopupPrimaryTextColor()`.

**Version migration**: `ThemeConfig.THEME_CONFIG_VERSION` constant. `migrateIfNeeded()` calls `ThemeStore.isConfigured(context, version)` — when version increments, old SP data is transparently recomputed. Current version: 1.

**Widget tinting**: `TintHelper` dispatches per-widget-type tint; `ThemeCheckBox`/`ThemeSwitch`/etc. auto-tint in init. `MenuExtensions.applyTint()`, `DialogExtensions.applyTint()`, `PopupThemeApplier` (reflection-based popup theming).

**Theme flow**: User picks color → `ColorPreference` persists to PreferKey SP → `ThemeConfigFragment` detects change → `ThemeConfig.applyTheme()` → `ThemeManager.refresh()` → invalidates cache → `postEvent(RECREATE)` → all Activities recreate. At startup: `App.onCreate()` → `applyDayNightInit()` → same path without RECREATE.

### Compose Patterns

31 Compose files under `ui/`. Compose is used for new screens and shared components; legacy View system still handles reading, settings, and most dialogs.

**Entry point pattern**: Fragment overrides `onCreateView()` to return a `ComposeView`. Fragment holds `MutableStateFlow` fields for reactive state, passes data as parameters to a stateless `@Composable` function. No ViewModel — data is read from `appDb` directly and sorted/filtered in the Fragment.

```kotlin
class BookshelfComposeFragment : BaseBookshelfFragment(0) {
    private val sortVersionFlow = MutableStateFlow(0)
    private val isRefreshingFlow = MutableStateFlow(false)

    override fun onCreateView(inflater, container, savedInstanceState): View {
        return ComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    BookshelfScreen(
                        groups = groups,
                        books = sortBooks(books),
                        // ... 20+ callbacks and state values
                    )
                }
            }
        }
    }
}
```

**Theme wrapping**: Every Compose root must be wrapped in `LegadoTheme { ... }`. For nested overlays (dropdowns, bottom sheets, alert dialogs) that need their own `MaterialExpressiveTheme` window, call `rememberLegadoColorScheme()` directly and create a separate theme scope:

```kotlin
// In RoundDropdownMenu, ModalLegadoBottomSheet, LegadoAlertDialog, etc.
val colorScheme = rememberLegadoColorScheme()
MaterialExpressiveTheme(colorScheme = colorScheme) {
    // popup content
}
```

**Colors**: `MaterialTheme.colorScheme.*` for standard colors. `legadoCardBackgroundColor()`/`legadoPopupBackgroundColor()` for user-customizable overrides (reads from `PreferKey.cCardBg`/`cNCardBg`).

**View→Compose bridge**: `ComposeDropdownAnchor` wraps a `Spinner`/`EditText` and manages a `PopupWindow` containing a `RoundDropdownMenu` — enables mixing Compose dropdowns in View-based layouts. `LegadoSheetDialog`/`LegadoContentSheet` wrap Compose content in `DialogFragment` for bottom sheets.

**E-Ink**: Compose screens check `AppConfig.isEInkMode` inline. `LegadoTheme` disables `LocalAnimationsEnabled` and switches to `MotionScheme.standard()` in E-Ink mode.

### Activity/Fragment Patterns

**Activity pattern:**
```kotlin
class FooActivity : VMBaseActivity<ActivityFooBinding, FooViewModel>(),
    SomeAdapter.CallBack, SelectActionBar.CallBack {
    override val binding by viewBinding(ActivityFooBinding::inflate)
    override val viewModel by viewModels<FooViewModel>()
    override fun onActivityCreated(savedInstanceState: Bundle?) { ... }
}
```

**Fragment pattern:**
```kotlin
class FooFragment : VMBaseFragment<FooViewModel>(R.layout.fragment_foo) {
    override val viewModel by viewModels<FooViewModel>()
    private val binding by viewBinding(FragmentFooBinding::bind)
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) { ... }
}
```

**Dialog pattern:**
```kotlin
class FooDialog : BaseDialogFragment(R.layout.dialog_foo, true),
    Toolbar.OnMenuItemClickListener {
    private val binding by viewBinding(DialogFooBinding::bind)
    override fun onStart() { super.onStart(); setLayout(0.95f, 0.95f) }
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) { ... }
    interface Callback { fun onFooResult(data: String) }
}
```

**Activity launching** (reified generics in `utils/ContextExtensions.kt` and `utils/FragmentExtensions.kt`):
```kotlin
startActivity<FooActivity> { putExtra("key", value) }
startActivityForBook(book)  // routes to ReadBook/AudioPlay/ReadManga by type
```

### RecyclerView Adapters (`base/adapter/`)

Two custom base adapters, both generic over `<ITEM, VB : ViewBinding>`:

- **`RecyclerAdapter<ITEM, VB>`** — primary adapter. Header/footer support via `SparseArray`. Diff-based updates via `setItems(items, itemCallback)`. Click listeners, item animations, GridLayoutManager support. Abstract methods: `getViewBinding(parent)`, `convert(holder, binding, item, payloads)`, `registerListener(holder, binding)`
- **`DiffRecyclerAdapter<ITEM, VB>`** — simpler alternative using `AsyncListDiffer`. Requires `diffItemCallback`. Has `keepScrollPosition` flag
- **`ItemViewHolder(val binding: ViewBinding)`** — shared ViewHolder class

**RecyclerView helpers** (`ui/widget/recycler/`): `ItemTouchCallback` (drag/swipe), `DragSelectTouchHelper` (drag-to-select), `FastScrollRecyclerView`, `DividerNoLast`, `VerticalDivider`, `LoadMoreView`, `NoChildScrollLinearLayoutManager`, `UpLinearLayoutManager`

### Custom Widgets (`ui/widget/`)

- **TitleBar** — extends `AppBarLayout`, wraps `Toolbar`. XML attrs: `title`, `subtitle`, `contentLayout`, `attachToActivity`, `displayHomeAsUp`, `fitStatusBar`, `themeMode`. Auto-attaches to activity via `setSupportActionBar()`. Sets `primaryColor` background
- **SelectActionBar** — bottom action bar for multi-select. `CallBack` interface: `selectAll()`, `revertSelection()`, `onClickSelectBarMainAction()`
- **SearchView** — extends `SearchView`, customizes hint icon
- **DynamicFrameLayout** — state-switching layout (content/progress/error/empty) using `ViewStub`
- **CoverImageView** — 5:7 aspect ratio, rounded corners via `Path`, draws book name/author on default covers, Glide integration
- **Text widgets** (`widget/text/`): `AccentTextView`, `PrimaryTextView`, `SecondaryTextView`, `BadgeView`, `StrokeTextView`, `BevelLabelView`, `ScrollTextView`, `TextInputLayout`
- **Animations** (`widget/anima/`): `RefreshProgressBar`, `RotateLoading`, `explosion_field/`
- **Checkbox** (`widget/checkbox/`): `SmoothCheckBox` with animated transitions
- **Code** (`widget/code/`): `CodeView` with syntax highlighting

### Dialog Widget Library (`ui/widget/dialog/`)

Reusable dialogs: `TextDialog` (text/markdown/HTML with toolbar), `TextListDialog`, `CodeDialog`, `PhotoDialog`, `UrlOptionDialog`, `WaitDialog` (loading spinner), `VariableDialog`, `NumberPickerDialog`

### Layout Conventions

- **Activity layouts**: vertical `LinearLayout` with `TitleBar` on top, content below (RecyclerView, ViewPager, or Fragment container). `SelectActionBar` at bottom for list screens
- **Fragment layouts**: `TitleBar` with `app:attachToActivity="false"` (critical — prevents fragment toolbar from stealing the activity's action bar)
- **Dialog layouts**: `TitleBar` or `Toolbar` at top, inputs below. Size set via `setLayout(widthRatio, heightRatio)` in `onStart()`
- **Item layouts**: `ConstraintLayout` root, `?android:attr/selectableItemBackground` for ripples, `includeFontPadding="false"`, `singleLine="true"` with `ellipsize="end"`
- **Main activity**: `ViewPager` + `ThemeBottomNavigationVIew` (no TitleBar). Bottom nav has 2-4 tabs based on `AppConfig.showDiscovery`/`showRSS`

### Menu/Toolbar Conventions

- `BaseActivity.onCreateOptionsMenu()` is `final` — delegates to `onCompatCreateOptionsMenu()`, then applies `menu.applyTint(this, toolBarTheme)`
- `BaseActivity.onOptionsItemSelected()` is `final` — handles `android.R.id.home` with `supportFinishAfterTransition()`, delegates to `onCompatOptionsItemSelected()`
- Fragments use `setSupportToolbar(toolbar)` or `MenuProvider` interface
- Menu tinting in `utils/MenuExtensions.kt`: `Menu.applyTint()`, `Menu.applyOpenTint()`, `Menu.iconItemOnLongClick()`

### E-Ink Mode Support

Extensive E-Ink handling throughout: removes animations and dim, uses border drawables (`bg_eink_border_*`) instead of colored backgrounds, transparent dialog backgrounds. Check `AppConfig.isEInkMode`

### UI Utility Extensions (`utils/`)

- **ColorUtils.kt**: `isColorLight()`, `darkenColor()`, `lightenColor()`, `blendColors()`, `adjustAlpha()`, `withAlpha()`, `invertColor()`
- **ViewExtensions.kt**: `gone()`, `visible()`, `invisible()`, `hideSoftInput()`, `showSoftInput()`, `applyTint()`, `setEdgeEffectColor()`, `screenshot()`, `applyStatusBarPadding()`, `applyNavigationBarPadding()`
- **ActivityExtensions.kt**: `showDialogFragment<T>()`, `setStatusBarColorAuto()`, `setNavigationBarColorAuto()`, `fullScreen()`, `keepScreenOn()`, `toggleSystemBar()`, `showHelp(fileName)`
- **ContextExtensions.kt**: `startActivity<T>()`, `startActivityForBook()`, preference helpers (`getPrefBoolean/Int/Long/String`, `putPref*`), `getCompatColor()`, `share()`, `sendToClip()`, `openUrl()`, `restart()`
- **FragmentExtensions.kt**: Fragment variants of the above
- **DialogExtensions.kt**: `AlertDialog.applyTint()`, `DialogFragment.setLayout()` — sizes dialogs as fractions of screen
- **MenuExtensions.kt**: `Menu.applyTint()`, `Menu.applyOpenTint()`, `Menu.iconItemOnLongClick()`, `Menu.transaction()`
- **IntentExtensions.kt**: `Intent.putJson()`, `Intent.getJsonObject<T>()` via GSON

### Settings/Preferences UI

- Host: `ConfigActivity` routes to fragments based on `"configTag"` extra (`THEME_CONFIG`, `OTHER_CONFIG`, `BACKUP_CONFIG`, `COVER_CONFIG`, `WELCOME_CONFIG`)
- Base: `PreferenceFragment` (extends `PreferenceFragmentCompat`) in `lib/prefs/fragment/`
- Custom preference widgets in `lib/prefs/`: `ColorPreference`, `SwitchPreference`, `IconListPreference`, `PreferenceCategory`
- Preference XML files in `res/xml/`: `pref_config_theme.xml`, `pref_config_other.xml`, `pref_config_backup.xml`, `pref_config_read.xml`, `pref_config_aloud.xml`

### Key Dimensions/Styles

- Font sizes: `font_size_normal` (14sp), `font_size_middle` (16sp), `font_size_large` (18sp)
- Icon size: `desc_icon_size` (18sp)
- Toolbar elevation: 4dp
- Preference card corner radius: 16dp, horizontal margin: 12dp
- Dialog corner radius: 16dp
- Base theme: `Theme.MaterialComponents.DayNight.NoActionBar`
- Theme variants: `AppTheme.Light`, `AppTheme.Dark`, `AppTheme.Transparent`

## Build Configuration

- **Build files**: Groovy DSL (not Kotlin DSL)
- **Version catalog**: `gradle/libs.versions.toml`
- **Gradle**: 9.3.1, **AGP**: 9.1.0, **Kotlin**: 2.3.0, **KSP**: 2.3.4
- **compileSdk/targetSdk**: 36, **minSdk**: 28
- **JVM toolchain**: Java 21
- **Product flavors**: dimension `mode`, flavor `app` (CI also builds `google` for Play Store)
- **Build types**: `release` (minified + shrunk, suffix `.release`) and `debug` (suffix `.debug`)
- **Versioning**: `3.{yy}.{MMdd}{HH}`, versionCode = `10000 + git commit count`
- **Core library desugaring** enabled (for java.nio on older APIs)

## Pinned Dependencies (do not update without careful review)

- **Jsoup** 1.16.2 — pinned due to breaking change in newer versions (see libs.versions.toml comment)
- **Hutool** 5.8.22 — pinned
- **protobuf-javalite** 4.26.1 — pinned

## Testing

Tests are minimal. Frameworks: JUnit 4, Espresso, AndroidX Test, Room Testing.

```bash
# Unit tests
./gradlew testAppDebugUnitTest

# Instrumented tests (requires device/emulator)
./gradlew connectedAppDebugAndroidTest
```

- Unit tests: `app/src/test/java/io/legado/app/`
- Instrumented tests: `app/src/androidTest/java/io/legado/app/`
- Room schemas exported to `app/schemas/` for migration testing

## Code Style

- No ktlint/detekt configured; Kotlin `official` code style (`gradle.properties`)
- `checkDependencies = true` for lint in each module
- No pre-commit hooks

## CI

GitHub Actions workflows in `.github/workflows/`:
- `test.yml` — builds on push to master and PRs
- `release.yml` — manual signed release build + Google Play upload
- `web.yml` — builds web module on changes to `modules/web/`
- `cronet.yml` — weekly Cronet update check
