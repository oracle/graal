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
 * Creates an array where only the component type is known.
 *
 * Uses the component type to determine the correct constructor to call.
 */
function _aH(length, componentHub) {
    const companion = componentHub.$t["com.oracle.svm.core.hub.DynamicHub"].$f["companion"];
    const hub = companion.$t["com.oracle.svm.core.hub.DynamicHubCompanion"].$f["arrayHub"];

    let fun;

    if (componentHub == $t["java.lang.Boolean"].$f["TYPE"]) {
        fun = _au8;
    } else if (componentHub == $t["java.lang.Byte"].$f["TYPE"]) {
        fun = _ai8;
    } else if (componentHub == $t["java.lang.Short"].$f["TYPE"]) {
        fun = _ai16;
    } else if (componentHub == $t["java.lang.Character"].$f["TYPE"]) {
        fun = _au16;
    } else if (componentHub == $t["java.lang.Integer"].$f["TYPE"]) {
        fun = _ai32;
    } else if (componentHub == $t["java.lang.Float"].$f["TYPE"]) {
        fun = _af32;
    } else if (componentHub == $t["java.lang.Long"].$f["TYPE"]) {
        fun = _ai64;
    } else if (componentHub == $t["java.lang.Double"].$f["TYPE"]) {
        fun = _af64;
    } else {
        fun = _aO;
    }

    return fun(length, hub);
}

/**
 * Create array of objects
 */
function _aO(length, hub) {
    const array = new Array(length);
    array.fill(null);
    array.hub = hub;
    return array;
}

/**
 * Create Int8Array
 */
function _ai8(length, hub) {
    const array = new Int8Array(length);
    array.hub = hub;
    return array;
}

/**
 * Create Uint8Array
 */
function _au8(length, hub) {
    const array = new Uint8Array(length);
    array.hub = hub;
    return array;
}

/**
 * Create Int16Array
 */
function _ai16(length, hub) {
    const array = new Int16Array(length);
    array.hub = hub;
    return array;
}

/**
 * Create Uint16Array
 */
function _au16(length, hub) {
    const array = new Uint16Array(length);
    array.hub = hub;
    return array;
}

/**
 * Create Int32Array
 */
function _ai32(length, hub) {
    const array = new Int32Array(length);
    array.hub = hub;
    return array;
}

/**
 * Create array for longs
 */
function _ai64(length, hub) {
    const array = new BigInt64Array(length);
    array.hub = hub;
    initComponentView(array);
    return array;
}

/**
 * Create Float32Array
 */
function _af32(length, hub) {
    const array = new Float32Array(length);
    array.hub = hub;
    return array;
}

/**
 * Create Float64Array
 */
function _af64(length, hub) {
    const array = new Float64Array(length);
    array.hub = hub;
    return array;
}

function isArray(array) {
    return Array.isArray(array) || (ArrayBuffer.isView(array) && !(array instanceof DataView));
}

function setArrayProps(array, hub, hashCode) {
    array.hub = hub;
    array[HASH_CODE_FIELD] = hashCode;
}

function _a$h_(array, defaultval, hub, hashCode) {
    if (!(defaultval === 0 && !Array.isArray(array))) {
        // array is not a typedArray that is filled with 0
        array.fill(defaultval);
    }
    setArrayProps(array, hub, hashCode);
}

/**
 * Initializes the array with the given hub and copies the given
 * values into the array.
 */
function _a$hi_(array, values, hub, hashCode) {
    if (array.length !== values.length) {
        throw ShouldNotReachHere("Values in array initializer do not match array length");
    }

    for (var i = 0; i < array.length; i++) {
        array[i] = values[i];
    }

    setArrayProps(array, hub, hashCode);
}

/**
 * Store a Long64 value into a BigInt64Array.
 */
function bigInt64ArrayStore(array, index, value) {
    let lowBits = Long64.lowBits(value);
    let highBits = Long64.highBits(value);
    if (runtime.isLittleEndian) {
        array.componentView[index * 2] = lowBits;
        array.componentView[index * 2 + 1] = highBits;
    } else {
        array.componentView[index * 2 + 1] = lowBits;
        array.componentView[index * 2] = highBits;
    }
}

