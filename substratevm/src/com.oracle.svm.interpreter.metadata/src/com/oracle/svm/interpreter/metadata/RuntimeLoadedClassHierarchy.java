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

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.Assumptions.ConcreteMethod;
import jdk.vm.ci.meta.Assumptions.ConcreteSubtype;
import jdk.vm.ci.meta.Assumptions.LeafType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Tracks the runtime-loaded class hierarchy used by Crema and invalidates Ristretto code when a
 * recorded JVMCI hierarchy assumption becomes false.
 *
 * The compiler records ordinary {@link Assumption} objects in its graph and compilation result.
 * Before installation, Ristretto converts their type and method references to
 * {@link InterpreterResolvedObjectType} and {@link InterpreterResolvedJavaMethod} metadata and
 * passes them to {@link Assumptions}. Assumptions about image-resident types are
 * registered too. For example, a concrete-subtype assumption can depend on the sole concrete
 * subtype present in the image and must be invalidated when a second concrete subtype is loaded at
 * run time.
 *
 * Each assumption context owns its mutable state. Participant types, methods, and installed code
 * are weakly referenced so the assumption does not prevent class unloading. Runtime-loaded classes
 * are not currently unloadable; their owner state therefore follows the existing metadata lifetime
 * without introducing an unload API.
 */
public final class RuntimeLoadedClassHierarchy {
    /** Serializes hierarchy updates, assumption checks, dependent registration, and publication. */
    private static final Object LOCK = new Object();

    /** Installed code whose validity depends on runtime hierarchy assumptions. */
    public interface AssumptionDependent {
        /** Returns whether the installed code can still be entered. */
        boolean isValid();

        /** Invalidates the installed code after one of its hierarchy assumptions becomes false. */
        void invalidate();
    }

    /**
     * Runtime hierarchy assumptions derived from one Graal compilation result.
     */
    public static final class Assumptions {
        /** Shared assumption set for compilations without runtime hierarchy assumptions. */
        private static final Assumptions EMPTY = new Assumptions(List.of());

        /** Immutable assumptions whose type and method references use interpreter metadata. */
        private final List<Assumption> assumptions;

        private Assumptions(List<Assumption> assumptions) {
            this.assumptions = assumptions;
        }

        /** Returns whether the compilation has no runtime hierarchy assumptions. */
        public boolean isEmpty() {
            return assumptions.isEmpty();
        }

        /** Returns whether every recorded hierarchy assumption still holds. */
        public boolean isValid() {
            synchronized (LOCK) {
                return allAssumptionsHoldLocked();
            }
        }

