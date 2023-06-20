/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.wasm.debugging.data;

import org.graalvm.wasm.collection.IntArrayList;
import org.graalvm.wasm.debugging.parser.DebugParserContext;
import org.graalvm.wasm.debugging.parser.DebugData;
import org.graalvm.wasm.debugging.encoding.Attributes;

/**
 * Utility class used for resolving the data of a debug entry.
 */
public final class DebugDataUtil {
    private DebugDataUtil() {
    }

    /**
     * Extracts the pcs from the given data.
     * 
     * @param data the debug data.
     * @param context the current debug context.
     * @return the pc values int an int array or null, if the pcs are not present or malformed.
     */
    public static int[] readPcsOrNull(DebugData data, DebugParserContext context) {
        int lowPc = data.asI32OrDefault(Attributes.LOW_PC, -1);
        if (lowPc == -1) {
            return null;
        }
        int highPc = data.asI32OrDefault(Attributes.HIGH_PC, -1);
        if (highPc != -1) {
            if (data.isConstant(Attributes.HIGH_PC)) {
                highPc = lowPc + highPc;
            }
        } else {
            final int rangeOffset = data.asI32OrDefault(Attributes.RANGES, -1);
            if (rangeOffset == -1) {
                return null;
            }
            final IntArrayList ranges = context.readRangeSectionOrNull(rangeOffset);
            if (ranges == null || ranges.size() < 2) {
                return null;
            }
            lowPc = ranges.get(0);
            highPc = ranges.get(ranges.size() - 1);
        }
        return new int[]{lowPc, highPc};
    }
}
