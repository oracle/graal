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

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.interpreter.metadata.Bytecodes;

import jdk.graal.compiler.bytecode.BytecodeStream;
import jdk.graal.compiler.nodes.IfNode;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

/**
 * 
 * Stores interpreter profiling data collected during the execution of a single
 * {@link ResolvedJavaMethod}.
 * <p>
 * The data is written concurrently by multiple Crema interpreter threads during method execution.
 * It is subsequently read by compilation consumers, typically wrapped in a {@link ProfilingInfo}
 * object.
 * </p>
 * <p>
 * <b>Thread Safety and Mutability:</b> Because multiple interpreter threads update the profiles
 * concurrently, the data within this object is highly volatile. Any profile-related information
 * returned by methods of this class can change significantly and rapidly over time. Consumers must
 * be aware of this mutability when reading and acting upon the profiling data.
 * </p>
 */
public final class MethodProfile {

    /** Artificial byte code index for the method entry profile. */
    private static final int JVMCI_METHOD_ENTRY_BCI = -1;

    /**
     * All profiles for the current method. Includes branch profiles, type profiles, profiles for
     * exceptions etc.
     */
    private final InterpreterProfile[] profiles;

    /**
     * Caches the index of the last returned profile for the next access. Initialized to 0, will be
     * set in {@link #getAtBCI(int, Class)}. This field may be written concurrently by multiple
     * threads, yet we do not synchronize access to it for performance reasons. Its value is always
     * compared to the length of {@code profiles[]} array and thus can never cause out of bounds
     * reads. Also tearing cannot happen because it is a 32 bit field and such writes are atomic on
     * all supported platforms.
     */
    private int lastIndex;

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
            // TODO GR-71949 - profile nullSeen
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

    /**
     * Abstract base class for all interpreter profiles. Every profile has at least a bytecode index
     * (BCI) it is associated with.
     */
    public abstract static class InterpreterProfile {
        protected final int bci;

        protected InterpreterProfile(int bci) {
            this.bci = bci;
        }

        public int getBci() {
            return bci;
        }
    }

    /**
     * Abstraction for counting profiles, i.e., profiles that record a frequency.
     */
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

    /**
     * Abstraction for a binary branch profile for bytecode if instructions. In a compiler graph
     * normally represented by {@link IfNode}.
     */
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

    /**
     * Abstraction for a type profile for bytecode instructions based on types: invokes, type
     * checks, etc.
     * <p>
     * Abstraction is generic to record any set of types together with their count and a
     * {@link JavaTypeProfile#getNotRecordedProbability() not recorded probability}. In a compiler
     * graph normally represented as a {@link JavaTypeProfile} attached to a
     * {@link jdk.graal.compiler.nodes.java.MethodCallTargetNode}.
     */
    public static class TypeProfile extends CountingProfile {

        private static final Unsafe UNSAFE = Unsafe.getUnsafe();

        private static final long TYPE_ARRAY_BASE = UNSAFE.arrayBaseOffset(ResolvedJavaType[].class);

        private static final long TYPE_ARRAY_SHIFT = UNSAFE.arrayIndexScale(ResolvedJavaType[].class);

        /**
         * List of profiled types. Initially allocated to a fixed size and filled lazily during
         * profiling.
         */
        private final ResolvedJavaType[] types;

        /**
         * All counts per type - each [index] represents the number of times {@code types[i]} was
         * seen during profiling.
         */
        private final long[] counts;

        public TypeProfile(int bci) {
            super(bci);
            final int typeProfileWidth = InterpreterProfilingOptions.JITProfileTypeProfileWidth.getValue();
            types = new ResolvedJavaType[typeProfileWidth];
            counts = new long[typeProfileWidth];
        }

        /**
         * See {@link JavaTypeProfile#getNotRecordedProbability()}.
         * 
         * Do not use from performance sensitive code, computes the notRecordedProbability via
         * {@link #toTypeProfile()} .
         */
        public double notRecordedProbability() {
            return toTypeProfile().getNotRecordedProbability();
        }

