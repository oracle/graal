/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;

import java.util.ArrayList;
import java.util.Arrays;

class FieldTable {
    static class CreationResult {
        Field[] fieldTable;
        Field[] staticFieldTable;
        Field[] declaredFields;

        int wordFields;
        int staticWordFields;
        int objectFields;
        int staticObjectFields;

        CreationResult(Field[] fieldTable, Field[] staticFieldTable, Field[] declaredFields, int wordFields, int staticWordFields, int objectFields, int staticObjectFields) {
            this.fieldTable = fieldTable;
            this.staticFieldTable = staticFieldTable;
            this.declaredFields = declaredFields;
            this.wordFields = wordFields;
            this.staticWordFields = staticWordFields;
            this.objectFields = objectFields;
            this.staticObjectFields = staticObjectFields;
        }
    }

    public static CreationResult create(ObjectKlass superKlass, ObjectKlass thisKlass, LinkedKlass linkedKlass) {
        ArrayList<Field> tmpFields;
        ArrayList<Field> tmpStatics = new ArrayList<>();

        int wordFields = 0;
        int staticWordFields = 0;
        int objectFields = 0;
        int staticObjectFields = 0;

        if (superKlass != null) {
            tmpFields = new ArrayList<>(Arrays.asList(superKlass.getFieldTable()));
            wordFields = superKlass.getWordFieldsCount();
            staticWordFields = superKlass.getStaticWordFieldsCount();
            objectFields = superKlass.getObjectFieldsCount();
            staticObjectFields = superKlass.getStaticObjectFieldsCount();
        } else {
            tmpFields = new ArrayList<>();
        }

        LinkedField[] linkedFields = linkedKlass.getLinkedFields();
        Field[] fields = new Field[linkedFields.length];
        for (int i = 0; i < fields.length; ++i) {
            Field f = new Field(linkedFields[i], thisKlass);
            fields[i] = f;
            if (f.isStatic()) {
                f.setSlot(tmpStatics.size());
                if (f.getKind().isPrimitive()) {
                    f.setFieldIndex(staticWordFields);
                    staticWordFields += f.getKind().getByteCount();
                } else {
                    f.setFieldIndex(staticObjectFields++);
                }
                tmpStatics.add(f);
            } else {
                f.setSlot(tmpFields.size());
                if (f.getKind().isPrimitive()) {
                    f.setFieldIndex(wordFields);
                    wordFields += f.getKind().getByteCount();
                } else {
                    f.setFieldIndex(objectFields++);
                }
                tmpFields.add(f);
            }
        }

        objectFields += setHiddenFields(thisKlass.getType(), tmpFields, thisKlass, objectFields);

        return new CreationResult(tmpFields.toArray(Field.EMPTY_ARRAY), tmpStatics.toArray(Field.EMPTY_ARRAY), fields,
                        wordFields, staticWordFields, objectFields, staticObjectFields);
    }

    private static int setHiddenFields(Symbol<Type> type, ArrayList<Field> tmpTable, ObjectKlass thisKlass, int fieldIndex) {
        // Gimmick to not forget to return correct increment. Forgetting results in dramatic JVM
        // crashes.
        int c = 0;

        if (type == Type.MemberName) {
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex + c++, Name.HIDDEN_VMTARGET));
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex + c++, Name.HIDDEN_VMINDEX));
            return c;
        } else if (type == Type.Method) {
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex + c++, Name.HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS));
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex + c++, Name.HIDDEN_METHOD_KEY));
            return c;
        } else if (type == Type.Constructor) {
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex + c++, Name.HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS));
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex + c++, Name.HIDDEN_CONSTRUCTOR_KEY));
            return c;
        } else if (type == Type.Field) {
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex + c++, Name.HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS));
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex + c++, Name.HIDDEN_FIELD_KEY));
            return c;
        } else if (type == Type.Throwable) {
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex + c++, Name.HIDDEN_FRAMES));
            return c;
        } else if (type == Type.Thread) {
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex + c++, Name.HIDDEN_HOST_THREAD));
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex + c++, Name.HIDDEN_IS_ALIVE));
            return c;
        } else if (type == Type.Class) {
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex + c++, Name.HIDDEN_SIGNERS));
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex + c++, Name.HIDDEN_MIRROR_KLASS));
            return c;
        } else {
            return c;
        }
    }
}
