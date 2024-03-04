/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.ast;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public class SubexpressionCall extends QuantifiableTerm {

    private final int groupNr;

    SubexpressionCall(int groupNr) {
        this.groupNr = groupNr;
    }

    private SubexpressionCall(SubexpressionCall copy) {
        super(copy);
        groupNr = copy.groupNr;
    }

    @Override
    public SubexpressionCall copy(RegexAST ast) {
        return ast.register(new SubexpressionCall(this));
    }

    @Override
    public SubexpressionCall copyRecursive(RegexAST ast, CompilationBuffer compilationBuffer) {
        return copy(ast);
    }

    @Override
    public Sequence getParent() {
        return (Sequence) super.getParent();
    }

    /**
     * Returns the capture group number this subexpression call is referring to, e.g. the referenced
     * group of {@code \g<1>} is 1.
     */
    public int getGroupNr() {
        return groupNr;
    }

    @Override
    public boolean isUnrollingCandidate() {
        return hasNotUnrolledQuantifier() && getQuantifier().isWithinThreshold(TRegexOptions.TRegexQuantifierUnrollThresholdGroup);
    }

    @Override
    public boolean equalsSemantic(RegexASTNode obj, boolean ignoreQuantifier) {
        return obj instanceof SubexpressionCall && ((SubexpressionCall) obj).groupNr == groupNr && (ignoreQuantifier || quantifierEquals((SubexpressionCall) obj));
    }

    @CompilerDirectives.TruffleBoundary
    @Override
    public String toString() {
        return "\\g<" + groupNr + ">" + quantifierToString();
    }

    @CompilerDirectives.TruffleBoundary
    @Override
    public JsonValue toJson() {
        return toJson("SubexpressionCall").append(Json.prop("groupNr", groupNr));
    }
}
