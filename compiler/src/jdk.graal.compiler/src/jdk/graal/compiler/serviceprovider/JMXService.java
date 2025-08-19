/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.serviceprovider;

import java.io.IOException;
import java.util.List;

/**
 * Access to thread specific information made available via Java Management Extensions (JMX). Using
 * this abstraction enables avoiding a dependency to the {@code java.management} and
 * {@code jdk.management} modules.
 */
@LibGraalService
public abstract class JMXService {
    protected abstract long getThreadAllocatedBytes(long id);

    protected abstract long getCurrentThreadCpuTime();

    protected abstract boolean isThreadAllocatedMemorySupported();

    protected abstract boolean isCurrentThreadCpuTimeSupported();

    protected abstract List<String> getInputArguments();

    /**
     * Dumps the heap to {@code outputFile} in hprof format.
     *
     * @param live if true, performs a full GC first so that only live objects are dumped
     * @throws IOException if an IO error occurred during dumping
     */
    protected abstract void dumpHeap(String outputFile, boolean live) throws IOException;

    /**
     * Reports information about time in the garbage collector.
     */
    public interface GCTimeStatistics {
        /**
         * The number of GCs since the creation of this object.
         */
        long getGCCount();

        /**
         * The amount of time spent in the garbage collector since the creation of this object.
         */
        long getGCTimeMillis();

        /**
         * The time since the creation of this object.
         */
        long getElapsedTimeMillis();
    }

    /**
     * Provides access to information about time spent in the garbage collector.
     */
    protected abstract GCTimeStatistics getGCTimeStatistics();
}
