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
package com.oracle.truffle.regex.tregex.parser.ast.visitors;

import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.tregex.nfa.ASTNodeSet;
import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.Term;

import java.util.ArrayList;
import java.util.Set;

/**
 * Special AST visitor that will find all immediate successors of a given Term when the AST is seen
 * as a NFA, in priority order. A successor can either be a {@link CharacterClass} or a
 * {@link MatchFound} node.
 *
 * <pre>
 * {@code
 * Examples:
 *
 * Successors of "b" in (a|b)c:
 * 1.: "c"
 *
 * Successors of "b" in (a|b)*c:
 * 1.: "a"
 * 2.: "b"
 * 3.: "c"
 * }
 * </pre>
 *
 * For every successor, the visitor will provide the full path of AST nodes that have been traversed
 * from the initial node to the successor node, where {@link Group} nodes are treated specially: The
 * path will contain separate entries for <em>entering</em> and <em>leaving</em> a {@link Group},
 * and if a {@link Group} was <em>entered and then left</em> (note the order!) in the same path,
 * both nodes in the path will be marked with the {@link PathElement#isGroupPassThrough()} flag.
 * Furthermore, the visitor will not descend into lookaround assertions, it will jump over them and
 * just add their corresponding {@link LookAheadAssertion} or {@link LookBehindAssertion} node to
 * the path. This visitor is not thread-safe, since it uses the methods of
 * {@link RegexASTVisitorIterable} for traversing groups.
 *
 * <pre>
 * {@code
 * Examples with full path information:
 *
 * Successors of "b" in (a|b)c:
 * 1.: [leave group 1], [CharClass c]
 *
 * Successors of "b" in (a|b)*c:
 * 1.: [leave group 1], [enter group 1], [Sequence 0 of group 1], [CharClass a]
 * 2.: [leave group 1], [enter group 1], [Sequence 1 of group 1], [CharClass b]
 * 3.: [leave group 1], [CharClass c]
 * }
 * </pre>
 */
public abstract class NFATraversalRegexASTVisitor {

    public static final class PathElement {

        private static final byte FLAG_GROUP_ENTER = 1;
        private static final byte FLAG_GROUP_EXIT = 1 << 1;
        private static final byte FLAG_GROUP_PASS_THROUGH = 1 << 2;

        private final RegexASTNode node;
        private byte flags = 0;
        // Since the same group can appear multiple times on the path, we cannot reuse Group's
        // implementation of RegexASTVisitorIterable. Therefore, every occurrence of a group on the
        // path has its own index for iterating and back-tracking over its alternatives.
        private int groupAlternativeIndex = 0;

        private PathElement(RegexASTNode node) {
            this.node = node;
        }

        public RegexASTNode getNode() {
            return node;
        }

        public boolean groupHasNextAlternative() {
            assert node instanceof Group;
            return groupAlternativeIndex < ((Group) node).getAlternatives().size();
        }

        public Sequence groupGetNextAlternative() {
            assert node instanceof Group;
            return ((Group) node).getAlternatives().get(groupAlternativeIndex++);
        }

        private boolean isFlagSet(byte flag) {
            return (flags & flag) != 0;
        }

        private void setFlag(byte flag) {
            flags |= flag;
        }

        private void clearFlag(byte flag) {
            flags &= ~flag;
        }

        public boolean isGroupEnter() {
            return isFlagSet(FLAG_GROUP_ENTER);
        }

        public void setGroupEnter() {
            setFlag(FLAG_GROUP_ENTER);
        }

        public boolean isGroupExit() {
            return isFlagSet(FLAG_GROUP_EXIT);
        }

        public void setGroupExit() {
            setFlag(FLAG_GROUP_EXIT);
        }

        public boolean isGroupPassThrough() {
            return isFlagSet(FLAG_GROUP_PASS_THROUGH);
        }

        public void setGroupPassThrough() {
            setFlag(FLAG_GROUP_PASS_THROUGH);
        }

        public void clearGroupPassThrough() {
            clearFlag(FLAG_GROUP_PASS_THROUGH);
        }
    }

