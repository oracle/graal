/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.VMError;

/**
 * Abstract superclass for classes that represent number sequences, x1, x2, x3, ..., xN, and can
 * calculate their avg, max, and sd.
 *
 * This class and its subclasses are ported from HotSpot. This class is named {@code AbsSeq} there.
 */
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/utilities/numberSeq.hpp")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/utilities/numberSeq.cpp")
abstract class AbstractSeq {
    /** The number of elements in the sequence. */
    protected int num = 0;
    /** The sum of the elements in the sequence. */
    protected double sum = 0.0;
    /** The sum of squares of the elements in the sequence. */
    protected double sumOfSquares = 0.0;
    /** Decaying average. */
    protected double davg = 0.0;
    /** Decaying variance. */
    protected double dvariance = 0.0;
    /** Factor for the decaying average / variance. */
    protected final double alpha;

    protected AbstractSeq(double alpha) {
        this.alpha = alpha;
    }

    /**
     * This is what we divide with to get the average. In a standard number, this should just be the
     * number of elements in it.
     */
    protected double total() {
        return num;
    }

    /** Adds a new element to the sequence. */
    public void add(double val) {
        if (num == 0) {
            // if the sequence is empty, the davg is the same as the value
            davg = val;
            // and the variance is 0
            dvariance = 0.0;
        } else {
            // otherwise, calculate both
            // Formula from "Incremental calculation of weighted mean and variance" by Tony Finch
            // diff := x - mean
            // incr := alpha * diff
            // mean := mean + incr
            // variance := (1 - alpha) * (variance + diff * incr)
            // PDF available at https://fanf2.user.srcf.net/hermes/doc/antiforgery/stats.pdf
            double diff = val - davg;
            double incr = alpha * diff;
            davg += incr;
            dvariance = (1.0 - alpha) * (dvariance + diff * incr);
        }
    }

    /** Last element added in the sequence. */
    public abstract double last();

    /** The average of the sequence. */
    public double avg() {
        if (num == 0) {
            return 0.0;
        } else {
            return sum / total();
        }
    }

    public double getSum() {
        return sum;
    }

    /** The variance of the sequence. */
    public double variance() {
        if (num <= 1) {
            return 0.0;
        }
        double xBar = avg();
        double result = sumOfSquares / total() - xBar * xBar;
        if (result < 0.0) {
            // Due to loss-of-precision errors, the variance might be negative by a small bit
            result = 0.0;
        }
        return result;
    }

    /** The standard deviation of the sequence. */
    public double sd() {
        double v = variance();
        VMError.guarantee(v >= 0.0);
        return Math.sqrt(v);
    }

    /** Decaying average. */
    public double davg() {
        return davg;
    }

    /** Decaying variance. */
    public double dvariance() {
        if (num <= 1) {
            return 0.0;
        }
        double result = dvariance;
        if (result < 0.0) {
            // due to loss-of-precision errors, the variance might be negative by a small bit
            VMError.guarantee(result > -0.1, "if variance is negative, it should be very small");
            result = 0.0;
        }
        return result;
    }

    /** Decaying "standard deviation". */
    public double dsd() {
        double v = dvariance();
        VMError.guarantee(v >= 0.0);
        return Math.sqrt(v);
    }
}

/**
 * The sequence is assumed to be very long and the maximum, avg, sd, davg, and dsd are calculated
 * over all its elements.
 */
class NumberSeq extends AbstractSeq {
    private double last = 0.0;
    /** keep track of maximum value. */
    private double maximum = 0.0;

    /** Constructs a NumberSeq with the specified decay factor. */
    NumberSeq(double alpha) {
        super(alpha);
    }

    /** Adds a new value to the sequence. */
    @Override
    public void add(double val) {
        super.add(val);

        last = val;
        if (num == 0) {
            maximum = val;
        } else if (val > maximum) {
            maximum = val;
        }
        sum += val;
        sumOfSquares += val * val;
        num++;
    }

    /** Returns the last element added. */
    @Override
    public double last() {
        return last;
    }
}

/**
 * This class keeps track of the last L elements of the sequence and calculates avg, max, and sd
 * only over them.
 */
class TruncatedSeq extends AbstractSeq {
    /** Buffers the last L elements in the sequence. */
    private final double[] sequence;
    /** This is L. */
    private final int length;
    /** Oldest slot in the array, i.e. next to be overwritten. */
    private int next = 0;

    /** Constructs with buffer length and decay alpha. */
    TruncatedSeq(int length, double alpha) {
        super(alpha);
        this.length = length;
        this.sequence = new double[length];
    }

    /** Adds a value, maintaining the buffer. */
    @Override
    public void add(double val) {
        super.add(val);

        // get the oldest value in the sequence...
        double oldVal = sequence[next];
        // ...remove it from the sum and sum of squares
        sum -= oldVal;
        sumOfSquares -= oldVal * oldVal;

        // ...and update them with the new value
        sum += val;
        sumOfSquares += val * val;

        // now replace the old value with the new one
        sequence[next] = val;
        next = (next + 1) % length;

        // only increase it if the buffer is not full
        if (num < length) {
            num++;
        }

        VMError.guarantee(variance() > -1.0);
    }

    /** Returns the last value added. */
    @Override
    public double last() {
        if (num == 0) {
            return 0.0;
        }
        int lastIndex = (next + length - 1) % length;
        return sequence[lastIndex];
    }

    /* Methods maximum(), oldest() and predict_next() are currently not needed. */
}
