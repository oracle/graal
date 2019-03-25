package com.oracle.truffle.espresso.impl;

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
        final ArrayList<Method> mirandas;

        CreationResult(Method[][] itable, Klass[] interfaceKlassTable) {
            this(itable, interfaceKlassTable, new ArrayList<>());
        }

        CreationResult(Method[][] itable, Klass[] interfaceKlassTable, ArrayList<Method> mirandas) {
            this.interfaceKlassTable = interfaceKlassTable;
            this.itable = itable;
            this.mirandas = mirandas;
        }

        Method[][] getItable() {
            return itable;
        }

        Klass[] getiKlass() {
            return interfaceKlassTable;
        }

        ArrayList<Method> getMirandas() {
            return mirandas;
        }
    }

    private InterfaceTables() {
    }

    /**
     * Constructor for non-interface itable. Copies (and rewrites as needed) the itable of its
     * superklass, then adds its own super-interfaces' itable
     *
     * @param superKlass superKlass of thisKlass.
     * @param superInterfaces All the interfaces thisKlass implements
     * @param thisKlass The Klass wanting to create this itable
     */
    static CreationResult create(ObjectKlass superKlass, ObjectKlass[] superInterfaces, ObjectKlass thisKlass) {
        ArrayList<Method[]> tmpTable;
        ArrayList<Klass> tmpKlass;
        ArrayList<Method> mirandas = new ArrayList<>();
        if (superKlass != null) {
            tmpTable = new ArrayList<>(Arrays.asList(superKlass.getItable()));
            tmpKlass = new ArrayList<>(Arrays.asList(superKlass.getiKlassTable()));
        } else {
            tmpTable = new ArrayList<>();
            tmpKlass = new ArrayList<>();
        }
        // Check for inherited method override.
        for (int n_itable = 0; n_itable < tmpTable.size(); n_itable++) {
            Method[] curItable = tmpTable.get(n_itable);
            for (int n_method = 0; n_method < curItable.length; n_method++) {
                Method im = curItable[n_method];
                Method override = thisKlass.lookupDeclaredMethod(im.getName(), im.getRawSignature());
                if (override != null) {
                    // Interface method override detected, make a copy of the inherited table and
                    // re-fill it.
                    // TODO(garcia) we know the starting index, give it to the constructor, so it
                    // avoids reworking.
                    tmpTable.set(n_itable, InterfaceTable.inherit(curItable, thisKlass));
                    break;
                }
            }
        }
        // Hopefully, a class does not re-implements a superklass' interface (though it works, we
        // will have a second itable for that particular interface)
        for (ObjectKlass interf : superInterfaces) {
            fillSuperInterfaceTables(interf, thisKlass, mirandas, tmpTable, tmpKlass);
        }
        return new CreationResult(tmpTable.toArray(new Method[0][]), tmpKlass.toArray(Klass.EMPTY_ARRAY), mirandas);
    }

    /**
     * Constructor for interface Klass itable Simply uses the declaredMethods array (without
     * copying), then sets each method's itableIndex. It then adds its superInterface itables to its
     * own.
     *
     * @param interfKlass the Interface trying to create its itable
     * @param superInterfaces All interface interfKlass implements
     * @param declaredMethods the declared Methods of interfKlass
     */
    static CreationResult create(Klass interfKlass, ObjectKlass[] superInterfaces, Method[] declaredMethods) {
        ArrayList<Method[]> tmp = new ArrayList<>();
        ArrayList<Klass> tmpKlass = new ArrayList<>();
        tmp.add(InterfaceTable.create(declaredMethods));
        tmpKlass.add(interfKlass);
        for (int i = 0; i < superInterfaces.length; i++) {
            tmp.addAll(Arrays.asList(superInterfaces[i].getItable()));
            tmpKlass.addAll(Arrays.asList(superInterfaces[i].getiKlassTable()));
        }
        return new CreationResult(tmp.toArray(new Method[0][]), tmpKlass.toArray(Klass.EMPTY_ARRAY));
    }

    private static void fillSuperInterfaceTables(ObjectKlass superInterface, ObjectKlass thisKlass, ArrayList<Method> mirandas, ArrayList<Method[]> tmpITable, ArrayList<Klass> tmpKlassTable) {
        Method[][] superTable = superInterface.getItable();
        Klass[] superInterfKlassTable = superInterface.getiKlassTable();
        for (int i = 0; i < superTable.length; i++) {
            tmpITable.add(InterfaceTable.inherit(superTable[i], thisKlass, mirandas));
            tmpKlassTable.add(superInterfKlassTable[i]);
        }
    }

}

final class InterfaceTable {

    static Method[] create(Method[] declaredMethods) {
        int i = 0;
        for (Method m : declaredMethods) {
            m.setITableIndex(i++);
        }
        return declaredMethods;
    }

    /**
     * Given a single interface table, produces a new interface table that contains the
     * implementation of the interface by thisKlass
     *
     * @param interfTable a single interface table
     * @param thisKlass The class implementing this interface
     * @return the interface table for thisKlass.
     */
    static Method[] inherit(Method[] interfTable, Klass thisKlass) {
        Method[] res = Arrays.copyOf(interfTable, interfTable.length);
        for (int i = 0; i < res.length; i++) {
            Method im = res[i];
            Method m = thisKlass.lookupMethod(im.getName(), im.getRawSignature());
            if (m != null) {
                m.setITableIndex(i);
                res[i] = m;
            }
        }
        return res;
    }

    static Method[] inherit(Method[] interfTable, ObjectKlass thisKlass, ArrayList<Method> mirandas) {
        Method[] res = Arrays.copyOf(interfTable, interfTable.length);
        for (int i = 0; i < res.length; i++) {
            Method im = res[i];
            // Lookup does not check inside interfaces declared method. Exploit this to detect
            // mirandas.
            Method m = thisKlass.lookupMethod(im.getName(), im.getRawSignature());
            if (m != null) {
                m.setITableIndex(i);
                res[i] = m;
            } else if (thisKlass.isAbstract()) { // Check for miranda methods
                Method mirandaMethod;
                // We will cheat a bit here. We will artificially add an abstract method declaration
                // for each miranda method in an abstract class.
                // It will later be added to its declared methods.
                // We do NOT want to use the method declared by the interface, as it vtable index
                // would be shared across every implementing class that has a miranda. We thus
                // create a new method for each one af those, for their own vtable index.
                if (im.hasBytecodes()) {
                    mirandaMethod = new Method((ObjectKlass) im.getDeclaringKlass(), im);
                } else {
                    mirandaMethod = new Method(thisKlass, im);
                }
                mirandas.add(mirandaMethod);
            }
        }
        return res;
    }
}