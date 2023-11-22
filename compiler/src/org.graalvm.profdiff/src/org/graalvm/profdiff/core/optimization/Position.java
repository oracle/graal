/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.core.optimization;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.profdiff.core.inlining.InliningPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An ordered map that represents the position of an optimization.
 *
 * For example, suppose that {@code a()} calls {@code b()} calls {@code c()}. The position of some
 * optimization in {@code c()} at bci 4 might be:
 *
 * <pre>
 * {c(): 4, b(): 3, a(): 2}
 * </pre>
 *
 * Positions are formatted with the innermost method first ({@code c()} in the example) and the
 * outermost method last ({@code a()}).
 *
 * The position of an optimization is defined as the node source position of a node that took part
 * in the transformation. For instance, it is the canonicalized node in case of a canonicalization.
 * For loop transformations, it could be the position of a {@code LoopBeginNode}.
 */
public final class Position implements Comparable<Position> {

    /**
     * The method names in the position. Starts from the innermost method.
     */
    private final List<String> methodNames;

    /**
     * The bcis relative to methods in {@link #methodNames}. These lists must be the same length.
     */
    private final List<Integer> bcis;

    /**
     * The empty position.
     */
    public static final Position EMPTY = new Position(List.of(), List.of());

    /**
     * Creates a new position or returns the {@link #EMPTY} position.
     *
     * The first method name represents the innermost method and the last method name represents the
     * outermost method. For example, let us have an optimization in method {@code b()} at bci 3. If
     * {@code a()} calls {@code b()} at bci 2, the position is created by calling:
     *
     * <pre>
     * Position.create(List.of("b()", "a()"), List.of(3, 2))
     * </pre>
     *
     * @param methodNames the method names comprising the position (innermost first)
     * @param bcis the bcis comprising the position, corresponding to the method names
     * @return a new position or the {@link #EMPTY} position
     */
    public static Position create(List<String> methodNames, List<Integer> bcis) {
        List<String> safeMethodNames = (methodNames == null) ? List.of() : methodNames;
        List<Integer> safeBcis = (bcis == null) ? List.of() : bcis;
        if (safeMethodNames.size() != safeBcis.size()) {
            throw new IllegalArgumentException("Method names must match bcis.");
        }
        if (safeMethodNames.isEmpty()) {
            return EMPTY;
        }
        return new Position(safeMethodNames, safeBcis);
    }

    /**
     * Creates a new position from an ordered map.
     *
     * @param methodBcis an ordered map which maps method names to bcis
     * @return a new position or the {@link #EMPTY} position
     */
    public static Position fromMap(EconomicMap<String, Integer> methodBcis) {
        if (methodBcis == null) {
            return EMPTY;
        }
        List<String> methodNames = new ArrayList<>();
        List<Integer> bcis = new ArrayList<>();
        MapCursor<String, Integer> cursor = methodBcis.getEntries();
        while (cursor.advance()) {
            methodNames.add(cursor.getKey());
            bcis.add(cursor.getValue());
        }
        return create(methodNames, bcis);
    }

    /**
     * Creates a new position from strings and integers.
     *
     * The innermost method is specified first and the outermost method is specified last. For
     * example, the call:
     *
     * <pre>
     * Position.of("c()", 4, "b()", 3, "a()", 2)
     * </pre>
     *
     * Creates the following position:
     *
     * <pre>
     * {c(): 4, b(): 3, a(): 2}
     * </pre>
     *
     * @param methodBcis alternating method names (strings) and bcis (integers)
     * @return a new position or the {@link #EMPTY} position
     */
    public static Position of(Object... methodBcis) {
        if (methodBcis.length % 2 != 0) {
            throw new IllegalArgumentException("Expected an even number of arguments.");
        }
        List<String> methodNames = new ArrayList<>();
        List<Integer> bcis = new ArrayList<>();
        for (int i = 0; i < methodBcis.length; i += 2) {
            methodNames.add((String) methodBcis[i]);
            bcis.add((Integer) methodBcis[i + 1]);
        }
        return create(methodNames, bcis);
    }

    private Position(List<String> methodNames, List<Integer> bcis) {
        this.methodNames = methodNames;
        this.bcis = bcis;
    }

    /**
     * Returns {@code true} if the position is empty. This usually means that an optimization could
     * not be associated with a non-null node source position.
     *
     * @return {@code true} if the position is empty
     */
    public boolean isEmpty() {
        return methodNames.isEmpty();
    }

    /**
     * Returns the bci relative to the outermost method.
     *
     * For example, if the position is:
     *
     * <pre>
     * {c(): 4, b(): 3, a(): 2}
     * </pre>
     *
     * This method returns 2, which is the bci relative to {@code a()}.
     *
     * @return the bci relative to the outermost method
     */
    private int bci() {
        if (bcis.isEmpty()) {
            throw new IllegalStateException("The empty position does not have a bci.");
        }
        return bcis.get(bcis.size() - 1);
    }

