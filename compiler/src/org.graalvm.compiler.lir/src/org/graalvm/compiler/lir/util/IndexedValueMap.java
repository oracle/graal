/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.lir.util;

import java.util.EnumSet;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.lir.InstructionValueConsumer;
import org.graalvm.compiler.lir.InstructionValueProcedure;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;

import jdk.vm.ci.meta.Value;

public final class IndexedValueMap {
    private static final int MAP_FALLBACK_THRESHOLD = 128;

    private Value[] values;
    private boolean copyOnWrite;
    private EconomicMap<Integer, Value> fallbackMap;

    public IndexedValueMap() {
        values = Value.NO_VALUES;
    }

    public IndexedValueMap(IndexedValueMap other) {
        values = other.values;
        fallbackMap = other.fallbackMap;
        copyOnWrite = true;
        other.copyOnWrite = true;
    }

    public Value get(int index) {
        if (values != null) {
            if (index < values.length) {
                return values[index];
            }
        } else {
            if (fallbackMap != null) {
                return fallbackMap.get(index);
            }
        }
        return null;
    }

    public void put(int index, Value value) {
        if (values != null) {
            if (values.length <= index) {
                if (value == null) {
                    return;
                }
                if (index + 1 <= MAP_FALLBACK_THRESHOLD) {
                    Value[] newValues = new Value[index + 1];
                    if (values.length > 0) {
                        System.arraycopy(values, 0, newValues, 0, values.length);
                    }
                    values = newValues;
                    copyOnWrite = false;
                } else {
                    fallbackToMap();
                    put(index, value);
                    return;
                }
            } else if (copyOnWrite) {
                if (Objects.equals(values[index], value)) {
                    return;
                }
                doCopyOnWrite(value == null ? 0 : index + 1);
            }
            /*
             * If the following condition is not satisfied, it means that value == null and the
             * values array was shortened to the last non-null element.
             */
            if (index < values.length) {
                values[index] = value;
            }
        } else {
            if (fallbackMap == null) {
                if (value == null) {
                    return;
                }
                fallbackMap = EconomicMap.create();
                copyOnWrite = false;
            } else if (copyOnWrite) {
                if (Objects.equals(fallbackMap.get(index), value)) {
                    return;
                }
                doCopyMapOnWrite(value == null);
            }
            /*
             * fallbackMap == null means that value == null and fallbackMap was emptied by
             * doCopyMapOnWrite.
             */
            if (fallbackMap != null) {
                fallbackMap.put(index, value);
            }
        }
    }

    private void doCopyOnWrite(int minLimit) {
        int limit = values.length;
        while (limit > minLimit) {
            if (values[limit - 1] == null) {
                limit--;
                continue;
            }
            break;
        }
        if (limit == 0) {
            values = Value.NO_VALUES;
        } else {
            Value[] newValues = new Value[limit];
            System.arraycopy(values, 0, newValues, 0, limit);
            values = newValues;
        }
        copyOnWrite = false;
    }

    private void doCopyMapOnWrite(boolean allowNullValues) {
        EconomicMap<Integer, Value> newFallbackMap = allowNullValues ? null : EconomicMap.create();
        MapCursor<Integer, Value> valueEntry = fallbackMap.getEntries();
        while (valueEntry.advance()) {
            if (valueEntry.getValue() != null) {
                if (newFallbackMap == null) {
                    newFallbackMap = EconomicMap.create();
                }
                newFallbackMap.put(valueEntry.getKey(), valueEntry.getValue());
            }
        }
        fallbackMap = newFallbackMap;
        copyOnWrite = false;
    }

    private boolean isEmpty() {
        boolean empty = true;
        if (values != null) {
            for (Value value : values) {
                if (value != null) {
                    empty = false;
                    break;
                }
            }
        } else {
            if (fallbackMap != null) {
                MapCursor<Integer, Value> valueEntry = fallbackMap.getEntries();
                while (valueEntry.advance()) {
                    if (valueEntry.getValue() != null) {
                        empty = false;
                        break;
                    }
                }
            }
        }
        return empty;
    }

