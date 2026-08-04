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

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.pgo.profiles.ProfilingImageHeapReason;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.ProfileData;
import jdk.graal.compiler.nodes.ProfileData.ProfileSource;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaType;

/**
 * Interface for looking up profiles during the image build.
 */
public interface PGOProfilesLookup {
    static PGOProfilesLookup singleton() {
        return ImageSingletons.lookup(PGOProfilesLookup.class);
    }

    static PGOProfilesLookup singletonOrNull() {
        return ImageSingletons.contains(PGOProfilesLookup.class) ? ImageSingletons.lookup(PGOProfilesLookup.class) : null;
    }

    // Checkstyle: stop
    /**
     * Profiled value together with the profile source. Possible sources:
     * {@link ProfileSource#PROFILED} and {@link ProfileSource#ADOPTED}.
     */
    record ProfiledValue<T>(ProfileData.ProfileSource source, T value) {
    }
    // Checkstyle: start

    /**
     * Get the number of times a method's body was executed (inlined or not) together with the
     * profile source.
     * <p/>
     * If the code is profiled on the same codebase the profile source will be
     * {@link ProfileSource#PROFILED}, or when the method is profiled on another code base
     * {@link ProfileSource#ADOPTED}. When we have multiple profile sources, this method will always
     * return the {@link ProfileSource} with the lowest ordinal value.
     *
     * @param method method whose call count is required.
     * @return call count with a source, or @{link Optional#empty()} if there is no information.
     */
    Optional<ProfiledValue<Long>> getCallCountProfile(HostedMethod method);

    /**
     * Retrieves the number of times the given method's body was executed (inlined after analysis or
     * not), returning zero if no profiling information is available.
     * <p/>
     * This is a convenience method that calls {@link #getCallCountProfile(HostedMethod)} and
     * returns the profiled call count if present, or {@code 0L} otherwise.
     *
     * @param method the method whose profiled call count is requested.
     * @return the profiled call count, or {@code 0L} if no data is available.
     */
    long getCallCountOrZero(HostedMethod method);

    /**
     * Returns true if <code>method</code>'s body was executed in the profiles collected on the same
     * codebase or inferred to be executed by the ML model, depending on the profiles lookup
     * implementation.
     */
    boolean isExecuted(HostedMethod method);

    /// Gets receiver type occurrence counts for a virtual invoke in `callingContext`.
    ///
    /// @param callingContext path to a node in a calling context forest
    /// @return a map from observed receiver types to their occurrence counts
    Optional<Map<AnalysisType, Long>> getVirtualInvokeProfile(BytecodePosition callingContext);

    /// Gets occurrence counts for methods observed as the targets of a virtual invoke in
    /// `callingContext`.
    ///
    /// For example, if five samples observed `A.toString` and seven observed `B.toString`, the map
    /// contains `A.toString -> 5` and `B.toString -> 7`.
    ///
    /// @param callingContext path to a node in a calling context forest
    /// @return a map from observed target methods to their occurrence counts
    Optional<Map<AnalysisMethod, Long>> getVirtualInvokeMethodProfile(BytecodePosition callingContext);

    /**
     * Get a conditional profile for the given calling-context.
     *
     * @param callingContext path to a node in a calling-context forest for which we are interested.
     * @return type profile as a map or an empty optional if there is no information.
     */
    Optional<ProfiledValue<long[]>> getConditionalProfile(BytecodePosition callingContext);

    /**
     * Get the sum of all the conditional profile values associated with the given method. A
     * conditional profile is associated with the method if it is found in a context where the given
     * method is the head of the context, i.e. the innermost method in the calling context.
     *
     * @param method method whose conditional profiles are requested.
     * @return sum of all the method's conditional profile values or empty optional if there is no
     *         information.
     */
    Optional<ProfiledValue<Long>> getTotalConditionalProfileValue(HostedMethod method);

    /**
     * @see PGOProfilesLookup#getTotalConditionalProfileValue(HostedMethod)
     *
     * @param method method whose conditional profiles are requested.
     * @return sum of all the method's conditional profile values or {@code 0L} if there is no
     *         information.
     */
    long getTotalConditionalProfileValueOrZero(HostedMethod method);

    /**
     * @return all monitor profiles or empty optional if there are no monitor profiles.
     */
    Optional<Map<AnalysisType, Long>> getMonitorProfiles();

    /**
     * @return all samples available in the profile mapped to the number of times that sample was
     *         observed at run time (according to the profile).
     */
    default Optional<Map<NodeSourcePosition, Long>> getSampleCounts() {
        return Optional.empty();
    }

    /**
     *
     * @param category the profile category stored in the profile data.
     * @return {@code true} if the profiling data contains profiles of the given category,
     *         {@code false} otherwise.
     */
    boolean profileCategoryRecorded(String category);

    /**
     * Clear the profile content to reduce memory usage. No profiles can be provided after calling
     * this method i.e. all lookups will return empty values.
     */
    default void clear() {
    }

    @SuppressWarnings("unused")
    default Optional<Map<JavaType, Long>> getInstanceofProfile(BytecodePosition callingContext) {
        return Optional.empty();
    }

    /**
     * Retrieves the timestamp of the first call to the specified method.
     *
     * @param method The method whose first-call timestamp is to be retrieved.
     * @return An {@code Optional} containing the timestamp of the method's first call, or an empty
     *         {@code Optional} if no timestamp is available.
     */
    default Optional<ProfiledValue<Long>> getFirstCallTimestampProfile(@SuppressWarnings("unused") HostedMethod method) {
        return Optional.empty();
    }

    /**
     * Returns the serialized context-augmented heap paths (CAHP) tree to be used for profile-driven
     * image-heap layouting.
     *
     * @return an {@link Optional} with parsed CAHP JSON-like content, or empty if unavailable.
     */
    default Optional<List<ProfilingImageHeapReason>> getContextAugmentedHeapPaths() {
        return Optional.empty();
    }

    /**
     * Returns image-heap access profiles, i.e., a list of CAHP IDs sorted by access index.
     *
     * @return an {@link Optional} containing accessed CAHP IDs, or empty if unavailable.
     */
    default Optional<List<Long>> getImageHeapAccessProfiles() {
        return Optional.empty();
    }
}
