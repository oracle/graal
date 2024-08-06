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
package jdk.graal.compiler.lir.util;

import java.util.EnumSet;
import java.util.Objects;

import jdk.graal.compiler.lir.InstructionValueConsumer;
import jdk.graal.compiler.lir.InstructionValueProcedure;
import jdk.graal.compiler.lir.LIRInstruction;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import jdk.graal.compiler.debug.GraalError;

import jdk.vm.ci.meta.Value;

/**
 * A map-like data structure for directly mapping int indexes to {@link Value} objects using an
 * array. If the size of the array grows beyond {@link IndexedValueMap#MAP_FALLBACK_THRESHOLD} then
 * this data structure does a switch to {@link EconomicMap} backing. When the data structure is
 * populated via the {@link IndexedValueMap#IndexedValueMap(IndexedValueMap) copy constructor}, or
 * the {@link IndexedValueMap#putAll(IndexedValueMap)} method while being empty, the backing array
 * or map are not copied, instead the backing array or map from the source are used. They are copied
 * only on the next modification. Therefore, {@link IndexedValueMap} instances must not be passed to
 * other threads.
 */
public final class IndexedValueMap {
    /**
     * Maximum number of elements in the {@link IndexedValueMap#values} array. If mappings for
     * higher indexes are needed, the data structure does a switch to
     * {@link IndexedValueMap#fallbackMap} backing.
     */
    private static final int MAP_FALLBACK_THRESHOLD = 128;

    /**
     * An array backing this data structure as long as all indexes are below
     * {@link IndexedValueMap#MAP_FALLBACK_THRESHOLD}, otherwise equal to {@code null}.
     */
    private Value[] values;

    /**
     * A fallback map backing this data structure since the occurrence of a mapping with an index
     * greater or equal to {@link IndexedValueMap#MAP_FALLBACK_THRESHOLD}. The data structure is
     * backed by the fallback map, if and only if the {@link IndexedValueMap#values} array is equal
     * to {@code null}. The data structure can switch back to being backed by the array if all
     * indexes are mapped to {@code null} and {@link IndexedValueMap#putAll(IndexedValueMap)} is
     * called with a map backed by an array.
     */
    private EconomicMap<Integer, Value> fallbackMap;

    /**
     * A boolean indicating whether the data structure should copy its backing array or map on the
     * next modification.
     */
    private boolean copyOnWrite;

    /**
     * Construct a new empty map.
     */
    public IndexedValueMap() {
        values = Value.NO_VALUES;
    }

    /**
     * Constructs a new map containing the same mappings as the {@code other} map. Note that the
     * mappings are not copied, instead the new map uses the same backing as the source map, copying
     * it only on the next modification. As a result, the backing of the source map has to be copied
     * on the next modification as well.
     *
     * @param other the source map whose mappings are to be placed in this map
     */
    public IndexedValueMap(IndexedValueMap other) {
        values = other.values;
        fallbackMap = other.fallbackMap;
        copyOnWrite = true;
        other.copyOnWrite = true;
    }

    /**
     * Returns the value to which the specified index is mapped, or {@code null} if the map contains
     * no mapping for the index.
     *
     * @param index the index whose associated value is to be returned
     * @return the value to which the specified index is mapped, or {@code null} if the map contains
     *         no mapping for the index
     */
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

    /**
     * Associates the specified value with the specified index in this map. If the map previously
     * contained a mapping for the index, the old value is replaced. If this operation requires a
     * modification of the backing array or map and {@link IndexedValueMap#copyOnWrite} is
     * {@code true}, the backing array or map are copied. If this map is backed by the array and
     * this operation requires it to grow above {@link IndexedValueMap#MAP_FALLBACK_THRESHOLD}, the
     * array is copied to the fallback map which becomes the new backing for this map.
     *
     * @param index the index with which the specified value is to be associated
     * @param value value to be associated with the specified index
     */
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

    /**
     * Copies the backing array to a new backing array.
     *
     * @param minLimit the minimal count of elements in the new array
     */
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