        /**
         * Returns whether all assumptions still hold; the caller must hold
         * {@link RuntimeLoadedClassHierarchy#LOCK}.
         */
        private boolean allAssumptionsHoldLocked() {
            assert Thread.holdsLock(LOCK);
            for (Assumption assumption : assumptions) {
                if (!isAssumptionValidLocked(assumption)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Registers valid {@code dependent} and publishes its code while the hierarchy lock is
         * held. Keeping the final validity check, dependent attachment, and publication callback in
         * one transaction prevents a newly registered runtime type from appearing between those
         * operations.
         *
         * The callback must not install code or invalidate dependents: both operations can take
         * additional locks and intentionally remain outside the hierarchy lock.
         *
         * @return {@code false} when an assumption or the publication callback rejects the code
         */
        public boolean registerAndPublishIfValid(AssumptionDependent dependent, BooleanSupplier publish) {
            if (isEmpty()) {
                return dependent.isValid() && publish.getAsBoolean();
            }
            synchronized (LOCK) {
                if (!dependent.isValid() || !allAssumptionsHoldLocked()) {
                    return false;
                }
                for (Assumption assumption : assumptions) {
                    attachDependentLocked(assumption, dependent);
                }
                TestingBackdoor.beforeFinalPublication(dependent);
                return publish.getAsBoolean();
            }
        }
    }

    /**
     * Creates an immutable assumption set from JVMCI hierarchy assumptions whose type and method
     * references use interpreter metadata.
     */
    public static Assumptions createAssumptions(List<Assumption> assumptions) {
        if (assumptions.isEmpty()) {
            return Assumptions.EMPTY;
        }
        for (Assumption assumption : assumptions) {
            validateRuntimeAssumption(assumption);
        }
        return new Assumptions(List.copyOf(assumptions));
    }

    /** Adds the direct hierarchy edges of one canonical image-resident interpreter type. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerImageType(InterpreterResolvedObjectType type) {
        synchronized (LOCK) {
            publishDirectSupertypeEdgesLocked(type);
        }
    }

    /**
     * Publishes a newly defined runtime type after all of its hierarchy metadata is initialized.
     * Each runtime {@code DynamicHub} owns one canonical interpreter type, and that type's
     * superclass, interfaces, methods, and fields do not change after publication. Consequently,
     * hierarchy changes arrive only through later registrations of new subtype objects.
     */
    public static void registerRuntimeType(InterpreterResolvedObjectType type) {
        TestingBackdoor.beforeRuntimeTypeRegistration(type);
        EconomicSet<AssumptionDependent> dependentsToInvalidate = EconomicSet.create(Equivalence.IDENTITY);
        synchronized (LOCK) {
            EconomicSet<InterpreterResolvedObjectType> seenInterfaces = EconomicSet.create(Equivalence.IDENTITY);
            publishDirectSupertypeEdgesLocked(type);
            collectInvalidatedDependentsFromDirectSuperTypesLocked(type, dependentsToInvalidate, seenInterfaces);
        }
        invalidateOutsideLock(dependentsToInvalidate);
    }

    /** Test-only hooks for coordinating runtime hierarchy publication. */
    public static final class TestingBackdoor {
        private static volatile Consumer<AssumptionDependent> beforeFinalPublicationHook;
        private static volatile Consumer<InterpreterResolvedObjectType> runtimeTypeRegistrationAttemptHook;

        /** Installs a hook that runs under the hierarchy lock after validation and attachment. */
        public static void setBeforeFinalPublicationHook(Consumer<AssumptionDependent> hook) {
            beforeFinalPublicationHook = hook;
        }

        /** Installs a hook that runs immediately before a runtime type attempts to acquire the hierarchy lock. */
        public static void setRuntimeTypeRegistrationAttemptHook(Consumer<InterpreterResolvedObjectType> hook) {
            runtimeTypeRegistrationAttemptHook = hook;
        }

        /** Clears all runtime hierarchy test hooks. */
        public static void clearHooks() {
            beforeFinalPublicationHook = null;
            runtimeTypeRegistrationAttemptHook = null;
        }

        /** Returns whether the calling thread holds the hierarchy lock. */
        public static boolean isHierarchyLockHeldByCurrentThread() {
            return Thread.holdsLock(LOCK);
        }

        private static void beforeFinalPublication(AssumptionDependent dependent) {
            assert Thread.holdsLock(LOCK);
            Consumer<AssumptionDependent> hook = beforeFinalPublicationHook;
            if (hook != null) {
                hook.accept(dependent);
            }
        }

        private static void beforeRuntimeTypeRegistration(InterpreterResolvedObjectType type) {
            assert !Thread.holdsLock(LOCK);
            Consumer<InterpreterResolvedObjectType> hook = runtimeTypeRegistrationAttemptHook;
            if (hook != null) {
                hook.accept(type);
            }
        }
    }

    /** Adds the direct superclass and superinterface edges of {@code type}. */
    private static void publishDirectSupertypeEdgesLocked(InterpreterResolvedObjectType type) {
        assert Thread.holdsLock(LOCK);
        InterpreterResolvedObjectType superclass = type.getSuperclass();
        if (superclass != null) {
            publishDirectSubtypeLocked(superclass, type);
        }
        for (InterpreterResolvedObjectType interfaceType : type.getInterfaces()) {
            publishDirectSubtypeLocked(interfaceType, type);
        }
    }

    /**
     * Walks the transitive supertypes of {@code type} once to collect invalidated dependents. The
     * traversal deliberately does not materialize a supertype list.
     */
    private static void collectInvalidatedDependentsFromDirectSuperTypesLocked(InterpreterResolvedObjectType type, EconomicSet<AssumptionDependent> dependentsToInvalidate,
                    EconomicSet<InterpreterResolvedObjectType> seenInterfaces) {
        assert Thread.holdsLock(LOCK);
        InterpreterResolvedObjectType superclass = type.getSuperclass();
        if (superclass != null) {
            collectInvalidatedDependentsFromClassSuperTypesLocked(superclass, type, dependentsToInvalidate, seenInterfaces);
        }
        for (InterpreterResolvedObjectType interfaceType : type.getInterfaces()) {
            collectInvalidatedDependentsFromInterfaceSuperTypesLocked(interfaceType, type, dependentsToInvalidate, seenInterfaces);
        }
    }

    /** Adds one direct-subtype edge. */
    private static void publishDirectSubtypeLocked(InterpreterResolvedObjectType context, InterpreterResolvedObjectType subtype) {
        assert Thread.holdsLock(LOCK);
        assert isDirectSupertype(context, subtype);
        context.getOrCreateRuntimeClassHierarchyState().addRuntimeSubtype(subtype);
    }

    /** Collects dependents from a superclass chain and its superinterfaces. */
    private static void collectInvalidatedDependentsFromClassSuperTypesLocked(InterpreterResolvedObjectType context, InterpreterResolvedObjectType subtype,
                    EconomicSet<AssumptionDependent> dependentsToInvalidate, EconomicSet<InterpreterResolvedObjectType> seenInterfaces) {
        assert Thread.holdsLock(LOCK);
        for (InterpreterResolvedObjectType current = context; current != null; current = current.getSuperclass()) {
            collectInvalidatedDependentsLocked(current, subtype, dependentsToInvalidate);
            for (InterpreterResolvedObjectType interfaceType : current.getInterfaces()) {
                collectInvalidatedDependentsFromInterfaceSuperTypesLocked(interfaceType, subtype, dependentsToInvalidate, seenInterfaces);
            }
        }
    }

    /** Collects dependents from an interface and its transitive superinterfaces once. */
    private static void collectInvalidatedDependentsFromInterfaceSuperTypesLocked(InterpreterResolvedObjectType context, InterpreterResolvedObjectType subtype,
                    EconomicSet<AssumptionDependent> dependentsToInvalidate, EconomicSet<InterpreterResolvedObjectType> seenInterfaces) {
        assert Thread.holdsLock(LOCK);
        if (!seenInterfaces.add(context)) {
            return;
        }
        collectInvalidatedDependentsLocked(context, subtype, dependentsToInvalidate);
        for (InterpreterResolvedObjectType interfaceType : context.getInterfaces()) {
            collectInvalidatedDependentsFromInterfaceSuperTypesLocked(interfaceType, subtype, dependentsToInvalidate, seenInterfaces);
        }
    }

    /** Collects dependents owned by one affected hierarchy context. */
    private static void collectInvalidatedDependentsLocked(InterpreterResolvedObjectType context, InterpreterResolvedObjectType subtype,
                    EconomicSet<AssumptionDependent> dependentsToInvalidate) {
        assert Thread.holdsLock(LOCK);
        RuntimeClassHierarchyState state = context.getRuntimeClassHierarchyState();
        if (state != null) {
            state.collectInvalidatedDependents(subtype, dependentsToInvalidate);
        }
    }

    private static boolean isDirectSupertype(InterpreterResolvedObjectType context, InterpreterResolvedObjectType subtype) {
        if (context.equals(subtype.getSuperclass())) {
            return true;
        }
        for (InterpreterResolvedObjectType interfaceType : subtype.getInterfaces()) {
            if (context.equals(interfaceType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the only concrete runtime-loaded subtype of {@code context} and records the hierarchy
     * assumption that makes the result valid.
     */
    static AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype(InterpreterResolvedObjectType context) {
        synchronized (LOCK) {
            if (context.isConcrete() && hasNoKnownSubtypesLocked(context)) {
                return new AssumptionResult<>(context, new LeafType(context));
            }
            if (context.isAbstract()) {
                InterpreterResolvedObjectType uniqueConcreteSubtype = uniqueConcreteSubtypeLocked(context);
                if (uniqueConcreteSubtype != null) {
                    return new AssumptionResult<>(uniqueConcreteSubtype, new ConcreteSubtype(context, uniqueConcreteSubtype));
                }
            }
            return null;
        }
    }

    /** Returns the only concrete runtime-loaded implementor of {@code context}, or {@code null}. */
    static ResolvedJavaType getSingleImplementor(InterpreterResolvedObjectType context) {
        synchronized (LOCK) {
            if (!context.isInterface()) {
                throw new JVMCIError("Cannot call getSingleImplementor() on a non-interface type: %s", context);
            }
            InterpreterResolvedObjectType uniqueImplementor = uniqueConcreteSubtypeLocked(context);
            if (uniqueImplementor == null) {
                return null;
            }
            return uniqueImplementor;
        }
    }

    /**
     * Finds the unique concrete implementation of {@code method} below {@code context} and records
     * the hierarchy assumption that makes the result valid.
     */
    static AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(InterpreterResolvedObjectType context, InterpreterResolvedJavaMethod method) {
        synchronized (LOCK) {
            InterpreterResolvedJavaMethod uniqueMethod = uniqueConcreteMethodLocked(context, method);
            if (uniqueMethod == null) {
                return null;
            }
            return new AssumptionResult<>(uniqueMethod, new ConcreteMethod(method, context, uniqueMethod));
        }
    }

    /** Returns whether {@code context} has no published subtypes; the caller must hold {@link #LOCK}. */
    private static boolean hasNoKnownSubtypesLocked(InterpreterResolvedObjectType context) {
        assert Thread.holdsLock(LOCK);
        RuntimeClassHierarchyState state = context.getRuntimeClassHierarchyState();
        return state == null || state.hasNoRuntimeSubtypes();
    }

    /**
     * Returns the only concrete published subtype of {@code context}, or {@code null}; the caller
     * must hold {@link #LOCK}.
     */
    private static InterpreterResolvedObjectType uniqueConcreteSubtypeLocked(InterpreterResolvedObjectType context) {
        assert Thread.holdsLock(LOCK);
        InterpreterResolvedObjectType found = RuntimeClassHierarchyState.findFirstConcreteRuntimeDescendant(context);
        if (found == null || RuntimeClassHierarchyState.hasConcreteRuntimeDescendantOtherThan(context, found)) {
            return null;
        }
        return found;
    }

    /**
     * Returns the unique concrete implementation of {@code method} below {@code context}, or
     * {@code null}; the caller must hold {@link #LOCK}.
     */
    private static InterpreterResolvedJavaMethod uniqueConcreteMethodLocked(InterpreterResolvedObjectType context, InterpreterResolvedJavaMethod method) {
        assert Thread.holdsLock(LOCK);
        InterpreterResolvedJavaMethod found = null;
        if (context.isConcrete()) {
            ResolvedJavaMethod resolved = context.resolveConcreteMethod(method, null);
            if (resolved instanceof InterpreterResolvedJavaMethod resolvedInterpreterMethod) {
                found = resolvedInterpreterMethod;
            }
        }
        if (found == null) {
            InterpreterResolvedObjectType firstConcreteDescendant = RuntimeClassHierarchyState.findFirstConcreteRuntimeDescendant(context);
            if (firstConcreteDescendant == null) {
                return null;
            }
            ResolvedJavaMethod resolved = firstConcreteDescendant.resolveConcreteMethod(method, null);
            if (!(resolved instanceof InterpreterResolvedJavaMethod resolvedInterpreterMethod)) {
                return null;
            }
            found = resolvedInterpreterMethod;
        }
        if (!RuntimeClassHierarchyState.runtimeDescendantsResolveTo(context, method, found)) {
            return null;
        }
        return found;
    }

    /** Attaches one validated assumption to its hierarchy owner's typed bucket. */
    private static void attachDependentLocked(Assumption assumption, AssumptionDependent dependent) {
        assert Thread.holdsLock(LOCK);
        InterpreterResolvedObjectType context = assumptionContext(assumption);
        RuntimeClassHierarchyState state = context.getOrCreateRuntimeClassHierarchyState();
        if (assumption instanceof LeafType) {
            state.addLeafDependent(dependent);
        } else if (assumption instanceof ConcreteSubtype) {
            state.addConcreteSubtypeDependent(dependent);
        } else if (assumption instanceof ConcreteMethod concreteMethod) {
            state.addConcreteMethodDependent(context, asInterpreterMethod(concreteMethod.method), asInterpreterMethod(concreteMethod.impl), dependent);
        } else {
            throw new IllegalArgumentException("Unsupported runtime hierarchy assumption: " + assumption);
        }
    }

    /** Invalidates installed code after releasing the hierarchy lock. */
    private static void invalidateOutsideLock(EconomicSet<AssumptionDependent> dependentsToInvalidate) {
        for (AssumptionDependent dependent : dependentsToInvalidate) {
            dependent.invalidate();
        }
    }

    /** Verifies that an assumption contains only supported interpreter metadata. */
    private static void validateRuntimeAssumption(Assumption assumption) {
        assumptionContext(assumption);
        if (assumption instanceof ConcreteSubtype concreteSubtype) {
            asObjectType(concreteSubtype.subtype);
        } else if (assumption instanceof ConcreteMethod concreteMethod) {
            asInterpreterMethod(concreteMethod.method);
            asInterpreterMethod(concreteMethod.impl);
        } else if (!(assumption instanceof LeafType)) {
            throw new IllegalArgumentException("Unsupported runtime hierarchy assumption: " + assumption);
        }
    }

    /** Returns the assumption context whose subtype changes can invalidate {@code assumption}. */
    private static InterpreterResolvedObjectType assumptionContext(Assumption assumption) {
        if (assumption instanceof LeafType leafType) {
            return asObjectType(leafType.context);
        } else if (assumption instanceof ConcreteSubtype concreteSubtype) {
            return asObjectType(concreteSubtype.context);
        } else if (assumption instanceof ConcreteMethod concreteMethod) {
            return asObjectType(concreteMethod.context);
        }
        throw new IllegalArgumentException("Unsupported runtime hierarchy assumption: " + assumption);
    }

    /** Re-evaluates one JVMCI assumption against the current runtime-loaded hierarchy. */
    private static boolean isAssumptionValidLocked(Assumption assumption) {
        assert Thread.holdsLock(LOCK);
        if (assumption instanceof LeafType leafType) {
            InterpreterResolvedObjectType context = asObjectType(leafType.context);
            return hasNoKnownSubtypesLocked(context);
        } else if (assumption instanceof ConcreteSubtype concreteSubtype) {
            InterpreterResolvedObjectType context = asObjectType(concreteSubtype.context);
            InterpreterResolvedObjectType subtype = asObjectType(concreteSubtype.subtype);
            return subtype.equals(uniqueConcreteSubtypeLocked(context));
        } else if (assumption instanceof ConcreteMethod concreteMethod) {
            InterpreterResolvedObjectType context = asObjectType(concreteMethod.context);
            InterpreterResolvedJavaMethod method = asInterpreterMethod(concreteMethod.method);
            return concreteMethod.impl.equals(uniqueConcreteMethodLocked(context, method));
        }
        throw new IllegalArgumentException("Unsupported runtime hierarchy assumption: " + assumption);
    }

    private static InterpreterResolvedObjectType asObjectType(ResolvedJavaType type) {
        if (type instanceof InterpreterResolvedObjectType objectType) {
            return objectType;
        }
        throw new IllegalArgumentException("Runtime hierarchy assumption must use interpreter type metadata: " + type);
    }

    private static InterpreterResolvedJavaMethod asInterpreterMethod(ResolvedJavaMethod method) {
        if (method instanceof InterpreterResolvedJavaMethod interpreterMethod) {
            return interpreterMethod;
        }
        throw new IllegalArgumentException("Runtime hierarchy assumption must use interpreter method metadata: " + method);
    }
}
