/*
 * Copyright (c) 2026 Hirameki contributors
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.reviewer.compose

import android.view.HapticFeedbackConstants
import android.webkit.WebView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import anki.scheduler.CardAnswer
import com.ichi2.anki.R
import com.ichi2.anki.reviewer.AnswerFeedback
import com.ichi2.anki.reviewer.ReviewerJavascriptCommand
import com.ichi2.anki.ui.compose.theme.LocalAnkiColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val WellLabelInset = 18.dp
private val RestElevation = 2.dp
private val DragElevation = 14.dp
private val FrontBorder = 1.dp
private val BackBorder = 1.5.dp
private const val BACK_TINT = 0.14f
private const val BACK_BORDER_ALPHA = 0.55f
private const val CAMERA_DISTANCE_DP = 14f
private const val HALF_TURN = 90f
private const val FULL_TURN = 180f
private const val DEGREES_PER_RADIAN = 180f / Math.PI.toFloat()
private const val WELL_FRACTION = 0.62f
private const val FADE_STARTS_AT = 0.86f
private const val TINT_ALPHA = 0.16f
private const val LANDING_RING_ALPHA = 0.55f
private const val LANDING_FLARE_ALPHA = 0.3f
private const val LANDING_LABEL_POP = 0.22f
private const val LABEL_IDLE_ALPHA = 0.55f
private const val GLOW_SETTLED = 0.002f

/**
 * Strokes of the outer and inner ring a well draws as the card nears it. In dp: they were 3 and 2 raw pixels, which
 * thinned to a hairline as screen density rose; these match that weight on a common 420 dpi phone.
 */
private val AimRingOuterStroke = 1.dp
private val AimRingInnerStroke = 0.75.dp

/** The thumb-lock solve stops once a step moves the centre less than this many px. */
private const val THUMB_LOCK_TOLERANCE_PX = 0.05f

/** Most steps the thumb-lock solve may take; it normally settles well before this, see [DraggableFlashcard]. */
private const val THUMB_LOCK_MAX_ITERATIONS = 64

/** Slowest a graded flight may move, in px per ms, so a release from rest still arrives. */
private const val MIN_FLIGHT_SPEED = 0.15f

/** Floor under the card's neck, so a fully necked card keeps a shape that can still be drawn and inverted. */
private const val MIN_NECK_SCALE = 0.05f

/** What the card is doing right now. */
private enum class CardPhase {
    Idle,
    Drag,
    Flight,

    /** Graded and flown into its corner; hidden until the view model delivers the next card. */
    Away,

    /** The next card is being swapped into the faces' pages; hidden and disarmed until the face that is up has painted it. */
    Arriving,
}

/** Out of sight and not answerable: graded and gone, or the next card not painted yet. */
private val CardPhase.isHidden: Boolean get() = this == CardPhase.Away || this == CardPhase.Arriving

/**
 * A corner the card can be dropped into, and the rating that records.
 *
 * Left is negative and right is positive; the two most-used ratings sit along the bottom, where a
 * thumb already rests. Declaration order matches the interval labels the scheduler hands back.
 */
private enum class GradeCorner(
    val rating: CardAnswer.Rating,
    val alignment: Alignment,
    val labelRes: Int,
    val towardsStart: Boolean,
    val towardsTop: Boolean,
) {
    AGAIN(CardAnswer.Rating.AGAIN, Alignment.BottomStart, R.string.ease_button_again, true, false),
    HARD(CardAnswer.Rating.HARD, Alignment.TopStart, R.string.ease_button_hard, true, true),
    GOOD(CardAnswer.Rating.GOOD, Alignment.BottomEnd, R.string.ease_button_good, false, false),
    EASY(CardAnswer.Rating.EASY, Alignment.TopEnd, R.string.ease_button_easy, false, true),
}

/** Whether this corner sits on the left of the screen, which flips with the layout direction. */
private fun GradeCorner.isLeft(isRtl: Boolean) = towardsStart != isRtl

private fun quadrantIndex(
    towardsStart: Boolean,
    towardsTop: Boolean,
) = (if (towardsStart) 1 else 0) + (if (towardsTop) 2 else 0)

/**
 * The corners by quadrant, indexed by [quadrantIndex]. Built once from the corners' own directions: the thumb-lock
 * solve looks a quadrant up as often as 64 times a frame, and searching the entries allocated an iterator each time.
 */
private val CORNERS_BY_QUADRANT: Array<GradeCorner> =
    Array(4) { index -> GradeCorner.entries.first { quadrantIndex(it.towardsStart, it.towardsTop) == index } }

/**
 * Tracks a face's current webview. A face keeps one page and swaps cards inside it; it only gets a new one when
 * the renderer behind the old one died, and the identity check guards that release racing the re-creation.
 *
 * A new page has drawn nothing, so [forgetPainted] drops the card the face last reported painted: kept, a face
 * rebuilt after its renderer died counted as painted and a turn or an entrance brought round its blank page.
 */
private fun keepPage(
    pages: Array<WebView?>,
    index: Int,
    page: WebView,
    alive: Boolean,
    forgetPainted: () -> Unit,
) {
    if (alive) {
        pages[index] = page
        forgetPainted()
    } else if (pages[index] === page) {
        pages[index] = null
    }
}

/**
 * A 2x2 linear map in screen orientation (y down), enough to follow the card's scale, lean and neck
 * without the full layer matrix. Rotations are clockwise for positive degrees, as graphicsLayer's are.
 */
private data class Linear(
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
) {
    operator fun times(other: Linear) =
        Linear(
            a * other.a + b * other.c,
            a * other.b + b * other.d,
            c * other.a + d * other.c,
            c * other.b + d * other.d,
        )

    fun apply(v: Offset) = Offset(a * v.x + b * v.y, c * v.x + d * v.y)

    fun inverse(): Linear {
        val det = a * d - b * c
        if (abs(det) < 1e-6f) return IDENTITY
        return Linear(d / det, -b / det, -c / det, a / det)
    }

    companion object {
        val IDENTITY = Linear(1f, 0f, 0f, 1f)

        fun rotation(degrees: Float): Linear {
            val radians = degrees / DEGREES_PER_RADIAN
            return Linear(cos(radians), -sin(radians), sin(radians), cos(radians))
        }

        fun scale(
            x: Float,
            y: Float,
        ) = Linear(x, 0f, 0f, y)
    }
}

