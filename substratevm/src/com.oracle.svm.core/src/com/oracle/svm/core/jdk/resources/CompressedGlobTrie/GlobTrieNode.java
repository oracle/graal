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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.UnmodifiableEconomicSet;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.BuildPhaseProvider.AfterAnalysis;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.util.GlobUtils;

public class GlobTrieNode<C> {

    private String content;
    @UnknownObjectField(availability = AfterAnalysis.class, fullyQualifiedTypes = {"java.util.HashMap", "java.util.ImmutableCollections$MapN", "java.util.ImmutableCollections$Map1"}) //
    private Map<String, GlobTrieNode<C>> children;
    @UnknownPrimitiveField(availability = AfterAnalysis.class) //
    private boolean isLeaf;
    @UnknownPrimitiveField(availability = AfterAnalysis.class) //
    private boolean isNewLevel;

    /*
     * While the Trie data structure is general-purpose, this field is used to store information
     * that we know must not leak into an image for the only current use case of the Trie: this
     * field stores the source and origin of resources, which are often absolute paths of the image
     * build machine.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private EconomicSet<C> hostedOnlyContent;
    @UnknownObjectField(availability = AfterAnalysis.class, fullyQualifiedTypes = "org.graalvm.collections.EconomicMapImpl", canBeNull = true) //
    private EconomicSet<C> runtimeContent;

    protected GlobTrieNode() {
        content = "";
        children = new HashMap<>();
        isLeaf = false;
        isNewLevel = false;
        if (SubstrateUtil.HOSTED) {
            hostedOnlyContent = EconomicSet.create();
        }
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

    protected void makeNodeInternal() {
        isLeaf = false;
    }

    public String getContent() {
        return content;
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    protected UnmodifiableEconomicSet<C> getHostedOnlyContent() {
        return hostedOnlyContent;
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    protected void removeHostedOnlyContent(List<C> ac) {
        hostedOnlyContent.removeAll(ac);
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    protected void addHostedOnlyContent(C ac) {
        this.hostedOnlyContent.add(ac);
    }

    protected UnmodifiableEconomicSet<C> getRuntimeContent() {
        return runtimeContent == null ? EconomicSet.emptySet() : runtimeContent;
    }

    protected void addRuntimeContent(C ac) {
        if (runtimeContent == null) {
            runtimeContent = EconomicSet.create();
        }
        runtimeContent.add(ac);
    }

    public List<GlobTrieNode<C>> getChildren() {
        List<GlobTrieNode<C>> result = new ArrayList<>(children.size());
        for (GlobTrieNode<C> child : children.values()) {
            result.add(child);
        }
        return result;
    }

    protected GlobTrieNode<C> getChild(String child) {
        return children.get(child);
    }

    protected void removeChildren(List<GlobTrieNode<C>> childKeys) {
        for (var child : childKeys) {
            /*
             * we need exact name of the child key in order to delete it. In case when we have a
             * complex level (with stars), all children from the same level will have
             * SAME_LEVEL_IDENTIFIER, so we must append it here
             */
            String sameLevel = !child.isNewLevel() ? GlobUtils.SAME_LEVEL_IDENTIFIER : "";
            String childKey = child.getContent() + sameLevel;
            children.remove(childKey);
        }
    }

    protected GlobTrieNode<C> getChildFromSameLevel(String child) {
        return children.get(child + GlobUtils.SAME_LEVEL_IDENTIFIER);
    }

    protected GlobTrieNode<C> addChild(String child, GlobTrieNode<C> childValue) {
        StringBuilder sb = new StringBuilder(child);

        // to make difference between a*b* (represented as: a* -> b*#)
        // and a*/b* (represented as: a* -> b*) append # when current node is a part of previous one
        if (!childValue.isNewLevel()) {
            sb.append(GlobUtils.SAME_LEVEL_IDENTIFIER);
        }

        /* only add if we don't have same child to avoid duplicates */
        children.putIfAbsent(sb.toString(), childValue);
        return children.get(sb.toString());
    }

    protected List<StarTrieNode<C>> getChildrenWithStar() {
        List<StarTrieNode<C>> result = new ArrayList<>();
        for (GlobTrieNode<C> child : children.values()) {
            if (child instanceof StarTrieNode) {
                result.add((StarTrieNode<C>) child);
            }
        }
        return result;
    }

    protected List<LiteralNode<C>> getChildrenWithLiteral() {
        List<LiteralNode<C>> result = new ArrayList<>();
        for (GlobTrieNode<C> child : children.values()) {
            if (child instanceof LiteralNode) {
                result.add((LiteralNode<C>) child);
            }
        }
        return result;
    }

    protected boolean hasChildrenOnSameLevel() {
        for (GlobTrieNode<C> child : children.values()) {
            if (!child.isNewLevel()) {
                return true;
            }
        }
        return false;
    }

    protected DoubleStarNode<C> getDoubleStarNode() {
        return (DoubleStarNode<C>) getChild(GlobUtils.STAR_STAR);
    }

    /**
     * This function makes all Map/List fields of the class immutable and more efficient. It should
     * only be called once, from the root of the structure, since it traverses through all nodes in
     * the structure.
     */
    protected void trim() {
        for (GlobTrieNode<C> child : children.values()) {
            child.trim();
        }

        children = Map.copyOf(children);
    }
}