    protected final RegexAST ast;
    private final ArrayList<PathElement> curPath = new ArrayList<>();
    // insideLoops is the set of looping groups that we are currently inside of. We need to maintain
    // this in order to detect infinite loops in the NFA traversal. If we enter a looping group,
    // traverse it without encountering a CharacterClass node or a MatchFound node and arrive back
    // at the same group, then we are bound to loop like this forever. Using insideLoops, we can
    // detect this situation and proceed with the search using another alternative.
    // For example, in the RegexAST ((|[a])*|)*, which corresponds to the regex /(a*?)*/, we can
    // traverse the inner loop, (|[a])*, without hitting any CharacterClass node by choosing the
    // first alternative and we will then arrive back at the outer loop. There, we detect an
    // infinite loop, which causes us to backtrack and choose the second alternative in the inner
    // loop, leading us to the CharacterClass node [a].
    private final ASTNodeSet<Group> insideLoops;
    private RegexASTNode cur;
    private Set<LookBehindAssertion> traversableLookBehindAssertions;
    private boolean canTraverseCaret = false;
    private boolean reverse = false;
    private boolean done = false;
    private int dollarsInPath = 0;

    protected NFATraversalRegexASTVisitor(RegexAST ast) {
        this.ast = ast;
        this.insideLoops = new ASTNodeSet<>(ast);
    }

    public void setTraversableLookBehindAssertions(Set<LookBehindAssertion> traversableLookBehindAssertions) {
        this.traversableLookBehindAssertions = traversableLookBehindAssertions;
    }

    public boolean canTraverseCaret() {
        return canTraverseCaret;
    }

    public void setCanTraverseCaret(boolean canTraverseCaret) {
        this.canTraverseCaret = canTraverseCaret;
    }

    public void setReverse(boolean reverse) {
        this.reverse = reverse;
    }

    protected void run(Term runRoot) {
        if (runRoot instanceof Group) {
            cur = runRoot;
        } else {
            advanceTerm(runRoot);
        }
        while (!done) {
            while (doAdvance()) {
                // advance until we reach the next node to visit
            }
            if (done) {
                break;
            }
            visit(curPath);
            retreat();
        }
        done = false;
    }

    /**
     * Visit the next successor found.
     *
     * @param path Path to the successor. Do not modify this list, it will be reused by the visitor
     *            for finding the next successor!
     */
    protected abstract void visit(ArrayList<PathElement> path);

    protected abstract void enterLookAhead(LookAheadAssertion assertion);

    protected abstract void leaveLookAhead(LookAheadAssertion assertion);