/**
 * The reviewer's card as a card: tap to turn it over, then drag it into a corner to answer.
 *
 * It is shaped and sized like a bank card rather than filling the screen, so the four corner wells stay
 * visible around it. Both sides are real, separately rendered faces, so the flip only reveals a side
 * that is already painted, and nothing reflows when the answer appears.
 *
 * Geometry. The card always turns and shrinks about its own centre, and [cardOffset] is where that
 * centre is drawn. Corners, the registering point, the glow and the flight all read it, so a card
 * dropped in a corner lands its centre on the corner whichever part of it was held. Keeping the held
 * part under the thumb is then a question of where to put the centre: as the card shrinks the held
 * point would slide toward the middle, so each drag step solves for the centre that puts it back under
 * the finger ([THUMB_LOCK_TOLERANCE_PX]). An earlier version pivoted the card on the thumb instead; that
 * kept the thumb honest but threw every corner calculation off by where the card was held, so cards
 * landed up to half a card away from their corner.
 *
 * Nothing is recorded until the finger lifts. While dragging the card only shrinks as far as
 * [CardMotionSpec.dragFloorScale]; the rest of the journey happens on release, after which the card
 * stays hidden until the next card actually arrives and its page has drawn it, so the old one can never
 * be graded twice and the new one never grows in before its question is on it.
 *
 * Motion lives entirely in [spec]; this function only applies it.
 */
