"use strict";

/*
 * exit motion for the popups of anki's deck options page.
 *
 * anki_material3_theme.css animates every popup in, and animates bootstrap's .fade dialogs back
 * out, but three surfaces cannot be given an exit by css alone, because what would animate is out
 * of the document in the same frame the close starts:
 *   - menus (preset list, save, select options, restore default): svelte unmounts the .popover
 *   - the fsrs simulator dialog: it carries no .fade, so bootstrap waits for no transition and
 *     sets display:none right after it removes .show
 *   - that dialog's scrim: its backdrop was built unanimated with it and is removed just as fast
 * each of those is put back here for exactly one exit animation and then dropped again. the css
 * stays the source of truth for the motion: it owns the keyframes, and every exit is an enter
 * played backwards over --m3-exit-duration on --m3-exit-easing. the nodes this leaves behind are
 * marked .m3-exit, which is what those rules hang off.
 *
 * nothing here runs while no popup is open: a menu is picked up by the enter animation's own
 * animationstart, a dialog by bootstrap's hidden.bs.modal, and the observer a closing menu needs
 * watches one floating box and goes away with the ghost.
 */
(function () {
    // every sveltekit page is given this script; only the deck options page has the rules it drives
    if (!location.pathname.startsWith("/deck-options")) {
        return;
    }
    // a document installs the hooks once, however often it is styled
    if (window.ankiMaterial3MotionInstalled) {
        return;
    }
    window.ankiMaterial3MotionInstalled = true;

    /** the enter animation the css runs on menus and on the dialog bootstrap does not animate */
    const ENTER_ANIMATION = "m3-scale-in";
    /** marks a node that is only still on screen to be animated away */
    const EXIT_CLASS = "m3-exit";
    /** clears a ghost whose exit animation never ran, so nothing can be left on screen */
    const EXIT_FALLBACK_MS = 1000;

    /** dialogs on their way out, each with the callback that leaves bootstrap's state as it was */
    const dialogExits = new WeakMap();

    /**
     * whether a popup should be animated away at all. the same question the css asks, so that
     * reduced motion closes every popup the way it has always closed the menus: at once
     */
    function animates() {
        return !window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    }

    /**
     * keeps a menu on screen for its exit once svelte unmounts it.
     *
     * @param {Element} popover the menu that has just started its enter animation
     */
    function watchMenu(popover) {
        // WithFloating keeps this box in the document and floating-ui leaves it where the menu was
        // placed, so putting the menu back into it needs no measuring and moves nothing
        const floating = popover.closest(".floating");
        if (floating === null) {
            return;
        }
        // the branch of that box the menu hangs in, which is what svelte removes on close
        let branch = popover;
        while (branch.parentElement !== null && branch.parentElement !== floating) {
            branch = branch.parentElement;
        }

        let exiting = false;
        let childrenWhileExiting = 0;
        let fallback = 0;

        function finish() {
            observer.disconnect();
            clearTimeout(fallback);
            popover.removeEventListener("animationend", finish);
            branch.remove();
        }

        function startExit() {
            exiting = true;
            popover.classList.add(EXIT_CLASS);
            branch.classList.add(EXIT_CLASS);
            popover.removeAttribute("id"); // the menu that reopens carries the same one
            branch.setAttribute("aria-hidden", "true");
            floating.insertBefore(branch, floating.firstChild);
            childrenWhileExiting = floating.children.length;
            popover.addEventListener("animationend", finish);
            fallback = setTimeout(finish, EXIT_FALLBACK_MS);
        }

        const observer = new MutationObserver(function () {
            if (exiting) {
                // the menu was opened again over the ghost. it goes now, before floating-ui measures
                // this box for the new menu: measured with both, the new menu would be misplaced
                if (floating.children.length !== childrenWhileExiting) {
                    finish();
                }
                return;
            }
            if (popover.isConnected) {
                return;
            }
            // the branch outlived the menu, so this is not the shape the exit knows how to put back
            // and guessing would leave the wrong thing on screen; the close stays as it is
            if (branch.isConnected || !animates()) {
                observer.disconnect();
                return;
            }
            startExit();
        });

        observer.observe(floating, { childList: true });
    }

    /**
     * shows a dialog bootstrap has just hidden for as long as its exit animation, along with the
     * scrim bootstrap removed with it.
     *
     * @param {Element} modal the .modal element, already display:none and stripped of .show
     */
    function playDialogExit(modal) {
        // a stand-in for the backdrop: bootstrap reuses its own element on the next open, and the
        // .modal-backdrop z-index puts this one under the dialog it dims, as the real one was
        const scrim = document.createElement("div");
        scrim.className = `modal-backdrop show ${EXIT_CLASS}`;
        document.body.appendChild(scrim);

        modal.style.display = "block";
        modal.classList.add(EXIT_CLASS);

        let fallback = 0;

        function finish() {
            clearTimeout(fallback);
            dialogExits.delete(modal);
            modal.removeEventListener("animationend", finish);
            modal.classList.remove(EXIT_CLASS);
            modal.style.display = "none"; // what bootstrap left behind
            scrim.remove();
        }

        dialogExits.set(modal, finish);
        modal.addEventListener("animationend", finish);
        fallback = setTimeout(finish, EXIT_FALLBACK_MS);
    }

    document.addEventListener("animationstart", function (event) {
        const popover = event.target;
        if (event.animationName !== ENTER_ANIMATION) {
            return;
        }
        if (!(popover instanceof Element) || !popover.classList.contains("popover")) {
            return;
        }
        // the ghost's own exit, which reuses the enter's keyframes under the same name: putting a
        // node back restarts its animations, and watching it a second time would install a second
        // observer that sees the first one drop the ghost and puts it back, on and on
        if (popover.classList.contains(EXIT_CLASS)) {
            return;
        }
        watchMenu(popover);
    });

    // bootstrap fires this once it has hidden a dialog it does not animate: the dialog is already
    // display:none and its backdrop already gone, both in this frame, before anything was painted
    document.addEventListener("hidden.bs.modal", function (event) {
        const modal = event.target;
        if (!(modal instanceof Element) || modal.classList.contains("fade")) {
            return; // bootstrap waits for a .fade dialog's own transitions, which the css animates
        }
        if (!animates()) {
            return;
        }
        playDialogExit(modal);
    });

    // a dialog opened again while it was still fading out: its exit lands first, so that nothing is
    // left to undo the show that follows this event
    document.addEventListener("show.bs.modal", function (event) {
        const finish = dialogExits.get(event.target);
        if (finish !== undefined) {
            finish();
        }
    });
})();
