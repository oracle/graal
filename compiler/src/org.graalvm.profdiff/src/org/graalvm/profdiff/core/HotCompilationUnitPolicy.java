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

import java.util.stream.StreamSupport;

/**
 * Marks the longest executing Graal compilation units as hot.
 */
public class HotCompilationUnitPolicy {
    /**
     * The minimum number of compilation units to mark as hot.
     */
    private int hotMinLimit = 1;

    /**
     * The maximum number of compilation units to mark as hot.
     */
    private int hotMaxLimit = 10;

    /**
     * The percentile of the execution period that is spent executing hot compilation units.
     */
    private double hotPercentile = 0.9;

    /**
     * Marks the longest executing compilation units of an experiment as hot. The compilation units
     * are sorted by decreasing periods of execution. The first {@link #hotMinLimit} compilation
     * units are always marked as hot. The compilation units which fit into the
     * {@link #hotPercentile} of total Graal execution are marked as hot. A maximum of
     * {@link #hotMaxLimit} compilation units is marked as hot. The method calls
     * {@link CompilationUnit#setHot} for each method of the experiment to avoid an inconsistent
     * state.
     *
     * @param experiment the experiment which is evaluated for hot methods
     */
    public void markHotCompilationUnits(Experiment experiment) {
        double periodLimit = experiment.getGraalPeriod() * hotPercentile;
        Iterable<CompilationUnit> compilationUnits = () -> StreamSupport.stream(experiment.getCompilationUnits().spliterator(), false).sorted(
                        (CompilationUnit a, CompilationUnit b) -> Long.compare(b.getPeriod(), a.getPeriod())).iterator();
        int index = 0;
        for (CompilationUnit compilationUnit : compilationUnits) {
            periodLimit -= compilationUnit.getPeriod();
            compilationUnit.setHot(index < hotMinLimit || (periodLimit >= 0 && index < hotMaxLimit));
            ++index;
        }
    }

    /**
     * Sets the minimum number of compilation units to mark as hot.
     *
     * @param hotMinLimit the minimum number of methods to mark as hot
     */
    public void setHotMinLimit(int hotMinLimit) {
        this.hotMinLimit = hotMinLimit;
    }

    /**
     * Sets maximum number of compilation units to mark as hot.
     *
     * @param hotMaxLimit the maximum number of methods to mark as hot
     */
    public void setHotMaxLimit(int hotMaxLimit) {
        this.hotMaxLimit = hotMaxLimit;
    }

    /**
     * Sets the percentile of the execution period that is spent executing hot compilation units.
     *
     * @param hotPercentile the percentile of the execution period that is spent executing hot
     *            compilation units
     */
    public void setHotPercentile(double hotPercentile) {
        this.hotPercentile = hotPercentile;
    }
}
