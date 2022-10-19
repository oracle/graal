/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.options;

import java.util.EnumSet;

import org.graalvm.collections.EconomicSet;

/**
 * An option key whose value is a subset of enum values. The raw option value is parsed as a
 * comma-separated list of enum values (i.e., their string representations).
 *
 * @param <T> the type of the enum
 */
public class EnumMultiOptionKey<T extends Enum<T>> extends OptionKey<EconomicSet<T>> {
    private final Class<T> enumClass;

    public EnumMultiOptionKey(Class<T> enumClass, EconomicSet<T> defaultValue) {
        super(defaultValue);
        this.enumClass = enumClass;
    }

    public Class<T> getEnumClass() {
        return enumClass;
    }

    /**
     * Returns the set of possible values for this option.
     */
    public EnumSet<T> getAllValues() {
        return EnumSet.allOf(enumClass);
    }

    public Object valueOf(String name) {
        EconomicSet<T> value = EconomicSet.create();
        if (name.isEmpty()) {
            return value;
        }
        String[] names = name.split(",");
        for (String splitName : names) {
            try {
                value.add(Enum.valueOf(enumClass, splitName));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("\"" + splitName + "\" is not a valid option for " + getName() + ". Valid values are " + getAllValues());
            }
        }
        return value;
    }
}
