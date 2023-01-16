/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * Conditional back-reference groups represent the following construct found, e.g., in Python:
 * <p>
 * {@code (?(group_name)then_branch|else_branch}
 * <p>
 * Every conditional back-reference group has exactly 2 alternatives. The first alternative can only
 * be taken if the referenced group was matched and the second alternative can be taken only if the
 * referenced group was *not* matched.
 */
public final class ConditionalBackReferenceGroup extends Group {

    private final int referencedGroupNumber;

    /**
     * Creates an empty conditional back-reference group. It should later be filled with exactly 2
     * {@link Sequence}s as its alternatives.
     * 
     * @param referencedGroupNumber The number of the capture group referenced in the condition.
     */
    public ConditionalBackReferenceGroup(int referencedGroupNumber) {
        this.referencedGroupNumber = referencedGroupNumber;
    }

    private ConditionalBackReferenceGroup(ConditionalBackReferenceGroup copy) {
        super(copy);
        referencedGroupNumber = copy.referencedGroupNumber;
    }

    /**
     * Returns the index of the capture group that is referenced by this conditional expression.
     */
    public int getReferencedGroupNumber() {
        return referencedGroupNumber;
    }

    @Override
    public ConditionalBackReferenceGroup copy(RegexAST ast) {
        return ast.register(new ConditionalBackReferenceGroup(this));
    }

    @Override
    public ConditionalBackReferenceGroup copyRecursive(RegexAST ast, CompilationBuffer compilationBuffer) {
        ConditionalBackReferenceGroup copy = copy(ast);
        for (Sequence s : getAlternatives()) {
            copy.add(s.copyRecursive(ast, compilationBuffer));
        }
        return copy;
    }

    @Override
    public boolean equalsSemantic(RegexASTNode obj, boolean ignoreQuantifier) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof ConditionalBackReferenceGroup)) {
            return false;
        }
        ConditionalBackReferenceGroup o = (ConditionalBackReferenceGroup) obj;
        assert size() == 2 && o.size() == 2;
        assert getGroupNumber() == -1 && o.getGroupNumber() == -1;
        if (referencedGroupNumber != o.referencedGroupNumber || isLoop() != o.isLoop() || (!ignoreQuantifier && !quantifierEquals(o))) {
            return false;
        }
        for (int i = 0; i < size(); i++) {
            if (!getAlternatives().get(i).equalsSemantic(o.getAlternatives().get(i))) {
                return false;
            }
        }
        return true;
    }

    @CompilerDirectives.TruffleBoundary
    @Override
    public String toString() {
        return "(?(" + referencedGroupNumber + ")" + alternativesToString() + ")" + loopToString();
    }

    @CompilerDirectives.TruffleBoundary
    @Override
    public JsonValue toJson() {
        return super.toJson("ConditionalBackReferenceGroup").append(
                        Json.prop("referencedGroupNumber", referencedGroupNumber),
                        Json.prop("isLoop", isLoop()),
                        Json.prop("isExpandedLoop", isExpandedQuantifier()),
                        Json.prop("alternatives", getAlternatives()));
    }
}
