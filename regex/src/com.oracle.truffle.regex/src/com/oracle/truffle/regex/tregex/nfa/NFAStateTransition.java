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
package com.oracle.truffle.regex.tregex.nfa;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.tregex.automaton.AbstractTransition;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * Provides information about a transition from one NFAState to another state.
 */
public final class NFAStateTransition implements AbstractTransition<NFAState, NFAStateTransition>, JsonConvertible {

    private final short id;
    @CompilationFinal private NFAState source;
    private final NFAState target;
    private final CodePointSet codePointSet;
    private final GroupBoundaries groupBoundaries;

    public NFAStateTransition(short id, NFAState source, NFAState target, CodePointSet codePointSet, GroupBoundaries groupBoundaries) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.codePointSet = codePointSet;
        this.groupBoundaries = groupBoundaries;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public NFAState getSource() {
        return source;
    }

    public void setSource(NFAState source) {
        this.source = source;
    }

    @Override
    public NFAState getTarget() {
        return target;
    }

    public NFAState getSource(boolean forward) {
        return forward ? source : target;
    }

    public CodePointSet getCodePointSet() {
        return codePointSet;
    }

    /**
     * groups entered and exited by this transition.
     */
    public GroupBoundaries getGroupBoundaries() {
        return groupBoundaries;
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("id", id),
                        Json.prop("source", source.getId()),
                        Json.prop("target", target.getId()),
                        Json.prop("matcherBuilder", codePointSet.toString()),
                        Json.prop("groupBoundaries", groupBoundaries),
                        Json.prop("sourceSections", groupBoundaries.indexUpdateSourceSectionsToJson(source.getStateSet().getStateIndex())));
    }

    @TruffleBoundary
    public JsonValue toJson(boolean forward) {
        return Json.obj(Json.prop("id", id),
                        Json.prop("source", getSource(forward).getId()),
                        Json.prop("target", getTarget(forward).getId()),
                        Json.prop("matcherBuilder", codePointSet.toString()),
                        Json.prop("groupBoundaries", groupBoundaries),
                        Json.prop("sourceSections", groupBoundaries.indexUpdateSourceSectionsToJson(source.getStateSet().getStateIndex())));
    }
}
