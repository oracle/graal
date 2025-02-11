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

public final class VTable {
    public static <C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> Tables<C, M, F> create(
                    PartialType<C, M, F> targetClass,
                    boolean verbose,
                    boolean allowInterfaceResolvingToPrivate) {
        return new Builder<>(targetClass, verbose, allowInterfaceResolvingToPrivate).build();
    }

    private static final class Builder<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> {
        private final boolean verbose;
        private final boolean allowInterfaceResolvingToPrivate;

        private final PartialType<C, M, F> targetClass;
        private final EconomicMap<MethodKey, Locations<C, M, F>> locations = EconomicMap.create();

        private final List<PartialMethod<C, M, F>> vtable = new ArrayList<>();
        private final EconomicMap<C, List<PartialMethod<C, M, F>>> itables = EconomicMap.create(Equivalence.IDENTITY);
        private final List<M> mirandas = new ArrayList<>();

        Builder(PartialType<C, M, F> targetClass, boolean verbose, boolean allowInterfaceResolvingToPrivate) {
            this.targetClass = targetClass;
            this.verbose = verbose;
            this.allowInterfaceResolvingToPrivate = allowInterfaceResolvingToPrivate;
        }

        Tables<C, M, F> build() {
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

            return new Tables<>(vtable, itables, mirandas);
        }

        private void buildLocations() {
            registerFromTable(targetClass.getParentTable(), LocationKind.V);
            MapCursor<C, List<M>> cursor = targetClass.getInterfacesData().getEntries();
            while (cursor.advance()) {
                registerFromTable(cursor.getValue(), LocationKind.I);
            }
        }

        private void assignCandidateTargets() {
            for (PartialMethod<C, M, F> impl : targetClass.getDeclaredMethodsList()) {
                if (!impl.isVirtualEntry()) {
                    continue;
                }
                MethodKey k = MethodKey.of(impl);
                if (locations.containsKey(k)) {
                    Locations<C, M, F> entries = locations.get(k);
                    entries.setTarget(impl);
                }
            }
        }

        private void resolveVirtual() {
            List<M> parentTable = targetClass.getParentTable();
            for (int i = 0; i < parentTable.size(); i++) {
                M m = parentTable.get(i);
                MethodKey k = MethodKey.of(m);
                assert locations.containsKey(k) : "Should have been populated with super table.";
                Locations<C, M, F> currentLocations = locations.get(k);
                PartialMethod<C, M, F> target = currentLocations.target;
                if (target == null) {
                    // No declared method to override parent's
                    vtable.add(m);
                    continue;
                }
                assert currentLocations.vLookup(i) == m : "Should have been populated with super table.";
                if (target.canOverride(m, i)) {
                    vtable.add(target);
                    if (!target.sameAccess(m)) {
                        currentLocations.markForPopulation();
                    }
                } else {
                    vtable.add(m);
                    currentLocations.markForPopulation();
                }
            }
            for (PartialMethod<C, M, F> impl : targetClass.getDeclaredMethodsList()) {
                if (!impl.isVirtualEntry()) {
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
                    MethodKey k = MethodKey.of(m);
                    table.add(locations.get(k).resolveInterface(k, this));
                }

                itables.put(cursor.getKey(), table);
            }
        }

        private void registerFromTable(List<M> table, LocationKind kind) {
            int index = 0;
            for (M m : table) {
                MethodKey k = MethodKey.of(m);
                if (!locations.containsKey(k)) {
                    locations.put(k, new Locations<>());
                }
                assert locations.containsKey(k);
                locations.get(k).register(kind, m, index);
                index++;
            }
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
            boolean shouldPopulate = false;
            // Cached result of itable resolution.
            boolean resolved = false;
            private PartialMethod<C, M, F> resolvedInterfaceMethod;

            void register(LocationKind kind, M method, int index) {
                switch (kind) {
                    case V:
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
                for (Location loc : vLocations) {
                    if (loc.index == index) {
                        return loc.value;
                    }
                }
                return null;
            }

            PartialMethod<C, M, F> resolveInterface(MethodKey k, Builder<C, M, F> b) {
                if (resolved) {
                    return resolvedInterfaceMethod;
                }
                resolvedInterfaceMethod = resolveInterfaceImpl(k, b);
                resolved = true;
                return resolvedInterfaceMethod;
            }

            public void markForPopulation() {
                shouldPopulate = true;
            }

            public boolean shouldPopulate() {
                return shouldPopulate || vLocations.isEmpty();
            }

            private PartialMethod<C, M, F> resolveInterfaceImpl(MethodKey k, Builder<C, M, F> b) {
                if (target != null) {
                    // simple case: This class declares a method that implements our interface
                    // method.
                    return target;
                }
                PartialMethod<C, M, F> result;
                if (b.allowInterfaceResolvingToPrivate) {
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
                // No method in classes. Lookup in interfaces. This will be a miranda method.
                M miranda = resolveMaximallySpecific();
                if (miranda == null) {
                    b.mirandas.add(iLocations.get(0).value);
                } else {
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

            private M resolveMaximallySpecific() {
                EconomicSet<M> maximallySpecific = EconomicSet.create(Equivalence.IDENTITY);
                locationLoop: //
                for (Location loc : iLocations) {
                    if (loc.value.isAbstract()) {
                        // Ignore non-abstract methods
                        continue;
                    }
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
                if (maximallySpecific.size() == 1) {
                    return maximallySpecific.iterator().next();
                }
                // Failed to find a maximally specific method:
                return null;
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
                    return m2;
                }
                if (m1.getDeclaringClass().isAssignableFrom(m2.getDeclaringClass())) {
                    return m2;
                }
                return null;
            }

            private class Location {
                private final M value;
                private final int index;

                Location(M value, int index) {
                    this.value = value;
                    this.index = index;
                }
            }
        }

        private enum LocationKind {
            V,
            I,
        }
    }
}