    private void fallbackToMap() {
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                if (fallbackMap == null) {
                    fallbackMap = EconomicMap.create();
                }
                fallbackMap.put(i, values[i]);
            }
        }
        values = null;
        copyOnWrite = false;
    }

    public void putAll(IndexedValueMap stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (isEmpty()) {
            values = stack.values;
            fallbackMap = stack.fallbackMap;
            copyOnWrite = true;
            stack.copyOnWrite = true;
            return;
        }
        Value[] otherValues = stack.values;
        if (values != null && otherValues != null) {
            int limit = otherValues.length;
            if (limit > values.length) {
                while (limit > 0) {
                    if (otherValues[limit - 1] == null) {
                        limit--;
                        continue;
                    }
                    break;
                }
                if (limit > values.length) {
                    if (limit <= MAP_FALLBACK_THRESHOLD) {
                        Value[] newValues = new Value[limit];
                        System.arraycopy(values, 0, newValues, 0, values.length);
                        values = newValues;
                        copyOnWrite = false;
                    } else {
                        fallbackToMap();
                        putAll(stack);
                        return;
                    }
                } else if (copyOnWrite) {
                    doCopyOnWrite(limit);
                }
            } else if (copyOnWrite) {
                doCopyOnWrite(limit);
            }
            for (int i = 0; i < limit; i++) {
                Value value = otherValues[i];
                if (value != null) {
                    values[i] = value;
                }
            }
        } else if (values != null || otherValues == null) {
            if (values != null) { // => otherValues == null
                fallbackToMap();
            } else { // values == null && othreValues == null
                if (copyOnWrite) {
                    doCopyMapOnWrite(false);
                }
            }
            MapCursor<Integer, Value> valueEntry = stack.fallbackMap.getEntries();
            while (valueEntry.advance()) {
                if (valueEntry.getValue() != null) {
                    fallbackMap.put(valueEntry.getKey(), valueEntry.getValue());
                }
            }
        } else { // values == null && otherValues != null
            if (copyOnWrite) {
                doCopyMapOnWrite(false);
            }

            for (int i = 0; i < otherValues.length; i++) {
                Value value = otherValues[i];
                if (value != null) {
                    fallbackMap.put(i, otherValues[i]);
                }
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof IndexedValueMap) {
            IndexedValueMap that = (IndexedValueMap) other;
            return isSubsetOf(that) && that.isSubsetOf(this);
        }
        return false;
    }

    private boolean isSubsetOf(IndexedValueMap other) {
        boolean isSubset = true;
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                if (!Objects.equals(values[i], other.get(i))) {
                    isSubset = false;
                    break;
                }
            }
        } else {
            if (fallbackMap != null) {
                MapCursor<Integer, Value> valueEntry = fallbackMap.getEntries();
                while (valueEntry.advance()) {
                    if (!Objects.equals(valueEntry.getValue(), other.get(valueEntry.getKey()))) {
                        isSubset = false;
                        break;
                    }
                }
            }
        }
        return isSubset;
    }

    public void forEach(LIRInstruction inst, OperandMode mode, EnumSet<OperandFlag> flags, InstructionValueProcedure proc) {
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null) {
                    values[i] = proc.doValue(inst, values[i], mode, flags);
                }
            }
        } else {
            if (fallbackMap != null) {
                MapCursor<Integer, Value> valueEntry = fallbackMap.getEntries();
                while (valueEntry.advance()) {
                    if (valueEntry.getValue() != null) {
                        valueEntry.setValue(proc.doValue(inst, valueEntry.getValue(), mode, flags));
                    }
                }
            }
        }
    }

    public void visitEach(LIRInstruction inst, OperandMode mode, EnumSet<OperandFlag> flags, InstructionValueConsumer consumer) {
        if (values != null) {
            for (Value v : values) {
                if (v != null) {
                    consumer.visitValue(inst, v, mode, flags);
                }
            }
        } else {
            if (fallbackMap != null) {
                MapCursor<Integer, Value> valueEntry = fallbackMap.getEntries();
                while (valueEntry.advance()) {
                    if (valueEntry.getValue() != null) {
                        consumer.visitValue(inst, valueEntry.getValue(), mode, flags);
                    }
                }
            }
        }
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        boolean comma = false;

        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null) {
                    if (comma) {
                        sb.append(", ");
                    } else {
                        comma = true;
                    }

                    sb.append(i);
                    sb.append(": ");
                    sb.append(values[i]);
                }
            }
        } else {
            if (fallbackMap != null) {
                MapCursor<Integer, Value> valueEntry = fallbackMap.getEntries();
                while (valueEntry.advance()) {
                    if (valueEntry.getValue() != null) {
                        if (comma) {
                            sb.append(", ");
                        } else {
                            comma = true;
                        }

                        sb.append(valueEntry.getKey());
                        sb.append(": ");
                        sb.append(valueEntry.getValue());
                    }
                }
            }
        }
        sb.append(']');
        return sb.toString();
    }
}
