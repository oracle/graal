/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.nodes.spi;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;

/**
 * A cache for graphs associated with {@linkplain StructuredGraph#method() methods}.
 */
public interface GraphCache {

    /**
     * Requests that a graph be added to this cache.
     * 
     * @param hasMatureProfilingInfo indicates that the caller has
     *            {@linkplain ProfilingInfo#isMature() mature} profiling info for the method
     *            associated with the graph
     * @return true if {@code graph} was added to this cache, false otherwise
     */
    boolean put(StructuredGraph graph, boolean hasMatureProfilingInfo);

    /**
     * Gets the graph from this cache associated with a given method.
     * 
     * @param method a method for which a cached graph is requested
     * @return the graph cached for {@code method} or null if it does not exist
     */
    StructuredGraph get(ResolvedJavaMethod method);

    /**
     * The cache will remove graphs it considers stale. For example, graphs associated with
     * installed code that has subsequently be deoptimized might be considered stale.
     */
    void removeStaleGraphs();
}
