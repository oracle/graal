/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.CopyVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.MarkLookBehindEntriesVisitor;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public abstract class RegexASTNode implements JsonConvertible {

    static final int FLAG_PREFIX = 1;
    static final int FLAG_DEAD = 1 << 1;
    static final int FLAG_HAS_CARET = 1 << 2;
    static final int FLAG_HAS_DOLLAR = 1 << 3;
    static final int FLAG_STARTS_WITH_CARET = 1 << 4;
    static final int FLAG_ENDS_WITH_DOLLAR = 1 << 5;
    static final int FLAG_BACK_REFERENCE_IS_NESTED = 1 << 6;
    static final int FLAG_BACK_REFERENCE_IS_FORWARD = 1 << 7;
    static final int FLAG_BACK_REFERENCE_IS_NESTED_OR_FORWARD = 1 << 8;
    static final int FLAG_BACK_REFERENCE_IS_IGNORE_CASE = 1 << 9;
    static final int FLAG_GROUP_LOOP = 1 << 10;
    static final int FLAG_GROUP_EXPANDED_QUANTIFIER = 1 << 11;
    static final int FLAG_GROUP_UNROLLED_QUANTIFIER = 1 << 12;
    static final int FLAG_GROUP_EXPANDED_QUANTIFIER_EMPTY_SEQUENCE = 1 << 13;
    static final int FLAG_GROUP_QUANTIFIER_EXPANSION_DONE = 1 << 14;
    static final int FLAG_GROUP_LOCAL_FLAGS = 1 << 15;
    static final int FLAG_EMPTY_GUARD = 1 << 16;
    static final int FLAG_LOOK_AROUND_NEGATED = 1 << 17;
    static final int FLAG_HAS_LOOPS = 1 << 18;
    static final int FLAG_HAS_CAPTURE_GROUPS = 1 << 19;
    static final int FLAG_HAS_QUANTIFIERS = 1 << 20;
    static final int FLAG_HAS_LOOK_BEHINDS = 1 << 21;
    static final int FLAG_HAS_LOOK_AHEADS = 1 << 22;
    static final int FLAG_HAS_BACK_REFERENCES = 1 << 23;
    static final int FLAG_CHARACTER_CLASS_WAS_SINGLE_CHAR = 1 << 24;

    private int id = -1;
    private RegexASTNode parent;
    private int flags;
    private int minPath = 0;
    private int maxPath = 0;
    private int prefixLengthMin = 0;
    private int prefixLengthMax = 0;

    protected RegexASTNode() {
    }

    protected RegexASTNode(RegexASTNode copy) {
        flags = copy.flags;
        minPath = copy.minPath;
        maxPath = copy.maxPath;
    }

    /**
     * Copy this node only, without any child nodes. The ID and minPath of the copied nodes is left
     * unset.
     *
     * @param ast RegexAST the node should belong to.
     * @return A shallow copy of this node.
     */
    public abstract RegexASTNode copy(RegexAST ast);

    /**
     * Recursively copy this subtree. This method should be used instead of {@link CopyVisitor} if
     * the copying process is required to be thread-safe. The ID and minPath of the copied nodes is
     * left unset.
     *
     * @param ast RegexAST the new nodes should belong to.
     * @return A deep copy of this node.
     */
    public abstract RegexASTNode copyRecursive(RegexAST ast, CompilationBuffer compilationBuffer);

    public abstract boolean equalsSemantic(RegexASTNode obj);

    public boolean idInitialized() {
        return id >= 0;
    }

    public final int getId() {
        assert idInitialized();
        return id;
    }

    public final void setId(int id) {
        assert !idInitialized();
        assert id <= TRegexOptions.TRegexParserTreeMaxSize;
        this.id = id;
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

    protected boolean isFlagSet(int flag) {
        return (flags & flag) != 0;
    }

    protected void setFlag(int flag) {
        setFlag(flag, true);
    }

    protected int getFlags(int mask) {
        return flags & mask;
    }

    /**
     * Update all flags denoted by {@code mask} with the values from {@code newFlags}.
     */
    protected void setFlags(int newFlags, int mask) {
        assert (newFlags & ~mask) == 0;
        flags = flags & ~mask | newFlags;
    }

    protected void setFlag(int flag, boolean value) {
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
        setDead(true);
    }

    public void setDead(boolean dead) {
        setFlag(FLAG_DEAD, dead);
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

    /**
     * Subexpression contains {@link #isCaret() "^"}.
     */
    public boolean hasCaret() {
        return isFlagSet(FLAG_HAS_CARET);
    }

    public void setHasCaret() {
        setHasCaret(true);
    }

    public void setHasCaret(boolean hasCaret) {
        setFlag(FLAG_HAS_CARET, hasCaret);
    }

    /**
     * Subexpression contains {@link #isDollar() "$"}.
     */
    public boolean hasDollar() {
        return isFlagSet(FLAG_HAS_DOLLAR);
    }

    public void setHasDollar() {
        setHasDollar(true);
    }

    public void setHasDollar(boolean hasDollar) {
        setFlag(FLAG_HAS_DOLLAR, hasDollar);
    }

    /**
     * This subexpression is dominated by {@link #isCaret() "^"}.
     */
    public boolean startsWithCaret() {
        return isFlagSet(FLAG_STARTS_WITH_CARET);
    }

    public void setStartsWithCaret() {
        setStartsWithCaret(true);
    }

    public void setStartsWithCaret(boolean startsWithCaret) {
        setFlag(FLAG_STARTS_WITH_CARET, startsWithCaret);
    }

    /**
     * All paths out of this subexpression go through {@link #isCaret() "$"}.
     */
    public boolean endsWithDollar() {
        return isFlagSet(FLAG_ENDS_WITH_DOLLAR);
    }

    public void setEndsWithDollar() {
        setEndsWithDollar(true);
    }

    public void setEndsWithDollar(boolean endsWithDollar) {
        setFlag(FLAG_ENDS_WITH_DOLLAR, endsWithDollar);
    }

    /**
     * Subexpression contains {@link Group#isLoop() loops}.
     */
    public boolean hasLoops() {
        return isFlagSet(FLAG_HAS_LOOPS);
    }

    public void setHasLoops() {
        setHasLoops(true);
    }

    public void setHasLoops(boolean hasLoops) {
        setFlag(FLAG_HAS_LOOPS, hasLoops);
    }

    /**
     * Subexpression contains {@link QuantifiableTerm#hasNotUnrolledQuantifier() not unrolled
     * quantifiers}.
     */
    public boolean hasQuantifiers() {
        return isFlagSet(FLAG_HAS_QUANTIFIERS);
    }

    public void setHasQuantifiers() {
        setFlag(FLAG_HAS_QUANTIFIERS, true);
    }

    /**
     * Subexpression contains {@link Group#isCapturing() capturing groups}.
     */
    public boolean hasCaptureGroups() {
        return isFlagSet(FLAG_HAS_CAPTURE_GROUPS);
    }

    public void setHasCaptureGroups() {
        setFlag(FLAG_HAS_CAPTURE_GROUPS, true);
    }

    /**
     * Subexpression contains {@link #isLookAheadAssertion() look-ahead assertions}.
     */
    public boolean hasLookAheads() {
        return isFlagSet(FLAG_HAS_LOOK_AHEADS);
    }

    public void setHasLookAheads() {
        setFlag(FLAG_HAS_LOOK_AHEADS, true);
    }

    /**
     * Subexpression contains {@link #isLookBehindAssertion() look-behind assertions}.
     */
    public boolean hasLookBehinds() {
        return isFlagSet(FLAG_HAS_LOOK_BEHINDS);
    }

    public void setHasLookBehinds() {
        setFlag(FLAG_HAS_LOOK_BEHINDS, true);
    }

    /**
     * Subexpression contains {@link #isBackReference() back-references}.
     */
    public boolean hasBackReferences() {
        return isFlagSet(FLAG_HAS_BACK_REFERENCES);
    }

    public void setHasBackReferences() {
        setFlag(FLAG_HAS_BACK_REFERENCES, true);
    }

    /**
     * Indicates whether this {@link RegexASTNode} was inserted into the AST as the result of
     * expanding quantifier syntax (*, +, ?, {n,m}).
     *
     * E.g., if A is some term, then:
     * <ul>
     * <li>A* is expanded as (A|)*
     * <li>A*? is expanded as (|A)*
     * <li>A+ is expanded as A(A|)*
     * <li>A+? is expanded as A(|A)*
     * <li>A? is expanded as (A|)
     * <li>A?? is expanded as (|A)
     * <li>A{2,4} is expanded as AA(A(A|)|)
     * <li>A{2,4}? is expanded as AA(|A(|A))
     * </ul>
     * where (X|Y) is a group with alternatives X and Y and (X|Y)* is a looping group with
     * alternatives X and Y. In the examples above, all of the occurrences of A in the expansions as
     * well as the additional empty {@link Sequence}s would be marked with this flag.
     */
    public boolean isExpandedQuantifier() {
        return isFlagSet(FLAG_GROUP_EXPANDED_QUANTIFIER);
    }

    /**
     * Marks this {@link RegexASTNode} as being inserted into the AST as part of expanding
     * quantifier syntax (*, +, ?, {n,m}).
     *
     * @see #isExpandedQuantifier()
     */
    public void setExpandedQuantifier(boolean expandedQuantifier) {
        setFlag(FLAG_GROUP_EXPANDED_QUANTIFIER, expandedQuantifier);
    }

    /**
     * Indicates whether this {@link RegexASTNode} represents a mandatory copy of a quantified term
     * after unrolling.
     *
     * E.g., in the expansion of A{2,4}, which is AA(A(A|)|), the first two occurrences of A are
     * marked with this flag.
     */
    public boolean isUnrolledQuantifier() {
        return isFlagSet(FLAG_GROUP_UNROLLED_QUANTIFIER);
    }

    /**
     * Marks this {@link RegexASTNode} as being inserted into the AST as part of unrolling the
     * mandatory part of a quantified term.
     *
     * @see #isUnrolledQuantifier()
     */
    public void setUnrolledQuantifer(boolean unrolledQuantifer) {
        setFlag(FLAG_GROUP_UNROLLED_QUANTIFIER, unrolledQuantifer);
    }

    public boolean isExpandedQuantifierEmptySequence() {
        return isFlagSet(FLAG_GROUP_EXPANDED_QUANTIFIER_EMPTY_SEQUENCE);
    }

    public void setExpandedQuantifierEmptySequence(boolean expandedQuantifierEmptySequence) {
        setFlag(FLAG_GROUP_EXPANDED_QUANTIFIER_EMPTY_SEQUENCE, expandedQuantifierEmptySequence);
    }

    public boolean isQuantifierExpansionDone() {
        return isFlagSet(FLAG_GROUP_QUANTIFIER_EXPANSION_DONE);
    }

    public void setQuantifierExpansionDone(boolean quantifierExpansionDone) {
        setFlag(FLAG_GROUP_QUANTIFIER_EXPANSION_DONE, quantifierExpansionDone);
    }

    public int getMinPath() {
        return minPath;
    }

    public void setMinPath(int n) {
        minPath = n;
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
        maxPath = n;
    }

    public void incMaxPath() {
        incMaxPath(1);
    }

    public void incMaxPath(int n) {
        maxPath += n;
    }

    public int getPrefixLengthMin() {
        return prefixLengthMin;
    }

    public void setPrefixLengthMin(int prefixLengthMin) {
        this.prefixLengthMin = prefixLengthMin;
    }

    public int getPrefixLengthMax() {
        return prefixLengthMax;
    }

    public void setPrefixLengthMax(int prefixLengthMax) {
        this.prefixLengthMax = prefixLengthMax;
    }

    public boolean hasVariablePrefixLength() {
        return getPrefixLengthMin() != getPrefixLengthMax();
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

    public boolean isBackReference() {
        return this instanceof BackReference;
    }

    public boolean isCharacterClass() {
        return this instanceof CharacterClass;
    }

    public boolean isGroup() {
        return this instanceof Group;
    }

    public boolean isConditionalBackReferenceGroup() {
        return this instanceof ConditionalBackReferenceGroup;
    }

    public boolean isLookAroundAssertion() {
        return this instanceof LookAroundAssertion;
    }

    public boolean isLookAheadAssertion() {
        return this instanceof LookAheadAssertion;
    }

    public boolean isLookBehindAssertion() {
        return this instanceof LookBehindAssertion;
    }

    public boolean isAtomicGroup() {
        return this instanceof AtomicGroup;
    }

    public boolean isMatchFound() {
        return this instanceof MatchFound;
    }

    public boolean isPositionAssertion() {
        return this instanceof PositionAssertion;
    }

    public boolean isQuantifiableTerm() {
        return this instanceof QuantifiableTerm;
    }

    public boolean isSubexpressionCall() {
        return this instanceof SubexpressionCall;
    }

    public boolean isRoot() {
        return this instanceof RegexASTRootNode;
    }

    public boolean isSubtreeRoot() {
        return this instanceof RegexASTSubtreeRootNode;
    }

    public boolean isSequence() {
        return this instanceof Sequence;
    }

    public boolean isCaret() {
        return isPositionAssertion() && asPositionAssertion().isCaret();
    }

    public boolean isDollar() {
        return isPositionAssertion() && asPositionAssertion().isDollar();
    }

    public BackReference asBackReference() {
        return (BackReference) this;
    }

    public CharacterClass asCharacterClass() {
        return (CharacterClass) this;
    }

    public Group asGroup() {
        return (Group) this;
    }

    public ConditionalBackReferenceGroup asConditionalBackReferenceGroup() {
        return (ConditionalBackReferenceGroup) this;
    }

    public LookAroundAssertion asLookAroundAssertion() {
        return (LookAroundAssertion) this;
    }

    public LookAheadAssertion asLookAheadAssertion() {
        return (LookAheadAssertion) this;
    }

    public LookBehindAssertion asLookBehindAssertion() {
        return (LookBehindAssertion) this;
    }

    public AtomicGroup asAtomicGroup() {
        return (AtomicGroup) this;
    }

    public RegexASTSubtreeRootNode asSubtreeRootNode() {
        return (RegexASTSubtreeRootNode) this;
    }

    public MatchFound asMatchFound() {
        return (MatchFound) this;
    }

    public PositionAssertion asPositionAssertion() {
        return (PositionAssertion) this;
    }

    public QuantifiableTerm asQuantifiableTerm() {
        return (QuantifiableTerm) this;
    }

    public Sequence asSequence() {
        return (Sequence) this;
    }

    public SubexpressionCall asSubexpressionCall() {
        return (SubexpressionCall) this;
    }

    protected JsonObject toJson(String typeName) {
        return Json.obj(Json.prop("id", id),
                        Json.prop("type", typeName),
                        Json.prop("parent", astNodeId(parent)),
                        Json.prop("minPath", minPath),
                        Json.prop("maxPath", maxPath),
                        Json.prop("prefixLengthMin", prefixLengthMin),
                        Json.prop("prefixLengthMax", prefixLengthMax),
                        Json.prop("isPrefix", isPrefix()),
                        Json.prop("isDead", isDead()));
    }
}
