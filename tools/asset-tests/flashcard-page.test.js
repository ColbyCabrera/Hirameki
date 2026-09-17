"use strict";
// the page script Flashcard.kt loads into every card page (HIRAMEKI_PAGE_SCRIPT), taken from the kotlin source with its
// constants filled in. jsdom lays nothing out, so the text fit runs against a simple layout model of its own: font
// sizes, line heights and the page height are computed here.
// run: npm ci && npm test
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { JSDOM } = require("jsdom");

const KOTLIN = fs.readFileSync(
    path.join(__dirname, "..", "..", "AnkiDroid", "src", "main", "java", "com", "ichi2", "anki", "reviewer", "compose", "Flashcard.kt"),
    "utf8",
);

function pageScript() {
    const start = KOTLIN.indexOf('const val HIRAMEKI_PAGE_SCRIPT = """');
    assert.notEqual(start, -1, "HIRAMEKI_PAGE_SCRIPT not found in Flashcard.kt");
    const from = KOTLIN.indexOf('"""', start) + 3;
    const block = KOTLIN.slice(from, KOTLIN.indexOf('"""', from));
    assert.equal(block.includes("${"), false, "an expression template in the script: fill it in here");
    // a kotlin template names a string constant of the same file
    return block
        .replace(/<\/?script[^>]*>/g, "")
        .replace(/\$([A-Z][A-Z0-9_]*)/g, (_, name) => {
            const constant = new RegExp(`const val ${name} = "([^"$]*)"`).exec(KOTLIN);
            assert.ok(constant, `no string constant ${name} in Flashcard.kt`);
            return constant[1];
        });
}
const SCRIPT = pageScript();

const WIDTH = 300;
const PADDING = 80;
const ROOT_SIZE = 22;
const ROOT_LINE = 28;
const PRIMARY = "#6750a4";

/**
 * a card page. model: data-size (px) or data-em sets an element's font size, otherwise it inherits; data-line sets a
 * line height in px or "normal", otherwise the parent's computed one is inherited (a px value inherits as px). a
 * text-bearing element adds lines * line height; data-height adds a fixed block (an image, a deck's min-height).
 * [fit] is whether the style block allows the fit, as it does with all hirameki css on
 */
