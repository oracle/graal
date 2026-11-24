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
package com.oracle.svm.core.methodhandles;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

import com.oracle.svm.core.deopt.RuntimeAssumptionSupport;
import com.oracle.svm.core.deopt.RuntimeAssumptionSupport.DependentAttachment;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.internal.misc.Unsafe;

/**
 * Maintains dependencies of runtime-compiled code on {@link CallSite} targets.
 *
 * <p>
 * The invariant is: published code must never keep using a target that was already replaced. A
 * compilation records the target it used. At publication time, that target is checked again and
 * the installed code is attached to the CallSite as a dependent. A later target update invalidates
 * every attached dependent before it stores the new target.
 *
 * <p>
 * Publication performs the check, attachment, and publication under the shared runtime-assumption
 * monitor:
 *
 * <pre>{@code
 * publish(code, assumptions):
 *     lock assumptionMonitor
 *         for each (callSite, expectedTarget):
 *             if callSite.updateInProgress or callSite.target != expectedTarget:
 *                 reject publication
 *
 *         for each callSite target assumption:
 *             callSite.dependents.add(weakReferenceTo(code))
 *
 *         publish code
 *     unlock assumptionMonitor
 * }</pre>
 *
 * A normal target update reserves one CallSite, invalidates outside the shared monitor, and only
 * then installs the new target:
 *
 * <pre>{@code
 * update(callSite, newTarget):
 *     lock callSite.state                  // serialize updates of this CallSite
 *         lock assumptionMonitor
 *             if nonvolatile update and callSite.target == newTarget:
 *                 return
 *             callSite.updateInProgress = true
 *             dependents = take(callSite.dependents)
 *         unlock assumptionMonitor
 *
 *         for each code in dependents:
 *             invalidate code              // may be expensive
 *
 *         lock assumptionMonitor
 *             callSite.target = newTarget
 *             callSite.updateInProgress = false
 *         unlock assumptionMonitor
 *     unlock callSite.state
 * }</pre>
 *
 * The reservation closes the race in which publication starts while invalidation is running:
 *
 * <pre>{@code
 * target updater                         code publisher
 * --------------                         --------------
 * lock assumptionMonitor
 * updateInProgress = true
 * take dependents
 * unlock assumptionMonitor
 *                                        lock assumptionMonitor
 *                                        see updateInProgress
 *                                        reject publication
 *                                        unlock assumptionMonitor
 * invalidate old code
 * lock assumptionMonitor
 * target = newTarget
 * updateInProgress = false
 * unlock assumptionMonitor
 * }</pre>
 *
 * The shared monitor is held only for these short metadata operations; invalidating installed code
 * happens outside it. Consequently, an expensive update of CallSite A does not prevent code that
 * depends only on CallSite B from being published. Updates of the same CallSite are serialized by
 * that CallSite's {@link CallSiteDependencyState} monitor.
 *
 * <p>
 * One publication uses the same weak reference for all of its CallSites:
 *
 * <pre>{@code
 * CallSite A -> CallSiteDependencyState -> weak(code X) --+
 *                                                         +-> synchronized weak reference -> invalidate code X once
 * CallSite B -> CallSiteDependencyState -> weak(code X) --+
 * }</pre>
 *
 * This shared reference serializes concurrent invalidation when two CallSites select the same
 * installed code. The first update invalidates the code; the second then observes that it is
 * already invalid. Weak references also ensure that dependency tracking does not keep installed
 * code alive.
 *
 * <p>
 * If publication fails, its attachments remain until the code is invalidated outside the
 * assumption monitor, then are removed under that monitor. If target-update invalidation fails,
 * the detached list is restored intact and the reservation is cleared. Code already invalidated
 * remains invalid and is skipped by later validity filtering.
 */
public final class RuntimeCallSiteTargetSupport {
    /** Set during VM startup only when Ristretto can publish CallSite target assumptions. */
    private static volatile boolean targetAssumptionTrackingEnabled;
    /** Prevents configuration from changing after Ristretto startup has selected its mode. */
    private static boolean targetAssumptionTrackingConfigured;

    /**
     * Mutable dependency state owned by one {@link CallSite}. The
     * {@link Target_java_lang_invoke_CallSite#runtimeTargetAssumptionState} field injected into
     * {@code java.lang.invoke.CallSite} strongly owns this state, so no global map retains the
     * CallSite. The state's weak dependent references do not keep installed code alive.
     */
    static final class CallSiteDependencyState {
        private ArrayList<WeakReference<SubstrateInstalledCode>> dependents = new ArrayList<>();
        /** Attachment attempts credited since the last completed prune of this CallSite. */
        private int pruneAttachmentCredit;
        private boolean targetUpdateInProgress;
    }

