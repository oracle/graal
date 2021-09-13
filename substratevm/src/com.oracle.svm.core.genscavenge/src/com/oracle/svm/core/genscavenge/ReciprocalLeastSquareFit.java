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
 * Least squares fitting on a data set to generate an equation y = b + a / x. Uses exponential decay
 * to assign a higher weight to newly added data points and effectively drop old data points without
 * keeping a history.
 *
 * Henrik J Blok, Discounted Least Squares Curve Fitting, 1997.
 *
 * Press, W.H. et al, Numerical Recipes in C: The Art of Scientific Computing, Second Edition, 1992.
 */
final class ReciprocalLeastSquareFit {
    private final double discount;

    private double sumY;
    private double sumXReciprocal;
    private double sumYdivX;
    private double sumXSquareReciprocal;
    private double count;

    private double a;
    private double b;

    ReciprocalLeastSquareFit(int effectiveHistoryLength) {
        this.discount = (effectiveHistoryLength - 1.0) / effectiveHistoryLength;
    }

    public void sample(double x, double y) {
        assert x != 0 : "division by zero";

        sumY = y + discount * sumY;
        sumXReciprocal = 1 / x + discount * sumXReciprocal;
        sumYdivX = y / x + discount * sumYdivX;
        sumXSquareReciprocal = 1 / (x * x) + discount * sumXSquareReciprocal;
        count = 1 + discount * count;

        double denominator = count * sumXSquareReciprocal - sumXReciprocal * sumXReciprocal;
        if (denominator != 0) {
            b = (count * sumYdivX - sumXReciprocal * sumY) / denominator;
            a = (sumY - b * sumXReciprocal) / count;
        }
    }

    public double estimate(double x) {
        return a + b / x;
    }

    public double getSlope(double x) {
        return -b / (x * x);
    }
}