function page(html, { height = 600, primary = PRIMARY, fit = true } = {}) {
    const style = { primary, fit };
    const dom = new JSDOM(`<!DOCTYPE html><html><head></head><body class="card"><div id="qa">${html}</div></body></html>`, {
        runScripts: "outside-only",
        pretendToBeVisual: true,
    });
    const { window } = dom;
    const { document } = window;
    let clientHeight = height;
    let layouts = 0;

    function computed(node) {
        if (node === document.documentElement || node === document.body || !node.parentElement) {
            return { size: ROOT_SIZE, line: ROOT_LINE };
        }
        const parent = computed(node.parentElement);
        let size = parent.size;
        const inlineSize = node.style.getPropertyValue("font-size");
        if (inlineSize) size = parseFloat(inlineSize);
        else if (node.dataset.size) size = parseFloat(node.dataset.size);
        else if (node.dataset.em) size = parent.size * parseFloat(node.dataset.em);
        let line = parent.line;
        const inlineLine = node.style.getPropertyValue("line-height");
        if (inlineLine) line = parseFloat(inlineLine);
        else if (node.dataset.line) line = node.dataset.line === "normal" ? "normal" : parseFloat(node.dataset.line);
        return { size, line };
    }

    function hidden(node) {
        for (let n = node; n; n = n.parentElement) if (n.dataset && n.dataset.hidden) return true;
        return false;
    }

    function contentHeight() {
        layouts++;
        let total = PADDING;
        const qa = document.getElementById("qa");
        for (const node of [qa, ...qa.querySelectorAll("*")]) {
            if (hidden(node)) continue;
            if (node.dataset.height) total += parseFloat(node.dataset.height);
            let chars = 0;
            for (const child of node.childNodes) if (child.nodeType === 3) chars += child.nodeValue.trim().length;
            if (!chars) continue;
            const { size, line } = computed(node);
            const perLine = Math.max(1, Math.floor(WIDTH / (0.55 * size)));
            total += Math.ceil(chars / perLine) * (line === "normal" ? 1.2 * size : line);
        }
        return Math.ceil(total);
    }

    const variables = () => ({
        "--hirameki-primary": style.primary ? ` ${style.primary}` : "",
        "--hirameki-fit": style.fit ? " 1" : "",
    });
    window.getComputedStyle = (node) => {
        if (node === document.documentElement) return { getPropertyValue: (name) => variables()[name] || "" };
        const { size, line } = computed(node);
        return { fontSize: `${size}px`, lineHeight: line === "normal" ? "normal" : `${line}px`, color: "rgb(0, 0, 0)" };
    };
    Object.defineProperty(document.documentElement, "scrollHeight", { get: () => Math.max(clientHeight, contentHeight()) });
    Object.defineProperty(document.documentElement, "clientHeight", { get: () => clientHeight });
    window.Element.prototype.getClientRects = function () {
        return hidden(this) ? [] : [{}];
    };
    window.requestAnimationFrame = (fn) => fn();
    const messages = [];
    window.hiramekiLongPress = { postMessage: (message) => messages.push(message) };
    window.eval(SCRIPT);
    // jsdom is still "loading" here, so the script waits for this before it starts listening
    document.dispatchEvent(new window.Event("DOMContentLoaded"));
    return {
        window,
        document,
        messages,
        fit: () => window.hiramekiFitText(),
        fits: () => contentHeight() <= clientHeight,
        size: (selector) => computed(document.querySelector(selector)).size,
        line: (selector) => computed(document.querySelector(selector)).line,
        inline: (selector) => document.querySelector(selector).getAttribute("style"),
        // an inline style the fit set and took back leaves an empty style attribute, as it does in chromium
        styled: () => [document.getElementById("qa"), ...document.querySelectorAll("#qa *")].some((node) => node.style.length > 0),
        resize: (h) => {
            clientHeight = h;
            window.dispatchEvent(new window.Event("resize"));
        },
        layouts: () => layouts,
        // what swapping the style block in for another css mode does, before the shell update fits the card again
        cssMode: ({ primary, fit }) => {
            style.primary = primary;
            style.fit = fit;
        },
    };
}

const words = (n) => Array.from({ length: n }, (_, i) => `word${i % 10}`).join(" ");
const near = (actual, expected, message) => assert.ok(Math.abs(actual - expected) < 1e-9, `${message}: ${actual} vs ${expected}`);

