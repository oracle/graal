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

import org.graalvm.collections.Pair;
import org.graalvm.collections.UnmodifiableMapCursor;
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
 */
public final class InliningPath {
    /**
     * A single element of an inlining path. It is composed by a method name and the bci of the
     * method's callsite.
     */
    public static final class PathElement {
        /**
         * The name of the method.
         */
        private final String methodName;

        /**
         * The bci of the method's callsite.
         */
        private final int callsiteBCI;

        public PathElement(String methodName, int callsiteBCI) {
            this.methodName = methodName;
            this.callsiteBCI = callsiteBCI;
        }

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

        /**
         * Gets the method name.
         */
        public String getMethodName() {
            return methodName;
        }

        /**
         * Gets the bci of the method's callsite.
         */
        public int getCallsiteBCI() {
            return callsiteBCI;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PathElement)) {
                return false;
            }
            PathElement element = (PathElement) o;
            return callsiteBCI == element.callsiteBCI && methodName.equals(element.methodName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(callsiteBCI, methodName);
        }
    }

    /**
     * A list of path elements which compose this path.
     */
    private final List<PathElement> path;

    /**
     * The empty path.
     */
    public static final InliningPath EMPTY = new InliningPath(List.of());

    public InliningPath(List<PathElement> path) {
        this.path = path;
    }

    /**
     * Gets the number of path elements which compose this path.
     */
    public int size() {
        return path.size();
    }

    /**
     * Gets a path element at an index.
     *
     * @param index the index of the path element
     * @return the path element at the index
     */
    public PathElement get(int index) {
        return path.get(index);
    }

    /**
     * Gets the path elements which compose this path.
     */
    public Iterable<PathElement> elements() {
        return path;
    }

    /**
     * Returns a path to the optimization's enclosing method, starting from the root method. If the
     * given {@link Optimization} does not have {@link Optimization#getPosition() a position},
     * {@link #EMPTY the empty path} is returned.
     *
     * For instance, if an optimization has the position:
     *
     * <pre>
     * {c(): 4, b(): 3, a(): 2}
     * </pre>
     *
     * Note that {@link Optimization#getPosition() positions} start with the innermost method. Then
     * the path from root to the enclosing method is:
     *
     * <pre>
     * a() at bci -1, b() at bci 2, c() at bci 3
     * </pre>
     *
     * @param optimization the optimization to which the path is computed
     * @return a path to the optimization's enclosing method
     */
    public static InliningPath ofEnclosingMethod(Optimization optimization) {
        if (optimization.getPosition() == null) {
            return EMPTY;
        }
        List<Pair<String, Integer>> pairs = new ArrayList<>();
        UnmodifiableMapCursor<String, Integer> cursor = optimization.getPosition().getEntries();
        while (cursor.advance()) {
            pairs.add(Pair.create(cursor.getKey(), cursor.getValue()));
        }
        Collections.reverse(pairs);
        List<PathElement> path = new ArrayList<>();
        int previousBCI = Optimization.UNKNOWN_BCI;
        for (Pair<String, Integer> pair : pairs) {
            path.add(new PathElement(pair.getLeft(), previousBCI));
            previousBCI = pair.getRight();
        }
        return new InliningPath(path);
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
     * another path iff each path element in this path {@link PathElement#matches(InliningPath)
     * matches} the corresponding path element (at the same index) from the other path.
     *
     * @param otherPath the other path
     * @return {@code true} iff this path is a prefix of the other path
     */
    public boolean isPrefixOf(InliningPath otherPath) {
        if (path.size() > otherPath.path.size()) {
            return false;
        }
        for (int i = 0; i < path.size(); i++) {
            if (!path.get(i).matches(otherPath.path.get(i))) {
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
        assert prefixLength >= 0 && prefixLength <= path.size();
        if (prefixLength == 0) {
            return InliningPath.EMPTY;
        }
        return new InliningPath(path.subList(0, prefixLength));
    }

    /**
     * Returns {@code true} iff this path matches the other path. This path matches the other path
     * iff this path is a prefix of the other path and the other path is a prefix of this path.
     *
     * @param otherPath the other path
     * @return {@code true} iff this path matches the other path
     */
    public boolean matches(InliningPath otherPath) {
        return path.size() == otherPath.size() && isPrefixOf(otherPath);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InliningPath)) {
            return false;
        }
        InliningPath path1 = (InliningPath) o;
        return path.equals(path1.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }
}
