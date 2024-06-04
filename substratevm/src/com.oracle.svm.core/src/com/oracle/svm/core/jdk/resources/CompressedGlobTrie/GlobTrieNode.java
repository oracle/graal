/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk.resources.CompressedGlobTrie;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GlobTrieNode {
    protected static final String STAR = "*";
    protected static final String STAR_STAR = "**";
    protected static final String LEVEL_IDENTIFIER = "/";
    public static final String SAME_LEVEL_IDENTIFIER = "#";

    private String content;
    private Map<String, GlobTrieNode> children;
    private boolean isLeaf;
    private boolean isNewLevel;
    private Set<String> additionalContent;

    protected GlobTrieNode() {
        content = "";
        children = new HashMap<>();
        isLeaf = false;
        isNewLevel = false;
        additionalContent = new HashSet<>();
    }

    protected GlobTrieNode(String content) {
        this();
        this.content = content;
    }

    public boolean isNewLevel() {
        return isNewLevel;
    }

    protected void setNewLevel() {
        isNewLevel = true;
    }

    public boolean isLeaf() {
        return isLeaf;
    }

    protected void setLeaf() {
        isLeaf = true;
    }

    public String getContent() {
        return content;
    }

    protected Set<String> getAdditionalContent() {
        return additionalContent;
    }

    protected void addAdditionalContent(String ac) {
        this.additionalContent.add(ac);
    }

    public List<GlobTrieNode> getChildren() {
        return children.values().stream().toList();
    }

    protected GlobTrieNode getChild(String child) {
        return children.get(child);
    }

    protected GlobTrieNode getChildFromSameLevel(String child) {
        return children.get(child + SAME_LEVEL_IDENTIFIER);
    }

    protected GlobTrieNode addChild(String child, GlobTrieNode childValue) {
        StringBuilder sb = new StringBuilder(child);

        // to make difference between a*b* (represented as: a* -> b*#)
        // and a*/b* (represented as: a* -> b*) append # when current node is a part of previous one
        if (!childValue.isNewLevel()) {
            sb.append(SAME_LEVEL_IDENTIFIER);
        }

        /* only add if we don't have same child to avoid duplicates */
        children.putIfAbsent(sb.toString(), childValue);
        return children.get(sb.toString());
    }

    protected List<StarTrieNode> getChildrenWithStar() {
        return this.getChildren().stream()
                        .filter(node -> node instanceof StarTrieNode)
                        .map(node -> (StarTrieNode) node)
                        .toList();
    }

    protected List<LiteralNode> getChildrenWithLiteral() {
        return this.getChildren()
                        .stream()
                        .filter(node -> node instanceof LiteralNode)
                        .map(node -> (LiteralNode) node)
                        .toList();
    }

    protected DoubleStarNode getDoubleStarNode() {
        return (DoubleStarNode) getChild(STAR_STAR);
    }

    /**
     * This function makes all Map/List fields of the class immutable and more efficient. It should
     * only be called once, from the root of the structure, since it traverses through all nodes in
     * the structure.
     */
    protected void trim() {
        for (GlobTrieNode child : children.values()) {
            child.trim();
        }

        additionalContent = Set.copyOf(additionalContent);
        children = Map.copyOf(children);
    }

}
