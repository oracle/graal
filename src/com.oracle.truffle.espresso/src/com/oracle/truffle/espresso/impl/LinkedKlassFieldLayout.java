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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;

import java.util.ArrayList;
import java.util.List;

final class LinkedKlassFieldLayout {
    private static final int N_PRIMITIVES = 8;
    private static final JavaKind[] order = {JavaKind.Long, JavaKind.Double, JavaKind.Int, JavaKind.Float, JavaKind.Short, JavaKind.Char, JavaKind.Byte, JavaKind.Boolean};

    // instance fields declared in the corresponding LinkedKlass (includes hidden fields)
    @CompilerDirectives.CompilationFinal(dimensions = 1) //
    final LinkedField[] instanceFields;
    // static fields declared in the corresponding LinkedKlass (no hidden fields)
    @CompilerDirectives.CompilationFinal(dimensions = 1) //
    final LinkedField[] staticFields;

    @CompilerDirectives.CompilationFinal(dimensions = 2) //
    final int[][] leftoverHoles;

    final int primitiveFieldTotalByteCount;
    final int primitiveStaticFieldTotalByteCount;
    final int fieldTableLength;
    final int objectFields;
    final int staticObjectFields;

    private LinkedKlassFieldLayout(LinkedField[] instanceFields, LinkedField[] staticFields, int[][] leftoverHoles, int primitiveFieldTotalByteCount, int primitiveStaticFieldTotalByteCount,
                    int fieldTableLength, int objectFields, int staticObjectFields) {
        this.instanceFields = instanceFields;
        this.staticFields = staticFields;
        this.leftoverHoles = leftoverHoles;
        this.primitiveFieldTotalByteCount = primitiveFieldTotalByteCount;
        this.primitiveStaticFieldTotalByteCount = primitiveStaticFieldTotalByteCount;
        this.fieldTableLength = fieldTableLength;
        this.objectFields = objectFields;
        this.staticObjectFields = staticObjectFields;
    }

    static LinkedKlassFieldLayout create(ParserKlass parserKlass, LinkedKlass superKlass) {
        FieldCounter fieldCounter = new FieldCounter(parserKlass);

        // Stats about primitive fields
        int superTotalInstanceByteCount;
        int superTotalStaticByteCount;
        int[][] leftoverHoles;

        // Indexes for object fields
        int instanceFieldInsertionIndex = 0;
        int nextFieldTableSlot;
        int nextObjectFieldIndex;
        // The staticFieldTable does not include fields of parent classes.
        // Therefore, nextStaticFieldTableSlot can be used also as staticFieldInsertionIndex.
        int nextStaticFieldTableSlot = 0;
        int nextStaticObjectFieldIndex;

        if (superKlass != null) {
            superTotalInstanceByteCount = superKlass.getPrimitiveFieldTotalByteCount();
            superTotalStaticByteCount = superKlass.getPrimitiveStaticFieldTotalByteCount();
            leftoverHoles = superKlass.getLeftoverHoles();
            nextFieldTableSlot = superKlass.getFieldTableLength();
            nextObjectFieldIndex = superKlass.getObjectFieldsCount();
            nextStaticObjectFieldIndex = superKlass.getStaticObjectFieldsCount();
        } else {
            superTotalInstanceByteCount = 0;
            superTotalStaticByteCount = 0;
            leftoverHoles = new int[0][];
            nextFieldTableSlot = 0;
            nextObjectFieldIndex = 0;
            nextStaticObjectFieldIndex = 0;
        }

        PrimitiveFieldIndexes instancePrimitiveFieldIndexes = new PrimitiveFieldIndexes(fieldCounter.instancePrimitiveFields, superTotalInstanceByteCount, leftoverHoles);
        PrimitiveFieldIndexes staticPrimitiveFieldIndexes = new PrimitiveFieldIndexes(fieldCounter.staticPrimitiveFields, superTotalStaticByteCount, FillingSchedule.EMPTY_INT_ARRAY_ARRAY);

        LinkedField[] instanceFields = new LinkedField[fieldCounter.instanceFields];
        LinkedField[] staticFields = new LinkedField[fieldCounter.staticFields];

        for (ParserField parserField : parserKlass.getFields()) {
            JavaKind kind = parserField.getKind();
            int index;
            if (parserField.isStatic()) {
                if (kind.isPrimitive()) {
                    index = staticPrimitiveFieldIndexes.getIndex(kind);
                } else {
                    index = nextStaticObjectFieldIndex++;
                }
                LinkedField linkedField = new LinkedField(parserField, nextStaticFieldTableSlot, index);
                staticFields[nextStaticFieldTableSlot++] = linkedField;
            } else {
                if (kind.isPrimitive()) {
                    index = instancePrimitiveFieldIndexes.getIndex(kind);
                } else {
                    index = nextObjectFieldIndex++;
                }
                LinkedField linkedField = new LinkedField(parserField, nextFieldTableSlot++, index);
                instanceFields[instanceFieldInsertionIndex++] = linkedField;
            }
        }

        // Add hidden fields after all instance fields
        for (Symbol<Name> hiddenFieldName : fieldCounter.hiddenFieldNames) {
            LinkedField hiddenField = LinkedField.createHidden(hiddenFieldName, nextFieldTableSlot++, nextObjectFieldIndex++);
            instanceFields[instanceFieldInsertionIndex++] = hiddenField;
        }

        return new LinkedKlassFieldLayout(
                        instanceFields,
                        staticFields,
                        instancePrimitiveFieldIndexes.schedule.nextLeftoverHoles,
                        instancePrimitiveFieldIndexes.offsets[N_PRIMITIVES - 1],
                        staticPrimitiveFieldIndexes.offsets[N_PRIMITIVES - 1],
                        nextFieldTableSlot,
                        nextObjectFieldIndex,
                        nextStaticObjectFieldIndex);
    }

