/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.classinitialization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Pair;

import com.oracle.svm.core.util.UserError;

/**
 * Maintains user and system configuration for class initialization in Native Image.
 *
 * The configuration is maintained in a tree, where the root (
 * {@link ClassInitializationConfiguration#root}) represents the whole package hierarchy. Every
 * non-leaf node in the tree represents a package and leafs can represent either packages or
 * classes. The {@link InitKind} of a class is determined by looking at the longest path from the
 * root that * matches the qualified name of the class and has a non-null kind.
 *
 * The kind ({@link InitializationNode#kind}) of tree nodes can be either: <code>null</code> which
 * denotes there is no initialization config or one of the {@link InitKind}s that are set over the
 * command line or programmatically through features. Once defined, a node can not be changed,
 * however, the node children can override the value for nested packages or classes.
 *
 * Every node tracks a list of reasons for the set configuration. This list helps the users debug
 * conflicts in the configuration.
 */
public class ClassInitializationConfiguration {
    private static final String ROOT_QUALIFIER = "";
    private static final int MAX_NUMBER_OF_REASONS = 3;

    private InitializationNode root = new InitializationNode("", null, null, false);

    public synchronized void insert(String classOrPackage, InitKind kind, String reason, boolean strict) {
        assert kind != null;
        insertRec(root, qualifierList(classOrPackage), kind, reason, strict);
    }

    synchronized Pair<InitKind, Boolean> lookupKind(String classOrPackage) {
        Pair<InitializationNode, Boolean> kindPair = lookupRec(root, qualifierList(classOrPackage), null);
        return Pair.create(kindPair.getLeft() == null ? null : kindPair.getLeft().kind, kindPair.getRight());
    }

    synchronized String lookupReason(String classOrPackage) {
        assert lookupRec(root, qualifierList(classOrPackage), null).getLeft() != null : "Path for a file should be ";
        return String.join(" and ", lookupRec(root, qualifierList(classOrPackage), null).getLeft().reasons);
    }

    private static List<String> qualifierList(String classOrPackage) {
        List<String> qualifiers = classOrPackage.isEmpty() ? Collections.emptyList() : Arrays.asList(classOrPackage.split("\\."));
        List<String> prefixed = new ArrayList<>(Collections.singletonList(ROOT_QUALIFIER));
        prefixed.addAll(qualifiers);
        return prefixed;
    }

    private void insertRec(InitializationNode node, List<String> classOrPackage, InitKind kind, String reason, boolean strict) {
        assert !classOrPackage.isEmpty();
        assert node.qualifier.equals(classOrPackage.get(0));
        if (classOrPackage.size() == 1) {
            if (node.kind == null) {
                node.kind = kind;
                node.strict = strict;
            } else if (node.kind == kind) {
                if (node.reasons.size() < MAX_NUMBER_OF_REASONS) {
                    node.reasons.add(reason);
                } else if (node.reasons.size() == MAX_NUMBER_OF_REASONS) {
                    node.reasons.add("others");
                }
            } else {
                if (node.strict) {
                    throw UserError.abort("Incompatible change of initialization policy for " + qualifiedName(node) + ": trying to change " + node.kind + " " + String.join(" and ", node.reasons) +
                                    " to " + kind + " " + reason);
                } else {
                    node.kind = node.kind.max(kind);
                }
            }
        } else {
            List<String> tail = new ArrayList<>(classOrPackage);
            tail.remove(0);
            String nextQualifier = tail.get(0);
            if (!node.children.containsKey(nextQualifier)) {
                node.children.put(nextQualifier, new InitializationNode(nextQualifier, node, null, false, reason));
                assert node.children.containsKey(nextQualifier);
            }
            insertRec(node.children.get(nextQualifier), tail, kind, reason, strict);
        }
    }

    private Pair<InitializationNode, Boolean> lookupRec(InitializationNode node, List<String> classOrPackage, InitializationNode lastNonNullKind) {
        List<String> tail = new ArrayList<>(classOrPackage);
        tail.remove(0);
        boolean reachedBottom = tail.isEmpty();
        if (!reachedBottom && node.children.containsKey(tail.get(0))) {
            return lookupRec(node.children.get(tail.get(0)), tail, node.kind != null ? node : lastNonNullKind);
        } else if (node.kind == null) {
            return Pair.create(lastNonNullKind, reachedBottom);
        } else {
            return Pair.create(node, reachedBottom);
        }
    }

    private static String qualifiedName(InitializationNode node) {
        InitializationNode currentNode = node;
        List<String> name = new ArrayList<>();
        while (currentNode != null) {
            name.add(currentNode.qualifier);
            currentNode = currentNode.parent;
        }
        Collections.reverse(name);
        name.remove(0);

        return String.join(".", name);
    }

    synchronized List<ClassOrPackageConfig> allConfigs() {
        LinkedList<InitializationNode> printingQueue = new LinkedList<>();
        printingQueue.add(root);
        ArrayList<ClassOrPackageConfig> allClasses = new ArrayList<>();
        while (!printingQueue.isEmpty()) {
            InitializationNode node = printingQueue.remove();
            if (node.kind != null) {
                String name = node.qualifier.isEmpty() ? "whole type hierarchy" : qualifiedName(node);
                allClasses.add(new ClassOrPackageConfig(name, node.reasons, node.kind));
            }

            node.children.getValues().forEach(printingQueue::push);
        }
        return allClasses;
    }
}

final class InitializationNode {
    final String qualifier;
    boolean strict;
    InitKind kind;
    final EconomicSet<String> reasons = EconomicSet.create();

    final InitializationNode parent;
    final EconomicMap<String, InitializationNode> children = EconomicMap.create();

    InitializationNode(String qualifier, InitializationNode parent, InitKind kind, boolean strict, String... reasons) {
        this.parent = parent;
        this.qualifier = qualifier;
        this.kind = kind;
        this.strict = strict;
        this.reasons.addAll(Arrays.asList(reasons));
    }
}
