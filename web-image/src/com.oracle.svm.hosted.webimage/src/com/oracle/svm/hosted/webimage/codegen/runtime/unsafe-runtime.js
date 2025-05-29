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
function unsafe_assert(b) {
    if (!b) {
        throw ShouldNotReachHere("Unsafe sanity fail");
    }
}

function atomic_read_and_write(object, offset, newValue) {
    if (object === undefined || offset === undefined || newValue === undefined) {
        throw ShouldNotReachHere("undefined");
    }
    if (object === null) {
        throw ShouldNotReachHere("Unsafe store is null");
    }
    let oldValue = unsafe_load_runtime(object, offset);
    unsafe_store_runtime(object, offset, newValue);
    return oldValue;
}

function atomic_read_and_add(object, offset, delta) {
    if (object === undefined || offset === undefined || delta === undefined) {
        throw ShouldNotReachHere("undefined");
    }
    if (object === null) {
        throw ShouldNotReachHere("Unsafe store is null");
    }
    let oldValue = unsafe_load_runtime(object, offset);
    unsafe_store_runtime(object, offset, oldValue + delta);
    return oldValue;
}

const NEUTRAL_VALUE = 0;
const PRIMITIVE_VALUE = NEUTRAL_VALUE + 1;
const INTERFACE_VALUE = PRIMITIVE_VALUE + 1;
const ABSTRACT_VALUE = INTERFACE_VALUE + 1;
const STORED_CONTINUATION_VALUE = ABSTRACT_VALUE + 1;
const LAST_SPECIAL_VALUE = STORED_CONTINUATION_VALUE;

const ARRAY_INDEX_SHIFT_SHIFT = 0;
const ARRAY_INDEX_SHIFT_MASK = 0xff;
const ARRAY_BASE_SHIFT = 8 + ARRAY_INDEX_SHIFT_SHIFT;
const ARRAY_BASE_MASK = 0xfff;
const ARRAY_TAG_BITS = 2;
const ARRAY_TAG_SHIFT = 32 - ARRAY_TAG_BITS;
const ARRAY_TAG_PRIMITIVE_VALUE = ~0x00;
const ARRAY_TAG_OBJECT_VALUE = ~0x01;

function getArrayBaseOffset(encoding) {
    return (encoding >> ARRAY_BASE_SHIFT) & ARRAY_BASE_MASK;
}

function getArrayIndexScale(encoding) {
    return $t["com.oracle.svm.core.hub.LayoutEncoding"].$m["getArrayIndexScale"](encoding);
}

function getLayoutEncoding(object) {
    const dynamic_hub = object.hub;
    // call in java field layout encoding
    return dynamic_hub.$t["com.oracle.svm.core.hub.DynamicHub"].$f["layoutEncoding"];
}

/**
 * Implement runtime contracts for storing data in raw arrays.
 *
 * Refer to the documentation of `unsafe_load_runtime`.
 *
 * @param {number=} type
 */
function unsafe_store_runtime(object, offset, value, type) {
    if (object === undefined || offset === undefined || value === undefined) {
        throw ShouldNotReachHere("undefined");
    }

    if (object === null) {
        throw ShouldNotReachHere("Unsafe store on null.");
    }

    if (Long64.instanceof(object) && Long64.equal(object, Long64.Zero())) {
        throw ShouldNotReachHere("Unsafe store on zero.");
    }

    let ooffset = offset;
    if (Long64.instanceof(offset)) {
        ooffset = Long64.lowBits(offset);
    }

    if (isArray(object)) {
        // array case
        const layout_encoding = getLayoutEncoding(object);
        let base_offset = getArrayBaseOffset(layout_encoding);

        // Anything before the base offset is a field table access that can be delegated to Object
        if (ooffset < base_offset) {
            $t["java.lang.Object"][CM].ft[ooffset](object, value);
        } else {
            let index_scale = getArrayIndexScale(layout_encoding);

            // sanity
            unsafe_assert(base_offset % 2 === 0);
            unsafe_assert(index_scale > 1 ? index_scale % 2 === 0 : true);

            const index = ((ooffset - base_offset) / index_scale) | 0;
            if (index >= object.length) {
                throw ShouldNotReachHere("Index " + index + " out of bounds for length " + object.length);
            }

            if (type >= 0 && ArrayBuffer.isView(object) && !(object instanceof DataView)) {
                const view = new DataView(object.buffer, ooffset - base_offset);
                switch (type) {
                    case 0:
                    case 1:
                        view.setInt8(0, value);
                        break;
                    case 2:
                        view.setUint16(0, value, runtime.isLittleEndian);
                        break;
                    case 3:
                        view.setInt16(0, value, runtime.isLittleEndian);
                        break;
                    case 4:
                        view.setInt32(0, value, runtime.isLittleEndian);
                        break;
                    case 5:
                        view.setFloat32(0, value, runtime.isLittleEndian);
                        break;
                    case 6:
                        const lo = Long64.lowBits(value);
                        const hi = Long64.highBits(value);
                        if (runtime.isLittleEndian) {
                            view.setInt32(0, lo, true);
                            view.setInt32(4, hi, true);
                        } else {
                            view.setInt32(0, hi, false);
                            view.setInt32(4, lo, false);
                        }
                        break;
                    case 7:
                        view.setFloat64(0, value, runtime.isLittleEndian);
                        break;
                    default:
                        throw ShouldNotReachHere("Unsafe array store of type ");
                }
            } else {
                object[index] = value;
            }
        }
    } else {
        // object case
        object.constructor[CM].ft[ooffset](object, value);
    }
}

