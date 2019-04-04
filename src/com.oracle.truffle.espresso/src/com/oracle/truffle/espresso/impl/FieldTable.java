package com.oracle.truffle.espresso.impl;

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
                assert (tmpStatics.size() == f.getSlot());
                if (f.getKind().isSubWord()) {
                    f.setFieldIndex(staticWordFields++);
                } else {
                    f.setFieldIndex(staticObjectFields++);
                }
                tmpStatics.add(f);
            } else {
                assert (tmpFields.size() == f.getSlot());
                if (f.getKind().isSubWord()) {
                    f.setFieldIndex(wordFields++);
                } else {
                    f.setFieldIndex(objectFields++);
                }
                tmpFields.add(f);
            }
        }
        return new CreationResult(tmpFields.toArray(Field.EMPTY_ARRAY), tmpStatics.toArray(Field.EMPTY_ARRAY), fields,
                        wordFields, staticWordFields, objectFields, staticObjectFields);
    }
}
