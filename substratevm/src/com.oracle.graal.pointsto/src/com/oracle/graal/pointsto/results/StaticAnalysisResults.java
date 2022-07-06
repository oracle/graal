/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.results;

import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.TriState;

public class StaticAnalysisResults implements ProfilingInfo {
    public static final StaticAnalysisResults NO_RESULTS = new StaticAnalysisResults(0, null, null, null);

    public static class BytecodeEntry {
        /** The bytecode index of this entry. */
        private final int bci;

        /** Next element of linked list, with higher bci. */
        protected BytecodeEntry next;

        /** The method profile for invocation bytecodes. */
        private final JavaMethodProfile methodProfile;

        /** The type profiles for the return type of invocation bytecodes. */
        private final JavaTypeProfile invokeResultTypeProfile;

        /**
         * The type profiles for this method (for all bytecodes that have types, e.g.,
         * invokevirtual, instanceof, checkcast.
         */
        private final JavaTypeProfile typeProfile;

        /** The type profiles for this method - updated only with statically calculated profiles. */
        private final JavaTypeProfile staticTypeProfile;

        public BytecodeEntry(int bci, JavaTypeProfile typeProfile, JavaMethodProfile methodProfile, JavaTypeProfile invokeResultTypeProfile, JavaTypeProfile staticTypeProfile) {
            this.bci = bci;
            this.methodProfile = methodProfile;
            this.invokeResultTypeProfile = invokeResultTypeProfile;
            this.typeProfile = typeProfile;
            this.staticTypeProfile = staticTypeProfile;
        }

        @Override
        public String toString() {
            return "BytecodeEntry(bci=" + bci + ", typeProfile=" + typeProfile + ", methodProfile=" + methodProfile + ", invokeResultTypeProfile=" + invokeResultTypeProfile + ")";
        }

    }

    /** The bytecode size of the method. */
    private final int codeSize;

    /** The type profiles for method parameters. */
    private final JavaTypeProfile[] parameterTypeProfiles;

    /** The type profile for the method result. */
    private final JavaTypeProfile resultTypeProfile;

    private final BytecodeEntry first;
    private BytecodeEntry cache;

    public StaticAnalysisResults(int codeSize, JavaTypeProfile[] parameterTypeProfiles, JavaTypeProfile resultTypeProfile, BytecodeEntry first) {
        this.codeSize = codeSize;
        this.parameterTypeProfiles = parameterTypeProfiles;
        this.resultTypeProfile = resultTypeProfile;
        this.first = first;
        this.cache = first;
    }

    /**
     * Returns the type profile for the parameter with the given number, or {@code null} if no type
     * profile is available. For non-static methods, the receiver is the parameter with number 0.
     */
    public JavaTypeProfile getParameterTypeProfile(int parameter) {
        if (parameterTypeProfiles != null && parameter < parameterTypeProfiles.length) {
            return parameterTypeProfiles[parameter];
        } else {
            return null;
        }
    }

    /**
     * Returns the type profile for values returned by the method, or {@code null} if no type
     * profile is available.
     */
    public JavaTypeProfile getResultTypeProfile() {
        return resultTypeProfile;
    }

    private BytecodeEntry lookup(int bci) {
        if (first == null) {
            return null;
        }
        BytecodeEntry cur = bci >= cache.bci ? cache : first;
        while (cur != null && cur.bci < bci) {
            cur = cur.next;
        }
        if (cur != null) {
            cache = cur;
            if (cur.bci == bci) {
                return cur;
            }
        }
        return null;
    }

    /**
     * Returns the type profile for values returned by the invocation bytecode with the given bci,
     * or {@code null} if no type profile is available.
     */
    public JavaTypeProfile getInvokeResultTypeProfile(int bci) {
        BytecodeEntry entry = lookup(bci);
        return entry == null ? null : entry.invokeResultTypeProfile;
    }

    @Override
    public int getCodeSize() {
        return codeSize;
    }

    @Override
    public double getBranchTakenProbability(int bci) {
        /* Static analysis cannot determine branch probabilities. */
        return -1;
    }

    @Override
    public double[] getSwitchProbabilities(int bci) {
        /* Static analysis cannot determine branch probabilities. */
        return null;
    }

    @Override
    public JavaTypeProfile getTypeProfile(int bci) {
        BytecodeEntry entry = lookup(bci);
        return entry == null ? null : entry.typeProfile;
    }

    public JavaTypeProfile getStaticTypeProfile(int bci) {
        BytecodeEntry entry = lookup(bci);
        return entry == null ? null : entry.staticTypeProfile;
    }

    @Override
    public JavaMethodProfile getMethodProfile(int bci) {
        BytecodeEntry entry = lookup(bci);
        return entry == null ? null : entry.methodProfile;
    }

    @Override
    public TriState getExceptionSeen(int bci) {
        /*
         * The design of the Substrate VM exception handling allows to be conservative here and
         * always return true. Unnecessary exception edges are removed during compilation.
         */
        return TriState.TRUE;
    }

    @Override
    public TriState getNullSeen(int bci) {
        BytecodeEntry entry = lookup(bci);
        return entry == null ? TriState.UNKNOWN : entry.typeProfile.getNullSeen();
    }

    @Override
    public int getExecutionCount(int bci) {
        /* Static analysis cannot determine execution counts. */
        return -1;
    }

    @Override
    public int getDeoptimizationCount(DeoptimizationReason reason) {
        /* Ahead-of-time compiled code does not deoptimize. */
        return 0;
    }

    @Override
    public boolean setCompilerIRSize(Class<?> irType, int size) {
        throw AnalysisError.shouldNotReachHere("unreachable");
    }

    @Override
    public int getCompilerIRSize(Class<?> irType) {
        throw AnalysisError.shouldNotReachHere("unreachable");
    }

    @Override
    public boolean isMature() {
        /*
         * Static analysis results are definitely correct and do not change, but do not provide
         * mature branch probability information.
         */
        return false;
    }

    @Override
    public void setMature() {
        /* Nothing to do, results cannot change. */
    }
}
