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
package com.oracle.svm.interpreter.ristretto.profile;

import com.oracle.svm.interpreter.metadata.profile.InterpreterProfilingOptions;
import com.oracle.svm.interpreter.metadata.profile.MethodProfile;

import jdk.graal.compiler.debug.Assertions;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.TriState;

public class RistrettoProfilingInfo implements ProfilingInfo {

    private static final double PROB_DELTA = 1e-6;

    private final MethodProfile methodProfile;

    public RistrettoProfilingInfo(MethodProfile methodProfile) {
        this.methodProfile = methodProfile;
    }

    @Override
    public int getCodeSize() {
        return 0;
    }

    @Override
    public JavaTypeProfile getTypeProfile(int bci) {
        return methodProfile.getTypeProfile(bci);
    }

    @Override
    public JavaMethodProfile getMethodProfile(int bci) {
        return null;
    }

    @Override
    public double getBranchTakenProbability(int bci) {
        double recordedProbability = methodProfile.getBranchTakenProbability(bci);
        if (recordedProbability == -1D) {
            return -1D;
        }

        assert !Double.isNaN(recordedProbability) && !Double.isInfinite(recordedProbability) : Assertions.errorMessage("Invalid recorded branch probability", recordedProbability,
                        methodProfile.getMethod(), MethodProfile.TestingBackdoor.profilesAtBCI(methodProfile, bci));

        // at runtime if assertions are disabled fall back to unknown probability
        if (Double.isNaN(recordedProbability) || Double.isInfinite(recordedProbability)) {
            recordedProbability = 0.5D;
        }

        // ensure profile is in an expected range (modulo a small delta)
        assert recordedProbability >= 0D - PROB_DELTA && recordedProbability <= 1D + PROB_DELTA : Assertions.errorMessage("Must be within [0,1] bounds modulo a small delta but is ",
                        recordedProbability);

        // Clamp to [0, 1]
        double clamped = Math.max(0.0, Math.min(1.0, recordedProbability));

        // Verify the invariant
        assert clamped >= 0.0 && clamped <= 1.0 : Assertions.errorMessage("Branch probability out of [0,1]", clamped, methodProfile.getMethod(),
                        MethodProfile.TestingBackdoor.profilesAtBCI(methodProfile, bci));

        return clamped;
    }

    @Override
    public double[] getSwitchProbabilities(int bci) {
        return null;
    }

    @Override
    public TriState getExceptionSeen(int bci) {
        return TriState.UNKNOWN;
    }

    @Override
    public TriState getNullSeen(int bci) {
        return TriState.UNKNOWN;
    }

    @Override
    public int getExecutionCount(int bci) {
        return -1;
    }

    @Override
    public int getDeoptimizationCount(DeoptimizationReason reason) {
        return 0;
    }

    @Override
    public boolean isMature() {
        /*
         * Either maturity was explicitly requested or we follow regular ergonomics.
         */
        return methodProfile.isMature() || methodProfile.getProfileEntryCount() > InterpreterProfilingOptions.JITProfileMatureInvocationThreshold.getValue();
    }

    @Override
    public void setMature() {
        methodProfile.setMature(true);
    }

    @Override
    public boolean setCompilerIRSize(Class<?> irType, int nodeCount) {
        return false;
    }

    @Override
    public int getCompilerIRSize(Class<?> irType) {
        return -1;
    }

    @Override
    public String toString() {
        return "RistrettoProfilingInfo<" + this.toString(null, "; ") + ">";
    }
}
