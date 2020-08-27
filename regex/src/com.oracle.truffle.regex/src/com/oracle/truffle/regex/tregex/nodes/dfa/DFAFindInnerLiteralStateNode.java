/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.regex.tregex.nodes.input.InputIndexOfStringNode;
import com.oracle.truffle.regex.tregex.parser.ast.InnerLiteral;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public final class DFAFindInnerLiteralStateNode extends DFAAbstractStateNode {

    private final InnerLiteral innerLiteral;
    @Child private InputIndexOfStringNode indexOfNode = InputIndexOfStringNode.create();
    @Child private TRegexDFAExecutorNode prefixMatcher;

    public DFAFindInnerLiteralStateNode(short id, short[] successors, InnerLiteral innerLiteral, TRegexDFAExecutorNode prefixMatcher) {
        super(id, successors);
        assert successors.length == 1;
        this.innerLiteral = innerLiteral;
        this.prefixMatcher = prefixMatcher;
    }

    public InnerLiteral getInnerLiteral() {
        return innerLiteral;
    }

    @Override
    public DFAAbstractStateNode createNodeSplitCopy(short copyID) {
        return new DFAFindInnerLiteralStateNode(copyID, Arrays.copyOf(getSuccessors(), getSuccessors().length), innerLiteral, prefixMatcher);
    }

    public boolean hasPrefixMatcher() {
        return prefixMatcher != null;
    }

    int executeInnerLiteralSearch(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        return indexOfNode.execute(locals.getInput(), locals.getIndex(), executor.getMaxIndex(locals), innerLiteral.getLiteral().content(), innerLiteral.getMaskContent());
    }

    boolean prefixMatcherMatches(TRegexDFAExecutorLocals locals, boolean compactString) {
        Object result = prefixMatcher.execute(locals.toInnerLiteralBackwardLocals(), compactString);
        return prefixMatcher.isSimpleCG() ? result != null : (int) result != TRegexDFAExecutorNode.NO_MATCH;
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("id", getId()),
                        Json.prop("anchoredFinalState", false),
                        Json.prop("finalState", false),
                        Json.prop("loopToSelf", false),
                        Json.prop("transitions", Json.array(Json.obj(Json.prop("matcher", "innerLiteral(" + innerLiteral.getLiteral() + ")"), Json.prop("target", successors[0])))));
    }
}
