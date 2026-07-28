/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.vtable;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.UnmodifiableMapCursor;

import com.oracle.truffle.espresso.classfile.ParserMethod;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.shared.lookup.MethodLookup;
import com.oracle.truffle.espresso.shared.meta.FieldAccess;
import com.oracle.truffle.espresso.shared.meta.MethodAccess;
import com.oracle.truffle.espresso.shared.meta.TypeAccess;

/**
 * Creates the virtual and interface dispatch tables for a {@link PartialType}.
 * <p>
 * The builder is independent of a particular runtime representation. The caller provides a partial
 * type model: the superclass vtable, all implemented interface table prototypes, and the methods
 * declared by the type being created. From that model, this class computes:
 * <ul>
 * <li>the {@link Tables#getVtable() vtable}, preserving the superclass slot layout and appending
 * newly introduced virtual methods,</li>
 * <li>one {@link Tables#getItables() itable} per implemented interface, resolving each interface
 * slot against the target type, and</li>
 * <li>the {@link Tables#getImplicitInterfaceMethods() implicit interface methods} that have no class
 * implementation and therefore require runtime handling as miranda/default-method entries.</li>
 * </ul>
 * <p>
 * Returned table entries are {@link TableEntryRef references} so the builder can attach slot-local
 * metadata such as whether a declared method may use its vtable slot index, whether an entry was
 * appended as an implicit interface method, or whether dispatch must fail because method selection
 * found multiple maximally-specific non-abstract interface methods.
 */
public final class VTable {
    /**
     * Creates tables associated with the given {@link PartialType targetClass} using the default
     * builder policy.
     * <p>
     * The returned object is a {@link Tables} containing the {@link Tables#getVtable() virtual
     * table} and the {@link Tables#getItables() interface tables}. That object also makes known the
     * {@link Tables#getImplicitInterfaceMethods() implicit interface methods} that do not have a
     * concrete implementation in the class hierarchy of {@code targetClass}.
     * <p>
     * All the methods of a {@link Tables} returned by this method are guaranteed to return non-null
     * results, though returned {@link List lists} may be empty if appropriate. All lists in the
     * returned table will never contain {@code null} elements. Any {@link TableEntryRef} in the
     * resulting {@link Tables} references a {@link TableEntry} that was already present in the
     * various {@link PartialType} method's returned lists.
     *
     * @param targetClass The type for which tables should be created
     *
     * @param <C> The class providing access to the VM-side java {@link java.lang.Class}.
     * @param <M> The class providing access to the VM-side java {@link java.lang.reflect.Method}.
     * @param <F> The class providing access to the VM-side java {@link java.lang.reflect.Field}.
     */
    public static <C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> Tables<C, M, F> create(PartialType<C, M, F> targetClass)
                    throws MethodTableException {
        return create(targetClass, false, false, true, true);
    }

    /**
     * Creates tables associated with the given {@link PartialType targetClass} using an explicit
     * builder policy.
     * <p>
     * The returned object has the same non-null guarantees as {@link #create(PartialType)}. If
     * {@code allowInterfaceResolvingToPrivate} is {@code true} or {@code finalMethodsInSuperTable}
     * is {@code false}, interface resolution may also use
     * {@link PartialType#fallbackLookup(Symbol, Symbol, boolean)}; entries returned by fallback
     * lookup can appear in the result even if they did not appear in one of the input lists.
     *
     * @param targetClass The type for which method tables should be created
     * @param verbose Whether all declared methods should be unconditionally added to the vtable.
     * @param allowInterfaceResolvingToPrivate Whether the runtime allows selection of interface
     *            invokes to select private methods. Requires implementing
     *            {@link PartialType#fallbackLookup(Symbol, Symbol, boolean)}.
     * @param addImplicitInterfaceMethods Whether the builder should add implicit interface methods
     *            to the VTable.
     * @param finalMethodsInSuperTable Whether all final instance methods appear in the super's
     *            tables. Requires implementing
     *            {@link PartialType#fallbackLookup(Symbol, Symbol, boolean)} if set to
     *            {@code false}.
     *
     * @param <C> The class providing access to the VM-side java {@link java.lang.Class}.
     * @param <M> The class providing access to the VM-side java {@link java.lang.reflect.Method}.
     * @param <F> The class providing access to the VM-side java {@link java.lang.reflect.Field}.
     */
    public static <C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> Tables<C, M, F> create(
                    PartialType<C, M, F> targetClass,
                    boolean verbose,
                    boolean allowInterfaceResolvingToPrivate,
                    boolean addImplicitInterfaceMethods,
                    boolean finalMethodsInSuperTable) throws MethodTableException {
        return new Builder<>(targetClass, verbose, allowInterfaceResolvingToPrivate, addImplicitInterfaceMethods, finalMethodsInSuperTable).build();
    }

