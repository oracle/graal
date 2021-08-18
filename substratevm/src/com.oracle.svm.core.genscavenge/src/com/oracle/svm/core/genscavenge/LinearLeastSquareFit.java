/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

/**
 * Use a least squares fit to a set of data to generate a linear equation.
 *
 * y = intercept + slope * x
 */
final class LinearLeastSquareFit {
    /** Sum of all independent data points x. */
    private double sumX;
    /** Sum of all independent data points x**2. */
    private double sumXSquared;
    /** Sum of all dependent data points y. */
    private double sumY;
    /** Sum of all x * y. */
    private double sumXY;

    private double intercept;
    private double slope;
    private long count;

    LinearLeastSquareFit(@SuppressWarnings("unused") int weight) {
        // Weighted averages are not currently used but perhaps should be to get decaying averages.
    }

    /**
     * Add a new data point.
     *
     * @param x independent variable value
     * @param y dependent variable value
     */
    public void sample(double x, double y) {
        sumX += x;
        sumXSquared += x * x;
        sumY += y;
        sumXY += x * y;
        count++;
        if (count > 1) {
            double slopeDenominator = count * sumXSquared - sumX * sumX;
            // Some tolerance should be injected here. A denominator of nearly 0 should be avoided.

            if (slopeDenominator != 0) {
                double slopeNumerator = count * sumXY - sumX * sumY;
                slope = slopeNumerator / slopeDenominator;

                /*
                 * Decaying averages for x and y can be used to discount earlier data. If they are
                 * used, first consider whether all quantities should be kept as decaying averages.
                 *
                 * intercept = meanY.average() - slope * meanX.average();
                 */
                intercept = (sumY - slope * sumX) / count;
            }
        }
    }

    /*
     * Both decrementWillDecrease and incrementWillDecrease() return true for a slope of 0. That is
     * because a change is necessary before a slope can be calculated and a 0 slope will, in
     * general, indicate that no calculation of the slope has yet been done. Returning true for a
     * slope equal to 0 reflects the intuitive expectation of the dependence on the slope. Don't use
     * the complement of these functions since that intuitive expectation is not built into the
     * complement.
     */

    public boolean decrementWillDecrease() {
        return slope >= 0;
    }

    public boolean incrementWillDecrease() {
        return slope <= 0;
    }
}
