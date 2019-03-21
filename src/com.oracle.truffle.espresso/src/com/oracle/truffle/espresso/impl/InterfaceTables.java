package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;

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

    InterfaceTables(Symbol<Name> name, ObjectKlass[] superInterfaces, Method[] declaredMethods) {
        ArrayList<InterfaceTable> tmp = new ArrayList<>();
        tmp.add(new InterfaceTable(name, declaredMethods));
        for (ObjectKlass interf : superInterfaces) {
            tmp.addAll(Arrays.asList(interf.getItable().itables));
        }
        this.itables = tmp.toArray(new InterfaceTable[0]);
    }

    public Method lookupMethod(Symbol<Name> interfName, int index) {
        for (InterfaceTable itable : itables) {
            if (itable.getName() == interfName) {
                return itable.lookupMethod(interfName, index);
            }
        }
        return null;
    }

}

class InterfaceTable {
    private final Symbol<Name> iname;
    @CompilationFinal(dimensions = 1) private final Method[] table;

    InterfaceTable(ObjectKlass interf, Klass thisKlass) {
        this.iname = interf.getName();
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

    InterfaceTable(Symbol<Name> name, Method[] declaredMethods) {
        this.iname = name;
        table = declaredMethods;
        int i = 0;
        for (Method m : declaredMethods) {
            m.setITableIndex(i++);
        }
    }

    public final Symbol<Name> getName() {
        return iname;
    }

    public final Method lookupMethod(Symbol<Name> name, int index) {
        assert name == iname;
        return table[index];
    }
}