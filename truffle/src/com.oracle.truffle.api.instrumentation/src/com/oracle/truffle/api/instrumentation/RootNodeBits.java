/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import com.oracle.truffle.api.nodes.RootNode;

final class RootNodeBits {

    private static final int INITIALIZED = 1;
    private static final int SAME_SOURCE = 1 << 1;
    private static final int NO_SOURCE_SECTION = 1 << 2;
    private static final int SOURCE_SECTION_HIERARCHICAL = 1 << 3;
    private static final int NOT_EXECUTED = 1 << 4;
    private static final int ALL = INITIALIZED | SAME_SOURCE | NO_SOURCE_SECTION | SOURCE_SECTION_HIERARCHICAL | NOT_EXECUTED;

    /**
     * Returns true from the point just before the root is executed onwards. This is not guaranteed!
     * The root bits are not always initialized and the not-executed bit is not always unset before
     * the first execution of the root node. It is manipulated by the instrumentation handler only
     * when certain additional conditions hold. The application logic must ensure that this method
     * is relied upon only at places where this root node bit is properly initialized.
     */
    static boolean wasExecuted(int bits) {
        return bits > 0 && (bits & NOT_EXECUTED) == 0;
    }

    static boolean wasNotExecuted(int bits) {
        return (bits & NOT_EXECUTED) > 0;
    }

    /**
     * Returns true if source the source sections of the root node are all contained within the
     * bounds of the root source section.
     *
     */
    static boolean isSourceSectionsHierachical(int bits) {
        return (bits & SOURCE_SECTION_HIERARCHICAL) > 0;
    }

    /**
     * Returns true if the same source is used for the whole root node.
     */
    static boolean isSameSource(int bits) {
        return (bits & SAME_SOURCE) > 0;
    }

    /**
     * Returns true if there is no source section available in the whole RootNode.
     */
    static boolean isNoSourceSection(int bits) {
        return (bits & NO_SOURCE_SECTION) > 0;
    }

    static int setSourceSectionsUnstructured(int bits) {
        return bits & ~SOURCE_SECTION_HIERARCHICAL;
    }

    static int setHasDifferentSource(int bits) {
        return bits & ~SAME_SOURCE;
    }

    static int setHasSourceSection(int bits) {
        return bits & ~NO_SOURCE_SECTION;
    }

    static int setExecuted(int bits) {
        return bits & ~NOT_EXECUTED;
    }

    static int get(RootNode root) {
        return InstrumentAccessor.nodesAccess().getRootNodeBits(root);
    }

    static void set(RootNode root, int bits) {
        InstrumentAccessor.nodesAccess().setRootNodeBits(root, bits);
    }

    static boolean isUninitialized(int bits) {
        return bits == 0;
    }

    static int getAll() {
        return ALL;
    }
}