    /**
     * Returns the {@link InliningPath path} to the enclosing method.
     *
     * For instance, if the position is:
     *
     * <pre>
     * {c(): 4, b(): 3, a(): 2}
     * </pre>
     *
     * Then the path from the root to the enclosing method is:
     *
     * <pre>
     * a() at bci -1, b() at bci 2, c() at bci 3
     * </pre>
     *
     * @return the path to the enclosing method
     */
    public InliningPath enclosingMethodPath() {
        if (methodNames.isEmpty()) {
            return InliningPath.EMPTY;
        }
        List<InliningPath.PathElement> path = new ArrayList<>();
        int previousBCI = Optimization.UNKNOWN_BCI;
        for (int i = size() - 1; i >= 0; i--) {
            path.add(new InliningPath.PathElement(methodNames.get(i), previousBCI));
            previousBCI = bcis.get(i);
        }
        return new InliningPath(path);
    }

    /**
     * Returns the position relative to an enclosing method.
     *
     * For example, if the position is:
     *
     * <pre>
     * {c(): 4, b(): 3, a(): 2}
     * </pre>
     *
     * The position relative to method {@code b()} is:
     *
     * <pre>
     * {c(): 4, b(): 3}
     * </pre>
     *
     * @param enclosingMethodPath the path of the enclosing method
     * @return the position relative to an enclosing method
     * @throws IllegalArgumentException the provided path does not denote an enclosing method
     */
    public Position relativeTo(InliningPath enclosingMethodPath) {
        InliningPath path = enclosingMethodPath();
        if (!enclosingMethodPath.isPrefixOf(path)) {
            throw new IllegalArgumentException("the provided argument must be an enclosing method");
        }
        int size = size() - enclosingMethodPath.size() + 1;
        return new Position(methodNames.subList(0, size), bcis.subList(0, size));
    }

    /**
     * Returns the bci relative to an enclosing method.
     *
     * As an example, if the position is:
     *
     * <pre>
     * {c(): 4, b(): 3, a(): 2}
     * </pre>
     *
     * Then, the method returns 2 for method {@code a()}, 3 for method {@code b()}, and 4 for method
     * {@code c()}.
     *
     * @param enclosingMethodPath a path to an enclosing method
     * @return the bci relative to an enclosing method
     */
    private int relativeBCI(InliningPath enclosingMethodPath) {
        return relativeTo(enclosingMethodPath).bci();
    }

    private String formatAsMap() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (int i = 0; i < size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(methodNames.get(i)).append(": ").append(bcis.get(i));
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public String toString() {
        return toString(true, null);
    }

    /**
     * Formats the position as a string. The empty position is represented as the empty string.
     *
     * If the long form is enabled, an example of a formatted position is:
     *
     * <pre>
     * {c(): 4, b(): 3, a(): 2}
     * </pre>
     *
     * Otherwise, the position is formatted as a single bci relative to a method. By default, it is
     * relative to the outermost method ({@code a()} in the example). If the path to an enclosing
     * method is specified, the bci is {@link #relativeBCI(InliningPath) relative} to that method.
     *
     * @param longForm byte code indices should be printed in the long form
     * @param enclosingMethodPath the path an enclosing method or {@code null}; ignored if the long
     *            form is enabled
     * @return the position formatted as a string
     */
    public String toString(boolean longForm, InliningPath enclosingMethodPath) {
        if (isEmpty()) {
            return "";
        }
        if (longForm) {
            return formatAsMap();
        } else if (enclosingMethodPath == null || enclosingMethodPath.isEmpty()) {
            return Integer.toString(bci());
        } else {
            return Integer.toString(relativeBCI(enclosingMethodPath));
        }
    }

    /**
     * Returns the size of this position, i.e., the number of method names (or bcis).
     */
    private int size() {
        return methodNames.size();
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodNames, bcis);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Position)) {
            return false;
        }
        Position position = (Position) other;
        return methodNames.equals(position.methodNames) && bcis.equals(position.bcis);
    }

    /**
     * Compares positions lexicographically, starting from the outermost method.
     *
     * As an example, consider the sorted list of positions below. Note that positions begin with
     * the innermost method and end with the outermost method.
     *
     * <pre>
     * EMPTY
     * {a(): 1}
     * {c(): 1, a(): 1}
     * {a(): 2}
     * {b(): 1}
     * </pre>
     *
     * @param other the object to be compared
     * @return the result of the comparison
     */
    @Override
    public int compareTo(Position other) {
        if (size() < other.size()) {
            return -other.compareTo(this);
        }
        for (int i = 1; i <= size(); i++) {
            if (other.size() - i < 0) {
                return 1;
            }
            int order = methodNames.get(size() - i).compareTo(other.methodNames.get(other.size() - i));
            if (order != 0) {
                return order;
            }
            order = bcis.get(size() - i) - other.bcis.get(other.size() - i);
            if (order != 0) {
                return order;
            }
        }
        return 0;
    }
}
