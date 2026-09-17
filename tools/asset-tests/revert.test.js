"use strict";
// anki_material3_revert.js against a replica of the RevertButton in anki 25.09's deck options bundle: the
// badge's own click only toggles a menu, the restore itself is the menu item's click handler (it writes the
// default back through the row and closes the menu), and svelte renders and unmounts both on a microtask.
//
// the two orderings the script presses in are both real. chrome performs a microtask checkpoint after every
// listener of a dispatch, so on the phone svelte's flush lands between anki's own badge handler and the
// script's, and the menu is already on screen when the script looks for it; jsdom runs the whole dispatch in
// one js stack and has no checkpoint, so a queued flush cannot arrive before the script's handler. the
// replica takes `sync: true` for the phone's ordering and queues its flush otherwise, and the script keeps a
// press for each: neither branch is dead code.
// run: npm ci && npm test
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { JSDOM } = require("jsdom");

const SCRIPT = fs.readFileSync(
    path.join(__dirname, "..", "..", "AnkiDroid", "src", "main", "assets", "anki_material3_revert.js"),
    "utf8",
);
/** anki's own words for the restore, which the menu item carries */
const LABEL = "Restore the default value.";
const RESTORING = "data-m3-restoring";
const RESTORED = "data-m3-restored";

function page(path = "/deck-options/1", { withLabel = true, reducedMotion = false } = {}) {
    const dom = new JSDOM(`<!doctype html><body></body>`, { url: `http://127.0.0.1:1${path}`, runScripts: "outside-only" });
    const w = dom.window;
    // a manual clock for the wash's fallback
    const clock = { now: 0, timers: new Map(), next: 1 };
    w.setTimeout = (fn, ms) => {
        const id = clock.next++;
        clock.timers.set(id, { at: clock.now + ms, fn });
        return id;
    };
    w.clearTimeout = (id) => clock.timers.delete(id);
    clock.advance = (ms) => {
        const end = clock.now + ms;
        for (;;) {
            let due = null;
            for (const [id, t] of clock.timers) if (t.at <= end && (due === null || t.at < due[1].at)) due = [id, t];
            if (due === null) break;
            clock.timers.delete(due[0]);
            clock.now = due[1].at;
            due[1].fn();
        }
        clock.now = end;
    };
    w.matchMedia = (query) => ({ matches: reducedMotion && query.includes("reduce") });
    if (withLabel) w.ankiMaterial3RevertLabel = LABEL;
    const errors = [];
    const warnings = [];
    w.console.error = (...a) => errors.push(a.join(" "));
    w.console.warn = (...a) => warnings.push(a.join(" "));
    w.document.body.innerHTML =
        `<div class="deck-options-page"><svelte-css-wrapper><div class="container-columns"><div class="row row-columns">` +
        `<div class="container"><div class="dynamically-slottable" id="slot"></div></div></div></div></svelte-css-wrapper></div>`;
    return { w, doc: w.document, clock, errors, warnings, run: () => w.eval(SCRIPT) };
}

/** anki's number field, the control most rows with a revert badge have */
const SPIN_BOX = `<div class="spin-box"><input type="number"></div>`;
/** anki's Select, which an enum row is edited in: a div with a tabindex, not a form element */
const COMBOBOX = `<div tabindex="0" role="combobox"><div class="inner"><div class="label">a</div></div></div>`;

/**
 * what svelte renders for one changed setting: the badge, the (always present) floating box its menu goes
 * in, and the field. the wrapper around the badge carries .hide while the value is the default one.
 *
 * `sync` renders the menu inside anki's own click handler, which is what chrome's microtask checkpoint
 * between listeners amounts to: the phone's ordering. otherwise the flush is queued, as jsdom leaves it.
 * `ghosts` adds what anki_material3_motion.js does to a menu svelte has just unmounted.
 */
