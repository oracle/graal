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

import com.oracle.svm.interpreter.metadata.profile.InterpreterProfileContainer;
import com.oracle.svm.interpreter.ristretto.RistrettoRuntimeOptions;

import jdk.graal.compiler.debug.Assertions;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.TriState;

public class RistrettoProfilingInfo implements ProfilingInfo {
    private final InterpreterProfileContainer interpreterProfileContainer;

    public RistrettoProfilingInfo(InterpreterProfileContainer interpreterProfileContainer) {
        this.interpreterProfileContainer = interpreterProfileContainer;
    }

    @Override
    public int getCodeSize() {
        return 0;
    }

    @Override
    public JavaTypeProfile getTypeProfile(int bci) {
        return null;
    }

    @Override
    public JavaMethodProfile getMethodProfile(int bci) {
        return null;
    }

    @Override
    public double getBranchTakenProbability(int bci) {
        final double takenProfile = interpreterProfileContainer.getBranchTakenProbability(bci);
        assert !Double.isNaN(takenProfile) && !Double.isNaN(takenProfile) : Assertions.errorMessage("Must have sane value", takenProfile, interpreterProfileContainer.getMethod(),
                        InterpreterProfileContainer.TestingBackdoor.profilesAtBCI(interpreterProfileContainer, bci));
        return takenProfile;
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
        return interpreterProfileContainer.isMature() || interpreterProfileContainer.getProfileEntryCount() > RistrettoRuntimeOptions.JITProfileMatureInvocationThreshold.getValue();
    }

    @Override
    public String toString() {
        return "DefaultProfilingInfo<" + this.toString(null, "; ") + ">";
    }

    @Override
    public void setMature() {
        // Do nothing
    }

    @Override
    public boolean setCompilerIRSize(Class<?> irType, int nodeCount) {
        return false;
    }

    @Override
    public int getCompilerIRSize(Class<?> irType) {
        return -1;
    }
}
