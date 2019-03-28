package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.descriptors.Symbol;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Helper to create itables in ObjectKlass.
 */
class InterfaceTables {
    /**
     * Pretty much the same as a 3-tuple. Used to wrap the results of ITable creation.
     */
    static class CreationResult {
        final Method[][] itable;
        final Klass[] interfaceKlassTable;

        CreationResult(Method[][] itable, Klass[] interfaceKlassTable) {
            this.interfaceKlassTable = interfaceKlassTable;
            this.itable = itable;
        }

        Method[][] getItable() {
            return itable;
        }

        Klass[] getiKlass() {
            return interfaceKlassTable;
        }
    }

    private InterfaceTables() {
    }

    /**
     * Constructor for concrete/abstract klasses' itable. Copies (and rewrites as needed) the itable
     * of its superklass, then adds its own super-interfaces' itable
     *
     * @param superKlass superKlass of thisKlass.
     * @param superInterfaces All the interfaces thisKlass implements
     * @param thisKlass The Klass wanting to create this itable
     */
    static CreationResult create(ObjectKlass superKlass, ObjectKlass[] superInterfaces, ObjectKlass thisKlass) {
        ArrayList<Method[]> tmpTables = new ArrayList<>();
        ArrayList<Klass> tmpKlassTable = new ArrayList<>();
        ArrayList<Miranda> mirandas = new ArrayList<>();
        // Hopefully, a class does not re-implements a superklass' interface.
        // Unfortunately, the Java world isn't that forgiving.
        // A check will be done later to prevents dupe tables.
        for (ObjectKlass interf : superInterfaces) {
            fillSuperInterfaceTables(interf, thisKlass, mirandas, tmpTables, tmpKlassTable);
        }
        // Adds all the miranda methods to thisKlass' declared methods
        thisKlass.setMirandas(mirandas);
        if (superKlass == null) {
            fixMirandas(mirandas, tmpTables);
            return new CreationResult(tmpTables.toArray(new Method[0][]), tmpKlassTable.toArray(Klass.EMPTY_ARRAY));
        }
        // Inherit superklass' interfaces
        Method[][] superKlassITable = superKlass.getItable();
        // Check for inherited method override.
        // At this point, mirandas are in thisKlass' declared methods
        for (int n_itable = 0; n_itable < superKlassITable.length; n_itable++) {
            // Prevent duplicate tables
            int dupePos = dupePos(superKlass.getiKlassTable()[n_itable], tmpKlassTable);
            if (dupePos == -1) {
                Method[] curItable = lookupOverride(superKlassITable[n_itable], thisKlass, mirandas);
                tmpTables.add(curItable);
                tmpKlassTable.add(superKlass.getiKlassTable()[n_itable]);
            } else {
                mergeTables(tmpTables.get(dupePos), superKlassITable[n_itable], mirandas);
            }
        }
        fixMirandas(mirandas, tmpTables);
        return new CreationResult(tmpTables.toArray(new Method[0][]), tmpKlassTable.toArray(Klass.EMPTY_ARRAY));
    }

    private static void mergeTables(Method[] currentTable, Method[] toMergeIn, ArrayList<Miranda> mirandas) {
        assert (currentTable.length == toMergeIn.length);
        for(int i = 0; i < currentTable.length; i++) {
            Method m1 = currentTable[i];
            Method m2 = toMergeIn[i];
            assert (m1.getName() == m2.getName());
            if (checkDefaultConflict(m1, m2)) {
                Method result = resolveMaximallySpecific(m1, m2);
                if (result == m2) {
                    currentTable[i] = result;
                    lookupAndSetFixMirandas(m2, mirandas);
                }
            }
        }
    }

    private static void lookupAndSetFixMirandas(Method method, ArrayList<Miranda> mirandas) {
        Symbol<Symbol.Name> methodName = method.getName();
        Symbol<Symbol.Signature> methodSig = method.getRawSignature();
        for (Miranda miranda : mirandas) {
            Method m = miranda.method;
            if (m.getName() == methodName && m.getRawSignature() == methodSig) {
                miranda.method = method;
                miranda.setFix(true);
            }
        }
    }

    // TODO(garcia) this.
    private static Method resolveMaximallySpecific(Method m1, Method m2) {
        return (m1.getDeclaringKlass().isAssignableFrom(m2.getDeclaringKlass())) ? m2 : m1;
    }

