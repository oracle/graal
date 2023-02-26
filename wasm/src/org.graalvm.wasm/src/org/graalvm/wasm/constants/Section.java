/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.constants;

public final class Section {
    public static final int CUSTOM = 0;
    public static final int TYPE = 1;
    public static final int IMPORT = 2;
    public static final int FUNCTION = 3;
    public static final int TABLE = 4;
    public static final int MEMORY = 5;
    public static final int GLOBAL = 6;
    public static final int EXPORT = 7;
    public static final int START = 8;
    public static final int ELEMENT = 9;
    public static final int CODE = 10;
    public static final int DATA = 11;
    public static final int DATA_COUNT = 12;

    private static final int[] SECTION_ORDER = new int[13];
    public static final int LAST_SECTION_ID = SECTION_ORDER.length - 1;

    static {
        SECTION_ORDER[CUSTOM] = 0;
        SECTION_ORDER[TYPE] = 1;
        SECTION_ORDER[IMPORT] = 2;
        SECTION_ORDER[FUNCTION] = 3;
        SECTION_ORDER[TABLE] = 4;
        SECTION_ORDER[MEMORY] = 5;
        SECTION_ORDER[GLOBAL] = 6;
        SECTION_ORDER[EXPORT] = 7;
        SECTION_ORDER[START] = 8;
        SECTION_ORDER[ELEMENT] = 9;
        SECTION_ORDER[DATA_COUNT] = 10;
        SECTION_ORDER[CODE] = 11;
        SECTION_ORDER[DATA] = 12;
    }

    private Section() {
    }

    public static boolean isNextSectionOrderValid(int sectionID, int lastSectionID) {
        // Undefined section ids and custom section ids are seen as valid and will be handled later
        return Integer.compareUnsigned(sectionID, SECTION_ORDER.length) >= 0 || SECTION_ORDER[sectionID] > SECTION_ORDER[lastSectionID];
    }
}
