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
package org.graalvm.bisect.core.optimization;

import java.util.List;

import org.graalvm.bisect.util.EconomicMapUtil;
import org.graalvm.bisect.util.Writer;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

/**
 * Represents an optimization in a compiled method at a particular BCI.
 */
public class OptimizationImpl implements Optimization {
    /**
     * Gets the bci of the position where this optimization was performed. The bci can come from a
     * NodeSourcePosition of a given node or from a FrameState. The value {@link #NO_BCI} means that
     * no fitting bci could be assigned.
     */
    private final int bci;
    /**
     * The name of this optimization. Corresponds to the name of the compiler phase or another class
     * which performed this optimization.
     */
    private final String optimizationName;
    /**
     * The name of the event that occurred, which describes this optimization entry.
     */
    private final String eventName;
    /**
     * The map of additional properties of this optimization, mapped by property name.
     */
    private final EconomicMap<String, Object> properties;

    public OptimizationImpl(String optimizationName, String eventName, int bci, EconomicMap<String, Object> properties) {
        this.optimizationName = optimizationName;
        this.eventName = eventName;
        this.bci = bci;
        this.properties = (properties == null) ? EconomicMap.emptyMap() : properties;
    }

    @Override
    public String getOptimizationName() {
        return optimizationName;
    }

    /**
     * Gets the name of the event that occurred. Compared to {@link #getOptimizationName()}, it
     * should return a more specific description of the optimization.
     *
     * @return the name of the event that occurred
     */
    @Override
    public String getName() {
        return eventName;
    }

    @Override
    public EconomicMap<String, Object> getProperties() {
        return properties;
    }

    @Override
    public int getBCI() {
        return bci;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getOptimizationName()).append(" ").append(getName()).append(" at bci ").append(getBCI());
        if (properties.isEmpty()) {
            return sb.toString();
        }
        sb.append(" {");
        boolean first = true;
        MapCursor<String, Object> cursor = properties.getEntries();
        while (cursor.advance()) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(cursor.getKey()).append(": ").append(cursor.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public void writeHead(Writer writer) {
        writer.writeln(toString());
    }

    @Override
    public int hashCode() {
        int result = bci;
        result = 31 * result + optimizationName.hashCode();
        result = 31 * result + eventName.hashCode();
        result = 31 * result + EconomicMapUtil.hashCode(properties);
        return result;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof OptimizationImpl)) {
            return false;
        }
        OptimizationImpl other = (OptimizationImpl) object;
        return bci == other.bci && optimizationName.equals(other.optimizationName) &&
                        eventName.equals(other.eventName) && EconomicMapUtil.equals(properties, other.properties);
    }

    @Override
    public List<OptimizationTreeNode> getChildren() {
        return List.of();
    }
}
