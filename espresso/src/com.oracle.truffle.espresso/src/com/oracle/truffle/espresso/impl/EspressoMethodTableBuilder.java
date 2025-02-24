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
package com.oracle.truffle.espresso.impl;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.shared.vtable.MethodTableException;
import com.oracle.truffle.espresso.shared.vtable.PartialMethod;
import com.oracle.truffle.espresso.shared.vtable.PartialType;
import com.oracle.truffle.espresso.shared.vtable.Tables;
import com.oracle.truffle.espresso.shared.vtable.VTable;

public final class EspressoMethodTableBuilder {
    public static EspressoTables create(
                    ObjectKlass.KlassVersion thisKlass,
                    ObjectKlass.KlassVersion[] transitiveInterfaces,
                    Method.MethodVersion[] declaredMethods,
                    boolean allowInterfaceResolutionToPrivete) {
        try {
            if (thisKlass.isInterface()) {
                return new EspressoTables(
                                assignITableIndexes(thisKlass,
                                                toEspressoVTable(filterInterfaceMethods(declaredMethods), Method.EMPTY_VERSION_ARRAY)),
                                null,
                                null);
            }
            Tables<Klass, Method, Field> tables;
            tables = VTable.create(
                            new PartialKlass(thisKlass, thisKlass.getSuperKlass(), transitiveInterfaces, declaredMethods),
                            false,
                            allowInterfaceResolutionToPrivete);
            Method.MethodVersion[] mirandas = mirandas(tables);
            return new EspressoTables(
                            assignVTableIndexes(thisKlass, vtable(tables, mirandas)),
                            itable(tables, transitiveInterfaces),
                            mirandas);
        } catch (MethodTableException e) {
            Meta meta = EspressoContext.get(null).getMeta();
            switch (e.getKind()) {
                case IllegalClassChangeError:
                    meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, e.getMessage());
            }
            throw EspressoError.shouldNotReachHere("Unknown exception kind: " + e.getKind(), e);
        }
    }

    public static ObjectKlass.KlassVersion[] transitiveInterfaceList(ObjectKlass superKlass, ObjectKlass[] declaredInterfaces) {
        EconomicSet<ObjectKlass.KlassVersion> set = EconomicSet.create(Equivalence.IDENTITY);
        if (superKlass != null) {
            for (ObjectKlass.KlassVersion intf : superKlass.getiKlassTable()) {
                set.add(intf);
            }
        }
        for (ObjectKlass intf : declaredInterfaces) {
            set.add(intf.getKlassVersion());
            for (ObjectKlass.KlassVersion transitiveIntf : intf.getiKlassTable()) {
                set.add(transitiveIntf);
            }
        }
        if (set.isEmpty()) {
            return ObjectKlass.EMPTY_KLASSVERSION_ARRAY;
        }
        ObjectKlass.KlassVersion[] array = set.toArray(new ObjectKlass.KlassVersion[set.size()]);
        Arrays.sort(array, Klass.KLASS_VERSION_ID_COMPARATOR);
        return array;
    }

    public static boolean declaresDefaultMethod(Method.MethodVersion[] declared) {
        for (Method.MethodVersion method : declared) {
            if (!method.isAbstract() && !method.isStatic()) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasDefaultMethods(boolean declaresDefaultMethod, ObjectKlass superKlass, ObjectKlass[] declaredInterfaces) {
        if (declaresDefaultMethod) {
            return true;
        }
        if (superKlass != null && superKlass.hasDefaultMethods()) {
            return true;
        }
        for (ObjectKlass interf : declaredInterfaces) {
            if (interf.hasDefaultMethods()) {
                return true;
            }
        }
        return false;
    }

    public static final class EspressoTables {

        private final Method.MethodVersion[] vtable;
        private final Method.MethodVersion[][] itable;
        private final Method.MethodVersion[] mirandas;

        public EspressoTables(Method.MethodVersion[] vtable, Method.MethodVersion[][] itable, Method.MethodVersion[] mirandas) {
            this.vtable = vtable;
            this.itable = itable;
            this.mirandas = mirandas;
        }

        public Method.MethodVersion[] getVTable() {
            return vtable;
        }

        public Method.MethodVersion[][] getITable() {
            return itable;
        }

        public Method.MethodVersion[] getMirandas() {
            return mirandas;
        }
    }

    private static List<PartialMethod<Klass, Method, Field>> filterInterfaceMethods(Method.MethodVersion[] declaredMethods) {
        List<PartialMethod<Klass, Method, Field>> table = new ArrayList<>(declaredMethods.length);
        for (Method.MethodVersion m : declaredMethods) {
            if (VTable.isVirtualEntry(m.getMethod())) {
                table.add(m.getMethod());
            }
        }
        return table;
    }

    private static Method.MethodVersion[] assignITableIndexes(ObjectKlass.KlassVersion thisKlass, Method.MethodVersion[] table) {
        assert thisKlass.isInterface() && table != null;
        for (int i = 0; i < table.length; i++) {
            Method.MethodVersion m = table[i];
            assert m.getITableIndex() == -1;
            assert m.getDeclaringKlass() == thisKlass.getKlass();
            m.setITableIndex(i);
        }
        return table;
    }

    private static Method.MethodVersion[] assignVTableIndexes(ObjectKlass.KlassVersion thisKlass, Method.MethodVersion[] vtable) {
        assert !thisKlass.isInterface() && vtable != null;
        for (int i = vtable.length - 1; i >= 0; i--) {
            Method.MethodVersion m = vtable[i];
            if (m.getVTableIndex() == -1) {
                // There are three cases here:
                // - m is a method declared in thisKlass.
                // - m is a miranda method of thisKlass.
                // - m is a miranda method of a parent of thisKlass.
                /*
                 * The 3rd case is a bit problematic for us, as it will be not the proxy in the
                 * parent's table, but an actual method in an interface (not necessarily the same as
                 * the one in the parent's table).
                 *
                 * We must detect that case, and simply put the parent's proxy back in this table.
                 * This case can be detected by checking if m has its itable index set (resolved to
                 * an interface method).
                 *
                 * Note that if m was a selection failure for thisKlass (but not for its parents),
                 * it would have already been proxified, and would have its itable index not set.
                 */
                if (m.getITableIndex() == -1) {
                    // 1st and 2nd case
                    assert m.getDeclaringKlass() == thisKlass.getKlass() ||
                                    m.getDeclaringKlass().isInterface(); /*- mirandas are added as clean proxies */
                    m.setVTableIndex(i);
                } else {
                    // 3rd case
                    // m is a miranda method from one of thisKlass parents
                    Method.MethodVersion[] superTable = thisKlass.getSuperKlass().getVTable();
                    assert i < superTable.length;
                    Method.MethodVersion parentMethod = superTable[i];
                    assert parentMethod.getVTableIndex() == i;
                    if (parentMethod.getMethod().identity() == m.getMethod().identity()) {
                        // If they resolve to the same method, we can re-use the parent's
                        vtable[i] = parentMethod;
                    } else {
                        // One of the interfaces of thisKlass is more specific than all the
                        // parent's, so we need to spawn a new proxy.
                        Method.MethodVersion proxy = new Method(m.getMethod()).getMethodVersion();
                        proxy.setVTableIndex(i);
                        vtable[i] = proxy;
                    }
                }
            }
        }
        return vtable;
    }

    private static Method.MethodVersion[] vtable(Tables<Klass, Method, Field> tables, Method.MethodVersion[] mirandas) {
        List<PartialMethod<Klass, Method, Field>> table = tables.getVtable();
        assert table != null && mirandas != null;
        return toEspressoVTable(table, mirandas);
    }

    private static Method.MethodVersion[] toEspressoVTable(List<? extends PartialMethod<Klass, Method, Field>> table, Method.MethodVersion[] mirandas) {
        Method.MethodVersion[] vtable = new Method.MethodVersion[table.size() + mirandas.length];
        int vtableIndex = 0;
        for (PartialMethod<Klass, Method, Field> m : table) {
            Method entry = m.asMethodAccess();
            if (m.isSelectionFailure()) {
                entry = new Method(entry).setPoisonPill();
            }
            vtable[vtableIndex] = entry.getMethodVersion();
            vtableIndex++;
        }
        for (Method.MethodVersion m : mirandas) {
            vtable[vtableIndex] = m;
            vtableIndex++;
        }
        return vtable;
    }

    private static Method.MethodVersion[][] itable(Tables<Klass, Method, Field> tables, ObjectKlass.KlassVersion[] transitiveInterfaces) {
        EconomicMap<Klass, List<PartialMethod<Klass, Method, Field>>> table = tables.getItables();
        assert table != null && transitiveInterfaces != null;
        Method.MethodVersion[][] itable = new Method.MethodVersion[transitiveInterfaces.length][];
        for (int i = 0; i < transitiveInterfaces.length; i++) {
            ObjectKlass intf = transitiveInterfaces[i].getKlass();
            itable[i] = toEspressoITable(table.get(intf));
        }
        return itable;
    }

    private static Method.MethodVersion[] toEspressoITable(List<? extends PartialMethod<Klass, Method, Field>> table) {
        Method.MethodVersion[] itable = new Method.MethodVersion[table.size()];
        int itableIndex = 0;
        for (PartialMethod<Klass, Method, Field> m : table) {
            Method entry = m.asMethodAccess();
            if (m.isSelectionFailure()) {
                entry = new Method(entry).setPoisonPill();
            }
            itable[itableIndex] = entry.getMethodVersion();
            itableIndex++;
        }
        return itable;
    }

    private static Method.MethodVersion[] mirandas(Tables<Klass, Method, Field> tables) {
        List<PartialMethod<Klass, Method, Field>> mirandas = tables.getImplicitInterfaceMethods();
        assert mirandas != null;
        return toEspressoMirandas(mirandas);
    }

    private static Method.MethodVersion[] toEspressoMirandas(List<PartialMethod<Klass, Method, Field>> mirandas) {
        Method.MethodVersion[] table = new Method.MethodVersion[mirandas.size()];
        int idx = 0;
        for (PartialMethod<Klass, Method, Field> m : mirandas) {
            // Force proxy-ing to ensure methods have either an itable or vtable index, but not
            // both.
            Method entry = new Method(m.asMethodAccess());
            if (m.isSelectionFailure()) {
                entry.setPoisonPill();
            }
            table[idx] = entry.getMethodVersion();
            idx++;
        }
        return table;
    }

    private static class PartialKlass implements PartialType<Klass, Method, Field> {
        private final ObjectKlass thisKlass;
        private final ObjectKlass superKlass;
        private final List<Method> parentTable;
        private final List<? extends PartialMethod<Klass, Method, Field>> declaredMethods;
        private final EconomicMap<Klass, List<Method>> interfacesData;

        PartialKlass(ObjectKlass.KlassVersion thisKlass, ObjectKlass superKlass, ObjectKlass.KlassVersion[] transitiveInterfaces, Method.MethodVersion[] declaredMethods) {
            this.thisKlass = thisKlass.getKlass();
            this.superKlass = superKlass;
            this.parentTable = superKlass == null ? Collections.emptyList() : new VersionToMethodList(superKlass.getVTable());
            this.declaredMethods = new VersionToMethodList(declaredMethods);
            this.interfacesData = EconomicMap.create(Equivalence.IDENTITY, transitiveInterfaces.length);
            for (ObjectKlass.KlassVersion intfVersion : transitiveInterfaces) {
                ObjectKlass intf = intfVersion.getKlass();
                interfacesData.put(intf, new VersionToMethodList(intf.getInterfaceMethodsTable()));
            }
        }

        @Override
        public Symbol<Name> getSymbolicName() {
            return thisKlass.getSymbolicName();
        }

        @Override
        public List<Method> getParentTable() {
            return parentTable;
        }

        @Override
        public EconomicMap<Klass, List<Method>> getInterfacesData() {
            return interfacesData;
        }

        @Override
        public List<? extends PartialMethod<Klass, Method, Field>> getDeclaredMethodsList() {
            return declaredMethods;
        }

        @Override
        public boolean sameRuntimePackage(Klass otherType) {
            return thisKlass.sameRuntimePackage(otherType);
        }

        @Override
        public PartialMethod<Klass, Method, Field> lookupOverrideWithPrivate(Symbol<Name> name, Symbol<Signature> signature) {
            for (PartialMethod<Klass, Method, Field> m : declaredMethods) {
                if (!m.isStatic() && m.getSymbolicName() == name && m.getSymbolicSignature() == signature) {
                    return m;
                }
            }
            return superKlass.lookupInstanceMethod(name, signature);
        }
    }

    /**
     * Helper list implementation to serve a {@link Method} from an array of
     * {@link Method.MethodVersion}.
     */
    private static final class VersionToMethodList extends AbstractList<Method> {
        private final Method.MethodVersion[] table;

        VersionToMethodList(Method.MethodVersion[] table) {
            this.table = table;
        }

        @Override
        public Method get(int index) {
            return table[index].getMethod();
        }

        @Override
        public int size() {
            return table.length;
        }
    }
}