test.describe("text fit", () => {
    test("a card that fits is left exactly as the deck styles it", () => {
        const p = page(`<div class="word" data-size="40">word</div><div class="s">${words(5)}</div>`);
        p.fit();
        assert.equal(p.styled(), false);
        assert.equal(p.size(".word"), 40);
    });

    test("supporting text shrinks first, only as far as needed, and the main word keeps its size", () => {
        // at 22px the page is 668px tall, at 85% 513px
        const p = page(`<div class="word" data-size="40">word</div><div class="s">${words(80)}</div>`);
        assert.equal(p.fits(), false);
        p.fit();
        assert.equal(p.fits(), true, "fits after the fit");
        assert.equal(p.size(".word"), 40, "main word untouched");
        const s = p.size(".s");
        assert.ok(s < 22 && s >= 22 * 0.85, `supporting between its floor and the deck's size: ${s}`);
        // the search keeps the largest fitting scale, to within one step of the halving: a step more would not fit
        const scale = s / 22 + 0.15 / 64 + 1e-6;
        p.document.querySelector(".s").style.setProperty("font-size", `${22 * scale}px`, "important");
        p.document.querySelector(".s").style.setProperty("line-height", `${28 * scale}px`, "important");
        assert.equal(p.fits(), false, "a larger size would overflow");
    });

    test("line heights in px shrink with their text; normal ones are left alone", () => {
        const p = page(`<div class="word" data-size="40" data-line="normal">word</div><div class="s">${words(80)}</div>`);
        assert.equal(p.fits(), false);
        p.fit();
        const ratio = p.size(".s") / 22;
        assert.ok(Math.abs(p.line(".s") - 28 * ratio) < 0.01, `line ${p.line(".s")} vs ${28 * ratio}`);
        assert.equal(p.inline(".word").includes("line-height"), false);
    });

    test("the main word shrinks only once supporting text is at its floor, and never below the supporting text", () => {
        // supporting at 85% still does not fit; shrinking the word gains its lines back
        const p = page(`<div class="word" data-size="44">${words(12)}</div><div class="s">${words(70)}</div>`);
        p.fit();
        assert.equal(p.fits(), true, "fits");
        near(p.size(".s"), 22 * 0.85, "supporting at its floor");
        const w = p.size(".word");
        assert.ok(w < 44 && w >= 44 * 0.7 && w >= 22, `main shrunk within its floor: ${w}`);
    });

    test("the main word never goes below the supporting text's size, even where that would make the card fit", () => {
        // fits with the word at 70%, 18.2px, but not at the 22px of the text around it
        const p = page(`<div class="word" data-size="26">${words(30)}</div><div class="s">${words(70)}</div>`);
        p.fit();
        assert.ok(p.size(".word") >= 22, `the main word went below the supporting text: ${p.size(".word")}`);
    });

    test("text only a little larger than the body is supporting text too", () => {
        const p = page(`<div class="h" data-size="24">${words(10)}</div><div class="s">${words(80)}</div>`);
        assert.equal(p.fits(), false);
        p.fit();
        assert.equal(p.fits(), true);
        assert.ok(p.size(".h") < 24, `24px text kept its size as a main word would: ${p.size(".h")}`);
    });

    test("a large wrapper with no text of its own is no main word: it shrinks with the text in it", () => {
        // in a real layout the wrapper's own size sets the least height of each line in it
        const p = page(`<div class="wrap" data-size="40"><span class="s" data-size="22">${words(80)}</span></div>`);
        p.fit();
        assert.equal(p.fits(), true);
        assert.ok(p.size(".wrap") < 40, `the wrapper kept its size: ${p.size(".wrap")}`);
    });

    test("a card that fits at no size scrolls at the deck's own sizes", () => {
        const p = page(`<div class="word" data-size="44">word</div><div class="s">${words(400)}</div>`);
        p.fit();
        assert.equal(p.fits(), false, "still scrolls");
        assert.equal(p.size(".word"), 44);
        assert.equal(p.size(".s"), 22, "shrunk, it would only scroll a little less");
        assert.equal(p.styled(), false);
    });

    test("text all one size that fits at no size scrolls at the deck's own size", () => {
        const p = page(`<div class="a">${words(200)}</div><div class="b">${words(200)}</div>`);
        p.fit();
        assert.equal(p.size(".a"), 22);
        assert.equal(p.size(".b"), 22);
    });

    test("a card taller than its page for another reason than its text keeps its text's sizes", () => {
        // an image, or a deck's min-height: the overflow is the same at every text size
        const p = page(`<div class="word" data-size="40">word</div><div class="s">${words(6)}</div><img data-height="900">`);
        p.fit();
        assert.equal(p.size(".s"), 22);
        assert.equal(p.size(".word"), 40);
        assert.equal(p.styled(), false);
    });

    test("a small note does not turn the body into the main word", () => {
        // too long to fit at 85%: the body was shrunk to 17.6px as a main word would be
        const p = page(`<div class="s">${words(110)}</div><div class="note" data-size="12">${words(2)}</div>`);
        p.fit();
        assert.ok(p.size(".s") >= 22 * 0.85, `the body went below its 85% floor: ${p.size(".s")}`);
    });

    test("a card with a small note fits within the body's floor, and the note keeps its size", () => {
        const p = page(`<div class="s">${words(80)}</div><div class="note" data-size="12">${words(2)}</div>`);
        assert.equal(p.fits(), false);
        p.fit();
        assert.equal(p.fits(), true);
        const s = p.size(".s");
        assert.ok(s < 22 && s >= 22 * 0.85, `body between its floor and the deck's size: ${s}`);
        assert.equal(p.size(".note"), 12);
    });

    test("furigana does not turn the text it sits over into the main word", () => {
        const ruby = `<ruby>漢字<rt class="rt" data-em="0.5">かんじ</rt></ruby>`;
        const p = page(`<div class="s">${words(110)}${ruby}</div>`);
        p.fit();
        assert.ok(p.size(".s") >= 22 * 0.85, `the body went below its 85% floor: ${p.size(".s")}`);
        assert.equal(p.size(".rt"), 11, "furigana keeps its size");
    });

    test("no text goes below 16px, and text a deck set smaller keeps its size", () => {
        const p = page(
            `<div class="word" data-size="40">word</div><div class="m" data-size="18">${words(100)}</div>` +
                `<div class="t" data-size="12">${words(20)}</div>`,
            // fits once the 18px text is at 16px, a little above its 85%
            { height: 645 },
        );
        assert.equal(p.fits(), false);
        p.fit();
        assert.equal(p.fits(), true);
        assert.ok(p.size(".m") < 18 && p.size(".m") >= 16, `18px text stops at 16: ${p.size(".m")}`);
        assert.equal(p.size(".t"), 12, "12px text untouched");
    });

    test("an em-sized child is pinned, so it does not follow its shrunk parent below its floor", () => {
        const p = page(
            `<div class="word" data-size="40">word</div><div class="s">${words(80)}<span class="small" data-em="0.6">${words(3)}</span></div>`,
        );
        assert.equal(p.fits(), false);
        p.fit();
        near(p.size(".small"), 22 * 0.6, "the 13.2px child keeps its size");
    });

    test("hirameki css off: no fit", () => {
        const p = page(`<div class="word" data-size="40">word</div><div class="s">${words(80)}</div>`, { primary: "", fit: false });
        p.fit();
        assert.equal(p.size(".s"), 22);
        assert.equal(p.styled(), false);
    });

    test("font size changes off: no fit, though the rest of hirameki css applies", () => {
        const p = page(`<div class="word" data-size="40">word</div><div class="s">${words(80)}</div>`, { fit: false });
        assert.equal(p.fits(), false);
        p.fit();
        assert.equal(p.size(".s"), 22);
        assert.equal(p.styled(), false);
    });

    test("switching font size changes or all hirameki css off gives a fitted card the deck's sizes back", () => {
        for (const mode of [{ primary: PRIMARY, fit: false }, { primary: "", fit: false }]) {
            const p = page(`<div class="word" data-size="40">word</div><div class="s">${words(80)}</div>`);
            p.fit();
            assert.ok(p.size(".s") < 22);
            p.cssMode(mode);
            p.fit();
            assert.equal(p.size(".s"), 22);
            assert.equal(p.size(".word"), 40);
            assert.equal(p.styled(), false);
        }
    });

    test("image occlusion cards are left to their own script", () => {
        // would fit with the text at 85%
        const p = page(`<div id="image-occlusion-container" data-height="300"><img></div><div class="s">${words(80)}</div>`, { height: 800 });
        assert.equal(p.fits(), false);
        p.fit();
        assert.equal(p.size(".s"), 22);
    });

    test("math, replay buttons and form controls are never given a size", () => {
        const p = page(
            `<div class="word" data-size="40">word</div><div class="s">${words(80)} <mjx-container class="m">x<mjx-mi class="mi">y</mjx-mi></mjx-container>` +
                `<a class="replay-button">p</a><input class="in"><button class="b">tap <b class="bb">me</b></button></div>`,
            { height: 700 },
        );
        assert.equal(p.fits(), false);
        p.fit();
        assert.ok(p.size(".s") < 22, "the card was fitted");
        for (const selector of [".m", ".mi", ".replay-button", ".in", ".b", ".bb"]) {
            const style = p.document.querySelector(selector).style;
            assert.equal(style.getPropertyValue("font-size") + style.getPropertyValue("line-height"), "", `${selector} was sized`);
        }
    });

    test("hidden text has no say in which text is the main word", () => {
        const p = page(
            `<div class="h" data-size="60" data-hidden="1">${words(200)}</div><div class="word" data-size="40">word</div><div class="s">${words(95)}</div>`,
            { height: 700 },
        );
        assert.equal(p.fits(), false);
        p.fit();
        assert.equal(p.fits(), true);
        assert.equal(p.size(".word"), 40, "the visible large word is still the main word");
    });

    test("more room gives the deck's sizes back, with a card's own inline sizes restored as they were", () => {
        const p = page(`<div class="word" data-size="40" style="font-size: 40px !important; color: red">word</div><div class="s">${words(80)}</div>`);
        p.fit();
        assert.ok(p.size(".s") < 22);
        p.resize(2000);
        assert.equal(p.size(".s"), 22);
        const word = p.document.querySelector(".word");
        assert.equal(word.style.getPropertyValue("font-size"), "40px");
        assert.equal(word.style.getPropertyPriority("font-size"), "important");
        assert.equal(p.document.querySelector(".s").style.length, 0);
    });

    test("less room fits again on resize", () => {
        const p = page(`<div class="word" data-size="40">word</div><div class="s">${words(80)}</div>`, { height: 2000 });
        p.fit();
        assert.equal(p.inline(".s"), null);
        p.resize(600);
        assert.equal(p.fits(), true);
        assert.ok(p.size(".s") < 22);
    });

    test("a page not laid out yet is not measured", () => {
        const p = page(`<div class="s">${words(80)}</div>`, { height: 0 });
        p.fit();
        assert.equal(p.inline(".s"), null);
    });

    test("the search is bounded", () => {
        const p = page(`<div class="word" data-size="44">${words(12)}</div><div class="s">${words(70)}</div>`);
        const before = p.layouts();
        p.fit();
        assert.ok(p.layouts() - before <= 20, `layouts: ${p.layouts() - before}`);
    });
});

