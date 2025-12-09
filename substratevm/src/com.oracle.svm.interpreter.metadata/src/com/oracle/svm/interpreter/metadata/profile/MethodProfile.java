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
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.interpreter.metadata.Bytecodes;

import jdk.graal.compiler.bytecode.BytecodeStream;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.PauseNode;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

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
    private volatile int lastIndex;

    private final ResolvedJavaMethod method;

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
            if (Bytecodes.isProfiledIfBranch(opcode)) {
                allProfiles.add(new BranchProfile(bci));
            }
            if (Bytecodes.isTypeProfiled(opcode)) {
                allProfiles.add(new TypeProfile(bci));
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

    public JavaTypeProfile getTypeProfile(int bci) {
        return ((TypeProfile) getAtBCI(bci, TypeProfile.class)).toTypeProfile();
    }

    public double getBranchTakenProbability(int bci) {
        return ((BranchProfile) getAtBCI(bci, BranchProfile.class)).takenProfile();
    }

    public void profileType(int bci, ResolvedJavaType type) {
        ((TypeProfile) getAtBCI(bci, TypeProfile.class)).incrementTypeProfile(type);
    }

    public void profileReceiver(int bci, Object receiver) {
        if (receiver == null) {
            /*
             * TODO GR-71949 - profile nullSeen
             */
            return;
        }
        ResolvedJavaType type = DynamicHub.fromClass(receiver.getClass()).getInterpreterType();
        profileType(bci, type);
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
                return -1D;
            }
            return (double) takenCounter / (double) counter;
        }

        public double notTakenProfile() {
            if (counter == 0) {
                return -1D;
            }
            return 1D - takenProfile();
        }

        @Override
        public String toString() {
            return "{BranchProfile:bci=" + bci + ", takenCounter=" + takenCounter + ", counter=" + counter + "}";
        }
    }

    public static class TypeProfile extends CountingProfile {
        /**
         * All types profiled.
         */
        private final ResolvedJavaType[] types;

        /**
         * All counts per type - each [index] represents the number of times {@code types[i]} was
         * seen during profiling.
         */
        private final long[] counts;
        private long notRecordedCount;
        private volatile int nextFreeSlot;

        // value of state is 0 if the state is READING, else it holds the id of the currently
        // writing thread
        private volatile long state;

        public static final AtomicLongFieldUpdater<TypeProfile> PROFILING_STATE_UPDATER = AtomicLongFieldUpdater.newUpdater(TypeProfile.class, "state");

        public TypeProfile(int bci) {
            super(bci);
            final int typeProfileWidth = InterpreterProfilingOptions.JITProfileTypeProfileWidth.getValue();
            types = new ResolvedJavaType[typeProfileWidth];
            counts = new long[typeProfileWidth];
        }

        public double notRecordedProbability() {
            if (counter == 0L) {
                return -1D;
            }
            return (double) notRecordedCount / (double) counter;
        }

        public double getProbability(ResolvedJavaType type) {
            if (counter == 0L) {
                // no entry yet seen
                return -1D;
            }
            for (int i = 0; i < nextFreeSlot; i++) {
                ResolvedJavaType t = types[i];
                if (t.equals(type)) {
                    return (double) counts[i] / (double) counter;
                }
            }
            return -1;
        }

        public void incrementTypeProfile(ResolvedJavaType type) {
            counter++;
            // check if the type was already recorded, in which case we update the counts in a racy
            // fashion
            for (int i = 0; i < types.length && i < nextFreeSlot; i++) {
                if (types[i].equals(type)) {
                    counts[i]++;
                    return;
                }
            }
            if (nextFreeSlot >= types.length) {
                // all types saturated, racy update to notRecorded
                notRecordedCount++;
                return;
            }
            // type was not seen and we have space, "lock" and increment
            addTypeAndIncrement(type);
        }

        @SuppressWarnings("finally")
        private void addTypeAndIncrement(ResolvedJavaType type) {
            final long currentThreadId = Thread.currentThread().threadId();
            assert currentThreadId != 0L : Assertions.errorMessage("ThreadID must never be 0", currentThreadId);
            // we are adding a new type to the profile, we have to perform this under a heavy lock
            while (true) {
                if (!PROFILING_STATE_UPDATER.compareAndSet(this, 0, currentThreadId)) {
                    // try to acquire the state lock, spin if this fails until we are allowed to
                    PauseNode.pause();
                } else {
                    // we got the "lock" to write the profile
                    break;
                }
            }
            try {
                /*
                 * in the meantime its possible another thread already saturated the list of
                 * profiles, in this case we have to add to the remaining ones
                 */
                if (nextFreeSlot >= types.length) {
                    notRecordedCount++;
                    return;
                }
                types[nextFreeSlot] = type;
                counts[nextFreeSlot]++;
                nextFreeSlot = nextFreeSlot + 1;
            } finally {
                if (PROFILING_STATE_UPDATER.compareAndSet(this, currentThreadId, 0)) {
                    return;
                } else {
                    throw GraalError.shouldNotReachHere("Must always be able to set back threadID lock to 0 after profile update");
                }
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(128);
            sb.append("{TypeProfile:bci=").append(bci).append(", counter=").append(counter);
            sb.append(", state=").append(state);
            int limit = Math.min(nextFreeSlot, types.length);
            sb.append(", types=[");
            for (int i = 0; i < limit; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                ResolvedJavaType t = types[i];
                long c = counts[i];
                sb.append(t.getName()).append(':').append(c);
            }
            sb.append(']');
            if (limit >= types.length) {
                sb.append(", saturated=true");
            } else {
                sb.append(", freeSlots=").append(types.length - limit);
            }
            if (notRecordedCount > 0) {
                sb.append(", notRecorded=").append(notRecordedCount);
            }
            sb.append('}');
            return sb.toString();
        }

        public JavaTypeProfile toTypeProfile() {
            if (nextFreeSlot == 0L || counter == 0L) {
                // nothing recorded
                return null;
            }
            // taken from HotSpotMethodData.java#createTypeProfile - sync any bug fixes there
            JavaTypeProfile.ProfiledType[] ptypes = new JavaTypeProfile.ProfiledType[nextFreeSlot];
            for (int i = 0; i < nextFreeSlot; i++) {
                double p = counts[i];
                p = p / counter;
                ptypes[i] = new JavaTypeProfile.ProfiledType(types[i], p);
            }
            Arrays.sort(ptypes);
            double notRecordedTypeProbability = nextFreeSlot < types.length ? 0.0 : Math.min(1.0, Math.max(0.0, notRecordedProbability()));
            assert notRecordedTypeProbability == 0 || nextFreeSlot == types.length;
            return new JavaTypeProfile(TriState.UNKNOWN, notRecordedTypeProbability, ptypes);
        }
    }
}
