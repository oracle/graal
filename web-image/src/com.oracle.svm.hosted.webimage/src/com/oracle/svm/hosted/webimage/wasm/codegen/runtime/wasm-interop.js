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
 * Creates a Java string from a JavaScript string.
 *
 * The returned value is a pointer into the WasmLM linear memory. As long as
 * JavaScript code is holding this pointer, no Java code must run because it
 * could trigger a GC, which will free this String (since it's not reachable
 * from Java code currently).
 * See `WasmLMStringSupport` for more information.
 */
function toJavaString(jsStr) {
    const numChars = jsStr.length;
    const byteLength = 2 * numChars;

    const charPointer = Number(getExport("string.prepare")(numChars));
    const charView = new DataView(getExport("memory").buffer, charPointer, byteLength);

    for (let i = 0; i < numChars; i++) {
        charView.setUint16(2 * i, jsStr.charCodeAt(i), true);
    }

    const javaString = getExport("string.finish")(numChars, BigInt(charPointer));

    return javaString;
}
