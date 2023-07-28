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
package com.oracle.truffle.regex.tregex.parser.ast;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.Arrays;

/**
 * A reference to the contents of a previously matched capturing group.
 * <p>
 * Corresponds to the goal symbol <em>DecimalEscape</em> in the ECMAScript RegExp syntax.
 * <p>
 * Currently not implemented in TRegex and so any use of this node type causes TRegex to bail out.
 */
public class BackReference extends QuantifiableTerm {

    private final int[] groupNumbers;

    BackReference(int[] referencedGroupNumbers) {
        this.groupNumbers = referencedGroupNumbers;
    }

    private BackReference(BackReference copy) {
        super(copy);
        groupNumbers = copy.groupNumbers;
    }

    @Override
    public BackReference copy(RegexAST ast) {
        return ast.register(new BackReference(this));
    }

    @Override
    public BackReference copyRecursive(RegexAST ast, CompilationBuffer compilationBuffer) {
        return copy(ast);
    }

    /**
     * Returns the capture group numbers this back-reference is referring to, e.g. the referenced
     * groups of {@code \1} is [1] and the references groups of {@code \k<x>} in
     * {@code (?:(?<x>a|?<x>b))\k<x>} is [1, 2].
     */
    public int[] getGroupNumbers() {
        return groupNumbers;
    }

    /**
     * Returns {@code true} iff this back-reference refers to its own parent group. In order for
     * this to be {@code true}, all of the target group numbers must be nested references.
     */
    public boolean isNestedBackReference() {
        return isFlagSet(FLAG_BACK_REFERENCE_IS_NESTED);
    }

    public void setNestedBackReference() {
        setFlag(FLAG_BACK_REFERENCE_IS_NESTED, true);
    }

    /**
     * Returns {@code true} iff this "back-reference" is actually a reference to a later group in
     * the expression. In order for this to be {@code true}, all of the target group numbers must be
     * forward references.
     */
    public boolean isForwardReference() {
        return isFlagSet(FLAG_BACK_REFERENCE_IS_FORWARD);
    }

    public void setForwardReference() {
        setFlag(FLAG_BACK_REFERENCE_IS_FORWARD, true);
    }

    /**
     * Returns {@code true} iff this "back-reference" is actually a reference to its own parent
     * group or a later group in the expression. In order for this to be {@code true}, all of the
     * target group numbers must either be referenced or nested references. In JavaScript, such
     * nested/forward references will always match the empty string.
     */
    public boolean isNestedOrForwardReference() {
        return isFlagSet(FLAG_BACK_REFERENCE_IS_NESTED | FLAG_BACK_REFERENCE_IS_FORWARD | FLAG_BACK_REFERENCE_IS_NESTED_OR_FORWARD);
    }

    public void setNestedOrForwardReference() {
        setFlag(FLAG_BACK_REFERENCE_IS_NESTED_OR_FORWARD, true);
    }

    public boolean isIgnoreCaseReference() {
        return isFlagSet(FLAG_BACK_REFERENCE_IS_IGNORE_CASE);
    }

    public void setIgnoreCaseReference() {
        setFlag(FLAG_BACK_REFERENCE_IS_IGNORE_CASE, true);
    }

    @Override
    public boolean isUnrollingCandidate() {
        return hasQuantifier() && getQuantifier().isUnrollTrivial();
    }

    @Override
    public boolean equalsSemantic(RegexASTNode obj, boolean ignoreQuantifier) {
        return obj instanceof BackReference && Arrays.equals(((BackReference) obj).groupNumbers, groupNumbers) && (ignoreQuantifier || quantifierEquals((BackReference) obj));
    }

    @TruffleBoundary
    @Override
    public String toString() {
        if (groupNumbers.length == 1) {
            return "\\" + groupNumbers[0] + quantifierToString();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("\\k<");
            sb.append(groupNumbers[0]);
            for (int i = 1; i < groupNumbers.length; i++) {
                sb.append(",");
                sb.append(groupNumbers[i]);
            }
            sb.append(">");
            sb.append(quantifierToString());
            return sb.toString();
        }
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return toJson("BackReference").append(Json.prop("groupNumbers", Arrays.stream(groupNumbers).mapToObj(x -> Json.val(x))));
    }
}
