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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;

class FieldTable {
    static class CreationResult {
        Field[] fieldTable;
        Field[] staticFieldTable;
        Field[] declaredFields;

        int[][] leftoverHoles;

        int primitiveFieldTotalByteCount;
        int primitiveStaticFieldTotalByteCount;
        int objectFields;
        int staticObjectFields;

        CreationResult(Field[] fieldTable, Field[] staticFieldTable, Field[] declaredFields, int[][] leftoverHoles, int primitiveFieldTotalByteCount, int primitiveStaticFieldTotalByteCount,
                        int objectFields,
                        int staticObjectFields) {
            this.fieldTable = fieldTable;
            this.staticFieldTable = staticFieldTable;
            this.declaredFields = declaredFields;
            this.leftoverHoles = leftoverHoles;
            this.primitiveFieldTotalByteCount = primitiveFieldTotalByteCount;
            this.primitiveStaticFieldTotalByteCount = primitiveStaticFieldTotalByteCount;
            this.objectFields = objectFields;
            this.staticObjectFields = staticObjectFields;
        }
    }

    private static final int N_PRIMITIVES = 8;

    private static final JavaKind[] order = {JavaKind.Long, JavaKind.Double, JavaKind.Int, JavaKind.Float, JavaKind.Short, JavaKind.Char, JavaKind.Byte, JavaKind.Boolean};

    private static int indexFromKind(JavaKind kind) {
        // @formatter:off
        // Checkstyle: stop
        switch (kind) {
            case Boolean: return 7;
            case Byte   : return 6;
            case Short  : return 4;
            case Char   : return 5;
            case Int    : return 2;
            case Float  : return 3;
            case Long   : return 0;
            case Double : return 1;
            default:
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        // Checkstyle: resume
    }

    public static CreationResult create(ObjectKlass superKlass, ObjectKlass thisKlass, LinkedKlass linkedKlass) {
        ArrayList<Field> tmpFields;
        ArrayList<Field> tmpStatics = new ArrayList<>();

        int superTotalByteCount = 0;
        int superTotalStaticByteCount = 0;
        int objectFields = 0;
        int staticObjectFields = 0;

        int[] primitiveCounts = new int[N_PRIMITIVES];
        int[] staticPrimitiveCounts = new int[N_PRIMITIVES];

        int[][] leftoverHoles = new int[0][];

        if (superKlass != null) {
            tmpFields = new ArrayList<>(Arrays.asList(superKlass.getFieldTable()));
            superTotalByteCount = superKlass.getPrimitiveFieldTotalByteCount();
            superTotalStaticByteCount = superKlass.getPrimitiveStaticFieldTotalByteCount();
            objectFields = superKlass.getObjectFieldsCount();
            staticObjectFields = superKlass.getStaticObjectFieldsCount();
            leftoverHoles = superKlass.getLeftoverHoles();
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
                    staticPrimitiveCounts[indexFromKind(f.getKind())]++;
                } else {
                    f.setFieldIndex(staticObjectFields++);
                }
                tmpStatics.add(f);
            } else {
                f.setSlot(tmpFields.size());
                if (f.getKind().isPrimitive()) {
                    primitiveCounts[indexFromKind(f.getKind())]++;
                } else {
                    f.setFieldIndex(objectFields++);
                }
                tmpFields.add(f);
            }
        }

        int[] primitiveOffsets = new int[N_PRIMITIVES];
        int[] staticPrimitiveOffsets = new int[N_PRIMITIVES];

        int startOffset = startOffset(superTotalByteCount, primitiveCounts);
        primitiveOffsets[0] = startOffset;

        int staticStartOffset = startOffset(superTotalStaticByteCount, staticPrimitiveCounts);
        staticPrimitiveOffsets[0] = staticStartOffset;

        FillingSchedule schedule = FillingSchedule.create(superTotalByteCount, startOffset, primitiveCounts, leftoverHoles);
        FillingSchedule staticSchedule = FillingSchedule.create(superTotalStaticByteCount, staticStartOffset, staticPrimitiveCounts);

        for (int i = 1; i < N_PRIMITIVES; i++) {
            primitiveOffsets[i] = primitiveOffsets[i - 1] + primitiveCounts[i - 1] * order[i - 1].getByteCount();
            staticPrimitiveOffsets[i] = staticPrimitiveOffsets[i - 1] + staticPrimitiveCounts[i - 1] * order[i - 1].getByteCount();
        }

        for (Field f : fields) {
            if (f.getKind().isPrimitive()) {
                if (f.isStatic()) {
                    ScheduleEntry entry = staticSchedule.query(f.getKind());
                    if (entry != null) {
                        f.setFieldIndex(entry.offset);
                    } else {
                        f.setFieldIndex(staticPrimitiveOffsets[indexFromKind(f.getKind())]);
                        staticPrimitiveOffsets[indexFromKind(f.getKind())] += f.getKind().getByteCount();
                    }
                } else {
                    ScheduleEntry entry = schedule.query(f.getKind());
                    if (entry != null) {
                        f.setFieldIndex(entry.offset);
                    } else {
                        f.setFieldIndex(primitiveOffsets[indexFromKind(f.getKind())]);
                        primitiveOffsets[indexFromKind(f.getKind())] += f.getKind().getByteCount();
                    }
                }
            }
        }

