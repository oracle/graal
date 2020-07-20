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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import com.oracle.svm.configure.json.JsonWriter;

/** Represents a rule that includes or excludes a set of Java classes. */
public final class RuleNode {

    /** Everything that is not included is considered excluded. */
    private static final Inclusion DEFAULT_INCLUSION = Inclusion.Exclude;

    private static final String CHILDREN_PATTERN = "*";
    private static final String DESCENDANTS_PATTERN = "**";

    /** Inclusion status of a {@link RuleNode}. */
    public enum Inclusion {
        Include("+"),
        Exclude("-");

        final String s;

        Inclusion(String s) {
            this.s = s;
        }

        @Override
        public String toString() {
            return s;
        }
    }

    /** The non-qualified name. The qualified name is derived from the names of all parents. */
    private final String name;

    /** Inclusion for the exact qualified name when queried via {@link #treeIncludes}. */
    private Inclusion inclusion;

    /** Inclusion for immediate children except those in {@link #children}. */
    private Inclusion childrenInclusion;

    /** Inclusion for descendants not covered by {@link #children} or {@link #childrenInclusion}. */
    private Inclusion descendantsInclusion;

    /** Explicit rules for all immediate children. */
    private Map<String, RuleNode> children;

    public static RuleNode createRoot() {
        return new RuleNode("");
    }

    private RuleNode(String unqualifiedName) {
        this.name = unqualifiedName;
    }

