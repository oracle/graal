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
package com.oracle.svm.configure.filters;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import com.oracle.svm.configure.json.JsonWriter;

/**
 * Represents a Java package and whether that package (and any subpackages that do not exist as
 * direct or indirect child nodes) is included in a specific set.
 */
public final class PackageNode {

    /** Everything that is not included is considered excluded. */
    private static final Inclusion DEFAULT_INCLUSION = Inclusion.Exclude;

    /**
     * Inclusion status of a {@link PackageNode}.
     */
    public enum Inclusion {
        /**
         * Include all classes directly in the package.
         */
        Include("+"),

        /**
         * Exclude all classes directly in the package.
         */
        Exclude("-"),

        /**
         * Classes from the package are not needed or the package doesn't directly contain classes.
         * The inclusion status is flexible to be adjusted according to the subpackages for a
         * smaller set of rules.
         */
        Irrelevant("?");

        final String s;

        Inclusion(String s) {
            this.s = s;
        }

        @Override
        public String toString() {
            return s;
        }
    }

    private final String name;
    private Inclusion inclusion;
    private boolean recursive;

    private Map<String, PackageNode> children;
    private Set<String> modules;

    public static PackageNode createRoot() {
        return new PackageNode("", DEFAULT_INCLUSION, true);
    }

    private PackageNode(String name, Inclusion inclusion, boolean recursive) {
        this.name = name;
        this.inclusion = inclusion;
        this.recursive = recursive;
    }

    public PackageNode addOrGetChild(String qualifiedName, Inclusion inclusion, boolean recursive, String moduleName) {
        if (inclusion != Inclusion.Include && inclusion != Inclusion.Exclude) {
            throw new IllegalArgumentException(inclusion + " not supported");
        }
        PackageNode parent = this;
        StringTokenizer tokenizer = new StringTokenizer(qualifiedName, ".", false);
        /*
         * We split the qualified package name, such as "com.sun.crypto", into its individual
         * packages. Only the innermost package, "crypto" in this example, contains classes relevant
         * to us and therefore it is the package that we actually want to include or exclude.
         * Intermediate packages, such as "com" and "sun", are not relevant to us unless they
         * are/were added as an innermost package in a separate call. Otherwise, we leave them
         * undecided for inclusion/exclusion.
         */
        while (tokenizer.hasMoreTokens()) {
            String part = tokenizer.nextToken();
            boolean intermediate = tokenizer.hasMoreTokens();
            PackageNode node = null;
            if (parent.children != null) {
                node = parent.children.get(part);
            } else {
                parent.children = new HashMap<>();
            }
            if (node != null) {
                if (!intermediate) { // this is the actual package we're interested in
                    node.inclusion = inclusion;
                    node.recursive = recursive;
                    if (recursive) { // overrides rules for subpackages: remove descendants
                        this.children = null;
                    }
                }
            } else {
                Inclusion nodeInclusion = intermediate ? Inclusion.Irrelevant : inclusion;
                boolean nodeRecursive = recursive && !intermediate;
                node = new PackageNode(part, nodeInclusion, nodeRecursive);
                parent.children.put(part, node);
            }
            if (moduleName != null) {
                if (node.modules == null) {
                    node.modules = new HashSet<>();
                }
                node.modules.add((intermediate ? ("(" + inclusion + moduleName + ')') : (inclusion + moduleName)));
            }
            parent = node;
        }
        return parent;
    }

    /**
     * Transforms a tree that contains a node for <b>each</b> individual package (as opposed to a
     * tree where nodes recursively cover subpackages that are not individual nodes in the tree) to
     * result in a smaller set of recursive nodes. This tree is not as precise as the original tree
     * however, and may match packages that were not explicitly included in the original tree, but
     * never matches packages that were explicitly excluded in the original tree.
     */
    public void reduceExhaustiveTree() {
        reduceExhaustiveTree0();
        removeRedundantNodes();
    }

    private int reduceExhaustiveTree0() {
        if (children != null) {
            int sum = 0;
            for (PackageNode child : children.values()) {
                if (child.recursive) {
                    throw new IllegalStateException("Recursive rules not allowed");
                }
                sum += child.reduceExhaustiveTree0();
            }
            if (inclusion == Inclusion.Irrelevant) {
                if (sum > 0) {
                    inclusion = Inclusion.Include;
                } else {
                    inclusion = Inclusion.Exclude;
                }
            }
            recursive = true;
        }
        int score;
        if (inclusion == Inclusion.Include) {
            score = +1;
        } else if (inclusion == Inclusion.Exclude) {
            score = -1;
        } else {
            score = 0;
        }
        return score;
    }