    /** One live dependent and its original weak-reference entry selected for invalidation. */
    private record DependentInvalidation(SubstrateInstalledCode dependent, WeakReference<SubstrateInstalledCode> reference) {
    }

    /**
     * One publication's shared weak reference. Every assumption attachment returns its own rollback
     * operation; the JVMCI assumptions set has already eliminated identical assumptions.
     */
    public static final class Registration {
        /** Also serializes invalidation when different sites select the same installed code. */
        private final WeakReference<SubstrateInstalledCode> reference;

        private Registration(SubstrateInstalledCode dependent) {
            reference = new WeakReference<>(dependent);
        }
    }

    /** Creates one family-specific registration for a final publication transaction. */
    public static Registration createRegistration(SubstrateInstalledCode dependent) {
        return new Registration(Objects.requireNonNull(dependent, "dependent"));
    }

    /**
     * Returns whether {@code site} still has {@code target} and no update is reserved.
     *
     * Must be called with the {@link RuntimeAssumptionSupport} monitor held.
     */
    public static boolean isTargetValidLocked(CallSite site, MethodHandle target) {
        assert RuntimeAssumptionSupport.isAssumptionLockHeldByCurrentThread();
        CallSiteDependencyState state = getState(site);
        return (state == null || !state.targetUpdateInProgress) && site.getTarget() == target;
    }

    /**
     * Attaches the publication's dependent to {@code site}, returning its exact rollback action.
     *
     * Must be called with the {@link RuntimeAssumptionSupport} monitor held.
     */
    public static DependentAttachment attachDependentLocked(CallSite site, Registration registration) {
        assert RuntimeAssumptionSupport.isAssumptionLockHeldByCurrentThread();
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(registration, "registration");
        return attachDependentLocked(site, registration.reference);
    }

    /**
     * Selects the runtime-assumption mode for this isolate during Ristretto startup.
     *
     * A CallSite target assumption is only sound while setters use the invalidating path. The
     * startup hook therefore seals this setting: changing it after code can attach dependencies
     * would let a direct target store bypass those dependencies.
     */
    public static synchronized void setTargetAssumptionTrackingEnabled(boolean enabled) {
        if (targetAssumptionTrackingConfigured) {
            if (targetAssumptionTrackingEnabled != enabled) {
                throw new IllegalStateException("CallSite target assumption tracking is fixed after startup");
            }
            return;
        }
        targetAssumptionTrackingEnabled = enabled;
        targetAssumptionTrackingConfigured = true;
    }

    /** Returns whether this isolate can publish and invalidate CallSite target assumptions. */
    public static boolean isTargetAssumptionTrackingEnabled() {
        return targetAssumptionTrackingEnabled;
    }

    /**
     * Invalidates target dependents before a normal CallSite target store.
     *
     * When target-assumption tracking is enabled, must be called with the
     * {@link RuntimeAssumptionSupport} monitor not held; acquires it temporarily.
     */
    static void setCallSiteTargetNormal(CallSite site, MethodHandle target) {
        setCallSiteTarget(site, target, false);
    }

    /**
     * Invalidates target dependents before a volatile CallSite target store.
     *
     * When target-assumption tracking is enabled, must be called with the
     * {@link RuntimeAssumptionSupport} monitor not held; acquires it temporarily.
     */
    static void setCallSiteTargetVolatile(CallSite site, MethodHandle target) {
        setCallSiteTarget(site, target, true);
    }

    private static void setCallSiteTarget(CallSite site, MethodHandle target, boolean isVolatile) {
        if (!targetAssumptionTrackingEnabled) {
            putTarget(site, target, isVolatile);
            return;
        }
        if (RuntimeAssumptionSupport.isAssumptionLockHeldByCurrentThread()) {
            throw new IllegalStateException("CallSite target updates are not allowed during final code publication");
        }
        CallSiteDependencyState state = storeIfNoDependencyState(site, target, isVolatile);
        if (state != null) {
            updateTargetWithDependents(site, target, isVolatile, state);
        }
    }

    /**
     * Stores the target when no code has ever depended on this site. A concurrent
     * publication either sees the new target or attaches first and sends this update to the slow
     * path.
     *
     * Must be called with the {@link RuntimeAssumptionSupport} monitor not held. Acquires it temporarily.
     */
    private static CallSiteDependencyState storeIfNoDependencyState(CallSite site, MethodHandle target, boolean isVolatile) {
        return RuntimeAssumptionSupport.withAssumptionLock(() -> {
            CallSiteDependencyState currentState = getState(site);
            if (currentState == null) {
                putTarget(site, target, isVolatile);
            }
            return currentState;
        });
    }

