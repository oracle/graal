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
package org.graalvm.compiler.nodes.spi;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.nodes.StructuredGraph;

import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.TriState;

/**
 * A {@link ProfileProvider} that caches the answer to all queries so that each query returns the
 * same answer for the entire compilation. This can improve the consistency of compilation results.
 */
public class StableProfileProvider implements ProfileProvider {
    private static final JavaTypeProfile NULL_PROFILE = new JavaTypeProfile(TriState.UNKNOWN, 1.0, new JavaTypeProfile.ProfiledType[0]);

    private final EconomicMap<ProfileKey, CachingProfilingInfo> profiles = EconomicMap.create();

    @Override
    public ProfilingInfo getProfilingInfo(ResolvedJavaMethod method) {
        return getProfilingInfo(method, true, true);
    }

    static class ProfileKey {
        final ResolvedJavaMethod method;
        final boolean includeNormal;
        final boolean includeOSR;

        ProfileKey(ResolvedJavaMethod method, boolean includeNormal, boolean includeOSR) {
            this.method = method;
            this.includeNormal = includeNormal;
            this.includeOSR = includeOSR;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ProfileKey that = (ProfileKey) o;
            return includeNormal == that.includeNormal && includeOSR == that.includeOSR && method.equals(that.method);
        }

        @Override
        public int hashCode() {
            return method.hashCode() + (includeNormal ? 1 : 0) + (includeOSR ? 2 : 0);
        }
    }

    @Override
    public ProfilingInfo getProfilingInfo(ResolvedJavaMethod method, boolean includeNormal, boolean includeOSR) {
        // In the normal case true is passed for both arguments but the root method of the compile
        // will pass true for only one of these flags.
        ProfileKey key = new ProfileKey(method, includeNormal, includeOSR);
        CachingProfilingInfo profile = profiles.get(key);
        if (profile == null) {
            profile = new CachingProfilingInfo(method, includeNormal, includeOSR);
            profiles.put(key, profile);
        }
        return profile;
    }

    /**
     * Lazy cache of the per method profile queries.
     */
    public static class CachingProfilingInfo implements ProfilingInfo {

        /**
         * Lazy cache of the per bytecode profile queries.
         */
        static class BytecodeProfile {
            final int bci;
            TriState exceptionSeen;
            TriState nullSeen;
            Double branchTakenProbability;
            double[] switchProbabilities;
            Integer executionCount;
            JavaTypeProfile typeProfile;

            BytecodeProfile(int bci) {
                this.bci = bci;
            }
        }

        /**
         * The underlying profiling object used for queries.
         */
        private final ProfilingInfo realProfile;

        private Boolean isMature;

        private Integer compilerIRSize;

        /**
         * Per bci profiles.
         */
        private final EconomicMap<Integer, BytecodeProfile> bytecodeProfiles;

        CachingProfilingInfo(ResolvedJavaMethod method, boolean includeNormal, boolean includeOSR) {
            this.realProfile = method.getProfilingInfo(includeNormal, includeOSR);
            this.bytecodeProfiles = EconomicMap.create();
        }

        @Override
        public int getCodeSize() {
            return realProfile.getCodeSize();
        }

        @Override
        public double getBranchTakenProbability(int bci) {
            BytecodeProfile cached = getBytecodeProfile(bci);
            if (cached.branchTakenProbability == null) {
                cached.branchTakenProbability = realProfile.getBranchTakenProbability(bci);
            }
            return cached.branchTakenProbability;
        }

        @Override
        public double[] getSwitchProbabilities(int bci) {
            BytecodeProfile cached = getBytecodeProfile(bci);
            if (cached.switchProbabilities == null) {
                cached.switchProbabilities = realProfile.getSwitchProbabilities(bci);
            }
            return cached.switchProbabilities;
        }

        @Override
        public JavaTypeProfile getTypeProfile(int bci) {
            BytecodeProfile cached = getBytecodeProfile(bci);
            if (cached.typeProfile == null) {
                cached.typeProfile = realProfile.getTypeProfile(bci);
                if (cached.typeProfile == null) {
                    cached.typeProfile = NULL_PROFILE;
                }
            }
            return cached.typeProfile == NULL_PROFILE ? null : cached.typeProfile;
        }

        @Override
        public JavaMethodProfile getMethodProfile(int bci) {
            return null;
        }

        @Override
        public TriState getExceptionSeen(int bci) {
            BytecodeProfile cached = getBytecodeProfile(bci);
            if (cached.exceptionSeen == null) {
                cached.exceptionSeen = realProfile.getExceptionSeen(bci);
            }
            return cached.exceptionSeen;
        }

        private BytecodeProfile getBytecodeProfile(int bci) {
            BytecodeProfile cached = bytecodeProfiles.get(bci);
            if (cached == null) {
                cached = new BytecodeProfile(bci);
                bytecodeProfiles.put(bci, cached);
            }
            return cached;
        }

        @Override
        public TriState getNullSeen(int bci) {
            BytecodeProfile cached = getBytecodeProfile(bci);
            if (cached.nullSeen == null) {
                cached.nullSeen = realProfile.getNullSeen(bci);
            }
            return cached.nullSeen;
        }

        @Override
        public int getExecutionCount(int bci) {
            BytecodeProfile cached = getBytecodeProfile(bci);
            if (cached.executionCount == null) {
                cached.executionCount = realProfile.getExecutionCount(bci);
            }
            return cached.executionCount;
        }

        @Override
        public int getDeoptimizationCount(DeoptimizationReason reason) {
            return realProfile.getDeoptimizationCount(reason);
        }

        @Override
        public boolean setCompilerIRSize(Class<?> irType, int irSize) {
            return realProfile.setCompilerIRSize(irType, irSize);
        }

        @Override
        public int getCompilerIRSize(Class<?> irType) {
            assert irType == StructuredGraph.class;
            if (compilerIRSize == null) {
                compilerIRSize = realProfile.getCompilerIRSize(irType);
            }
            return compilerIRSize;
        }

        @Override
        public boolean isMature() {
            if (isMature == null) {
                isMature = realProfile.isMature();
            }
            return isMature;
        }

        @Override
        public void setMature() {
            throw new UnsupportedOperationException();
        }
    }
}
