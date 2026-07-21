/**
 *  Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * AnkiDroid sessionStorage Polyfill
 *
 * Problem:
 * In Android WebView (Chromium backend), calling `WebView.loadDataWithBaseURL` replaces the main-frame
 * document and resets Chromium's `SessionStorageNamespace`. Consequently, native `window.sessionStorage`
 * is wiped on every card flip (Front -> Back) and card transition.
 *
 * Solution:
 * This polyfill transparently wraps `window.sessionStorage` with a JavaScript `Proxy` backed by
 * `localStorage` under a dedicated namespace prefix (`__anki_ss_`). Because `localStorage` is
 * persisted per-origin (`server.baseUrl()`), stored values survive `loadDataWithBaseURL` reloads
 * while offering full synchronous HTML5 `Storage` spec compliance to card template scripts.
 *
 * Specifications & Security:
 * - Implements HTML5 Web Storage API: getItem, setItem, removeItem, clear, key, and length.
 * - Coerces index via ToUint32 (index >>> 0) according to Web Storage spec.
 * - Protects internal methods (getItem, setItem, etc.) from being overwritten or deleted by card scripts.
 * - Supports property syntax: sessionStorage.myKey = "val", sessionStorage['myKey'], delete sessionStorage.myKey.
 * - Supports enumeration: Object.keys(sessionStorage), for...in loops, and 'key' in sessionStorage.
 * - Symbol safe across all Proxy traps.
 */
(function () {
    "use strict";

    try {
        var PREFIX = "__anki_ss_";
        var STORAGE_METHODS = new Set([
            "getItem",
            "setItem",
            "removeItem",
            "clear",
            "key",
            "length",
        ]);

        /**
         * Core Storage object mapping sessionStorage methods to prefixed localStorage keys.
         */
        var polyfillStorage = {
            /**
             * Retrieves a key's value from session storage.
             * @param {string} key
             * @returns {string|null} The stored string value or null if key does not exist.
             */
            getItem: function (key) {
                var stringKey = String(key);
                var val = localStorage.getItem(PREFIX + stringKey);
                return val !== null ? val : null;
            },

            /**
             * Stores a key-value pair in session storage.
             * @param {string} key
             * @param {*} value
             */
            setItem: function (key, value) {
                var stringKey = String(key);
                var stringVal = String(value);
                localStorage.setItem(PREFIX + stringKey, stringVal);
            },

            /**
             * Removes a key-value pair from session storage.
             * @param {string} key
             */
            removeItem: function (key) {
                var stringKey = String(key);
                localStorage.removeItem(PREFIX + stringKey);
            },

            /**
             * Clears all session storage keys (purges only __anki_ss_ prefixed keys from localStorage).
             */
            clear: function () {
                var keysToRemove = [];
                for (var i = 0; i < localStorage.length; i++) {
                    var k = localStorage.key(i);
                    if (k && k.indexOf(PREFIX) === 0) {
                        keysToRemove.push(k);
                    }
                }
                for (var j = 0; j < keysToRemove.length; j++) {
                    localStorage.removeItem(keysToRemove[j]);
                }
            },

            /**
             * Returns the key at the specified index in session storage.
             * Spec requirement: Converts index using ToUint32 (index >>> 0).
             * @param {number} index
             * @returns {string|null} Key name or null if index is out of bounds.
             */
            key: function (index) {
                var numIndex = index >>> 0;
                var count = 0;
                for (var i = 0; i < localStorage.length; i++) {
                    var k = localStorage.key(i);
                    if (k && k.indexOf(PREFIX) === 0) {
                        if (count === numIndex) {
                            return k.substring(PREFIX.length);
                        }
                        count++;
                    }
                }
                return null;
            },

            /**
             * Number of key-value pairs in session storage.
             * @type {number}
             */
            get length() {
                var count = 0;
                for (var i = 0; i < localStorage.length; i++) {
                    var k = localStorage.key(i);
                    if (k && k.indexOf(PREFIX) === 0) {
                        count++;
                    }
                }
                return count;
            },
        };

        /**
         * Proxy handler trapping property reads, assignments, deletions, and enumerations.
         */
        var proxyHandler = {
            get: function (target, prop, receiver) {
                if (typeof prop === "symbol") {
                    return target[prop];
                }
                if (STORAGE_METHODS.has(prop)) {
                    var member = target[prop];
                    return typeof member === "function" ? member.bind(target) : member;
                }
                var val = target.getItem(prop);
                return val !== null ? val : target[prop];
            },

            set: function (target, prop, value, receiver) {
                if (typeof prop === "symbol") {
                    target[prop] = value;
                    return true;
                }
                if (STORAGE_METHODS.has(prop)) {
                    return false; // Prevent overwriting polyfill methods/length
                }
                target.setItem(prop, value);
                return true;
            },

            deleteProperty: function (target, prop) {
                if (typeof prop === "symbol") {
                    delete target[prop];
                    return true;
                }
                if (STORAGE_METHODS.has(prop)) {
                    return false; // Prevent deleting polyfill methods
                }
                target.removeItem(prop);
                return true;
            },

            has: function (target, prop) {
                if (typeof prop === "symbol") {
                    return prop in target;
                }
                if (STORAGE_METHODS.has(prop)) {
                    return true;
                }
                return target.getItem(prop) !== null || prop in target;
            },

            ownKeys: function (target) {
                var keys = [];
                for (var i = 0; i < localStorage.length; i++) {
                    var k = localStorage.key(i);
                    if (k && k.indexOf(PREFIX) === 0) {
                        keys.push(k.substring(PREFIX.length));
                    }
                }
                return keys;
            },

            getOwnPropertyDescriptor: function (target, prop) {
                if (typeof prop === "symbol") {
                    return Object.getOwnPropertyDescriptor(target, prop);
                }
                if (STORAGE_METHODS.has(prop)) {
                    return Object.getOwnPropertyDescriptor(target, prop);
                }
                var val = target.getItem(prop);
                if (val !== null) {
                    return {
                        configurable: true,
                        enumerable: true,
                        writable: true,
                        value: val,
                    };
                }
                return Object.getOwnPropertyDescriptor(target, prop);
            },
        };

        // Create Proxy instance over polyfillStorage
        var proxy = new Proxy(polyfillStorage, proxyHandler);

        // Define window.sessionStorage getter
        Object.defineProperty(window, "sessionStorage", {
            configurable: true,
            enumerable: true,
            get: function () {
                return proxy;
            },
        });
    } catch (e) {
        if (window.console && console.error) {
            console.error("AnkiDroid sessionStorage polyfill error:", e);
        }
    }
})();
