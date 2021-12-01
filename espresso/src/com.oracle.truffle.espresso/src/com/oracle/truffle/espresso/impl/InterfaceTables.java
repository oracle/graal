/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * 3 pass interface table constructor helper:
 *
 * - First pass collects miranda methods and constructs an intermediate itable whose job is to
 * locate where to fetch the needed methods for the second pass.
 *
 * - Second pass is performed after constructing the virtual table (which itself is done after first
 * pass). Its goal is to find and insert in the vtable and the miranda methods the maximally
 * specific methods.
 *
 * - Third pass is performed just after second. Using the now correct vtable and mirandas, perform a
 * simple mapping from the helper table to the final itable.
 */
final class InterfaceTables {

    private static final Comparator<TableData> SORTER = new Comparator<TableData>() {
        @Override
        public int compare(TableData o1, TableData o2) {
            return Integer.compare(o1.klass.getKlass().getId(), o2.klass.getKlass().getId());
        }
    };

    private static final Entry[][] EMPTY_ENTRY_DUAL_ARRAY = new Entry[0][];
    private static final Method.MethodVersion[][] EMPTY_METHOD_DUAL_ARRAY = new Method.MethodVersion[0][];

    private final ObjectKlass superKlass;
    private final ObjectKlass[] superInterfaces;
    private final Method.MethodVersion[] declaredMethods;
    private final ArrayList<Entry[]> tmpTables = new ArrayList<>();
    private final ArrayList<ObjectKlass.KlassVersion> tmpKlassTable = new ArrayList<>();
    private final ArrayList<Method.MethodVersion> mirandas = new ArrayList<>();

    private enum Location {
        SUPERVTABLE,
        DECLARED,
        MIRANDAS
    }

    static class CreationResult {
        Entry[][] tables;
        ObjectKlass.KlassVersion[] klassTable;
        Method.MethodVersion[] mirandas;

        CreationResult(Entry[][] tables, ObjectKlass.KlassVersion[] klassTable, Method.MethodVersion[] mirandas) {
            TableData[] data = new TableData[klassTable.length];
            for (int i = 0; i < data.length; i++) {
                data[i] = new TableData(klassTable[i], tables[i]);
            }
            Arrays.sort(data, SORTER);
            for (int i = 0; i < data.length; i++) {
                tables[i] = data[i].table;
                klassTable[i] = data[i].klass;
            }
            this.tables = tables;
            this.klassTable = klassTable;
            this.mirandas = mirandas;
        }
    }

    static class InterfaceCreationResult {
        ObjectKlass.KlassVersion[] klassTable;
        Method.MethodVersion[] methodtable;

        InterfaceCreationResult(ObjectKlass.KlassVersion[] klassTable, Method.MethodVersion[] methodtable) {
            this.klassTable = klassTable;
            this.methodtable = methodtable;
        }
    }

    static class TableData {
        ObjectKlass.KlassVersion klass;
        Entry[] table;

        TableData(ObjectKlass.KlassVersion klass, Entry[] table) {
            this.klass = klass;
            this.table = table;
        }
    }

    static final class Entry {
        Location loc;
        int index;

        Entry(Location loc, int index) {
            this.loc = loc;
            this.index = index;
        }
    }

    private InterfaceTables(ObjectKlass superKlass, ObjectKlass[] superInterfaces, Method.MethodVersion[] declaredMethods) {
        this.superKlass = superKlass;
        this.superInterfaces = superInterfaces;
        this.declaredMethods = declaredMethods;
    }

    /**
     * Constructs the complete list of interfaces an interface needs to implement. Also initializes
     * itable indexes.
     *
     * @param thisInterfKlass The interface in question
     * @param declared The declared methods of the interface.
     * @return the requested klass array
     */
    public static InterfaceCreationResult constructInterfaceItable(ObjectKlass.KlassVersion thisInterfKlass, Method.MethodVersion[] declared) {
        assert thisInterfKlass.isInterface();
        CompilerAsserts.neverPartOfCompilation();
        ArrayList<Method.MethodVersion> tmpMethodTable = new ArrayList<>();
        for (Method.MethodVersion method : declared) {
            if (!method.isStatic() && !method.isPrivate()) {
                method.setITableIndex(tmpMethodTable.size());
                tmpMethodTable.add(method);
            }
            if (!method.isAbstract() && !method.isStatic()) {
                thisInterfKlass.hasDeclaredDefaultMethods = true;
            }
        }
        Method.MethodVersion[] methods = tmpMethodTable.toArray(Method.EMPTY_VERSION_ARRAY);
        ArrayList<ObjectKlass.KlassVersion> tmpKlassTable = new ArrayList<>();
        tmpKlassTable.add(thisInterfKlass);
        for (ObjectKlass interf : thisInterfKlass.getKlass().getSuperInterfaces()) {
            for (ObjectKlass.KlassVersion supInterf : interf.getVersionIKlassTable()) {
                if (canInsert(supInterf, tmpKlassTable)) {
                    tmpKlassTable.add(supInterf);
                }
            }
        }
        ObjectKlass.KlassVersion[] sortedInterfaces = tmpKlassTable.toArray(ObjectKlass.EMPTY_KLASSVERSION_ARRAY);
        // Interfaces must be sorted, superinterfaces first.
        // The Klass.ID (class loading counter) can be used, since parent classes/interfaces are
        // always loaded first.
        Arrays.sort(sortedInterfaces, Klass.KLASS_VERSION_ID_COMPARATOR);
        return new InterfaceCreationResult(sortedInterfaces, methods);
    }

