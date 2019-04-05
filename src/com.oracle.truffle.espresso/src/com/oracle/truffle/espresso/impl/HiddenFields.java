package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;

import java.util.ArrayList;

public final class HiddenFields {
    private HiddenFields() {}

    // j.l.i.MemberName hidden fields
    @CompilerDirectives.CompilationFinal static public int HIDDEN_VMTARGET = -1;
    @CompilerDirectives.CompilationFinal static public int HIDDEN_VMINDEX = -1;

    // j.l.r.Method hidden fields
    @CompilerDirectives.CompilationFinal static public int HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = -1;
    @CompilerDirectives.CompilationFinal static public int HIDDEN_METHOD_KEY = -1;

    // j.l.r.Constructor hidden fields
    @CompilerDirectives.CompilationFinal static public int HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = -1;
    @CompilerDirectives.CompilationFinal static public int HIDDEN_CONSTRUCTOR_KEY = -1;

    // j.l.r.Field hidden fields
    @CompilerDirectives.CompilationFinal static public int HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = -1;
    @CompilerDirectives.CompilationFinal static public int HIDDEN_FIELD_KEY = -1;

    // EmptyObject hidden fields
    @CompilerDirectives.CompilationFinal static public int HIDDEN_FRAMES = -1;

    //j.l.Thread hidden fields
    @CompilerDirectives.CompilationFinal static public int HIDDEN_HOST_THREAD = -1;

    static int setHiddenFields(Symbol<Type> type, ArrayList<Field> tmpTable, ObjectKlass thisKlass, int fieldIndex) {
        if (type == Type.MemberName) {
            HIDDEN_VMTARGET = fieldIndex;
            tmpTable.add(new Field(thisKlass, tmpTable.size(), HIDDEN_VMTARGET));
            HIDDEN_VMINDEX = fieldIndex + 1;
            tmpTable.add(new Field(thisKlass, tmpTable.size(), HIDDEN_VMINDEX));
            return 2;
        } else if (type == Type.Method) {
            HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = fieldIndex;
            tmpTable.add(new Field(thisKlass, tmpTable.size(), HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS));
            HIDDEN_METHOD_KEY = fieldIndex + 1;
            tmpTable.add(new Field(thisKlass, tmpTable.size(), HIDDEN_METHOD_KEY));
            return 2;
        } else if (type == Type.Constructor) {
            HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = fieldIndex;
            tmpTable.add(new Field(thisKlass, tmpTable.size(), HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS));
            HIDDEN_CONSTRUCTOR_KEY = fieldIndex + 1;
            tmpTable.add(new Field(thisKlass, tmpTable.size(), HIDDEN_CONSTRUCTOR_KEY));
                return 2;
        } else if (type == Type.Field) {
            HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = fieldIndex;
            tmpTable.add(new Field(thisKlass, tmpTable.size(), HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS));
            HIDDEN_FIELD_KEY = fieldIndex + 1;
            tmpTable.add(new Field(thisKlass, tmpTable.size(), HIDDEN_FIELD_KEY));
            return 2;
        } else if (type == Type.Throwable) {
            HIDDEN_FRAMES = fieldIndex;
            tmpTable.add(new Field(thisKlass, tmpTable.size(), HIDDEN_FRAMES));
            return 1;
        } else if (type == Type.Thread) {
            HIDDEN_HOST_THREAD = fieldIndex;
            tmpTable.add(new Field(thisKlass, tmpTable.size(), HIDDEN_HOST_THREAD));
            return 1;
        } else {
            return 0;
        }
    }
}