    /**
     * Returns whether a given method may appear in a vtable.
     */
    public static <C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> boolean isVirtualEntry(TableEntry<C, M, F> m) {
        return !m.isPrivate() && !m.isStatic() &&
                        !ParserMethod.isConstructor(m.getModifiers(), m.getSymbolicName()) &&
                        !ParserMethod.isClassInitializer(m.getModifiers(), m.getSymbolicName(), m.getSymbolicSignature());
    }

    private static final class Builder<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> {
        private final boolean verbose;
        private final boolean allowInterfaceResolvingToPrivate;
        private final boolean addMirandas;
        private final boolean finalMethodsInSuperTable;

        private final PartialType<C, M, F> targetClass;
        private final EconomicMap<MethodKey, Locations<C, M, F>> locations = EconomicMap.create();

        private final List<TableEntryRef<C, M, F>> vtable = new ArrayList<>();
        private final EconomicMap<C, List<TableEntryRef<C, M, F>>> itables = EconomicMap.create(Equivalence.IDENTITY);
        private final List<TableEntryRef<C, M, F>> mirandas = new ArrayList<>();

        Builder(PartialType<C, M, F> targetClass, boolean verbose, boolean allowInterfaceResolvingToPrivate, boolean addMirandas, boolean finalMethodsInSuperTable) {
            this.targetClass = targetClass;
            this.verbose = verbose;
            this.allowInterfaceResolvingToPrivate = allowInterfaceResolvingToPrivate;
            this.addMirandas = addMirandas;
            this.finalMethodsInSuperTable = finalMethodsInSuperTable;
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
            int mirandaStart = vtable.size();
            if (addMirandas) {
                resolveMirandas();
            }

            return new Tables<>(vtable, itables, mirandas, mirandaStart);
        }

        private boolean needsFallback(boolean inVTable) {
            return !inVTable && (allowInterfaceResolvingToPrivate || !finalMethodsInSuperTable);
        }

        private void buildLocations() {
            registerFromTable(targetClass.getParentTable(), LocationKind.V);
            UnmodifiableMapCursor<C, List<M>> cursor = targetClass.getInterfacesData().getEntries();
            while (cursor.advance()) {
                registerFromTable(cursor.getValue(), LocationKind.I);
            }
        }

        private void assignCandidateTargets() {
            for (TableEntry<C, M, F> impl : targetClass.getDeclaredMethodsList()) {
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
                M parentMethod = parentTable.get(i);
                MethodKey k = MethodKey.of(parentMethod);
                assert locations.containsKey(k) : "Should have been populated with super table.";
                Locations<C, M, F> currentLocations = locations.get(k);
                // If this class declares a method with same name and signature, it might be the
                // entry in the vtable for this slot.
                TableEntry<C, M, F> declaredMethod = currentLocations.target;
                if (declaredMethod != null) {
                    assert parentMethod.getDeclaringClass().isInterface() || currentLocations.vLookup(i) == parentMethod : "Should have been populated with super table.";
                    if (canOverride(declaredMethod, parentMethod, i)) {
                        if (parentMethod.isFinalFlagSet()) {
                            throw new MethodTableException(
                                            "Method " + declaredMethod.getSymbolicName() + declaredMethod.getSymbolicSignature() +
                                                            " from type " + targetClass.getSymbolicName() +
                                                            " overrides final method " + parentMethod.getSymbolicName() + declaredMethod.getSymbolicSignature() +
                                                            " from type " + parentMethod.getDeclaringClass().getSymbolicName(),
                                            MethodTableException.Kind.IncompatibleClassChangeError);
                        }
                        TableEntryRef<C, M, F> declaredEntry = TableEntryRef.create(declaredMethod);
                        if (!verbose && sameOverrideAccess(declaredMethod, parentMethod)) {
                            // If this declared method overrides a method with equivalent access, we
                            // don't need to add that method at the end.
                            if (currentLocations.markEquivalentEntry()) {
                                // Make sure if this method has multiple equivalent entries, only
                                // one gets to use the vtable index.
                                declaredEntry.useVTableSlotIndex();
                            }
                        }
                        // Success: write this declared method in the table
                        vtable.add(declaredEntry);
                        continue;
                    }
                }
                TableEntryRef<C, M, F> entry = TableEntryRef.create(parentMethod);
                if (parentMethod.getDeclaringClass().isInterface()) {
                    // This is a miranda method from the parent's. Though this type does not declare
                    // a method that can override it, one of its interfaces may be more specific, so
                    // we need a full resolution here.
                    // The result may be failing (TableEntryRef.isSelectionFailure == true)
                    entry = currentLocations.resolve(k, this, true);
                    if (!entry.isSelectionFailure() && entry.getEntry() instanceof MethodAccess<C, M, F> ma) {
                        // Sneaky optimization: if the parent's entry has the same identity as the
                        // resolution, prefer inheriting the parent's.
                        if (ma.getDeclaringClass() == parentMethod.getDeclaringClass()) {
                            // Same non-failing method as parent, re-use it.
                            vtable.add(TableEntryRef.create(parentMethod));
                            continue;
                        }
                    }
                    // This is a newly resolved miranda method, mark it as such.
                    vtable.add(entry.asImplicitInterfaceMethod());
                    continue;
                }
                // An entry in parent's table, already has a vtable index.
                vtable.add(entry);
            }
            assert vtable.size() == parentTable.size();
            for (TableEntry<C, M, F> declaredMethod : targetClass.getDeclaredMethodsList()) {
                if (!isVirtualEntry(declaredMethod)) {
                    continue;
                }
                // Declared method do not have a vtable index yet, so assign it here.
                if (verbose) {
                    vtable.add(TableEntryRef.create(declaredMethod).useVTableSlotIndex());
                } else {
                    MethodKey k = MethodKey.of(declaredMethod);
                    Locations<C, M, F> loc = locations.get(k);
                    if (loc == null || loc.shouldPopulate()) {
                        // No equivalent slot was found, we must add the method to the vtable.
                        vtable.add(TableEntryRef.create(declaredMethod).useVTableSlotIndex());
                    }
                }
            }
        }

