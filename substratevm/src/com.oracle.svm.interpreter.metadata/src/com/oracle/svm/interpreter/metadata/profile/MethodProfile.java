/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.interpreter.metadata.BytecodeStream;
import com.oracle.svm.interpreter.metadata.Bytecodes;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.LookupSwitch;
import com.oracle.svm.interpreter.metadata.TableSwitch;
import com.oracle.svm.shared.NeverInline;

import jdk.graal.compiler.nodes.IfNode;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.DeoptimizationReason;
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
    /** Number of backedges between attempts to compile or enter OSR code. */
    private static final int OSR_PROCESSING_INTERVAL = 1024;
    /** Requests the OSR slow path at the next execution of this backedge. */
    private static final long OSR_PROCESSING_FORCED = -1;
    /** The backedge count has not yet crossed the initial OSR threshold. */
    private static final long OSR_PROCESSING_BELOW_INITIAL_THRESHOLD = 0;

    /**
     * All profiles for the current method. Includes branch profiles, type profiles, profiles for
     * exceptions etc. Look up profiles by BCI and type with {@link #getAtBCI(int, Class)}. For OSR
     * backedge profiles, {@link #getOSRBackedgeProfileIndex(int)} returns an index into this array.
     */
    private final InterpreterProfile[] profiles;
    /**
     * Per-profile OSR scheduling state, using the same indices as {@link #profiles}. Obtain an OSR
     * backedge profile's index with {@link #getOSRBackedgeProfileIndex(int)}, or use
     * {@link #findOSRBackedgeProfileIndex(int)} when the target might not have a profile. Entries for
     * other profile types are unused.
     *
     * A zero value means that the initial threshold has not been crossed, an entry with the value
     * {@link #OSR_PROCESSING_FORCED} requests the OSR slow path on the next backedge, and a positive
     * value is the next backedge count at which the slow path is due.
     * Like the profile counters, scheduling updates are approximate under concurrent execution.
     * A concurrent scheduling write can overwrite a request to force the slow path; periodic checks
     * still poll for installed code. Compilation ownership is synchronized separately by Ristretto.
     */
    private final long[] nextOSRProcessingBackedgeCounts;

    /**
     * Caches the index of the last returned profile for the next access. Initialized to 0, will be
     * set in {@link #getAtBCI(int, Class)}. This field may be written concurrently by multiple
     * threads. Updates are coordinated through synchronized profile lookup and reset paths.
     */
    private int lastIndex;

    private final ResolvedJavaMethod method;

    private boolean isMature;

    /** Counts deoptimizations by reason for runtime-compiled code associated with this method. */
    private final int[] deoptimizationCounts = new int[DeoptimizationReason.values().length];

    public MethodProfile(ResolvedJavaMethod method, Function<InterpreterResolvedJavaType, ResolvedJavaType> ristrettoTypeSupplier) {
        this.method = method;
        this.profiles = buildProfiles(method, ristrettoTypeSupplier);
        this.nextOSRProcessingBackedgeCounts = new long[profiles.length];
    }

    private static InterpreterProfile[] buildProfiles(ResolvedJavaMethod method, Function<InterpreterResolvedJavaType, ResolvedJavaType> ristrettoTypeSupplier) {
        byte[] code = method.getCode();
        List<InterpreterProfile> allProfiles = new ArrayList<>();
        // we always add a method entry counting profile
        allProfiles.add(new CountingProfile(JVMCI_METHOD_ENTRY_BCI));

        for (int targetBCI : collectBackedgeTargets(code)) {
            allProfiles.add(new BackedgeProfile(targetBCI));
        }
        int bci = 0;
        while (bci < BytecodeStream.endBCI(code)) {
            int opcode = BytecodeStream.currentBC(code, bci);
            // we can have multiple profiles for a single BCI: type, exception etc
            if (Bytecodes.isProfiledIfBranch(opcode)) {
                allProfiles.add(new BranchProfile(bci));
            }
            if (Bytecodes.isTypeProfiled(opcode)) {
                allProfiles.add(new TypeProfile(bci, ristrettoTypeSupplier));
            }
            bci = BytecodeStream.nextBCI(code, bci);
        }

        return allProfiles.toArray(new InterpreterProfile[0]);
    }

    /**
     * Returns the sorted unique bytecode indices reached by backward branches in {@code code}.
     *
     * These BCIs are the only loop-entry candidates for Ristretto OSR backedge profiling. The helper
     * is shared with the Ristretto OSR lifecycle table so both structures agree on the set of
     * backedge targets.
     */
    public static int[] collectBackedgeTargets(byte[] code) {
        if (code == null || code.length == 0) {
            return new int[0];
        }
        BackedgeTargetBuilder targets = new BackedgeTargetBuilder();
        for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
            int opcode = BytecodeStream.currentBC(code, bci);
            switch (opcode) {
                case Bytecodes.TABLESWITCH:
                case Bytecodes.LOOKUPSWITCH:
                    addSwitchBackedgeTargets(targets, code, bci, opcode);
                    break;
                default:
                    if (isOSRBackedgeBranch(opcode)) {
                        targets.add(bci, BytecodeStream.readBranchDest(code, bci));
                    }
                    break;
            }
        }
        return targets.sortedUnique();
    }

    private static boolean isOSRBackedgeBranch(int opcode) {
        return Bytecodes.isBranch(opcode) && opcode != Bytecodes.JSR && opcode != Bytecodes.JSR_W;
    }

    /**
     * Adds the default target and all explicit case targets for a switch bytecode when they branch
     * backward.
     */
    private static void addSwitchBackedgeTargets(BackedgeTargetBuilder targets, byte[] code, int bci, int opcode) {
        targets.add(bci, switchDefaultTarget(code, bci, opcode));
        for (int i = 0; i < switchNumberOfCases(code, bci, opcode); i++) {
            targets.add(bci, switchTargetAt(code, bci, opcode, i));
        }
    }

    /**
     * Returns the default branch target for either switch bytecode shape.
     */
    private static int switchDefaultTarget(byte[] code, int bci, int opcode) {
        return switch (opcode) {
            case Bytecodes.TABLESWITCH -> TableSwitch.defaultTarget(code, bci);
            case Bytecodes.LOOKUPSWITCH -> LookupSwitch.defaultTarget(code, bci);
            default -> throw new IllegalArgumentException("Expected switch bytecode: " + opcode);
        };
    }

    /**
     * Returns the number of explicit case targets for either switch bytecode shape.
     */
    private static int switchNumberOfCases(byte[] code, int bci, int opcode) {
        return switch (opcode) {
            case Bytecodes.TABLESWITCH -> TableSwitch.numberOfCases(code, bci);
            case Bytecodes.LOOKUPSWITCH -> LookupSwitch.numberOfCases(code, bci);
            default -> throw new IllegalArgumentException("Expected switch bytecode: " + opcode);
        };
    }

    /**
     * Returns one explicit case target for either switch bytecode shape.
     */
    private static int switchTargetAt(byte[] code, int bci, int opcode, int index) {
        return switch (opcode) {
            case Bytecodes.TABLESWITCH -> TableSwitch.targetAt(code, bci, index);
            case Bytecodes.LOOKUPSWITCH -> LookupSwitch.targetAt(code, bci, index);
            default -> throw new IllegalArgumentException("Expected switch bytecode: " + opcode);
        };
    }

    /**
     * Small append-only collector that keeps bytecode scanning allocation-light and normalizes the
     * final target list only once.
     *
     * A JDK set would also sort and deduplicate the target BCIs, but it would box every bytecode index
     * while scanning method bytecodes. This primitive buffer matches the rest of the profiling code:
     * append cheaply on the scan path, then sort and compact the small final target list once.
     */
    private static final class BackedgeTargetBuilder {
        /*
         * Most methods have only a small number of backward branches. Four slots keep the common scan
         * allocation-free while still growing cheaply for methods with more OSR-capable targets.
         */
        private int[] targets = new int[4];
        private int size;

        void add(int bci, int targetBCI) {
            if (targetBCI <= bci && targetBCI >= 0) {
                ensureCapacity(size + 1);
                targets[size] = targetBCI;
                size++;
            }
        }

        private void ensureCapacity(int requiredCapacity) {
            if (requiredCapacity > targets.length) {
                targets = Arrays.copyOf(targets, targets.length * 2);
            }
        }

        int[] sortedUnique() {
            if (size == 0) {
                return new int[0];
            }
            Arrays.sort(targets, 0, size);
            int uniqueSize = 1;
            for (int i = 1; i < size; i++) {
                if (targets[i] != targets[uniqueSize - 1]) {
                    targets[uniqueSize++] = targets[i];
                }
            }
            return Arrays.copyOf(targets, uniqueSize);
        }
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
        return ++getAtBCI(JVMCI_METHOD_ENTRY_BCI, CountingProfile.class).counter;
    }

    public long getProfileEntryCount() {
        return getAtBCI(JVMCI_METHOD_ENTRY_BCI, CountingProfile.class).counter;
    }

    /**
     * Increments the hotness counter for the OSR backedge that reaches {@code targetBCI} and returns
     * whether Ristretto should attempt OSR.
     *
     * The OSR slow path is requested on the first backedge beyond {@code osrThreshold}. After the
     * initial threshold crossing, the interpreter checks for installed OSR code at periodic
     * backedge-count intervals. Subsequent runtime changes to the threshold do not affect these
     * periodic checks. The slow path rechecks the current threshold before submitting compilation,
     * but checks for installed OSR code independently of that threshold.
     * {@link #resetOSRBackedgeProcessing(int)} requests the slow path on the next backedge, for example
     * after compilation completes.
     *
     * @return true if the caller should enter the OSR slow path; false if {@code targetBCI} is not a
     *         statically identified OSR target or an OSR attempt is not yet due
     */
    public boolean profileOSRBackedgeAndShouldProcess(int targetBCI, int osrThreshold) {
        int profileIndex = findOSRBackedgeProfileIndex(targetBCI);
        if (profileIndex < 0) {
            return false;
        }
        long backedgeCount = ((BackedgeProfile) profiles[profileIndex]).incrementBackedgeCounter();
        long nextProcessingBackedgeCount = nextOSRProcessingBackedgeCounts[profileIndex];
        if (nextProcessingBackedgeCount == OSR_PROCESSING_BELOW_INITIAL_THRESHOLD) {
            if (backedgeCount <= osrThreshold) {
                return false;
            }
        } else if (nextProcessingBackedgeCount != OSR_PROCESSING_FORCED && backedgeCount < nextProcessingBackedgeCount) {
            return false;
        }
        nextOSRProcessingBackedgeCounts[profileIndex] = backedgeCount + OSR_PROCESSING_INTERVAL;
        return true;
    }

    /** Returns the current hotness count without incrementing it or changing OSR scheduling. */
    public long getOSRBackedgeCount(int targetBCI) {
        return ((BackedgeProfile) profiles[getOSRBackedgeProfileIndex(targetBCI)]).getCounter();
    }

    /**
     * Requests the Ristretto OSR slow path on the next backedge for {@code targetBCI}.
     *
     * Ristretto calls this after compilation completes so the interpreter can promptly observe
     * installed code or retry a failed compilation. Concurrent scheduling updates can delay this
     * observation until a later periodic attempt.
     */
    public void resetOSRBackedgeProcessing(int targetBCI) {
        nextOSRProcessingBackedgeCounts[getOSRBackedgeProfileIndex(targetBCI)] = OSR_PROCESSING_FORCED;
    }

    /**
     * Resets the counter and OSR processing cadence for the backedge that reaches {@code targetBCI}.
     */
    public synchronized void resetOSRBackedgeProfile(int targetBCI) {
        for (int i = 0; i < profiles.length; i++) {
            InterpreterProfile profile = profiles[i];
            if (profile.getBci() == targetBCI && profile.getClass() == BackedgeProfile.class) {
                profiles[i] = profile.reset();
                nextOSRProcessingBackedgeCounts[i] = OSR_PROCESSING_BELOW_INITIAL_THRESHOLD;
                return;
            }
        }
        throw new IllegalArgumentException("No OSR backedge profile for " + method + "@" + targetBCI);
    }

    /**
     * Resets all OSR backedge counters and processing cadence state for this method.
     */
    public synchronized void resetOSRBackedgeProfiles() {
        for (int i = 0; i < profiles.length; i++) {
            InterpreterProfile profile = profiles[i];
            if (profile.getClass() == BackedgeProfile.class) {
                profiles[i] = profile.reset();
                nextOSRProcessingBackedgeCounts[i] = OSR_PROCESSING_BELOW_INITIAL_THRESHOLD;
            }
        }
    }

    /**
     * Returns the internal profile-array index for an OSR backedge target.
     */
    private int getOSRBackedgeProfileIndex(int targetBCI) {
        int profileIndex = findOSRBackedgeProfileIndex(targetBCI);
        if (profileIndex >= 0) {
            return profileIndex;
        }
        throw new IllegalArgumentException("No OSR backedge profile for " + method + "@" + targetBCI);
    }

    /**
     * Returns the internal profile-array index for an OSR backedge target, or {@code -1} if the
     * target has no statically identified OSR backedge.
     */
    private synchronized int findOSRBackedgeProfileIndex(int targetBCI) {
        int lastIndexLocal = lastIndex;
        for (int i = lastIndexLocal; i < profiles.length; i++) {
            InterpreterProfile profile = profiles[i];
            if (profile.getBci() == targetBCI && profile.getClass() == BackedgeProfile.class) {
                lastIndex = i;
                return i;
            }
        }
        for (int i = 0; i < lastIndexLocal; i++) {
            InterpreterProfile profile = profiles[i];
            if (profile.getBci() == targetBCI && profile.getClass() == BackedgeProfile.class) {
                lastIndex = i;
                return i;
            }
        }
        return -1;
    }

    @NeverInline("Keep branch profiling machinery out of bytecode-handler stubs")
    public void profileBranch(int bci, boolean taken) {
        if (taken) {
            getAtBCI(bci, BranchProfile.class).incrementTakenCounter();
        } else {
            getAtBCI(bci, BranchProfile.class).incrementNotTakenCounter();
        }
    }

    public JavaTypeProfile getTypeProfile(int bci) {
        return getAtBCI(bci, TypeProfile.class).toTypeProfile();
    }

    public TriState getNullSeen(int bci) {
        TypeProfile typeProfile = getAtBCI(bci, TypeProfile.class);
        return typeProfile == null ? TriState.UNKNOWN : typeProfile.getNullSeen();
    }

    public double getBranchTakenProbability(int bci) {
        return getAtBCI(bci, BranchProfile.class).takenProfile();
    }

    public void profileType(int bci, ResolvedJavaType type) {
        getAtBCI(bci, TypeProfile.class).incrementTypeProfile(type);
    }

    public void profileReceiver(int bci, Object receiver) {
        if (receiver == null) {
            getAtBCI(bci, TypeProfile.class).markNullSeen();
            return;
        }
        ResolvedJavaType type = DynamicHub.fromClass(receiver.getClass()).getInterpreterType();
        profileType(bci, type);
    }

    public synchronized void reprofile() {
        /*
         * A reprofile request comes from invalidating an installed compilation and should make the
         * method entry behave like a fresh profiling epoch again: invocation counters have to mature
         * naturally before Ristretto queues another compile. It must not mean "forget all bytecode
         * feedback". The per-BCI branch and receiver-type profiles are the evidence that explains why
         * the previous speculative compile failed, and they are also the data the next compile needs to
         * avoid repeating the same too-narrow speculation. HotSpot's method-data/profile storage has
         * the same broad shape: deoptimization can request more profiling at the method entry without
         * discarding the existing method-data cells that describe bytecode-level behavior. Ristretto
         * therefore resets only the synthetic JVMCI method-entry profile here and deliberately keeps
         * the bytecode-indexed profiles and deoptimization counters durable across reprofiling. OSR
         * targets that already crossed their initial threshold are forced to process their next
         * backedge so that installed code remains promptly observable. Targets that have not crossed
         * the threshold remain in their initial state.
         */
        for (int i = 0; i < profiles.length; i++) {
            if (profiles[i].getBci() == JVMCI_METHOD_ENTRY_BCI) {
                profiles[i] = profiles[i].reset();
            }
            if (profiles[i].getClass() == BackedgeProfile.class && nextOSRProcessingBackedgeCounts[i] != OSR_PROCESSING_BELOW_INITIAL_THRESHOLD) {
                nextOSRProcessingBackedgeCounts[i] = OSR_PROCESSING_FORCED;
            }
        }
        lastIndex = 0;
        isMature = false;
    }

    public synchronized void recordDeoptimization(DeoptimizationReason reason) {
        deoptimizationCounts[reason.ordinal()]++;
    }

    public synchronized int getDeoptimizationCount(DeoptimizationReason reason) {
        return deoptimizationCounts[reason.ordinal()];
    }

    /**
     * Gets the profile for {@code bci} whose class is {@code clazz}.
     * 
     * @return null if there's no profile
     */
    private synchronized <T extends InterpreterProfile> T getAtBCI(int bci, Class<T> clazz) {
        int lastIndexLocal = lastIndex;
        for (int i = lastIndexLocal; i < profiles.length; i++) {
            InterpreterProfile profile = profiles[i];
            if (profile.getBci() == bci && profile.getClass() == clazz) {
                lastIndex = i;
                return clazz.cast(profile);
            }
        }
        for (int i = 0; i < lastIndexLocal; i++) {
            InterpreterProfile profile = profiles[i];
            if (profile.getBci() == bci && profile.getClass() == clazz) {
                lastIndex = i;
                return clazz.cast(profile);
            }
        }
        return null;
    }

    public static class TestingBackdoor {
        public static int osrProcessingInterval() {
            return OSR_PROCESSING_INTERVAL;
        }

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

        public abstract InterpreterProfile reset();
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

        @Override
        public InterpreterProfile reset() {
            return new CountingProfile(bci);
        }
    }

    /**
     * Counting profile for an OSR-capable loop backedge target.
     *
     * The interpreter updates this profile when execution reaches a backward-branch target. It is only
     * a hotness counter; Ristretto-specific submission thresholds and installed-code polling cadence
     * are kept in {@link MethodProfile} so the profile remains a plain counting profile.
     */
    public static class BackedgeProfile extends CountingProfile {
        BackedgeProfile(int bci) {
            super(bci);
        }

        public long incrementBackedgeCounter() {
            return ++counter;
        }

        @Override
        public String toString() {
            return "{BackedgeProfile:bci=" + bci + ", counter=" + counter + "}";
        }

        @Override
        public InterpreterProfile reset() {
            return new BackedgeProfile(bci);
        }
    }

    /**
     * Abstraction for a binary branch profile for bytecode if instructions. In a compiler graph
     * normally represented by {@link IfNode}.
     */
    public static class BranchProfile extends CountingProfile {
        private long notTakenCounter;

        public BranchProfile(int bci) {
            super(bci);
        }

        public void incrementTakenCounter() {
            counter++;
        }

        public void incrementNotTakenCounter() {
            notTakenCounter++;
        }

        public double takenProfile() {
            /*
             * The counters can be updated concurrently so we only read them once.
             */
            long localTakenCounter = counter;
            long sum = localTakenCounter + notTakenCounter;
            if (sum == 0) {
                return -1D;
            }
            return (double) localTakenCounter / (double) sum;
        }

        public double notTakenProfile() {
            /*
             * The counters can be updated concurrently so we only read them once.
             */
            long localNotTakenCounter = notTakenCounter;
            long sum = counter + localNotTakenCounter;
            if (sum == 0) {
                return -1D;
            }
            return (double) localNotTakenCounter / (double) sum;
        }

        @Override
        public String toString() {
            return "{BranchProfile:bci=" + bci + ", takenCounter=" + counter + ", notTakenCounter=" + notTakenCounter + "}";
        }

        @Override
        public InterpreterProfile reset() {
            return new BranchProfile(bci);
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
         *
         * Counts are updated concurrently without synchronization. Readers snapshot the counts and
         * compute probabilities using a total count at least as large as the sum of sampled counts.
         */
        private final long[] counts;

        /**
         * Tracks whether an observation could not be associated with a recorded type. Once set,
         * snapshots reserve probability mass for at least one unrecorded observation even if racy
         * updates lose more increments from {@link #counter} than from {@link #counts}.
         */
        private volatile boolean unrecordedSeen;

        /**
         * Tracks whether a null receiver or value was observed for this profiled bytecode.
         */
        private volatile boolean nullSeen;

        /**
         * We profile interpreter types but when we export the information to the compiler as a type
         * profile we need to use ristretto types.
         */
        private final Function<InterpreterResolvedJavaType, ResolvedJavaType> ristrettoTypeSupplier;

        public TypeProfile(int bci, Function<InterpreterResolvedJavaType, ResolvedJavaType> ristrettoTypeSupplier) {
            super(bci);
            final int typeProfileWidth = InterpreterProfilingOptions.JITProfileTypeProfileWidth.getValue();
            types = new ResolvedJavaType[typeProfileWidth];
            counts = new long[typeProfileWidth];
            this.ristrettoTypeSupplier = ristrettoTypeSupplier;
        }

        @Override
        public InterpreterProfile reset() {
            return new TypeProfile(bci, ristrettoTypeSupplier);
        }

        /**
         * See {@link JavaTypeProfile#getNotRecordedProbability()}.
         * 
         * Do not use from performance sensitive code, computes the notRecordedProbability via
         * {@link #toTypeProfile()} .
         */
        public double notRecordedProbability() {
            JavaTypeProfile typeProfile = toTypeProfile();
            if (typeProfile == null) {
                throw new IllegalStateException("No JavaTypeProfile recorded at bci " + bci);
            }
            return typeProfile.getNotRecordedProbability();
        }

        public void markNullSeen() {
            if (!nullSeen) {
                nullSeen = true;
            }
        }

        public TriState getNullSeen() {
            if (nullSeen) {
                return TriState.TRUE;
            }
            return getCounter() == 0L ? TriState.UNKNOWN : TriState.FALSE;
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
            int profiledTypeCount = getProfiledTypeCount();
            boolean localUnrecordedSeen = unrecordedSeen;
            long localCounter = getCounter();
            long matchingTypeCount = 0L;
            long sampledTypeCountSum = 0L;
            boolean typeFound = false;
            for (int i = 0; i < profiledTypeCount; i++) {
                long count = counts[i];
                sampledTypeCountSum += count;
                if (types[i].equals(type)) {
                    matchingTypeCount = count;
                    typeFound = true;
                }
            }
            localCounter = getSnapshotCounter(localCounter, sampledTypeCountSum, localUnrecordedSeen);
            if (localCounter == 0L) {
                // no type profiled yet
                return -1D;
            }
            if (typeFound) {
                return (double) matchingTypeCount / (double) localCounter;
            }
            return -1;
        }

        /**
         * Tries to increment the profile count for the given {@code type}. If the profile is
         * saturated ({@code getProfiledTypeCount() == types.length}) only the unrecorded count is
         * incremented (which results in the notRecordedProbability to be increased).
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
                    counter++;
                    return;
                }
            }

            /* The profile is saturated with other types. Record this observation as unrecorded. */
            incrementCounter();
        }

        /**
         * Records an observation without a corresponding type count. Such observations contribute
         * to the not-recorded probability, including when the type profile is saturated.
         */
        @Override
        public void incrementCounter() {
            super.incrementCounter();
            markUnrecordedSeen();
        }

        private void markUnrecordedSeen() {
            if (!unrecordedSeen) {
                unrecordedSeen = true;
            }
        }

        /**
         * Returns a total count that includes all sampled type counts and reserves probability mass
         * for at least one unrecorded observation when one has been seen. Concurrent updates can lose
         * more increments from the total counter than from the individual type counts.
         */
        private static long getSnapshotCounter(long localCounter, long sampledTypeCountSum, boolean localUnrecordedSeen) {
            long minimumCounter = sampledTypeCountSum + (localUnrecordedSeen ? 1L : 0L);
            return Math.max(localCounter, minimumCounter);
        }

        public JavaTypeProfile toTypeProfile() {
            final int profiledTypeCount = getProfiledTypeCount();
            boolean localUnrecordedSeen = unrecordedSeen;
            if (profiledTypeCount == 0) {
                if (localUnrecordedSeen) {
                    return new JavaTypeProfile(getNullSeen(), 1.0, new JavaTypeProfile.ProfiledType[0]);
                }
                return getNullSeen() == TriState.TRUE ? new JavaTypeProfile(TriState.TRUE, 0.0, new JavaTypeProfile.ProfiledType[0]) : null;
            }
            /* Profiles are updated and read concurrently without synchronization. Read the total
             * and each bucket once, then compute probabilities using at least their summed count. */
            final long[] localCounts = new long[profiledTypeCount];
            long localCounter = getCounter();
            long sampledTypeCountSum = 0L;
            for (int i = 0; i < profiledTypeCount; i++) {
                localCounts[i] = counts[i];
                sampledTypeCountSum += localCounts[i];
            }
            localCounter = getSnapshotCounter(localCounter, sampledTypeCountSum, localUnrecordedSeen);
            if (localCounter == 0L) {
                return null;
            }
            // taken from HotSpotMethodData.java#createTypeProfile - sync any bug fixes there
            JavaTypeProfile.ProfiledType[] ptypes = new JavaTypeProfile.ProfiledType[profiledTypeCount];
            double totalProbability = 0.0;
            for (int i = 0; i < profiledTypeCount; i++) {
                double p = localCounts[i];
                p = p / localCounter;
                totalProbability += p;
                /*
                 * When we give the profile out we want it to be ristretto types only. Consumers,
                 * especially the compiler, will require ristretto view of the JVMCI data.
                 */
                final ResolvedJavaType rType = ((InterpreterResolvedJavaType) types[i]).getRistrettoType(ristrettoTypeSupplier);
                ptypes[i] = new JavaTypeProfile.ProfiledType(rType, p);
            }
            Arrays.sort(ptypes);
            double notRecordedTypeProbability = !localUnrecordedSeen && profiledTypeCount < types.length ? 0.0 : Math.min(1.0, Math.max(0.0, 1.0 - totalProbability));
            assert notRecordedTypeProbability == 0 || localUnrecordedSeen || profiledTypeCount == types.length;
            return new JavaTypeProfile(getNullSeen(), notRecordedTypeProbability, ptypes);
        }

        @Override
        public String toString() {
            JavaTypeProfile typeProfile = toTypeProfile();
            StringBuilder sb = new StringBuilder(128);
            sb.append("{TypeProfile:bci=").append(bci).append(", counter=").append(getCounter()).append(", nullSeen=").append(getNullSeen());
            int limit = Math.min(getProfiledTypeCount(), types.length);
            sb.append(", types=[");
            for (int i = 0; i < limit; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                ResolvedJavaType t = types[i];
                long c = counts[i];
                if (t != null) {
                    sb.append(t.toClassName()).append(':').append(c);
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
            sb.append(", notRecorded=").append(typeProfile == null ? "n/a" : typeProfile.getNotRecordedProbability());
            sb.append('}');
            return sb.toString();
        }
    }
}
