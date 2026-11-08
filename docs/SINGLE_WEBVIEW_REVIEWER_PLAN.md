# Single-WebView Reviewer Plan

**Status**: Phases 0–3 implemented; the remaining device checks are listed under phase 3
**Branch**: `feat/single-webview-reviewer` (integration branch; nothing ships until phase 2 lands)
**Related**: #135 (flash between cards), #205 (scripts running twice), #143 (renderer loss)

## Goal

Keep a true crossfade between card sides **and** between cards, while rendering every card in
one persistent WebView, the way desktop Anki and upstream's new study screen do.

## Why the current design fails

`Flashcard.kt` keys a Compose `Crossfade` on `(isAnswerShown, html)`, so every side and every card
gets a new WebView. Page state lives in `AndroidView.update` blocks and `view.tag` payloads and
advances whenever recomposition happens to run. Each bug so far follows from that:

| Problem | Cause |
|---|---|
| #135: a flash of the theme colour on cards with custom backgrounds | A new page paints the shell's background (theme surface) before `_showQuestion` applies the card's CSS. `Crossfade` fades both layers, so the theme colour shows through mid-fade too. Simple templates use the theme colour, so it doesn't show. |
| #205: a command runs twice | Both crossfading WebViews receive it. |
| Image occlusion workarounds (`IO_SETUP_INTERCEPT`, `IO_POST_LOAD_SCRIPT`) | `setup()` runs in a WebView that hasn't settled its layout. |
| Two crash callbacks for one renderer loss | Two WebViews share the renderer process. |
| Cost per side | A page load, Google Fonts `@import`, MathJax and reviewer.js setup on every side and card. |

Moving this logic elsewhere without changing the model would fail the same way.

## Invariants

Every PR must keep these:

1. **One WebView** for the screen's lifetime. It is replaced only after a renderer loss.
2. **One page load.** The shell (`stdHtml`) loads once; cards are swapped with
   `_showQuestion`/`_showAnswer`, never `loadDataWithBaseURL`.
3. **Overlay before swap.** Visible content never changes until a snapshot of the previous frame
   is on screen above the WebView (or there is no previous frame).
4. **Fade only when ready.** The overlay fades only after the page reports the latest display's
   token as ready **and** `postVisualStateCallback` confirms that the WebView has drawn it, or a timeout passes.
5. **The latest display wins.** Every show request carries a token; a ready message with a
   stale token is ignored.
6. **Scripts run in the page queue.** Commands (user actions, hints, media) go through
   `_queueAction`, so they run after the card shown before them. The ViewModel clears pending
   commands on a new card, so none reaches a later one. A command that throws must not reject the
   queue.
7. **Composables hold no logic.** They place the WebView and the overlay and read state.

## Components

New package `com.ichi2.anki.reviewer.card`:

| Component | Responsibility |
|---|---|
| `CardDisplay` | One value derived from `ReviewerState` describing what to show: `cardDisplayIndex`, side, question and answer HTML, body class, base URL. |
| `CardPage.kt`, `CardPageStyle.kt` | The page contract, as pure functions: the shell, and the scripts that show a card, run a command or update the style. `_updateQA` already waits for fonts, images (capped at 200–800 ms) and MathJax. Per-side styles are CSS rules keyed on a side class passed with the body class, which `reviewer.js` applies in the same step as the content. `hirameki-card-page.js` reports when a shown card, including any image occlusion masks, is painted. |
| `CardWebViewController` | Owns the WebView (through `SafeWebViewLayout`). `update` records the latest display, style and command; the page is brought into step with them once it has loaded. `show` puts a card on the page and `awaitShown` waits for its report (androidx.webkit's `addWebMessageListener`). Phase 3 adds zoom reset (upstream's `resetZoom`/`waitForZoomReset`). |
| `CardTransitionCoordinator` | Plain Kotlin behind a `CardTransitionStage` interface: capture, cover, swap, fade. A new target cancels the transition in progress. Handles timeouts and the first card (no snapshot). Exposes the overlay and its opacity as Compose state, read in the draw phase. Tested with a fake stage. |
| `Flashcard` | Composable: the persistent WebView plus an `Image` overlay in a `Box` recorded into a `GraphicsLayer`. Implements the stage: capture, frame waits and the fade. |

The controller and coordinator hold a `Context` and bitmaps, so they are scoped to the composition
(`remember` + `DisposableEffect`), not to the ViewModel.

### A new card mid-transition