@Composable
fun DraggableFlashcard(
    cardKey: Long,
    baseUrl: String,
    questionHtml: String,
    answerHtml: String,
    bodyClass: String,
    isMediaAutoplayEnabled: Boolean,
    javascriptCommand: ReviewerJavascriptCommand?,
    onJavascriptCommandConsumed: (Int) -> Unit,
    onLinkClick: (String) -> Unit,
    isAnswerShown: Boolean,
    tapToFlip: Boolean,
    dragToGrade: Boolean,
    nextTimes: List<String>,
    replayFinished: Int,
    onShowAnswer: () -> Unit,
    onUnanswer: () -> Unit,
    onRateCard: (CardAnswer.Rating) -> Unit,
    /**
     * The view model's word that a rating was recorded. A corner only ripples once the grade dropped into it is
     * recorded: the view model refuses some ratings, and a ripple for those confirmed a grade never written.
     */
    recordedRating: AnswerFeedback?,
    modifier: Modifier = Modifier,
    spec: CardMotionSpec = CardMotionSpec.Default,
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ratingColors = LocalAnkiColors.current.ratings

    // the drag handler is installed once and outlives recompositions, so anything it needs from the
    // parameters is read through these; otherwise it would keep acting on the first card it saw
    val liveSpec by rememberUpdatedState(spec)
    val liveCardKey by rememberUpdatedState(cardKey)
    val liveAnswerShown by rememberUpdatedState(isAnswerShown)
    val liveDragToGrade by rememberUpdatedState(dragToGrade)
    val liveIsRtl by rememberUpdatedState(isRtl)
    val liveRate by rememberUpdatedState(onRateCard)
    val liveShowAnswer by rememberUpdatedState(onShowAnswer)

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var cardSizePx by remember { mutableStateOf(IntSize.Zero) }

    /** The held point of the card, from its centre, in unscaled card pixels. */
    var grabFromCentre by remember { mutableStateOf(Offset.Zero) }

    /** The thumb's travel, based so that picking the card up does not move it. */
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }

    /** Where the card's centre is drawn, from where it rests. Everything corner-related reads this. */
    var cardOffset by remember { mutableStateOf(Offset.Zero) }
    var activeCorner by remember { mutableStateOf<GradeCorner?>(null) }
    // composed with a card already loaded (switching in from the classic view, or a recreated activity), the faces'
    // new pages have yet to load it: hidden until they have, as for any new card
    var phase by remember { mutableStateOf(if (baseUrl.isNotEmpty()) CardPhase.Arriving else CardPhase.Idle) }
    var lastCardKey by remember { mutableLongStateOf(cardKey) }

    // a flight is either the trip into a corner, which grades, or the drift back to the middle
    val flight = remember { Animatable(0f) }
    var isGrading by remember { mutableStateOf(false) }
    var flightFrom by remember { mutableStateOf(Offset.Zero) }
    var flightVia by remember { mutableStateOf(Offset.Zero) }
    var flightTo by remember { mutableStateOf(Offset.Zero) }
    var flightFromScale by remember { mutableFloatStateOf(1f) }
    var flightCorner by remember { mutableStateOf<GradeCorner?>(null) }
    var throwTarget by remember { mutableFloatStateOf(0f) }

    // the corner a graded card sank into, and how far its ripple has spread: 1 is settled
    var landedCorner by remember { mutableStateOf<GradeCorner?>(null) }
    val landing = remember { Animatable(1f) }

    // the corner a card was dropped into, until the view model records that grade and the corner ripples
    var awaitingRecord by remember { mutableStateOf<GradeCorner?>(null) }

    fun landingOf(corner: GradeCorner): Float = if (corner == landedCorner) landing.value else 1f

    // composed while the answer is already up (coming back to the reviewer): start turned, not turning
    val flip = remember { Animatable(if (isAnswerShown) FULL_TURN else 0f) }
    val entrance = remember { Animatable(1f) }
    // changes only as the card passes edge-on, so the faces recompose twice per flip, not every frame
    val showsBackFace by remember { derivedStateOf { flip.value > HALF_TURN } }

    // the two faces' pages, so a vertical swipe can ask the visible one whether it still scrolls
    val pages = remember { arrayOfNulls<WebView>(2) }

    // the card each face's page has actually drawn, as its page reports it
    var paintedFront by remember { mutableLongStateOf(Long.MIN_VALUE) }
    var paintedBack by remember { mutableLongStateOf(Long.MIN_VALUE) }

    // whether the long press of the touch under way selected text, as the page reports it; null until it has. only the
    // drag handler reads it, which clears it at each touch-down
    var longPressSelectedText by remember { mutableStateOf<Boolean?>(null) }

    fun visiblePage(): WebView? = pages[if (showsBackFace) 1 else 0]

    /**
     * Whether a drag may grade: grading is on, the answer is up, and it has turned into view. Armed on the answer
     * alone, the card could be graded while it still showed its question, for as long as the answer took to paint
     * before the turn began.
     */
    fun armed(): Boolean = liveDragToGrade && liveAnswerShown && showsBackFace

    /** Where a corner sits, from the card's resting centre. Mirrored in RTL to stay under its label. */
    fun anchorFor(corner: GradeCorner): Offset {
        if (containerSize == IntSize.Zero) return Offset.Zero
        val inset = min(containerSize.width, containerSize.height) * liveSpec.cornerInset
        return Offset(
            x = if (corner.isLeft(liveIsRtl)) inset - containerSize.width / 2f else containerSize.width / 2f - inset,
            y = if (corner.towardsTop) inset - containerSize.height / 2f else containerSize.height / 2f - inset,
        )
    }

    /** How far along the line to [corner] a centre at [offset] is: 0 at rest, 1 at the corner, never more. */
    fun journeyOf(
        corner: GradeCorner,
        offset: Offset,
    ): Float {
        val anchor = anchorFor(corner)
        val lengthSquared = anchor.x * anchor.x + anchor.y * anchor.y
        if (lengthSquared <= 0f) return 0f
        // projected onto the line, so shoving the card past the corner stays at 1 instead of unwinding
        return ((offset.x * anchor.x + offset.y * anchor.y) / lengthSquared).coerceIn(0f, 1f)
    }

    /** The corner whose quarter of the screen [offset] points into, with no dead zone. */
    fun quadrantOf(offset: Offset): GradeCorner = CORNERS_BY_QUADRANT[quadrantIndex((offset.x < 0f) != liveIsRtl, offset.y < 0f)]

    /**
     * The corner a release would grade into: none until the card has left the middle, and none while it is still
     * near either axis. Straight down lies between Again and Good, and straight sideways between two corners as
     * well: a pixel of drift picked one of two opposite grades for a flick aimed at neither.
     */
    fun cornerUnder(offset: Offset): GradeCorner? {
        if (cardSizePx == IntSize.Zero) return null
        val dead = min(cardSizePx.width, cardSizePx.height) * liveSpec.deadZone
        if (abs(offset.x) < dead || abs(offset.y) < dead) return null
        return quadrantOf(offset)
    }

    fun currentOffset(): Offset =
        if (phase == CardPhase.Flight) {
            val t = flight.value
            val mt = 1f - t
            Offset(
                x = mt * mt * flightFrom.x + 2f * mt * t * flightVia.x + t * t * flightTo.x,
                y = mt * mt * flightFrom.y + 2f * mt * t * flightVia.y + t * t * flightTo.y,
            )
        } else {
            cardOffset
        }

    fun currentJourney(): Float {
        val offset = currentOffset()
        val corner = if (phase == CardPhase.Flight && isGrading) flightCorner ?: quadrantOf(offset) else quadrantOf(offset)
        return journeyOf(corner, offset)
    }

    /**
     * How far a release has turned from necking into tumbling: nothing while held, growing across the
     * whole flight. A shorter ramp finished within a frame or two of a fast flick and still read as a jump.
     */
    fun currentThrowiness(): Float = if (phase == CardPhase.Flight && isGrading) throwTarget * flight.value else 0f

    fun currentScale(): Float {
        val base =
            if (phase == CardPhase.Flight && isGrading) {
                flightFromScale + (liveSpec.minScale - flightFromScale) * flight.value
            } else {
                liveSpec.dragScaleAt(currentJourney())
            }
        return base * entrance.value
    }

    /**
     * The card's drawn scale, lean and neck about its centre, matching layers 1-4 below minus the
     * tumble, which only runs in a graded flight where nothing needs this.
     */
    fun linearAt(
        offset: Offset,
        journey: Float,
        scale: Float,
    ): Linear {
        val width = cardSizePx.width.coerceAtLeast(1)
        val tilt = (offset.x / width) * liveSpec.tiltDegrees
        val travel = atan2(offset.y, offset.x) * DEGREES_PER_RADIAN
        val neckX = 1f + liveSpec.funnelStretch * journey
        val neckY = (1f - liveSpec.funnelNarrow * journey).coerceAtLeast(MIN_NECK_SCALE)
        return Linear.rotation(tilt) * Linear.scale(scale, scale) *
            Linear.rotation(travel) * Linear.scale(neckX, neckY) * Linear.rotation(-travel)
    }

    /**
     * The centre that puts the held point of the card under a thumb at [thumb].
     *
     * The held point is drawn at centre + shape(grabFromCentre), so the centre is thumb +
     * (grabFromCentre - shape(grabFromCentre)). The shape depends on the centre through the journey, so
     * this iterates from last frame's answer until it settles. Each step keeps 75-87% of the remaining
     * error near the corners, where the card shrinks fastest, so a fixed four steps left the held point
     * 3-20 px off a fast-moving thumb; iterating to a tolerance costs a few dozen 2x2 products at worst.
     */
    fun centreUnderThumb(
        thumb: Offset,
        seed: Offset,
    ): Offset {
        var centre = seed
        for (step in 0 until THUMB_LOCK_MAX_ITERATIONS) {
            val journey = journeyOf(quadrantOf(centre), centre)
            val shape = linearAt(centre, journey, liveSpec.dragScaleAt(journey) * entrance.value)
            val next = thumb + grabFromCentre - shape.apply(grabFromCentre)
            val moved = (next - centre).getDistance()
            centre = next
            if (moved < THUMB_LOCK_TOLERANCE_PX) break
        }
        return centre
    }

    fun resetToRest() {
        if (phase == CardPhase.Flight) scope.launch { flight.stop() }
        phase = CardPhase.Idle
        isGrading = false
        flightCorner = null
        throwTarget = 0f
        cardOffset = Offset.Zero
        thumbOffset = Offset.Zero
        grabFromCentre = Offset.Zero
        activeCorner = null
    }

    // on the remembered scope, not inside an effect: a flip restarting the effect must not cancel the
    // entrance and leave the card stuck part-size
    fun playEntrance() {
        scope.launch {
            entrance.snapTo(0f)
            entrance.animateTo(1f, tween(liveSpec.enterMillis, easing = FastOutSlowInEasing))
        }
    }

    fun startFlight(
        to: Offset,
        millis: Int,
        onArrive: () -> Unit,
    ) {
        val from = currentOffset()
        flightFrom = from
        flightTo = to
        flightFromScale = liveSpec.dragScaleAt(currentJourney())
        // bow the path sideways, so the card pours in rather than sliding along a ruler
        val mid = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f)
        val run = to - from
        flightVia = Offset(mid.x - run.y * liveSpec.pathCurve, mid.y + run.x * liveSpec.pathCurve)
        phase = CardPhase.Flight
        scope.launch {
            flight.snapTo(0f)
            flight.animateTo(1f, tween(millis, easing = FastOutSlowInEasing))
            onArrive()
        }
    }

    fun driftHome() {
        isGrading = false
        flightCorner = null
        throwTarget = 0f
        startFlight(Offset.Zero, liveSpec.homeMillis) {
            // a finger may have caught the card on its way home; only an untouched drift ends at rest
            if (phase == CardPhase.Flight && !isGrading) resetToRest()
        }
    }

    /**
     * Sends home a card the gesture was holding. A new card resets the card under a finger that is still down, and
     * that card is not held: a flight started from the stale drag made an arriving card visible before its page had
     * drawn it, and skipped its entrance.
     */
    fun letGo() {
        if (phase == CardPhase.Drag) driftHome()
    }

    fun dropInto(
        corner: GradeCorner,
        towardsCornerPxPerMs: Float,
        throwiness: Float,
    ) {
        val anchor = anchorFor(corner)
        val remaining = (anchor - cardOffset).getDistance()
        // the flight carries on at the speed the hand was already moving toward the corner
        val millis =
            (remaining / towardsCornerPxPerMs.coerceAtLeast(MIN_FLIGHT_SPEED))
                .toInt()
                .coerceIn(liveSpec.minDropMillis, liveSpec.maxDropMillis)
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        isGrading = true
        flightCorner = corner
        throwTarget = throwiness
        val thrownKey = liveCardKey
        startFlight(anchor, millis) {
            // caught mid-flight, or the card was replaced under it: this throw grades nothing
            if (phase != CardPhase.Flight || !isGrading || liveCardKey != thrownKey) return@startFlight
            // hidden and disarmed until the view model delivers the next card. arriving in the corner is
            // not the next card being ready: shown again now, the old card could be graded a second time
            phase = CardPhase.Away
            // the corner answers for the grade, in place of a separate notice of which rating it was, once the grade
            // is recorded; see recordedRating
            awaitingRecord = corner
            liveRate(corner.rating)
        }
    }

    /**
     * Takes hold of the card if [touch] (container pixels) is on it. The held point is found by undoing
     * the card's drawn scale and lean, and the thumb is based so the card stays exactly where it is.
     */
    fun pickUp(
        touch: Offset,
        cardAtDown: Long,
    ): Boolean {
        // a graded card is committed, and an absent or not yet painted one cannot be held
        if (phase.isHidden || (phase == CardPhase.Flight && isGrading)) return false
        // re-checked here, not only at touch-down: a finger that lands during a throw and starts moving
        // after the next card has loaded would otherwise pick that card up, question side and all
        if (!armed() || liveCardKey != cardAtDown) return false
        if (cardSizePx == IntSize.Zero || containerSize == IntSize.Zero) return false
        val shown = currentOffset()
        val journey = journeyOf(quadrantOf(shown), shown)
        val shape = linearAt(shown, journey, liveSpec.dragScaleAt(journey) * entrance.value)
        val fromRestCentre = touch - Offset(containerSize.width / 2f, containerSize.height / 2f)
        val onCard = shape.inverse().apply(fromRestCentre - shown)
        val halfWidth = cardSizePx.width / 2f
        val halfHeight = cardSizePx.height / 2f
        val margin = min(cardSizePx.width, cardSizePx.height) * liveSpec.grabMargin
        if (abs(onCard.x) > halfWidth + margin || abs(onCard.y) > halfHeight + margin) return false

        if (phase == CardPhase.Flight) scope.launch { flight.stop() }
        grabFromCentre = Offset(onCard.x.coerceIn(-halfWidth, halfWidth), onCard.y.coerceIn(-halfHeight, halfHeight))
        thumbOffset = shown - (grabFromCentre - shape.apply(grabFromCentre))
        cardOffset = shown
        activeCorner = cornerUnder(shown)
        phase = CardPhase.Drag
        isGrading = false
        throwTarget = 0f
        return true
    }

    fun dragBy(delta: Offset) {
        // a new card may have reset the card under a finger that is still down
        if (phase != CardPhase.Drag) return
        // turned back to the question, or the setting switched off, mid-drag: the card lets go. the
        // handler outlives arming, so nothing else stops a drag that began while it was armed
        if (!armed()) return driftHome()
        thumbOffset += delta
        cardOffset = centreUnderThumb(thumbOffset, cardOffset)
        val corner = cornerUnder(cardOffset)
        if (corner != activeCorner) {
            if (corner != null) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            activeCorner = corner
        }
    }

    fun release(velocity: Velocity) {
        if (phase != CardPhase.Drag) return
        // a release is only a grade while the answer is up and grading by drag is on
        if (!armed()) return driftHome()
        val corner = activeCorner ?: return driftHome()
        val axis = anchorFor(corner)
        val axisLength = axis.getDistance().coerceAtLeast(1f)
        val cardWidth = cardSizePx.width.coerceAtLeast(1)
        // only motion heading for the corner counts as a throw, so flicking the card back toward the
        // middle is a "no" rather than a grade for the corner it was leaving. measured along the line
        // from rest to the corner, the line the journey uses: measured from the card to the corner, a
        // throw that carried the centre past the corner pointed away from it and was read as a retreat
        val towardsCorner = (velocity.x * axis.x + velocity.y * axis.y) / axisLength
        val cardWidthsPerSecond = towardsCorner / cardWidth
        val throwiness = liveSpec.throwiness(cardWidthsPerSecond)
        val retreating = -cardWidthsPerSecond >= liveSpec.flickSpeed
        val journey = journeyOf(corner, cardOffset)
        val thrown = cardWidthsPerSecond >= liveSpec.flickSpeed && journey >= liveSpec.flickReach
        if (!retreating && (journey >= liveSpec.registerAt || thrown)) {
            dropInto(corner, towardsCorner.coerceAtLeast(0f) / 1000f, throwiness)
        } else {
            driftHome()
        }
    }

    // declared ahead of the card effect below: effects start in this order, so a record that reaches the screen in the
    // same frame as the next card still finds the corner it confirms
    LaunchedEffect(recordedRating) {
        val corner = awaitingRecord ?: return@LaunchedEffect
        if (recordedRating?.rating != corner.rating) return@LaunchedEffect
        awaitingRecord = null
        landedCorner = corner
        // on the remembered scope: the record is acknowledged within a frame, and that restart must not cut the ripple
        scope.launch {
            landing.snapTo(0f)
            landing.animateTo(1f, tween(liveSpec.landMillis, easing = LinearEasing))
        }
    }

    // the effects read liveSpec, as the gesture does, rather than the spec they happened to start with
    LaunchedEffect(cardKey, isAnswerShown) {
        val face = if (isAnswerShown) FULL_TURN else 0f
        if (cardKey != lastCardKey) {
            lastCardKey = cardKey
            // a new card, even one whose html matches the last: centred and the right side up, then held out of
            // sight until its page has painted it; the Arriving effect below plays the entrance
            resetToRest()
            phase = CardPhase.Arriving
            flip.snapTo(face)
        } else if (phase.isHidden) {
            // out of sight there is no turn to watch. animated, a turn still running when the Arriving gate lets the
            // card in showed the side it turned from, whose page that gate had not waited for
            flip.snapTo(face)
        } else {
            // only a turn that moves waits: composed with the answer already up, the card is turned already
            if (isAnswerShown && flip.value != face) {
                // the back swaps the new answer in while the question is up; turning before it has painted
                // would bring the last card's answer round
                withTimeoutOrNull(liveSpec.paintWaitMillis.toLong()) { snapshotFlow { paintedBack }.first { it == cardKey } }
                    ?: Timber.w("card view: answer of card %d not painted after %d ms, turning anyway", cardKey, liveSpec.paintWaitMillis)
            }
            flip.animateTo(face, tween(liveSpec.flipMillis, easing = FastOutSlowInEasing))
            // turning the card back over drops any aim at a corner
            if (!isAnswerShown && phase == CardPhase.Idle) resetToRest()
        }
    }

    LaunchedEffect(phase) {
        if (phase != CardPhase.Away) return@LaunchedEffect
        delay(liveSpec.awayTimeoutMillis.toLong())
        if (phase == CardPhase.Away) {
            // the view model refuses a rating it must not record, such as one for a page that was never
            // served; with no next card coming, the graded card has to come back rather than leave the
            // screen empty
            Timber.w("card view: no next card %d ms after grading, showing the card again", liveSpec.awayTimeoutMillis)
            awaitingRecord = null
            resetToRest()
            playEntrance()
        }
    }

    LaunchedEffect(phase, cardKey) {
        if (phase != CardPhase.Arriving) return@LaunchedEffect
        // the faces keep their pages and swap the next card in place. grown in before the front has painted
        // it, the card showed the last card's question (before, a freshly loaded blank face) and then
        // snapped to the new card: the blink of issue #135. the wait is for the face that will be up: a restore
        // after process death reveals the answer in the same card action as the load, and the answer bar can reveal
        // it while the card arrives, and then the entrance shows the back
        withTimeoutOrNull(liveSpec.paintWaitMillis.toLong()) {
            snapshotFlow { if (liveAnswerShown) paintedBack else paintedFront }.first { it == cardKey }
        } ?: Timber.w(
            "card view: %s of card %d not painted after %d ms, showing it anyway",
            if (liveAnswerShown) "answer" else "question",
            cardKey,
            liveSpec.paintWaitMillis,
        )
        // a grade still unrecorded once the next card is in never will be: the view model moved on without recording it
        awaitingRecord = null
        phase = CardPhase.Idle
        playEntrance()
    }

    val labelAgain = stringResource(R.string.ease_button_again)
    val labelHard = stringResource(R.string.ease_button_hard)
    val labelGood = stringResource(R.string.ease_button_good)
    val labelEasy = stringResource(R.string.ease_button_easy)
    val labelShowAnswer = stringResource(R.string.show_answer)
    val labelHideAnswer = stringResource(R.string.hide_answer)
    val labelQuestionSide = stringResource(R.string.card_view_question_side)
    val labelAnswerSide = stringResource(R.string.card_view_answer_side)
    // a screen reader cannot drag or aim a tap, so each step of the card is offered as an action. keyed on the labels
    // alone and calling through the live callbacks: keyed on the callbacks too, a caller's fresh lambda rebuilt the
    // actions, and invalidated the card's semantics, on every recomposition. a grade made through an action is confirmed
    // by its corner once recorded, as a dragged one is: switch access and voice access users rate through these actions
    // too, and they can see the corner, while the notice of a grade is only shown to a screen reader
    val ratingActions =
        remember(labelAgain, labelHard, labelGood, labelEasy) {
            fun rateAction(
                label: String,
                corner: GradeCorner,
            ) = CustomAccessibilityAction(label) {
                awaitingRecord = corner
                liveRate(corner.rating)
                true
            }
            listOf(
                rateAction(labelAgain, GradeCorner.AGAIN),
                rateAction(labelHard, GradeCorner.HARD),
                rateAction(labelGood, GradeCorner.GOOD),
                rateAction(labelEasy, GradeCorner.EASY),
            )
        }
    val revealActions =
        remember(labelShowAnswer) {
            listOf(
                CustomAccessibilityAction(labelShowAnswer) {
                    liveShowAnswer()
                    true
                },
            )
        }
    // ratings need an answer turned into view and a card at rest, the same rule the drag follows; a hidden card
    // offers none
    val accessibilityActions =
        when {
            phase.isHidden -> emptyList()
            !isAnswerShown -> revealActions
            phase == CardPhase.Idle && showsBackFace -> ratingActions
            else -> emptyList()
        }

    val isArmed = dragToGrade && isAnswerShown && showsBackFace && !phase.isHidden

    // on the container, not the card: inside the card's scale and lean layers every movement arrives
    // divided by the card's size. installed once; everything it reads is live state
    val dragModifier =
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // a report is only ever for a long press of this touch, which comes long after its down
                longPressSelectedText = null
                if (!armed() || phase.isHidden) return@awaitEachGesture
                val cardAtDown = liveCardKey
                // fed from the touch-down on: started at pickup, a short flick had too few samples for a
                // speed at all, read as holding still, and the card drifted home instead of flying
                val tracker = VelocityTracker()
                tracker.addPointerInputChange(down)
                var dragging = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } > 1) {
                            // a second finger makes this a pinch-zoom for the page, never a card drag
                            if (dragging) {
                                dragging = false
                                letGo()
                            }
                            while (awaitPointerEvent().changes.any { it.pressed }) {
                                // wait out the pinch without taking any of it
                            }
                            return@awaitEachGesture
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (!change.pressed) {
                            if (dragging) {
                                dragging = false
                                if (change.isConsumed) {
                                    // compose's stand-in for ACTION_CANCEL (screen off, palm rejection, a system
                                    // window taking the touch) arrives already consumed, while a real lift during a
                                    // drag does not: the page stopped receiving the touch at the first consumed
                                    // move. the finger never let go, so this must not grade
                                    letGo()
                                } else {
                                    // the tracker takes no position from a lift, but it zeroes the speed of a card
                                    // held still for a moment before letting go; a lift added as a sample of its
                                    // own read every throw as slower than it was
                                    tracker.addPointerInputChange(change)
                                    release(tracker.calculateVelocity())
                                }
                            }
                            return@awaitEachGesture
                        }
                        tracker.addPointerInputChange(change)
                        if (!dragging) {
                            val moved = change.position - down.position
                            if (moved.getDistance() < viewConfiguration.touchSlop) continue
                            // a finger held still past the long-press timeout made that a long press. on text the page
                            // selects the word under it and the moves then extend the selection, which the card followed
                            // around; on blank card area it selects nothing, and a hold before a drag still picks the
                            // card up. the page reports which once it has handled the press, which can be after the
                            // finger has moved on, so until then the card waits. a webview that cannot message the app
                            // never reports, and the card stays put after any long press
                            if (change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) {
                                when (longPressSelectedText) {
                                    true -> return@awaitEachGesture
                                    null -> continue
                                    false -> Unit
                                }
                            }
                            // decided once, from the whole movement to the slop: judged frame by frame, one
                            // jittery sideways frame mid-scroll handed the swipe to the card. judged on the
                            // swipe's main axis, since a zoomed-in or wide page pans sideways as well
                            val page = visiblePage()
                            val scrollsPage =
                                page != null &&
                                    if (abs(moved.y) > abs(moved.x)) {
                                        page.canScrollVertically(if (moved.y > 0f) -1 else 1)
                                    } else {
                                        page.canScrollHorizontally(if (moved.x > 0f) -1 else 1)
                                    }
                            // a scroll stays a scroll until the finger lifts, its moves left unconsumed for the
                            // page. handing the swipe to the card once the page hit its end let the tail of a
                            // flick grade a card nobody meant to grade; with the page at its end, the next
                            // swipe that way drags the card
                            if (scrollsPage) return@awaitEachGesture
                            // taken where the finger came down, then moved by everything since: tested where the
                            // finger is at the slop, a fast flick from near the card's edge had already left the
                            // card and was dropped. the delta is applied exactly once, from the down position
                            if (!pickUp(down.position, cardAtDown)) return@awaitEachGesture
                            dragging = true
                            change.consume()
                            dragBy(change.position - down.position)
                            continue
                        }
                        change.consume()
                        dragBy(change.position - change.previousPosition)
                    }
                } finally {
                    // the handler detached or restarted mid-drag: never leave the card hanging off-centre
                    if (dragging) letGo()
                }
            }
        }

    // each well chases its target rather than snapping on, so crossing between corners is a cross-fade,
    // and a well lit by a graded card fades out once the card has gone rather than dropping in one frame
    val glows = remember { GradeCorner.entries.map { mutableFloatStateOf(0f) } }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { }
            val aim = (currentJourney() / liveSpec.registerAt).coerceIn(0f, 1f)
            val lit =
                when {
                    !armed() || phase.isHidden -> null
                    phase == CardPhase.Flight && isGrading -> flightCorner
                    else -> activeCorner
                }
            var settled = true
            GradeCorner.entries.forEachIndexed { index, corner ->
                val target = if (corner == lit) aim else 0f
                val current = glows[index].floatValue
                val next = current + (target - current) * liveSpec.wellSmoothing
                glows[index].floatValue = if (abs(target - next) < GLOW_SETTLED) target else next
                if (glows[index].floatValue != target) settled = false
            }
            if (settled && (phase == CardPhase.Idle || phase.isHidden)) {
                // nothing left to chase: sleep until the card is picked up rather than wake every frame
                snapshotFlow { phase }.first { it == CardPhase.Drag || it == CardPhase.Flight }
            }
        }
    }

    val armedAlpha by animateFloatAsState(
        targetValue = if (isArmed) 1f else 0f,
        animationSpec = tween(spec.wellFadeMillis, easing = FastOutSlowInEasing),
        label = "wellArmedAlpha",
    )

    // the shadow lifts with the card instead of jumping, so picking it up has no seam
    val elevation by animateDpAsState(
        targetValue = if (phase == CardPhase.Drag) DragElevation else RestElevation,
        animationSpec = tween(spec.liftMillis, easing = FastOutSlowInEasing),
        label = "cardLift",
    )

    // the card's tint mixes the rating colours by the same smoothed glow as the wells, so it cross-fades
    // at the axes between corners instead of switching colour in one frame
    val drawTint: DrawScope.() -> Unit = {
        var red = 0f
        var green = 0f
        var blue = 0f
        var total = 0f
        GradeCorner.entries.forEach { corner ->
            val weight = glows[corner.ordinal].floatValue
            if (weight > 0f) {
                val colour = ratingColors.forRating(corner.rating).color
                red += colour.red * weight
                green += colour.green * weight
                blue += colour.blue * weight
                total += weight
            }
        }
        if (total > 0f) {
            drawRect(Color(red / total, green / total, blue / total), alpha = min(1f, total) * TINT_ALPHA)
        }
    }

    // a hidden card keeps both faces' pages loaded, and alpha 0 stops no touch: consumed on the initial pass,
    // a tap on a card that was graded away or has not arrived cannot reach its webview to follow a link,
    // replay its audio or scroll a page nobody can see
    val hiddenTouchGate =
        if (phase.isHidden) {
            Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
        } else {
            Modifier
        }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
                .then(dragModifier),
    ) {
        // measured explicitly: a bank card's proportions, as large as fits inside the size fraction on
        // both axes. fillMaxSize() + aspectRatio() cannot do this, because tight constraints win.
        val cardWidth = min(maxWidth.value * spec.cardSizeFraction, maxHeight.value * spec.cardSizeFraction * spec.cardAspectRatio).dp
        val cardHeight = (cardWidth.value / spec.cardAspectRatio).dp

        for (corner in GradeCorner.entries) {
            CornerBloom(
                corner = corner,
                isRtl = isRtl,
                color = ratingColors.forRating(corner.rating).color,
                idleAlpha = spec.wellIdleAlpha,
                glow = { glows[corner.ordinal].floatValue },
                fade = { armedAlpha },
                landing = { landingOf(corner) },
            )
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(cardWidth, cardHeight)
                    .onSizeChanged { cardSizePx = it }
                    // every layer turns and scales about the card's centre; see the geometry note above
                    // 1: where the card is, how big it is, and how it leans
                    .graphicsLayer {
                        val offset = currentOffset()
                        translationX = offset.x
                        translationY = offset.y
                        val scale = currentScale()
                        scaleX = scale
                        scaleY = scale
                        rotationZ = (offset.x / size.width.coerceAtLeast(1f)) * liveSpec.tiltDegrees
                        alpha =
                            when {
                                phase.isHidden -> 0f
                                // only a graded flight dissolves, and only once it is deep in the corner
                                phase == CardPhase.Flight && isGrading ->
                                    1f - ((flight.value - FADE_STARTS_AT) / (1f - FADE_STARTS_AT)).coerceIn(0f, 1f)
                                else -> 1f
                            }
                        cameraDistance = CAMERA_DISTANCE_DP * density
                    }
                    // 2: line the frame up with the direction of travel
                    .graphicsLayer {
                        val offset = currentOffset()
                        rotationZ = atan2(offset.y, offset.x) * DEGREES_PER_RADIAN
                    }
                    // 3: necking while guided, tumbling once thrown
                    .graphicsLayer {
                        val journey = currentJourney()
                        val throwiness = currentThrowiness()
                        rotationX = journey * liveSpec.tumbleDegrees * throwiness
                        val neck = 1f - throwiness
                        scaleX = 1f + liveSpec.funnelStretch * journey * neck
                        scaleY = (1f - liveSpec.funnelNarrow * journey * neck).coerceAtLeast(MIN_NECK_SCALE)
                        cameraDistance = CAMERA_DISTANCE_DP * density
                    }
                    // 4: back into the card's own frame
                    .graphicsLayer {
                        val offset = currentOffset()
                        rotationZ = -atan2(offset.y, offset.x) * DEGREES_PER_RADIAN
                    }.semantics {
                        // with both card gestures on, the answer bar is never composed, so this is the only node
                        // a screen reader can reach on the whole step. unlabelled and without a role it was read
                        // as an empty container, which put the rating actions on it out of reach as well
                        contentDescription = if (isAnswerShown) labelAnswerSide else labelQuestionSide
                        role = Role.Button
                        if (tapToFlip) {
                            // a screen reader's double tap arrives as ACTION_CLICK, which never reaches the raw
                            // webview touch the flip is read from: the same turn is offered here
                            onClick(label = if (isAnswerShown) labelHideAnswer else labelShowAnswer) {
                                if (!phase.isHidden) {
                                    if (liveAnswerShown) onUnanswer() else onShowAnswer()
                                }
                                true
                            }
                        }
                        customActions = accessibilityActions
                    }.then(hiddenTouchGate),
        ) {
            Box(
                modifier =
                    Modifier.graphicsLayer {
                        rotationY = flip.value
                        cameraDistance = CAMERA_DISTANCE_DP * density
                    },
            ) {
                val onFaceTap: () -> Unit = {
                    // tapping turns the card over either way, so a reveal can be taken back
                    if (tapToFlip && !phase.isHidden) {
                        if (liveAnswerShown) onUnanswer() else onShowAnswer()
                    }
                }
                // both faces are full-size webviews stacked in one box, and alpha 0 hides a view without
                // stopping it from receiving touches. whichever face is showing must be on top, or the
                // invisible one swallows every tap, including the replay buttons on the visible side.
                val showsBack = showsBackFace
                val colors = MaterialTheme.colorScheme
                // the answer side is a step lighter and tinted toward the theme, so it is never mistaken
                // for the question side at a glance
                val backColor = lerp(colors.surfaceContainerHighest, colors.primaryContainer, BACK_TINT)

                // two real faces, both painted as soon as the card loads: the flip only reveals a side
                // that is already rendered, instead of swapping the html while the card turns
                CardFace(
                    modifier =
                        Modifier
                            .zIndex(if (showsBack) 0f else 1f)
                            .graphicsLayer { alpha = if (flip.value <= HALF_TURN) 1f else 0f },
                    color = colors.surfaceContainerHigh,
                    border = BorderStroke(FrontBorder, colors.outlineVariant),
                    elevation = { elevation },
                    drawTint = drawTint,
                ) { pageColor ->
                    Flashcard(
                        baseUrl = baseUrl,
                        questionHtml = questionHtml,
                        answerHtml = answerHtml,
                        bodyClass = bodyClass,
                        isMediaAutoplayEnabled = isMediaAutoplayEnabled && !isAnswerShown,
                        javascriptCommand = if (isAnswerShown) null else javascriptCommand,
                        onJavascriptCommandConsumed = onJavascriptCommandConsumed,
                        onTap = { if (!showsBackFace) onFaceTap() },
                        onLinkClick = onLinkClick,
                        isAnswerShown = false,
                        toolbarHeight = 0,
                        useStableLayout = true,
                        sideChangeDurationMs = 0,
                        pageColor = pageColor,
                        replayFinished = replayFinished,
                        // the face being turned away stops its media as the turn starts, not at edge-on,
                        // so the two sides never play over each other during the flip
                        isShowing = !isAnswerShown,
                        onWebView = { page, alive -> keepPage(pages, 0, page, alive) { paintedFront = Long.MIN_VALUE } },
                        paintKey = cardKey,
                        onPainted = { paintedFront = it },
                        onLongPress = { longPressSelectedText = it },
                    )
                }

                CardFace(
                    // pre-turned half a revolution, so it reads the right way round once flipped to
                    modifier =
                        Modifier
                            .zIndex(if (showsBack) 1f else 0f)
                            .graphicsLayer {
                                rotationY = FULL_TURN
                                alpha = if (flip.value > HALF_TURN) 1f else 0f
                            },
                    color = backColor,
                    border = BorderStroke(BackBorder, colors.primary.copy(alpha = BACK_BORDER_ALPHA)),
                    elevation = { elevation },
                    drawTint = drawTint,
                ) { pageColor ->
                    Flashcard(
                        baseUrl = baseUrl,
                        questionHtml = questionHtml,
                        answerHtml = answerHtml,
                        bodyClass = bodyClass,
                        // audio is autoplayed by the view model on reveal; the hidden face must not
                        // start any page media of its own while the question is still up
                        isMediaAutoplayEnabled = isMediaAutoplayEnabled && isAnswerShown,
                        javascriptCommand = if (isAnswerShown) javascriptCommand else null,
                        onJavascriptCommandConsumed = onJavascriptCommandConsumed,
                        onTap = { if (showsBackFace) onFaceTap() },
                        onLinkClick = onLinkClick,
                        isAnswerShown = true,
                        toolbarHeight = 0,
                        useStableLayout = true,
                        sideChangeDurationMs = 0,
                        pageColor = pageColor,
                        replayFinished = replayFinished,
                        isShowing = isAnswerShown,
                        onWebView = { page, alive -> keepPage(pages, 1, page, alive) { paintedBack = Long.MIN_VALUE } },
                        paintKey = cardKey,
                        onPainted = { paintedBack = it },
                        onLongPress = { longPressSelectedText = it },
                    )
                }
            }
        }

        // drawn after the card so a rating and its interval stay readable even as the card passes over
        for (corner in GradeCorner.entries) {
            CornerLabel(
                corner = corner,
                isRtl = isRtl,
                color = ratingColors.forRating(corner.rating).color,
                nextTime = nextTimes.getOrElse(corner.ordinal) { "" },
                glow = { glows[corner.ordinal].floatValue },
                fade = { armedAlpha },
                landing = { landingOf(corner) },
            )
        }
    }
}