    private static int indexFromKind(JavaKind kind) {
        // @formatter:off
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
    }

    private static final class FieldCounter {
        final int[] instancePrimitiveFields = new int[N_PRIMITIVES];
        final int[] staticPrimitiveFields = new int[N_PRIMITIVES];

        final Symbol<Name>[] hiddenFieldNames;

        // Includes hidden fields
        final int instanceFields;
        final int staticFields;

        FieldCounter(ParserKlass parserKlass) {
            int iFields = 0;
            int sFields = 0;
            for (ParserField f : parserKlass.getFields()) {
                JavaKind kind = f.getKind();
                if (f.isStatic()) {
                    sFields++;
                    if (kind.isPrimitive()) {
                        staticPrimitiveFields[indexFromKind(kind)]++;
                    }
                } else {
                    iFields++;
                    if (kind.isPrimitive()) {
                        instancePrimitiveFields[indexFromKind(kind)]++;
                    }
                }
            }
            // All hidden fields are of Object kind
            hiddenFieldNames = getHiddenFieldNames(parserKlass);
            instanceFields = iFields + hiddenFieldNames.length;
            staticFields = sFields;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static Symbol<Name>[] getHiddenFieldNames(ParserKlass parserKlass) {
            Symbol<Type> type = parserKlass.getType();
            if (type == Type.java_lang_invoke_MemberName) {
                return new Symbol[]{
                                Name.HIDDEN_VMTARGET,
                                Name.HIDDEN_VMINDEX
                };
            } else if (type == Type.java_lang_reflect_Method) {
                return new Symbol[]{
                                Name.HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS,
                                Name.HIDDEN_METHOD_KEY
                };
            } else if (type == Type.java_lang_reflect_Constructor) {
                return new Symbol[]{
                                Name.HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS,
                                Name.HIDDEN_CONSTRUCTOR_KEY
                };
            } else if (type == Type.java_lang_reflect_Field) {
                return new Symbol[]{
                                Name.HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS,
                                Name.HIDDEN_FIELD_KEY
                };
            } else if (type == Type.java_lang_ref_Reference) {
                return new Symbol[]{
                                // All references (including strong) get an extra hidden field, this
                                // simplifies the code for weak/soft/phantom/final references.
                                Name.HIDDEN_HOST_REFERENCE
                };
            } else if (type == Type.java_lang_Throwable) {
                return new Symbol[]{
                                Name.HIDDEN_FRAMES
                };
            } else if (type == Type.java_lang_Thread) {
                return new Symbol[]{
                                Name.HIDDEN_HOST_THREAD,
                                Name.HIDDEN_IS_ALIVE,
                                Name.HIDDEN_INTERRUPTED,
                                Name.HIDDEN_DEATH,
                                Name.HIDDEN_DEATH_THROWABLE,
                                Name.HIDDEN_SUSPEND_LOCK,

                                // Only used for j.l.management bookkeeping.
                                Name.HIDDEN_THREAD_BLOCKED_OBJECT,
                                Name.HIDDEN_THREAD_BLOCKED_COUNT,
                                Name.HIDDEN_THREAD_WAITED_COUNT
                };
            } else if (type == Type.java_lang_Class) {
                return new Symbol[]{
                                Name.HIDDEN_SIGNERS,
                                Name.HIDDEN_MIRROR_KLASS,
                                Name.HIDDEN_PROTECTION_DOMAIN
                };
            } else if (type == Type.java_lang_ClassLoader) {
                return new Symbol[]{
                                Name.HIDDEN_CLASS_LOADER_REGISTRY
                };
            }
            return Symbol.EMPTY_ARRAY;
        }
    }

