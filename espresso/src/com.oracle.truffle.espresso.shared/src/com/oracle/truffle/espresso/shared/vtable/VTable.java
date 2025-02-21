/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.shared.vtable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;

import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.shared.meta.FieldAccess;
import com.oracle.truffle.espresso.shared.meta.MethodAccess;
import com.oracle.truffle.espresso.shared.meta.TypeAccess;

/**
 * Helper for creating method tables.
 */
public final class VTable {
    /**
     * Creates method tables associated with the given {@link PartialType targetClass}.
     * <p>
     * The returned object is a {@link Tables} containing the {@link Tables#getVtable() virtual
     * table} and the {@link Tables#getItables() interface tables}. That object also makes known the
     * {@link Tables#getImplicitInterfaceMethods() implicit interface methods} that do not have a
     * concrete implementation in the type hierarchy of {@code targetClass}.
     * <p>
     * All the methods of a {@link Tables} returned by this method are guaranteed to return non-null
     * results, though returned {@link List lists} may be empty if appropriate. All lists in the
     * returned table will never contain {@code null} elements.
     *
     * @param targetClass The type for which method tables should be created
     * @param verbose Whether all declared methods should be unconditionally added to the vtable.
     * @param allowInterfaceResolvingToPrivate Whether the runtime allows selection of interface
     *            invokes to select private methods. Requires implementing
     *            {@link PartialType#lookupOverrideWithPrivate(Symbol, Symbol)}.
     *
     * @param <C> The class providing access to the VM-side java {@link java.lang.Class}.
     * @param <M> The class providing access to the VM-side java {@link java.lang.reflect.Method}.
     * @param <F> The class providing access to the VM-side java {@link java.lang.reflect.Field}.
     */
    public static <C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> Tables<C, M, F> create(
                    PartialType<C, M, F> targetClass,
                    boolean verbose,
                    boolean allowInterfaceResolvingToPrivate) throws MethodTableException {
        return new Builder<>(targetClass, verbose, allowInterfaceResolvingToPrivate).build();
    }

    /**
     * Returns whether a given method may appear in a vtable.
     */
    public static <C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> boolean isVirtualEntry(PartialMethod<C, M, F> m) {
        return !m.isPrivate() && !m.isStatic() && !m.isConstructor() && !m.isClassInitializer();
    }

    private static final class Builder<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> {
        private final boolean verbose;
        private final boolean allowInterfaceResolvingToPrivate;

        private final PartialType<C, M, F> targetClass;
        private final EconomicMap<MethodKey, Locations<C, M, F>> locations = EconomicMap.create();

        private final List<PartialMethod<C, M, F>> vtable = new ArrayList<>();
        private final EconomicMap<C, List<PartialMethod<C, M, F>>> itables = EconomicMap.create(Equivalence.IDENTITY);
        private final List<PartialMethod<C, M, F>> mirandas = new ArrayList<>();
        private final List<Integer> equivalentEntries = new ArrayList<>();

        Builder(PartialType<C, M, F> targetClass, boolean verbose, boolean allowInterfaceResolvingToPrivate) {
            this.targetClass = targetClass;
            this.verbose = verbose;
            this.allowInterfaceResolvingToPrivate = allowInterfaceResolvingToPrivate;
        }

        Tables<C, M, F> build() throws MethodTableException {
            // First, we collect all method present from the superclass / superinterfaces.
            buildLocations();
            // Next, we look at the declared method, and present it as a candidate for its
            // corresponding locations.
            assignCandidateTargets();
            // For each entry in the parent table, check if the candidate overrides it.
            // Afterward, populate the vtable with relevant declared methods.
            resolveVirtual();
            // For each entry in the interface tables, look for an implementation, first amongst the
            // concrete class methods, then from the maximally specific ones.
            // This step also detect miranda methods.
            resolveInterfaces();

            return new Tables<>(vtable, itables, mirandas, equivalentEntries);
        }

        private void buildLocations() {
            registerFromTable(targetClass.getParentTable());
            MapCursor<C, List<M>> cursor = targetClass.getInterfacesData().getEntries();
            while (cursor.advance()) {
                registerFromTable(cursor.getValue());
            }
        }

