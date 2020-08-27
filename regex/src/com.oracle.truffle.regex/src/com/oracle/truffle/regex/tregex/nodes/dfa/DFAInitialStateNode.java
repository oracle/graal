/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes.dfa;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * This state node is responsible for selecting a DFA's initial state based on the index the search
 * starts from. Successors are entry points in case we start matching at the beginning of the input
 * string, followed by entry points in case we do not start matching at the beginning of the input
 * string. If possible matches must start at the beginning of the input string, entry points may be
 * -1.
 */
public class DFAInitialStateNode extends DFAAbstractStateNode {

    private final boolean searching;
    private final boolean genericCG;

    public DFAInitialStateNode(short[] successors, boolean searching, boolean genericCG) {
        super((short) 0, successors);
        this.searching = searching;
        this.genericCG = genericCG;
    }

    private DFAInitialStateNode(DFAInitialStateNode copy) {
        this(Arrays.copyOf(copy.successors, copy.successors.length), copy.searching, copy.genericCG);
    }

    public int getPrefixLength() {
        return (successors.length / 2) - 1;
    }

    public boolean hasUnAnchoredEntry() {
        return successors[successors.length / 2] != -1;
    }

    /**
     * Creates a node split copy of this initial state as described in {@link DFAAbstractStateNode},
     * but ignores copyID, since having two initial states in a DFA is not supported. Therefore,
     * this method should be used for replacing the original initial state with the copy.
     *
     * @param copyID new ID for the copy.
     * @return a node split copy of this initial state as described in {@link DFAAbstractStateNode},
     *         ignoring copyID.
     */
    @Override
    public DFAAbstractStateNode createNodeSplitCopy(short copyID) {
        return new DFAInitialStateNode(this);
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj();
    }
}
