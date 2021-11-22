/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
            return Integer.compare(o1.klass.getId(), o2.klass.getId());
        }
    };

    private static final Entry[][] EMPTY_ENTRY_DUAL_ARRAY = new Entry[0][];
    private static final Method[][] EMPTY_METHOD_DUAL_ARRAY = new Method[0][];

    private final ObjectKlass superKlass;
    private final ObjectKlass[] superInterfaces;
    private final Method[] declaredMethods;
    private final ArrayList<Entry[]> tmpTables = new ArrayList<>();
    private final ArrayList<ObjectKlass> tmpKlassTable = new ArrayList<>();
    private final ArrayList<Method> mirandas = new ArrayList<>();

    private enum Location {
        SUPERVTABLE,
        DECLARED,
        MIRANDAS
    }

    static class CreationResult {
        Entry[][] tables;
        ObjectKlass[] klassTable;
        Method[] mirandas;

        CreationResult(Entry[][] tables, ObjectKlass[] klassTable, Method[] mirandas) {
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
        ObjectKlass[] klassTable;
        Method[] methodtable;

        InterfaceCreationResult(ObjectKlass[] klassTable, Method[] methodtable) {
            this.klassTable = klassTable;
            this.methodtable = methodtable;
        }
    }

    static class TableData {
        ObjectKlass klass;
        Entry[] table;

        TableData(ObjectKlass klass, Entry[] table) {
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

    private InterfaceTables(ObjectKlass superKlass, ObjectKlass[] superInterfaces, Method[] declaredMethods) {
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
    public static InterfaceCreationResult constructInterfaceItable(ObjectKlass thisInterfKlass, Method[] declared) {
        assert thisInterfKlass.isInterface();
        CompilerAsserts.neverPartOfCompilation();
        ArrayList<Method> tmpMethodTable = new ArrayList<>();
        for (Method method : declared) {
            if (!method.isStatic() && !method.isPrivate()) {
                method.setITableIndex(tmpMethodTable.size());
                tmpMethodTable.add(method);
            }
            if (!method.isAbstract() && !method.isStatic()) {
                thisInterfKlass.hasDeclaredDefaultMethods = true;
            }
        }
        Method[] methods = tmpMethodTable.toArray(Method.EMPTY_ARRAY);
        ArrayList<ObjectKlass> tmpKlassTable = new ArrayList<>();
        tmpKlassTable.add(thisInterfKlass);
        for (ObjectKlass interf : thisInterfKlass.getSuperInterfaces()) {
            for (ObjectKlass supInterf : interf.getiKlassTable()) {
                if (canInsert(supInterf, tmpKlassTable)) {
                    tmpKlassTable.add(supInterf);
                }
            }
        }
        ObjectKlass[] sortedInterfaces = tmpKlassTable.toArray(ObjectKlass.EMPTY_ARRAY);
        // Interfaces must be sorted, superinterfaces first.
        // The Klass.ID (class loading counter) can be used, since parent classes/interfaces are
        // always loaded first.
        Arrays.sort(sortedInterfaces, Klass.KLASS_ID_COMPARATOR);
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
    public static CreationResult create(ObjectKlass superKlass, ObjectKlass[] superInterfaces, Method[] declaredMethods) {
        return new InterfaceTables(superKlass, superInterfaces, declaredMethods).create();
    }

    /**
     * Performs second and third step of itable creation.
     * 
     * @param vtable the vtable of the klass for which we are creating an itable
     * @param mirandas the mirandas of the klass for which we are creating an itable
     * @param declaredMethods the declared methods of the klass for which we are creating an itable
     * @param tables The helper table obtained from first step
     * @param iklassTable the interfaces directly and indirectly implemented by thisKlass
     * @return the final itable
     */
    public static Method[][] fixTables(Method[] vtable, Method[] mirandas, Method[] declaredMethods, Entry[][] tables, ObjectKlass[] iklassTable) {
        assert tables.length == iklassTable.length;
        ArrayList<Method[]> tmpTables = new ArrayList<>();

        // Second step
        // Remember here that the interfaces are sorted, most specific at the end.
        for (int i = iklassTable.length - 1; i >= 0; i--) {
            fixVTable(tables[i], vtable, mirandas, declaredMethods, iklassTable[i].getInterfaceMethodsTable());
        }
        // Third step
        for (int tableIndex = 0; tableIndex < tables.length; tableIndex++) {
            Entry[] entries = tables[tableIndex];
            Method[] itable = getITable(entries, vtable, mirandas, declaredMethods);
            tmpTables.add(itable);

            // Update leaf assumptions for super interfaces
            ObjectKlass currInterface = iklassTable[tableIndex];
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
    private static void updateLeafAssumptions(Method[] itable, ObjectKlass currInterface) {
        for (int methodIndex = 0; methodIndex < itable.length; methodIndex++) {
            Method m = itable[methodIndex];
            // This class' itable entry for this method is not the interface's declared method.
            if (m.getDeclaringKlass() != currInterface) {
                Method intfMethod = currInterface.getInterfaceMethodsTable()[methodIndex];
                // sanity checks
                assert intfMethod.getDeclaringKlass() == currInterface;
                assert m.canOverride(intfMethod) && m.getName() == intfMethod.getName() && m.getRawSignature() == intfMethod.getRawSignature();
                if (intfMethod.leafAssumption()) {
                    intfMethod.invalidateLeaf();
                }
            }
        }
    }

    // Actual implementations

    private CreationResult create() {
        for (ObjectKlass interf : superInterfaces) {
            fillMirandas(interf);
            for (ObjectKlass supInterf : interf.getiKlassTable()) {
                fillMirandas(supInterf);
            }
        }
        // At this point, no more mirandas should be created.
        if (superKlass != null) {
            for (ObjectKlass superKlassInterf : superKlass.getiKlassTable()) {
                fillMirandas(superKlassInterf);
            }
        }

        return new CreationResult(tmpTables.toArray(EMPTY_ENTRY_DUAL_ARRAY), tmpKlassTable.toArray(ObjectKlass.EMPTY_ARRAY), mirandas.toArray(Method.EMPTY_ARRAY));
    }

    private void fillMirandas(ObjectKlass interf) {
        if (canInsert(interf, tmpKlassTable)) {
            Method[] interfMethods = interf.getInterfaceMethodsTable();
            Entry[] res = new Entry[interfMethods.length];
            for (int i = 0; i < res.length; i++) {
                Method im = interfMethods[i];
                Symbol<Name> mname = im.getName();
                Symbol<Signature> sig = im.getRawSignature();
                res[i] = lookupLocation(im, mname, sig);
            }
            tmpTables.add(res);
            tmpKlassTable.add(interf);
        }
    }

    private static void fixVTable(Entry[] table, Method[] vtable, Method[] mirandas, Method[] declared, Method[] interfMethods) {
        for (int i = 0; i < table.length; i++) {
            Entry entry = table[i];
            int index = entry.index;
            Method virtualMethod;
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
            if (!virtualMethod.getDeclaringKlass().isInterface()) {
                // Current method is a class method: no need to resolve maximally-specific.
                continue;
            }
            Method interfMethod = interfMethods[i];
            if (interfMethod.identity() == virtualMethod.identity()) {
                continue;
            }
            Method result = resolveMaximallySpecific(virtualMethod, interfMethod);
            if (result != virtualMethod) {
                updateEntry(vtable, mirandas, entry, index, virtualMethod, virtualize(result, virtualMethod.getVTableIndex()));
            }
        }
    }

    private static Method virtualize(Method m, int index) {
        if (m.getVTableIndex() != index) {
            return new Method(m);
        }
        return m;
    }

    private static void updateEntry(Method[] vtable, Method[] mirandas, Entry entry, int index, Method virtualMethod, Method toPut) {
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
                Method newMiranda = new Method(toPut);
                int vtableIndex = virtualMethod.getVTableIndex();
                vtable[vtableIndex] = newMiranda;
                mirandas[index] = newMiranda;
                newMiranda.setVTableIndex(vtableIndex);
                break;
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }

    private static Method[] getITable(Entry[] entries, Method[] vtable, Method[] mirandas, Method[] declared) {
        int pos = 0;
        Method[] res = new Method[entries.length];
        for (Entry entry : entries) {
            switch (entry.loc) {
                case SUPERVTABLE:
                    res[pos] = new Method(vtable[entry.index]);
                    break;
                case DECLARED:
                    res[pos] = new Method(declared[entry.index]);
                    break;
                case MIRANDAS:
                    res[pos] = new Method(mirandas[entry.index]);
                    break;
            }
            res[pos].setITableIndex(pos);
            pos++;
        }
        return res;
    }

    // lookup helpers

    private Entry lookupLocation(Method im, Symbol<Name> mname, Symbol<Signature> sig) {
        Method m = null;
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
        mirandas.add(new Method(im)); // Proxy
        return new Entry(Location.MIRANDAS, mirandas.size() - 1);

    }

    private static int getMethodTableIndex(Method[] table, Method interfMethod, Symbol<Name> mname, Symbol<Signature> sig) {
        for (int i = 0; i < table.length; i++) {
            Method m = table[i];
            if (canOverride(m, interfMethod, m.getContext()) && mname == m.getName() && sig == m.getRawSignature()) {
                return i;
            }
        }
        return -1;
    }

    private int lookupMirandas(Method interfMethod, Symbol<Name> mname, Symbol<Signature> sig) {
        int pos = 0;
        for (Method m : mirandas) {
            if (m.canOverride(interfMethod) && m.getName() == mname && sig == m.getRawSignature()) {
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
    public static Method resolveMaximallySpecific(Method m1, Method m2) {
        Klass k1 = m1.getDeclaringKlass();
        Klass k2 = m2.getDeclaringKlass();
        if (k1.isAssignableFrom(k2)) {
            return m2;
        } else if (k2.isAssignableFrom(k1)) {
            return m1;
        } else {
            boolean b1 = m1.isAbstract();
            boolean b2 = m2.isAbstract();
            if (b1 && b2) {
                return m1;
            }
            if (b1) {
                return m2;
            }
            if (b2) {
                return m1;
            }
            // JVM specs:
            // Can *declare* ambiguous default method (in bytecodes only, javac wouldn't compile
            // it). (5.4.3.3.)
            //
            // But if you try to *use* them, specs dictate to fail. (6.5.invoke{virtual,interface})
            Method m = new Method(m2);
            m.setPoisonPill();
            return m;
        }
    }

    private static boolean canInsert(ObjectKlass interf, ArrayList<ObjectKlass> tmpKlassTable) {
        for (Klass k : tmpKlassTable) {
            if (k == interf) {
                return false;
            }
        }
        return true;
    }
}
