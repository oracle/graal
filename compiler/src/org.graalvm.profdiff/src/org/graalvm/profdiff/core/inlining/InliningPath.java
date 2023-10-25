/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.core.inlining;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.graalvm.profdiff.core.optimization.Optimization;

/**
 * A path in the {@link InliningTree}.
 *
 * Consider the following inlining tree:
 *
 * <pre>
 *          a()
 *       at bci -1
 *      ___/  \_____
 *     /            \
 *    b()           c()
 * at bci 0       at bci 1
 *               __/  \__
 *             /         \
 *           d()         e()
 *        at bci 0     at bci 2
 * </pre>
 *
 * As an example, the right-most path in the inlining tree is
 * {@code a() at bci -1, c() at bci 1, e() at bci 2}.
 *
 * @param elements list of path elements which comprise this path
 */
public record InliningPath(List<PathElement> elements) {
    /**
     * A single element of an inlining path.
     *
     * @param methodName the name of the method
     * @param callsiteBCI the bci of the method's callsite
     */
    public record PathElement(String methodName, int callsiteBCI) {
        /**
         * Returns {@code true} iff the path element matches another path element. A path element
         * matches another element iff the method names are equals and the byte code indexes match
         * ({@link Optimization#UNKNOWN_BCI} is treated as a wildcard). The relation is not
         * transitive due to the possibility of a wildcard.
         *
         * @param otherElement the other path element
         * @return {@code true} if the path elements match
         */
        public boolean matches(PathElement otherElement) {
            if (!Objects.equals(methodName, otherElement.methodName)) {
                return false;
            }
            return callsiteBCI == Optimization.UNKNOWN_BCI || otherElement.callsiteBCI == Optimization.UNKNOWN_BCI || callsiteBCI == otherElement.callsiteBCI;
        }
    }

    /**
     * The empty path.
     */
    public static final InliningPath EMPTY = new InliningPath(List.of());

    /**
     * Creates an inlining path from method names and callsite bcis.
     *
     * For example, the call:
     *
     * <pre>
     * InliningPath.of("a()", -1, "b()", 2, "c()", 3)
     * </pre>
     *
     * Creates the following inlining path:
     *
     * <pre>
     * a() at bci -1, b() at bci 2, c() at bci 3
     * </pre>
     *
     * @param pathElements alternating method names (strings) and bcis (integers)
     * @return a new inlining path or the {@link #EMPTY} path
     */
    public static InliningPath of(Object... pathElements) {
        if (pathElements.length == 0) {
            return EMPTY;
        }
        if (pathElements.length % 2 != 0) {
            throw new IllegalArgumentException("Expected an even number of arguments.");
        }
        List<PathElement> elements = new ArrayList<>();
        for (int i = 0; i < pathElements.length; i += 2) {
            elements.add(new PathElement((String) pathElements[i], (Integer) pathElements[i + 1]));
        }
        return new InliningPath(elements);
    }

    /**
     * Returns {@code true} if the path is empty.
     */
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    /**
     * Gets the number of path elements which compose this path.
     */
    public int size() {
        return elements.size();
    }

    /**
     * Gets a path element at an index.
     *
     * @param index the index of the path element
     * @return the path element at the index
     */
    public PathElement get(int index) {
        return elements.get(index);
    }

    /**
     * Returns the path from a root node to the given node in an inlining tree.
     *
     * @param node a node in an inlining tree
     * @return the path from root the to the node in an inlining tree
     */
    public static InliningPath fromRootToNode(InliningTreeNode node) {
        List<PathElement> path = new ArrayList<>();
        InliningTreeNode iter = node;
        while (iter != null) {
            if (!iter.isIndirect()) {
                path.add(new PathElement(iter.getName(), iter.getBCI()));
            }
            iter = iter.getParent();
        }
        Collections.reverse(path);
        return new InliningPath(path);
    }

    /**
     * Returns {@code true} if this path is a prefix of the other path. This path is a prefix of
     * another path iff each path element in this path matches the corresponding path element (at
     * the same index) from the other path.
     *
     * @param otherPath the other path
     * @return {@code true} iff this path is a prefix of the other path
     */
    public boolean isPrefixOf(InliningPath otherPath) {
        if (elements.size() > otherPath.elements.size()) {
            return false;
        }
        for (int i = 0; i < elements.size(); i++) {
            if (!elements.get(i).matches(otherPath.elements.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the prefix of length {@code prefixLength}.
     *
     * @param prefixLength the length of the prefix
     * @return a path which represents the prefix of the given length
     */
    public InliningPath prefix(int prefixLength) {
        assert prefixLength >= 0 && prefixLength <= elements.size();
        if (prefixLength == 0) {
            return InliningPath.EMPTY;
        }
        return new InliningPath(elements.subList(0, prefixLength));
    }
}
