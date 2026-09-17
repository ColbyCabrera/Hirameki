"use strict";

/*
 * one tap on the revert badge puts a changed setting's default back, on anki's deck options page.
 *
 * upstream's badge opens a menu with a single item in it ("restore the default value"), so putting one
 * value back took two taps. the restore itself is a closure inside anki's RevertButton and the default
 * value is written into no attribute, so nothing outside the page can set that value: what this does
 * instead is press that one item for the user, in the same task as the tap on the badge. chrome runs a
 * microtask checkpoint after every listener of a click and svelte flushes on a microtask, so anki's own
 * handler has rendered the menu by the time this script's runs and the press is immediate; where that
 * flush has yet to land, the press waits one microtask for it and the .revert box wears
 * data-m3-restoring until it arrives, which anki_material3_theme.css holds display:none, so nothing lays
 * that menu out, places it or animates it on the way through. a browser paints nothing between the
 * microtasks of one task either way, so the menu is opened, pressed and gone again before the frame the
 * tap landed in is drawn. the restore therefore runs as anki's own action: the row stops showing as
 * modified, the input shows the default, and the page saves what a pick from the menu would have saved.
 *
 * anything that is not that single item is left open and untouched, which is upstream's two-tap flow: the
 * fallback for the day anki renders something else in there.
 *
 * the badge has never carried a name (a button with no text, no title and no aria-label) and is out of
 * the tab order, because the menu item behind it could not be reached with a keyboard either. now that
 * the badge is the whole control, it is given the menu item's own translated words, which
 * PageWebViewClient passes in window.ankiMaterial3RevertLabel, and a place in the tab order, so enter or
 * space on it restores exactly as a tap does. a press from a keyboard also hands its focus on to the
 * setting it restored, because the badge it was standing on is hidden by the restore itself.
 *
 * anki_material3_theme.css draws the wash that marks the field the value went back into.
 */
