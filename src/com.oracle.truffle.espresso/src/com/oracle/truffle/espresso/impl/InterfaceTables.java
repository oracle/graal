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
        fixMirandas(mirandas, tmpTables);
        if (superKlass == null) {
            return new CreationResult(tmpTables.toArray(new Method[0][]), tmpKlassTable.toArray(Klass.EMPTY_ARRAY));
        }
        // Inherit superklass' interfaces
        Method[][] superKlassITable = superKlass.getItable();
        // Check for inherited method override.
        // At this point, mirandas are in thisKlass' declared methods
        for (int n_itable = 0; n_itable < superKlass.getiKlassTable().length; n_itable++) {
            // Prevent duplicate tables
            if (!isPresent(superKlass.getiKlassTable()[n_itable], tmpKlassTable)) {
                Method[] curItable = lookupOverride(superKlassITable[n_itable], thisKlass);
                tmpTables.add(curItable);
                tmpKlassTable.add(superKlass.getiKlassTable()[n_itable]);
            }
        }
        return new CreationResult(tmpTables.toArray(new Method[0][]), tmpKlassTable.toArray(Klass.EMPTY_ARRAY));
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
    static CreationResult create(Klass interfKlass, ObjectKlass[] superInterfaces, Method[] declaredMethods) {
        ArrayList<Method[]> tmp = new ArrayList<>();
        ArrayList<Klass> tmpKlass = new ArrayList<>();
        tmp.add(createBaseITable(declaredMethods));
        tmpKlass.add(interfKlass);
        for (ObjectKlass superinterf : superInterfaces) {
            tmp.addAll(Arrays.asList(superinterf.getItable()));
            tmpKlass.addAll(Arrays.asList(superinterf.getiKlassTable()));
        }
        return new CreationResult(tmp.toArray(new Method[0][]), tmpKlass.toArray(Klass.EMPTY_ARRAY));
    }

    private static boolean isPresent(Klass interfKlass, ArrayList<Klass> tmpKlass) {
        for (Klass klass : tmpKlass) {
            if (interfKlass == klass) {
                return true;
            }
        }
        return false;
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

    private static Method[] lookupOverride(Method[] curItable, ObjectKlass thisKlass) {
        for (int i = 0; i < curItable.length; i++) {
            Method im = curItable[i];
            Method override = thisKlass.lookupDeclaredMethod(im.getName(), im.getRawSignature());
            if (override != null) {
                // Interface method override detected, make a copy of the inherited table and
                // re-fill it. Pass along info to avoid duplicate lookups
                return inherit(curItable, thisKlass, i, override);
            }
        }
        return curItable;
    }

    /**
     * Given a single interface table, produces a new interface table that contains the
     * implementation of the interface by thisKlass
     *
     * @param interfTable a single interface table
     * @param thisKlass The class implementing this interface
     * @return the interface table for thisKlass.
     */
    private static Method[] inherit(Method[] interfTable, Klass thisKlass, int start, Method override) {
        Method[] res = Arrays.copyOf(interfTable, interfTable.length);
        res[start] = override;
        override.setITableIndex(start);
        for (int i = start + 1; i < res.length; i++) {
            Method im = res[i];
            Method m = thisKlass.lookupDeclaredMethod(im.getName(), im.getRawSignature());
            if (m != null) {
                Method proxy = new Method(m);
                proxy.setITableIndex(i);
                res[i] = proxy;
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
            if (m != null && (m.hasCode() || thisKlass.isAbstract())) {
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
                        mirandaMethod = new Miranda(new Method(im));
                    } else {
                        // This is a *still* unimplemented method. Further searching could prove us
                        // wrong.
                        mirandaMethod = new Miranda(im, n_itable, i);
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

    private static Miranda lookupMirandaWithOverride(Method im, ArrayList<Miranda> mirandas, int itable, int index) {
        Symbol<Symbol.Name> methodName = im.getName();
        Symbol<Symbol.Signature> methodSig = im.getRawSignature();
        for (Miranda miranda : mirandas) {
            Method m = miranda.method;
            if (m.getName() == methodName && m.getRawSignature() == methodSig) {
                // We will be linking to this method for further inquiries
                if (im.hasCode() && !m.hasCode()) {
                    // mirandas.set(pos, new Miranda(im)); // Doesn't need fixing
                    miranda.method = im;
                    return miranda;
                } else if (m.hasCode()) {
                    return miranda;
                } else {
                    miranda.push(itable, index);
                    return null;
                }
            }
        }
        return null;
    }

    // Fix yet unimplemented methods that have found a default one in a later interface
    private static void fixMirandas(ArrayList<Miranda> mirandas, ArrayList<Method[]> tmpITTable) {
        for (Miranda miranda : mirandas) {
            if (miranda.toFix) {
                for (int i = 0; i < miranda.length(); i++) {
                    if (miranda.method.hasCode()) {
                        // When called, this is false only for non-default interface methods in
                        // abstract klasses that does not provide an implementation.
                        Method toPut = new Method(miranda.method);
                        toPut.setITableIndex(miranda.itablesIndex.get(i));
                        tmpITTable.get(miranda.n_itables.get(i))[miranda.itablesIndex.get(i)] = new Method(miranda.method);
                    }
                }
            }
        }
    }

    public static class Miranda {
        Method method;
        final ArrayList<Integer> n_itables;
        final ArrayList<Integer> itablesIndex;
        final boolean toFix;

        Miranda(Method method) {
            this.method = method;
            this.n_itables = null;
            this.itablesIndex = null;
            this.toFix = false;
        }

        Miranda(Method method, int n_itable, int itableIndex) {
            this.method = method;
            this.n_itables = new ArrayList<>();
            this.itablesIndex = new ArrayList<>();
            n_itables.add(n_itable);
            itablesIndex.add(itableIndex);
            this.toFix = true;
        }

        void push(int itable, int index) {
            n_itables.add(itable);
            itablesIndex.add(index);
        }

        int length() {
            return n_itables.size();
        }
    }
}