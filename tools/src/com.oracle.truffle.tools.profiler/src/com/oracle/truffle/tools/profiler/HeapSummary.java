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
package com.oracle.truffle.tools.profiler;

/**
 * Represents a summary of total and alive instances and object sizes.
 *
 * @since 19.0
 */
public final class HeapSummary {

    long totalInstances;
    long aliveInstances;
    long totalBytes;
    long aliveBytes;

    HeapSummary() {
    }

    HeapSummary(HeapSummary summary) {
        add(summary);
    }

    void add(HeapSummary summary) {
        this.totalBytes += summary.totalBytes;
        this.aliveInstances += summary.aliveInstances;
        this.totalInstances += summary.totalInstances;
        this.aliveBytes += summary.aliveBytes;
    }

    /**
     * Returns the total number of allocated instances.
     *
     * @since 19.0
     */
    public long getTotalInstances() {
        return totalInstances;
    }

    /**
     * Returns the number of objects that are alive (i.e. not garbage collected).
     *
     * @since 19.0
     */
    public long getAliveInstances() {
        return aliveInstances;
    }

    /**
     * Returns the total number of bytes allocated.
     *
     * @since 19.0
     */
    public long getTotalBytes() {
        return totalBytes;
    }

    /**
     * Returns the number of bytes used by alive object instances.
     *
     * @since 19.0
     */
    public long getAliveBytes() {
        return aliveBytes;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public String toString() {
        return "HeapSummary [totalInstances=" + totalInstances + ", aliveInstances=" + aliveInstances + ", totalBytes=" + totalBytes + ", aliveBytes=" + aliveBytes + "]";
    }

}