    /**
     * Constructor for building template itable attached to an interface Klass. Simply uses the
     * declaredMethods array (without copying), then sets each method's itableIndex. It then adds
     * its superInterface itables to its own.
     *
     * @param interfKlass the Interface trying to create its itable
     * @param superInterfaces All interface interfKlass implements
     * @param declaredMethods the declared Methods of interfKlass
     */
    static CreationResult create(ObjectKlass interfKlass, ObjectKlass[] superInterfaces, Method[] declaredMethods) {
        ArrayList<Method[]> tmpTables = new ArrayList<>();
        ArrayList<Klass> tmpKlass = new ArrayList<>();
        tmpTables.add(createBaseITable(declaredMethods));
        tmpKlass.add(interfKlass);
        for (ObjectKlass superinterf : superInterfaces) {
            int pos = 0;
            for (Klass curIKlass: superinterf.getiKlassTable()) {
                int tableDupePos = dupePos(curIKlass, tmpKlass);
                if (tableDupePos == -1) {
                    tmpTables.add(lookupOverride(superinterf.getItable()[pos], interfKlass, null));
                    tmpKlass.add(curIKlass);
                }
                // We know our table is more specific than our superinterfaces'.
                pos++;
            }
        }
        return new CreationResult(tmpTables.toArray(new Method[0][]), tmpKlass.toArray(Klass.EMPTY_ARRAY));
    }

    private static int dupePos(Klass interfKlass, ArrayList<Klass> tmpKlass) {
        int pos = 0;
        for (Klass klass : tmpKlass) {
            if (interfKlass == klass) {
                return pos;
            }
            pos++;
        }
        return -1;
    }

    // Should be called before copying superInterface.
    private static void fillSuperInterfaceTables(ObjectKlass superInterface, ObjectKlass thisKlass, ArrayList<Miranda> mirandas, ArrayList<Method[]> tmpITable, ArrayList<Klass> tmpKlassTable) {
        Method[][] superTable = superInterface.getItable();
        Klass[] superInterfKlassTable = superInterface.getiKlassTable();
        for (int i = 0; i < superTable.length; i++) {
            tmpITable.add(inherit(superTable[i], thisKlass, mirandas, i));
            tmpKlassTable.add(superInterfKlassTable[i]);
        }
    }

    private static Method[] createBaseITable(Method[] declaredMethods) {
        int i = 0;
        for (Method m : declaredMethods) {
            m.setITableIndex(i++);
        }
        return declaredMethods;
    }

    private static Method[] lookupOverride(Method[] curItable, ObjectKlass thisKlass, ArrayList<Miranda> mirandas) {
        for (int i = 0; i < curItable.length; i++) {
            Method im = curItable[i];
            int overridePos = lookupIndexDeclaredMethod(im, thisKlass.getDeclaredMethods());
            if (overridePos != -1) {
                Method override = thisKlass.getDeclaredMethods()[overridePos];
                if (checkDefaultConflict(override, im)) {
                    Method result = resolveMaximallySpecific(override, im);
                    if (result == im) {
                        lookupAndSetFixMirandas(im, mirandas);
                        thisKlass.getDeclaredMethods()[overridePos] = im;
                    } else {
                        return inherit(curItable, thisKlass, i, new Method(override), mirandas);
                    }
                } else {
                    // Interface method override detected, make a copy of the inherited table and
                    // re-fill it. Pass along info to avoid duplicate lookups
                    return inherit(curItable, thisKlass, i, new Method(override), mirandas);
                }
            }
        }
        return curItable;
    }

    private static int lookupIndexDeclaredMethod(Method method, Method[] declaredMethods) {
        Symbol<Symbol.Name> name = method.getName();
        Symbol<Symbol.Signature> sig = method.getRawSignature();
        for (int i = 0; i < declaredMethods.length; i++) {
            Method toCompare = declaredMethods[i];
            if (toCompare.getName() == name && toCompare.getRawSignature() == sig) {
                return i;
            }
        }
        return -1;
    }

    private static boolean checkDefaultConflict(Method m1, Method m2) {
        return m1.getDeclaringKlass() != m2.getDeclaringKlass() && m1.isDefault() && m2.isDefault();
    }

    /**
     * Given a single interface table, produces a new interface table that contains the
     * implementation of the interface by thisKlass
     *
     * @param interfTable a single interface table
     * @param thisKlass The class implementing this interface
     * @return the interface table for thisKlass.
     */
    private static Method[] inherit(Method[] interfTable, Klass thisKlass, int start, Method override, ArrayList<Miranda> mirandas) {
        Method[] res = Arrays.copyOf(interfTable, interfTable.length);
        res[start] = override;
        override.setITableIndex(start);

        for (int i = start + 1; i < res.length; i++) {
            Method im = res[i];
            int overridePos = lookupIndexDeclaredMethod(im, thisKlass.getDeclaredMethods());
            if (overridePos != -1) {
                Method m = thisKlass.getDeclaredMethods()[overridePos];
                if (checkDefaultConflict(m, im)) {
                    Method result = resolveMaximallySpecific(m, im);
                    if (result == im) {
                        lookupAndSetFixMirandas(im, mirandas);
                        thisKlass.getDeclaredMethods()[overridePos] = im;
                    } else {
                        Method proxy = new Method(m);
                        proxy.setITableIndex(i);
                        res[i] = proxy;
                    }
                } else {
                    Method proxy = new Method(m);
                    proxy.setITableIndex(i);
                    res[i] = proxy;
                }
            }
        }
        return res;
    }

