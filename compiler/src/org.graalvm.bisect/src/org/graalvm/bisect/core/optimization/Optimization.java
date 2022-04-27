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

import java.util.Map;

/**
 * Represents an optimization in a compiled method at a particular BCI.
 */
public interface Optimization {
    /**
     * Gets the name of this optimization. Corresponds to the name of the compiler phase or another class which
     * performed this optimization.
     * @return the name of this optimization
     */
    String getOptimizationName();

    /**
     * Gets the name of the event that occurred. Compared to {@link #getOptimizationName()}, it should return a more
     * specific description of the optimization.
     * @return the name of the event that occurred
     */
    String getEventName();

    /**
     * Gets the map of additional properties of this optimization, mapped by the name of the property.
     * @return the map of additional properties
     */
    Map<String, Object> getProperties();

    /**
     * Gets the bci of the position where the optimization was performed. The bci can come from a NodeSourcePosition
     * of a given node or from a FrameState. The value {@link #NO_BCI} means that no fitting bci could be assigned.
     * @return the byte code index of this optimization
     */
    int getBCI();

    /**
     * A special bci value meaning that no byte code index was found.
     */
    int NO_BCI = -1;
}
