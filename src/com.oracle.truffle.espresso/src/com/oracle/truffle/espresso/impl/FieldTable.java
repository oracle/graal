package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.descriptors.Symbol;

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

    private static int setHiddenFields(Symbol<Symbol.Type> type, ArrayList<Field> tmpTable, ObjectKlass thisKlass, int fieldIndex) {
        if (type == Symbol.Type.MemberName) {
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex, Symbol.Name.HIDDEN_VMTARGET));
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex+1, Symbol.Name.HIDDEN_VMINDEX));
            return 2;
        } else if (type == Symbol.Type.Method) {
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex, Symbol.Name.HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS));
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex+1, Symbol.Name.HIDDEN_METHOD_KEY));
            return 2;
        } else if (type == Symbol.Type.Constructor) {
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex, Symbol.Name.HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS));
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex+1, Symbol.Name.HIDDEN_CONSTRUCTOR_KEY));
            return 2;
        } else if (type == Symbol.Type.Field) {
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex, Symbol.Name.HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS));
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex+1, Symbol.Name.HIDDEN_FIELD_KEY));
            return 2;
        } else if (type == Symbol.Type.Throwable) {
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex, Symbol.Name.HIDDEN_FRAMES));
            return 1;
        } else if (type == Symbol.Type.Thread) {
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex, Symbol.Name.HIDDEN_HOST_THREAD));
            return 1;
        } else if (type == Symbol.Type.Class) {
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex, Symbol.Name.HIDDEN_MIRROR_KLASS));
            return 1;
        } else {
            return 0;
        }
    }
}
