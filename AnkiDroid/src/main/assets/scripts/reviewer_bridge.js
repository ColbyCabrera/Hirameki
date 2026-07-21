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
                    eval(handler);
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

    // --- 2. Complete Media Cleanup Helper ---
    function purgeMedia(container) {
        if (!container) return;
        const mediaElements = container.querySelectorAll("audio, video");
        mediaElements.forEach(media => {
            try {
                media.pause();
                media.currentTime = 0;
            } catch (e) {
                console.warn("Error stopping media element:", e);
            }
        });
    }

    // --- 3. CSS Scoping Helper for @scope & Selector Transformation ---
    function processAndScopeCss(css, containerId) {
        if (!css || !css.trim()) return { imports: "", scopedCss: "" };
        const cleanRaw = css.replace(/<\/?style[^>]*>/gi, "");
        let imports = "";
        const withoutImports = cleanRaw.replace(/@import\s+url\([^)]+\);?/g, match => {
            imports += match + "\n";
            return "";
        });

        // Replace ancestor selectors (html, body.card, body.nightMode, body) with :scope so @scope correctly targets the layer container
        const transformed = withoutImports
            .replace(/\bhtml\b/g, ":scope")
            .replace(/\bbody\.card\b/g, ":scope")
            .replace(/\bbody\.nightMode\b/g, ":scope")
            .replace(/\bbody\.night_mode\b/g, ":scope")
            .replace(/\bbody\b/g, ":scope");

        const scopedCss = `@scope (#${containerId}) {\n${transformed}\n}`;
        return { imports, scopedCss };
    }

    // --- 4. Script & Scoped Style Extraction Helper ---
    function extractAndExecuteScripts(container, rawHtml, containerId) {
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
            const headStyle = document.getElementById("compose-styles") || document.head;
            headStyle.insertAdjacentHTML("beforeend", `<style>${rootImports}</style>`);
        }

        container.innerHTML = "";
        Array.from(doc.head.childNodes).forEach(node => {
            container.appendChild(node.cloneNode(true));
        });
        Array.from(doc.body.childNodes).forEach(node => {
            container.appendChild(node.cloneNode(true));
        });

        // Re-create script elements dynamically to force browser execution
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

        // STEP 1: Teardown & Purge of Event Listeners & Timers
        EventListenerTracker.purge();
        document.querySelectorAll(".anki-card-script").forEach(el => el.remove());

        document.documentElement.className = isNightMode ? "night-mode" : "";
        document.documentElement.setAttribute("data-bs-theme", isNightMode ? "dark" : "light");
        document.body.className = bodyClass;

        // STEP 2: DOM Swap & Scoped Script/Style Execution
        extractAndExecuteScripts(nextContainer, html, nextContainerId);

        // Process and scope composeCss + card css
        const combinedCss = (composeCss || "") + "\n" + (css || "");
        if (combinedCss.trim()) {
            const { imports, scopedCss } = processAndScopeCss(combinedCss, nextContainerId);
            if (imports) {
                const headStyle = document.getElementById("compose-styles") || document.head;
                headStyle.insertAdjacentHTML("beforeend", `<style>${imports}</style>`);
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

        // STEP 4: True Simultaneous 300ms CSS Crossfade with Accurate Root Height Lock (Criterion 2.5)
        if (enableCrossfade && currentContainer.children.length > 0) {
            // Un-hide nextContainer BEFORE measuring offsetHeight so offsetHeight is accurate (not 0)
            nextContainer.style.opacity = "0";
            nextContainer.classList.remove("layer-hidden");
            nextContainer.classList.add("crossfade-active");
            currentContainer.classList.add("crossfade-out");

            const currentH = currentContainer.offsetHeight || 0;
            const nextH = nextContainer.offsetHeight || 0;
            qaRoot.style.minHeight = Math.max(currentH, nextH) + "px";

            // Force reflow while nextContainer is at opacity: 0
            void nextContainer.offsetWidth;

            // Trigger concurrent transitions
            nextContainer.style.opacity = "1";
            currentContainer.style.opacity = "0";

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
        if (!isAnswer) {
            window.scrollTo(0, 0);
        } else {
            const answerAnchor = container.querySelector("#answer");
            if (answerAnchor) {
                answerAnchor.scrollIntoView({ behavior: "instant" });
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
})();
