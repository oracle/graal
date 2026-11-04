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
package com.oracle.svm.interpreter.metadata;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;

import com.oracle.svm.core.deopt.RuntimeAssumptionSupport.DependentAttachment;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Mutable runtime hierarchy state owned by one {@link InterpreterResolvedObjectType}. All access
 * is serialized by the shared runtime-assumption monitor. Descendants are weak so a longer-lived superclass
 * or interface does not keep a child-loader class alive.
 */
final class RuntimeClassHierarchyState {
    /** Weak references to published direct runtime subtypes of this owner. */
    private final ArrayList<WeakReference<InterpreterResolvedObjectType>> runtimeSubtypes = new ArrayList<>();
    /** Code that assumes this owner has no runtime descendants. */
    private ArrayList<WeakReference<SubstrateInstalledCode>> leafDependents;
    /** Attachment attempts credited since the last completed leaf-dependent prune. */
    private int leafPruneAttachmentCredit;
    /** Code that assumes this owner has no additional concrete runtime descendant. */
    private ArrayList<WeakReference<SubstrateInstalledCode>> concreteSubtypeDependents;
    /** Attachment attempts credited since the last completed concrete-subtype prune. */
    private int concreteSubtypePruneAttachmentCredit;
    /** Code whose concrete-method assumptions are rooted at this owner. */
    private ArrayList<ConcreteMethodDependent> concreteMethodDependents;
    /** Attachment attempts credited since the last completed concrete-method prune. */
    private int concreteMethodPruneAttachmentCredit;

    /** Adds one direct runtime subtype. Each runtime type is published exactly once. */
    void addRuntimeSubtype(InterpreterResolvedObjectType subtype) {
        runtimeSubtypes.add(new WeakReference<>(subtype));
    }

    /** Returns whether this owner has no live direct runtime subtypes. */
    boolean hasNoRuntimeSubtypes() {
        Iterator<WeakReference<InterpreterResolvedObjectType>> iterator = runtimeSubtypes.iterator();
        while (iterator.hasNext()) {
            InterpreterResolvedObjectType subtype = iterator.next().get();
            if (subtype == null) {
                iterator.remove();
            } else {
                return false;
            }
        }
        return true;
    }

    /** Returns the first concrete runtime descendant, or {@code null}. */
    static InterpreterResolvedObjectType findFirstConcreteRuntimeDescendant(InterpreterResolvedObjectType context) {
        return findFirstConcreteRuntimeDescendant(context, newSeenSet(context));
    }

