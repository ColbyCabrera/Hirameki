/*
 * - and + buttons at the two ends of every number field on anki's deck options page.
 *
 * anki's SpinBox has step buttons of its own, but renders them only when the user agent is not a
 * phone or tablet (!/iphone|ipad|ipod|android/i.test(navigator.userAgent)), so in the app a number
 * could only be typed. a step is committed the way a typed value is: the input is given the new
 * value and the input and change events SpinBox listens to, so SpinBox itself still clamps and
 * formats it and the preset is marked modified as after any edit. nothing here focuses the field,
 * so a step never brings up the keyboard.
 *
 * a tap steps once. a press held past REPEAT_DELAY_MS repeats, faster the longer it is held, like
 * upstream's own buttons, and its release does not add the tap's step on top.
 *
 * PageWebViewClient sets window.ankiMaterial3StepperLabels ({ decrement, increment }: anki's own
 * translated names for the actions) before it runs this. anki_material3_theme.css draws the buttons.
 */
(function () {
    "use strict";

    // every sveltekit page is given this script; only the deck options page has these fields
    if (!location.pathname.startsWith("/deck-options")) {
        return;
    }
    // a document installs its observer and listeners once, however often it is styled
    if (window.ankiMaterial3SteppersInstalled) {
        return;
    }
    const labels = window.ankiMaterial3StepperLabels;
    if (labels === undefined) {
        console.error("anki_material3_steppers.js: window.ankiMaterial3StepperLabels is not set, no steppers added");
        return;
    }
    window.ankiMaterial3SteppersInstalled = true;

    /** the number fields anki_material3_theme.css lays out, so the only ones with room for the buttons */
    const FIELDS = ".deck-options-page .row-columns .dynamically-slottable .spin-box";
    const STEPPER_CLASS = "m3-stepper";
    /** how long a press is held before it repeats: android's long-press timeout */
    const REPEAT_DELAY_MS = 400;
    /** the first gap between repeats. it halves after every second of repeating, down to a frame,
        so a hold still crosses a range of thousands */
    const FIRST_REPEAT_INTERVAL_MS = 128;
    const MIN_REPEAT_INTERVAL_MS = 16;
    const SPEED_UP_EVERY_MS = 1000;
    /** material "remove" and "add" icons, 24px viewport */
    const ICON_PATHS = {
        "-1": "M19 13H5v-2h14v2z",
        "1": "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z",
    };

    /** the press on a stepper, from its pointerdown until the click that ends it, or null */
    let press = null;

    /**
     * moves a field's value one step within its bounds and commits it through SpinBox. SpinBox
     * writes step, min and max onto the input in the units the field shows (a percentage field's
     * are its fraction times 100), which is also what the input's value holds.
     *
     * @param {Element} stepper
     * @returns {boolean} whether the value changed
     */
    function step(stepper) {
        const input = stepper.parentElement.querySelector("input");
        const value = parseFloat(input.value);
        if (Number.isNaN(value)) {
            // the field was emptied mid-edit: committing lets SpinBox put its last value back, which
            // the next step then starts from
            commit(input);
            return false;
        }
        let next = value + Number(stepper.dataset.direction) * parseFloat(input.step);
        const min = parseFloat(input.min);
        const max = parseFloat(input.max);
        if (!Number.isNaN(min)) {
            next = Math.max(min, next);
        }
        if (!Number.isNaN(max)) {
            next = Math.min(max, next);
        }
        // sums of decimal steps carry binary noise (2.5 + 0.01 is 2.5100000000000002)
        next = parseFloat(next.toFixed(10));
        if (next === value) {
            return false;
        }
        input.value = String(next);
        commit(input);
        return true;
    }

    /** the events a typed value reaches SpinBox with: input redraws the "%" suffix, change commits */
    function commit(input) {
        input.dispatchEvent(new Event("input", { bubbles: true }));
        input.dispatchEvent(new Event("change", { bubbles: true }));
    }

    /**
     * @param {number} direction -1 or 1
     * @returns {HTMLButtonElement}
     */
    function createStepper(direction) {
        const stepper = document.createElement("button");
        stepper.type = "button";
        stepper.className = STEPPER_CLASS;
        stepper.dataset.direction = String(direction);
        // out of the tab order like upstream's buttons: on a keyboard the field steps with its arrow keys
        stepper.tabIndex = -1;
        stepper.setAttribute("aria-label", direction < 0 ? labels.decrement : labels.increment);
        const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
        svg.setAttribute("viewBox", "0 0 24 24");
        svg.setAttribute("aria-hidden", "true");
        const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
        path.setAttribute("d", ICON_PATHS[direction]);
        svg.appendChild(path);
        stepper.appendChild(svg);
        return stepper;
    }

    /** gives every field without steppers its pair; fields that have them are left as they are */
    function addSteppers() {
        for (const field of document.querySelectorAll(FIELDS)) {
            if (field.querySelector(":scope > ." + STEPPER_CLASS) !== null || field.querySelector("input") === null) {
                continue;
            }
            // first and last, around svelte's own nodes: its blocks insert before their comment anchors,
            // which sit between the input and the end
            field.insertBefore(createStepper(-1), field.firstChild);
            field.appendChild(createStepper(1));
        }
    }

    /** @returns {Element | null} the stepper an event happened on */
    function stepperAt(target) {
        return target instanceof Element ? target.closest("." + STEPPER_CLASS) : null;
    }

    function stopRepeating() {
        if (press !== null) {
            clearTimeout(press.timer);
        }
    }

    document.addEventListener("pointerdown", function (event) {
        const stepper = stepperAt(event.target);
        if (stepper === null || event.button !== 0) {
            return;
        }
        // the press moves no focus: a field being typed in keeps its keyboard, and none opens
        event.preventDefault();
        stopRepeating();
        const current = { stepper: stepper, pointerId: event.pointerId, repeated: false, timer: 0 };
        let interval = FIRST_REPEAT_INTERVAL_MS;
        let sinceSpeedUp = 0;
        function repeat() {
            current.repeated = true;
            // svelte took the field away under the finger, or the value reached its bound
            if (!stepper.isConnected || !step(stepper)) {
                return;
            }
            sinceSpeedUp += interval;
            if (sinceSpeedUp >= SPEED_UP_EVERY_MS) {
                interval = Math.max(MIN_REPEAT_INTERVAL_MS, interval / 2);
                sinceSpeedUp = 0;
            }
            current.timer = setTimeout(repeat, interval);
        }
        current.timer = setTimeout(repeat, REPEAT_DELAY_MS);
        press = current;
    });

    document.addEventListener("pointerup", function (event) {
        if (press !== null && press.pointerId === event.pointerId) {
            clearTimeout(press.timer); // the click that follows still needs to know whether it repeated
        }
    });

    // a scroll that started on a stepper, or the app leaving the screen: no click follows
    document.addEventListener("pointercancel", function (event) {
        if (press !== null && press.pointerId === event.pointerId) {
            stopRepeating();
            press = null;
        }
    });

    document.addEventListener("click", function (event) {
        const ended = press;
        stopRepeating();
        press = null;
        const stepper = stepperAt(event.target);
        if (stepper === null) {
            return;
        }
        // a held press has stepped already. a click from talkback or a keyboard has no pointer type
        // and always steps, even if a stale press was never ended by its own click
        if (ended !== null && ended.repeated && ended.stepper === stepper && event.pointerType !== "") {
            return;
        }
        step(stepper);
    });

    // a long press on android can open a context menu or start a text selection
    document.addEventListener("contextmenu", function (event) {
        if (stepperAt(event.target) !== null) {
            event.preventDefault();
        }
    });

    addSteppers();
    // svelte renders fields later than this runs (the page loads its presets first), and renders them
    // again, for instance for the simulator; the fields it adds get their steppers here
    new MutationObserver(function (records) {
        for (const record of records) {
            for (const node of record.addedNodes) {
                if (node.nodeType === Node.ELEMENT_NODE) {
                    addSteppers();
                    return;
                }
            }
        }
    }).observe(document.body, { childList: true, subtree: true });
})();
