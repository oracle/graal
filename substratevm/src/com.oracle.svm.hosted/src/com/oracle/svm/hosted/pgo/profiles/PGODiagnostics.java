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

/**
 * What {@link IprofProfileResolver} found when it matched a profile to the image, for the build
 * output: one record per section of the iprof file, with how many entries matched and why the
 * others did not; how many descriptors name more than one image method or type, so that no entry
 * is applied to them; and, when asked for, a readable form of every dropped entry. It is built once
 * and does not change. {@link IprofProfilesLookup} counts how the compiler then uses the profile.
 * The numbers depend only on the profile and the image, so two builds of the same inputs report
 * the same ones.
 *
 * @param version the iprof format version of the profile, such as {@code 1.1.0}
 * @param conditional the {@code conditionalProfiles} section: branches and switches
 * @param virtualInvoke the {@code virtualInvokeProfiles} section: receiver types
 * @param sampling the {@code samplingProfiles} section: stack samples
 * @param callCount the {@code callCountProfiles} section
 * @param ambiguousImageMethods how many method descriptors name more than one image method
 * @param ambiguousImageTypes how many type descriptors name more than one image type
 * @param droppedEntries the entries the image cannot use, in whole or, for a stack cut at a missing
 *            frame, in part, such as {@code receiver type com.example.Old not found in the image:
 *            Lcom/example/Foo;.bar()V:9}; empty unless the resolver was asked to collect them
 */