Rating quickly while a fade is running is the common case.

- **While swapping** (the overlay is fully opaque): replace the target and re-send `show`; the
  old token's ready message is ignored.
- **While fading** (the overlay is at opacity `a`): capture again. The `GraphicsLayer` records the
  WebView and the overlay together, so the capture is already `a × overlay + (1 − a) × card`,
  exactly what is on screen. Then start a new transition from it.

### Why the fade is exact

The WebView under the overlay is opaque, so fading the snapshot out produces
`a × old + (1 − a) × new`. Today's `Crossfade` gives `0.5 new + 0.25 old + 0.25 theme` at its
midpoint.

## Phases

### Phase 0: spike (throwaway branch `spike/card-rendering`)

Instrumented tests in `androidTest/.../reviewer/spike/`, run on a Pixel 9 and a low-end device
(e.g. the miatoll from #135). Results are recorded below before any production code is written.

| Question | Pass criteria |
|---|---|
| Which capture method gives correct, WebView-only pixels? Candidates: `WebView.draw` to a bitmap (software), Compose `GraphicsLayer.toImageBitmap`, `PixelCopy` of the card area. | Card background, text and a 2D `<canvas>` are captured; the floating Compose overlay is **not** captured; p95 ≤ 16 ms on Pixel 9, ≤ 33 ms on low-end. |
| How long from `show` to ready plus a visual-state callback? | Text card p95 ≤ 100 ms on low-end (excluding `_updateQA`'s image preload). |
| Does image occlusion work on a persistent page without the workarounds? | Masks drawn with a non-zero canvas on 20 consecutive IO sides, including a cached image. |
| What happens to templates with top-level `const`/`let` across cards? | Record the behaviour (expected: a redeclaration error, as on desktop). |
| Does `addWebMessageListener` work with the reviewer's origin? | Ready messages arrive on both devices. |

To run it, connect the devices (all connected devices run in one go), leave them unlocked and
untouched so the test window stays in front, and run:

```sh
git switch spike/card-rendering
./gradlew :AnkiDroid:connectedPlayDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.package=com.ichi2.anki.reviewer.spike
```

The tests report rather than assert; they fail only if the page can't load. Each test's log, with
its `CardSpike` lines, is saved in
`AnkiDroid/build/outputs/androidTest-results/connected/debug/flavors/play/<device>/`.

**Fallback.** If no capture method passes, swap instantly with no fade, but still with no flash.
Two persistent WebViews swapped back and forth is a possible alternative for a crossfade
between cards. It loses invariant 1, and the front and back of a card would be in different pages.

### Phase 1: one page

`CardDisplay`, the page contract and `CardWebViewController`, with an instant swap and no fade.
Delete the `Crossfade`, the IO workarounds, `FlashcardPayload` and `FlashcardCommandRunner`
(#208), and the double crash-callback handling. This fixes #135. Renderer loss moves to
`SafeWebViewLayout`, whose crash-loop state a new card now leaves.

### Phase 2: transition

`CardTransitionCoordinator` and the overlay, for sides and cards; a new card mid-transition;
timeouts; a "Crossfade between cards" setting. A failed capture, or a WebView that cannot report,
swaps instantly.

`FlashcardOnDeviceTest` samples real screen pixels through a fade and requires every sample to be a
blend of the two cards' backgrounds. Its fade tests turn animations back on, because the build runs
connected tests with `animationsDisabled`. Still to do: measure capture time on a release build.

Known limitation: the outgoing card is a still image, so anything moving on it (particles, GIFs,
video) freezes while it fades. The freeze lasts as long as the swap plus the fade. Avoiding it would
take two live WebViews, which is what caused #135.

### Phase 3: hardening

- **Zoom** resets on every new card or side, as upstream does. The page's queue waits for the reset,
  with a time limit so that a reset the renderer can't finish cannot stop later cards.
- **Night mode classes** come from the page's current theme. The ViewModel outlives an Activity
  recreation, so a class stored with the card went stale when the system theme changed mid-review.
- **Renderer loss** during a transition stays covered, because the overlay remains until the card is
  back. With no transition running there is no snapshot to show: one cannot be taken once the
  renderer is gone, and keeping one of every card would cost a capture and a screen-sized bitmap per
  card for a rare event. The theme colour shows for the reload.
- **Autoplay** stays on the state change, as upstream's does. Audio can start a few frames before the
  fade ends. Videos are started by page commands, which already wait for their card.

`FlashcardOnDeviceTest` covers zoom reset, MathJax, IO, very fast rating and renderer loss. Still to
check by hand: the Modern Anki Template, video autoplay and a `<video>` in the snapshot, type-in
answer, the whiteboard and a theme switch mid-review. Capture time on a release build is unmeasured.

## Borrowed from upstream

The study screen isn't ported; it is built on Fragments and Views, and its core idea (one page with
`_showQuestion`) is already what we call. Borrowed pieces:

- `resetZoom` / `ankidroid.waitForZoomReset`.
- The `hasLoaded` guard in `onPageFinished` (`<a href="#">` fires it again without `onPageStarted`).
- `ankidroid-reviewer.js` helpers (`userAction`, `showHint`, `showAllHints`).
- `SafeWebViewLayout` (already ported, #196).

## Template compatibility

- **`sessionStorage`**: works today through `session_storage_polyfill.js` (#131), which survives
  page reloads. With one page it also works natively; keep the polyfill.
- **JS globals**: shared across sides and cards, as on desktop and upstream's new screen. Upstream
  passes card HTML to `_showQuestion`/`_showAnswer` unchanged (`CardViewerViewModel`), and
  `reviewer.js` re-inserts each `<script>` in the global scope. A template that declares top-level
  `const`/`let` therefore gets a redeclaration error when that script runs again in the page. We
  match upstream and do not wrap or rewrite template scripts.

## Verification

- **Unit**: the coordinator: every transition, a new card at each stage, stale tokens, timeouts.
- **Robolectric**: the controller's wiring (`ShadowWebView`).
- **Device matrix**: the #135 deck, the Modern Anki Template, IO, MathJax, video autoplay, type-in
  answer, the whiteboard, very fast rating, a theme switch mid-review, `chrome://crash`.
- **#135 check**: record the screen while switching cards on the #135 deck, sample every frame, and
  assert that none shows the theme colour where the card's background should be.

## Phase 0 results

Measured 2026-11-08 on debug builds, WebView 156.0.8078.47: Pixel 9 Pro XL (SDK 37) and Samsung
SM-P610 (SDK 33, low-end). Debug builds slow the Kotlin and Compose side but not WebView's renderer,
so capture times are upper bounds; re-measure on a release build in phase 2.

| Capture method | Background | 2D canvas | Excludes floating UI | Pixel p50 / p95 | SM-P610 p50 / p95 | Verdict |
|---|---|---|---|---|---|---|
| Software `WebView.draw` | yes | **no** | yes | 9–17 / 27–59 ms | 23 / 35–54 ms | Rejected |
| `PixelCopy` of the card area | yes | yes | **no** | 6–8 / 9–25 ms | 24–25 / 28–38 ms | Rejected |
| Compose `GraphicsLayer` | yes | yes | yes | 12–13 / 18–28 ms | 28–31 / 44–79 ms | **Chosen** |

**Capture decision: `GraphicsLayer`.** It is the only method with correct pixels. Its p95 misses the
16/33 ms targets on these debug runs (n = 15 per card). A capture happens once per tap, before the
fade starts, so a miss delays the fade rather than dropping frames in it. Text was not sampled
separately. `<video>` frames are untested and belong to the device matrix.

| Question | Pixel 9 Pro XL | SM-P610 | Result |
|---|---|---|---|
| Show to ready, p95 | 21 ms | 55 ms | Pass |
| Show to visual-state callback, p95 | 34 ms | 84 ms | Pass (≤ 100 ms) |
| IO masks on 20 sides, no workarounds | 20/20 | 20/20 | Pass; `IO_SETUP_INTERCEPT` and `IO_POST_LOAD_SCRIPT` can go |
| Top-level `const` on the next card | SyntaxError | SyntaxError | As expected; see "Template compatibility" |
| `addWebMessageListener` ready messages | arrive | arrive | Pass |

Requirements these runs add:

- **The WebView needs `MATCH_PARENT` layout params.** With the default `WRAP_CONTENT`, `vh` computes
  to 0. That collapsed the IO container (`max-height: calc(95vh - 40px)`) and made the first spike
  runs fail. `SafeWebViewLayout` already sets them.
- **IO masks paint after the visual-state callback** (p95 25 ms Pixel, 45 ms SM-P610; max 64 ms),
  because `setup()` draws from `requestAnimationFrame`. Ready must wait for them, or the first frames
  of the fade would reveal the answer under the masks.
