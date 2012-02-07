/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.cri.ri;

import java.io.*;

/**
 * This profile object represents the type profile at a specific BCI. The precision of the supplied values may vary,
 * but a runtime that provides this information should be aware that it will be used to guide performance-critical
 * decisions like speculative inlining, etc.
 */
public final class RiTypeProfile implements Serializable {
    /**
     *
     */
    private static final long serialVersionUID = -6877016333706838441L;

    private final RiResolvedType[] types;
    private final double notRecordedProbability;
    private final double[] probabilities;

    public RiTypeProfile(RiResolvedType[] types, double notRecordedProbability, double[] probabilites) {
        this.types = types;
        this.notRecordedProbability = notRecordedProbability;
        this.probabilities = probabilites;
    }

    /**
     * The estimated probabilities of the different receivers. This array needs to have the same length as the array returned by
     * {@link RiTypeProfile#types}.
     */
    public double[] getProbabilities() {
        return probabilities;
    }

    /**
     * Returns the estimated probability of all types that could not be recorded due to profiling limitations.
     * @return double value >= 0.0 and <= 1.0
     */
    public double getNotRecordedProbability() {
        return notRecordedProbability;
    }

    /**
     * A list of receivers for which the runtime has recorded probability information. This array needs to have the same
     * length as {@link RiTypeProfile#probabilities}.
     */
    public RiResolvedType[] getTypes() {
        return types;
    }
}