test.describe("long press", () => {
    /** what chromium does for a long press at [target]: the pointer goes down, the word under it is selected when it is text, then contextmenu */
    function longPress(p, target, { selects }) {
        const { window } = p;
        target.dispatchEvent(new window.Event("pointerdown", { bubbles: true }));
        if (selects) {
            target.dispatchEvent(new window.Event("selectstart", { bubbles: true, cancelable: true }));
            window.getSelection().selectAllChildren(target);
        }
        target.dispatchEvent(new window.MouseEvent("contextmenu", { bubbles: true, cancelable: true }));
    }

    test("a long press that selects text says so", () => {
        const p = page(`<div class="s">${words(5)}</div>`);
        longPress(p, p.document.querySelector(".s"), { selects: true });
        assert.deepEqual(p.messages, ["selected"]);
    });

    test("a long press on blank card area selects nothing", () => {
        const p = page(`<div class="s">${words(5)}</div>`);
        longPress(p, p.document.body, { selects: false });
        assert.deepEqual(p.messages, ["none"]);
    });

    test("a selection an earlier press left is not this press's", () => {
        const p = page(`<div class="s">${words(5)}</div>`);
        longPress(p, p.document.querySelector(".s"), { selects: true });
        longPress(p, p.document.body, { selects: false });
        assert.equal(p.window.getSelection().isCollapsed, false, "the earlier selection is still there");
        assert.deepEqual(p.messages, ["selected", "none"]);
    });

    test("a selection the card cancelled is no selection", () => {
        const p = page(`<div class="s">${words(5)}</div>`);
        const target = p.document.querySelector(".s");
        target.dispatchEvent(new p.window.Event("pointerdown", { bubbles: true }));
        target.dispatchEvent(new p.window.Event("selectstart", { bubbles: true, cancelable: true }));
        target.dispatchEvent(new p.window.MouseEvent("contextmenu", { bubbles: true, cancelable: true }));
        assert.deepEqual(p.messages, ["none"]);
    });

    test("a webview that cannot message the app is not an error", () => {
        const p = page(`<div class="s">${words(5)}</div>`);
        delete p.window.hiramekiLongPress;
        const errors = [];
        p.window.addEventListener("error", (event) => errors.push(event.message));
        longPress(p, p.document.querySelector(".s"), { selects: true });
        assert.deepEqual(errors, []);
    });
});
