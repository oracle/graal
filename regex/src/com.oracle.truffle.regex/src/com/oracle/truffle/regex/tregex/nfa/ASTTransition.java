/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nfa;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.tregex.automaton.AbstractTransition;
import com.oracle.truffle.regex.tregex.automaton.TransitionConstraint;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import com.oracle.truffle.regex.util.TBitSet;

public class ASTTransition implements AbstractTransition<Term, ASTTransition>, JsonConvertible {

    private Term target;
    private GroupBoundaries groupBoundaries;
    private TBitSet matchedConditionGroups;
    private final long[] constraints;
    private final long[] operations;

    public ASTTransition(RegexLanguage language, long[] constraints, long[] operations) {
        this.groupBoundaries = GroupBoundaries.getEmptyInstance(language);
        this.constraints = constraints;
        this.operations = operations;
    }

    public ASTTransition(RegexLanguage language, Term target, long[] constraints, long[] operations) {
        this.target = target;
        this.groupBoundaries = GroupBoundaries.getEmptyInstance(language);
        this.constraints = constraints;
        this.operations = operations;
    }

    @TruffleBoundary
    @Override
    public int getId() {
        throw new UnsupportedOperationException();
    }

    @TruffleBoundary
    @Override
    public Term getSource() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Term getTarget() {
        return target;
    }

    public void setTarget(Term target) {
        this.target = target;
    }

    public GroupBoundaries getGroupBoundaries() {
        return groupBoundaries;
    }

    public void setGroupBoundaries(GroupBoundaries groupBoundaries) {
        this.groupBoundaries = groupBoundaries;
    }

    public TBitSet getMatchedConditionGroups() {
        return matchedConditionGroups;
    }

    public void setMatchedConditionGroups(TBitSet matchedConditionGroups) {
        this.matchedConditionGroups = matchedConditionGroups;
    }

    public long[] getConstraints() {
        return constraints;
    }

    public long[] getOperations() {
        return operations;
    }

    @Override
    public int hashCode() {
        return target.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ASTTransition && target == ((ASTTransition) obj).target;
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("target", target.getId()),
                        Json.prop("groupBoundaries", groupBoundaries),
                        Json.prop("guards", TransitionConstraint.combineToJson(constraints, operations)));
    }
}