    private static Method[] inherit(Method[] interfTable, ObjectKlass thisKlass, ArrayList<Miranda> mirandas, int n_itable) {
        Method[] res = Arrays.copyOf(interfTable, interfTable.length);
        for (int i = 0; i < res.length; i++) {
            Method im = res[i];
            // Lookup does not check inside interfaces' declared method. Exploit this to detect
            // mirandas.
            Method m = thisKlass.lookupMethod(im.getName(), im.getRawSignature());
//            if (m != null && (m.hasCode() || thisKlass.isAbstract())) {
            if (canSetProxy(m, im, thisKlass)) {
                Method proxy = new Method(m);
                proxy.setITableIndex(i);
                res[i] = proxy;
            } else {
                // thisKlass does not implement the interface method: check why.
                //
                // Check for miranda methods
                // We will cheat a bit here. We will artificially add an abstract method declaration
                // for each miranda method in a class.
                // It will later be added to its declared methods.
                // We do NOT want to use the method declared by the interface, as its vtable index
                // would be shared across every implementing class that has a miranda. We thus
                // create a new method for each one of those, for their own vtable index.
                Miranda mirandaMethod = lookupMirandaWithOverride(im, mirandas, n_itable, i);
                if (mirandaMethod == null) {
                    // Yet unseen Miranda method
                    if (im.hasCode()) {
                        // This was an unseen default method
                        mirandaMethod = new Miranda(new Method(im), n_itable, i, false);
                    } else {
                        // This is a *still* unimplemented method. Further searching could prove us
                        // wrong.
                        mirandaMethod = new Miranda(im, n_itable, i, true);
                    }
                    mirandas.add(mirandaMethod);
                } else {
                    // We already have seen a default implementation. Use it.
                    res[i] = new Method(mirandaMethod.method);
                    res[i].setITableIndex(i);
                }
            }
        }
        return res;
    }

    private static boolean canSetProxy(Method m, Method im, ObjectKlass thisKlass) {
        if (m == null) {
            return false;
        }
        if (m.isDefault() && im.isDefault()) {
            return false;
        }
        return (m.hasCode() || thisKlass.isAbstract());
    }

    private static Miranda lookupMirandaWithOverride(Method im, ArrayList<Miranda> mirandas, int itable, int index) {
        Symbol<Symbol.Name> methodName = im.getName();
        Symbol<Symbol.Signature> methodSig = im.getRawSignature();
        for (Miranda miranda : mirandas) {
            Method m = miranda.method;
            if (m.getName() == methodName && m.getRawSignature() == methodSig) {
                miranda.push(itable, index);
                // We will be linking to this method for further inquiries
                if (im.hasCode() && !m.hasCode()) {
                    miranda.method = im;
                    return miranda;
                } else if (m.hasCode()) {
                    return miranda;
                } else {
                    return null;
                }
            }
        }
        return null;
    }

    // Fix yet unimplemented methods that have found a default one in a later interface
    private static void fixMirandas(ArrayList<Miranda> mirandas, ArrayList<Method[]> tmpITTable) {
        for (Miranda miranda : mirandas) {
            if (miranda.toFix && miranda.method.hasCode()) {
                for (int i = 0; i < miranda.length(); i++) {
                    // When called, this is false only for non-default interface methods in
                    // abstract klasses that does not provide an implementation.
                    Method toPut = new Method(miranda.method);
                    toPut.setITableIndex(miranda.itablesIndex.get(i));
                    tmpITTable.get(miranda.n_itables.get(i))[miranda.itablesIndex.get(i)] = new Method(miranda.method);
                }
                miranda.setFix(false);
            }
        }
    }

    static class Miranda {
        Method method;
        final ArrayList<Integer> n_itables;
        final ArrayList<Integer> itablesIndex;
        boolean toFix;

        Miranda(Method method, int n_itable, int itableIndex, boolean toFix) {
            this.method = method;
            this.n_itables = new ArrayList<>();
            this.itablesIndex = new ArrayList<>();
            n_itables.add(n_itable);
            itablesIndex.add(itableIndex);
            this.toFix = toFix;
        }

        void push(int itable, int index) {
            n_itables.add(itable);
            itablesIndex.add(index);
        }

        int length() {
            return n_itables.size();
        }

        void setFix(boolean toFix) {
            this.toFix = toFix;
        }
    }
}