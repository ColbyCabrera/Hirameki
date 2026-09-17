"use strict";
// anki_material3_steppers.js against a replica of the SpinBox in anki 25.09's deck options bundle: on blur or
// change it commits parseFloat(value) / g (g is 100 for a percentage, else 1), clamped to its bounds, then after
// a tick the input shows (value * g).toFixed(the step's decimals, minus 2 for a percentage).
// run: npm ci && npm test
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { JSDOM } = require("jsdom");

const SCRIPT = fs.readFileSync(
    path.join(__dirname, "..", "..", "AnkiDroid", "src", "main", "assets", "anki_material3_steppers.js"),
    "utf8",
);
const LABELS = { decrement: "Decrement value", increment: "Increment value" };

function page(path = "/deck-options/1", { withLabels = true } = {}) {
    const dom = new JSDOM(`<!doctype html><body></body>`, { url: `http://127.0.0.1:1${path}`, runScripts: "outside-only" });
    const w = dom.window;
    // a manual clock for the hold repeat
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
    if (withLabels) w.ankiMaterial3StepperLabels = LABELS;
    const errors = [];
    w.console.error = (...a) => errors.push(a.join(" "));
    w.document.body.innerHTML =
        `<div class="deck-options-page"><svelte-css-wrapper><div class="container-columns"><div class="row row-columns">` +
        `<div class="container"><div class="dynamically-slottable" id="slot"></div></div></div></div></svelte-css-wrapper></div>`;
    return { w, doc: w.document, clock, errors, run: () => w.eval(SCRIPT) };
}

/** what svelte renders for one SpinBoxRow, with SpinBox's own listeners */
function spinBox(p, { value, min, max, step = 1, percentage = false }, parent = p.doc.getElementById("slot")) {
    const g = percentage ? 100 : 1;
    const decimals = (() => {
        if (Math.floor(step) === step) return 0;
        const k = step.toString().split(".")[1].length || 0;
        return Math.max(0, percentage ? k - 2 : k);
    })();
    const config = p.doc.createElement("div");
    config.className = "config-input position-relative flex-grow-1";
    config.innerHTML = `<div class="revert"></div><div class="spin-box"><input type="number" pattern="[0-9]*" inputmode="numeric"> <!----> <!----></div>`;
    parent.appendChild(config);
    const box = config.querySelector(".spin-box");
    const input = box.querySelector("input");
    input.setAttribute("min", String(min * g));
    input.setAttribute("max", String(max * g));
    input.setAttribute("step", String(step * g));
    const state = { value, commits: 0, inputs: 0 };
    const show = () => (input.value = (state.value * g).toFixed(decimals));
    show();
    function commit() {
        state.commits++;
        const v = parseFloat(this.value) / g;
        if (!Number.isNaN(v)) state.value = Math.min(max, Math.max(min, v));
        Promise.resolve().then(show);
    }
    input.addEventListener("blur", commit);
    input.addEventListener("change", commit);
    input.addEventListener("input", () => state.inputs++);
    return { box, input, state, config };
}

const tick = () => new Promise((r) => setImmediate(r));

function pointer(p, type, target, { pointerId = 1, pointerType = "touch", button = 0 } = {}) {
    const e = new p.w.MouseEvent(type, { bubbles: true, cancelable: true, button });
    Object.defineProperty(e, "pointerId", { value: pointerId });
    Object.defineProperty(e, "pointerType", { value: pointerType });
    target.dispatchEvent(e);
    return e;
}

async function tap(p, stepper, options) {
    pointer(p, "pointerdown", stepper, options);
    p.clock.advance(100);
    pointer(p, "pointerup", stepper, options);
    pointer(p, "click", stepper, options);
    await tick();
}

const steppers = (box) => [...box.querySelectorAll(":scope > .m3-stepper")];

test("fields get a - at the start and a + at the end, labelled, outside the tab order", () => {
    const p = page();
    const { box, input } = spinBox(p, { value: 20, min: 0, max: 9999 });
    p.run();
    const [minus, plus] = steppers(box);
    assert.equal(box.firstElementChild, minus);
    assert.equal(box.lastElementChild, plus);
    assert.equal(minus.nextElementSibling, input, "svelte's input and anchors stay together in between");
    assert.equal(minus.getAttribute("aria-label"), LABELS.decrement);
    assert.equal(plus.getAttribute("aria-label"), LABELS.increment);
    assert.equal(minus.type, "button");
    assert.equal(minus.tabIndex, -1);
    assert.equal(minus.querySelector("svg").getAttribute("aria-hidden"), "true");
});

