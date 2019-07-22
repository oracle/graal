/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.automaton.IndexedState;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.CopyVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.MarkLookBehindEntriesVisitor;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public abstract class RegexASTNode implements IndexedState, JsonConvertible {

    private static final short FLAG_PREFIX = 1;
    private static final short FLAG_DEAD = 1 << 1;
    private static final short FLAG_CARET = 1 << 2;
    private static final short FLAG_DOLLAR = 1 << 3;
    protected static final short FLAG_GROUP_LOOP = 1 << 4;
    protected static final short FLAG_GROUP_EXPANDED_QUANTIFIER = 1 << 5;
    protected static final short FLAG_LOOK_AROUND_NEGATED = 1 << 6;
    protected static final short FLAG_EMPTY_GUARD = 1 << 7;
    protected static final short FLAG_HAS_LOOPS = 1 << 8;

    private short id = -1;
    private RegexASTNode parent;
    private short flags;
    private short minPath = 0;
    private short maxPath = 0;
    private SourceSection sourceSection;

    protected RegexASTNode() {
    }

    protected RegexASTNode(RegexASTNode copy) {
        flags = copy.flags;
        minPath = copy.minPath;
        sourceSection = copy.sourceSection;
    }

    /**
     * Copy this node, in one of the following ways:
     * <ul>
     * <li>if {@code recursive} is {@code true}, recursively copy this subtree. This method should
     * be used instead of {@link CopyVisitor} if the copying process is required to be thread-safe.
     * </li>
     * <li>else, copy this node only, without any child nodes.</li>
     * </ul>
     * In both cases, the ID and minPath of the copied nodes is left unset.
     *
     * @param ast RegexAST the new nodes should belong to.
     * @return A shallow or deep copy of this node.
     */
    public abstract RegexASTNode copy(RegexAST ast, boolean recursive);

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

    protected boolean isFlagSet(short flag) {
        return (flags & flag) != 0;
    }

    protected void setFlag(short flag) {
        setFlag(flag, true);
    }

    protected void setFlag(short flag, boolean value) {
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

    public void setHasLoops() {
        setHasLoops(true);
    }

    public void setHasLoops(boolean hasLoops) {
        setFlag(FLAG_HAS_LOOPS, hasLoops);
    }

    public boolean hasLoops() {
        return isFlagSet(FLAG_HAS_LOOPS);
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

    public int getMaxPath() {
        return maxPath;
    }

    public void setMaxPath(int n) {
        maxPath = (short) n;
    }

    public void incMaxPath() {
        incMaxPath(1);
    }

    public void incMaxPath(int n) {
        maxPath += n;
    }

    public void setSourceSection(SourceSection sourceSection) {
        this.sourceSection = sourceSection;
    }

    public SourceSection getSourceSection() {
        return sourceSection;
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

    protected static JsonValue astNodeId(RegexASTNode astNode) {
        return astNode == null ? Json.nullValue() : Json.val(astNode.id);
    }

    protected JsonObject toJson(String typeName) {
        return Json.obj(Json.prop("id", id),
                        Json.prop("type", typeName),
                        Json.prop("parent", astNodeId(parent)),
                        Json.prop("minPath", minPath),
                        Json.prop("isPrefix", isPrefix()),
                        Json.prop("isDead", isDead()));
    }
}