        objectFields += setHiddenFields(thisKlass.getType(), tmpFields, thisKlass, objectFields);

        return new CreationResult(tmpFields.toArray(Field.EMPTY_ARRAY), tmpStatics.toArray(Field.EMPTY_ARRAY), fields, schedule.nextLeftoverHoles,
                        primitiveOffsets[N_PRIMITIVES - 1], staticPrimitiveOffsets[N_PRIMITIVES - 1], objectFields, staticObjectFields);
    }

    // Find first primitive to set, and align on it.
    private static int startOffset(int superTotalByteCount, int[] primitiveCounts) {
        int i = 0;
        while (i < N_PRIMITIVES && primitiveCounts[i] == 0) {
            i++;
        }
        if (i == N_PRIMITIVES) {
            return superTotalByteCount;
        }
        int r = superTotalByteCount % order[i].getByteCount();
        if (r == 0) {
            return superTotalByteCount;
        }
        return superTotalByteCount + order[i].getByteCount() - r;
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
            tmpTable.add(new Field(thisKlass, tmpTable.size(), fieldIndex + c++, Name.HIDDEN_PROTECTION_DOMAIN));
            return c;
        } else {
            return c;
        }
    }

    /**
     * Greedily tries to fill the space between a parent's fields and its child.
     */
    static class FillingSchedule {
        static final int[][] EMPTY_INT_ARRAY_ARRAY = new int[0][];

        List<ScheduleEntry> schedule;
        int[][] nextLeftoverHoles;

        static FillingSchedule create(int holeStart, int holeEnd, int[] counts, int[][] leftoverHoles) {
            List<ScheduleEntry> schedule = new ArrayList<>();
            List<int[]> nextHoles = new ArrayList<>();

            scheduleHole(holeStart, holeEnd, counts, schedule, nextHoles);
            if (leftoverHoles != null) {
                for (int[] hole : leftoverHoles) {
                    scheduleHole(hole[0], hole[1], counts, schedule, nextHoles);
                }
            }

            return new FillingSchedule(schedule, nextHoles);
        }

        // packing static fields is not as interesting as instance fields: the array created to
        // remember the hole would be bigger than what we would gain. Only schedule for direct
        // parent.
        static FillingSchedule create(int holeStart, int holeEnd, int[] counts) {
            List<ScheduleEntry> schedule = new ArrayList<>();

            scheduleHole(holeStart, holeEnd, counts, schedule);

            return new FillingSchedule(schedule);
        }

        private static void scheduleHole(int holeStart, int holeEnd, int[] counts, List<ScheduleEntry> schedule, List<int[]> nextHoles) {
            int end = holeEnd;
            int holeSize = holeEnd - holeStart;
            int i = 0;

            mainloop: while (holeSize > 0 && i < N_PRIMITIVES) {
                int byteCount = order[i].getByteCount();
                while (counts[i] > 0 && byteCount <= holeSize) {
                    int newEnd = end - byteCount;
                    if (newEnd % byteCount != 0) {
                        int misalignment = newEnd % byteCount;
                        int aligned = newEnd - misalignment;
                        if (aligned < holeStart) {
                            // re-aligning the store makes it overlap with somethig else: abort.
                            i++;
                            continue mainloop;
                        }
                        schedule.add(new ScheduleEntry(order[i], aligned));
                        counts[i]--;
                        // We created a new hole of size `misaligned`. Try to fill it.
                        scheduleHole(end - misalignment, end, counts, schedule, nextHoles);
                        newEnd = aligned;
                    } else {
                        counts[i]--;
                        schedule.add(new ScheduleEntry(order[i], newEnd));
                    }
                    end = newEnd;
                    holeSize = end - holeStart;
                }
                i++;
            }
            if (holeSize > 0) {
                nextHoles.add(new int[]{holeStart, end});
            }
        }

        private static void scheduleHole(int holeStart, int holeEnd, int[] counts, List<ScheduleEntry> schedule) {
            int end = holeEnd;
            int holeSize = holeEnd - holeStart;
            int i = 0;

            while (holeSize > 0 && i < N_PRIMITIVES) {
                if (counts[i] > 0 && order[i].getByteCount() <= holeSize) {
                    while (counts[i] > 0 && order[i].getByteCount() <= holeSize) {
                        counts[i]--;
                        end -= order[i].getByteCount();
                        holeSize -= order[i].getByteCount();
                        schedule.add(new ScheduleEntry(order[i], end));
                    }
                }
                i++;
            }
            assert holeSize >= 0;
        }

        private FillingSchedule(List<ScheduleEntry> schedule) {
            this.schedule = schedule;
        }

        private FillingSchedule(List<ScheduleEntry> schedule, List<int[]> nextHoles) {
            this.schedule = schedule;
            this.nextLeftoverHoles = nextHoles.isEmpty() ? null : nextHoles.toArray(EMPTY_INT_ARRAY_ARRAY);
        }

        ScheduleEntry query(JavaKind kind) {
            for (ScheduleEntry e : schedule) {
                if (e.kind == kind) {
                    schedule.remove(e);
                    return e;
                }
            }
            return null;
        }
    }

    static class ScheduleEntry {
        final JavaKind kind;
        final int offset;

        ScheduleEntry(JavaKind kind, int offset) {
            this.kind = kind;
            this.offset = offset;
        }
    }
}