/**
 * One side of the card: a Material surface in its own tone and border, with the page drawn in that
 * same tone, and the corner colour washed over it as the card is aimed.
 */
@Composable
private fun CardFace(
    color: Color,
    border: BorderStroke,
    /**
     * The card's shadow. Read inside the draw layer rather than taken as a value: read during composition,
     * every frame of the lift animation re-subcomposed this whole scene, both faces' webviews with it.
     */
    elevation: () -> Dp,
    drawTint: DrawScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (pageColor: Color) -> Unit,
) {
    val cardShape = MaterialTheme.shapes.extraLarge
    Surface(
        modifier =
            modifier
                .fillMaxSize()
                // the layer Surface builds for a shadowElevation of its own, with the height read at draw time
                .graphicsLayer {
                    shadowElevation = elevation().toPx()
                    shape = cardShape
                    clip = false
                },
        shape = cardShape,
        color = color,
        border = border,
    ) {
        Box {
            content(color)
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .drawBehind(drawTint),
            )
        }
    }
}

/**
 * The well that swallows the card: a soft bloom of the rating colour with rings that contract toward
 * the corner as the card approaches, and one ring that spreads back out once the card has sunk in.
 *
 * [glow], [fade] and [landing] are read lazily inside draw and layer scopes, so the drag repaints the
 * well without recomposing it.
 */
