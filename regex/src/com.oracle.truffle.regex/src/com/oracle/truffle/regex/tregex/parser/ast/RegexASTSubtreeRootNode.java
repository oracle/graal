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

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.RegexASTVisitorIterable;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * A common supertype to the root node and look-ahead and look-behind assertions. Every AST subtree
 * contains a {@link Group} which contains the syntactic subtree, as well as a {@link MatchFound}
 * node, which is needed for NFA-like traversal of the AST, see
 * {@link com.oracle.truffle.regex.tregex.parser.ast.visitors.NFATraversalRegexASTVisitor}.
 */
public abstract class RegexASTSubtreeRootNode extends Term implements RegexASTVisitorIterable {

    private Group group;
    private MatchFound matchFound;
    private boolean visitorGroupVisited = false;

    RegexASTSubtreeRootNode() {
    }

    RegexASTSubtreeRootNode(RegexASTSubtreeRootNode copy, RegexAST ast, boolean recursive) {
        super(copy);
        if (recursive) {
            setGroup(copy.group.copy(ast, true));
        }
        ast.createEndPoint(this);
    }

    @Override
    public abstract RegexASTSubtreeRootNode copy(RegexAST ast, boolean recursive);

    /**
     * Returns the {@link Group} that represents the contents of this subtree.
     */
    public Group getGroup() {
        return group;
    }

    /**
     * Sets the contents of this subtree.
     * <p>
     * This method should be called after creating any instance of this class. Otherwise, methods of
     * this class could throw {@link NullPointerException}s or return {@code null}s.
     */
    public void setGroup(Group group) {
        this.group = group;
        group.setParent(this);
    }

    /**
     * Returns this subtree's corresponding {@link MatchFound} node.
     */
    public MatchFound getMatchFound() {
        return matchFound;
    }

    public void setMatchFound(MatchFound matchFound) {
        this.matchFound = matchFound;
        matchFound.setParent(this);
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
        if (!group.isDead()) {
            group.markAsDead();
        }
    }

    @Override
    public boolean visitorHasNext() {
        return !visitorGroupVisited;
    }

    @Override
    public RegexASTNode visitorGetNext(boolean reverse) {
        visitorGroupVisited = true;
        return group;
    }

    @Override
    public void resetVisitorIterator() {
        visitorGroupVisited = false;
    }

    public abstract String getPrefix();

    @Override
    public SourceSection getSourceSection() {
        return group.getSourceSection();
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return "(" + getPrefix() + group.alternativesToString() + ")";
    }

    @Override
    protected JsonObject toJson(String typeName) {
        return super.toJson(typeName).append(Json.prop("group", astNodeId(group)));
    }
}