function revertRow(
    p,
    { value = 7, defaultValue = 3, items = 1, controlHtml = SPIN_BOX, sync = false, ghosts = false } = {},
    parent = p.doc.getElementById("slot"),
) {
    const config = p.doc.createElement("div");
    config.className = "config-input position-relative justify-content-end flex-grow-1";
    config.innerHTML =
        `<div class="revert"><div class="svelte-c1i4cr"><button tabindex="-1" class="badge p-1"><svg></svg></button></div>` +
        `<div class="floating"></div></div>${controlHtml}`;
    parent.appendChild(config);
    const host = config.querySelector(".revert");
    const wrapper = host.firstElementChild;
    const badge = config.querySelector(".badge");
    const floating = config.querySelector(".floating");
    const input = config.querySelector("input");
    /** where the value is edited, which is where a keyboard press's focus belongs afterwards */
    const control = config.querySelector(`.spin-box input, [role="combobox"]`);
    const state = { value, defaultValue, show: false, presses: 0, markedWhileOpen: [] };

    /** the branch svelte mounts the menu in, beside any ghost of an earlier one, and the arrow with it */
    let branch = null;
    let arrow = null;

    function flush() {
        wrapper.classList.toggle("hide", state.value === state.defaultValue);
        if (input !== null) input.value = String(state.value);
        if (!state.show) {
            if (branch !== null) {
                branch.remove();
                arrow.remove();
                // anki_material3_motion.js puts the branch svelte unmounted straight back with .m3-exit, to
                // animate the menu away; its item keeps the click listener svelte never removed
                if (ghosts) {
                    branch.classList.add("m3-exit");
                    floating.appendChild(branch);
                }
                branch = null;
                arrow = null;
            }
            return;
        }
        if (branch !== null) {
            return; // svelte mounts the menu once, however often the row is flushed
        }
        branch = p.doc.createElement("div");
        branch.className = "popover-wrapper d-flex";
        branch.innerHTML = `<div role="listbox" class="popover hidden"></div>`;
        arrow = p.doc.createElement("div");
        arrow.className = "floating-arrow";
        arrow.innerHTML = `<div class="arrow"></div>`;
        floating.append(branch, arrow);
        const popover = branch.querySelector(".popover");
        for (let index = 0; index < items; index++) {
            const item = p.doc.createElement("button");
            item.className = "dropdown-item";
            item.tabIndex = -1;
            item.textContent = LABEL;
            item.addEventListener("click", () => {
                state.presses++;
                state.value = state.defaultValue; // RevertButton writes a clone of the default through the row
                state.show = false;
                p.w.queueMicrotask(flush);
            });
            popover.appendChild(item);
        }
        // what the .revert box carried as the menu arrived: a menu a press is waiting for must be marked,
        // or it would be on the screen, and one anki rendered before the script looked needs no mark
        state.markedWhileOpen.push(host.hasAttribute(RESTORING));
    }

    badge.addEventListener("click", () => {
        if (state.value === state.defaultValue) return; // upstream's badge opens nothing for an unchanged row
        state.show = !state.show;
        if (sync) flush();
        else p.w.queueMicrotask(flush);
    });
    flush();
    /** what upstream's own first tap leaves behind: the menu open, waiting for its item to be picked */
    const open = () => {
        state.show = true;
        flush();
    };
    return { config, host, wrapper, badge, floating, input, control, state, open };
}

const tick = () => new Promise((r) => setImmediate(r));

async function tap(p, target, { detail = 1 } = {}) {
    target.dispatchEvent(new p.w.MouseEvent("click", { bubbles: true, cancelable: true, detail }));
    await tick();
}

function animationEnd(p, target, name) {
    const event = new p.w.Event("animationend", { bubbles: true });
    Object.defineProperty(event, "animationName", { value: name });
    target.dispatchEvent(event);
}

const items = (row) => [...row.floating.querySelectorAll(".dropdown-item")];

