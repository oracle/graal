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
 * Runs the main entry point for the WasmLM backend.
 *
 * The arguments (array of JS strings) is passed to Wasm by allocating Wasm
 * memory for the characters and passing pointers to the main method, which
 * constructs Java strings from the data.
 *
 * Concretely, we allocate two buffers. The first contains 32-bit signed
 * integers, one for each string encoding the string length (number of UTF-16
 * code units). The second contains the characters of all strings stitched
 * together; i.e. all UTF-16 code units as unsigned 16-bit integers.
 */
function doRun(args) {
    const exports = getExports();
    const memory = exports.memory;
    // TODO GR-42105 The conversion from/to BigInt is not needed with 4-byte pointers
    const malloc = (size) => Number(exports.malloc(BigInt(size)));
    const free = (ptr) => exports.free(BigInt(ptr));

    const numArgs = args.length;
    let numChars = 0;

    const lengthsPointer = malloc(4 * numArgs);
    try {
        const lengthsView = new DataView(memory.buffer, lengthsPointer, 4 * numArgs);

        for (let i = 0; i < numArgs; i++) {
            const argLength = args[i].length;
            lengthsView.setInt32(4 * i, argLength, true);
            numChars += argLength;
        }

        const charsPointer = malloc(2 * numChars);

        try {
            const charsView = new DataView(memory.buffer, charsPointer, 2 * numChars);

            let base = 0;
            for (let i = 0; i < numArgs; i++) {
                const arg = args[i];
                const argLength = arg.length;
                for (let j = 0; j < argLength; j++) {
                    charsView.setUint16(2 * (base + j), arg.charCodeAt(j), true);
                }
                base += argLength;
            }
            // TODO GR-42105 The conversion from/to BigInt is not needed with 4-byte pointers
            exports.main(args.length, BigInt(lengthsPointer), BigInt(charsPointer));
        } finally {
            free(charsPointer);
        }
    } finally {
        free(lengthsPointer);
    }
}