    private static final class PrimitiveFieldIndexes {
        final int[] offsets;
        final FillingSchedule schedule;

        // To ignore leftoverHoles, pass FillingSchedule.EMPTY_INT_ARRAY_ARRAY.
        // This is used for static fields, where the gain would be negligible.
        PrimitiveFieldIndexes(int[] primitiveFields, int superTotalByteCount, int[][] leftoverHoles) {
            offsets = new int[N_PRIMITIVES];
            offsets[0] = startOffset(superTotalByteCount, primitiveFields);
            this.schedule = FillingSchedule.create(superTotalByteCount, offsets[0], primitiveFields, leftoverHoles);
            // FillingSchedule.create() modifies primitiveFields.
            // Only offsets[0] must be initialized before creating the filling schedule.
            for (int i = 1; i < N_PRIMITIVES; i++) {
                offsets[i] = offsets[i - 1] + primitiveFields[i - 1] * order[i - 1].getByteCount();
            }
        }

        int getIndex(JavaKind kind) {
            ScheduleEntry entry = schedule.query(kind);
            if (entry != null) {
                return entry.offset;
            } else {
                int offsetsIndex = indexFromKind(kind);
                int prevOffset = offsets[offsetsIndex];
                offsets[offsetsIndex] += kind.getByteCount();
                return prevOffset;
            }
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
    }

    /**
     * Greedily tries to fill the space between a parent's fields and its child.
     */
    private static final class FillingSchedule {
        static final int[][] EMPTY_INT_ARRAY_ARRAY = new int[0][];

        List<ScheduleEntry> schedule;
        int[][] nextLeftoverHoles;

        static FillingSchedule create(int holeStart, int holeEnd, int[] counts, int[][] leftoverHoles) {
            List<ScheduleEntry> schedule = new ArrayList<>();
            if (leftoverHoles == EMPTY_INT_ARRAY_ARRAY) {
                // packing static fields is not as interesting as instance fields: the array created
                // to remember the hole would be bigger than what we would gain. Only schedule for
                // direct parent.
                scheduleHole(holeStart, holeEnd, counts, schedule);
                return new FillingSchedule(schedule);
            } else {
                List<int[]> nextHoles = new ArrayList<>();
                scheduleHole(holeStart, holeEnd, counts, schedule, nextHoles);
                if (leftoverHoles != null) {
                    for (int[] hole : leftoverHoles) {
                        scheduleHole(hole[0], hole[1], counts, schedule, nextHoles);
                    }
                }
                return new FillingSchedule(schedule, nextHoles);
            }
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

    private static class ScheduleEntry {
        final JavaKind kind;
        final int offset;

        ScheduleEntry(JavaKind kind, int offset) {
            this.kind = kind;
            this.offset = offset;
        }
    }
}
