/**
 * AnkiDroid SPA Reviewer Bridge & DOM Swap Execution Pipeline
 * 
 * Manages Single-Page Application (SPA) card transitions without document reloads,
 * ensuring clean teardown, container-scoped CSS isolation (@scope), script extraction/execution,
 * MathJax re-triggering, immediate scroll/focus preservation, media cleanup, and smooth 300ms CSS crossfading.
 */
(function () {
    "use strict";

    // --- 1. EventListener, Timer, Observer & Media Tracker ---
    const EventListenerTracker = (function () {
        const trackedListeners = [];
        const trackedIntervals = new Set();
        const trackedTimeouts = new Set();
        const trackedObservers = new Set();
        const trackedAnimFrames = new Set();
        const trackedAudioObjects = new Set();

        const origAddEventListener = EventTarget.prototype.addEventListener;
        const origRemoveEventListener = EventTarget.prototype.removeEventListener;
        const origSetInterval = window.setInterval;
        const origClearInterval = window.clearInterval;
        const origSetTimeout = window.setTimeout;
        const origClearTimeout = window.clearTimeout;
        const origReqAnimFrame = window.requestAnimationFrame;
        const origCancelAnimFrame = window.cancelAnimationFrame;

        EventTarget.prototype.addEventListener = function (type, listener, options) {
            trackedListeners.push({ target: this, type, listener, options });
            if (type === "load" && (document.readyState === "complete" || document.readyState === "interactive")) {
                if (typeof listener === "function") {
                    origSetTimeout.call(window, () => listener.call(this, new Event("load")), 0);
                } else if (listener && typeof listener.handleEvent === "function") {
                    origSetTimeout.call(window, () => listener.handleEvent(new Event("load")), 0);
                }
            }
            return origAddEventListener.call(this, type, listener, options);
        };

        EventTarget.prototype.removeEventListener = function (type, listener, options) {
            for (let i = trackedListeners.length - 1; i >= 0; i--) {
                const entry = trackedListeners[i];
                if (entry.target === this && entry.type === type && entry.listener === listener) {
                    trackedListeners.splice(i, 1);
                    break;
                }
            }
            return origRemoveEventListener.call(this, type, listener, options);
        };

        window.setInterval = function (handler, timeout, ...args) {
            const id = origSetInterval.call(window, handler, timeout, ...args);
            trackedIntervals.add(id);
            return id;
        };

        window.clearInterval = function (id) {
            trackedIntervals.delete(id);
            return origClearInterval.call(window, id);
        };

        window.setTimeout = function (handler, timeout, ...args) {
            let id;
            const wrappedHandler = function (...handlerArgs) {
                trackedTimeouts.delete(id);
                if (typeof handler === "function") {
                    handler.apply(this !== undefined && this !== null ? this : window, handlerArgs);
                } else {
                    Function(handler)();
                }
            };
            id = origSetTimeout.call(window, wrappedHandler, timeout, ...args);
            trackedTimeouts.add(id);
            return id;
        };

        window.clearTimeout = function (id) {
            trackedTimeouts.delete(id);
            return origClearTimeout.call(window, id);
        };

        window.requestAnimationFrame = function (callback) {
            let id;
            const wrappedCb = function (timestamp) {
                trackedAnimFrames.delete(id);
                callback(timestamp);
            };
            id = origReqAnimFrame.call(window, wrappedCb);
            trackedAnimFrames.add(id);
            return id;
        };

        window.cancelAnimationFrame = function (id) {
            trackedAnimFrames.delete(id);
            return origCancelAnimFrame.call(window, id);
        };

        if (typeof window.MutationObserver === "function") {
            const OrigMO = window.MutationObserver;
            window.MutationObserver = function (callback) {
                const instance = new OrigMO(callback);
                trackedObservers.add(instance);
                return instance;
            };
            window.MutationObserver.prototype = OrigMO.prototype;
        }

        if (typeof window.ResizeObserver === "function") {
            const OrigRO = window.ResizeObserver;
            window.ResizeObserver = function (callback) {
                const instance = new OrigRO(callback);
                trackedObservers.add(instance);
                return instance;
            };
            window.ResizeObserver.prototype = OrigRO.prototype;
        }

        if (typeof window.IntersectionObserver === "function") {
            const OrigIO = window.IntersectionObserver;
            window.IntersectionObserver = function (callback) {
                const instance = new OrigIO(callback);
                trackedObservers.add(instance);
                return instance;
            };
            window.IntersectionObserver.prototype = OrigIO.prototype;
        }

        if (typeof window.Audio === "function") {
            const OrigAudio = window.Audio;
            window.Audio = function (...args) {
                const audio = new OrigAudio(...args);
                trackedAudioObjects.add(audio);
                return audio;
            };
            window.Audio.prototype = OrigAudio.prototype;
        }

        function purge() {
            while (trackedListeners.length > 0) {
                const { target, type, listener, options } = trackedListeners.pop();
                try {
                    origRemoveEventListener.call(target, type, listener, options);
                } catch (e) {}
            }

            for (const id of trackedIntervals) {
                try { origClearInterval.call(window, id); } catch (e) {}
            }
            trackedIntervals.clear();

            for (const id of trackedTimeouts) {
                try { origClearTimeout.call(window, id); } catch (e) {}
            }
            trackedTimeouts.clear();

            for (const id of trackedAnimFrames) {
                try { origCancelAnimFrame.call(window, id); } catch (e) {}
            }
            trackedAnimFrames.clear();

            for (const obs of trackedObservers) {
                try { obs.disconnect(); } catch (e) {}
            }
            trackedObservers.clear();

            for (const audio of trackedAudioObjects) {
                try {
                    audio.pause();
                    audio.currentTime = 0;
                } catch (e) {}
            }
            trackedAudioObjects.clear();
        }

        return { purge };
    })();

    // --- Dynamic @import Management ---
    function getDynamicImportsElement() {
        let styleEl = document.getElementById("dynamic-imports");
        if (!styleEl) {
            styleEl = document.createElement("style");
            styleEl.id = "dynamic-imports";
            (document.head || document.documentElement).appendChild(styleEl);
        }
        return styleEl;
    }

    function appendDynamicImports(importsCss) {
        if (!importsCss || !importsCss.trim()) return;
        const styleEl = getDynamicImportsElement();
        styleEl.textContent += importsCss.trim() + "\n";
    }

    function purgeDynamicImports() {
        const styleEl = document.getElementById("dynamic-imports");
        if (styleEl) {
            styleEl.textContent = "";
        }
    }

    // --- 2. Complete Media Cleanup Helper & Decoder Release ---
    function purgeMedia(container) {
        purgeDynamicImports();
        if (!container) return;
        const mediaElements = container.querySelectorAll("audio, video");
        mediaElements.forEach(media => {
            try {
                media.pause();
                media.currentTime = 0;
                media.removeAttribute("src");
                media.src = "";
                media.querySelectorAll("source").forEach(s => s.remove());
                media.load();
            } catch (e) {
                console.warn("Error stopping media element:", e);
            }
        });
    }

    function clearCard() {
        EventListenerTracker.purge();
        purgeDynamicImports();
        const containerA = document.getElementById("card-container-a");
        const containerB = document.getElementById("card-container-b");
        if (containerA) {
            purgeMedia(containerA);
            containerA.innerHTML = "";
        }
        if (containerB) {
            purgeMedia(containerB);
            containerB.innerHTML = "";
        }
    }

    // --- 3. CSS Scoping Helper for @scope & Selector Transformation ---
    function processAndScopeCss(css, containerId) {
        if (!css || !css.trim()) return { imports: "", scopedCss: "" };
        const cleanRaw = css.replace(/<\/?style[^>]*>/gi, "");
        let imports = "";
        const withoutImports = cleanRaw.replace(
            /@import\s+(?:url\((?:'[^']*'|"[^"]*"|[^)])+\)|'[^']*'|"[^"]*")[^;]*;?/gi,
            match => {
                imports += match + "\n";
                return "";
            }
        );

        // Replace ancestor selectors (html, body.card, body.nightMode, body.night_mode, body) with :scope so @scope correctly targets the layer container
        const transformed = withoutImports.replace(
            /(^|\}|\s)(html|body(\.(card|nightMode|night_mode))?)(?=[\s,{.#[:>]|$)/g,
            "$1:scope"
        );

        const scopedCss = `@scope (#${containerId}) {\n${transformed}\n}`;
        return { imports, scopedCss };
    }

    // --- 4. Script & Scoped Style Extraction Helper ---
    function extractAndExecuteScripts(container, rawHtml, containerId, isAnswer) {
        const parser = new DOMParser();
        const doc = parser.parseFromString(rawHtml, "text/html");

        // Extract script elements from both doc.head and doc.body
        const headScripts = Array.from(doc.head.querySelectorAll("script"));
        const bodyScripts = Array.from(doc.body.querySelectorAll("script"));
        const scriptElements = headScripts.concat(bodyScripts);

        scriptElements.forEach(script => script.remove());

        // Extract inline <style> elements, transform ancestor selectors to :scope, and wrap in @scope (#containerId)
        let rootImports = "";
        const styles = Array.from(doc.querySelectorAll("style"));
        styles.forEach(style => {
            const { imports, scopedCss } = processAndScopeCss(style.textContent, containerId);
            if (imports) rootImports += imports;
            style.textContent = scopedCss;
        });

        if (rootImports) {
            appendDynamicImports(rootImports);
        }

        container.innerHTML = "";
        Array.from(doc.head.childNodes).forEach(node => {
            container.appendChild(node.cloneNode(true));
        });
        Array.from(doc.body.childNodes).forEach(node => {
            container.appendChild(node.cloneNode(true));
        });

        // Alias #qa and #content wrapper before running scripts so inline scripts execute on the final DOM tree structure
        if (!container.querySelector("#qa") && !container.querySelector("#content")) {
            const wrapper = document.createElement("div");
            wrapper.id = "qa";
            while (container.firstChild) {
                wrapper.appendChild(container.firstChild);
            }
            container.appendChild(wrapper);
        }

        // Ensure #answer marker element is present when isAnswer is true (using hr#answer placed inside #qa with opacity:0 and display:block so visibility checks pass for Anki IO resize re-evaluation)
        if (isAnswer && !container.querySelector("#answer")) {
            const answerMarker = document.createElement("hr");
            answerMarker.id = "answer";
            answerMarker.style.display = "block";
            answerMarker.style.opacity = "0";
            answerMarker.style.margin = "0";
            answerMarker.style.padding = "0";
            answerMarker.style.border = "none";
            answerMarker.style.height = "0px";
            answerMarker.style.overflow = "hidden";
            answerMarker.dataset.generatedAnswerMarker = "true";
            const qaWrapper = container.querySelector("#qa") || container;
            qaWrapper.appendChild(answerMarker);
        }

        // Helper to execute script elements dynamically
        const runScripts = () => {
            scriptElements.forEach(script => {
                const newScript = document.createElement("script");
                newScript.className = "anki-card-script";

                Array.from(script.attributes).forEach(attr => {
                    newScript.setAttribute(attr.name, attr.value);
                });

                if (script.src) {
                    newScript.src = script.src;
                } else {
                    newScript.textContent = script.textContent;
                }

                container.appendChild(newScript);
            });
        };

        const ioImg = container.querySelector("#image-occlusion-container img");
        if (ioImg && !ioImg.complete && ioImg.naturalWidth === 0) {
            ioImg.addEventListener("load", runScripts, { once: true });
            ioImg.addEventListener("error", runScripts, { once: true });
        } else {
            runScripts();
        }
    }

    // --- 5. Main SPA Card Display Bridge ---
    let currentContainerId = "card-container-a";
    let crossfadeTimeoutId = null;

    function showCard(payload) {
        const data = typeof payload === "string" ? JSON.parse(payload) : payload;
        const {
            html = "",
            isAnswer = false,
            css = "",
            composeCss = "",
            bodyClass = "card",
            isNightMode = false,
            enableCrossfade = true,
            baseUrl = ""
        } = data;

        if (baseUrl) {
            const baseTag = document.getElementById("base-href");
            if (baseTag) {
                baseTag.href = baseUrl;
            }
        }

        const containerA = document.getElementById("card-container-a");
        const containerB = document.getElementById("card-container-b");
        const qaRoot = document.getElementById("qa-root");
        if (!containerA || !containerB || !qaRoot) return;

        // Handle rapid card flips during active crossfade transition (<300ms)
        if (crossfadeTimeoutId) {
            clearTimeout(crossfadeTimeoutId);
            crossfadeTimeoutId = null;
            
            const prevInactive = currentContainerId === "card-container-a" ? containerB : containerA;
            const prevActive = currentContainerId === "card-container-a" ? containerA : containerB;

            prevInactive.className = "card-layer";
            prevInactive.style.opacity = "";

            purgeMedia(prevActive);
            prevActive.innerHTML = "";
            prevActive.className = "card-layer layer-hidden";
            prevActive.style.opacity = "";

            currentContainerId = currentContainerId === "card-container-a" ? "card-container-b" : "card-container-a";
        }

        const currentContainer = currentContainerId === "card-container-a" ? containerA : containerB;
        const nextContainer = currentContainerId === "card-container-a" ? containerB : containerA;
        const nextContainerId = currentContainerId === "card-container-a" ? "card-container-b" : "card-container-a";

        // STEP 1: Teardown & Purge of Event Listeners, Timers & Dynamic Imports
        EventListenerTracker.purge();
        purgeDynamicImports();
        document.querySelectorAll(".anki-card-script").forEach(el => el.remove());

        // Strip duplicate IDs from currentContainer so document.getElementById() inside nextContainer scripts always matches new layer
        currentContainer.querySelectorAll("[id]").forEach(el => {
            el.removeAttribute("id");
        });

        // Immediately purge SVG and Image Occlusion container nodes from currentContainer
        // so global document.querySelector("svg") inside nextContainer setup() NEVER collides with previous card SVG
        currentContainer.querySelectorAll("#image-occlusion-container, svg, .image-occlusion-container").forEach(el => el.remove());

        // Purge leftover instance state on window.anki.imageOcclusion so setup() treats every card flip as a fresh invocation
        if (window.anki && window.anki.imageOcclusion) {
            try {
                for (const key in window.anki.imageOcclusion) {
                    if (typeof window.anki.imageOcclusion[key] !== "function") {
                        delete window.anki.imageOcclusion[key];
                    }
                }
            } catch (e) {
                console.warn("Error purging imageOcclusion state:", e);
            }
        }

        document.documentElement.className = isNightMode ? "night-mode" : "";
        document.documentElement.setAttribute("data-bs-theme", isNightMode ? "dark" : "light");
        document.body.className = bodyClass;

        // Un-hide nextContainer (at opacity 0) BEFORE extractAndExecuteScripts so offsetWidth/height are non-zero when imageOcclusion setup runs
        nextContainer.style.opacity = "0";
        nextContainer.classList.remove("layer-hidden");

        // STEP 2: DOM Swap & Scoped Script/Style Execution
        extractAndExecuteScripts(nextContainer, html, nextContainerId, isAnswer);

        // Process and scope composeCss + card css
        const combinedCss = (composeCss || "") + "\n" + (css || "");
        if (combinedCss.trim()) {
            const { imports, scopedCss } = processAndScopeCss(combinedCss, nextContainerId);
            if (imports) {
                appendDynamicImports(imports);
            }
            if (scopedCss) {
                const scopedStyle = document.createElement("style");
                scopedStyle.className = "card-scoped-style";
                scopedStyle.textContent = scopedCss;
                nextContainer.prepend(scopedStyle);
            }
        }

        // Alias #qa and #content inside nextContainer for legacy script compatibility
        if (!nextContainer.querySelector("#qa") && !nextContainer.querySelector("#content")) {
            const wrapper = document.createElement("div");
            wrapper.id = "qa";
            while (nextContainer.firstChild) {
                wrapper.appendChild(nextContainer.firstChild);
            }
            nextContainer.appendChild(wrapper);
        }

        // STEP 3: Immediate Navigation Reset & Focus Preservation
        finishNavigationAndFocus(nextContainer, isAnswer);

        // STEP 4: True Simultaneous 300ms CSS Crossfade (bypassed for Image Occlusion cards to match single-container Previewer/Desktop behavior)
        const isImageOcclusionCard = html.includes("image-occlusion-container") || !!currentContainer.querySelector("#image-occlusion-container");
        const shouldUseCrossfade = enableCrossfade && !isImageOcclusionCard && currentContainer.children.length > 0;

        if (shouldUseCrossfade) {
            // Read all layout geometry values FIRST before style mutations to avoid layout thrashing
            const currentH = currentContainer.offsetHeight || 0;
            const nextH = nextContainer.offsetHeight || 0;
            qaRoot.style.minHeight = Math.max(currentH, nextH) + "px";

            nextContainer.classList.add("crossfade-active");
            currentContainer.classList.add("crossfade-out");

            // Use requestAnimationFrame to schedule opacity transitions without forced synchronous reflows
            requestAnimationFrame(() => {
                nextContainer.style.opacity = "1";
                currentContainer.style.opacity = "0";
            });

            crossfadeTimeoutId = setTimeout(() => {
                crossfadeTimeoutId = null;
                purgeMedia(currentContainer);
                currentContainer.innerHTML = "";
                currentContainer.className = "card-layer layer-hidden";
                currentContainer.style.opacity = "";

                nextContainer.className = "card-layer";
                nextContainer.style.opacity = "";
                currentContainerId = nextContainerId;
                qaRoot.style.minHeight = "";
            }, 300);
        } else {
            purgeMedia(currentContainer);
            currentContainer.innerHTML = "";
            currentContainer.className = "card-layer layer-hidden";
            currentContainer.style.opacity = "";

            nextContainer.className = "card-layer";
            nextContainer.style.opacity = "1";
            currentContainerId = nextContainerId;
            qaRoot.style.minHeight = "";
        }

        // STEP 5: Extension Re-Trigger
        if (window.MathJax) {
            if (typeof window.MathJax.typesetPromise === "function") {
                window.MathJax.typesetPromise([nextContainer]).catch(err => console.error("MathJax error:", err));
            } else if (window.MathJax.Hub && typeof window.MathJax.Hub.Queue === "function") {
                window.MathJax.Hub.Queue(["Typeset", window.MathJax.Hub, nextContainer]);
            }
        }

        if (typeof window.renderMathInElement === "function") {
            window.renderMathInElement(nextContainer);
        }

        if (typeof window.resizeImages === "function") {
            window.resizeImages();
        }

        // Image Occlusion Layout Sizing & Setup Helper
        const ioContainer = nextContainer.querySelector("#image-occlusion-container");
        if (ioContainer) {
            const img = ioContainer.querySelector("img");
            if (img) {
                const setupIoLayout = () => {
                    if (img.naturalWidth > 0) {
                        const width = Math.max(1, ioContainer.parentElement?.clientWidth || window.innerWidth || 0);
                        const height = Math.round(width * img.naturalHeight / img.naturalWidth);
                        ioContainer.style.width = width + "px";
                        ioContainer.style.height = height + "px";
                        ioContainer.style.display = "block";
                        img.style.width = width + "px";
                        img.style.height = height + "px";
                    }
                };
                if (img.complete) {
                    setupIoLayout();
                } else {
                    img.addEventListener("load", setupIoLayout, { once: true });
                }
            }
        }

        if (Array.isArray(window.onShownHook)) {
            window.onShownHook.forEach(hook => {
                try {
                    if (typeof hook === "function") hook();
                } catch (e) {
                    console.error("Error in onShownHook:", e);
                }
            });
        } else if (typeof window.onShownHook === "function") {
            try {
                window.onShownHook();
            } catch (e) {
                console.error("Error in onShownHook:", e);
            }
        }
    }

    // Helper: Navigation Reset & Focus Preservation
    function finishNavigationAndFocus(container, isAnswer) {
        const isImageOcclusion = !!container.querySelector("#image-occlusion-container");
        if (!isAnswer || isImageOcclusion) {
            window.scrollTo(0, 0);
        } else {
            const answerAnchor = container.querySelector("#answer");
            if (answerAnchor && !answerAnchor.dataset.generatedAnswerMarker) {
                answerAnchor.scrollIntoView({ behavior: "instant" });
            } else {
                window.scrollTo(0, 0);
            }
        }

        const typeInput = container.querySelector("input#typeans");
        if (typeInput) {
            typeInput.addEventListener("input", function (e) {
                if (window.ankidroid && typeof window.ankidroid.onTypeAnswerInput === "function") {
                    window.ankidroid.onTypeAnswerInput(e);
                }
            });
            typeInput.addEventListener("focus", function () {
                if (typeof window.taFocus === "function") {
                    window.taFocus();
                }
            });
        }
    }

    // Global Public API
    window.anki = window.anki || {};
    window.anki.showCard = showCard;
    window.anki.clearCard = clearCard;
    window.anki.purgeMedia = purgeMedia;
    window.anki.purgeDynamicImports = purgeDynamicImports;
})();