test("a tap steps an integer field once, committed through SpinBox", async () => {
    const p = page();
    const f = spinBox(p, { value: 20, min: 0, max: 9999 });
    p.run();
    const [minus, plus] = steppers(f.box);
    await tap(p, plus);
    assert.equal(f.state.value, 21);
    assert.equal(f.input.value, "21");
    assert.equal(f.state.inputs, 1, "the suffix is redrawn as for typing");
    await tap(p, minus);
    await tap(p, minus);
    assert.equal(f.state.value, 19);
});

test("steps stay within min and max, and a step at a bound commits nothing", async () => {
    const p = page();
    const f = spinBox(p, { value: 9999, min: 0, max: 9999 });
    p.run();
    const [, plus] = steppers(f.box);
    await tap(p, plus);
    assert.equal(f.state.value, 9999);
    assert.equal(f.state.commits, 0);
    const g = spinBox(p, { value: 1, min: 1, max: 5, step: 3 });
    p.clock.advance(0);
    await tick();
    const [gm, gp] = steppers(g.box);
    await tap(p, gp);
    await tap(p, gp);
    assert.equal(g.state.value, 5, "clamped to max, not past it");
    await tap(p, gm);
    await tap(p, gm);
    assert.equal(g.state.value, 1, "clamped to min");
});

test("decimal fields step by their step without float noise", async () => {
    const p = page();
    const f = spinBox(p, { value: 2.5, min: 1.31, max: 5, step: 0.01 });
    p.run();
    const [minus, plus] = steppers(f.box);
    assert.equal(f.input.value, "2.50");
    for (let i = 0; i < 7; i++) await tap(p, plus);
    assert.equal(f.input.value, "2.57");
    assert.equal(f.state.value, 2.57);
    for (let i = 0; i < 3; i++) await tap(p, minus);
    assert.equal(f.state.value, 2.54);
});

test("percentage fields step in the units they show", async () => {
    const p = page();
    // desired retention: SpinBoxFloatRow, min 0.7, max 0.99, step 0.01, percentage
    const f = spinBox(p, { value: 0.9, min: 0.7, max: 0.99, step: 0.01, percentage: true });
    p.run();
    const [minus, plus] = steppers(f.box);
    assert.equal(f.input.value, "90");
    await tap(p, plus);
    assert.equal(f.input.value, "91");
    assert.equal(f.state.value, 0.91);
    for (let i = 0; i < 20; i++) await tap(p, plus);
    assert.equal(f.state.value, 0.99);
    for (let i = 0; i < 40; i++) await tap(p, minus);
    assert.equal(f.state.value, 0.7);
    assert.equal(f.input.value, "70");
});

test("a held press repeats, speeds up, stops on release, and its click adds no step", async () => {
    const p = page();
    const f = spinBox(p, { value: 0, min: 0, max: 99999 });
    p.run();
    const [, plus] = steppers(f.box);
    pointer(p, "pointerdown", plus);
    p.clock.advance(399);
    assert.equal(f.state.value, 0, "no step before the long-press delay");
    p.clock.advance(1);
    assert.equal(f.state.value, 1);
    p.clock.advance(1000); // 128ms apart for the first second
    const afterOneSecond = f.state.value;
    assert.ok(afterOneSecond >= 8 && afterOneSecond <= 9, `about 8 repeats in the first second, got ${afterOneSecond}`);
    p.clock.advance(1000);
    assert.ok(f.state.value - afterOneSecond >= 15, "faster in the second");
    p.clock.advance(10000);
    const held = f.state.value;
    pointer(p, "pointerup", plus);
    p.clock.advance(5000);
    assert.equal(f.state.value, held, "release stops the repeat");
    pointer(p, "click", plus);
    await tick();
    assert.equal(f.state.value, held, "the click of a held press does not step again");
    await tap(p, plus);
    assert.equal(f.state.value, held + 1, "the next tap steps again");
});

test("a hold stops at the bound and when svelte removes the field", async () => {
    const p = page();
    const f = spinBox(p, { value: 95, min: 0, max: 100 });
    p.run();
    const [, plus] = steppers(f.box);
    pointer(p, "pointerdown", plus);
    p.clock.advance(5000);
    assert.equal(f.state.value, 100);
    assert.equal(p.clock.timers.size, 0, "no timer keeps running at the bound");
    pointer(p, "pointerup", plus);
    pointer(p, "click", plus);

    const g = spinBox(p, { value: 0, min: 0, max: 9999 });
    await tick();
    const [, gp] = steppers(g.box);
    pointer(p, "pointerdown", gp);
    p.clock.advance(600);
    const before = g.state.value;
    g.config.remove();
    p.clock.advance(5000);
    assert.equal(g.state.value, before, "the next tick finds the field gone and steps nothing");
    assert.equal(p.clock.timers.size, 0, "and nothing is left running");
});

