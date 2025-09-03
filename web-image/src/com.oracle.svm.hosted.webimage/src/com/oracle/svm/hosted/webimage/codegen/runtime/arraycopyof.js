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
function arraysCopyOfWithHub(orig, len, hub, defaultVal) {
    const newarray = new orig.constructor(len);
    newarray.hub = hub;
    // Up to len, but at most the entire original array has to be copied over
    const copylen = orig.length < len ? orig.length : len;

    // Copy over elements
    arrayCopy(orig, 0, newarray, 0, copylen);

    // Insert default values for remaining elements (if any)
    if (newarray instanceof BigInt64Array) {
        initComponentView(newarray);
        for (var i = copylen; i < len; i++) {
            bigInt64ArrayStore(newarray, i, defaultVal);
        }
    } else {
        for (var i = copylen; i < len; i++) {
            newarray[i] = defaultVal;
        }
    }
    return newarray;
}

function arraysCopyOf(orig, len, defaultVal) {
    return arraysCopyOfWithHub(orig, len, orig.hub, defaultVal);
}

function arrayCopy(src, sstart, dst, dstart, length) {
    if (
        ArrayBuffer.isView(src) &&
        !(src instanceof DataView) &&
        ArrayBuffer.isView(dst) &&
        !(dst instanceof DataView)
    ) {
        // src and dst are typed arrays - we can use faster methods that have same semantics as Java
        dst.set(src.subarray(sstart, sstart + length), dstart);
    } else {
        // special case see System.arraycopy spec if src===dst
        if (src === dst) {
            // slowpath
            if (length == 0) return;
            if (dstart < sstart) {
                // copy to the left
                for (var i = 0; i < length; i++) {
                    dst[dstart + i] = dst[sstart + i];
                }
            } else {
                // copy to the right
                for (var i = length - 1; i >= 0; i--) {
                    dst[dstart + i] = dst[sstart + i];
                }
            }
        } else {
            for (var i = 0; i < length; i++) {
                dst[dstart + i] = src[sstart + i];
            }
        }
    }
}