(function () {
    // every sveltekit page is given this script; only the deck options page has revert badges
    if (!location.pathname.startsWith("/deck-options")) {
        return;
    }
    // a document installs its listener and observer once, however often it is styled
    if (window.ankiMaterial3RevertInstalled) {
        return;
    }
    window.ankiMaterial3RevertInstalled = true;

    /** anki's own words for the restore; the page's strings live inside its bundle, out of reach from here */
    const label = window.ankiMaterial3RevertLabel;
    if (label === undefined) {
        // the one-tap restore is worth more than the name, so this is reported and everything else still runs
        console.error("anki_material3_revert.js: window.ankiMaterial3RevertLabel is not set, the badge keeps no name");
    }

    /** the badge of a changed setting. the help "?" badge is a badge too, but has no .revert around it */
    const BADGE = ".revert .badge";
    /**
     * the one item of the menu that badge opens, while the menu is open. `:not(.m3-exit)` leaves out the
     * ghost anki_material3_motion.js puts back into this same box to animate a closing menu away: svelte
     * keeps its click listener on a node it has unmounted (its own helper only drops listeners again for
     * body, window and document), so pressing that dead item would restore through a destroyed component
     */
    const MENU_ITEM = ".floating > :not(.m3-exit) .dropdown-item";
    /** that ghost: while one is in the box, the click being handled is the one that closed its menu */
    const MENU_GHOST = ".floating > .m3-exit";
    /** on the .revert box for as long as the menu is open for the press, see the stylesheet */
    const RESTORING_ATTRIBUTE = "data-m3-restoring";
    /** on the .config-input of a field whose value has just gone back: its wash */
    const RESTORED_ATTRIBUTE = "data-m3-restored";
    /** marks a badge that has its name and its place in the tab order */
    const ADOPTED_ATTRIBUTE = "data-m3-revert";
    /**
     * what a field's value is edited in, to hand a keyboard press's focus on to. an enum row's control is
     * a div with tabindex 0 and role combobox, not a form element, so this cannot simply ask for inputs
     */
    const FIELD_CONTROL = 'input, select, textarea, [tabindex="0"]';
    /** the wash the stylesheet plays, whose end clears the mark that started it */
    const FLASH_ANIMATION = "m3-restore-flash";
    /** clears a wash whose animation never ran, so that no field is left tinted */
    const FLASH_FALLBACK_MS = 1000;

    /**
     * whether the wash should play at all: the same question anki_material3_theme.css and
     * anki_material3_motion.js ask, so with motion off a restore is simply instant and unadorned
     */
    function animates() {
        return !window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    }

    /**
     * washes over the field a value has just gone back into. the badge vanishing is the page's own sign
     * that the restore happened, and it vanishes from under the finger that is covering it, so this is
     * what is left to see it by.
     *
     * @param {Element | null} field the .config-input the badge belongs to
     */
    function flash(field) {
        // a field already washing keeps the wash it has: restarting an animation takes a forced reflow,
        // and the badge is gone by the time a second restore of the same field could be asked for
        if (field === null || !animates() || field.hasAttribute(RESTORED_ATTRIBUTE)) {
            return;
        }
        field.setAttribute(RESTORED_ATTRIBUTE, "");
        let fallback = 0;

        /** @param {AnimationEvent} [event] absent when the fallback calls this */
        function done(event) {
            // animationend bubbles: another animation inside the field must not end this one early
            if (event !== undefined && (event.target !== field || event.animationName !== FLASH_ANIMATION)) {
                return;
            }
            clearTimeout(fallback);
            field.removeEventListener("animationend", done);
            field.removeAttribute(RESTORED_ATTRIBUTE);
        }

        field.addEventListener("animationend", done);
        fallback = setTimeout(done, FLASH_FALLBACK_MS);
    }

    /**
     * hands focus on to the setting whose value has just gone back.
     *
     * the restore makes the row unmodified, and anki hides an unmodified row's badge
     * (`.hide .badge { display: none }`, its own stylesheet), so the button that was pressed is gone in
     * the flush the press triggers and chrome drops focus to the body. without this, a keyboard or
     * talkback user restoring a second setting would start again from the top of the page every time.
     *
     * @param {Element | null} field the .config-input the badge belongs to
     */
    function focusField(field) {
        if (field === null) {
            return;
        }
        for (const candidate of field.querySelectorAll(FIELD_CONTROL)) {
            // the badge is inside the field too, and this script is what gave it its tabindex 0
            if (candidate.closest(".revert") === null) {
                candidate.focus();
                return;
            }
        }
        // a row whose control this does not know: the field itself takes the focus, so that the next tab
        // still carries on from this setting rather than from the top of the page
        field.tabIndex = -1;
        field.focus();
    }

    /**
     * presses the item anki's restore hangs off, if the menu is the single-item one this knows.
     *
     * @param {Element} host the .revert box the menu belongs to
     * @param {Element | null} field the .config-input to wash once the value is back
     * @param {boolean} keepFocus whether to hand focus on to that field, for a press from a keyboard
     * @returns {number} how many items the menu had, 0 when there is no menu
     */
    function pressMenu(host, field, keepFocus) {
        const items = host.querySelectorAll(MENU_ITEM);
        if (items.length === 1) {
            // svelte listens to the item with addEventListener and never asks whether a click is trusted, so
            // this runs the restore exactly as a tap on it would: the default value is cloned back into the
            // row, and the menu closes itself
            items[0].click();
            flash(field);
            if (keepFocus) {
                // still in the same task as the press, so the badge holding the focus is on screen yet
                focusField(field);
            }
        } else if (items.length > 1) {
            // not the single item this knows how to press, and pressing the wrong entry would change
            // something else: the menu is left open for the user to pick from, which is the two-tap flow
            // this replaced
            console.warn(`anki_material3_revert.js: the revert menu has ${items.length} items, not restoring`);
        }
        return items.length;
    }

    document.addEventListener("click", function (event) {
        const badge = event.target instanceof Element ? event.target.closest(BADGE) : null;
        if (badge === null) {
            return;
        }
        // the badge of an unchanged setting, which upstream hides and whose own handler opens nothing:
        // there is no menu to press. no tap can land on a display:none button, but talkback and a
        // keyboard can still reach one, and this script is what put the badge in the tab order
        if (badge.closest(".hide") !== null) {
            return;
        }
        const host = badge.closest(".revert");
        const field = host.closest(".config-input");
        // enter or space on the focused badge makes a click with no pointer behind it, and that is the only
        // press whose focus has anywhere to go: moving focus for a tap would open android's keyboard over
        // the field the finger just restored, and a finger has no tab order to lose
        const fromKeyboard = event.detail === 0 && document.activeElement === badge;
        // the menu anki's own handler opened for this same click, already rendered: chrome runs a microtask
        // checkpoint after every listener, so svelte's flush lands between anki's handler and this one.
        // a menu an earlier tap left open, because it was not the one item this knows how to press, arrives
        // here the same way
        if (pressMenu(host, field, fromKeyboard) > 0) {
            return;
        }
        // nothing rendered yet: the mark keeps the menu that is on its way off the screen for the few
        // microtasks it takes to arrive, and nothing is painted while it is held
        host.setAttribute(RESTORING_ATTRIBUTE, "");
        queueMicrotask(function () {
            // a ghost in the box means this click closed a menu instead of opening one, which is a plain
            // dismiss: nothing was meant to be pressed and nothing is wrong. with motion off there is no
            // ghost to tell that apart from anki not opening a menu at all, which costs a line in the log
            if (pressMenu(host, field, fromKeyboard) === 0 && host.querySelector(MENU_GHOST) === null) {
                // anki opened no menu for this tap, so there is nothing to press and nothing was changed;
                // the badge is as it was, and the next tap on it opens the menu the way it always did
                console.warn("anki_material3_revert.js: the revert menu did not open, not restoring");
            }
            // after the flush that takes a pressed menu away again: dropping the mark any earlier would
            // put a closing menu on the screen for a frame
            queueMicrotask(function () {
                host.removeAttribute(RESTORING_ATTRIBUTE);
            });
        });
    });

    /** gives every revert badge its name and its place in the tab order; adopted badges are left alone */
    function adoptBadges() {
        for (const badge of document.querySelectorAll(BADGE)) {
            if (badge.hasAttribute(ADOPTED_ATTRIBUTE)) {
                continue;
            }
            badge.setAttribute(ADOPTED_ATTRIBUTE, "");
            // upstream keeps the badge at tabindex -1 because the menu item behind it was out of the tab
            // order too, so a keyboard could reach neither and could restore nothing. one tap needs no
            // menu, so the badge is the control now; an unchanged row's badge is display:none and takes
            // no focus, so the tab order still stops only at settings that have something to restore
            badge.tabIndex = 0;
            if (label !== undefined) {
                badge.setAttribute("aria-label", label);
            }
        }
    }

    adoptBadges();
    // svelte renders the rows later than this runs (the page loads its presets first) and renders them
    // again, for a preset switch or the simulator; the badges it adds are adopted here
    new MutationObserver(function (records) {
        for (const record of records) {
            for (const node of record.addedNodes) {
                if (node.nodeType === Node.ELEMENT_NODE) {
                    adoptBadges();
                    return;
                }
            }
        }
    }).observe(document.body, { childList: true, subtree: true });
})();