test("the phone's tap: one tap restores through the menu anki has already rendered", async () => {
    const p = page();
    // chrome flushes svelte between anki's badge handler and the script's, so the menu is in the document
    // before the script looks for it: the press the phone takes is the one at the top of the handler
    const row = revertRow(p, { value: 7, defaultValue: 3, sync: true });
    p.run();

    await tap(p, row.badge);

    assert.equal(row.state.presses, 1, "the restore ran once, as a tap on the menu item runs it");
    assert.equal(row.state.value, 3);
    assert.equal(row.input.value, "3");
    assert.equal(row.wrapper.classList.contains("hide"), true, "the row no longer counts as modified");
    assert.deepEqual(items(row), [], "no menu is left behind");
    assert.deepEqual(row.state.markedWhileOpen, [false], "nothing waited for it, so nothing had to mark it");
    assert.equal(row.host.hasAttribute(RESTORING), false);
    assert.deepEqual(p.warnings, []);
});

test("a menu rendered after the tap is pressed as soon as it is there", async () => {
    const p = page();
    const row = revertRow(p, { value: 7, defaultValue: 3 });
    p.run();

    await tap(p, row.badge);

    assert.equal(row.state.presses, 1, "the restore ran once, as a tap on the menu item runs it");
    assert.equal(row.state.value, 3);
    assert.equal(row.input.value, "3");
    assert.equal(row.wrapper.classList.contains("hide"), true, "the row no longer counts as modified");
    assert.deepEqual(items(row), [], "no menu is left behind");
    assert.equal(row.host.hasAttribute(RESTORING), false);
    assert.deepEqual(p.warnings, []);
});

test("the menu that press waits for is never on the screen", async () => {
    const p = page();
    const row = revertRow(p);
    p.run();

    await tap(p, row.badge);

    assert.deepEqual(row.state.markedWhileOpen, [true], "the menu was rendered once, marked the whole time");
    assert.equal(row.host.hasAttribute(RESTORING), false, "and the mark goes once the menu is gone");
});

test("a menu that is in the box when the tap arrives is pressed, whoever opened it", async () => {
    const p = page();
    const row = revertRow(p);
    p.run();
    row.open();
    assert.equal(items(row).length, 1, "the replica has the menu open");

    await tap(p, row.badge);

    assert.equal(row.state.value, row.state.defaultValue);
    assert.deepEqual(items(row), [], "and the menu is closed again");
});

test("a tap that dismisses a menu leaves the ghost of it alone", async () => {
    const p = page();
    // the phone's ordering: anki's own handler closes the open menu on this tap, and the exit animation
    // has the branch back in the box by the time the script looks in it
    const row = revertRow(p, { sync: true, ghosts: true });
    p.run();
    row.open();

    await tap(p, row.badge);

    assert.equal(row.floating.querySelectorAll(".m3-exit .dropdown-item").length, 1, "the ghost was there");
    assert.equal(row.state.presses, 0, "a component svelte has destroyed restores nothing for anyone");
    assert.equal(row.state.value, 7, "the tap dismissed the menu, which is all it asked for");
    assert.equal(row.input.value, "7");
    assert.deepEqual(p.warnings, [], "and a tap that only dismissed a menu is not an anomaly worth a line");
});

test("a menu opened over a ghost is still the one that gets pressed", async () => {
    const p = page();
    const row = revertRow(p, { sync: true, ghosts: true });
    p.run();
    row.open();
    await tap(p, row.badge); // dismissed, and its ghost is still animating away

    await tap(p, row.badge);

    assert.equal(row.state.presses, 1, "the live menu this tap opened, not the dead one beside it");
    assert.equal(row.state.value, row.state.defaultValue);
});

