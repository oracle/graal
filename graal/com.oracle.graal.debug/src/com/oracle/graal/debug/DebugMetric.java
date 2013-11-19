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
package com.oracle.graal.debug;

/**
 * A counter for some value of interest.
 */
public interface DebugMetric {

    /**
     * Adds 1 to this counter if metering is {@link Debug#isMeterEnabled() enabled} or this is an
     * {@linkplain #isConditional() unconditional} metric.
     */
    void increment();

    /**
     * Adds {@code value} to this counter if metering is {@link Debug#isMeterEnabled() enabled} or
     * this is an {@linkplain #isConditional() unconditional} metric.
     */
    void add(long value);

    /**
     * Sets a flag determining if this counter is only enabled if metering is
     * {@link Debug#isMeterEnabled() enabled}.
     */
    void setConditional(boolean flag);

    /**
     * Determines if this counter is only enabled if metering is {@link Debug#isMeterEnabled()
     * enabled}.
     */
    boolean isConditional();

    /**
     * Gets the current value of this metric.
     */
    long getCurrentValue();
}