@Composable
private fun BoxScope.CornerBloom(
    corner: GradeCorner,
    isRtl: Boolean,
    color: Color,
    idleAlpha: Float,
    glow: () -> Float,
    fade: () -> Float,
    landing: () -> Float,
) {
    Box(
        modifier =
            Modifier
                .align(corner.alignment)
                .fillMaxSize(WELL_FRACTION),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // the bloom radiates from the screen corner, which in RTL is on the other side of its box
            val focus =
                Offset(
                    x = if (corner.isLeft(isRtl)) 0f else size.width,
                    y = if (corner.towardsTop) 0f else size.height,
                )
            // drawn apart from the fade: the wells fade out as soon as the graded card has gone, which is
            // exactly when this plays
            val t = landing()
            if (t < 1f) {
                val left = 1f - t
                val spread = 1f - left * left
                drawRect(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(color.copy(alpha = LANDING_FLARE_ALPHA * left * left), Color.Transparent),
                            center = focus,
                            radius = size.minDimension * (0.35f + 0.65f * spread),
                        ),
                )
                drawCircle(
                    color = color.copy(alpha = LANDING_RING_ALPHA * left),
                    radius = size.minDimension * (0.12f + 0.9f * spread),
                    center = focus,
                    style = Stroke(width = (1.dp + 2.dp * left).toPx()),
                )
            }
            val visibility = fade()
            if (visibility <= 0f) return@Canvas
            val p = glow() * visibility
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors = listOf(color.copy(alpha = (idleAlpha + 0.3f * p) * visibility), Color.Transparent),
                        center = focus,
                        radius = size.minDimension,
                    ),
            )
            if (p > 0f) {
                drawCircle(
                    color = color.copy(alpha = 0.3f * p),
                    radius = size.minDimension * (1.05f - 0.55f * p),
                    center = focus,
                    style = Stroke(width = AimRingOuterStroke.toPx()),
                )
                drawCircle(
                    color = color.copy(alpha = 0.2f * p),
                    radius = size.minDimension * (0.7f - 0.4f * p),
                    center = focus,
                    style = Stroke(width = AimRingInnerStroke.toPx()),
                )
            }
        }
    }
}

