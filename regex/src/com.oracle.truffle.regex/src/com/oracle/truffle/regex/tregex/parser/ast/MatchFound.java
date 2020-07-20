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
import com.oracle.truffle.regex.tregex.util.Exceptions;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * {@link MatchFound} nodes are {@link RegexASTNode}s that represent the initial/final states of the
 * non-deterministic finite state automaton generated from the regular expression.
 * <p>
 * Regular expressions are translated into non-deterministic finite state automata, with each
 * {@link RegexASTNode} in the {@link RegexAST} contributing some of the states or transitions. The
 * {@link MatchFound} nodes are those that contribute the final (accepting) states. The root group
 * of every regular expression is linked (using the 'next' pointer) to a single {@link MatchFound}
 * node. Other {@link MatchFound} nodes appear in look-behind and look-ahead assertions, where they
 * contribute the final states of their subautomata (look-around assertions generate subautomata
 * which are then joined with the root automaton using a product construction).
 * <p>
 * {@link MatchFound} nodes are also used as initial states (the initial states of the forward
 * search automaton are the final states of the reverse search automaton). Therefore, there is a set
 * of {@link MatchFound} nodes used as final states in forward search (reachable by 'next' pointers)
 * and as initial states in reverse search and a set of {@link MatchFound} nodes used as final
 * states in reverse search (reachable by 'prev' pointers) and as initial states in forward search.
 * {@link MatchFound} being used as NFA initial states is also why they can have a next-pointer (
 * {@link #getNext()}) themselves (see {@link RegexAST#getNFAUnAnchoredInitialState(int)}).
 */
public class MatchFound extends Term {

    private RegexASTNode next;

    @Override
    public MatchFound copy(RegexAST ast) {
        throw Exceptions.shouldNotReachHere();
    }

    @Override
    public MatchFound copyRecursive(RegexAST ast, CompilationBuffer compilationBuffer) {
        throw Exceptions.shouldNotReachHere();
    }

    public RegexASTNode getNext() {
        return next;
    }

    public void setNext(RegexASTNode next) {
        this.next = next;
    }

    @Override
    public boolean equalsSemantic(RegexASTNode obj) {
        return obj instanceof MatchFound;
    }

    @Override
    public String toString() {
        return "::";
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return toJson("MatchFound");
    }
}
