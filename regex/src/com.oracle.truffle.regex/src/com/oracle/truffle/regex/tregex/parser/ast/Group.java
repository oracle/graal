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
import com.oracle.truffle.regex.tregex.parser.ast.visitors.RegexASTVisitorIterable;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.ArrayList;
import java.util.stream.Collectors;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

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
public final class Group extends Term implements RegexASTVisitorIterable {

    private final ArrayList<Sequence> alternatives = new ArrayList<>();
    private short visitorIterationIndex = 0;
    private byte groupNumber = -1;
    private byte enclosedCaptureGroupsLow;
    private byte enclosedCaptureGroupsHigh;
    private SourceSection sourceSectionBegin;
    private SourceSection sourceSectionEnd;

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

    private Group(Group copy, RegexAST ast, boolean recursive) {
        super(copy);
        groupNumber = copy.groupNumber;
        enclosedCaptureGroupsLow = copy.enclosedCaptureGroupsLow;
        enclosedCaptureGroupsHigh = copy.enclosedCaptureGroupsHigh;
        sourceSectionBegin = copy.sourceSectionBegin;
        sourceSectionEnd = copy.sourceSectionEnd;
        if (recursive) {
            for (Sequence s : copy.alternatives) {
                add(s.copy(ast, true));
            }
        }
    }

    @Override
    public Group copy(RegexAST ast, boolean recursive) {
        return ast.register(new Group(this, ast, recursive));
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
     * Indicates whether this {@link Group} was inserted into the AST as the result of expanding
     * quantifier syntax (*, +, ?, {n,m}).
     * 
     * E.g., if A is some group, then:
     * <ul>
     * <li>A* is expanded as (A|)*
     * <li>A*? is expanded as (|A)*
     * <li>A+ is expanded as A(A|)*
     * <li>A+? is expanded as A(|A)*
     * <li>A? is expanded as (A|)
     * <li>A?? is expanded as (|A)
     * <li>A{2,4} is expanded as AA(A|)(A|)
     * <li>A{2,4}? is expanded as AA(|A)(|A)
     * </ul>
     * where (X|Y) is a group with alternatives X and Y and (X|Y)* is a looping group with
     * alternatives X and Y. In the examples above, all of the occurrences of A in the expansions
     * would be marked with this flag.
     */
    public boolean isExpandedQuantifier() {
        return isFlagSet(FLAG_GROUP_EXPANDED_QUANTIFIER);
    }

    /**
     * Marks this {@link Group} as being inserted into the AST as part of expanding quantifier
     * syntax (*, +, ?, {n,m}).
     * 
     * @see #isExpandedQuantifier()
     */
    public void setExpandedQuantifier(boolean expandedQuantifier) {
        setFlag(FLAG_GROUP_EXPANDED_QUANTIFIER, expandedQuantifier);
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
        this.groupNumber = (byte) groupNumber;
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
        this.enclosedCaptureGroupsLow = (byte) enclosedCaptureGroupsLow;
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
        this.enclosedCaptureGroupsHigh = (byte) enclosedCaptureGroupsHigh;
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

    @Override
    public SourceSection getSourceSection() {
        if (super.getSourceSection() == null && sourceSectionBegin != null && sourceSectionEnd != null) {
            super.setSourceSection(sourceSectionBegin.getSource().createSection(sourceSectionBegin.getCharIndex(),
                            sourceSectionEnd.getCharEndIndex() - sourceSectionBegin.getCharIndex()));
        }
        return super.getSourceSection();
    }

    /**
     * Returns the {@link SourceSection} corresponding to this group's opening bracket and modifier
     * symbols (like "?:", "?=", ...), or {@code null} if this group has no corresponding source
     * (this is the case for groups inserted by the parser when expanding quantifiers etc.).
     */
    public SourceSection getSourceSectionBegin() {
        return sourceSectionBegin;
    }

    public void setSourceSectionBegin(SourceSection sourceSectionBegin) {
        this.sourceSectionBegin = sourceSectionBegin;
    }

    /**
     * Returns the {@link SourceSection} corresponding to this group's closing bracket, or
     * {@code null} if this group has no corresponding source (this is the case for groups inserted
     * by the parser when expanding quantifiers etc.).
     */
    public SourceSection getSourceSectionEnd() {
        return sourceSectionEnd;
    }

    public void setSourceSectionEnd(SourceSection sourceSectionEnd) {
        this.sourceSectionEnd = sourceSectionEnd;
    }

    public int size() {
        return alternatives.size();
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

    public void removeLastSequence() {
        alternatives.remove(alternatives.size() - 1);
    }

    public boolean isLiteral() {
        return alternatives.size() == 1 && alternatives.get(0).isLiteral();
    }

    /**
     * Marks the node as dead, i.e. unmatchable.
     * <p>
     * Note that using this setter also traverses the ancestors and children of this node and
     * updates their "dead" status as well.
     */
    @Override
    public void markAsDead() {
        super.markAsDead();
        for (Sequence s : alternatives) {
            if (!s.isDead()) {
                s.markAsDead();
            }
        }
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
        return isLoop() ? "*" : "";
    }

    @Override
    @TruffleBoundary
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
