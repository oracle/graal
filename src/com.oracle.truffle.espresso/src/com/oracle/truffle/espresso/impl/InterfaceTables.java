package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import java.util.ArrayList;
import java.util.Arrays;

public class InterfaceTables {
    @CompilationFinal(dimensions = 1) InterfaceTable[] itables;

    InterfaceTables(ObjectKlass superKlass, ObjectKlass[] superInterfaces, Klass thisKlass) {
        ArrayList<InterfaceTable> tmp;
        if (superKlass != null) {
            tmp = new ArrayList<>(Arrays.asList(superKlass.getItable().itables));
        } else {
            tmp = new ArrayList<>();
        }
        for (ObjectKlass interf : superInterfaces) {
            tmp.add(new InterfaceTable(interf, thisKlass));
        }
        this.itables = tmp.toArray(new InterfaceTable[0]);
    }

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

}

class InterfaceTable {
    private final Klass thisInterfKlass;
    @CompilationFinal(dimensions = 1) private final Method[] table;

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

    public final Klass getKlass() {
        return thisInterfKlass;
    }

    public final Method lookupMethod(Klass interfKlass, int index) {
        assert thisInterfKlass == interfKlass;
        return table[index];
    }
}