test("a menu that is not the single item this knows is left to the user", async () => {
    const p = page();
    const row = revertRow(p, { value: 7, defaultValue: 3, items: 2 });
    p.run();

    await tap(p, row.badge);

    assert.equal(row.state.presses, 0, "nothing was pressed: the wrong entry would change something else");
    assert.equal(row.state.value, 7);
    assert.equal(items(row).length, 2, "the menu stays open, which is the two-tap flow this replaced");
    assert.equal(row.host.hasAttribute(RESTORING), false, "and it is no longer held off the screen");
    assert.equal(p.warnings.length, 1);
    assert.match(p.warnings[0], /2 items/);

    // and a second tap, with that menu still open, presses nothing either
    await tap(p, row.badge);

    assert.equal(row.state.presses, 0);
    assert.equal(row.state.value, 7);
    assert.equal(p.warnings.length, 2);
});

test("the badge of an unchanged setting restores nothing", async () => {
    const p = page();
    const row = revertRow(p, { value: 3, defaultValue: 3 });
    p.run();
    assert.equal(row.wrapper.classList.contains("hide"), true);

    await tap(p, row.badge);

    assert.equal(row.state.presses, 0);
    assert.equal(row.host.hasAttribute(RESTORING), false);
    assert.deepEqual(p.warnings, []);
});

test("the badge is named and tabbable, and enter or space restores like a tap", async () => {
    const p = page();
    const row = revertRow(p);
    p.run();
    assert.equal(row.badge.getAttribute("aria-label"), LABEL);
    assert.equal(row.badge.tabIndex, 0, "upstream leaves it at -1, where no keyboard can reach it");

    row.badge.focus();
    assert.equal(p.doc.activeElement, row.badge);
    // enter and space on a focused button produce a click with no pointer behind it
    await tap(p, row.badge, { detail: 0 });

    assert.equal(row.state.value, row.state.defaultValue);
});

test("a keyboard press hands focus on to the setting it restored", async () => {
    const p = page();
    const row = revertRow(p, { sync: true });
    p.run();
    row.badge.focus();

    await tap(p, row.badge, { detail: 0 });

    // the restore hides the badge that was focused (anki's `.hide .badge { display: none }`), and chrome
    // drops focus to the body when it goes: without this the next tab starts from the top of the page
    assert.equal(p.doc.activeElement, row.input, "the field, not the badge that is on its way out");
    assert.equal(row.state.value, row.state.defaultValue);
});

test("a keyboard press on an enum row lands on its combobox, not on the badge above it", async () => {
    const p = page();
    // anki's Select is a div with tabindex 0, and this script gave the badge a tabindex of its own
    const row = revertRow(p, { controlHtml: COMBOBOX, sync: true });
    p.run();
    row.badge.focus();

    await tap(p, row.badge, { detail: 0 });

    assert.equal(p.doc.activeElement, row.control);
    assert.equal(row.control.getAttribute("role"), "combobox");
});

test("a tap moves no focus, so no keyboard opens over the field it restored", async () => {
    const p = page();
    const row = revertRow(p, { sync: true });
    p.run();
    row.badge.focus(); // android's chrome focuses a button a finger presses

    await tap(p, row.badge);

    assert.equal(row.state.value, row.state.defaultValue);
    assert.notEqual(p.doc.activeElement, row.input, "focusing an input is what opens android's keyboard");
});

test("a row whose control this does not know still keeps the focus in the page", async () => {
    const p = page();
    const row = revertRow(p, { controlHtml: `<div class="unknown">7</div>`, sync: true });
    p.run();
    row.badge.focus();

    await tap(p, row.badge, { detail: 0 });

    assert.equal(p.doc.activeElement, row.config, "the field itself, rather than the body");
    assert.equal(row.config.tabIndex, -1, "which takes focus without taking a place in the tab order");
});

