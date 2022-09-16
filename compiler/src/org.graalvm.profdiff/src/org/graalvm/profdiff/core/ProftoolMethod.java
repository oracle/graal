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
package org.graalvm.profdiff.core;

/**
 * Represents a piece of executed code with an execution period sampled by proftool.
 */
public class ProftoolMethod {
    /**
     * 10^9 cycles. Used to convert the {@link #period} to billions of cycles.
     */
    public static long BILLION = 1000000000;

    /**
     * The compilation ID of this method or {@code null} if it does not have any.
     */
    private final String compilationId;

    /**
     * The name of this method as reported by proftool. The name unstable for lambdas and different
     * from the name of the matching compilation unit.
     */
    private final String name;

    /**
     * The level of the compiler used that generated this method or {@code null} for e.g. the
     * interpreter.
     */
    private final Integer level;

    /**
     * The sum of cycles of the samples collected for this method.
     */
    private final long period;

    public ProftoolMethod(String compilationId, String name, Integer level, long period) {
        this.compilationId = compilationId;
        this.name = name;
        this.level = level;
        this.period = period;
    }

    /**
     * Gets the compilation ID of this method.
     */
    public String getCompilationId() {
        return compilationId;
    }

    /**
     * Gets the name of this method as reported by proftool.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the level of the compiler that generated this method.
     */
    public Integer getLevel() {
        return level;
    }

    /**
     * Gets the sum of cycles of samples collected for this method.
     */
    public long getPeriod() {
        return period;
    }
}