    /**
     * Serializes updates of one site while leaving invalidation outside the shared monitor.
     *
     * Must be called with the {@link RuntimeAssumptionSupport} monitor not held. Acquires it temporarily. Acquires the site state monitor.
     */
    private static void updateTargetWithDependents(CallSite site, MethodHandle target, boolean isVolatile, CallSiteDependencyState state) {
        synchronized (state) {
            ArrayList<WeakReference<SubstrateInstalledCode>> dependentReferences = reserveTargetUpdate(site, target, isVolatile, state);
            if (dependentReferences == null) {
                return;
            }
            ArrayList<DependentInvalidation> invalidations;
            try {
                invalidations = collectInvalidations(dependentReferences);
            } catch (RuntimeException | Error failure) {
                RuntimeAssumptionSupport.withAssumptionLock(() -> {
                    assert state.dependents.isEmpty();
                    state.dependents = dependentReferences;
                    state.targetUpdateInProgress = false;
                });
                throw failure;
            }
            invalidateAndPublishTarget(site, target, isVolatile, state, dependentReferences, invalidations);
        }
    }

    /**
     * Reserves a site only when live dependents need invalidation. A volatile setter still performs
     * its store and invalidates dependents when the target identity is unchanged: that write can
     * publish earlier ordinary writes to a reader.
     *
     * Must be called with the {@link RuntimeAssumptionSupport} monitor not held. Acquires it temporarily. The caller must hold the site state monitor.
     */
    private static ArrayList<WeakReference<SubstrateInstalledCode>> reserveTargetUpdate(CallSite site, MethodHandle target, boolean isVolatile, CallSiteDependencyState state) {
        try {
            return RuntimeAssumptionSupport.withAssumptionLock(() -> {
                if (!isVolatile && site.getTarget() == target) {
                    return null;
                }
                if (state.dependents.isEmpty()) {
                    /* Keep the state object so concurrent updaters retain one monitor. */
                    putTarget(site, target, isVolatile);
                    return null;
                }
                state.targetUpdateInProgress = true;
                return takeDependentReferencesLocked(state);
            });
        } catch (RuntimeException | Error failure) {
            RuntimeAssumptionSupport.withAssumptionLock(() -> state.targetUpdateInProgress = false);
            throw failure;
        }
    }

    /**
     * Invalidates every selected dependent, then stores the target; restores the detached list on failure.
     *
     * Must be called with the {@link RuntimeAssumptionSupport} monitor not held. Acquires it temporarily. The caller must hold the site state monitor.
     */
    private static void invalidateAndPublishTarget(CallSite site, MethodHandle target, boolean isVolatile, CallSiteDependencyState state,
                    ArrayList<WeakReference<SubstrateInstalledCode>> dependentReferences, List<DependentInvalidation> invalidations) {
        boolean completed = false;
        try {
            for (int i = 0; i < invalidations.size(); i++) {
                invalidateOutsideLock(invalidations.get(i));
            }
            RuntimeAssumptionSupport.withAssumptionLock(() -> {
                putTarget(site, target, isVolatile);
                state.targetUpdateInProgress = false;
            });
            completed = true;
        } finally {
            if (!completed) {
                RuntimeAssumptionSupport.withAssumptionLock(() -> {
                    assert state.dependents.isEmpty();
                    /* Restore without scanning or shifting entries under the shared monitor. */
                    state.dependents = dependentReferences;
                    state.targetUpdateInProgress = false;
                });
            }
        }
    }

    /** Stores a CallSite target with the memory semantics required by its concrete CallSite kind. */
    private static void putTarget(CallSite site, MethodHandle target, boolean isVolatile) {
        long targetOffset = Target_java_lang_invoke_CallSite.getTargetOffset();
        if (isVolatile) {
            Unsafe.getUnsafe().putReferenceVolatile(site, targetOffset, target);
        } else {
            Unsafe.getUnsafe().putReference(site, targetOffset, target);
        }
    }

    /**
     * Attaches the publication's shared weak reference to one CallSite and returns the exact
     * rollback operation for that attachment.
     *
     * Must be called with the {@link RuntimeAssumptionSupport} monitor held.
     */
    private static DependentAttachment attachDependentLocked(CallSite callSite, WeakReference<SubstrateInstalledCode> reference) {
        assert RuntimeAssumptionSupport.isAssumptionLockHeldByCurrentThread();
        CallSiteDependencyState state = getOrCreateState(callSite);
        pruneInvalidDependentsAmortized(state);
        state.dependents.add(reference);
        return () -> detachDependentLocked(state, reference);
    }

    /**
     * Removes exactly one reference created by a rejected publication transaction.
     *
     * Must be called with the {@link RuntimeAssumptionSupport} monitor held.
     */
    private static void detachDependentLocked(CallSiteDependencyState state, WeakReference<SubstrateInstalledCode> reference) {
        assert RuntimeAssumptionSupport.isAssumptionLockHeldByCurrentThread();
        state.dependents.remove(reference);
    }

