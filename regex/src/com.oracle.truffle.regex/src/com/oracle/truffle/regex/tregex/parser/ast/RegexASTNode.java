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
package com.oracle.truffle.regex.tregex.parser.ast;

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
    protected static final short FLAG_CHARACTER_CLASS_WAS_SINGLE_CHAR = 1 << 9;

    private short id = -1;
    private RegexASTNode parent;
    private short flags;
    private short minPath = 0;
    private short maxPath = 0;

    protected RegexASTNode() {
    }

    protected RegexASTNode(RegexASTNode copy) {
        flags = copy.flags;
        minPath = copy.minPath;
        maxPath = copy.maxPath;
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

    public abstract boolean equalsSemantic(RegexASTNode obj);

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
