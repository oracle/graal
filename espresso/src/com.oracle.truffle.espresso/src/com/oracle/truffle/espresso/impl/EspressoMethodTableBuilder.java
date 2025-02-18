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
                    boolean isInterface,
                    Symbol<Name> name,
                    ObjectKlass superKlass,
                    ObjectKlass.KlassVersion[] transitiveInterfaces,
                    Method.MethodVersion[] declaredMethods,
                    boolean allowInterfaceResolutionToPrivete) {
        try {
            Tables<Klass, Method, Field> tables;
            if (isInterface) {
                tables = new Tables<>(filterInterfaceMethods(declaredMethods), null, null, null);
            } else {
                tables = VTable.create(
                                new PartialKlass(name, superKlass, transitiveInterfaces, declaredMethods),
                                false,
                                allowInterfaceResolutionToPrivete);
            }
            return new EspressoTables(
                            assignTableIndexes(isInterface, vtable(tables)),
                            itable(tables, transitiveInterfaces),
                            mirandas(tables));
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
            if (m.getMethod().isVirtualEntry()) {
                table.add(m.getMethod());
            }
        }
        return table;
    }

    private static Method.MethodVersion[] assignTableIndexes(boolean isInterface, Method.MethodVersion[] vtable) {
        if (vtable == null) {
            return null;
        }
        for (int i = vtable.length - 1; i >= 0; i--) {
            Method.MethodVersion m = vtable[i];
            if (isInterface) {
                if (m.getITableIndex() == -1) {
                    m.setITableIndex(i);
                }
            } else {
                if (m.getVTableIndex() == -1) {
                    m.setVTableIndex(i);
                }
            }
        }
        return vtable;
    }

    private static Method.MethodVersion[] vtable(Tables<Klass, Method, Field> tables) {
        List<PartialMethod<Klass, Method, Field>> table = tables.getVtable();
        if (table == null) {
            return null;
        }
        return toEspressoVTable(table);
    }

    private static Method.MethodVersion[] toEspressoVTable(List<? extends PartialMethod<Klass, Method, Field>> table) {
        Method.MethodVersion[] vtable = new Method.MethodVersion[table.size()];
        int vtableIndex = 0;
        for (PartialMethod<Klass, Method, Field> m : table) {
            vtable[vtableIndex] = m.asMethodAccess().getMethodVersion();
            vtableIndex++;
        }
        return vtable;
    }

    private static Method.MethodVersion[][] itable(Tables<Klass, Method, Field> tables, ObjectKlass.KlassVersion[] transitiveInterfaces) {
        EconomicMap<Klass, List<PartialMethod<Klass, Method, Field>>> table = tables.getItables();
        if (table == null) {
            return null;
        }
        Method.MethodVersion[][] itable = new Method.MethodVersion[transitiveInterfaces.length][];
        for (int i = 0; i < transitiveInterfaces.length; i++) {
            ObjectKlass intf = transitiveInterfaces[i].getKlass();
            itable[i] = toEspressoITable(intf.getInterfaceMethodsTable(), table.get(intf));
        }
        return itable;
    }

    private static Method.MethodVersion[] toEspressoITable(Method.MethodVersion[] intfTable, List<? extends PartialMethod<Klass, Method, Field>> table) {
        Method.MethodVersion[] itable = new Method.MethodVersion[table.size()];
        int itableIndex = 0;
        for (PartialMethod<Klass, Method, Field> m : table) {
            if (m != null) {
                itable[itableIndex] = m.asMethodAccess().getMethodVersion();
            } else {
                itable[itableIndex] = new Method(intfTable[itableIndex].getMethod()).setPoisonPill().getMethodVersion();
            }
            itableIndex++;
        }
        return itable;
    }

    private static Method.MethodVersion[] mirandas(Tables<Klass, Method, Field> tables) {
        List<PartialMethod<Klass, Method, Field>> successfulMirandas = tables.getSuccessfulImplicitInterfaceMethods();
        List<PartialMethod<Klass, Method, Field>> failingMirandas = tables.getFailingImplicitInterfaceMethods();
        if (successfulMirandas == null && failingMirandas == null) {
            return null;
        }
        assert successfulMirandas != null && failingMirandas != null;
        return toEspressoMirandas(successfulMirandas, failingMirandas);
    }

    private static Method.MethodVersion[] toEspressoMirandas(List<PartialMethod<Klass, Method, Field>> successMirandas, List<PartialMethod<Klass, Method, Field>> failingMirandas) {
        Method.MethodVersion[] table = new Method.MethodVersion[successMirandas.size() + failingMirandas.size()];
        int idx = 0;
        for (PartialMethod<Klass, Method, Field> m : successMirandas) {
            table[idx] = m.asMethodAccess().getMethodVersion();
            idx++;
        }
        for (PartialMethod<Klass, Method, Field> m : failingMirandas) {
            Method method = new Method(m.asMethodAccess());
            method.setITableIndex(m.asMethodAccess().getITableIndex());
            table[idx] = method.setPoisonPill().getMethodVersion();
        }
        return table;
    }

    private static class PartialKlass implements PartialType<Klass, Method, Field> {
        private final Symbol<Name> targetName;
        private final ObjectKlass superKlass;
        private final List<Method> parentTable;
        private final List<? extends PartialMethod<Klass, Method, Field>> declaredMethods;
        private final EconomicMap<Klass, List<Method>> interfacesData;

        PartialKlass(Symbol<Name> targetName, ObjectKlass superKlass, ObjectKlass.KlassVersion[] transitiveInterfaces, Method.MethodVersion[] declaredMethods) {
            this.targetName = targetName;
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
            return targetName;
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
