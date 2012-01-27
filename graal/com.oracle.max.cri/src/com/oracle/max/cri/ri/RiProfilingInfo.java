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
package com.oracle.max.cri.ri;


/**
 * Represents profiling information for one specific method.
 * Every accessor method returns the information that is available at the time of its invocation.
 * If a method is invoked multiple times, it may return a significantly different results for every invocation.
 */
public interface RiProfilingInfo {
    /**
     * Returns an estimate of how often the branch at the given byte code was taken.
     * @return The estimated probability, with 0.0 meaning never and 1.0 meaning always, or -1 if this information is not available.
     */
    double getBranchTakenProbability(int bci);

    /**
     * Returns an estimate of how often the switch cases are taken at the given BCI.
     * The default case is stored as the last entry.
     * @return A double value that contains the estimated probabilities, with 0.0 meaning never and 1.0 meaning always,
     * or -1 if this information is not available.
     */
    double[] getSwitchProbabilities(int bci);

    /**
     * Returns the TypeProfile for the given BCI.
     * @return Returns an RiTypeProfile object, or null if not available.
     */
    RiTypeProfile getTypeProfile(int bci);

    /**
     * Returns true if the given BCI did throw an implicit exception (NullPointerException, ClassCastException,
     * ArrayStoreException, or ArithmeticException) during profiling.
     * @return true if any of the exceptions was encountered during profiling, false otherwise.
     */
    boolean getImplicitExceptionSeen(int bci);

    /**
     * Returns an estimate how often the current BCI was executed. Avoid comparing execution counts to each other,
     * as the returned value highly depends on the time of invocation.
     * @return the estimated execution count or -1 if not available.
     */
    int getExecutionCount(int bci);
}