        private void assignCandidateTargets() {
            for (PartialMethod<C, M, F> impl : targetClass.getDeclaredMethodsList()) {
                if (!isVirtualEntry(impl)) {
                    continue;
                }
                MethodKey k = MethodKey.of(impl);
                if (locations.containsKey(k)) {
                    Locations<C, M, F> entries = locations.get(k);
                    entries.setTarget(impl);
                }
            }
        }

        private void resolveVirtual() throws MethodTableException {
            List<M> parentTable = targetClass.getParentTable();
            for (int i = 0; i < parentTable.size(); i++) {
                M m = parentTable.get(i);
                MethodKey k = MethodKey.of(m);
                assert locations.containsKey(k) : "Should have been populated with super table.";
                Locations<C, M, F> currentLocations = locations.get(k);
                PartialMethod<C, M, F> target = currentLocations.target;
                if (target != null) {
                    assert m.getDeclaringClass().isInterface() || currentLocations.vLookup(i) == m : "Should have been populated with super table.";
                    if (canOverride(target, m, i)) {
                        if (m.isFinalFlagSet()) {
                            throw new MethodTableException(
                                            "Method " + target.getSymbolicName() + target.getSymbolicSignature() +
                                                            " from type " + targetClass.getSymbolicName() +
                                                            " overrides final method " + m.getSymbolicName() + target.getSymbolicSignature() +
                                                            " from type " + m.getDeclaringClass().getSymbolicName(),
                                            MethodTableException.Kind.IllegalClassChangeError);
                        }
                        vtable.add(target);
                        if (!verbose && sameOverrideAccess(target, m)) {
                            currentLocations.markEquivalentEntry(this, i);
                        }
                        continue;
                    }
                }
                PartialMethod<C, M, F> entry;
                if (m.getDeclaringClass().isInterface()) {
                    // This is a miranda method from the parent's. Though this type does not declare
                    // a method that can override it, one of its interfaces may be more specific, so
                    // we need a full resolution here.
                    // The result may be failing (PartialMethod.isSelectionFailure == true)
                    entry = currentLocations.resolve(k, this, true);
                } else {
                    entry = m;
                }
                vtable.add(entry);
            }
            assert vtable.size() == parentTable.size();
            for (PartialMethod<C, M, F> impl : targetClass.getDeclaredMethodsList()) {
                if (!isVirtualEntry(impl)) {
                    continue;
                }
                if (verbose) {
                    vtable.add(impl);
                } else {
                    MethodKey k = MethodKey.of(impl);
                    Locations<C, M, F> loc = locations.get(k);
                    if (loc == null || loc.shouldPopulate()) {
                        vtable.add(impl);
                    }
                }
            }
        }

        private void resolveInterfaces() {
            MapCursor<C, List<M>> cursor = targetClass.getInterfacesData().getEntries();
            while (cursor.advance()) {
                List<M> parentTable = cursor.getValue();
                List<PartialMethod<C, M, F>> table = new ArrayList<>(cursor.getValue().size());

                for (M m : parentTable) {
                    if (!isVirtualEntry(m)) {
                        // This should ideally not happen, but we must respect the decisions
                        // previously made for the tables of our super-interfaces.
                        table.add(m);
                        continue;
                    }
                    MethodKey k = MethodKey.of(m);
                    table.add(locations.get(k).resolve(k, this, false));
                }

                itables.put(cursor.getKey(), table);
            }
        }

        private void registerFromTable(List<M> table) {
            int index = 0;
            for (M m : table) {
                if (!isVirtualEntry(m)) {
                    continue;
                }
                MethodKey k = MethodKey.of(m);
                if (!locations.containsKey(k)) {
                    locations.put(k, new Locations<>());
                }
                assert locations.containsKey(k);
                locations.get(k).register(LocationKind.of(m), m, index);
                index++;
            }
        }

