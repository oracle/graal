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
     * The compilation ID of this method. The value is {@code null} if the method does not have an
     * ID or if it is unknown. The IDs are unknown in Native Image profiles.
     */
    private final String compilationId;

    /**
     * The name of this method as reported by proftool. In JIT, the names are unstable for lambdas,
     * and they may be distinct from the names of matching compilation units. In AOT, the names are
     * stable, and they match the names of compilation units.
     */
    private final String name;

    /**
     * The level (tier) of the compiler used that generated this method or {@code null} for the
     * interpreter and Native Image methods.
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
     * Gets the compilation ID of this method. Returns {@code null} if no compilation ID is assigned
     * or the ID is unknown. The IDs are unknown in Native Image profiles.
     *
     * @return the compilation ID of this method or {@code null}
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
     * Gets the level (tier) of the compiler that generated this method. Returns {@code null} for
     * Native Image methods or the interpreter.
     *
     * @return the level (tier) of the compiler or {@code null}
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