    /**
     * Transfers all dependent references to the updater.
     * The updater scans the detached list outside that monitor, so high-fanout CallSites do not
     * block unrelated assumption families during validity checks and identity deduplication.
     *
     * Must be called with the {@link RuntimeAssumptionSupport} monitor held.
     */
    private static ArrayList<WeakReference<SubstrateInstalledCode>> takeDependentReferencesLocked(CallSiteDependencyState state) {
        assert RuntimeAssumptionSupport.isAssumptionLockHeldByCurrentThread();
        ArrayList<WeakReference<SubstrateInstalledCode>> references = state.dependents;
        state.dependents = new ArrayList<>();
        return references;
    }

    /**
     * Returns the live, identity-distinct dependents selected by one target update.
     *
     * Must be called with the {@link RuntimeAssumptionSupport} monitor not held.
     */
    private static ArrayList<DependentInvalidation> collectInvalidations(List<WeakReference<SubstrateInstalledCode>> references) {
        assert !RuntimeAssumptionSupport.isAssumptionLockHeldByCurrentThread();
        ArrayList<DependentInvalidation> result = new ArrayList<>();
        IdentityHashMap<SubstrateInstalledCode, Boolean> seen = new IdentityHashMap<>();
        for (WeakReference<SubstrateInstalledCode> reference : references) {
            SubstrateInstalledCode dependent = reference.get();
            if (dependent != null && dependent.isValid() && seen.put(dependent, Boolean.TRUE) == null) {
                result.add(new DependentInvalidation(dependent, reference));
            }
        }
        return result;
    }

    /**
     * Returns the injected state of {@code callSite}, allocating it if unused.
     *
     * Must be called with the {@link RuntimeAssumptionSupport} monitor held.
     */
    private static CallSiteDependencyState getOrCreateState(CallSite callSite) {
        assert RuntimeAssumptionSupport.isAssumptionLockHeldByCurrentThread();
        CallSiteDependencyState state = getState(callSite);
        if (state == null) {
            state = new CallSiteDependencyState();
            setState(callSite, state);
        }
        return state;
    }

    /**
     * Returns the dependency state injected into {@code callSite}, or {@code null} if unused.
     *
     * Must be called with the {@link RuntimeAssumptionSupport} monitor held.
     */
    private static CallSiteDependencyState getState(CallSite callSite) {
        return SubstrateUtil.cast(callSite, Target_java_lang_invoke_CallSite.class).runtimeTargetAssumptionState;
    }

    /**
     * Publishes the injected dependency state.
     *
     * Must be called with the {@link RuntimeAssumptionSupport} monitor held.
     */
    private static void setState(CallSite callSite, CallSiteDependencyState state) {
        assert RuntimeAssumptionSupport.isAssumptionLockHeldByCurrentThread();
        SubstrateUtil.cast(callSite, Target_java_lang_invoke_CallSite.class).runtimeTargetAssumptionState = state;
    }

    /**
     * Prunes invalid entries after at least half the current list size in attachment attempts. This
     * bounds pruning-predicate visits amortized over attempts; an individual scan may still hold the
     * shared monitor for the full list traversal.
     *
     * This intentionally copies the three amortized schedules in {@code RuntimeClassHierarchyState}:
     * each bucket has its own credit, an empty bucket resets it, and each nonempty attempt increments
     * it once. Prune at {@code ceil(currentSize / 2)} attempts and reset the credit after pruning.
     * Keep all four copies consistent; the runtime-assumption tests share a schedule oracle.
     *
     * Must be called with the {@link RuntimeAssumptionSupport} monitor held.
     */
    private static void pruneInvalidDependentsAmortized(CallSiteDependencyState state) {
        int size = state.dependents.size();
        if (size == 0) {
            state.pruneAttachmentCredit = 0;
            return;
        }
        state.pruneAttachmentCredit++;
        if (state.pruneAttachmentCredit < size - size / 2) {
            return;
        }
        state.dependents.removeIf(ref -> {
            SubstrateInstalledCode dependent = ref.get();
            if (dependent == null || !dependent.isValid()) {
                return true;
            }
            return false;
        });
        state.pruneAttachmentCredit = 0;
    }

    /**
     * Invalidates one selected dependent outside the shared metadata monitor. Synchronizing on the
     * publication's shared weak reference ensures concurrent CallSite updates invalidate that code
     * at most once.
     *
     * Must be called with the {@link RuntimeAssumptionSupport} monitor not held.
     */
    private static void invalidateOutsideLock(DependentInvalidation invalidation) {
        assert !RuntimeAssumptionSupport.isAssumptionLockHeldByCurrentThread();
        synchronized (invalidation.reference) {
            if (invalidation.dependent.isValid()) {
                invalidation.dependent.invalidate();
            }
        }
    }
}