    // @formatter:off
    // checkstyle: stop
    /**
     * Performs the first step of itable creation.
     *
     * @param superKlass the super class of this Klass
     * @param superInterfaces the superInterfaces of thisKlass
     * @return a 3-uple containing: <p>
     *      - An intermediate helper for the itable.
     *        Each entry of the helper table contains information of where to find the method that will be put in its place<p>
     *      - An array containing all directly and indirectly implemented interfaces<p>
     *      - An array of implicitly declared methods (aka, mirandas). This most notably contains default methods.<p>
     */
    // checkstyle: resume
    // @formatter:on
    public static CreationResult create(ObjectKlass superKlass, ObjectKlass[] superInterfaces, Method.MethodVersion[] declaredMethods) {
        return new InterfaceTables(superKlass, superInterfaces, declaredMethods).create();
    }

    /**
     * Performs second and third step of itable creation.
     *
     * @param self the klass for which we are creating an itable
     * @param vtable the vtable of the klass for which we are creating an itable
     * @param mirandas the mirandas of the klass for which we are creating an itable
     * @param declaredMethods the declared methods of the klass for which we are creating an itable
     * @param tables The helper table obtained from first step
     * @param iklassTable the interfaces directly and indirectly implemented by thisKlass
     * @return the final itable
     */
    public static Method.MethodVersion[][] fixTables(ObjectKlass.KlassVersion self, Method.MethodVersion[] vtable, Method.MethodVersion[] mirandas, Method.MethodVersion[] declaredMethods,
                    Entry[][] tables, ObjectKlass.KlassVersion[] iklassTable) {
        assert tables.length == iklassTable.length;
        ArrayList<Method.MethodVersion[]> tmpTables = new ArrayList<>();

        // Second step
        // Remember here that the interfaces are sorted, most specific at the end.
        for (int i = iklassTable.length - 1; i >= 0; i--) {
            fixVTable(self, tables[i], vtable, mirandas, declaredMethods, iklassTable[i].getKlass().getInterfaceMethodsTable());
        }
        // Third step
        for (int tableIndex = 0; tableIndex < tables.length; tableIndex++) {
            Entry[] entries = tables[tableIndex];
            Method.MethodVersion[] itable = getITable(entries, vtable, mirandas, declaredMethods);
            tmpTables.add(itable);

            // Update leaf assumptions for super interfaces
            ObjectKlass currInterface = iklassTable[tableIndex].getKlass();
            updateLeafAssumptions(itable, currInterface);
        }
        return tmpTables.toArray(EMPTY_METHOD_DUAL_ARRAY);
    }

    /**
     * Note: Leaf assumptions are not invalidated on creation of an interface. This means that in
     * the following example:
     * 
     * <pre>
     * interface A {
     *     default void m() {
     *     }
     * }
     * 
     * interface B extends A {
     *     default void m() {
     *     }
     * }
     * </pre>
     * 
     * Unless a concrete class that implements B is loaded, the leaf assumption for A.m() will not
     * be invalidated.
     */
    private static void updateLeafAssumptions(Method.MethodVersion[] itable, ObjectKlass currInterface) {
        for (int methodIndex = 0; methodIndex < itable.length; methodIndex++) {
            Method.MethodVersion m = itable[methodIndex];
            // This class' itable entry for this method is not the interface's declared method.
            if (m.getDeclaringKlassRef() != currInterface) {
                Method.MethodVersion intfMethod = currInterface.getInterfaceMethodsTable()[methodIndex];
                // sanity checks
                assert intfMethod.getDeclaringKlassRef() == currInterface;
                assert m.getMethod().canOverride(intfMethod.getMethod()) && m.getName() == intfMethod.getName() && m.getRawSignature() == intfMethod.getRawSignature();
                if (intfMethod.leafAssumption()) {
                    intfMethod.invalidateLeaf();
                }
            }
        }
    }

