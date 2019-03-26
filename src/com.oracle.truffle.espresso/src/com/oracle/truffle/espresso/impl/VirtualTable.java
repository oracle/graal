package com.oracle.truffle.espresso.impl;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Helper for creating virtual tables in ObjectKlass
 */
public class VirtualTable {

    private VirtualTable() {
    }

    // Mirandas are already present in declaredMethods
    public static Method[] create(ObjectKlass superKlass, Method[] declaredMethods, ObjectKlass thisKlass) {
        ArrayList<Method> tmp;
        if (superKlass != null) {
            tmp = new ArrayList<>(Arrays.asList(superKlass.getVTable()));
        } else {
            tmp = new ArrayList<>();
        }
        Method override;
        int pos;
        int n_method = 0;
        for (Method m : declaredMethods) {
            if (m.isVirtualCall() || !(n_method < thisKlass.trueDeclaredMethods)) {
                if (superKlass != null) {
                    override = superKlass.lookupVirtualMethod(m.getName(), m.getRawSignature());
                } else {
                    override = null;
                }
                if (override != null) {
                    pos = override.getVTableIndex();
                    m.setVTableIndex(pos);
                    tmp.set(pos, m);
                } else {
                    pos = tmp.size();
                    m.setVTableIndex(pos);
                    tmp.add(m);
                }
            }
            n_method++;
        }
        return tmp.toArray(Method.EMPTY_ARRAY);
    }
}
