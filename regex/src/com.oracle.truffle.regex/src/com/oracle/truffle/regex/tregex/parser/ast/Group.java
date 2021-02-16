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

import java.util.ArrayList;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.RegexASTVisitorIterable;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * Groups are the top-most elements of regular expression ASTs.
 * <p>
 * A {@link Group} is composed of several alternatives, each of which is a {@link Sequence} of other
 * {@link Term}s. A {@link Group} can be wrapped in parentheses and used anywhere a {@link Term} is
 * expected. Therefore, {@link Group} extends {@link Term}.
 * <p>
 * Corresponds to the goal symbol <em>Disjunction</em> and the right-hand sides <strong>(</strong>
 * <em>Disjunction</em> <strong>)</strong> and <strong>( ? :</strong> <em>Disjunction</em>
 * <strong>)</strong> of the goal symbol <em>Atom</em> in the ECMAScript RegExp syntax.
 * <p>
 * On top of managing alternation and capturing, the {@link Group} AST nodes are also the primitives
 * used to represent loops (quantifiers * and +). A {@link Group} can be marked as looping, in which
 * case successfully matching one of its non-empty alternatives should be followed by attempting to
 * match the {@link Group} itself again.
 * <p>
 * The collections of alternatives that make up a {@link Group} is ordered and the order indicates
 * the priority of the alternatives: if matching with an earlier alternative is possible, that match
 * result is preferred to those from later alternatives.
 */
public final class Group extends QuantifiableTerm implements RegexASTVisitorIterable {

    private ArrayList<Sequence> alternatives = new ArrayList<>();
    private short visitorIterationIndex = 0;
    private short groupNumber = -1;
    private short enclosedCaptureGroupsLow;
    private short enclosedCaptureGroupsHigh;

    /**
     * Creates an empty non-capturing group.
     */
    Group() {
    }

    /**
     * Creates an empty capturing group.
     *
     * @param groupNumber The number of this capturing group.
     */
    Group(int groupNumber) {
        setGroupNumber(groupNumber);
    }

    private Group(Group copy) {
        super(copy);
        groupNumber = copy.groupNumber;
        enclosedCaptureGroupsLow = copy.enclosedCaptureGroupsLow;
        enclosedCaptureGroupsHigh = copy.enclosedCaptureGroupsHigh;
    }

    @Override
    public Group copy(RegexAST ast) {
        return ast.register(new Group(this));
    }

    @Override
    public Group copyRecursive(RegexAST ast, CompilationBuffer compilationBuffer) {
        Group copy = copy(ast);
        for (Sequence s : alternatives) {
            copy.add(s.copyRecursive(ast, compilationBuffer));
        }
        return copy;
    }

    /**
     * Returns whether or not this group loops. A looping group differs from a non-looping one in
     * that when you match one of the group's non-empty alternatives, instead of continuing to the
     * next node after the group, the same group is attempted again.
     */
    public boolean isLoop() {
        return isFlagSet(FLAG_GROUP_LOOP);
    }

    /**
     * Sets whether or this group should loop. If the group is set to be looping, this updates the
     * 'next' and 'prev' pointers on the non-empty alternatives to point to the group itself.
     *
     * @param loop true if this group should loop
     * @see #isLoop()
     */
    public void setLoop(boolean loop) {
        setFlag(FLAG_GROUP_LOOP, loop);
    }

    /**
     * Returns the number of this capturing group. If this group is not a capturing group, returns
     * -1.
     */
    public int getGroupNumber() {
        return groupNumber;
    }

    /**
     * Returns the index corresponding to this capture group's BEGIN in a result array returned by a
     * capture-group aware DFA.
     */
    public int getBoundaryIndexStart() {
        assert isCapturing();
        return groupNumberToBoundaryIndexStart(groupNumber);
    }

    /**
     * Returns the index corresponding to this capture group's END in a result array returned by a
     * capture-group aware DFA.
     */
    public int getBoundaryIndexEnd() {
        assert isCapturing();
        return groupNumberToBoundaryIndexEnd(groupNumber);
    }

    /**
     * Returns the index corresponding to a capture group's BEGIN in a result array returned by a
     * capture-group aware DFA.
     */
    public static int groupNumberToBoundaryIndexStart(int groupNumber) {
        return groupNumber * 2;
    }

    /**
     * Returns the index corresponding to a capture group's END in a result array returned by a
     * capture-group aware DFA.
     */
    public static int groupNumberToBoundaryIndexEnd(int groupNumber) {
        return groupNumber * 2 + 1;
    }

    /**
     * Returns whether this group is a capturing group.
     * <p>
     * This is the case when this Group was built using the {@link #Group(int)} constructor or if
     * {@link #setGroupNumber(int)} was called.
     */
    public boolean isCapturing() {
        return groupNumber >= 0;
    }

    /**
     * Marks this {@link Group} as capturing and sets its group number.
     *
     * @param groupNumber
     */
    public void setGroupNumber(int groupNumber) {
        assert groupNumber <= TRegexOptions.TRegexMaxNumberOfCaptureGroups;
        this.groupNumber = (short) groupNumber;
    }

    /**
     * Gets the (inclusive) lower bound of the range of capture groups contained within this group.
     */
    public int getEnclosedCaptureGroupsLow() {
        return enclosedCaptureGroupsLow;
    }

