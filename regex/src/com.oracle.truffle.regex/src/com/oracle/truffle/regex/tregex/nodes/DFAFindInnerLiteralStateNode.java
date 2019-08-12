/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.nodes.input.InputIndexOfStringNode;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public final class DFAFindInnerLiteralStateNode extends DFAAbstractStateNode {

    private final String literal;
    private final String mask;
    @Child private InputIndexOfStringNode indexOfNode = InputIndexOfStringNode.create();
    @Child private TRegexDFAExecutorNode prefixMatcher;

    public DFAFindInnerLiteralStateNode(short id, short[] successors, String literal, String mask, TRegexDFAExecutorNode prefixMatcher) {
        super(id, successors);
        assert successors.length == 1;
        this.literal = literal;
        this.mask = mask;
        this.prefixMatcher = prefixMatcher;
    }

    @Override
    public DFAAbstractStateNode createNodeSplitCopy(short copyID) {
        return new DFAFindInnerLiteralStateNode(copyID, Arrays.copyOf(getSuccessors(), getSuccessors().length), literal, mask, prefixMatcher);
    }

    @Override
    public void executeFindSuccessor(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString) {
        while (true) {
            if (!executor.hasNext(locals)) {
                locals.setSuccessorIndex(FS_RESULT_NO_SUCCESSOR);
                return;
            }
            locals.setIndex(indexOfNode.execute(locals.getInput(), locals.getIndex(), locals.getCurMaxIndex(), literal, mask));
            if (locals.getIndex() < 0) {
                locals.setSuccessorIndex(FS_RESULT_NO_SUCCESSOR);
                return;
            }
            if ((prefixMatcher == null || (int) prefixMatcher.execute(locals.toBackwardLocals(executor.getPrefixLength()), compactString) != TRegexDFAExecutorNode.NO_MATCH)) {
                locals.setIndex(locals.getIndex() + literal.length());
                locals.setSuccessorIndex(0);
                return;
            }
            executor.advance(locals);
        }
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("id", getId()),
                        Json.prop("anchoredFinalState", false),
                        Json.prop("finalState", false),
                        Json.prop("loopToSelf", false),
                        Json.prop("transitions", Json.array(Json.obj(Json.prop("matcher", "innerLiteral(" + literal + ")"), Json.prop("target", successors[0])))));
    }

}
