/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pgo.profiles;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.pgo.IprofFormat;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.pgo.ProfilingUtilities;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import jdk.graal.compiler.nodes.ProfileData.ProfileSource;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.vm.ci.code.BytecodePosition;

/**
 * The {@link PGOProfilesLookup} that serves a parsed iprof profile to the compiler.
 * <p>
 * It answers four recorded categories, which are named as the iprof sections
 * ({@link IprofFormat}): {@code conditionalProfiles} (branch and switch successor counts),
 * {@code callCountProfiles}, {@code virtualInvokeProfiles} (receiver types) and
 * {@code samplingProfiles} (stack samples). Conditional and receiver profiles, and call counts per
 * call edge, are looked up by {@link PGOContext}; call counts per method by method descriptor; the
 * stack samples are handed out as one map. All other queries, such as instanceof, image-heap and
 * monitor profiles, answer as for an absent profile.
 * <p>
 * {@link IprofProfileResolver} builds the tables and the {@link PGODiagnostics} once the
 * {@code HostedUniverse} is available. The tables do not change until {@link #clear} drops them.
 * The counters record how the compiler used the tables; each counter's name starts with the
 * category it counts.
 */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = DisallowLayered.class)
public final class IprofProfilesLookup implements PGOProfilesLookup {

    /**
     * The branch and switch records ({@code conditionalProfiles}) by full context. A record is
     * successor bci, successor index and count.
     */
    private volatile Map<PGOContext, long[]> conditionalData;
    /**
     * Sum of the conditional records of every context that starts with the key, innermost frame
     * first: {@code foo:12} is all executions of that branch, {@code foo:12 < a:5} all executions
     * inlined into {@code a} at bci 5, whatever the outer callers (see
     * {@code IprofProfileResolver.conditionalPrefixSums}).
     */
    private volatile Map<PGOContext, long[]> conditionalPrefixSums;
    /** The stack samples ({@code samplingProfiles}): each resolved stack and its count. */
    private volatile Map<NodeSourcePosition, Long> sampleCounts;
    /** The call count of each method in the image by its descriptor, summed over all its contexts. */
    private volatile Map<String, Long> callCountsByMethod;
    /** The call count of each call edge, by context: the callee first, then its callers. */
    private volatile Map<PGOContext, Long> callCountsByContext;
    /** The receiver types and counts ({@code virtualInvokeProfiles}) by indirect-invoke context. */
    private volatile Map<PGOContext, Map<AnalysisType, Long>> virtualInvokeData;
    /** What the resolver found when it matched the profile to the image. */
    private final PGODiagnostics diagnostics;
    /** Whether a conditional query with no full-context record falls back to a prefix sum. */
    private volatile boolean conditionalContextFallback = true;
    /** Whether a receiver query with no full-context record falls back to a shorter context. */
    private volatile boolean virtualInvokeContextFallback = true;
    /** Whether consumers see the call counts; if not, queries answer as if the profile had none. */
    private volatile boolean useCallCounts = true;
    /** Whether consumers see the stack samples; if not, queries answer as if the profile had none. */
    private volatile boolean useStackSamples = true;
    /**
     * Number of {@link #getConditionalProfile(BytecodePosition)} calls that found a record, for the
     * full context or, with the context fallback, as a prefix sum. This and the other counters
     * count how the profile is used while compiling image graphs, independent of the phase-level
     * {@code PGOApplyProfilesPhase.ProfileQuality} counters (which are only updated when
     * {@code PGOPrintProfileQuality} or {@code PGOPrintProfileQualityDetails} is set). They are
     * always maintained, so that {@code PGOProfileUseFeature} can print a deterministic summary
     * after compilation with {@code PGOPrintStatistics} alone.
     */
    private final AtomicLong conditionalHits = new AtomicLong();
    /** Number of {@link #getConditionalProfile(BytecodePosition)} calls that found no record. */
    private final AtomicLong conditionalMisses = new AtomicLong();
    /** Conditional hits obtained by dropping outermost frames of the query context. */
    private final AtomicLong conditionalContextFallbackHits = new AtomicLong();
    /** Total number of outermost frames dropped over all {@link #conditionalContextFallbackHits}. */
    private final AtomicLong conditionalContextFallbackDroppedFrames = new AtomicLong();
    /** Exact conditional contexts that matched at least one compiler query. */
    private final Set<PGOContext> matchedConditionalContexts = ConcurrentHashMap.newKeySet();
    /** Best successor-record coverage observed for every matched conditional context. */
    private final Map<PGOContext, ConditionalCoverage> conditionalCoverage = new ConcurrentHashMap<>();
    /** Number of {@link #getVirtualInvokeProfile} calls that found receivers, also by fallback. */
    private final AtomicLong virtualInvokeHits = new AtomicLong();
    /** Number of {@link #getVirtualInvokeProfile} calls that found no receivers. */
    private final AtomicLong virtualInvokeMisses = new AtomicLong();
    /** Receiver contexts of the profile that answered at least one compiler query. */
    private final Set<PGOContext> matchedVirtualInvokeContexts = ConcurrentHashMap.newKeySet();
    /** Receiver-profile hits obtained by dropping outermost frames of the query context. */
    private final AtomicLong virtualInvokeContextFallbackHits = new AtomicLong();
    /** Total number of outermost frames dropped over all {@link #virtualInvokeContextFallbackHits}. */
    private final AtomicLong virtualInvokeContextFallbackDroppedFrames = new AtomicLong();
    /** Number of {@link #recordImpossibleReceiver} calls: receiver records the analysis rules out. */
    private final AtomicLong impossibleReceiverRecords = new AtomicLong();
    /** Sum of the counts of the impossible receiver records, capped at {@link Long#MAX_VALUE}. */
    private final AtomicLong impossibleReceiverEvents = new AtomicLong();
    /** Number of call-edge lookups ({@link #getContextCallCount}) that found a count. */
    private final AtomicLong contextCallCountHits = new AtomicLong();
    /** Number of call-edge lookups ({@link #getContextCallCount}) that found none. */
    private final AtomicLong contextCallCountMisses = new AtomicLong();
    /** Number of per-method call-count lookups ({@link #methodCallCount}) that found a count. */
    private final AtomicLong methodCallCountHits = new AtomicLong();
    /** Number of per-method call-count lookups ({@link #methodCallCount}) that found none. */
    private final AtomicLong methodCallCountMisses = new AtomicLong();

