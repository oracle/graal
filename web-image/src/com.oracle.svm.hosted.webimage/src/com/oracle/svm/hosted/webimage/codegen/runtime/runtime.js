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
 * Class that holds the configuration of the VM.
 */
class Config {
    constructor() {
        this.libraries = {};
        this.currentWorkingDirectory = "/root";
    }
}

/**
 * Class that holds the data required to start the VM.
 */
class Data {
    constructor(config) {
        /**
         * User-specified configuration object.
         *
         * @type Config
         */
        this.config = config;

        /**
         * Optionally holds a binary support file.
         */
        this.binaryImageHeap = null;

        /**
         * Maps the library names to prefetched content during VM bootup.
         * After the VM is initialized, the keys are retained, but values are set to null.
         */
        this.libraries = {};
    }
}

/**
 * Dummy object that exists only to avoid warnings in IDEs for the `$t[name]` expressions (used in JavaScript support files).
 */
const $t = {};

/**
 * For a given JavaScript class, returns an object that can lookup its properties.
 */
function cprops(cls) {
    return cls.prototype;
}

/**
 * Placeholder for lazy-value initializers.
 */
class LazyValueThunk {
    constructor(initializer) {
        this.initializer = initializer;
    }
}

/**
 * Creates a lazy property on the specified object.
 */
function lazy(obj, name, initializer) {
    let state = new LazyValueThunk(initializer);
    Object.defineProperty(obj, name, {
        configurable: false,
        enumerable: true,
        get: () => {
            if (state instanceof LazyValueThunk) {
                state = state.initializer();
            }
            return state;
        },
        set: () => {
            throw new Error("Property is not writable.");
        },
    });
}

/**
 * Placeholder for a method and its signature.
 */
class MethodMetadata {
    constructor(method, isStatic, returnHub, ...paramHubs) {
        this.method = method;
        this.isStatic = isStatic;
        this.returnHub = returnHub;
        this.paramHubs = paramHubs;
    }
}

/**
 * Create MethodMetadata object for an instance method.
 */
function mmeta(method, returnHub, ...paramHubs) {
    return new MethodMetadata(method, false, returnHub, ...paramHubs);
}

/**
 * Create MethodMetadata object for a static method.
 */
function smmeta(method, returnHub, ...paramHubs) {
    return new MethodMetadata(method, true, returnHub, ...paramHubs);
}

/**
 * Describes extra class metadata used by the runtime.
 */
class ClassMetadata {
    /**
     * Constructs the class metadata.
     *
     * @param ft Field table
     * @param singleAbstractMethod Method metadata for the single abstract method, when the class implements exactly one functional interface
     * @param methodTable Dictionary mapping each method name to the list of overloaded signatures
     */
    constructor(ft, singleAbstractMethod = undefined, methodTable = undefined) {
        this.ft = ft;
        this.singleAbstractMethod = singleAbstractMethod;
        this.methodTable = methodTable;
    }
}

/**
 * Class for the various runtime utilities.
 */