    public void addOrGetChildren(String qualifiedName, Inclusion leafInclusion) {
        if (leafInclusion != Inclusion.Include && leafInclusion != Inclusion.Exclude) {
            throw new IllegalArgumentException(leafInclusion + " not supported");
        }
        RuleNode parent = this;
        StringTokenizer tokenizer = new StringTokenizer(qualifiedName, ".", false);
        /*
         * We split the qualified name, such as "com.sun.crypto.**", into its individual tokens.
         * Only the last token and possibly the second-to-last token, "**" and "crypto" in this
         * example, are the tokens for which the include or exclude action is relevant. Intermediate
         * tokens, such as "com" and "sun", are irrelevant unless rules for them are added in a
         * separate call. Otherwise, we leave them undecided for inclusion/exclusion.
         */
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            boolean isLeaf = !tokenizer.hasMoreTokens();
            boolean isPattern = (token.indexOf('*') != -1);
            if (isPattern) {
                boolean recursive = token.equals(DESCENDANTS_PATTERN);
                if (!isLeaf || (!recursive && !token.equals(CHILDREN_PATTERN))) {
                    throw new IllegalArgumentException(qualifiedName + ": only " + CHILDREN_PATTERN + " and " + DESCENDANTS_PATTERN +
                                    " are allowed as the entire pattern (no complex patterns), and only in the last position");
                }
                if (recursive) { // "**" replaces "*" and all descendant rules
                    parent.descendantsInclusion = leafInclusion;
                    parent.childrenInclusion = null;
                    parent.children = null;
                } else {
                    parent.childrenInclusion = leafInclusion;
                    if (parent.children != null) { // "*" replaces all immediate child rules
                        parent.children.values().removeIf(c -> {
                            c.inclusion = null;
                            return c.isRedundantLeaf();
                        });
                    }
                }
            } else {
                RuleNode node = null;
                if (parent.children != null) {
                    node = parent.children.get(token);
                } else {
                    parent.children = new HashMap<>();
                }
                if (node != null) {
                    if (isLeaf) {
                        node.inclusion = leafInclusion;
                    }
                } else {
                    node = new RuleNode(token);
                    node.inclusion = isLeaf ? leafInclusion : null;
                    parent.children.put(token, node);
                }
                parent = node;
            }
        }
    }

    private boolean isLeafNode() {
        return children == null || children.isEmpty();
    }

    /**
     * Transforms a tree that must not contain recursive rules so that it becomes a smaller tree
     * with recursive rules. The resulting tree is not as precise as the original tree however, and
     * may match expressions that were not explicitly included in the original tree, but it never
     * matches expressions that were explicitly excluded in the original tree.
     */
    public void reduceExhaustiveTree() {
        if (children != null) {
            for (RuleNode child : children.values()) {
                child.reduceExhaustiveTree0();
            }
        }
        removeRedundantNodes();
    }

    private int reduceExhaustiveTree0() {
        if (descendantsInclusion != null) {
            throw new IllegalStateException("Recursive rules are not allowed");
        }
        if (children != null) {
            int sum = 0;
            for (RuleNode child : children.values()) {
                sum += child.reduceExhaustiveTree0();
            }
            if (descendantsInclusion == null) {
                descendantsInclusion = (sum > 0) ? Inclusion.Include : Inclusion.Exclude;
            }
        }
        int score = 0;
        score = addToScore(inclusion, score);
        score = addToScore(childrenInclusion, score);
        score = addToScore(descendantsInclusion, score);
        return score;
    }

    private static int addToScore(Inclusion inclusion, int score) {
        if (inclusion == Inclusion.Include) {
            return score + 1;
        } else if (inclusion == Inclusion.Exclude) {
            return score - 1;
        }
        return score;
    }

    /** Removes redundant nodes that are covered by a recursive ancestor. */
    public void removeRedundantNodes() {
        removeRedundantDescendants(DEFAULT_INCLUSION);
    }

    private void removeRedundantDescendants(Inclusion inheritedDescendantsInclusion) {
        if (!isLeafNode()) {
            Inclusion descendantsDefaultInclusion = (descendantsInclusion != null) ? descendantsInclusion : inheritedDescendantsInclusion;
            Inclusion childrenDefaultInclusion = (childrenInclusion != null) ? childrenInclusion : descendantsDefaultInclusion;
            children.values().removeIf(c -> c.removeRedundantNodes(childrenDefaultInclusion, descendantsDefaultInclusion));
        }
    }

    private boolean removeRedundantNodes(Inclusion defaultInclusion, Inclusion descendantsDefaultInclusion) {
        assert defaultInclusion == Inclusion.Include || descendantsDefaultInclusion == Inclusion.Exclude;
        assert descendantsDefaultInclusion == Inclusion.Include || descendantsDefaultInclusion == Inclusion.Exclude;
        removeRedundantDescendants(descendantsDefaultInclusion);
        if (inclusion == defaultInclusion) {
            inclusion = null;
        }
        if (descendantsInclusion == descendantsDefaultInclusion) {
            descendantsInclusion = null;
        }
        if (childrenInclusion == descendantsInclusion || (descendantsInclusion == null && childrenInclusion == descendantsDefaultInclusion)) {
            childrenInclusion = null;
        }
        return isRedundantLeaf();
    }

    private boolean isRedundantLeaf() {
        return inclusion == null && childrenInclusion == null && descendantsInclusion == null && isLeafNode();
    }

    public void printJsonTree(OutputStream out) throws IOException {
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(out))) {
            writer.append('{');
            writer.indent().newline();
            writer.quote("rules").append(": [").indent().newline();
            final boolean[] isFirstRule = {true};
            printJsonEntries(writer, isFirstRule, "");
            writer.unindent().newline();
            writer.append(']').unindent().newline();
            writer.append('}').newline();
        }
    }

    private void printJsonEntries(JsonWriter writer, boolean[] isFirstRule, String parentQualified) throws IOException {
        String qualified = parentQualified.isEmpty() ? name : (parentQualified + '.' + name);
        // NOTE: the order in which these rules are printed is important!
        String patternBegin = qualified.isEmpty() ? qualified : (qualified + ".");
        printJsonRule(writer, isFirstRule, patternBegin + DESCENDANTS_PATTERN, descendantsInclusion);
        printJsonRule(writer, isFirstRule, patternBegin + CHILDREN_PATTERN, childrenInclusion);
        printJsonRule(writer, isFirstRule, qualified, inclusion);
        if (children != null) {
            RuleNode[] sorted = children.values().toArray(new RuleNode[0]);
            Arrays.sort(sorted, Comparator.comparing(child -> child.name));
            for (RuleNode child : sorted) {
                child.printJsonEntries(writer, isFirstRule, qualified);
            }
        }
    }

    private static void printJsonRule(JsonWriter writer, boolean[] isFirstRule, String qualified, Inclusion inclusion) throws IOException {
        if (inclusion == null) {
            return;
        }
        if (!isFirstRule[0]) {
            writer.append(',').newline();
        } else {
            isFirstRule[0] = false;
        }
        writer.append('{');
        switch (inclusion) {
            case Include:
                writer.quote("includeClasses");
                break;
            case Exclude:
                writer.quote("excludeClasses");
                break;
            default:
                throw new IllegalStateException("Unsupported inclusion value: " + inclusion.name());
        }
        writer.append(':');
        writer.quote(qualified);
        writer.append("}");
    }

    public boolean treeIncludes(String qualifiedName) {
        RuleNode current = this;
        Inclusion inheritedInclusion = DEFAULT_INCLUSION;
        StringTokenizer tokenizer = new StringTokenizer(qualifiedName, ".", false);
        while (tokenizer.hasMoreTokens()) {
            if (current.descendantsInclusion != null) {
                inheritedInclusion = current.descendantsInclusion;
            }
            String part = tokenizer.nextToken();
            RuleNode child = (current.children != null) ? current.children.get(part) : null;
            if (child == null) {
                boolean isDirectChild = !tokenizer.hasMoreTokens();
                if (isDirectChild && current.childrenInclusion != null) {
                    return (current.childrenInclusion == Inclusion.Include);
                }
                return (inheritedInclusion == Inclusion.Include);
            }
            current = child;
        }
        return (current.inclusion == Inclusion.Include);
    }

    public RuleNode copy() {
        RuleNode copy = new RuleNode(name);
        copy.inclusion = inclusion;
        copy.childrenInclusion = childrenInclusion;
        copy.descendantsInclusion = descendantsInclusion;
        if (children != null) {
            copy.children = new HashMap<>();
            for (Map.Entry<String, RuleNode> entry : children.entrySet()) {
                RuleNode childCopy = entry.getValue().copy();
                copy.children.put(entry.getKey(), childCopy);
            }
        }
        return copy;
    }
}
