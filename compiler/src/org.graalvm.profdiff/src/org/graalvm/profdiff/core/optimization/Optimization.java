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
package org.graalvm.profdiff.core.optimization;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicMapUtil;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.graalvm.profdiff.core.inlining.InliningPath;
import org.graalvm.profdiff.core.Writer;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;

/**
 * Represents an immutable optimization (applied graph transformation) in a compilation unit at a
 * particular source position.
 *
 * An example of an optimization is loop peeling of a loop at a particular source position, which is
 * performed by an {@link OptimizationPhase optimization phase} like {@code LoopPeelingPhase}.
 */
public class Optimization extends OptimizationTreeNode {
    /**
     * A special bci value indicating that the true bci is unknown.
     */
    public static final int UNKNOWN_BCI = -1;

    /**
     * The name of the transformation performed by the optimization phase. One optimization phase
     * can perform several transformations.
     */
    private final String eventName;

    /**
     * An ordered map that represents the position of a significant node related to this
     * optimization. It maps method names to byte code indices, starting with the method containing
     * the significant node and its bci. If the node does not belong to the root method in the
     * compilation unit, the map also contains the method names of the method's callsites mapped to
     * the byte code indices of their invokes.
     *
     * A significant node is a node that describes the applied transformation well. For instance, it
     * is the canonicalized node in case of a canonicalization. For loop transformations, it could
     * be the position of a {@code LoopBeginNode}.
     */
    private final UnmodifiableEconomicMap<String, Integer> position;

    /**
     * A map of additional properties of this optimization, mapped by property name.
     */
    private final UnmodifiableEconomicMap<String, Object> properties;

    /**
     * A pre-calculated hash code.
     */
    private final int cachedHashCode;

    /**
     * Constructs an optimization.
     *
     * @param optimizationName the name of this optimization
     * @param eventName a more specific description of the optimization
     * @param position a position of a significant node related to this optimization
     * @param properties a map of additional properties of this optimization, mapped by property
     *            name
     */
    @SuppressWarnings("this-escape")
    public Optimization(String optimizationName,
                    String eventName,
                    UnmodifiableEconomicMap<String, Integer> position,
                    UnmodifiableEconomicMap<String, Object> properties) {
        super(optimizationName);
        this.eventName = eventName;
        this.position = (position == null) ? EconomicMap.emptyMap() : position;
        this.properties = (properties == null) ? EconomicMap.emptyMap() : properties;
        cachedHashCode = calculateHashCode();
    }

    /**
     * Gets the name of the transformation performed by the optimization phase.
     */
    public String getEventName() {
        return eventName;
    }

    /**
     * Gets the map of additional properties of this optimization, mapped by property name.
     *
     * As an example, the properties of a canonicalization could be:
     *
     * <pre>
     * {replacedNodeClass: +, canonicalNodeClass: Constant}
     * </pre>
     *
     * @return the map of additional properties
     */
    public UnmodifiableEconomicMap<String, Object> getProperties() {
        return properties;
    }

    /**
     * Gets an ordered map that represents the position of a significant node related to this
     * optimization. It maps method names to byte code indices, starting with the method containing
     * the significant node and its bci. If the node does not belong the root method in the
     * compilation unit, the map also contains the method names of the method's callsites mapped to
     * the byte code indices of their invokes.
     *
     * @return an ordered map that represents the position of a significant node
     */
    public UnmodifiableEconomicMap<String, Integer> getPosition() {
        return position;
    }

    /**
     * Returns the bci of this optimization relative to an enclosing method.
     *
     * As an example, if the position of this optimization is:
     *
     * <pre>
     * {c(): 4, b(): 3, a(): 2}
     * </pre>
     *
     * Then, the method returns 2 for method {@code a()}, 3 for method {@code b()}, and 4 for method
     * {@code c()}.
     *
     * @param enclosingMethod an enclosing method
     * @return the bci of this optimization relative to an enclosing method
     */
    private int getRelativeBCI(InliningTreeNode enclosingMethod) {
        assert !position.isEmpty() : "the position must not be empty";
        InliningPath optimizationPath = InliningPath.ofEnclosingMethod(this);
        InliningPath enclosingMethodPath = InliningPath.fromRootToNode(enclosingMethod);
        if (enclosingMethodPath.matches(optimizationPath)) {
            return position.getValues().iterator().next();
        }
        assert enclosingMethodPath.isPrefixOf(optimizationPath) : "the path must lead to an enclosing method of the optimization";
        return optimizationPath.get(enclosingMethodPath.size()).getCallsiteBCI();
    }