    /**
     * How many successor records of a matched conditional context the profile has, and how many of
     * them the phase could match to successors in the graph.
     *
     * @param profiledSuccessors the number of successor records in the profile
     * @param appliedSuccessors the number of those records matched to a successor in the graph
     */
    private record ConditionalCoverage(int profiledSuccessors, int appliedSuccessors) {
    }

    /**
     * A prefix of a query context that has a prefix sum.
     *
     * @param context the shortened context, a key of {@link #conditionalPrefixSums}
     * @param droppedFrames the number of outermost frames dropped from the query context
     */
    private record ShortenedContext(PGOContext context, int droppedFrames) {
    }

    /**
     * Creates the lookup from the tables that {@link IprofProfileResolver} built. The tables are
     * copied.
     *
     * @param conditionalData the branch and switch records by full context
     * @param conditionalPrefixSums the branch and switch records summed by context prefix
     * @param virtualInvokeData the receiver types and their counts by indirect-invoke context
     * @param sampleCounts the resolved stack samples and their counts
     * @param callCountsByMethod the call count of each method by its descriptor
     * @param callCountsByContext the call count of each call edge by its context
     * @param diagnostics what the resolver found when it matched the profile to the image
     */
    IprofProfilesLookup(Map<PGOContext, long[]> conditionalData, Map<PGOContext, long[]> conditionalPrefixSums, Map<PGOContext, Map<AnalysisType, Long>> virtualInvokeData,
                    Map<NodeSourcePosition, Long> sampleCounts, Map<String, Long> callCountsByMethod, Map<PGOContext, Long> callCountsByContext, PGODiagnostics diagnostics) {
        this.conditionalData = Map.copyOf(conditionalData);
        this.conditionalPrefixSums = Map.copyOf(conditionalPrefixSums);
        this.virtualInvokeData = Map.copyOf(virtualInvokeData);
        this.sampleCounts = Map.copyOf(sampleCounts);
        this.callCountsByMethod = Map.copyOf(callCountsByMethod);
        this.callCountsByContext = Map.copyOf(callCountsByContext);
        this.diagnostics = diagnostics;
    }