/**
 * Load a Long64 value from a BigInt64Array.
 */
function bigInt64ArrayLoad(array, index) {
    let low, high;
    if (runtime.isLittleEndian) {
        low = array.componentView[index * 2];
        high = array.componentView[index * 2 + 1];
    } else {
        low = array.componentView[index * 2 + 1];
        high = array.componentView[index * 2];
    }
    return Long64.fromTwoInt(low, high);
}

/**
 * BigInt64Arrays require a componentView property which is a Int32 view on the array data.
 */
function initComponentView(bigInt64Array) {
    bigInt64Array.componentView = new Int32Array(bigInt64Array.buffer);
}

/**
 * Similar to _a$h_, except that it installs an Int32Array view in the BigInt64Array and does a component-wise store.
 */
function _a$h_BigInt64Array_(array, defaultval, hub, hashCode) {
    let lowBits = Long64.lowBits(defaultval);
    let highBits = Long64.highBits(defaultval);
    initComponentView(array);
    for (var i = 0; i < array.length; i++) {
        bigInt64ArrayStore(array, i, defaultval);
    }
    setArrayProps(array, hub, hashCode);
}

/**
 * Similar to _a$hi_, except that it installs an Int32Array view in the BigInt64Array and does a component-wise store.
 */
function _a$hi_BigInt64Array_(array, values, hub, hashCode) {
    if (array.length !== values.length) {
        throw ShouldNotReachHere("Values in array initializer do not match array length");
    }

    initComponentView(array);
    for (var i = 0; i < array.length; i++) {
        bigInt64ArrayStore(array, i, values[i]);
    }
    setArrayProps(array, hub, hashCode);
}

/**
 * Converts a 'bits'-sized unsigned value into its 2s-complement value.
 *
 * JS does not have < 32 bit integer values, so any smaller sized integer value
 * passed to JS will be treated as a positive 32-bit integer.
 *
 * For example for bits = 8, 128 is the 32-bit interpretation, but -1 is the
 * actual value.
 */
function unsignedToSigned(unsignedValue, bits) {
    if (bits == 32) {
        return unsignedValue | 0;
    }

    const mask = (1 << bits) - 1;

    const signMask = 1 << (bits - 1);

    unsignedValue &= mask;

    // Check if the sign bit is set
    if (unsignedValue & signMask) {
        return unsignedValue | ~mask;
    } else {
        return unsignedValue;
    }
}

/**
 * The same as _a$hi_, but accepts an array of packed primitive values.
 *
 * Each element in values contains (32 / bits) values each with 'bits' bits. In
 * total values contains len values.
 */
function _a$hip_(array, len, bits, values, hub, hashCode) {
    if (array.length !== len) {
        throw ShouldNotReachHere("Values in array initializer do not match array length");
    }

    const perInt = 32 / bits;

    // For 32 bits, we need a special case, because the general case overflows.
    const mask = bits == 32 ? 0xffffffff : (1 << bits) - 1;

    if (bits == 32) {
        for (let i = 0; i < array.length; i++) {
            array[i] = values[i];
        }
    } else {
        // Unpack values into target array.
        for (let i = 0; i < values.length; i++) {
            const compound = values[i];
            for (let j = 0; j < perInt; j++) {
                let idx = i * perInt + j;

                if (idx >= len) {
                    break;
                }

                array[idx] = unsignedToSigned((compound >> (j * bits)) & mask, bits);
            }
        }
    }

    if (bits == 1) {
        array.map((v) => !!v);
    }

    setArrayProps(array, hub, hashCode);
}