        /**
         * Return the "saturation" of this type profile, i.e., the number of types already recorded.
         */
        private int getProfiledTypeCount() {
            for (int i = 0; i < types.length; i++) {
                if (types[i] == null) {
                    return i;
                }
            }
            return types.length;
        }

        /**
         * Returns the probability of a given type in this profile.
         */
        public double getProbability(ResolvedJavaType type) {
            if (counter == 0L) {
                // no type profiled yet
                return -1D;
            }
            for (int i = 0; i < getProfiledTypeCount(); i++) {
                ResolvedJavaType t = types[i];
                if (t.equals(type)) {
                    return (double) counts[i] / (double) counter;
                }
            }
            return -1;
        }

        /**
         * Tries to increment the profile count for the given {@code type}. If the profile is
         * saturated ({@code getProfiledTypeCount() == types.length}) only
         * {@link CountingProfile#counter} is incremented (which results in the
         * notRecordedProbability to be increased).
         * <p>
         * If {@code type} cannot be found in the profile tries to add it to the profile array. If
         * that fails, because another thread concurrently added a type (sequentialized via
         * {@link Unsafe#compareAndSetReference(Object, long, Object, Object)}) tries the next slot
         * until the profile is saturated by other threads or the current thread added the type.
         */
        public void incrementTypeProfile(ResolvedJavaType type) {
            for (int i = 0; i < types.length; i++) {
                ResolvedJavaType slotType = types[i];
                if (slotType == null) {
                    /* Try to "claim" this slot for the current type. */
                    long offset = TYPE_ARRAY_BASE + i * TYPE_ARRAY_SHIFT;
                    slotType = (ResolvedJavaType) UNSAFE.compareAndExchangeReference(types, offset, null, type);
                    if (slotType == null) {
                        /* CAS succeeded. */
                        slotType = type;
                    }
                }

                if (slotType.equals(type)) {
                    /* Either the CAS succeeded or another thread wrote the same type already. */
                    counts[i]++;
                    break;
                }
            }

            /* Always update the total count, even if recording the type failed. */
            counter++;
        }

        public JavaTypeProfile toTypeProfile() {
            final int profiledTypeCount = getProfiledTypeCount();
            if (profiledTypeCount == 0 || counter == 0L) {
                // nothing recorded
                return null;
            }
            // taken from HotSpotMethodData.java#createTypeProfile - sync any bug fixes there
            JavaTypeProfile.ProfiledType[] ptypes = new JavaTypeProfile.ProfiledType[profiledTypeCount];
            double totalProbability = 0.0;
            for (int i = 0; i < profiledTypeCount; i++) {
                double p = counts[i];
                p = p / counter;
                totalProbability += p;
                ptypes[i] = new JavaTypeProfile.ProfiledType(types[i], p);
            }
            Arrays.sort(ptypes);
            double notRecordedTypeProbability = profiledTypeCount < types.length ? 0.0 : Math.min(1.0, Math.max(0.0, 1.0 - totalProbability));
            assert notRecordedTypeProbability == 0 || profiledTypeCount == types.length;
            // TODO GR-71949 - null seen
            return new JavaTypeProfile(TriState.UNKNOWN, notRecordedTypeProbability, ptypes);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(128);
            sb.append("{TypeProfile:bci=").append(bci).append(", counter=").append(counter);
            int limit = Math.min(getProfiledTypeCount(), types.length);
            sb.append(", types=[");
            for (int i = 0; i < limit; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                ResolvedJavaType t = types[i];
                long c = counts[i];
                if (t != null) {
                    sb.append(t.getName()).append(':').append(c);
                } else {
                    sb.append("null:").append(c);
                }
            }
            sb.append(']');
            if (limit >= types.length) {
                sb.append(", saturated=true");
            } else {
                sb.append(", freeSlots=").append(types.length - limit);
            }
            sb.append(", notRecorded=").append(notRecordedProbability());
            sb.append('}');
            return sb.toString();
        }
    }
}
