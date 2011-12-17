/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.util;

import java.util.*;

import com.sun.max.*;

/**
 * @see Enumerable
 */
public class Enumerator<E extends Enum<E> & Enumerable<E>>
    implements Symbolizer<E> {

    private final Class<E> type;
    private final E[] ordinalMap;
    private final E[] valueMap;
    private final int lowestValue;

    public Enumerator(Class<E> type) {
        this.type = type;
        ordinalMap = type.getEnumConstants();

        int lowValue = 0;
        int highestValue = ordinalMap.length - 1;
        boolean valuesAreSameAsOrdinals = true;
        for (E e : ordinalMap) {
            final int value = e.value();
            if (value != e.ordinal()) {
                valuesAreSameAsOrdinals = false;
            }
            if (value < lowValue) {
                lowValue = value;
            } else if (value > highestValue) {
                highestValue = value;
            }
        }

        if (valuesAreSameAsOrdinals) {
            this.lowestValue = 0;
            valueMap = ordinalMap;
        } else {
            final int valueMapLength = (highestValue - lowValue) + 1;
            final Class<E[]> arrayType = null;
            this.lowestValue = lowValue;
            valueMap = Utils.cast(arrayType, new Enum[valueMapLength]);
            for (E e : ordinalMap) {
                final int value = e.value();
                // The enumerable with the lowest ordinal is stored in the value map:
                if (valueMap[value] == null) {
                    valueMap[value] = e;
                }
            }
        }
    }

    public Class<E> type() {
        return type;
    }

    public int numberOfValues() {
        return ordinalMap.length;
    }

    /**
     * Adds all the enumerable constants in this enumerator to a given set.
     *
     * @param set
     *                the set to which the enumerable constants are to be added
     */
    public void addAll(Set<E> set) {
        for (E e : this) {
            set.add(e);
        }
    }

    public int size() {
        return ordinalMap.length;
    }

    public Iterator<E> iterator() {
        return Arrays.asList(ordinalMap).iterator();
    }

    /**
     * Gets the enumerable constant denoted by a given ordinal. Note that this differs from {@link #fromValue(int)} in
     * that the latter retrieves an enumerable constant matching a given {@linkplain Enumerable#value() value}. An
     * enumerable's value is not necessarily the same as its ordinal.
     *
     * @throws IndexOutOfBoundsException
     *                 if {@code 0 < ordinal || ordinal >= length()}
     */
    public E get(int ordinal) throws IndexOutOfBoundsException {
        return ordinalMap[ordinal];
    }

    /**
     * Gets the enumerable constant matching a given value. That is, this method gets an enumerable from this enumerator
     * whose {@linkplain Enumerable#value() value} is equal to {@code value}. Note that the given value may not match
     * any enumerable in this enumerator in which case null is returned. Additionally, there may be more than one
     * enumerable with a matching value in which case the matching enumerable with the lowest
     * {@linkplain Enum#ordinal() ordinal} is returned.
     */
    public E fromValue(int value) {
        final int index = value - lowestValue;
        if (index >= 0 && index < valueMap.length) {
            return valueMap[index];
        }
        return null;
    }
}