        /**
         * Whether the method {@code candidate} overrides the method {@code parentMethod} at index
         * {@code vtableIndex}, according to the definition from {@code jvms-5.4.5}.
         */
        private boolean canOverride(PartialMethod<C, M, F> candidate, M parentMethod, int vtableIndex) {
            if (canOverride(candidate, parentMethod)) {
                return true;
            }
            C parentClass = parentMethod.getDeclaringClass().getSuperClass();
            M currentMethod;
            while (parentClass != null &&
                            (currentMethod = parentClass.lookupVTableEntry(vtableIndex)) != null) {
                if (canOverride(candidate, currentMethod)) {
                    return true;
                }
                parentClass = parentClass.getSuperClass();
            }
            return false;
        }

        private boolean canOverride(PartialMethod<C, M, F> candidate, M parentMethod) {
            assert isVirtualEntry(candidate) && isVirtualEntry(parentMethod);
            assert candidate.getSymbolicName() == parentMethod.getSymbolicName() && candidate.getSymbolicSignature() == parentMethod.getSymbolicSignature();
            if (parentMethod.isPublic() || parentMethod.isProtected()) {
                return true;
            }
            return targetClass.sameRuntimePackage(parentMethod.getDeclaringClass());
        }

        /**
         * If two methods have equivalent access rules, they can safely share the same vtable slot.
         */
        private boolean sameOverrideAccess(PartialMethod<C, M, F> candidate, M parentMethod) {
            assert isVirtualEntry(candidate) && isVirtualEntry(parentMethod);
            assert candidate.getSymbolicName() == parentMethod.getSymbolicName() && candidate.getSymbolicSignature() == parentMethod.getSymbolicSignature();
            if (candidate.isPublic() || candidate.isProtected()) {
                return parentMethod.isPublic() || parentMethod.isProtected();
            }
            assert candidate.isPackagePrivate();
            return parentMethod.isPackagePrivate() && targetClass.sameRuntimePackage(parentMethod.getDeclaringClass());
        }

        private record MethodKey(Symbol<Name> name, Symbol<Signature> signature) {
            static <C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> MethodKey of(PartialMethod<C, M, F> method) {
                return new MethodKey(method.getSymbolicName(), method.getSymbolicSignature());
            }
        }

        private static final class Locations<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> {
            // Implementations found in the super vtable
            private final List<Location> vLocations = new ArrayList<>();
            // Declarations found in the interface's table
            private final List<Location> iLocations = new ArrayList<>();

            // Implementation of this class.
            private PartialMethod<C, M, F> target;
            // Whether the declaration should be added to the vtable.
            boolean shouldPopulate = true;
            // Cached result of itable resolution.
            boolean resolved = false;
            private PartialMethod<C, M, F> resolvedInterfaceMethod;

            void register(LocationKind kind, M method, int index) {
                switch (kind) {
                    case V:
                        assert vLocations.isEmpty() || vLocations.getLast().index < index;
                        vLocations.add(new Location(method, index));
                        break;
                    case I:
                        iLocations.add(new Location(method, index));
                        break;
                }
            }

            void setTarget(PartialMethod<C, M, F> declared) {
                this.target = declared;
            }

            M vLookup(int index) {
                int locIdx = Collections.binarySearch(vLocations, new Location(null, index));
                if (locIdx < 0) {
                    return null;
                }
                return vLocations.get(locIdx).value;
            }

            PartialMethod<C, M, F> resolve(MethodKey k, Builder<C, M, F> b, boolean inVTable) {
                if (resolved) {
                    return resolvedInterfaceMethod;
                }
                resolvedInterfaceMethod = resolveImpl(k, b, inVTable);
                resolved = true;
                return resolvedInterfaceMethod;
            }

            void markEquivalentEntry(Builder<?, ?, ?> b, int idx) {
                assert idx >= 0;
                if (shouldPopulate) {
                    b.equivalentEntries.add(idx);
                    shouldPopulate = false;
                }
            }

            boolean shouldPopulate() {
                return shouldPopulate;
            }

