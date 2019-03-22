package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import java.util.ArrayList;
import java.util.Arrays;

public class InterfaceTables {
    @CompilationFinal(dimensions = 1) InterfaceTable[] itables;

    /**
     * Constructor for non-interface itable. Copies (and rewrites as needed) the itable of its
     * superklass, then adds its own interfaces' itable
     *
     * @param superKlass superKlass of thisKlass.
     * @param superInterfaces All the interfaces thisKlass implements
     * @param thisKlass The Klass wanting to create this itable
     */
    InterfaceTables(ObjectKlass superKlass, ObjectKlass[] superInterfaces, Klass thisKlass) {
        ArrayList<InterfaceTable> tmp;
        if (superKlass != null) {
            tmp = new ArrayList<>(Arrays.asList(superKlass.getItable().itables));
        } else {
            tmp = new ArrayList<>();
        }
        // Check for inherited method override.
        for (int n_itable = 0; n_itable < tmp.size(); n_itable++) {
            InterfaceTable curItable = tmp.get(n_itable);
            for (int n_method = 0; n_method < curItable.table.length; n_method++) {
                Method im = curItable.table[n_method];
                Method override = thisKlass.lookupDeclaredMethod(im.getName(), im.getRawSignature());
                if (override != null) {
                    // Interface method override detected, make a copy of the inherited table and
                    // re-fill it.
                    // TODO(garcia) we know the starting index, give it to the constructor, so it
                    // avoids reworking.
                    tmp.set(n_itable, new InterfaceTable(curItable, thisKlass));
                    break;
                }
            }
        }
        // Hopefully, a class does not re-implements a superklass' interface (though it works, we
        // will have a second itable for that particular interface)
        for (ObjectKlass interf : superInterfaces) {
            tmp.addAll(getSuperInterfaceTables(interf, thisKlass));
        }
        this.itables = tmp.toArray(new InterfaceTable[0]);
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
    InterfaceTables(Klass interfKlass, ObjectKlass[] superInterfaces, Method[] declaredMethods) {
        ArrayList<InterfaceTable> tmp = new ArrayList<>();
        tmp.add(new InterfaceTable(interfKlass, declaredMethods));
        for (ObjectKlass interf : superInterfaces) {
            tmp.addAll(Arrays.asList(interf.getItable().itables));
        }
        this.itables = tmp.toArray(new InterfaceTable[0]);
    }

    public Method lookupMethod(Klass interfKlass, int index) {
        for (InterfaceTable itable : itables) {
            if (itable.getKlass() == interfKlass) {
                return itable.lookupMethod(interfKlass, index);
            }
        }
        return null;
    }

    private static ArrayList<InterfaceTable> getSuperInterfaceTables(ObjectKlass superInterface, Klass thisKlass) {
        ArrayList<InterfaceTable> res = new ArrayList<>();
        for (InterfaceTable itable : superInterface.getItable().itables) {
            res.add(new InterfaceTable(itable, thisKlass));
        }
        return res;
    }

}

class InterfaceTable {
    private final Klass thisInterfKlass;
    @CompilationFinal(dimensions = 1) final Method[] table;

    InterfaceTable(ObjectKlass interf, Klass thisKlass) {
        this.thisInterfKlass = interf;
        table = Arrays.copyOf(interf.getItable().itables[0].table, interf.getDeclaredMethods().length);
        for (int i = 0; i < table.length; i++) {
            Method im = table[i];
            Method m = thisKlass.lookupMethod(im.getName(), im.getRawSignature());
            if (m != null) {
                m.setITableIndex(i);
                table[i] = m;
            }
        }
    }

    InterfaceTable(Klass thisKlass, Method[] declaredMethods) {
        this.thisInterfKlass = thisKlass;
        table = declaredMethods;
        int i = 0;
        for (Method m : declaredMethods) {
            m.setITableIndex(i++);
        }
    }

    InterfaceTable(InterfaceTable inherit, Klass thisKlass) {
        this.thisInterfKlass = inherit.thisInterfKlass;
        table = Arrays.copyOf(inherit.table, inherit.table.length);
        for (int i = 0; i < table.length; i++) {
            Method im = table[i];
            Method m = thisKlass.lookupMethod(im.getName(), im.getRawSignature());
            if (m != null) {
                m.setITableIndex(i);
                table[i] = m;
            }
        }
    }

    public final Klass getKlass() {
        return thisInterfKlass;
    }

    public final Method lookupMethod(Klass interfKlass, int index) {
        assert thisInterfKlass == interfKlass;
        return table[index];
    }
}