class Runtime {
    constructor() {
        this.isLittleEndian = false;
        /**
         * Dictionary of all initializer functions.
         */
        this.jsResourceInits = {};
        /**
         * The data object of the current VM, which contains the configuration settings,
         * optionally a binary-encoded image heap, and other resources.
         *
         * The initial data value is present in the enclosing scope.
         *
         * @type Data
         */
        this.data = null;
        /**
         * Map from full Java class names to corresponding Java hubs, for classes that are accessible outside of the image.
         */
        this.hubs = {};
        /**
         * The table of native functions that can be invoked via indirect calls.
         *
         * The index in this table represents the address of the function.
         * The zero-th entry is always set to null.
         */
        this.funtab = [null];
        /**
         * Map of internal symbols that are used during execution.
         */
        Object.defineProperty(this, "symbol", {
            writable: false,
            configurable: false,
            value: {
                /**
                 * Symbol used to symbolically get the to-JavaScript-native coercion object on the Java proxy.
                 *
                 * This symbol is available as a property on Java proxies, and will return a special object
                 * that can coerce the Java proxy to various native JavaScript values.
                 *
                 * See the ProxyHandler class for more details.
                 */
                javaScriptCoerceAs: Symbol("__javascript_coerce_as__"),

                /**
                 * Key used by Web Image to store the corresponding JS native value as a property of JSValue objects.
                 *
                 * Used by the JS annotation.
                 *
                 * Use conversion.setJavaScriptNative and conversion.extractJavaScriptNative to access that property
                 * instead of using this symbol directly.
                 */
                javaScriptNative: Symbol("__javascript_native__"),

                /**
                 * Key used to store a property value (inside a JavaScript object) that contains the Java-native object.
                 *
                 * Used by the JS annotation.
                 */
                javaNative: Symbol("__java_native__"),

                /**
                 * Key used to store the runtime-generated proxy handler inside the Java class.
                 *
                 * The handler is created lazily the first time that the corresponding class is added
                 *
                 * Use getOrCreateProxyHandler to retrieve the proxy handler instead of using this symbol directly.
                 */
                javaProxyHandler: Symbol("__java_proxy_handler__"),

                /**
                 * Key used to store the extra class metadata when emitting Java classes.
                 */
                classMeta: Symbol("__class_metadata__"),

                /**
                 * Key for the hub-object property that points to the corresponding generated JavaScript class.
                 */
                jsClass: Symbol("__js_class__"),

                /**
                 * Key for the property on primitive hubs, which points to the corresponding boxed hub.
                 */
                boxedHub: Symbol("__boxed_hub__"),

                /**
                 * Key for the property on primitive hubs, which holds the boxing function.
                 */
                box: Symbol("__box__"),

                /**
                 * Key for the property on primitive hubs, which holds the unboxing function.
                 */
                unbox: Symbol("__unbox__"),

                /**
                 * Key for the constructor-overload list that is stored in the class metadata.
                 */
                ctor: Symbol("__ctor__"),

                /**
                 * Internal value passed to JavaScript mirror-class constructors
                 * to denote that the mirrored class was instantiated from Java.
                 *
                 * This is used when a JSObject subclass gets constructed from Java.
                 */
                skipJavaCtor: Symbol("__skip_java_ctor__"),

                /**
                 * Symbol for the Java toString method.
                 */
                toString: Symbol("__toString__"),
            },
        });

        // Conversion-related functions and values.
        // The following values are set or used by the jsconversion module.

        /**
         * The holder of JavaScript mirror class for JSObject subclasses.
         */
        this.mirrors = {};
        /**
         * Reference to the hub of the java.lang.Class class.
         */
        this.classHub = null;
        /**
         * Function that retrieves the hub of the specified Java object.
         */
        this.hubOf = null;
        /**
         * Function that checks if the first argument hub is the supertype or the same as the second argument hub.
         */
        this.isSupertype = null;
        /**
         * Mapping from JavaScript classes that were imported to the list of internal Java classes
         * under which the corresponding JavaScript class was imported.
         * See JS.Import annotation.
         */
        this.importMap = new Map();
    }

    /**
     * Use the build-time endianness at run-time.
     *
     * Unsafe operations that write values to byte arrays at build-time assumes the
     * endianness of the build machine. Therefore, unsafe read and write operations
     * at run-time need to assume the same endianness.
     */
    setEndianness(isLittleEndian) {
        runtime.isLittleEndian = isLittleEndian;
    }

    /**
     * Ensures that there is a Set entry for the given JavaScript class, and returns it.
     */
    ensureFacadeSetFor(cls) {
        let facades = this.importMap.get(cls);
        if (facades === undefined) {
            facades = new Set();
            this.importMap.set(cls, facades);
        }
        return facades;
    }

    /**
     * Finds the set of Java facade classes for the given JavaScript class, or an empty set if there are none.
     */
    findFacadesFor(cls) {
        let facades = this.importMap.get(cls);
        if (facades === undefined) {
            facades = new Set();
        }
        return facades;
    }

    _ensurePackage(container, name) {
        const elements = name === "" ? [] : name.split(".");
        let current = container;
        for (let i = 0; i < elements.length; i++) {
            const element = elements[i];
            current = element in current ? current[element] : (current[element] = {});
        }
        return current;
    }

    /**
     * Get or create the specified exported JavaScript mirror-class export package on the VM object.
     *
     * @param name Full Java name of the package
     */
    ensureExportPackage(name) {
        return this._ensurePackage(vm.exports, name);
    }

    /**
     * Get or create the specified exported JavaScript mirror-class package on the Runtime object.
     *
     * @param name Full Java name of the package
     */
    ensureVmPackage(name) {
        return this._ensurePackage(runtime.mirrors, name);
    }