            private PartialMethod<C, M, F> resolveImpl(MethodKey k, Builder<C, M, F> b, boolean inVTable) {
                if (target != null) {
                    // simple case: This class declares a method that implements our interface
                    // method.
                    return target;
                }
                PartialMethod<C, M, F> result;
                if (!inVTable && b.allowInterfaceResolvingToPrivate) {
                    // Unfortunately, our representation does not take into account private methods.
                    // Ask the runtime for help.
                    result = b.targetClass.lookupOverrideWithPrivate(k.name(), k.signature());
                } else {
                    // Find the most specific virtual entry.
                    result = resolveConcrete();
                }
                if (result != null) {
                    return result;
                }
                /*
                 * No method in classes. Lookup in interfaces. This will be a miranda method. This
                 * also handles default method selection, if appropriate.
                 *
                 * Note: By construction of the locations map, each MethodKey is added only once to
                 * the miranda list, so it does not need to be a Set.
                 */
                PartialMethod<C, M, F> miranda = resolveMaximallySpecific();
                if (!inVTable) {
                    // Handling a miranda NOT from a parent's table, we can add it
                    b.mirandas.add(miranda);
                }
                return miranda;
            }

            private PartialMethod<C, M, F> resolveConcrete() {
                M candidate = null;
                for (Location loc : vLocations) {
                    candidate = mostSpecific(loc.value, candidate, true);
                }
                return candidate;
            }

            private PartialMethod<C, M, F> resolveMaximallySpecific() {
                EconomicSet<M> maximallySpecific = EconomicSet.create(Equivalence.IDENTITY);
                locationLoop: //
                for (Location loc : iLocations) {
                    Iterator<M> iter = maximallySpecific.iterator();
                    while (iter.hasNext()) {
                        M next = iter.next();
                        M mostSpecific = mostSpecific(loc.value, next, false);
                        if (mostSpecific == next) {
                            // An existing declaring class was already more specific.
                            continue locationLoop;
                        } else if (mostSpecific == loc.value) {
                            // The current declaring class is more specific: replace.
                            iter.remove();
                        } else {
                            assert mostSpecific == null;
                            // The declaring classes are unrelated
                        }
                    }
                    maximallySpecific.add(loc.value);
                }
                M nonAbstractMaximallySpecific = null;
                for (M m : maximallySpecific) {
                    if (!m.isAbstract()) {
                        if (nonAbstractMaximallySpecific != null) {
                            /*
                             * Multiple maximally specific non-abstract methods: mark the slot as
                             * null.
                             *
                             * Will require handling from runtime.
                             */
                            return new FailingPartialMethod<>(nonAbstractMaximallySpecific);
                        }
                        nonAbstractMaximallySpecific = m;
                    }
                }
                if (nonAbstractMaximallySpecific != null) {
                    // A single non-abstract maximally-specific method.
                    return nonAbstractMaximallySpecific;
                }
                // No non-abstract maximally specific: select an abstract method to fail on
                // invoke
                assert !maximallySpecific.isEmpty();
                return maximallySpecific.iterator().next();
            }

            private M mostSpecific(M m1, M m2, boolean totalOrder) {
                assert m1 != null;
                if (m2 == null) {
                    return m1;
                }
                if (m2.getDeclaringClass().isAssignableFrom(m1.getDeclaringClass())) {
                    return m1;
                }
                if (totalOrder) {
                    assert m1.getDeclaringClass().isAssignableFrom(m2.getDeclaringClass());
                    return m2;
                }
                if (m1.getDeclaringClass().isAssignableFrom(m2.getDeclaringClass())) {
                    return m2;
                }
                return null;
            }

            private class Location implements Comparable<Location> {
                private final M value;
                private final int index;

                Location(M value, int index) {
                    this.value = value;
                    this.index = index;
                }

                @Override
                public int compareTo(Location o) {
                    return Integer.compare(this.index, o.index);
                }
            }
        }

        private enum LocationKind {
            V,
            I,
            ;

            public static <M extends MethodAccess<?, ?, ?>> LocationKind of(M m) {
                if (m.getDeclaringClass().isInterface()) {
                    return I;
                } else {
                    return V;
                }
            }
        }
    }
}
