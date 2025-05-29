/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * Represents a potential feature of the JS runtime.
 *
 * During construction, the detection callback is called to detect whether the runtime supports the feature.
 * The callback returns whether the feature was detected and may store some data
 * into the 'data' member for future use.
 */
class Feature {
    constructor(descr, detection_callback) {
        this.description = descr;
        this.data = {};
        this.detected = detection_callback(this.data);
    }
}

/**
 * Specialized feature to detect global variables.
 */
class GlobalVariableFeature extends Feature {
    constructor(descr, var_name) {
        super(descr, GlobalVariableFeature.detection_callback.bind(null, var_name));
    }

    /**
     * Function to detect a global variable.
     *
     * Uses the 'globalThis' object which should represent the global scope in
     * modern runtimes.
     */
    static detection_callback(var_name, data) {
        if (var_name in globalThis) {
            data.global = globalThis[var_name];
            return true;
        }

        return false;
    }

    /**
     * Returns the global detected by this feature.
     */
    get() {
        return this.data.global;
    }
}

/**
 * Specialized feature to detect the presence of Node.js modules.
 */
class RequireFeature extends Feature {
    constructor(descr, module_name) {
        super(descr, RequireFeature.detection_callback.bind(null, module_name));
    }

    /**
     * Function to detect a Node.js module.
     */
    static detection_callback(module_name, data) {
        if (typeof require != "function") {
            return false;
        }

        try {
            data.module = require(module_name);
            return true;
        } catch (e) {
            return false;
        }
    }

    /**
     * Returns the module detected by this feature.
     */
    get() {
        return this.data.module;
    }
}

/**
 * Collection of features needed for runtime functions.
 */
let features = {
    // https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API
    fetch: new GlobalVariableFeature("Presence of the Fetch API", "fetch"),

    // https://nodejs.org/api/fs.html#promises-api
    node_fs: new RequireFeature("Presence of Node.js fs promises module", "fs/promises"),

    // https://nodejs.org/api/https.html
    node_https: new RequireFeature("Presence of Node.js https module", "https"),

    // https://nodejs.org/api/process.html
    node_process: new RequireFeature("Presence of Node.js process module", "process"),

    /**
     * Technically, '__filename' is not a global variable, it is a variable in the module scope.
     * https://nodejs.org/api/globals.html#__filename
     */
    filename: new Feature("Presence of __filename global", (d) => {
        if (typeof __filename != "undefined") {
            d.filename = __filename;
            return true;
        }

        return false;
    }),

    // https://developer.mozilla.org/en-US/docs/Web/API/Document/currentScript
    currentScript: new Feature("Presence of document.currentScript global", (d) => {
        if (
            typeof document != "undefined" &&
            "currentScript" in document &&
            document.currentScript != null &&
            "src" in document.currentScript
        ) {
            d.currentScript = document.currentScript;
            return true;
        }

        return false;
    }),

    // https://developer.mozilla.org/en-US/docs/Web/API/WorkerGlobalScope/location
    location: new Feature("Presence of Web worker location", (d) => {
        if (typeof self != "undefined") {
            d.location = self.location;
            return true;
        }
        return false;
    }),
};