    private boolean doAdvance() {
        if (cur.isDead() || insideLoops.contains(cur)) {
            return retreat();
        }
        final PathElement curPathElement = new PathElement(cur);
        curPath.add(curPathElement);
        if (cur instanceof Group) {
            curPathElement.setGroupEnter();
            final Group group = (Group) cur;
            if (group.isLoop()) {
                insideLoops.add(group);
            }
            // This path will only be hit when visiting a group for the first time. All groups must
            // have at least one child sequence, so no check is needed here.
            assert curPathElement.groupHasNextAlternative();
            cur = curPathElement.groupGetNextAlternative();
            return true;
        } else if (cur instanceof Sequence) {
            final Sequence sequence = (Sequence) cur;
            if (sequence.isEmpty()) {
                Group parent = sequence.getParent();
                pushGroupExit(parent);
                if (parent.isLoop()) {
                    insideLoops.remove(parent);
                }
                return advanceTerm(parent);
            } else {
                cur = reverse ? sequence.getLastTerm() : sequence.getFirstTerm();
                return true;
            }
        } else if (cur instanceof PositionAssertion) {
            final PositionAssertion assertion = (PositionAssertion) cur;
            switch (assertion.type) {
                case CARET:
                    if (canTraverseCaret) {
                        return advanceTerm(assertion);
                    } else {
                        return retreat();
                    }
                case DOLLAR:
                    dollarsInPath++;
                    return advanceTerm(assertion);
                default:
                    throw new IllegalStateException();
            }
        } else if (cur instanceof LookAheadAssertion) {
            enterLookAhead((LookAheadAssertion) cur);
            return advanceTerm((LookAheadAssertion) cur);
        } else if (cur instanceof LookBehindAssertion) {
            if (traversableLookBehindAssertions == null || traversableLookBehindAssertions.contains(cur)) {
                return advanceTerm((LookBehindAssertion) cur);
            } else {
                return retreat();
            }
        } else if (cur instanceof CharacterClass) {
            if (!reverse && dollarsInPath > 0) {
                // don't visit CharacterClass nodes if we traversed dollar - PositionAssertions
                // already
                return retreat();
            } else {
                return false;
            }
        } else if (cur instanceof BackReference) {
            throw new UnsupportedRegexException("back-references are not suitable for this visitor!");
        } else if (cur instanceof MatchFound) {
            return false;
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * Checks whether we have already entered this node before and would therefore be passing
     * through its contents without matching any input characters.
     *
     * @param node the node that we are about to leave
     * @return true if we entered the node before
     */
    private boolean passingThrough(RegexASTNode node) {
        for (PathElement elem : curPath) {
            if (!elem.isGroupExit() && elem.getNode() == node) {
                return true;
            }
        }
        return false;
    }

    private boolean advanceTerm(Term term) {
        if (ast.isNFAInitialState(term)) {
            assert term instanceof PositionAssertion || term instanceof MatchFound;
            if (term instanceof PositionAssertion) {
                cur = ((PositionAssertion) term).getNext();
            } else {
                cur = ((MatchFound) term).getNext();
            }
            return true;
        }
        Term curTerm = term;
        while (!(curTerm.getParent() instanceof RegexASTSubtreeRootNode)) {
            // We are leaving curTerm. If curTerm has an empty guard and we have already entered
            // curTerm during this step, then we stop and retreat. Otherwise, we would end up
            // letting curTerm match the empty string, which is what the empty guard is meant to
            // forbid.
            if (curTerm.hasEmptyGuard() && passingThrough(curTerm)) {
                return retreat();
            }
            Sequence parentSeq = (Sequence) curTerm.getParent();
            if (curTerm == (reverse ? parentSeq.getFirstTerm() : parentSeq.getLastTerm())) {
                if (parentSeq.hasEmptyGuard() && passingThrough(parentSeq)) {
                    return retreat();
                }
                final Group parentGroup = parentSeq.getParent();
                pushGroupExit(parentGroup);
                if (parentGroup.isLoop()) {
                    cur = parentGroup;
                    return true;
                }
                curTerm = parentGroup;
            } else {
                cur = parentSeq.getTerms().get(curTerm.getSeqIndex() + (reverse ? -1 : 1));
                return true;
            }
        }
        assert curTerm instanceof Group;
        assert curTerm.getParent() instanceof RegexASTSubtreeRootNode;
        if (curTerm.hasEmptyGuard() && passingThrough(curTerm)) {
            return retreat();
        }
        cur = curTerm.getSubTreeParent().getMatchFound();
        return true;
    }

    private void pushGroupExit(Group group) {
        PathElement groupPathElement = new PathElement(group);
        groupPathElement.setGroupExit();
        for (int i = curPath.size() - 1; i >= 0; i--) {
            if (curPath.get(i).getNode() == group) {
                curPath.get(i).setGroupPassThrough();
                groupPathElement.setGroupPassThrough();
                break;
            }
        }
        curPath.add(groupPathElement);
    }

    private boolean retreat() {
        if (curPath.isEmpty()) {
            done = true;
            return false;
        }
        PathElement lastVisited = popPath();
        while (true) {
            if (lastVisited.getNode() instanceof Group) {
                Group group = (Group) lastVisited.getNode();
                if (lastVisited.isGroupExit()) {
                    if (lastVisited.isGroupPassThrough()) {
                        for (int i = curPath.size() - 1; i >= 0; i--) {
                            if (curPath.get(i).getNode() == group) {
                                assert curPath.get(i).isGroupEnter();
                                curPath.get(i).clearGroupPassThrough();
                                break;
                            }
                        }
                        if (group.isLoop()) {
                            insideLoops.add(group);
                        }
                    }
                } else {
                    assert lastVisited.isGroupEnter();
                    if (lastVisited.groupHasNextAlternative()) {
                        curPath.add(lastVisited);
                        cur = lastVisited.groupGetNextAlternative();
                        return true;
                    } else {
                        if (group.isLoop()) {
                            insideLoops.remove(group);
                        }
                    }
                }
            } else {
                if (lastVisited.getNode() instanceof LookAheadAssertion) {
                    leaveLookAhead((LookAheadAssertion) lastVisited.getNode());
                } else if (lastVisited.getNode() instanceof PositionAssertion) {
                    if (((PositionAssertion) lastVisited.getNode()).type == PositionAssertion.Type.DOLLAR) {
                        dollarsInPath--;
                    }
                }
            }
            if (curPath.isEmpty()) {
                done = true;
                return false;
            }
            lastVisited = popPath();
        }
    }

    private PathElement popPath() {
        return curPath.remove(curPath.size() - 1);
    }
}
