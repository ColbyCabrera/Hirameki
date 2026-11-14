// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

"use strict";

(() => {
    const MASK_DRAW_LIMIT_MS = 500;
    // requestAnimationFrame stops while the page is hidden, which must not stop the card queue
    const FRAME_WAIT_LIMIT_MS = 200;
    // A reset the renderer cannot finish must not stop the card queue
    const ZOOM_RESET_LIMIT_MS = 300;
    const ZOOM_TOLERANCE = 0.001;

    let masksDrawn = Promise.resolve();

    const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
    const nextFrame = () => new Promise(resolve => requestAnimationFrame(resolve));

    function reportingMaskDrawing(setup) {
        return function (options) {
            let onMasksDrawn;
            masksDrawn = new Promise(resolve => (onMasksDrawn = resolve));
            return setup.call(this, {
                ...options,
                onDidDrawShapes(...args) {
                    options?.onDidDrawShapes?.(...args);
                    onMasksDrawn();
                },
            });
        };
    }

    function zoomResetFinished(scale) {
        const viewport = window.visualViewport;
        return new Promise(resolve => {
            const finish = () => {
                clearTimeout(limit);
                viewport.removeEventListener("resize", onResize);
                resolve();
            };
            const onResize = () => {
                if (Math.abs(viewport.scale - scale) < ZOOM_TOLERANCE) finish();
            };
            const limit = setTimeout(finish, ZOOM_RESET_LIMIT_MS);
            viewport.addEventListener("resize", onResize);
            // The reset may have finished before this queued action runs
            onResize();
        });
    }

    if (globalThis.anki?.imageOcclusion) {
        anki.imageOcclusion.setup = reportingMaskDrawing(anki.imageOcclusion.setup);
        anki.setupImageCloze = anki.imageOcclusion.setup;
    }

    globalThis.hirameki = {
        /** Holds later cards until a native zoom reset to the page scale has reached the renderer. */
        awaitZoomReset(scale) {
            _queueAction(() => zoomResetFinished(scale));
        },

        /** Tells the app once the card shown before this call has been laid out and painted. */
        reportWhenShown(token) {
            _queueAction(async () => {
                await Promise.race([masksDrawn, delay(MASK_DRAW_LIMIT_MS)]);
                masksDrawn = Promise.resolve();
                await Promise.race([nextFrame().then(nextFrame), delay(FRAME_WAIT_LIMIT_MS)]);
                globalThis.hiramekiCardBridge?.postMessage(String(token));
            });
        },
    };
})();