    /**
     * Copies the backing map to a new backing map.
     *
     * @param allowNullFallbackMap specifies whether the backing map containing no mapping with
     *            non-null value should be represented by {@code null} backing map or empty backing
     *            map
     */
    private void doCopyMapOnWrite(boolean allowNullFallbackMap) {
        EconomicMap<Integer, Value> newFallbackMap = allowNullFallbackMap ? null : EconomicMap.create();
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

    /**
     * Determine whether the map contains no mapping with non-null value.
     *
     * @return {@code true} if and only if the map contains no mapping with non-null value
     */
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

    /**
     * Copy the backing array to a map which becomes the new backing for this map.
     */
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

    /**
     * Copies all of the mappings with non-null value from the specified map to this map. These
     * mappings replace any mappings that this map had for any of the indexes currently in the
     * specified map. If this operation requires a modification of the backing array or map and
     * {@link IndexedValueMap#copyOnWrite} is {@code true}, the backing array or map are copied. If
     * this map is backed by the array and this operation requires it to grow above
     * {@link IndexedValueMap#MAP_FALLBACK_THRESHOLD}, the array is copied to the fallback map which
     * becomes the new backing for this map. If this map is empty, the mappings from the specified
     * map are not copied, instead the new map uses the same backing as the source map, copying it
     * only on the next modification. As a result, the backing of the source map has to be copied on
     * the next modification as well.
     *
     * @param stack mappings to be stored in this map
     */
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
                    values[i] = mergeValues(value, values[i]);
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
                Value value = valueEntry.getValue();
                if (value != null) {
                    fallbackMap.put(valueEntry.getKey(), mergeValues(value, fallbackMap.get(valueEntry.getKey())));
                }
            }
        } else { // values == null && otherValues != null
            if (copyOnWrite) {
                doCopyMapOnWrite(false);
            }

            for (int i = 0; i < otherValues.length; i++) {
                Value value = otherValues[i];
                if (value != null) {
                    fallbackMap.put(i, mergeValues(value, fallbackMap.get(i)));
                }
            }
        }
    }

    /**
     * Merge values for the same location. Either value may be null and if they are both non-null
     * they should be exactly equal. Otherwise, the defs and uses disagree about the value which
     * signals an error of some sort.
     */
    private static Value mergeValues(Value v1, Value v2) {
        if (v1 == null) {
            return v2;
        } else if (v2 == null) {
            return v1;
        } else if (v1.equals(v2)) {
            return v1;
        } else {
            throw GraalError.shouldNotReachHere("unable to merge %s and %s".formatted(v1, v2));
        }
    }

    /**
     * Determine whether the specified object is an {@link IndexedValueMap} and all the mappings
     * with non-null values are equal in this map and the specified map.
     *
     * @param other the object to compare this map with
     * @return {@code true} if and only if the specified object is an {@link IndexedValueMap} and
     *         all the mappings with non-null values are equal in this map and the specified map.
     */
    @Override
    public boolean equals(Object other) {
        if (other instanceof IndexedValueMap) {
            IndexedValueMap that = (IndexedValueMap) other;
            return isSubsetOf(that) && that.isSubsetOf(this);
        }
        return false;
    }

    /**
     * Determine whether all the mappings with non-null values in this map are also present in the
     * specified map.
     *
     * @param other the object to compare this map with
     * @return {@code true} if and only if all the mappings with non-null values in this map are
     *         also present in the specified map
     */
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

    /**
     * Iterate over all non-null values and, optionally, modify them. For each mapping in this map
     * with a non-null value, apply the specified procedure to the value and replace the value in
     * the mapping by the value returned by the procedure.
     *
     * @param inst current instruction
     * @param mode the operand mode for each value
     * @param flags a set of flags for each value
     * @param proc procedure to apply to each value
     */
    public void forEach(LIRInstruction inst, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags, InstructionValueProcedure proc) {
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

    /**
     * Iterate over all non-null values without modifying them. For each mapping in this map with a
     * non-null value, pass the value to the specified consumer function.
     *
     * @param inst current instruction
     * @param mode the operand mode for each value
     * @param flags a set of flags for each value
     * @param consumer consumer function to be called for each value
     */
    public void visitEach(LIRInstruction inst, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags, InstructionValueConsumer consumer) {
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
                    Value v = valueEntry.getValue();
                    if (v != null) {
                        consumer.visitValue(inst, v, mode, flags);
                    }
                }
            }
        }
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException();
    }

    /**
     * Get a string representation for all mappings in this map with a non-null value.
     *
     * @return a string representation for all mappings in this map with a non-null value
     */
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