    /**
     * Sets the (inclusive) lower bound of the range of capture groups contained within this group.
     */
    public void setEnclosedCaptureGroupsLow(int enclosedCaptureGroupsLow) {
        assert enclosedCaptureGroupsLow <= TRegexOptions.TRegexMaxNumberOfCaptureGroups;
        this.enclosedCaptureGroupsLow = (short) enclosedCaptureGroupsLow;
    }

    /**
     * Gets the (exclusive) upper bound of the range of capture groups contained within this group.
     */
    public int getEnclosedCaptureGroupsHigh() {
        return enclosedCaptureGroupsHigh;
    }

    /**
     * Sets the (exclusive) upper bound of the range of capture groups contained within this group.
     */
    public void setEnclosedCaptureGroupsHigh(int enclosedCaptureGroupsHigh) {
        assert enclosedCaptureGroupsHigh <= TRegexOptions.TRegexMaxNumberOfCaptureGroups;
        this.enclosedCaptureGroupsHigh = (short) enclosedCaptureGroupsHigh;
    }

    public boolean hasEnclosedCaptureGroups() {
        return enclosedCaptureGroupsHigh > enclosedCaptureGroupsLow;
    }

    /**
     * Returns {@code true} iff all alternatives of this group match only the empty string.
     */
    public boolean isAlwaysZeroWidth() {
        for (Sequence s : alternatives) {
            for (Term t : s.getTerms()) {
                if (!(t instanceof PositionAssertion || t instanceof LookAroundAssertion || (t instanceof Group && ((Group) t).isAlwaysZeroWidth()))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean isUnrollingCandidate() {
        return hasQuantifier() && getQuantifier().isWithinThreshold(TRegexOptions.TRegexQuantifierUnrollThresholdGroup);
    }

    /**
     * Returns the list of alternatives that make up this {@link Group}.
     * <p>
     * Elements should not be added or removed from this list. Use the {@link #add(Sequence)},
     * {@link #insertFirst(Sequence)} and {@link #addSequence(RegexAST)} methods instead.
     */
    public ArrayList<Sequence> getAlternatives() {
        return alternatives;
    }

    public void setAlternatives(ArrayList<Sequence> alternatives) {
        for (Sequence s : alternatives) {
            s.setParent(this);
        }
        this.alternatives = alternatives;
    }

    public Sequence getFirstAlternative() {
        return alternatives.get(0);
    }

    public int size() {
        return alternatives.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Adds a new alternative to this group. The new alternative will be <em>appended to the
     * end</em>, meaning it will have the <em>lowest priority</em> among all the alternatives.
     *
     * @param sequence
     */
    public void add(Sequence sequence) {
        sequence.setParent(this);
        alternatives.add(sequence);
        checkMaxSize();
    }

    /**
     * Inserts a new alternative to this group. The new alternative will be <em>inserted at the
     * beginning</em>, meaning it will have the <em>highest priority</em> among all the
     * alternatives.
     *
     * @param sequence
     */
    public void insertFirst(Sequence sequence) {
        sequence.setParent(this);
        alternatives.add(0, sequence);
        checkMaxSize();
    }

    private void checkMaxSize() {
        if (alternatives.size() > TRegexOptions.TRegexParserTreeMaxNumberOfSequencesInGroup) {
            throw new UnsupportedRegexException("too many sequences in a single group");
        }
    }

    /**
     * Creates a new empty alternatives and adds it to the end of the list of alternatives.
     *
     * @param ast The AST that the new alternative should belong to
     * @return The newly created alternative
     */
    public Sequence addSequence(RegexAST ast) {
        Sequence sequence = ast.createSequence();
        add(sequence);
        return sequence;
    }

    public Sequence getLastAlternative() {
        return alternatives.get(size() - 1);
    }

    public void removeLastSequence() {
        alternatives.remove(alternatives.size() - 1);
    }

    public boolean isLiteral() {
        return alternatives.size() == 1 && alternatives.get(0).isLiteral();
    }

    @Override
    public boolean visitorHasNext() {
        return visitorIterationIndex < alternatives.size();
    }

    @Override
    public RegexASTNode visitorGetNext(boolean reverse) {
        return alternatives.get(visitorIterationIndex++);
    }

    @Override
    public void resetVisitorIterator() {
        visitorIterationIndex = 0;
    }

    @TruffleBoundary
    public String alternativesToString() {
        return alternatives.stream().map(Sequence::toString).collect(Collectors.joining("|"));
    }

    public String loopToString() {
        return isLoop() ? "*" : quantifierToString();
    }

    @Override
    public boolean equalsSemantic(RegexASTNode obj, boolean ignoreQuantifier) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Group)) {
            return false;
        }
        Group o = (Group) obj;
        if (size() != o.size() || groupNumber != o.groupNumber || isLoop() != o.isLoop() || (!ignoreQuantifier && !quantifierEquals(o))) {
            return false;
        }
        for (int i = 0; i < size(); i++) {
            if (!alternatives.get(i).equalsSemantic(o.alternatives.get(i))) {
                return false;
            }
        }
        return true;
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return "(" + (isCapturing() ? "" : "?:") + alternativesToString() + ")" + loopToString();
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return toJson("Group").append(
                        Json.prop("groupNumber", groupNumber),
                        Json.prop("isCapturing", isCapturing()),
                        Json.prop("isLoop", isLoop()),
                        Json.prop("isExpandedLoop", isExpandedQuantifier()),
                        Json.prop("alternatives", alternatives));
    }
}
