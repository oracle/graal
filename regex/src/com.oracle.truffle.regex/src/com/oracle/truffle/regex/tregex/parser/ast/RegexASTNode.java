/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.ast;

import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.automaton.IndexedState;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.MarkLookBehindEntriesVisitor;
import com.oracle.truffle.regex.tregex.util.DebugUtil;

public abstract class RegexASTNode implements IndexedState {

    private static final byte FLAG_PREFIX = 1;
    private static final byte FLAG_DEAD = 1 << 1;
    private static final byte FLAG_CARET = 1 << 2;
    private static final byte FLAG_DOLLAR = 1 << 3;
    protected static final byte FLAG_GROUP_LOOP = 1 << 4;
    protected static final byte FLAG_GROUP_EXPANDED_QUANTIFIER = 1 << 5;
    protected static final byte FLAG_LOOK_AHEAD_NEGATED = 1 << 6;
    protected static final byte FLAG_EMPTY_GUARD = (byte) (1 << 7);

    private short id = -1;
    private RegexASTNode parent;
    private byte flags;
    private short minPath = 0;

    protected RegexASTNode() {
    }

    protected RegexASTNode(RegexASTNode copy) {
        flags = copy.flags;
        minPath = copy.minPath;
    }

    /**
     * Recursively copy this subtree. This method should be used instead of
     * {@link com.oracle.truffle.regex.tregex.parser.ast.visitors.CopyVisitor} if the copying
     * process is required to be thread-safe.
     * 
     * @param ast RegexAST the new subtree should belong to.
     * @return A deep copy of this subtree.
     */
    public abstract RegexASTNode copy(RegexAST ast);

    public boolean idInitialized() {
        return id >= 0;
    }

    @Override
    public short getId() {
        assert idInitialized();
        return id;
    }

    public void setId(int id) {
        assert !idInitialized();
        assert id <= TRegexOptions.TRegexMaxParseTreeSize;
        this.id = (short) id;
    }

    /**
     * Gets the syntactic parent of this AST node.
     */
    public RegexASTNode getParent() {
        return parent;
    }

    /**
     * Sets the syntactic parent of this AST node.
     * 
     * @param parent
     */
    public void setParent(RegexASTNode parent) {
        this.parent = parent;
    }

    protected boolean isFlagSet(byte flag) {
        return (flags & flag) != 0;
    }

    protected void setFlag(byte flag) {
        setFlag(flag, true);
    }

    protected void setFlag(byte flag, boolean value) {
        if (value) {
            flags |= flag;
        } else {
            flags &= ~flag;
        }
    }

    /**
     * Marks the node as dead, i.e. unmatchable.
     *
     * @see Term#markAsDead()
     * @see Group#markAsDead()
     * @see Sequence#markAsDead()
     * @see RegexASTSubtreeRootNode#markAsDead()
     */
    public void markAsDead() {
        setFlag(FLAG_DEAD);
    }

    /**
     * Returns whether the node is dead, i.e. unmatchable.
     */
    public boolean isDead() {
        return isFlagSet(FLAG_DEAD);
    }

    /**
     * This flag marks all nodes that were inserted into the AST for look-behind matching, see
     * {@link MarkLookBehindEntriesVisitor} and {@link RegexAST#createPrefix()}.
     * 
     * @return true if this node belongs to an inserted prefix construct, otherwise false.
     */
    public boolean isPrefix() {
        return isFlagSet(FLAG_PREFIX);
    }

    /**
     * Sets the {@link #isPrefix} flag to true.
     * 
     * @see #isPrefix()
     */
    public void setPrefix() {
        setFlag(FLAG_PREFIX);
    }

    public boolean startsWithCaret() {
        return isFlagSet(FLAG_CARET);
    }

    public void setStartsWithCaret() {
        setStartsWithCaret(true);
    }

    public void setStartsWithCaret(boolean startsWithCaret) {
        setFlag(FLAG_CARET, startsWithCaret);
    }

    public boolean endsWithDollar() {
        return isFlagSet(FLAG_DOLLAR);
    }

    public void setEndsWithDollar() {
        setEndsWithDollar(true);
    }

    public void setEndsWithDollar(boolean endsWithDollar) {
        setFlag(FLAG_DOLLAR, endsWithDollar);
    }

    /**
     * Indicates whether or not this node should be allowed to match the empty string.
     * 
     * @return true if this node is <em>not</em> allowed to match the empty string
     */
    public boolean hasEmptyGuard() {
        return isFlagSet(FLAG_EMPTY_GUARD);
    }

    public void setEmptyGuard(boolean emptyGuard) {
        setFlag(FLAG_EMPTY_GUARD, emptyGuard);
    }

    public int getMinPath() {
        return minPath;
    }

    public void setMinPath(int n) {
        minPath = (short) n;
    }

    public void incMinPath() {
        incMinPath(1);
    }

    public void incMinPath(int n) {
        minPath += n;
    }

    /**
     * Returns the subtree root node that this node is a part of. If this node is nested inside
     * several look-around assertion nodes, returns the innermost one that contains this node. Every
     * AST node should have a subtree parent, but nodes implicitly generated by
     * {@link RegexAST#getNFAAnchoredInitialState(int)} and
     * {@link RegexAST#getNFAUnAnchoredInitialState(int)} technically don't belong to the AST, so
     * they will return {@code null}.
     */
    public abstract RegexASTSubtreeRootNode getSubTreeParent();

    public boolean isInLookBehindAssertion() {
        return getSubTreeParent() instanceof LookBehindAssertion;
    }

    public boolean isInLookAheadAssertion() {
        return getSubTreeParent() instanceof LookAheadAssertion;
    }

    public String toStringWithID() {
        return String.format("%d (%s)", id, toString());
    }

    protected static String astNodeId(RegexASTNode astNode) {
        return astNode == null ? "null" : String.valueOf(astNode.id);
    }

    public abstract DebugUtil.Table toTable();

    protected DebugUtil.Table toTable(String name) {
        return new DebugUtil.Table(name,
                        new DebugUtil.Value("id", id),
                        new DebugUtil.Value("parent", astNodeId(parent)),
                        new DebugUtil.Value("minPath", minPath),
                        new DebugUtil.Value("isPrefix", isPrefix()),
                        new DebugUtil.Value("isDead", isDead()));
    }
}