test("the field a value went back into is washed, once, and the wash clears itself", async () => {
    const p = page();
    const row = revertRow(p);
    p.run();

    await tap(p, row.badge);

    assert.equal(row.config.hasAttribute(RESTORED), true);
    // an animation of anything inside the field is not this one
    animationEnd(p, row.input, "m3-restore-flash");
    animationEnd(p, row.config, "m3-scale-in");
    assert.equal(row.config.hasAttribute(RESTORED), true);
    animationEnd(p, row.config, "m3-restore-flash");
    assert.equal(row.config.hasAttribute(RESTORED), false);
    assert.equal(p.clock.timers.size, 0, "and nothing is left running");
});

test("a wash whose animation never ran is cleared anyway", async () => {
    const p = page();
    const row = revertRow(p);
    p.run();

    await tap(p, row.badge);
    assert.equal(row.config.hasAttribute(RESTORED), true);

    p.clock.advance(1000);
    assert.equal(row.config.hasAttribute(RESTORED), false);
});

test("with reduced motion the restore is instant and unwashed", async () => {
    const p = page("/deck-options/1", { reducedMotion: true });
    const row = revertRow(p);
    p.run();

    await tap(p, row.badge);

    assert.equal(row.state.value, row.state.defaultValue);
    assert.equal(row.config.hasAttribute(RESTORED), false);
    assert.equal(p.clock.timers.size, 0, "no wash, no timer");
});

test("rows svelte renders later are adopted, and re-running the script installs nothing twice", async () => {
    const p = page();
    p.run();
    p.run();
    const row = revertRow(p);
    await tick();
    assert.equal(row.badge.getAttribute("aria-label"), LABEL);
    assert.equal(row.badge.tabIndex, 0);

    await tap(p, row.badge);

    assert.equal(row.state.presses, 1, "one listener set: one restore per tap");
    assert.deepEqual(p.warnings, [], "and no second handler looking for a menu that is already gone");

    // svelte re-rendering a row: brand new elements in place of the old ones
    const parent = row.config.parentElement;
    row.config.remove();
    const fresh = revertRow(p, { value: 9, defaultValue: 1 }, parent);
    await tick();
    assert.equal(fresh.badge.getAttribute("aria-label"), LABEL);
    await tap(p, fresh.badge);
    assert.equal(fresh.state.value, 1);
});

test("the help badge, which opens a page of its own, is left alone", async () => {
    const p = page();
    const help = p.doc.createElement("div");
    help.className = "help-badge";
    help.innerHTML = `<button class="badge p-1"><svg></svg></button>`;
    p.doc.getElementById("slot").appendChild(help);
    p.run();
    await tick();
    const badge = help.querySelector(".badge");
    assert.equal(badge.hasAttribute("aria-label"), false);
    assert.equal(badge.tabIndex, 0, "a button with no tabindex of its own is tabbable already");

    await tap(p, badge);

    assert.deepEqual(p.warnings, []);
    assert.equal(p.doc.querySelector(`[${RESTORING}]`), null);
});

test("other sveltekit pages are untouched", async () => {
    for (const path of ["/graphs", "/card-info/1", "/import-csv", "/image-occlusion/1"]) {
        const p = page(path);
        const row = revertRow(p);
        p.run();
        await tick();
        assert.equal(p.w.ankiMaterial3RevertInstalled, undefined, path);
        assert.equal(row.badge.tabIndex, -1, path);
        await tap(p, row.badge);
        assert.equal(row.state.presses, 0, path);
        assert.equal(items(row).length, 1, `${path}: the menu opens, as it always has`);
    }
});

test("without a label the badge keeps no name, says so, and still restores", async () => {
    const p = page("/deck-options/1", { withLabel: false });
    const row = revertRow(p);
    p.run();

    assert.equal(p.errors.length, 1);
    assert.match(p.errors[0], /ankiMaterial3RevertLabel/);
    assert.equal(row.badge.hasAttribute("aria-label"), false);
    assert.equal(row.badge.tabIndex, 0);

    await tap(p, row.badge);

    assert.equal(row.state.value, row.state.defaultValue);
});
