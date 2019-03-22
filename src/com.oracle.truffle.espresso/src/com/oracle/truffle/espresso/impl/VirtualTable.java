package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives;

import java.util.ArrayList;
import java.util.Arrays;

// Helper for creating virtual tables
public class VirtualTable {

    private VirtualTable() {
    }

    public static Method[] create(ObjectKlass superKlass, Method[] declaredMethods) {
        ArrayList<Method> tmp;
        if (superKlass != null) {
            tmp = new ArrayList<>(Arrays.asList(superKlass.getVTable()));
        } else {
            tmp = new ArrayList<>();
        }
        Method override;
        int pos;
        for (Method m : declaredMethods) {
            if (m.getRefKind() == Target_java_lang_invoke_MethodHandleNatives.REF_invokeVirtual) {
                if (superKlass != null) {
                    override = superKlass.lookupMethod(m.getName(), m.getRawSignature());
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
        }
        return tmp.toArray(Method.EMPTY_ARRAY);
    }
}
