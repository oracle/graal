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
package com.oracle.svm.interpreter.metadata.profile;

import static jdk.graal.compiler.bytecode.Bytecodes.END;

import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.interpreter.metadata.Bytecodes;

import jdk.graal.compiler.bytecode.BytecodeStream;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Stores interpreter profiling data collected during the execution of a single
 * {@link ResolvedJavaMethod}.
 * <p>
 * The data is written concurrently by multiple Crema interpreter threads during method execution.
 * It is subsequently read by compilation consumers, typically wrapped in a {@link ProfilingInfo}
 * object.
 * <p>
 * <b>Thread Safety and Mutability:</b> Because multiple interpreter threads update the profiles
 * concurrently, the data within this object is <b>highly volatile</b>. Any profile-related
 * information returned by methods of this class can change significantly and rapidly over time.
 * Consumers must be aware of this mutability when reading and acting upon the profiling data.
 */
public final class MethodProfile {

    /**
     * Artificial byte code index for the method entry profile.
     */
    private static final int JVMCI_METHOD_ENTRY_BCI = -1;

    private final InterpreterProfile[] profiles;

    /**
     * Caches the index of the last returned profile for the next access. Initialized to 0, will be
     * set in {@link #getAtBCI(int, Class)}.
     */
    private int lastIndex;

    private final ResolvedJavaMethod method;

    /**
     * See {@link #isMature()}.
     */
    private boolean isMature;

    public MethodProfile(ResolvedJavaMethod method) {
        this.method = method;
        this.profiles = buildProfiles(method);
    }

    private static InterpreterProfile[] buildProfiles(ResolvedJavaMethod method) {
        BytecodeStream stream = new BytecodeStream(method.getCode());
        stream.setBCI(0);

        List<InterpreterProfile> allProfiles = new ArrayList<>();
        // we always add a method entry counting profile
        allProfiles.add(new CountingProfile(JVMCI_METHOD_ENTRY_BCI));

        while (stream.currentBC() != END) {
            int bci = stream.currentBCI();
            int opcode = stream.currentBC();
            // we can have multiple profiles for a single BCI: type, exception etc
            if (Bytecodes.isProfiledBranch(opcode)) {
                allProfiles.add(new BranchProfile(bci));
            }
            if (Bytecodes.isTypeProfiled(opcode)) {
                // TODO GR-71567
            }
            // TODO GR-71799 - backedge / goto profiles
            stream.next();
        }
        return allProfiles.toArray(new InterpreterProfile[0]);
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    /**
     * Similar semantics as {@link ProfilingInfo#isMature()} except this method does not perform an
     * ergonomic decision. A profile is only mature if it was explicitly set with
     * {@link #setMature(boolean)}. Normally this is done by test code for example. Users of this
     * {@link MethodProfile} can combine this with real ergonomics.
     *
     * @return true if an explicit maturity override has been set on this profiling data; false
     *         otherwise
     */
    public boolean isMature() {
        return isMature;
    }

    public void setMature(boolean mature) {
        isMature = mature;
    }

    public long profileMethodEntry() {
        return ((CountingProfile) getAtBCI(JVMCI_METHOD_ENTRY_BCI, CountingProfile.class)).counter++;
    }

    public long getProfileEntryCount() {
        return ((CountingProfile) getAtBCI(JVMCI_METHOD_ENTRY_BCI, CountingProfile.class)).counter;
    }

    public void profileBranch(int bci, boolean taken) {
        if (taken) {
            ((BranchProfile) getAtBCI(bci, BranchProfile.class)).incrementTakenCounter();
        } else {
            ((BranchProfile) getAtBCI(bci, BranchProfile.class)).incrementNotTakenCounter();
        }
    }

    public double getBranchTakenProbability(int bci) {
        return ((BranchProfile) getAtBCI(bci, BranchProfile.class)).takenProfile();
    }

    /**
     * Gets the profile for {@code bci} whose class is {@code clazz}.
     *
     * @return null if there's no profile
     */
    private InterpreterProfile getAtBCI(int bci, Class<? extends InterpreterProfile> clazz) {
        int lastIndexLocal = lastIndex;
        for (int i = lastIndexLocal; i < profiles.length; i++) {
            InterpreterProfile profile = profiles[i];
            if (profile.getBci() == bci && profile.getClass() == clazz) {
                lastIndex = i;
                return profile;
            }
        }
        for (int i = 0; i < lastIndexLocal; i++) {
            InterpreterProfile profile = profiles[i];
            if (profile.getBci() == bci && profile.getClass() == clazz) {
                lastIndex = i;
                return profile;
            }
        }
        return null;
    }

    public static class TestingBackdoor {
        public static List<InterpreterProfile> profilesAtBCI(MethodProfile methodProfile, int bci) {
            ArrayList<InterpreterProfile> profiles = new ArrayList<>();
            for (int i = 0; i < methodProfile.profiles.length; i++) {
                InterpreterProfile profile = methodProfile.profiles[i];
                if (profile.getBci() == bci) {
                    profiles.add(profile);
                }
            }
            return profiles;
        }
    }

    public abstract static class InterpreterProfile {
        protected final int bci;

        protected InterpreterProfile(int bci) {
            this.bci = bci;
        }

        public int getBci() {
            return bci;
        }
    }

    public static class CountingProfile extends InterpreterProfile {
        protected long counter;

        CountingProfile(int bci) {
            super(bci);
        }

        public long getCounter() {
            return counter;
        }

        public void incrementCounter() {
            counter++;
        }

        @Override
        public String toString() {
            return "{Counting:bci=" + bci + ", counter=" + counter + "}";
        }
    }

    public static class BranchProfile extends CountingProfile {
        private long takenCounter;

        public BranchProfile(int bci) {
            super(bci);
        }

        public void incrementTakenCounter() {
            takenCounter++;
            counter++;
        }

        public void incrementNotTakenCounter() {
            counter++;
        }

        public double takenProfile() {
            if (counter == 0) {
                return -1;
            }
            return (double) takenCounter / (double) counter;
        }

        public double notTakenProfile() {
            if (counter == 0) {
                return -1;
            }
            return 1D - takenProfile();
        }

        @Override
        public String toString() {
            return "{BranchProfile:bci=" + bci + ", takenCounter=" + takenCounter + ", counter=" + counter + "}";
        }
    }

}
