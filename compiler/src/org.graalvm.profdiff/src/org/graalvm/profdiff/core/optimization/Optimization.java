/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.profdiff.util.Writer;

/**
 * Represents an immutable optimization (applied graph transformation) in a compilation unit at a
 * particular source position.
 *
 * An example of an optimization is loop peeling of a loop at a particular source position, which is
 * performed by an {@link OptimizationPhase optimization phase} like {@code LoopPeelingPhase}.
 */
public class Optimization extends OptimizationTreeNode {
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
     * the properties/position is {@code null}, it is omitted.
     *
     * An example of the output in the long form is:
     *
     * <pre>
     * DeadCodeElimination NodeRemoval at bci {java.util.stream.ReferencePipeline$8$1.accept(Object): 13, java.util.Spliterators$ArraySpliterator.forEachRemaining(Consumer): 53}
     * </pre>
     *
     * The short form looks like the example below.
     *
     * <pre>
     * Canonicalizer CanonicalReplacement at bci 18 with {replacedNodeClass: ValuePhi, canonicalNodeClass: Constant}
     * </pre>
     *
     * @param bciLongForm byte code indices should be printed in the long form
     * @return a string representation of this optimization
     */
    private String toString(boolean bciLongForm) {
        StringBuilder sb = new StringBuilder();
        sb.append(getName()).append(" ").append(eventName);
        if (!position.isEmpty()) {
            sb.append(" at bci ");
            if (bciLongForm) {
                formatMap(sb, position);
            } else {
                UnmodifiableMapCursor<String, Integer> positionCursor = position.getEntries();
                if (positionCursor.advance()) {
                    sb.append(positionCursor.getValue());
                }
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
     * Writes {@link #toString(boolean) the representation of this optimization} to the destination
     * writer according to the current verbosity level.
     *
     * @param writer the destination writer
     */
    @Override
    public void writeHead(Writer writer) {
        writer.writeln(toString(writer.getVerbosityLevel().isBciLongForm()));
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
}
