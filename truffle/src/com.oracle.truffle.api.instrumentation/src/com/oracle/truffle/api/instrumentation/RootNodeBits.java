/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import com.oracle.truffle.api.instrumentation.InstrumentationHandler.AccessorInstrumentHandler;
import com.oracle.truffle.api.nodes.RootNode;

final class RootNodeBits {

    private static final int INITIALIZED = 1;
    private static final int SAME_SOURCE = 1 << 1;
    private static final int NO_SOURCE_SECTION = 1 << 2;
    private static final int SOURCE_SECTION_HIERARCHICAL = 1 << 3;
    private static final int ALL = INITIALIZED | SAME_SOURCE | NO_SOURCE_SECTION | SOURCE_SECTION_HIERARCHICAL;

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
        return bits & ~NO_SOURCE_SECTION;
    }

    static int setHasSourceSection(int bits) {
        return bits & ~NO_SOURCE_SECTION;
    }

    static int get(RootNode root) {
        return AccessorInstrumentHandler.nodesAccess().getRootNodeBits(root);
    }

    static void set(RootNode root, int bits) {
        AccessorInstrumentHandler.nodesAccess().setRootNodeBits(root, bits);
    }

    static boolean isUninitialized(int bits) {
        return bits == 0;
    }

    static int getAll() {
        return ALL;
    }
}
