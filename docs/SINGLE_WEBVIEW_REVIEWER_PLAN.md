# Single-WebView Reviewer Plan

**Status**: Phase 0 (spike)
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
   token as ready **and** `postVisualStateCallback` confirms that the WebView has drawn it.
5. **The latest display wins.** Every show request carries a token; a ready message with a
   stale token is ignored.
6. **Scripts run in the page queue.** Commands (user actions, hints, media) go through
   `_queueAction` and carry the display token, so they run after the content they target and
   never on a later card.
7. **Composables hold no logic.** They place the WebView and the overlay and read state.

## Components

New package `com.ichi2.anki.reviewer.card`:

| Component | Responsibility |
|---|---|
| `CardDisplay` | One value in `ReviewerState` describing what to show: token (`cardDisplayIndex` + side), side, HTML, answer HTML, body class, the side's Hirameki style values. |
| `hirameki-card-page.js` + `CardPage.kt` | The page contract. `show(request)` queues: apply style, then `_showQuestion`/`_showAnswer`, then `ready(token)`. `_updateQA` already waits for fonts, images (capped at 200–800 ms) and MathJax. Per-side styles become CSS custom properties, so a side change doesn't replace a `<style>` that re-imports Google Fonts. `CardPage.kt` builds the scripts and parses messages as pure functions. |
| `CardWebViewController` | Owns the WebView (through `SafeWebViewLayout`). `suspend fun show(display)` returns on ready or timeout; `run(command)`; zoom reset (upstream's `resetZoom`/`waitForZoomReset`). The ready message uses androidx.webkit's `addWebMessageListener`. |
| `CardSnapshotter` | Captures the current frame into one reused bitmap. The method is decided in phase 0. |
| `CardTransitionCoordinator` | A pure Kotlin state machine: idle → capturing → swapping → fading. Handles a new card mid-transition, timeouts and the first card (no snapshot). Exposes the overlay bitmap and opacity as `StateFlow`s. Tested with fakes and Turbine. |
| `CardSurface` | Composable: `AndroidView` with the persistent WebView, plus an `Image` overlay whose opacity is set in `graphicsLayer` (draw phase only, no recomposition per frame). |

The controller and coordinator hold a `Context` and bitmaps, so they are scoped to the composition
(`remember` + `DisposableEffect`), not to the ViewModel.

### A new card mid-transition

Rating quickly while a fade is running is the common case.

- **While swapping** (the overlay is fully opaque): replace the target and re-send `show`; the
  old token's ready message is ignored.
- **While fading** (the overlay is at opacity `a`): composite `a × overlay + (1 − a) × capture`
  into the reused bitmap. That is exactly what is on screen. Then start a new transition from it.

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

**Fallback.** If no capture method passes, swap instantly with no fade, but still with no flash.
Two persistent WebViews swapped back and forth is a possible alternative for a crossfade
between cards. It loses invariant 1, and the front and back of a card would be in different pages.

### Phase 1: one page

`CardDisplay`, the page contract and `CardWebViewController`, with an instant swap and no fade.
Delete the `Crossfade`, the IO workarounds, `FlashcardPayload` and `FlashcardCommandRunner`
(#208), and the double crash-callback handling. This fixes #135.

### Phase 2: transition

`CardSnapshotter`, `CardTransitionCoordinator` and `CardSurface`, for sides and cards; a new
card mid-transition; timeouts; a "Card transition: crossfade / none" setting (also the fallback
when capture fails).

### Phase 3: hardening

- After a renderer loss, the last snapshot covers the reload.
- Zoom reset per card; autoplay when the side is actually shown, not on the state change.
- Theme changes applied in place.
- The device matrix below.

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
- **JS globals**: shared across sides and cards, as on desktop and upstream's new screen. A template
  that declares top-level `const`/`let` gets a redeclaration error on the next card. Accepted to
  match desktop.

## Verification

- **Unit**: the coordinator: every transition, a new card at each stage, stale tokens, timeouts.
- **Robolectric**: the controller's wiring (`ShadowWebView`).
- **Device matrix**: the #135 deck, the Modern Anki Template, IO, MathJax, video autoplay, type-in
  answer, the whiteboard, very fast rating, a theme switch mid-review, `chrome://crash`.
- **#135 check**: record the screen while switching cards on the #135 deck, sample every frame, and
  assert that none shows the theme colour where the card's background should be.

## Phase 0 results

_To be filled in from the spike runs._
