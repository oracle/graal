/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.tregex.nodesplitter.DFANodeSplit;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;

public abstract class DFAAbstractStateNode extends Node implements JsonConvertible {

    static final int FS_RESULT_NO_SUCCESSOR = -1;

    @CompilationFinal(dimensions = 1) protected final short[] successors;

    DFAAbstractStateNode(short[] successors) {
        this.successors = successors;
    }

    /**
     * Creates a copy of this state node, where all attributes are copied shallowly, except for the
     * {@link #successors} array, which is deep-copied, and the node ID, which is replaced by the
     * parameter copyID. Used by {@link DFANodeSplit}.
     *
     * @param copyID new ID for the copy.
     * @return an "almost shallow" copy of this node.
     */
    public abstract DFAAbstractStateNode createNodeSplitCopy(short copyID);

    public abstract short getId();

    public final short[] getSuccessors() {
        return successors;
    }

    /**
     * Calculates this state's successor and returns its ID ({@link DFAStateNode#getId()}) via
     * {@link TRegexDFAExecutorLocals#setSuccessorIndex(int)}. This return value is called
     * "successor index" and may either be an index of the successors array (between 0 and
     * {@link #getSuccessors()}{@code .length}) or {@link #FS_RESULT_NO_SUCCESSOR}.
     *
     * @param locals a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param compactString
     */
    public abstract void executeFindSuccessor(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString);
}
