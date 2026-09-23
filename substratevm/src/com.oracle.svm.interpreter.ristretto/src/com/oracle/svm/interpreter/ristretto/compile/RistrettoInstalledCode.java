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
package com.oracle.svm.interpreter.ristretto.compile;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.interpreter.metadata.RuntimeLoadedClassHierarchy;
import com.oracle.svm.graal.meta.SubstrateInstalledCodeImpl;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;
import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Ristretto-specific implementation of {@link SubstrateInstalledCode} that also records a pointer
 * to the corresponding {@link RistrettoMethod}.
 */
public class RistrettoInstalledCode extends SubstrateInstalledCodeImpl implements RuntimeLoadedClassHierarchy.AssumptionDependent {
    /** Runtime method that owns this installed code. */
    private final RistrettoMethod rMethod;
    /** Infopoints indexed by their code offset from the installation start. */
    private final EconomicMap<Integer, Infopoint> relativeIpToInfopoint;
    /** Speculation log shared with recompilations of the owning method. */
    private final RistrettoSpeculationLog speculationLog;
    /** Runtime hierarchy assumptions that must remain valid while this code is installed. */
    private RuntimeLoadedClassHierarchy.Assumptions hierarchyAssumptions;
    /** Whether this code was created with runtime hierarchy assumptions. */
    private final boolean hasHierarchyAssumptions;

    /**
     * Single-bit latch that keeps a reprofiling request attached to this installed-code instance
     * until invalidation reaches the same instance.
     *
     * The deoptimization runtime asks for reprofiling via {@link #reprofile()} before it invokes
     * {@link #invalidate()}, but the profile reset cannot happen eagerly. Between those two calls a
     * newer compilation can become the method's current installed code. By deferring the decision
     * to {@link #invalidate()}, and then passing the consumed bit to
     * {@link RistrettoMethod#invalidateInstalledCode(SubstrateInstalledCodeImpl, boolean)}, the
     * reprofiling request only takes effect if this exact code object is still current.
     *
     * Stale invalidations therefore discard their own reprofiling request instead of resetting the
     * method-global profile state for a newer compilation.
     */
    private AtomicBoolean reprofileRequested;

    /** Creates installed Ristretto code with its infopoints, speculation log, and assumptions. */
    public RistrettoInstalledCode(RistrettoMethod rMethod, EconomicMap<Integer, Infopoint> relativeIpToInfopoint, RistrettoSpeculationLog speculationLog,
                    RuntimeLoadedClassHierarchy.Assumptions hierarchyAssumptions) {
        super(rMethod);
        this.rMethod = rMethod;
        VMError.guarantee(relativeIpToInfopoint != null, "relativeIpToInfopoint must be precomputed");
        VMError.guarantee(speculationLog != null, "speculationLog must be provided");
        VMError.guarantee(hierarchyAssumptions != null, "hierarchyAssumptions must be provided");
        this.relativeIpToInfopoint = relativeIpToInfopoint;
        this.speculationLog = speculationLog;
        this.hierarchyAssumptions = hierarchyAssumptions;
        this.hasHierarchyAssumptions = !hierarchyAssumptions.isEmpty();
        this.reprofileRequested = new AtomicBoolean(false);
    }

    /** Returns the runtime method that owns this installed code. */
    @Override
    public ResolvedJavaMethod getMethod() {
        return rMethod;
    }

    /** Returns the infopoint recorded at {@code relativeIp}. */
    public Infopoint getInfopointForRelativeIP(int relativeIp) {
        var infopoint = relativeIpToInfopoint.get(relativeIp);
        if (infopoint == null) {
            throw VMError.shouldNotReachHere("Must find infopoint for relativeIp " + relativeIp);
        }
        return infopoint;
    }

    /** Returns the speculation log shared with recompilations of the owning method. */
    @Override
    public RistrettoSpeculationLog getSpeculationLog() {
        return speculationLog;
    }

    /**
     * Registers this code for hierarchy-driven invalidation and publishes it in one hierarchy-lock
     * transaction. A successful transaction consumes the pending assumptions; a rejected
     * transaction retains them so that any retry must validate under the hierarchy lock again.
     *
     * @return {@code false} when this code was already finalized, an assumption became invalid, or
     *         publication was rejected
     */
    public synchronized boolean registerHierarchyAssumptionsAndPublish(BooleanSupplier publish) {
        RuntimeLoadedClassHierarchy.Assumptions assumptions = hierarchyAssumptions;
        if (assumptions == null) {
            return false;
        }
        boolean published = assumptions.registerAndPublishIfValid(this, publish);
        if (published) {
            hierarchyAssumptions = null;
        }
        return published;
    }

    /** Test-only access to hierarchy assumption state. */
    public static final class TestingBackdoor {

        /** Returns whether {@code code} depends on at least one runtime hierarchy assumption. */
        public static boolean hasHierarchyAssumptions(RistrettoInstalledCode code) {
            return code.hasHierarchyAssumptions;
        }
    }

    /** Invalidates this code and applies a pending reprofiling request if it is still current. */
    @Override
    public void invalidate() {
        super.invalidate();
        rMethod.invalidateInstalledCode(this, reprofileRequested.getAndSet(false));
    }

    /**
     * Records that the next invalidation attempt for this installed-code instance should restore
     * interpreter profiling for the owning {@link RistrettoMethod}.
     *
     * The actual profile reset is intentionally deferred to {@link #invalidate()} so it remains
     * tied to the installed code object that triggered the request.
     */
    @Override
    public void reprofile() {
        reprofileRequested.set(true);
    }

    /** Records one deoptimization against the owning runtime method. */
    @Override
    public void recordDeoptimization(DeoptimizationReason reason) {
        rMethod.recordDeoptimization(reason);
    }
}