    private static InterpreterResolvedObjectType findFirstConcreteRuntimeDescendant(InterpreterResolvedObjectType context,
                    EconomicSet<InterpreterResolvedObjectType> seen) {
        RuntimeClassHierarchyState state = context.getRuntimeClassHierarchyState();
        if (state == null) {
            return null;
        }
        Iterator<WeakReference<InterpreterResolvedObjectType>> iterator = state.runtimeSubtypes.iterator();
        while (iterator.hasNext()) {
            InterpreterResolvedObjectType subtype = iterator.next().get();
            if (subtype == null) {
                iterator.remove();
            } else if (seen == null || seen.add(subtype)) {
                if (subtype.isConcrete()) {
                    return subtype;
                }
                InterpreterResolvedObjectType found = findFirstConcreteRuntimeDescendant(subtype, seen);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Returns whether a concrete runtime descendant other than {@code expected} exists. */
    static boolean hasConcreteRuntimeDescendantOtherThan(InterpreterResolvedObjectType context, InterpreterResolvedObjectType expected) {
        return hasConcreteRuntimeDescendantOtherThan(context, expected, newSeenSet(context));
    }

    private static boolean hasConcreteRuntimeDescendantOtherThan(InterpreterResolvedObjectType context, InterpreterResolvedObjectType expected,
                    EconomicSet<InterpreterResolvedObjectType> seen) {
        RuntimeClassHierarchyState state = context.getRuntimeClassHierarchyState();
        if (state == null) {
            return false;
        }
        Iterator<WeakReference<InterpreterResolvedObjectType>> iterator = state.runtimeSubtypes.iterator();
        while (iterator.hasNext()) {
            InterpreterResolvedObjectType subtype = iterator.next().get();
            if (subtype == null) {
                iterator.remove();
            } else if (seen == null || seen.add(subtype)) {
                if (subtype.isConcrete() && !subtype.equals(expected)) {
                    return true;
                }
                if (hasConcreteRuntimeDescendantOtherThan(subtype, expected, seen)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Returns whether every concrete runtime descendant resolves {@code method} to {@code impl}. */
    static boolean runtimeDescendantsResolveTo(InterpreterResolvedObjectType context, InterpreterResolvedJavaMethod method, ResolvedJavaMethod impl) {
        return runtimeDescendantsResolveTo(context, method, impl, newSeenSet(context));
    }

    private static boolean runtimeDescendantsResolveTo(InterpreterResolvedObjectType context, InterpreterResolvedJavaMethod method, ResolvedJavaMethod impl,
                    EconomicSet<InterpreterResolvedObjectType> seen) {
        RuntimeClassHierarchyState state = context.getRuntimeClassHierarchyState();
        if (state == null) {
            return true;
        }
        Iterator<WeakReference<InterpreterResolvedObjectType>> iterator = state.runtimeSubtypes.iterator();
        while (iterator.hasNext()) {
            InterpreterResolvedObjectType subtype = iterator.next().get();
            if (subtype == null) {
                iterator.remove();
            } else if (seen == null || seen.add(subtype)) {
                if (subtype.isConcrete()) {
                    ResolvedJavaMethod resolved = subtype.resolveConcreteMethod(method, null);
                    if (!(resolved instanceof InterpreterResolvedJavaMethod) || !impl.equals(resolved)) {
                        return false;
                    }
                }
                if (!runtimeDescendantsResolveTo(subtype, method, impl, seen)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Interface descendant graphs can contain diamonds; class descendant graphs are trees. */
    private static EconomicSet<InterpreterResolvedObjectType> newSeenSet(InterpreterResolvedObjectType context) {
        return context.isInterface() ? EconomicSet.create(Equivalence.IDENTITY) : null;
    }

    /** Records code that assumes this owner has no runtime descendants. */
    DependentAttachment addLeafDependent(SubstrateInstalledCode dependent) {
        if (leafDependents == null) {
            leafDependents = new ArrayList<>();
        }
        pruneLeafDependentsAmortized();
        WeakReference<SubstrateInstalledCode> attachment = new WeakReference<>(dependent);
        leafDependents.add(attachment);
        return () -> leafDependents.remove(attachment);
    }

    /** Records code that assumes this owner has no additional concrete runtime descendant. */
    DependentAttachment addConcreteSubtypeDependent(SubstrateInstalledCode dependent) {
        if (concreteSubtypeDependents == null) {
            concreteSubtypeDependents = new ArrayList<>();
        }
        pruneConcreteSubtypeDependentsAmortized();
        WeakReference<SubstrateInstalledCode> attachment = new WeakReference<>(dependent);
        concreteSubtypeDependents.add(attachment);
        return () -> concreteSubtypeDependents.remove(attachment);
    }

    /** Records code that assumes all concrete descendants resolve {@code method} to {@code impl}. */
    DependentAttachment addConcreteMethodDependent(InterpreterResolvedObjectType context, InterpreterResolvedJavaMethod method, InterpreterResolvedJavaMethod impl,
                    SubstrateInstalledCode dependent) {
        assert method.getDeclaringClass().isAssignableFrom(context);
        if (concreteMethodDependents == null) {
            concreteMethodDependents = new ArrayList<>();
        }
        pruneConcreteMethodDependentsAmortized();
        ConcreteMethodDependent attachment = new ConcreteMethodDependent(method, impl, dependent);
        concreteMethodDependents.add(attachment);
        return () -> concreteMethodDependents.remove(attachment);
    }

    /**
     * Credits one leaf-bucket attempt before append and prunes after half its current size.
     *
     * Intentionally copies the other two amortized schedules here and the CallSite schedule in
     * {@code RuntimeCallSiteTargetSupport}. Each bucket has its own credit; an empty bucket resets
     * it, a nonempty attempt increments it once, and pruning at {@code ceil(currentSize / 2)} resets
     * it again. Keep all four copies consistent; the runtime-assumption tests share a schedule oracle.
     */
    private void pruneLeafDependentsAmortized() {
        int size = leafDependents.size();
        if (size == 0) {
            leafPruneAttachmentCredit = 0;
            return;
        }
        int threshold = size - size / 2;
        leafPruneAttachmentCredit++;
        if (leafPruneAttachmentCredit < threshold) {
            return;
        }
        pruneInvalidDependents(leafDependents);
        leafPruneAttachmentCredit = 0;
    }

    /**
     * Credits one concrete-subtype attempt before append and prunes after half its current size.
     *
     * Intentionally copies the other two amortized schedules here and the CallSite schedule in
     * {@code RuntimeCallSiteTargetSupport}. Each bucket has its own credit; an empty bucket resets
     * it, a nonempty attempt increments it once, and pruning at {@code ceil(currentSize / 2)} resets
     * it again. Keep all four copies consistent; the runtime-assumption tests share a schedule oracle.
     */
    private void pruneConcreteSubtypeDependentsAmortized() {
        int size = concreteSubtypeDependents.size();
        if (size == 0) {
            concreteSubtypePruneAttachmentCredit = 0;
            return;
        }
        int threshold = size - size / 2;
        concreteSubtypePruneAttachmentCredit++;
        if (concreteSubtypePruneAttachmentCredit < threshold) {
            return;
        }
        pruneInvalidDependents(concreteSubtypeDependents);
        concreteSubtypePruneAttachmentCredit = 0;
    }

    /** Applies the unchanged weak-code validity predicate to one hierarchy bucket. */
    private static void pruneInvalidDependents(ArrayList<WeakReference<SubstrateInstalledCode>> dependents) {
        dependents.removeIf(reference -> {
            SubstrateInstalledCode dependent = reference.get();
            return dependent == null || !dependent.isValid();
        });
    }

    /** Collects live code invalidated by publication of {@code descendant}. */
    void collectInvalidatedDependents(InterpreterResolvedObjectType descendant, EconomicSet<SubstrateInstalledCode> dependentsToInvalidate) {
        collectUnconditionalDependents(leafDependents, dependentsToInvalidate);
        if (descendant.isConcrete()) {
            collectUnconditionalDependents(concreteSubtypeDependents, dependentsToInvalidate);
            collectConcreteMethodDependents(descendant, dependentsToInvalidate);
        } else {
            pruneConcreteMethodDependents();
        }
    }

    /**
     * Adds every live, valid entry in {@code dependents} to {@code dependentsToInvalidate}, then
     * clears {@code dependents}.
     */
    private static void collectUnconditionalDependents(ArrayList<WeakReference<SubstrateInstalledCode>> dependents, EconomicSet<SubstrateInstalledCode> dependentsToInvalidate) {
        if (dependents == null) {
            return;
        }
        for (WeakReference<SubstrateInstalledCode> reference : dependents) {
            SubstrateInstalledCode dependent = reference.get();
            if (dependent != null && dependent.isValid()) {
                dependentsToInvalidate.add(dependent);
            }
        }
        dependents.clear();
    }

    /** Invalidates concrete-method dependents whose expected implementation changed. */
    private void collectConcreteMethodDependents(InterpreterResolvedObjectType descendant, EconomicSet<SubstrateInstalledCode> dependentsToInvalidate) {
        if (concreteMethodDependents == null) {
            return;
        }
        Iterator<ConcreteMethodDependent> iterator = concreteMethodDependents.iterator();
        while (iterator.hasNext()) {
            ConcreteMethodDependent entry = iterator.next();
            SubstrateInstalledCode dependent = entry.dependent.get();
            if (dependent == null || !dependent.isValid()) {
                iterator.remove();
            } else if (entry.isInvalidatedBy(descendant)) {
                dependentsToInvalidate.add(dependent);
                iterator.remove();
            }
        }
    }

    /** Prunes dead concrete-method dependents when an abstract descendant is published. */
    private void pruneConcreteMethodDependents() {
        if (concreteMethodDependents == null) {
            return;
        }
        concreteMethodDependents.removeIf(entry -> {
            SubstrateInstalledCode dependent = entry.dependent.get();
            return dependent == null || !dependent.isValid();
        });
    }

    /**
     * Credits one concrete-method attempt before append and prunes after half its current size.
     *
     * Intentionally copies the other two amortized schedules here and the CallSite schedule in
     * {@code RuntimeCallSiteTargetSupport}. Each bucket has its own credit; an empty bucket resets
     * it, a nonempty attempt increments it once, and pruning at {@code ceil(currentSize / 2)} resets
     * it again. Keep all four copies consistent; the runtime-assumption tests share a schedule oracle.
     */
    private void pruneConcreteMethodDependentsAmortized() {
        int size = concreteMethodDependents.size();
        if (size == 0) {
            concreteMethodPruneAttachmentCredit = 0;
            return;
        }
        int threshold = size - size / 2;
        concreteMethodPruneAttachmentCredit++;
        if (concreteMethodPruneAttachmentCredit < threshold) {
            return;
        }
        pruneConcreteMethodDependents();
        concreteMethodPruneAttachmentCredit = 0;
    }

    /** Weak participant payload for one concrete-method use case. */
    private static final class ConcreteMethodDependent {
        /** Method declared by this state's hierarchy context or a supertype. */
        private final InterpreterResolvedJavaMethod method;
        /** Weak implementation that can be declared by a runtime-loaded descendant. */
        private final WeakReference<InterpreterResolvedJavaMethod> impl;
        /** Installed code invalidated when the expected implementation changes. */
        private final WeakReference<SubstrateInstalledCode> dependent;

        private ConcreteMethodDependent(InterpreterResolvedJavaMethod method, InterpreterResolvedJavaMethod impl, SubstrateInstalledCode dependent) {
            this.method = method;
            this.impl = new WeakReference<>(impl);
            this.dependent = new WeakReference<>(dependent);
        }

        /** Missing payload is conservatively invalid rather than evidence that the assumption holds. */
        private boolean isInvalidatedBy(InterpreterResolvedObjectType descendant) {
            InterpreterResolvedJavaMethod expectedImpl = impl.get();
            if (expectedImpl == null) {
                return true;
            }
            ResolvedJavaMethod resolved = descendant.resolveConcreteMethod(method, null);
            return !(resolved instanceof InterpreterResolvedJavaMethod resolvedMethod) || !expectedImpl.equals(resolvedMethod);
        }
    }
}