    /**
     * Removes redundant nodes that are covered by a recursive ancestor.
     */
    public void removeRedundantNodes() {
        removeRedundantNodes(DEFAULT_INCLUSION);
    }

    private boolean removeRedundantNodes(Inclusion ancestorRecursiveInclusion) {
        assert ancestorRecursiveInclusion == Inclusion.Include || ancestorRecursiveInclusion == Inclusion.Exclude;
        if (children != null) {
            Inclusion thisRecursiveInclusion = recursive ? inclusion : ancestorRecursiveInclusion;
            children.values().removeIf(child -> child.removeRedundantNodes(thisRecursiveInclusion));
        }
        if (inclusion == ancestorRecursiveInclusion) {
            inclusion = Inclusion.Irrelevant;
            recursive = false;
            return (children == null || children.isEmpty());
        }
        return false;
    }

    public void printTree(boolean withIntermediateNodes) {
        prefixTraverse("", null, (node, qualified, parent) -> {
            boolean essential = (node.inclusion != Inclusion.Irrelevant);
            if (withIntermediateNodes || essential) {
                StringBuilder line = new StringBuilder();
                line.append(essential ? ' ' : '[');
                line.append(node.inclusion).append(qualified).append(node.recursive ? ".**" : ".*");
                if (node.modules != null) {
                    line.append("   from ").append(node.modules.stream().sorted().collect(Collectors.joining(", ")));
                }
                if (!essential) {
                    line.append(']');
                }
                System.out.println(line);
            }
        });
    }

    public void printJsonTree(OutputStream out) throws IOException {
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(out))) {
            writer.append('{');
            writer.indent().newline();
            writer.quote("rules").append(": [").indent().newline();
            final boolean[] isFirst = {true};
            prefixTraverse("", null, (node, qualified, parent) -> {
                try {
                    if (node.inclusion != Inclusion.Irrelevant) {
                        if (!isFirst[0]) {
                            writer.append(',').newline();
                        } else {
                            isFirst[0] = false;
                        }
                        writer.append('{');
                        switch (node.inclusion) {
                            case Include:
                                writer.quote("includeClasses");
                                break;
                            case Exclude:
                                writer.quote("excludeClasses");
                                break;
                            default:
                                throw new IllegalStateException("Unsupported inclusion value: " + node.inclusion.name());
                        }
                        writer.append(':');
                        writer.quote(qualified + (node.recursive ? ".**" : ".*"));
                        writer.append("}");
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            writer.unindent().newline();
            writer.append(']').unindent().newline();
            writer.append('}').newline();
        }
    }

    public boolean treeIncludes(String qualifiedPackage) {
        PackageNode current = this;
        Inclusion lastRecursive = DEFAULT_INCLUSION;
        StringTokenizer tokenizer = new StringTokenizer(qualifiedPackage, ".", false);
        while (tokenizer.hasMoreTokens()) {
            if (current.recursive) {
                lastRecursive = current.inclusion;
            }
            String part = tokenizer.nextToken();
            PackageNode child = (current.children != null) ? current.children.get(part) : null;
            if (child == null) {
                return (lastRecursive == Inclusion.Include);
            }
            current = child;
        }
        return (current.inclusion == Inclusion.Include);
    }

    public PackageNode copy() {
        PackageNode copy = new PackageNode(name, inclusion, recursive);
        if (children != null) {
            copy.children = new HashMap<>();
            for (Map.Entry<String, PackageNode> entry : children.entrySet()) {
                PackageNode childCopy = entry.getValue().copy();
                copy.children.put(entry.getKey(), childCopy);
            }
        }
        copy.modules = (modules != null) ? new HashSet<>(modules) : null;
        return copy;
    }

    private interface NodeVisitor {
        void visit(PackageNode node, String qualified, PackageNode parent);
    }

    private void prefixTraverse(String prefix, PackageNode parent, NodeVisitor visitor) {
        String qualified = prefix.isEmpty() ? name : (prefix + '.' + name);
        visitor.visit(this, qualified, parent);
        if (children != null) {
            children.values().stream().sorted(Comparator.comparing(child -> child.name))
                            .forEach(child -> child.prefixTraverse(qualified, this, visitor));
        }
    }
}