/**
 * Implement runtime contracts for accessing raw arrays.
 *
 * Valid usage of the API comes from the runtime.
 *
 * The API also partly emulates the semantics of `Unsafe.getXXX`. Due to the fact that `Unsafe` is under-specified
 * and it is unsafe, the end users are discouraged from using `Unsafe`.
 *
 * The parameter type is optional. Its value is enum index as defined in jdk.vm.ci.meta.JavaKind.
 * The following is a summary:
 *
 *      0 - Boolean
 *      1 - Byte
 *      2 - Short
 *      3 - Char
 *      4 - Int
 *      5 - Float
 *      6 - Long
 *      7 - Double
 *      8 - Object
 *
 * @param {number=} type
 */
function unsafe_load_runtime(object, offset, type) {
    if (object === undefined || offset === undefined) {
        throw ShouldNotReachHere("undefined");
    }

    if (object == null) {
        throw ShouldNotReachHere("Unsafe load on null.");
    }

    if (Long64.instanceof(object) && Long64.equal(object, Long64.Zero())) {
        throw ShouldNotReachHere("Unsafe load on zero.");
    }

    let ooffset = offset;
    if (Long64.instanceof(offset)) {
        ooffset = Long64.lowBits(offset);
    }

    if (isArray(object)) {
        // array case
        const layout_encoding = getLayoutEncoding(object);
        let base_offset = getArrayBaseOffset(layout_encoding);

        // Anything before the base offset is a field table access that can be delegated to Object
        if (ooffset < base_offset) {
            return $t["java.lang.Object"][CM].ft[ooffset](object, undefined);
        } else {
            let index_scale = getArrayIndexScale(layout_encoding);

            unsafe_assert(base_offset % 2 === 0);
            unsafe_assert(index_scale > 1 ? index_scale % 2 === 0 : true);

            const index = ((ooffset - base_offset) / index_scale) | 0;
            if (index >= object.length) {
                throw ShouldNotReachHere("Index " + index + " out of bounds for length " + object.length);
            }

            if (type >= 0 && ArrayBuffer.isView(object) && !(object instanceof DataView)) {
                const view = new DataView(object.buffer, ooffset - base_offset);
                switch (type) {
                    case 0:
                    case 1:
                        return view.getInt8(0);
                    case 2:
                        return view.getUint16(0, runtime.isLittleEndian);
                    case 3:
                        return view.getInt16(0, runtime.isLittleEndian);
                    case 4:
                        return view.getInt32(0, runtime.isLittleEndian);
                    case 5:
                        return view.getFloat32(0, runtime.isLittleEndian);
                    case 6:
                        var low, high;
                        if (runtime.isLittleEndian) {
                            low = view.getInt32(0, true);
                            high = view.getInt32(4, true);
                        } else {
                            low = view.getInt32(4, false);
                            high = view.getInt32(0, false);
                        }
                        return Long64.fromTwoInt(low, high);
                    case 7:
                        return view.getFloat64(0, runtime.isLittleEndian);
                    default:
                        throw ShouldNotReachHere("Unsafe array load of type " + type);
                }
            } else {
                return object[index];
            }
        }
    } else {
        // object case
        let getter = object.constructor[CM].ft[ooffset];
        if (getter) {
            return getter(object, undefined);
        } else if (object instanceof $t["java.lang.Class"]) {
            // get a function pointer from the vtable
            const layout_encoding = getLayoutEncoding(object);
            let vtableOffset = getArrayBaseOffset(layout_encoding);
            let index_scale = getArrayIndexScale(layout_encoding);
            if (ooffset >= vtableOffset) {
                let vtableIndex = (ooffset - vtableOffset) / index_scale;
                let fun = object[runtime.symbol.jsClass].vt[vtableIndex];
                let funTabIndex = runtime.funtab.indexOf(fun);
                if (funTabIndex === -1) {
                    let newLength = runtime.funtab.push(fun);
                    funTabIndex = newLength - 1;
                }
                return Long64.fromInt(funTabIndex);
            }
        }
        ShouldNotReachHere("Invalid index in load_unsafe_runtime for " + object + " at offset " + ooffset);
    }
}

/**
 * "Atomically" swaps the value at the given offset of the given object with a
 * new value if the original value matches the expected value.
 *
 * Returns whether the original value matched the expected value and the swap occurred.
 */
function compare_and_swap_runtime(object, offset, expected, newvalue, type) {
    let actual = unsafe_load_runtime(object, offset, type);
    let equal;
    if (Long64.instanceof(expected)) {
        equal = Long64.equal(actual, expected);
    } else {
        equal = actual === expected;
    }
    if (equal) {
        unsafe_store_runtime(object, offset, newvalue, type);
        return true;
    }
    return false;
}

/**
 * Like compare_and_swap_runtime, but returns the original value.
 */
function compare_and_exchange_runtime(object, offset, expected, newvalue, type) {
    let actual = unsafe_load_runtime(object, offset, type);
    let equal;
    if (Long64.instanceof(expected)) {
        equal = Long64.equal(actual, expected);
    } else {
        equal = actual === expected;
    }
    if (equal) {
        unsafe_store_runtime(object, offset, newvalue, type);
    }
    return actual;
}