    /**
     * Get an existing exported JavaScript mirror class on the Runtime object.
     *
     * @param className Full Java name of the class
     */
    vmClass(className) {
        const elements = className.split(".");
        let current = runtime.mirrors;
        for (let i = 0; i < elements.length; i++) {
            const element = elements[i];
            if (element in current) {
                current = current[element];
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * Returns the array with all the prefetched library names.
     */
    prefetchedLibraryNames() {
        const names = [];
        for (const name in this.data.libraries) {
            names.push(name);
        }
        return names;
    }

    /**
     * Adds a function to the function table.
     *
     * @param f The function to add
     * @returns {number} The address of the newly added function
     */
    addToFuntab(f) {
        this.funtab.push(f);
        return runtime.funtab.length - 1;
    }

    /**
     * Fetches binary data from the given url.
     *
     * @param url
     * @returns {!Promise<!ArrayBuffer>}
     */
    fetchData(url) {
        return Promise.reject(new Error("fetchData is not supported"));
    }

    /**
     * Fetches UTF8 text from the given url.
     *
     * @param url
     * @returns {!Promise<!String>}
     */
    fetchText(url) {
        return Promise.reject(new Error("fetchText is not supported"));
    }

    /**
     * Sets the exit code for the VM.
     */
    setExitCode(c) {
        vm.exitCode = c;
    }

    /**
     * Returns the absolute path of the JS file WebImage is running in.
     *
     * Depending on the runtime, this may be a URL or an absolute filesystem path.
     * @returns {!String}
     */
    getCurrentFile() {
        throw new Error("getCurrentFile is not supported");
    }
}

/**
 * Instance of the internal runtime state of the VM.
 */
const runtime = new Runtime();

/**
 * VM state that is exposed, and which represents the VM API accessible to external users.
 */
class VM {
    constructor() {
        this.exitCode = 0;
        this.exports = {};
        this.symbol = {};
        /**
         * The to-JavaScript-native coercion symbol in the external API.
         */
        Object.defineProperty(this.symbol, "as", {
            configurable: false,
            enumerable: true,
            writable: false,
            value: runtime.symbol.javaScriptCoerceAs,
        });
        /**
         * The symbol for the Java toString method in the external API.
         */
        Object.defineProperty(this.symbol, "toString", {
            configurable: false,
            enumerable: true,
            writable: false,
            value: runtime.symbol.toString,
        });
    }

    /**
     * Coerce the specified JavaScript value to the specified Java type.
     *
     * For precise summary of the coercion rules, please see the JS annotation JavaDoc.
     *
     * The implementation for this function is injected later.
     *
     * @param javaScriptValue The JavaScript value to coerce
     * @param type The name of the Java class to coerce to, or a Java Proxy representing the target class.
     * @returns {*} The closest corresponding Java Proxy value
     */
    as(javaScriptValue, type) {
        throw new Error("VM.as is not supported in this backend or it was called too early");
    }
}

/**
 * Instance of the class that represents the public VM API.
 */
const vm = new VM();

if (features.node_fs.detected && features.node_https.detected) {
    runtime.fetchText = (url) => {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return new Promise((fulfill, reject) => {
                let content = [];
                features.node_https
                    .get()
                    .get(url, (r) => {
                        r.on("data", (data) => {
                            content.push(data);
                        });
                        r.on("end", () => {
                            fulfill(content.join(""));
                        });
                    })
                    .on("error", (e) => {
                        reject(e);
                    });
            });
        } else {
            return features.node_fs.get().readFile(url, "utf8");
        }
    };
    runtime.fetchData = (url) => {
        return features.node_fs
            .get()
            .readFile(url)
            .then((d) => d.buffer);
    };
} else if (features.fetch.detected) {
    runtime.fetchText = (url) =>
        features.fetch
            .get()(url)
            .then((r) => r.text());
    runtime.fetchData = (url) =>
        features.fetch
            .get()(url)
            .then((response) => {
                if (!response.ok) {
                    throw new Error(`Failed to load data at '${url}': ${response.status} ${response.statusText}`);
                }
                return response.arrayBuffer();
            });
}

if (features.node_process.detected) {
    // Extend the setExitCode function to also set the exit code of the runtime.
    let oldFun = runtime.setExitCode;
    runtime.setExitCode = (exitCode) => {
        oldFun(exitCode);
        features.node_process.get().exitCode = exitCode;
    };
}

if (features.filename.detected) {
    runtime.getCurrentFile = () => features.filename.data.filename;
} else if (features.currentScript.detected) {
    runtime.getCurrentFile = () => features.currentScript.data.currentScript.src;
} else if (features.location.detected) {
    runtime.getCurrentFile = () => features.location.data.location.href;
}
