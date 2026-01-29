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
package jdk.graal.compiler.nodes.spi;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Supplies profiling data for methods to the Graal compiler.
 * <p>
 * A ProfileProvider abstracts where profiles come from (e.g., runtime counters, recorded metadata,
 * synthetic defaults) and how they are combined. The returned {@link ProfilingInfo} is a stable
 * snapshot suitable for consumption by compiler phases and nodes. If no information is available,
 * implementations should return a non-null {@link ProfilingInfo} that reports "not
 * recorded"/"unknown" rather than {@code null}.
 * <p>
 * Thread-safety: Implementations must be safe to call from compilation threads. Returned
 * {@code ProfilingInfo} should be immutable or otherwise safe for concurrent read-only use.
 * <p>
 * Performance: Calls to this interface may be frequent; implementations should avoid expensive
 * recomputation and unnecessary allocation where practical.
 */
public interface ProfileProvider {

    /**
     * @see ResolvedJavaMethod#getProfilingInfo(boolean, boolean)
     */
    ProfilingInfo getProfilingInfo(NodeSourcePosition callingContext, ResolvedJavaMethod method);

    /**
     * Returns profiling information for the given method in the specified context. The
     * {@code callingContext} key selects a context-sensitive partition of the profiling data, i.e.,
     * the context under which the profile should be injected into the graph. Implementations must
     * return a non-null {@link ProfilingInfo}. If no data is available for the requested
     * method-and-context pair, the returned object represents a context-insensitive aggregation of
     * all the profiles under the prefix of the specified calling context.
     * <p>
     * <strong>If context sensitivity is not available in the current virtual machine or call chain
     * the returned profiles are a context insensitive view of profiling data.</strong>
     * <p>
     * If {@code callingContext==null} returns a context insensitive profile.
     */
    ProfilingInfo getProfilingInfo(NodeSourcePosition callingContext, ResolvedJavaMethod method, boolean includeNormal, boolean includeOSR);

}