/**
 * A corner's rating and the interval it would schedule, sitting above the card so it stays readable
 * while the card travels over it. Brightens and grows out of its corner as the card comes for it, and
 * pops once when the card sinks in, which is how a grade by drag is confirmed.
 */
@Composable
private fun BoxScope.CornerLabel(
    corner: GradeCorner,
    isRtl: Boolean,
    color: Color,
    nextTime: String,
    glow: () -> Float,
    fade: () -> Float,
    landing: () -> Float,
) {
    Column(
        modifier =
            Modifier
                .align(corner.alignment)
                .padding(WellLabelInset)
                .graphicsLayer {
                    val p = glow()
                    val t = landing()
                    // fully lit as the card lands, then fading, even though the wells around it are going
                    alpha = max((LABEL_IDLE_ALPHA + (1f - LABEL_IDLE_ALPHA) * p) * fade(), 1f - t)
                    val grow = (1f + 0.16f * p) * (1f + LANDING_LABEL_POP * sin(PI.toFloat() * t))
                    scaleX = grow
                    scaleY = grow
                    transformOrigin =
                        TransformOrigin(
                            pivotFractionX = if (corner.isLeft(isRtl)) 0f else 1f,
                            pivotFractionY = if (corner.towardsTop) 0f else 1f,
                        )
                },
        horizontalAlignment = if (corner.towardsStart) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            text = stringResource(corner.labelRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
        if (nextTime.isNotEmpty()) {
            Text(
                text = nextTime,
                style = MaterialTheme.typography.labelMedium,
                color = color.copy(alpha = 0.8f),
            )
        }
    }
}
