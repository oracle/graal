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
package com.oracle.truffle.regex.tregex.nfa;

import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.parser.ast.LookAroundAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;

/**
 * Represents a transition of a {@link PureNFA}.
 */
public class PureNFATransition {

    private final short id;
    private final PureNFAState target;
    private final GroupBoundaries groupBoundaries;
    private final ASTNodeSet<RegexASTNode> traversedLookArounds;
    private final QuantifierGuard[] quantifierGuards;

    public PureNFATransition(short id, PureNFAState target, GroupBoundaries groupBoundaries, ASTNodeSet<RegexASTNode> traversedLookArounds, QuantifierGuard[] quantifierGuards) {
        this.id = id;
        this.target = target;
        this.groupBoundaries = groupBoundaries;
        this.traversedLookArounds = traversedLookArounds;
        this.quantifierGuards = quantifierGuards;
    }

    public short getId() {
        return id;
    }

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
     * Set of {@link LookAroundAssertion}s traversed by this transition. All
     * {@link LookAroundAssertion}s contained in this set must match in order for this transition to
     * be valid.<br>
     * Example: in the expression {@code /a(?=b)[a-z]/} , {@link #getTraversedLookArounds()} of the
     * transition from {@code a} to {@code [a-z]} will contain the look-ahead assertion
     * {@code (?=b)}, so the regex matcher must check {@code (?=b)} before continuing to
     * {@code [a-z]}.
     */
    public ASTNodeSet<RegexASTNode> getTraversedLookArounds() {
        return traversedLookArounds;
    }

    public QuantifierGuard[] getQuantifierGuards() {
        return quantifierGuards;
    }
}