    // Actual implementations

    private CreationResult create() {
        for (ObjectKlass interf : superInterfaces) {
            fillMirandas(interf.getKlassVersion());
            for (ObjectKlass.KlassVersion supInterf : interf.getiKlassTable()) {
                fillMirandas(supInterf);
            }
        }
        // At this point, no more mirandas should be created.
        if (superKlass != null) {
            for (ObjectKlass.KlassVersion superKlassInterf : superKlass.getVersionIKlassTable()) {
                fillMirandas(superKlassInterf);
            }
        }

        return new CreationResult(tmpTables.toArray(EMPTY_ENTRY_DUAL_ARRAY), tmpKlassTable.toArray(ObjectKlass.EMPTY_KLASSVERSION_ARRAY), mirandas.toArray(Method.EMPTY_VERSION_ARRAY));
    }

    private void fillMirandas(ObjectKlass.KlassVersion interf) {
        if (canInsert(interf, tmpKlassTable)) {
            Method.MethodVersion[] interfMethods = interf.getKlass().getInterfaceMethodsTable();
            Entry[] res = new Entry[interfMethods.length];
            for (int i = 0; i < res.length; i++) {
                Method im = interfMethods[i].getMethod();
                Symbol<Name> mname = im.getName();
                Symbol<Signature> sig = im.getRawSignature();
                res[i] = lookupLocation(im, mname, sig);
            }
            tmpTables.add(res);
            tmpKlassTable.add(interf);
        }
    }

    private static void fixVTable(ObjectKlass.KlassVersion self, Entry[] table, Method.MethodVersion[] vtable, Method.MethodVersion[] mirandas, Method.MethodVersion[] declared,
                    Method.MethodVersion[] interfMethods) {
        for (int i = 0; i < table.length; i++) {
            Entry entry = table[i];
            int index = entry.index;
            Method.MethodVersion virtualMethod;
            switch (entry.loc) {
                case SUPERVTABLE:
                    virtualMethod = vtable[index];
                    break;
                case MIRANDAS:
                    virtualMethod = mirandas[index];
                    break;
                case DECLARED:
                    virtualMethod = declared[index];
                    break;
                default:
                    throw EspressoError.shouldNotReachHere();
            }
            if (!virtualMethod.getKlassVersion().isInterface()) {
                // Current method is a class method: no need to resolve maximally-specific.
                continue;
            }
            Method.MethodVersion interfMethod = interfMethods[i];
            if (interfMethod.getMethod().identity() == virtualMethod.getMethod().identity()) {
                continue;
            }
            Method.MethodVersion result = resolveMaximallySpecific(virtualMethod.getMethod(), interfMethod.getMethod());
            if (result.getMethod() != virtualMethod.getMethod()) {
                updateEntry(self, vtable, mirandas, entry, index, virtualMethod, virtualize(result.getMethod(), virtualMethod.getVTableIndex()));
            }
        }
    }

    private static Method.MethodVersion virtualize(Method m, int index) {
        if (m.getVTableIndex() != index) {
            return new Method(m).getMethodVersion();
        }
        return m.getMethodVersion();
    }