        private void resolveInterfaces() {
            UnmodifiableMapCursor<C, List<M>> cursor = targetClass.getInterfacesData().getEntries();
            while (cursor.advance()) {
                List<TableEntryRef<C, M, F>> table = new ArrayList<>(cursor.getValue().size());

                for (M m : cursor.getValue()) {
                    if (!isVirtualEntry(m)) {
                        // This should ideally not happen, but we must respect the decisions
                        // previously made for the tables of our super-interfaces.
                        table.add(TableEntryRef.create(m));
                        continue;
                    }
                    MethodKey k = MethodKey.of(m);
                    TableEntryRef<C, M, F> entry = locations.get(k).resolve(k, this, false);
                    if (!entry.getEntry().isPublic()) {
                        entry.asNonPublicInterfaceSelection();
                    }
                    table.add(entry);
                }

                itables.put(cursor.getKey(), table);
            }
        }

        private void resolveMirandas() {
            for (int i = 0; i < mirandas.size(); i++) {
                // For leftover mirandas, add them to the table and mark them as such.
                TableEntryRef<C, M, F> entry = mirandas.get(i).asImplicitInterfaceMethod();
                vtable.add(entry);
            }
        }

        private void registerFromTable(List<M> table, LocationKind kind) {
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
                if (LocationKind.of(m) == kind) {
                    // Do not bother adding parent mirandas, they will be added later.
                    locations.get(k).register(kind, m, index);
                }
                index++;
            }
        }

