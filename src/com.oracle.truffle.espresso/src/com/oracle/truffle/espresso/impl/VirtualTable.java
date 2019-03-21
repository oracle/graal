package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives;

import java.util.ArrayList;
import java.util.Arrays;

public class VirtualTable {
    @CompilerDirectives.CompilationFinal(dimensions = 1) private final Method[] table;
    private final int length;

    VirtualTable(ObjectKlass superKlass, Method[] declaredMethods) {
        ArrayList<Method> tmp;
        if (superKlass != null) {
            tmp = new ArrayList<>(Arrays.asList(superKlass.getVTable().table));
        } else {
            tmp = new ArrayList<>();
        }
        Method override;
        int pos;
        for (Method m: declaredMethods) {
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
        this.table = tmp.toArray(Method.EMPTY_ARRAY);
        this.length = table.length;
    }

    final public int length() {
        return this.length;
    }

    final Method lookupMethod(int index) {
        return table[index];
    }


}
