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
 * Checks if the given string is an array index.
 *
 * Proxied accesses always get a string property (even for indexed accesses) so
 * we need to check if the property access is an indexed access.
 *
 * A property is an index if the numeric index it is refering to has the same
 * string representation as the original property.
 * E.g the string '010' is a property while '10' is an index.
 */
function isArrayIndex(property) {
    try {
        return Number(property).toString() === property;
    } catch (e) {
        // Catch clause because not all property keys (e.g. symbols) can be
        // converted to a number.
        return false;
    }
}

/**
 * Proxy handler that proxies all array element and length accesses to a Wasm
 * array and everything else to the underlying array.
 *
 * See `proxyArray` function for more information.
 */
class ArrayProxyHandler {
    /**
     * Reference to the Wasm-world object.
     *
     * In this case, this will be a Wasm struct containing a Wasm array.
     */
    #wasmObject;

    /**
     * Immutable length of the array, determined during construction.
     */
    #length;

    /**
     * A callable (usually an exported function) that accepts the Wasm object
     * and an index and returns the element at that location.
     */
    #reader;

    constructor(wasmObject, reader) {
        this.#wasmObject = wasmObject;
        this.#length = getExport("array.length")(wasmObject);
        this.#reader = reader;
    }

    #isInBounds(idx) {
        return idx >= 0 && idx < this.#length;
    }

    #getElement(idx) {
        /*
         * We need an additional bounds check here because Wasm will trap,
         * while JS expects an undefined value.
         */
        if (this.#isInBounds(idx)) {
            return this.#reader(this.#wasmObject, idx);
        } else {
            return undefined;
        }
    }

    defineProperty() {
        throw new TypeError("This array is immutable. Attempted to call defineProperty");
    }

    deleteProperty() {
        throw new TypeError("This array is immutable. Attempted to call deleteProperty");
    }

    /**
     * Indexed accesses and the `length` property are serviced from the Wasm
     * object, everything else goes to the underlying object.
     */
    get(target, property, receiver) {
        if (isArrayIndex(property)) {
            return this.#getElement(property);
        } else if (property == "length") {
            return this.#length;
        } else {
            return Reflect.get(target, property, receiver);
        }
    }

    getOwnPropertyDescriptor(target, property) {
        if (isArrayIndex(property)) {
            return {
                value: this.#getElement(property),
                writable: false,
                enumerable: true,
                configurable: false,
            };
        } else {
            return Reflect.getOwnPropertyDescriptor(target, property);
        }
    }

    has(target, property) {
        if (isArrayIndex(property)) {
            return this.#isInBounds(Number(property));
        } else {
            return Reflect.has(target, property);
        }
    }

    isExtensible() {
        return false;
    }

    /**
     * Returns the array's own enumerable string-keyed property names.
     *
     * For arrays this is simply an array of all indices in string form.
     */
    ownKeys() {
        return Object.keys(Array.from({ length: this.#length }, (x, i) => i));
    }

    preventExtensions() {
        // Do nothing this object is already not extensible
    }

    set() {
        throw new TypeError("This array is immutable. Attempted to call set");
    }

    setPrototypeOf() {
        throw new TypeError("This array is immutable. Attempted to call setPrototypeOf");
    }
}

/**
 * Creates a read-only view on a Wasm array that looks like a JavaScript Array.
 *
 * The proxy is backed by an Array instance (albeit an empty one) and any
 * accesses except for `length` and indexed accesses (see `isArrayIndex`) are
 * proxied to the original object.
 * Because the `Array.prototype` methods are all generic and only access
 * `length` and the indices, this works. For example if `indexOf` is called on
 * the proxy, `Array.prototype.indexOf` is called with the proxy bound to
 * `this`, thus the `indexOf` implementation goes through the proxy when
 * accessing elements or determining the array length.
 */
function proxyArray(a, reader) {
    return new Proxy(new Array(), new ArrayProxyHandler(a, reader));
}

function proxyCharArray(a) {
    return proxyArray(a, getExport("array.char.read"));
}

wasmImports.convert = {};
wasmImports.convert.proxyCharArray = proxyCharArray;