public record PGODiagnostics(String version, ConditionalDiagnostics conditional, VirtualInvokeDiagnostics virtualInvoke, SamplingDiagnostics sampling,
                CallCountDiagnostics callCount, int ambiguousImageMethods, int ambiguousImageTypes, List<String> droppedEntries) {

    /**
     * The {@code conditionalProfiles} section.
     *
     * @param totalEntries the entries in the file
     * @param resolvedEntries the entries whose every frame names a method of the image, which the
     *            lookup serves
     * @param unresolvedEntries the entries with a frame whose method the image lacks
     * @param duplicateContextEntries the entries whose context an earlier entry already has; the
     *            first one is kept
     * @param singleFrameEntries the resolved entries with a context of one frame
     * @param inlinedContextEntries the resolved entries with a context of more frames
     */
    public record ConditionalDiagnostics(int totalEntries, int resolvedEntries, int unresolvedEntries, int duplicateContextEntries, int singleFrameEntries,
                    int inlinedContextEntries) {
        /**
         * The line for the build output, for example {@code iprof conditionalProfiles: 7222
         * entries, 7208 resolved (99.8%), 14 unresolved, 0 duplicate-context; 2801 single-frame,
         * 4407 inlined-context}.
         *
         * @return the summary line
         */
        public String summary() {
            return String.format("iprof conditionalProfiles: %d entries, %d resolved (%.1f%%), %d unresolved, %d duplicate-context; %d single-frame, %d inlined-context",
                            totalEntries, resolvedEntries, totalEntries == 0 ? 0.0 : 100.0 * resolvedEntries / totalEntries, unresolvedEntries, duplicateContextEntries,
                            singleFrameEntries, inlinedContextEntries);
        }
    }

    /**
     * The {@code virtualInvokeProfiles} section.
     *
     * @param totalEntries the entries in the file
     * @param resolvedEntries the entries the lookup serves
     * @param unresolvedEntries the entries with a frame whose method the image lacks
     * @param duplicateContextEntries the entries whose context an earlier entry already has
     * @param noReceiverInImageEntries the entries none of whose receiver types the image has
     * @param receiverRecords the receiver types recorded over all entries with a resolved context
     * @param droppedReceiverRecords the receiver types of those that the image lacks
     * @param receiverEvents the calls recorded over all entries with a resolved context
     * @param droppedReceiverEvents the calls on a receiver type that the image lacks
     */
    public record VirtualInvokeDiagnostics(int totalEntries, int resolvedEntries, int unresolvedEntries, int duplicateContextEntries, int noReceiverInImageEntries,
                    long receiverRecords, long droppedReceiverRecords, long receiverEvents, long droppedReceiverEvents) {
        /**
         * The line for the build output, for example {@code iprof virtualInvokeProfiles: 1736
         * entries, 1726 resolved, 8 unresolved, 0 duplicate-context, 2 without a receiver type in
         * the image; receiver records 2950 (12 dropped), receiver events 81234 (40 dropped)}.
         *
         * @return the summary line
         */
        public String summary() {
            return String.format("iprof virtualInvokeProfiles: %d entries, %d resolved, %d unresolved, %d duplicate-context, %d without a receiver type in the image; " +
                            "receiver records %d (%d dropped), receiver events %d (%d dropped)",
                            totalEntries, resolvedEntries, unresolvedEntries, duplicateContextEntries, noReceiverInImageEntries,
                            receiverRecords, droppedReceiverRecords, receiverEvents, droppedReceiverEvents);
        }
    }

    /**
     * The {@code samplingProfiles} section.
     *
     * @param totalEntries the sampled stacks in the file
     * @param resolvedEntries the stacks whose every frame names a method of the image
     * @param truncatedEntries the stacks cut at their first frame, from the leaf outward, whose
     *            method the image lacks; the inner part is used
     * @param unresolvedEntries the stacks whose leaf the image lacks; they are dropped
     * @param samples the samples of all stacks
     * @param droppedSamples the samples of the dropped stacks
     * @param sampledMethods the distinct methods of the stacks that are used, in whole or in part
     * @param unresolvableFrameSamples the samples of the stacks that stop at each unresolvable
     *            frame, by its descriptor
     */
    public record SamplingDiagnostics(int totalEntries, int resolvedEntries, int truncatedEntries, int unresolvedEntries, long samples, long droppedSamples,
                    int sampledMethods, Map<String, Long> unresolvableFrameSamples) {
        /** How many of the unresolvable frames with the most samples the summary names. */
        private static final int REPORTED_UNRESOLVABLE_FRAMES = 8;

        /**
         * The line for the build output, for example {@code iprof samplingProfiles: 42 stacks, 41
         * fully resolved, 0 truncated at an unresolvable outer frame, 1 unresolved; samples 392 (4
         * dropped); 59 sampled methods; top unresolvable frames by samples:
         * Lcom/example/Old;.run()V=4}. It names at most {@link #REPORTED_UNRESOLVABLE_FRAMES}
         * frames, those with the most samples first.
         *
         * @return the summary line
         */
        public String summary() {
            StringBuilder frames = new StringBuilder();
            unresolvableFrameSamples.entrySet().stream().sorted((first, second) -> Long.compare(second.getValue(), first.getValue())).limit(REPORTED_UNRESOLVABLE_FRAMES)
                            .forEach(entry -> frames.append(frames.isEmpty() ? "" : ", ").append(entry.getKey()).append('=').append(entry.getValue()));
            return String.format("iprof samplingProfiles: %d stacks, %d fully resolved, %d truncated at an unresolvable outer frame, %d unresolved; " +
                            "samples %d (%d dropped); %d sampled methods; top unresolvable frames by samples: %s",
                            totalEntries, resolvedEntries, truncatedEntries, unresolvedEntries, samples, droppedSamples, sampledMethods, frames);
        }
    }

    /**
     * The {@code callCountProfiles} section.
     *
     * @param totalEntries the entries in the file
     * @param resolvedEntries the entries whose every frame names a method of the image
     * @param unresolvedEntries the entries with a frame whose method the image lacks; their counts
     *            still add to the called method's total if the image has that method
     * @param resolvedCalls the calls counted by the resolved entries
     */
    public record CallCountDiagnostics(int totalEntries, int resolvedEntries, int unresolvedEntries, long resolvedCalls) {
        /**
         * The line for the build output, for example {@code iprof callCountProfiles: 4424 entries,
         * 4411 resolved, 13 unresolved; 918273 calls in the resolved entries}.
         *
         * @return the summary line
         */
        public String summary() {
            return String.format("iprof callCountProfiles: %d entries, %d resolved, %d unresolved; %d calls in the resolved entries", totalEntries, resolvedEntries, unresolvedEntries,
                            resolvedCalls);
        }
    }
}
