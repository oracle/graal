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
package org.graalvm.bisect.core;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Marks the longest executing graal-compiled methods as hot.
 */
public class HotMethodPolicy {
    /**
     * The minimum number of methods to mark as hot.
     */
    private int hotMethodMinLimit = 1;
    /**
     * The maximum number of methods to mark as hot.
     */
    private int hotMethodMaxLimit = 10;
    /**
     * The percentile of the execution period that is spent executing hot methods.
     */
    private double hotMethodPercentile = 0.9;

    /**
     * Marks the longest executing methods of an experiment as hot. The methods are sorted by decreasing periods
     * of execution. The first {@link #hotMethodMinLimit} methods are always marked as hot. The methods which fit
     * into the {@link #hotMethodPercentile} of total graal execution are marked as hot. A maximum of
     * {@link #hotMethodMaxLimit} is marked as hot. The method calls {@link ExecutedMethod#setHot} for each method of
     * the experiment to avoid an inconsistent state.
     * @param experiment the experiment which is evaluated for hot methods
     */
    public void markHotMethods(Experiment experiment) {
        double periodLimit = experiment.sumGraalPeriod() * hotMethodPercentile;
        List<ExecutedMethod> sortedMethods = experiment.getExecutedMethods().stream()
                .sorted((ExecutedMethod a, ExecutedMethod b) -> Long.compare(b.getPeriod(), a.getPeriod()))
                .collect(Collectors.toList());
        int index = 0;
        for (ExecutedMethod method : sortedMethods) {
            periodLimit -= method.getPeriod();
            method.setHot(index < hotMethodMinLimit || (periodLimit >= 0 && index < hotMethodMaxLimit));
            ++index;
        }
    }

    /**
     * Sets the minimum number of methods to mark as hot.
     * @param hotMethodMinLimit the minimum number of methods to mark as hot
     */
    public void setHotMethodMinLimit(int hotMethodMinLimit) {
        this.hotMethodMinLimit = hotMethodMinLimit;
    }

    /**
     * Sets maximum number of methods to mark as hot.
     * @param hotMethodMaxLimit the maximum number of methods to mark as hot
     */
    public void setHotMethodMaxLimit(int hotMethodMaxLimit) {
        this.hotMethodMaxLimit = hotMethodMaxLimit;
    }

    /**
     * Sets the percentile of the execution period that is spent executing hot methods.
     * @param hotMethodPercentile the percentile of the execution period that is spent executing hot methods
     */
    public void setHotMethodPercentile(double hotMethodPercentile) {
        this.hotMethodPercentile = hotMethodPercentile;
    }
}