    /**
     * Sets whether consumers see the call counts ({@code callCountProfiles}).
     *
     * @param enabled {@code false} to answer every call-count query as for a profile without them
     */
    public void setUseCallCounts(boolean enabled) {
        this.useCallCounts = enabled;
    }

    /**
     * Sets whether consumers see the stack samples ({@code samplingProfiles}).
     *
     * @param enabled {@code false} to answer as for a profile without stack samples
     */
    public void setUseStackSamples(boolean enabled) {
        this.useStackSamples = enabled;
    }

    /**
     * Sets whether a conditional query with no full-context record falls back to a prefix sum.
     *
     * @param enabled {@code true} to use the prefix sums
     */
    public void setConditionalContextFallback(boolean enabled) {
        this.conditionalContextFallback = enabled;
    }

    /**
     * Sets whether a receiver query with no full-context record falls back to a shorter context.
     *
     * @param enabled {@code true} to try the shorter contexts
     */
    public void setVirtualInvokeContextFallback(boolean enabled) {
        this.virtualInvokeContextFallback = enabled;
    }

    /**
     * What the resolver found when it matched the profile to the image.
     *
     * @return the resolution diagnostics
     */
    public PGODiagnostics diagnostics() {
        return diagnostics;
    }

    /**
     * Drops the tables, as {@link PGOProfilesLookup#clear} asks, so that their memory can be
     * freed. Every later query then answers as for an absent profile. The counters and the
     * diagnostics stay, but the counts of available contexts read 0 from then on, so the
     * statistics should be printed before.
     */
    @Override
    public void clear() {
        conditionalData = Map.of();
        conditionalPrefixSums = Map.of();
        virtualInvokeData = Map.of();
        sampleCounts = Map.of();
        callCountsByMethod = Map.of();
        callCountsByContext = Map.of();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The categories are the iprof section names of {@link IprofFormat}. Call counts and stack
     * samples read as not recorded when {@link #setUseCallCounts} or {@link #setUseStackSamples}
     * turned them off.
     */
    @Override
    public boolean profileCategoryRecorded(String category) {
        if (IprofFormat.CALL_COUNT_PROFILES.equals(category)) {
            return useCallCounts && !callCountsByMethod.isEmpty();
        }
        if (IprofFormat.VIRTUAL_INVOKE_PROFILES.equals(category)) {
            return !virtualInvokeData.isEmpty();
        }
        if (IprofFormat.SAMPLING_PROFILES.equals(category)) {
            return useStackSamples && !sampleCounts.isEmpty();
        }
        return IprofFormat.CONDITIONAL_PROFILES.equals(category) && !conditionalData.isEmpty();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Answers the record of the full context or, with the context fallback, the prefix sum of the
     * longest matching prefix (see {@link #longestConditionalPrefix}), and updates the conditional
     * counters.
     */
    @Override
    public Optional<ProfiledValue<long[]>> getConditionalProfile(BytecodePosition callingContext) {
        if (callingContext == null) {
            return Optional.empty();
        }
        PGOContext context = PGOContext.of(callingContext);
        long[] records = conditionalData.get(context);
        if (records != null) {
            /* Context statistics are about the profile's own entries; fallback hits are counted apart. */
            matchedConditionalContexts.add(context);
        } else {
            ShortenedContext shortened = longestConditionalPrefix(context);
            if (shortened == null) {
                conditionalMisses.incrementAndGet();
                return Optional.empty();
            }
            records = conditionalPrefixSums.get(shortened.context());
            conditionalContextFallbackHits.incrementAndGet();
            conditionalContextFallbackDroppedFrames.addAndGet(shortened.droppedFrames());
        }
        conditionalHits.incrementAndGet();
        return Optional.of(new ProfiledValue<>(ProfileSource.PROFILED, records));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Keeps, per full context of the profile, the largest counts seen over all copies of the site.
     * Prefix-sum hits are not tracked.
     */
    @Override
    public void recordConditionalProfileApplication(BytecodePosition callingContext, int profiledSuccessors, int appliedSuccessors) {
        if (callingContext == null) {
            return;
        }
        PGOContext context = PGOContext.of(callingContext);
        if (!conditionalData.containsKey(context)) {
            /* Coverage is tracked for the profile's own entries only, like matchedConditionalContexts. */
            return;
        }
        conditionalCoverage.compute(context, (_, previous) -> {
            if (previous == null) {
                return new ConditionalCoverage(profiledSuccessors, appliedSuccessors);
            }
            return new ConditionalCoverage(Math.max(previous.profiledSuccessors(), profiledSuccessors), Math.max(previous.appliedSuccessors(), appliedSuccessors));
        });
    }

    /**
     * The longest prefix of {@code context} (outermost callers dropped) that has a prefix sum: the
     * sum over all profiled contexts that start with it. A prefix sum is the same bytecode branch
     * observed under a different or absent inlining chain, e.g. a callee the profile saw inlined
     * into other callers that this build now inlines into a new one. Returns {@code null} if no
     * prefix has a sum or the context fallback is off.
     */
    private ShortenedContext longestConditionalPrefix(PGOContext context) {
        if (!conditionalContextFallback) {
            return null;
        }
        /* The full length first: the context itself may only have been seen under more callers. */
        for (int depth = context.depth(); depth >= 1; depth--) {
            PGOContext shorter = context.prefix(depth);
            if (conditionalPrefixSums.containsKey(shorter)) {
                return new ShortenedContext(shorter, context.depth() - depth);
            }
        }
        return null;
    }

    /**
     * Number of {@link #getConditionalProfile} calls that found a record, for the full context or
     * as a prefix sum.
     *
     * @return the number of conditional hits
     */
    public long conditionalHits() {
        return conditionalHits.get();
    }

    /**
     * Number of {@link #getConditionalProfile} calls that found no record.
     *
     * @return the number of conditional misses
     */
    public long conditionalMisses() {
        return conditionalMisses.get();
    }

    /**
     * Number of {@link #getConditionalProfile} calls answered by a prefix sum.
     *
     * @return the number of conditional fallback hits
     */
    public long conditionalContextFallbackHits() {
        return conditionalContextFallbackHits.get();
    }

    /**
     * Total number of outermost frames dropped over all conditional fallback hits.
     *
     * @return the number of dropped frames
     */
    public long conditionalContextFallbackDroppedFrames() {
        return conditionalContextFallbackDroppedFrames.get();
    }

    /**
     * Number of distinct conditional contexts of the profile used by at least one compiler query.
     *
     * @return the number of matched conditional contexts
     */
    public int matchedConditionalContextCount() {
        return matchedConditionalContexts.size();
    }

    /**
     * Number of resolved conditional contexts available to the compiler; 0 after {@link #clear}.
     *
     * @return the number of available conditional contexts
     */
    public int availableConditionalContextCount() {
        return conditionalData.size();
    }

    /**
     * Number of resolved conditional contexts whose profile matched every successor at least once.
     *
     * @return the number of fully applied conditional contexts
     */
    public long fullyAppliedConditionalContextCount() {
        return conditionalCoverage.values().stream().filter(coverage -> coverage.profiledSuccessors() > 0 && coverage.appliedSuccessors() >= coverage.profiledSuccessors()).count();
    }

    /**
     * Number of resolved conditional contexts for which only some profiled successors matched.
     *
     * @return the number of partially applied conditional contexts
     */
    public long partiallyAppliedConditionalContextCount() {
        return conditionalCoverage.values().stream().filter(coverage -> coverage.appliedSuccessors() > 0 && coverage.appliedSuccessors() < coverage.profiledSuccessors()).count();
    }

    /**
     * Number of matched conditional contexts for which no profiled successor was applied.
     *
     * @return the number of matched but unapplied conditional contexts
     */
    public long unappliedMatchedConditionalContextCount() {
        return matchedConditionalContexts.size() - fullyAppliedConditionalContextCount() - partiallyAppliedConditionalContextCount();
    }

    /**
     * Number of resolved conditional contexts that no {@link #getConditionalProfile} call has
     * matched exactly so far.
     *
     * @return the number of unused conditional contexts
     */
    public long unusedConditionalContextCount() {
        return Math.max(0, availableConditionalContextCount() - matchedConditionalContexts.size());
    }

    /**
     * Up to {@code limit} resolved conditional contexts in sorted order, such as
     * {@code Ljava/lang/String;.charAt(I)C:12 < Lcom/example/Foo;.bar()V:5}.
     *
     * @param limit the maximum number of contexts to return
     * @return the contexts in their readable form
     */
    public List<String> conditionalContextExamples(int limit) {
        return conditionalData.keySet().stream().map(Object::toString).sorted().limit(limit).toList();
    }

    /**
     * The call count of one call edge: how often the innermost method of {@code callingContext}
     * was called from that chain of callers. Only exact contexts match; there is no fallback.
     *
     * @param callingContext the callee and its callers, innermost first
     * @return the count, or empty if the profile has none for this context or call counts are off
     */
    Optional<Long> getContextCallCount(BytecodePosition callingContext) {
        if (!useCallCounts || callingContext == null) {
            return Optional.empty();
        }
        Long count = callCountsByContext.get(PGOContext.of(callingContext));
        (count == null ? contextCallCountMisses : contextCallCountHits).incrementAndGet();
        return Optional.ofNullable(count);
    }

    @Override
    public Optional<ProfiledValue<Long>> getCallCountProfile(HostedMethod method) {
        if (method == null) {
            return Optional.empty();
        }
        return methodCallCount(ProfilingUtilities.methodDescriptor(method));
    }

    /**
     * The call count of the method with the given canonical descriptor, summed over all contexts.
     *
     * @param methodDescriptor the method's descriptor (see {@link ProfilingUtilities#methodDescriptor})
     * @return the count, or empty if the profile has none for the method or call counts are off
     */
    Optional<ProfiledValue<Long>> methodCallCount(String methodDescriptor) {
        if (!useCallCounts) {
            return Optional.empty();
        }
        Long count = callCountsByMethod.get(methodDescriptor);
        if (count == null) {
            methodCallCountMisses.incrementAndGet();
            return Optional.empty();
        }
        methodCallCountHits.incrementAndGet();
        return Optional.of(new ProfiledValue<>(ProfileSource.PROFILED, count));
    }

    @Override
    public long getCallCountOrZero(HostedMethod method) {
        return getCallCountProfile(method).map(ProfiledValue::value).orElse(0L);
    }

    @Override
    public boolean isExecuted(HostedMethod method) {
        return getCallCountOrZero(method) > 0;
    }

    /**
     * Number of call-edge lookups ({@link #getContextCallCount}) that found a count.
     *
     * @return the number of call-edge hits
     */
    public long contextCallCountHits() {
        return contextCallCountHits.get();
    }

    /**
     * Number of call-edge lookups ({@link #getContextCallCount}) that found none.
     *
     * @return the number of call-edge misses
     */
    public long contextCallCountMisses() {
        return contextCallCountMisses.get();
    }

    /**
     * Number of method call-count lookups that found a count.
     *
     * @return the number of method call-count hits
     */
    public long methodCallCountHits() {
        return methodCallCountHits.get();
    }

    /**
     * Number of method call-count lookups that found none.
     *
     * @return the number of method call-count misses
     */
    public long methodCallCountMisses() {
        return methodCallCountMisses.get();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Answers the receivers of the full context or, with the context fallback, of the longest
     * shorter context that has receivers, and updates the receiver counters.
     */
    @Override
    public Optional<Map<AnalysisType, Long>> getVirtualInvokeProfile(BytecodePosition callingContext) {
        if (callingContext == null || virtualInvokeData.isEmpty()) {
            return Optional.empty();
        }
        PGOContext key = PGOContext.of(callingContext);
        Map<AnalysisType, Long> receivers = virtualInvokeData.get(key);
        if (receivers == null && virtualInvokeContextFallback) {
            /*
             * The same indirect call recorded under a shorter inlining chain: the instrumented image
             * compiled the callee standalone (or inlined it less deeply) while this image inlines
             * it into a new caller. The receiver distribution is a property of the call site and
             * its data flow, which the extra callers do not change for a site whose recorded
             * receivers agree, so the shorter context is the best available estimate. A type guard
             * with a fallback keeps a wrong estimate a performance question, not a correctness one.
             */
            int queryDepth = key.depth();
            for (int depth = queryDepth - 1; depth >= 1 && receivers == null; depth--) {
                PGOContext shorter = key.prefix(depth);
                receivers = virtualInvokeData.get(shorter);
                if (receivers != null) {
                    key = shorter;
                    virtualInvokeContextFallbackHits.incrementAndGet();
                    virtualInvokeContextFallbackDroppedFrames.addAndGet(queryDepth - depth);
                }
            }
        }
        if (receivers == null) {
            virtualInvokeMisses.incrementAndGet();
            return Optional.empty();
        }
        virtualInvokeHits.incrementAndGet();
        matchedVirtualInvokeContexts.add(key);
        return Optional.of(receivers);
    }

    @Override
    public void recordImpossibleReceiver(long events) {
        impossibleReceiverRecords.incrementAndGet();
        /*
         * PGOApplyProfilesPhase drops a site's impossible receivers each time it applies the profile
         * to a copy of the site, so this sum is not bounded by the profile's counts (see
         * ParsedIprofProfile). It stops at Long.MAX_VALUE.
         */
        impossibleReceiverEvents.accumulateAndGet(events, (total, more) -> total > Long.MAX_VALUE - more ? Long.MAX_VALUE : total + more);
    }

    /**
     * Number of {@link #getVirtualInvokeProfile} calls that found receivers, directly or by
     * fallback.
     *
     * @return the number of receiver hits
     */
    public long virtualInvokeHits() {
        return virtualInvokeHits.get();
    }

    /**
     * Number of {@link #getVirtualInvokeProfile} calls that found no receivers. Calls made while
     * the profile has no receivers at all are not counted.
     *
     * @return the number of receiver misses
     */
    public long virtualInvokeMisses() {
        return virtualInvokeMisses.get();
    }

    /**
     * Number of {@link #getVirtualInvokeProfile} calls answered by a shorter context.
     *
     * @return the number of receiver fallback hits
     */
    public long virtualInvokeContextFallbackHits() {
        return virtualInvokeContextFallbackHits.get();
    }

    /**
     * Total number of outermost frames dropped over all receiver fallback hits.
     *
     * @return the number of dropped frames
     */
    public long virtualInvokeContextFallbackDroppedFrames() {
        return virtualInvokeContextFallbackDroppedFrames.get();
    }

    /**
     * Number of distinct receiver contexts of the profile that answered at least one query.
     *
     * @return the number of matched receiver contexts
     */
    public int matchedVirtualInvokeContextCount() {
        return matchedVirtualInvokeContexts.size();
    }

    /**
     * Number of resolved receiver contexts available to the compiler; 0 after {@link #clear}.
     *
     * @return the number of available receiver contexts
     */
    public int availableVirtualInvokeContextCount() {
        return virtualInvokeData.size();
    }

    /**
     * Number of recorded receivers that the analysis ruled out at their site (see
     * {@link #recordImpossibleReceiver}).
     *
     * @return the number of impossible receiver records
     */
    public long impossibleReceiverRecords() {
        return impossibleReceiverRecords.get();
    }

    /**
     * Sum of the counts of the impossible receiver records, capped at {@link Long#MAX_VALUE}.
     *
     * @return the number of impossible receiver events
     */
    public long impossibleReceiverEvents() {
        return impossibleReceiverEvents.get();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Empty when {@link #setUseStackSamples} turned the stack samples off.
     */
    @Override
    public Optional<Map<NodeSourcePosition, Long>> getSampleCounts() {
        Map<NodeSourcePosition, Long> samples = sampleCounts;
        return !useStackSamples || samples.isEmpty() ? Optional.empty() : Optional.of(samples);
    }

    @Override
    public Optional<Map<AnalysisMethod, Long>> getVirtualInvokeMethodProfile(BytecodePosition callingContext) {
        return Optional.empty();
    }

    @Override
    public Optional<ProfiledValue<Long>> getTotalConditionalProfileValue(HostedMethod method) {
        return Optional.empty();
    }

    @Override
    public long getTotalConditionalProfileValueOrZero(HostedMethod method) {
        return 0L;
    }

    @Override
    public Optional<Map<AnalysisType, Long>> getMonitorProfiles() {
        return Optional.empty();
    }
}
