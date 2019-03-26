package com.oracle.truffle.espresso.impl;

import java.util.ArrayList;
import java.util.Arrays;

class FieldTable {
    public static class CreationResult {
        Field[] fieldTable;
        Field[] staticFieldTable;
        Field[] declaredFields;

        CreationResult(Field[] fieldTable, Field[] staticFieldTable, Field[] declaredFields) {
            this.fieldTable = fieldTable;
            this.staticFieldTable = staticFieldTable;
            this.declaredFields = declaredFields;
        }
    }

    public static CreationResult create(ObjectKlass superKlass, ObjectKlass thisKlass, LinkedKlass linkedKlass) {
        ArrayList<Field> tmpFields;
        ArrayList<Field> tmpStatics = new ArrayList<>();

        if (superKlass != null) {
            tmpFields = new ArrayList<>(Arrays.asList(superKlass.getFieldTable()));
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
                tmpStatics.add(f);
            } else {
                assert (tmpFields.size() == f.getSlot());
                tmpFields.add(f);
            }
        }
        return new CreationResult(tmpFields.toArray(Field.EMPTY_ARRAY), tmpStatics.toArray(Field.EMPTY_ARRAY), fields);
    }
}
