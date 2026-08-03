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

import com.oracle.svm.interpreter.metadata.RuntimeLoadedClassHierarchy.AssumptionDependent;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Mutable runtime hierarchy state owned by one {@link InterpreterResolvedObjectType}. All access
 * is serialized by the runtime hierarchy lock. Descendants are weak so a longer-lived superclass
 * or interface does not keep a child-loader class alive.
 */
final class RuntimeClassHierarchyState {
    /** Weak references to published direct runtime subtypes of this owner. */
    private final ArrayList<WeakReference<InterpreterResolvedObjectType>> runtimeSubtypes = new ArrayList<>();
    /** Code that assumes this owner has no runtime descendants. */
    private ArrayList<WeakReference<AssumptionDependent>> leafDependents;
    /** Code that assumes this owner has no additional concrete runtime descendant. */
    private ArrayList<WeakReference<AssumptionDependent>> concreteSubtypeDependents;
    /** Code whose concrete-method assumptions are rooted at this owner. */
    private ArrayList<ConcreteMethodDependent> concreteMethodDependents;

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
    void addLeafDependent(AssumptionDependent dependent) {
        if (leafDependents == null) {
            leafDependents = new ArrayList<>();
        } else {
            pruneInvalidDependents(leafDependents);
        }
        leafDependents.add(new WeakReference<>(dependent));
    }

    /** Records code that assumes this owner has no additional concrete runtime descendant. */
    void addConcreteSubtypeDependent(AssumptionDependent dependent) {
        if (concreteSubtypeDependents == null) {
            concreteSubtypeDependents = new ArrayList<>();
        } else {
            pruneInvalidDependents(concreteSubtypeDependents);
        }
        concreteSubtypeDependents.add(new WeakReference<>(dependent));
    }

    /** Records code that assumes all concrete descendants resolve {@code method} to {@code impl}. */
    void addConcreteMethodDependent(InterpreterResolvedObjectType context, InterpreterResolvedJavaMethod method, InterpreterResolvedJavaMethod impl, AssumptionDependent dependent) {
        assert method.getDeclaringClass().isAssignableFrom(context);
        if (concreteMethodDependents == null) {
            concreteMethodDependents = new ArrayList<>();
        } else {
            pruneConcreteMethodDependents();
        }
        concreteMethodDependents.add(new ConcreteMethodDependent(method, impl, dependent));
    }

    /** Prunes cleared or invalid dependents before appending another entry. */
    private static void pruneInvalidDependents(ArrayList<WeakReference<AssumptionDependent>> dependents) {
        dependents.removeIf(reference -> {
            AssumptionDependent dependent = reference.get();
            return dependent == null || !dependent.isValid();
        });
    }

    /** Collects live code invalidated by publication of {@code descendant}. */
    void collectInvalidatedDependents(InterpreterResolvedObjectType descendant, EconomicSet<AssumptionDependent> dependentsToInvalidate) {
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
    private static void collectUnconditionalDependents(ArrayList<WeakReference<AssumptionDependent>> dependents, EconomicSet<AssumptionDependent> dependentsToInvalidate) {
        if (dependents == null) {
            return;
        }
        for (WeakReference<AssumptionDependent> reference : dependents) {
            AssumptionDependent dependent = reference.get();
            if (dependent != null && dependent.isValid()) {
                dependentsToInvalidate.add(dependent);
            }
        }
        dependents.clear();
    }

    /** Invalidates concrete-method dependents whose expected implementation changed. */
    private void collectConcreteMethodDependents(InterpreterResolvedObjectType descendant, EconomicSet<AssumptionDependent> dependentsToInvalidate) {
        if (concreteMethodDependents == null) {
            return;
        }
        Iterator<ConcreteMethodDependent> iterator = concreteMethodDependents.iterator();
        while (iterator.hasNext()) {
            ConcreteMethodDependent entry = iterator.next();
            AssumptionDependent dependent = entry.dependent.get();
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
            AssumptionDependent dependent = entry.dependent.get();
            return dependent == null || !dependent.isValid();
        });
    }

    /** Weak participant payload for one concrete-method use case. */
    private static final class ConcreteMethodDependent {
        /** Method declared by this state's hierarchy context or a supertype. */
        private final InterpreterResolvedJavaMethod method;
        /** Weak implementation that can be declared by a runtime-loaded descendant. */
        private final WeakReference<InterpreterResolvedJavaMethod> impl;
        /** Installed code invalidated when the expected implementation changes. */
        private final WeakReference<AssumptionDependent> dependent;

        private ConcreteMethodDependent(InterpreterResolvedJavaMethod method, InterpreterResolvedJavaMethod impl, AssumptionDependent dependent) {
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