    /**
     * Appends a representation of an {@link EconomicMap} to a {@link StringBuilder}.
     *
     * The output format is:
     *
     * <pre>
     * {key1: value1, key2: value2, ...}
     * </pre>
     *
     * @param sb the builder that is extended with the representation
     * @param map the map that is formatted
     */
    private static void formatMap(StringBuilder sb, UnmodifiableEconomicMap<String, ?> map) {
        sb.append('{');
        boolean first = true;
        UnmodifiableMapCursor<String, ?> cursor = map.getEntries();
        while (cursor.advance()) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(cursor.getKey()).append(": ").append(cursor.getValue());
        }
        sb.append('}');
    }

    /**
     * Returns a string representation of this optimization with its properties and the position. If
     * the properties/position are {@code null}, it is omitted.
     *
     * An example of the output in the long form is:
     *
     * <pre>
     * Canonicalizer CanonicalReplacement at bci {b(): 53, a(): 13} with {replacedNodeClass: ValuePhi, canonicalNodeClass: Constant}
     * </pre>
     *
     * The short form prints a bci relative to an enclosing method.
     *
     * <pre>
     * Canonicalizer CanonicalReplacement at bci 53 with {replacedNodeClass: ValuePhi, canonicalNodeClass: Constant}
     * </pre>
     *
     * If {@code enclosingMethod} is {@code null}, the method prints the bci of the innermost
     * enclosing method (53 in the example). If the enclosing method were specified as {@code a()}
     * in the above example, the bci would be 13.
     *
     * @param bciLongForm byte code indices should be printed in the long form
     * @param enclosingMethod an enclosing method or {@code null}; ignored if the long form is
     *            enabled
     * @return a string representation of this optimization
     */
    private String toString(boolean bciLongForm, InliningTreeNode enclosingMethod) {
        StringBuilder sb = new StringBuilder();
        sb.append(getName()).append(" ").append(eventName);
        if (!position.isEmpty()) {
            sb.append(" at bci ");
            if (bciLongForm) {
                formatMap(sb, position);
            } else if (enclosingMethod == null) {
                sb.append(position.getValues().iterator().next());
            } else {
                sb.append(getRelativeBCI(enclosingMethod));
            }
        }
        if (properties.isEmpty()) {
            return sb.toString();
        }
        sb.append(" with ");
        formatMap(sb, properties);
        return sb.toString();
    }

    /**
     * Writes {@link #toString the representation of this optimization} to the destination writer
     * according to the option values.
     *
     * @param writer the destination writer
     */
    @Override
    public void writeHead(Writer writer) {
        writer.writeln(toString(writer.getOptionValues().isBCILongForm(), null));
    }

    /**
     * Writes {@link #toString the representation of this optimization} to the destination writer
     * according to the option values. This is a variant of {@link #writeHead(Writer)} that allows
     * specifying a different enclosing method than the innermost enclosing method.
     *
     * This variant is useful in the optimization-context tree, where some optimizations cannot be
     * attributed to their exact position. In the following example, {@code SomeOptimization} is
     * attributed to method {@code a()}, because method {@code b()} is missing from the inlining
     * tree.
     *
     * <pre>
     * A compilation unit of method a()
     *     Optimization-context tree
     *         (root) a()
     *             SomeOptimization at bci {b(): 2, a(): 3}
     * </pre>
     *
     * The short-form bci of {@code SomeOptimization} should be 3 rather than 2.
     *
     * @param writer the destination writer
     * @param enclosingMethod an enclosing method or {@code null}; ignored if long form enabled
     * @see #toString(boolean, InliningTreeNode)
     */
    public void writeHead(Writer writer, InliningTreeNode enclosingMethod) {
        writer.writeln(toString(writer.getOptionValues().isBCILongForm(), enclosingMethod));
    }

    private int calculateHashCode() {
        int result = EconomicMapUtil.hashCode(position);
        result = 31 * result + getName().hashCode();
        result = 31 * result + eventName.hashCode();
        result = 31 * result + EconomicMapUtil.hashCode(properties);
        return result;
    }

    @Override
    public int hashCode() {
        return cachedHashCode;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Optimization)) {
            return false;
        }
        Optimization other = (Optimization) object;
        return cachedHashCode == other.cachedHashCode && eventName.equals(other.eventName) &&
                        getName().equals(other.getName()) && EconomicMapUtil.equals(position, other.position) &&
                        EconomicMapUtil.equals(properties, other.properties);
    }

    /**
     * Writes the representation of this subtree to the destination writer. This is equivalent to
     * writing the representation of this node, because an optimization has no children.
     *
     * @param writer the destination writer
     */
    @Override
    public void writeRecursive(Writer writer) {
        writeHead(writer);
    }

    @Override
    public Optimization cloneMatchingPath(InliningPath prefix) {
        InliningPath path = InliningPath.ofEnclosingMethod(this);
        if (!prefix.isPrefixOf(path)) {
            return null;
        }
        EconomicMap<String, Integer> newPosition = EconomicMap.create();
        UnmodifiableMapCursor<String, Integer> cursor = position.getEntries();
        int i = 0;
        int size = position.size() - prefix.size() + 1;
        while (cursor.advance() && i < size) {
            newPosition.put(cursor.getKey(), cursor.getValue());
            ++i;
        }
        return new Optimization(getName(), eventName, newPosition, properties);
    }
}