var Base64Byte = {
    chars: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=",
    padding: "=",

    /**
     * Decodes a base64 string with padding to a Uint8Array
     */
    decode: (str) => {
        Guarantee(str.length % 4 == 0, "Base64 string length is multiple of four.");
        let bytes = (str.length / 4) * 3;

        if (str[str.length - 1] == Base64Byte.padding) {
            bytes--;
            if (str[str.length - 2] == Base64Byte.padding) {
                bytes--;
            }
        }

        let array = new Uint8Array(bytes);

        let pos = 0;
        let bytePos = 0;

        while (pos < str.length) {
            let s1 = Base64Byte.chars.indexOf(str[pos++]);
            let s2 = Base64Byte.chars.indexOf(str[pos++]);
            let s3 = Base64Byte.chars.indexOf(str[pos++]);
            let s4 = Base64Byte.chars.indexOf(str[pos++]);

            /*
             * Checks are only enabled when debugging because they slow down
             * decoding massively.
             *
             * This guard references a constant and should be optimized away
             * by the runtime.
             */
            if (DEBUG_CHECKS) {
                Guarantee(s1 >= 0 && s1 <= 64, "Is valid Base64 char");
                Guarantee(s2 >= 0 && s2 <= 64, "Is valid Base64 char");
                Guarantee(s3 >= 0 && s3 <= 64, "Is valid Base64 char");
                Guarantee(s4 >= 0 && s4 <= 64, "Is valid Base64 char");
            }

            let b1 = (s1 << 2) | (s2 >> 4);
            let b2 = ((s2 << 4) & 0xff) | (s3 >> 2);
            let b3 = ((s3 << 6) & 0xff) | s4;

            array[bytePos++] = b1;
            /*
             * Two padding bytes mean that only a single byte is encoded in
             * these four characters.
             * One padding byte means two bytes are encoded.
             */
            if (s3 != 64) {
                array[bytePos++] = b2;
                if (s4 != 64) {
                    array[bytePos++] = b3;
                }
            }
        }

        return array;
    },
};

/**
 * Same as _a$hip_ but the values array is encoded as a base64 string.
 */
function _a$hipb64_(array, len, bits, base64str, hub, hashCode) {
    Guarantee(array.length == len, "Values in array initializer do not match array length");

    const byteArray = Base64Byte.decode(base64str);

    Guarantee(bits == 1 || (byteArray.length * 8) % bits == 0, "Byte array has bytes left over");
    Guarantee(bits == 1 || (byteArray.length * 8) / bits == len, "Byte array does not have expected number of bytes");

    switch (bits) {
        /*
         * Booleans are a special case because here we need to unpack a byte
         * into 8 booleans instead of packing bytes into a larger sized value.
         */
        case 1:
            Guarantee(byteArray.length * 8 >= len, "Byte array does not have enough bytes");
            for (let i = 0; i < byteArray.length; i++) {
                for (let j = 0; j < 8; j++) {
                    const idx = i * 8 + j;

                    if (idx >= len) {
                        break;
                    }

                    array[idx] = !!((byteArray[i] >> j) & 0x1);
                }
            }
            break;
        // Can directly copy over
        case 8:
            for (let i = 0; i < array.length; i++) {
                array[i] = unsignedToSigned(byteArray[i], bits);
            }
            break;
        case 16:
        case 32:
            // Number of bytes needed per element
            const perElement = bits / 8;
            /*
             * We need to convert the byte array decoded from the base64 string into an
             * array of 'bits'-sized values.
             */
            for (let i = 0; i < array.length; i++) {
                let compound = 0;
                for (let j = 0; j < perElement; j++) {
                    const idx = i * perElement + j;

                    if (idx >= byteArray.length) {
                        break;
                    }

                    compound |= byteArray[idx] << (j * 8);
                }

                array[i] = unsignedToSigned(compound, bits);
            }
            break;
        default:
            throw ShouldNotReachHere("Invalid number of bits: " + bits);
    }

    setArrayProps(array, hub, hashCode);
}

/**
 * Create a new (multi-dimensional) array.
 *
 * Takes 1 + n arguments where n is the array dimension.
 * The first argument is the hub of the multi-dimensional array
 * The following n are the sizes of the array dimensions
 */
function _nma(hub, ...sizes) {
    const componentHub = hub.$t["com.oracle.svm.core.hub.DynamicHub"].$f["componentType"];
    if (sizes.length === 1) {
        return _aH(sizes[0], componentHub);
    } else {
        const arr = new Array(sizes[0]);
        arr.hub = hub;

        for (let i = 0; i < sizes[0]; i++) {
            arr[i] = _nma(componentHub, ...sizes.slice(1));
        }

        return arr;
    }
}