        /**
         * Whether the method {@code candidate} overrides the method {@code parentMethod} at index
         * {@code vtableIndex}, according to the definition from {@code jvms-5.4.5}.
         */
        private boolean canOverride(TableEntry<C, M, F> candidate, M parentMethod, int vtableIndex) {
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

        private boolean canOverride(TableEntry<C, M, F> candidate, M parentMethod) {
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
        private boolean sameOverrideAccess(TableEntry<C, M, F> candidate, M parentMethod) {
            assert isVirtualEntry(candidate) && isVirtualEntry(parentMethod);
            assert candidate.getSymbolicName() == parentMethod.getSymbolicName() && candidate.getSymbolicSignature() == parentMethod.getSymbolicSignature();
            if (candidate.isPublic() || candidate.isProtected()) {
                return parentMethod.isPublic() || parentMethod.isProtected();
            }
            assert candidate.isPackagePrivate();
            return parentMethod.isPackagePrivate() && targetClass.sameRuntimePackage(parentMethod.getDeclaringClass());
        }

        private record MethodKey(Symbol<Name> name, Symbol<Signature> signature) {
            static <C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> MethodKey of(TableEntry<C, M, F> method) {
                return new MethodKey(method.getSymbolicName(), method.getSymbolicSignature());
            }
        }

        private static final class Locations<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> {
            // Implementations found in the super vtable
            private final List<Location> vLocations = new ArrayList<>();
            // Declarations found in the interface's table
            private final List<Location> iLocations = new ArrayList<>();

            // Implementation of this class.
            private TableEntry<C, M, F> target;
            // Whether the declaration should be added to the vtable.
            boolean shouldPopulate = true;
            // Cached result of itable resolution.
            private TableEntry<C, M, F> resolved;
            private boolean selectionFailure;

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

            void setTarget(TableEntry<C, M, F> declared) {
                this.target = declared;
            }

            M vLookup(int index) {
                int locIdx = Collections.binarySearch(vLocations, new Location(null, index));
                if (locIdx < 0) {
                    return null;
                }
                return vLocations.get(locIdx).value;
            }

            TableEntryRef<C, M, F> resolve(MethodKey k, Builder<C, M, F> b, boolean inVTable) {
                TableEntry<C, M, F> resolution;
                if (resolved != null && !b.needsFallback(inVTable)) {
                    resolution = resolved;
                } else {
                    resolution = resolveImpl(k, b, inVTable);
                }
                if (resolved == null && !b.needsFallback(inVTable)) {
                    resolved = resolution;
                }
                TableEntryRef<C, M, F> result = TableEntryRef.create(resolution);
                if (selectionFailure) {
                    result.asSelectionFailure();
                }
                return result;
            }

            boolean markEquivalentEntry() {
                if (shouldPopulate) {
                    shouldPopulate = false;
                    return true;
                }
                return false;
            }

            boolean shouldPopulate() {
                return shouldPopulate;
            }

            private TableEntry<C, M, F> resolveImpl(MethodKey k, Builder<C, M, F> b, boolean inVTable) {
                if (target != null && !b.targetClass.isInterface()) {
                    // simple case: This concrete class declares a method that implements our
                    // interface method.
                    return target;
                }
                TableEntry<C, M, F> result;
                if (!inVTable && b.allowInterfaceResolvingToPrivate) {
                    // Unfortunately, our representation does not take into account private methods.
                    // Ask the runtime for help.
                    result = b.targetClass.fallbackLookup(k.name(), k.signature(), true);
                } else {
                    // Find the most specific virtual entry.
                    result = resolveConcrete();
                }
                if (result != null) {
                    return result;
                }
                if (!inVTable && !b.finalMethodsInSuperTable) {
                    /*
                     * We may have missed a final method from the super hierarchy. This is done
                     * after resolveConcrete to not have to do a full lokup if it is in the tables
                     * already.
                     */
                    result = b.targetClass.fallbackLookup(k.name(), k.signature(), false);
                    if (result != null) {
                        return result;
                    }
                }
                if (target != null && !target.isAbstract() && b.targetClass.isInterface()) {
                    // No method from a concrete class to override this method declared in an interface.
                    return target;
                }
                /*
                 * No method in classes. Lookup in interfaces. This will be a miranda method. This
                 * also handles default method selection, if appropriate.
                 *
                 * Note: By construction of the locations map, each MethodKey is added only once to
                 * the miranda list, so it does not need to be a Set.
                 */
                TableEntry<C, M, F> miranda = resolveMaximallySpecific();
                if (!inVTable) {
                    // Handling a miranda NOT from a parent's table, we can add it
                    TableEntryRef<C, M, F> mirandaRef = TableEntryRef.create(miranda);
                    if (selectionFailure) {
                        mirandaRef.asSelectionFailure();
                    }
                    b.mirandas.add(mirandaRef);
                }
                return miranda;
            }

            private TableEntry<C, M, F> resolveConcrete() {
                M candidate = null;
                for (Location loc : vLocations) {
                    if (candidate == null) {
                        candidate = loc.value;
                    } else {
                        // Here, the classes are known to be from the same hierarchy, so a single
                        // type check is needed.
                        candidate = loc.value.getDeclaringClass().isAssignableFrom(candidate.getDeclaringClass()) ? candidate : loc.value;
                    }
                }
                return candidate;
            }

            private TableEntry<C, M, F> resolveMaximallySpecific() {
                Set<M> maximallySpecific = MethodLookup.resolveMaximallySpecific(new IMethodsList());
                M nonAbstractMaximallySpecific = null;
                for (M m : maximallySpecific) {
                    if (!m.isAbstract()) {
                        if (nonAbstractMaximallySpecific != null) {
                            /*
                             * Multiple maximally specific non-abstract methods: mark the slot as
                             * failing.
                             *
                             * Will require handling from runtime.
                             */
                            selectionFailure = true;
                            return nonAbstractMaximallySpecific;
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

            private final class IMethodsList extends AbstractList<M> {
                @Override
                public M get(int index) {
                    return iLocations.get(index).value;
                }

                @Override
                public int size() {
                    return iLocations.size();
                }
            }
        }

        private enum LocationKind {
            V,
            I,;

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