    private static void updateEntry(ObjectKlass.KlassVersion self, Method.MethodVersion[] vtable, Method.MethodVersion[] mirandas, Entry entry, int index, Method.MethodVersion virtualMethod,
                    Method.MethodVersion toPut) {
        switch (entry.loc) {
            case SUPERVTABLE:
                vtable[index] = toPut;
                toPut.setVTableIndex(index);
                break;
            case DECLARED:
                vtable[virtualMethod.getVTableIndex()] = toPut;
                toPut.setVTableIndex(virtualMethod.getVTableIndex());
                break;
            case MIRANDAS:
                Method newMiranda;
                if (toPut.getMethod().getDeclaringKlass() == self.getKlass()) {
                    newMiranda = new Method(toPut.getMethod());
                } else {
                    newMiranda = new Method(toPut.getMethod());
                }
                int vtableIndex = virtualMethod.getVTableIndex();
                vtable[vtableIndex] = newMiranda.getMethodVersion();
                mirandas[index] = newMiranda.getMethodVersion();
                newMiranda.setVTableIndex(vtableIndex);
                break;
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }

    private static Method.MethodVersion[] getITable(Entry[] entries, Method.MethodVersion[] vtable, Method.MethodVersion[] mirandas, Method.MethodVersion[] declared) {
        int pos = 0;
        Method.MethodVersion[] res = new Method.MethodVersion[entries.length];
        for (Entry entry : entries) {
            switch (entry.loc) {
                case SUPERVTABLE:
                    Method.MethodVersion m = vtable[entry.index];
                    res[pos] = new Method(m.getMethod()).getMethodVersion();
                    break;
                case DECLARED:
                    m = declared[entry.index];
                    res[pos] = new Method(m.getMethod()).getMethodVersion();
                    break;
                case MIRANDAS:
                    m = mirandas[entry.index];
                    res[pos] = new Method(m.getMethod()).getMethodVersion();
                    break;
            }
            res[pos].setITableIndex(pos);
            pos++;
        }
        return res;
    }

    // lookup helpers

    private Entry lookupLocation(Method im, Symbol<Name> mname, Symbol<Signature> sig) {
        Method m;
        int index = -1;
        // Look at VTable first. Even if this klass declares the method, it will be put in the same
        // place.
        if (superKlass != null) {
            index = getMethodTableIndex(superKlass.getVTable(), im, mname, sig);
        }
        if (index != -1) {
            m = superKlass.vtableLookup(index);
            assert index == m.getVTableIndex();
            return new Entry(Location.SUPERVTABLE, index);
        }
        index = getMethodTableIndex(declaredMethods, im, mname, sig);
        if (index != -1) {
            return new Entry(Location.DECLARED, index);
        }
        index = lookupMirandas(im, mname, sig);
        if (index != -1) {
            return new Entry(Location.MIRANDAS, index);
        }
        // This case should only happen during exploration of direct
        // superInterfaces and their interfaces
        mirandas.add(new Method(im).getMethodVersion()); // Proxy
        return new Entry(Location.MIRANDAS, mirandas.size() - 1);

    }

    private static int getMethodTableIndex(Method.MethodVersion[] table, Method interfMethod, Symbol<Name> mname, Symbol<Signature> sig) {
        for (int i = 0; i < table.length; i++) {
            Method.MethodVersion m = table[i];
            if (canOverride(m.getMethod(), interfMethod, m.getMethod().getContext()) && mname == m.getName() && sig == m.getRawSignature()) {
                return i;
            }
        }
        return -1;
    }

    private int lookupMirandas(Method interfMethod, Symbol<Name> mname, Symbol<Signature> sig) {
        int pos = 0;
        for (Method.MethodVersion m : mirandas) {
            if (m.getMethod().canOverride(interfMethod) && m.getName() == mname && sig == m.getRawSignature()) {
                return pos;
            }
            pos++;
        }
        return -1;
    }

    private static boolean canOverride(Method m, Method im, EspressoContext context) {
        // Interface method selection in Java 8 can select private methods.
        // In Java 11, the VM checks for actual overriding.
        return !m.isStatic() && (context.getJavaVersion().java8OrEarlier() || m.canOverride(im));
    }

    // helper checks

    /**
     * Returns the maximally specific method between the two given methods. If they are both
     * maximally-specific, returns a proxy of the second, to which a poison pill has been set.
     * <p>
     * Determining maximally specific method works as follow:
     * <li>If both methods are abstract, return any of the two.
     * <li>If exactly one is non-abstract, return it.
     * <li>If both are non-abstract, check if one of the declaring class subclasses the other. If
     * that is the case, return the method that is lower in the hierarchy. Otherwise, return a
     * freshly spawned proxy method pointing to either of them, which is set to fail on invocation.
     */

    public static Method.MethodVersion resolveMaximallySpecific(Method m1, Method m2) {
        ObjectKlass k1 = m1.getDeclaringKlass();
        ObjectKlass k2 = m2.getDeclaringKlass();
        if (k1.isAssignableFrom(k2)) {
            return m2.getMethodVersion();
        } else if (k2.isAssignableFrom(k1)) {
            return m1.getMethodVersion();
        } else {
            boolean b1 = m1.isAbstract();
            boolean b2 = m2.isAbstract();
            if (b1 && b2) {
                return m1.getMethodVersion();
            }
            if (b1) {
                return m2.getMethodVersion();
            }
            if (b2) {
                return m1.getMethodVersion();
            }
            // JVM specs:
            // Can *declare* ambiguous default method (in bytecodes only, javac wouldn't compile
            // it). (5.4.3.3.)
            //
            // But if you try to *use* them, specs dictate to fail. (6.5.invoke{virtual,interface})
            Method m;
            m = new Method(m2);
            m.setPoisonPill();
            return m.getMethodVersion();
        }
    }

    private static boolean canInsert(ObjectKlass.KlassVersion interf, ArrayList<ObjectKlass.KlassVersion> tmpKlassTable) {
        for (ObjectKlass.KlassVersion k : tmpKlassTable) {
            if (k.getKlass() == interf.getKlass()) {
                return false;
            }
        }
        return true;
    }
}
