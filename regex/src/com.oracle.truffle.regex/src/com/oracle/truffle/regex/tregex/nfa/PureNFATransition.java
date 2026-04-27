/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.automaton.AbstractTransition;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * Represents a transition of a {@link PureNFA}.
 */
public final class PureNFATransition implements AbstractTransition<PureNFAState, PureNFATransition> {

    private final int id;
    private static final int FLAG_CARET_GUARD = 1;
    private static final int FLAG_DOLLAR_GUARD = 1 << 1;
    private static final int FLAG_MATCH_BEGIN_GUARD = 1 << 2;
    private static final int FLAG_MATCH_END_GUARD = 1 << 3;

    private final PureNFAState source;
    private final PureNFAState target;
    private final GroupBoundaries groupBoundaries;
    private final int flags;
    @CompilationFinal(dimensions = 1) private final long[] guards;

    public PureNFATransition(int id,
                    PureNFAState source,
                    PureNFAState target,
                    GroupBoundaries groupBoundaries,
                    boolean caretGuard,
                    boolean dollarGuard,
                    boolean matchBeginGuard,
                    boolean matchEndGuard,
                    long[] guards) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.groupBoundaries = groupBoundaries;
        this.flags = createFlags(caretGuard, dollarGuard, matchBeginGuard, matchEndGuard);
        this.guards = guards;
    }

    private static int createFlags(boolean caretGuard, boolean dollarGuard, boolean matchBeginGuard, boolean matchEndGuard) {
        int ret = 0;
        ret = setFlagIf(ret, FLAG_CARET_GUARD, caretGuard);
        ret = setFlagIf(ret, FLAG_DOLLAR_GUARD, dollarGuard);
        ret = setFlagIf(ret, FLAG_MATCH_BEGIN_GUARD, matchBeginGuard);
        ret = setFlagIf(ret, FLAG_MATCH_END_GUARD, matchEndGuard);
        return ret;
    }

    private static int setFlagIf(int flags, int flag, boolean value) {
        return value ? flags | flag : flags;
    }

    private static boolean isFlagSet(int flags, int flag) {
        return (flags & flag) != 0;
    }

    @Override
    public int getId() {
        return id;
    }

    public int getFlags() {
        return flags;
    }

    @Override
    public PureNFAState getSource() {
        return source;
    }

    @Override
    public PureNFAState getTarget() {
        return target;
    }

    /**
     * Capture group boundaries traversed by this transition.
     */
    public GroupBoundaries getGroupBoundaries() {
        return groupBoundaries;
    }

    /**
     * Transition is guarded by the "^" - {@link PositionAssertion}.
     */
    public boolean hasCaretGuard() {
        return hasCaretGuard(flags);
    }

    public static boolean hasCaretGuard(int flags) {
        return isFlagSet(flags, FLAG_CARET_GUARD);
    }

    /**
     * Transition is guarded by the "$" - {@link PositionAssertion}.
     */
    public boolean hasDollarGuard() {
        return hasDollarGuard(flags);
    }

    public static boolean hasDollarGuard(int flags) {
        return isFlagSet(flags, FLAG_DOLLAR_GUARD);
    }

    /**
     * Transition is guarded by the "MATCH_BEGIN" - {@link PositionAssertion}.
     */
    public boolean hasMatchBeginGuard() {
        return hasMatchBeginGuard(flags);
    }

    public static boolean hasMatchBeginGuard(int flags) {
        return isFlagSet(flags, FLAG_MATCH_BEGIN_GUARD);
    }

    /**
     * Transition is guarded by the "MATCH_END" - {@link PositionAssertion}.
     */
    public boolean hasMatchEndGuard() {
        return hasMatchEndGuard(flags);
    }

    public static boolean hasMatchEndGuard(int flags) {
        return isFlagSet(flags, FLAG_MATCH_END_GUARD);
    }

    public long[] getGuards() {
        return guards;
    }

    @TruffleBoundary
    public JsonValue toJson(RegexAST ast) {
        return Json.obj(Json.prop("id", id),
                        Json.prop("source", source.getId()),
                        Json.prop("target", target.getId()),
                        Json.prop("groupBoundaries", groupBoundaries),
                        Json.prop("sourceSections", groupBoundaries.indexUpdateSourceSectionsToJson(ast)),
                        Json.prop("guards", guards.length == 0 ? Json.array(Json.val("no guards")) : Json.array(Arrays.stream(guards).mapToObj(TransitionGuard::toJson))));
    }
}
