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
import java.util.Arrays;
import java.util.List;

import com.oracle.svm.core.log.Log;
import com.oracle.svm.interpreter.metadata.Bytecodes;

import jdk.graal.compiler.bytecode.BytecodeStream;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Abstract base representation of profile data for crema.
 */
public final class InterpreterProfileContainer {

    /**
     * Developer debug only flag to log profile creation and access.
     */
    private static final boolean LOG_PROFILE_MACHINERY = false;

    private InterpreterProfile[] profiles;
    /*
     * Cached entry always starts at 0, if it's a cache miss we will initialize it to the correct
     * value.
     */
    private int lastIndex = 0;
    private final ResolvedJavaMethod method;
    private boolean isMature;

    public InterpreterProfileContainer(ResolvedJavaMethod method) {
        this.method = method;
        buildProfiles();
    }

    private void buildProfiles() {
        BytecodeStream stream = new BytecodeStream(method.getCode());
        stream.setBCI(0);

        ArrayList<InterpreterProfile> allProfiles = new ArrayList<>();
        // we always add a method entry counting profile
        allProfiles.add(new CountingProfile(JVMCI_METHOD_ENTRY_BCI));

        while (stream.currentBC() != END) {
            int bci = stream.currentBCI();
            int bc = stream.currentBC();
            // in theory, we can have multiple profiles for a single BCI, type, exception etc
            if (inArray(BRANCH_PROFILED_BYTECODES, bc)) {
                // allocate a branch profile for the bci
                allProfiles.add(new BranchProfile(bci));
            }
            if (inArray(TYPE_PROFILED_BYTECODES, bc)) {
                // allocate a type profile
                // TODO GR-71567
            }
            // TODO - backedge / goto profiles
            stream.next();
        }
        if (LOG_PROFILE_MACHINERY) {
            Log.log().string("Assigning profiles ").string(Arrays.toString(allProfiles.toArray())).string(" to method ").string(method.toString()).newline();
        }
        profiles = allProfiles.toArray(new InterpreterProfile[0]);
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public boolean isMature() {
        return isMature;
    }

    public void setMature(boolean mature) {
        isMature = mature;
    }

    private static boolean inArray(int[] arr, int key) {
        for (int i : arr) {
            if (i == key) {
                return true;
            }
        }
        return false;
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
     * Get the given profile at the current {@code bci} that's class equals the {@code  clazz}
     * given. There can be multiple profiles per bci, we want the one that matches the given clazz.
     * Beware: it is not checked nor guaranteed that there is only a single profile which's class
     * equals the given one. This is guaranteed by construction.
     */
    private InterpreterProfile getAtBCI(int bci, Class<? extends InterpreterProfile> clazz) {
        if (LOG_PROFILE_MACHINERY) {
            Log.log().string("getAtBCI method=").string(method.toString()).string(" bci=").unsigned(bci).string(" clazz=").string(clazz.getName()).string(" profilesArray=")
                            .string(Arrays.toString(profiles)).string(" lastIndex=").unsigned(lastIndex).newline();
        }
        InterpreterProfile lastUsedProfile = profiles[lastIndex];
        if (lastUsedProfile == null || lastUsedProfile.getBci() != bci || lastUsedProfile.getClass() != clazz) {
            if (LOG_PROFILE_MACHINERY) {
                Log.log().string("\tlastUsedProfile was ").string(lastUsedProfile.toString()).string(" but that is not matching the bci or class").newline();
            }
            // search for the correct profile
            for (int i = 0; i < profiles.length; i++) {
                InterpreterProfile profile = profiles[i];
                // make sure lastUsedProfile == null if we did not find a match
                if (profile.getBci() == bci && profile.getClass() == clazz) {
                    if (LOG_PROFILE_MACHINERY) {
                        Log.log().string("\tFound profile with bci and class ").string(profile.toString()).string(" setting index to ").unsigned(i).newline();
                    }
                    lastUsedProfile = profile;
                    lastIndex = i;
                    break;
                } else {
                    lastUsedProfile = null;
                }
            }
        } else {
            if (LOG_PROFILE_MACHINERY) {
                Log.log().string("\tReturning cached lastUsedProfile ").string(lastUsedProfile.toString()).newline();
            }
        }
        // found, probably the last entry used it as well
        return lastUsedProfile;
    }

    public static class TestingBackdoor {
        public static List<InterpreterProfile> profilesAtBCI(InterpreterProfileContainer methodProfile, int bci) {
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
        protected InterpreterProfile next;
        protected final int bci;

        protected InterpreterProfile(int bci) {
            this.bci = bci;
        }

        public void setNext(InterpreterProfile next) {
            this.next = next;
        }

        public InterpreterProfile getNext() {
            return next;
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

    /**
     * Artificial byte code index for the method entry profile.
     */
    private static final int JVMCI_METHOD_ENTRY_BCI = -1;

    /**
     * Special Java bytecode marker value for method entry that is not actually a bytecode. Used to
     * enumerate all operations that can be encountered in Crema that may need a profile.
     */
    private static final int NON_BYTECODE_METHOD_ENTRY = 0xDEAD0001;

    /**
     * Special Java bytecode marker value for a loop backedge that is not actually a bytecode. Used
     * to enumerate all operations that can be encountered in Crema that may need a profile.
     */
    private static final int NON_BYTECODE_LOOP_BACKEDGE = 0xDEAD0002;

    /**
     * All Java bytecodes that somehow represent a branching structure and thus need branch
     * profiles.
     */
    public static final int[] BRANCH_PROFILED_BYTECODES = {Bytecodes.IFEQ,
                    Bytecodes.IFNE,
                    Bytecodes.IFLT,
                    Bytecodes.IFLE,
                    Bytecodes.IFGT,
                    Bytecodes.IFGE,
                    Bytecodes.IF_ICMPEQ,
                    Bytecodes.IF_ICMPNE,
                    Bytecodes.IF_ICMPLT,
                    Bytecodes.IF_ICMPLE,
                    Bytecodes.IF_ICMPGT,
                    Bytecodes.IF_ICMPGE,
                    Bytecodes.IF_ACMPEQ,
                    Bytecodes.IF_ACMPNE,
                    Bytecodes.IFNULL,
                    Bytecodes.IFNONNULL,
                    Bytecodes.TABLESWITCH,
                    Bytecodes.LOOKUPSWITCH
    };

    /**
     * All Java bytecodes that somehow represent a type based operation and thus need type profiles.
     */
    public static final int[] TYPE_PROFILED_BYTECODES = {
                    Bytecodes.INVOKEVIRTUAL,
                    Bytecodes.INVOKEINTERFACE,
                    Bytecodes.INVOKEDYNAMIC,
                    Bytecodes.INVOKESTATIC,
                    Bytecodes.INVOKESPECIAL,
                    Bytecodes.CHECKCAST,
                    Bytecodes.INSTANCEOF,
                    Bytecodes.AALOAD,
                    Bytecodes.AASTORE
    };

    /**
     * All Java bytecodes that represent special operations that should be counted.
     */
    public static final int[] COUNTING_PROFILED_BYTECODES = {NON_BYTECODE_METHOD_ENTRY, NON_BYTECODE_LOOP_BACKEDGE, Bytecodes.GOTO, Bytecodes.GOTO_W};

    public static final int[] ALL_PROFILED_BYTECODES;

    static {
        int size = BRANCH_PROFILED_BYTECODES.length + TYPE_PROFILED_BYTECODES.length + COUNTING_PROFILED_BYTECODES.length;
        ALL_PROFILED_BYTECODES = new int[size];
        System.arraycopy(BRANCH_PROFILED_BYTECODES, 0, ALL_PROFILED_BYTECODES, 0, BRANCH_PROFILED_BYTECODES.length);
        System.arraycopy(TYPE_PROFILED_BYTECODES, 0, ALL_PROFILED_BYTECODES, BRANCH_PROFILED_BYTECODES.length, TYPE_PROFILED_BYTECODES.length);
        System.arraycopy(COUNTING_PROFILED_BYTECODES, 0, ALL_PROFILED_BYTECODES, BRANCH_PROFILED_BYTECODES.length + TYPE_PROFILED_BYTECODES.length, COUNTING_PROFILED_BYTECODES.length);
    }

}