test("a scroll that starts on a stepper steps nothing", async () => {
    const p = page();
    const f = spinBox(p, { value: 5, min: 0, max: 10 });
    p.run();
    const [, plus] = steppers(f.box);
    pointer(p, "pointerdown", plus);
    p.clock.advance(150);
    pointer(p, "pointercancel", plus);
    p.clock.advance(5000);
    assert.equal(f.state.value, 5);
    assert.equal(p.clock.timers.size, 0);
});

test("talkback's click (no pointer type) steps, even after a held press that got no click", async () => {
    const p = page();
    const f = spinBox(p, { value: 5, min: 0, max: 100 });
    p.run();
    const [, plus] = steppers(f.box);
    pointer(p, "pointerdown", plus);
    p.clock.advance(400);
    pointer(p, "pointerup", plus); // released off the button: no click on it
    assert.equal(f.state.value, 6);
    pointer(p, "click", plus, { pointerType: "" });
    await tick();
    assert.equal(f.state.value, 7);
});

test("pressing a stepper moves no focus, so no keyboard opens", async () => {
    const p = page();
    const f = spinBox(p, { value: 5, min: 0, max: 100 });
    p.run();
    const [, plus] = steppers(f.box);
    const down = pointer(p, "pointerdown", plus);
    assert.equal(down.defaultPrevented, true, "the pointerdown default (focus) is cancelled");
    pointer(p, "pointerup", plus);
    pointer(p, "click", plus);
    await tick();
    assert.notEqual(p.doc.activeElement, f.input);
    assert.notEqual(p.doc.activeElement, plus);
    const menu = new p.w.MouseEvent("contextmenu", { bubbles: true, cancelable: true });
    plus.dispatchEvent(menu);
    assert.equal(menu.defaultPrevented, true);
});

test("a field emptied mid-edit gets its last value back instead of a step", async () => {
    const p = page();
    const f = spinBox(p, { value: 12, min: 0, max: 100 });
    p.run();
    const [, plus] = steppers(f.box);
    f.input.value = "";
    await tap(p, plus);
    assert.equal(f.input.value, "12");
    await tap(p, plus);
    assert.equal(f.state.value, 13);
});

test("fields svelte renders later get steppers, once, and re-running the script adds nothing", async () => {
    const p = page();
    p.run();
    const f = spinBox(p, { value: 1, min: 0, max: 9 });
    await tick();
    assert.equal(steppers(f.box).length, 2);
    p.run();
    const g = spinBox(p, { value: 1, min: 0, max: 9 });
    await tick();
    assert.equal(steppers(f.box).length, 2);
    assert.equal(steppers(g.box).length, 2);
    // svelte re-rendering a field: a brand new element
    const parent = f.config.parentElement;
    f.config.remove();
    const h = spinBox(p, { value: 3, min: 0, max: 9 }, parent);
    await tick();
    assert.equal(steppers(h.box).length, 2);
    await tap(p, steppers(h.box)[1]);
    assert.equal(h.state.value, 4, "one listener set: one step per tap");
});

test("fields outside the deck options cards are left alone", async () => {
    const p = page();
    const outside = p.doc.createElement("div");
    p.doc.body.appendChild(outside);
    const f = spinBox(p, { value: 1, min: 0, max: 9 }, outside);
    p.run();
    await tick();
    assert.equal(steppers(f.box).length, 0);
});

test("other sveltekit pages get no steppers", async () => {
    for (const path of ["/graphs", "/card-info/1", "/import-csv", "/image-occlusion/1"]) {
        const p = page(path);
        const f = spinBox(p, { value: 1, min: 0, max: 9 });
        p.run();
        await tick();
        assert.equal(steppers(f.box).length, 0, path);
        assert.equal(p.w.ankiMaterial3SteppersInstalled, undefined, path);
    }
});

test("without labels the script says so and adds nothing", async () => {
    const p = page("/deck-options/1", { withLabels: false });
    const f = spinBox(p, { value: 1, min: 0, max: 9 });
    p.run();
    await tick();
    assert.equal(steppers(f.box).length, 0);
    assert.equal(p.errors.length, 1);
});
