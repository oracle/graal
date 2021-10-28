/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.option;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.svm.common.option.LocatableOption;
import com.oracle.svm.common.option.MultiOptionValue;
import org.graalvm.collections.Pair;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ClassUtil;

public abstract class LocatableMultiOptionValue<T> implements MultiOptionValue<T> {

    private final Class<T> valueType;
    private final List<Pair<T, String>> values;

    private LocatableMultiOptionValue(Class<T> valueType) {
        this.valueType = valueType;
        values = new ArrayList<>();
    }

    private LocatableMultiOptionValue(Class<T> valueType, List<T> defaults) {
        this(valueType);
        values.addAll(defaults.stream().map(val -> Pair.create(val, "default")).collect(Collectors.toList()));
    }

    private LocatableMultiOptionValue(LocatableMultiOptionValue<T> other) {
        this.valueType = other.valueType;
        this.values = new ArrayList<>(other.values);
    }

    @Override
    public Class<T> getValueType() {
        return valueType;
    }

    @Override
    public void valueUpdate(Object value) {
        Object rawValue = LocatableOption.rawValue(value);
        String origin = LocatableOption.valueOrigin(value);
        if (!valueType.isInstance(rawValue)) {
            VMError.shouldNotReachHere("Cannot update LocatableMultiOptionValue of type " + valueType + " with value of type " + rawValue.getClass());
        }
        values.add(Pair.create(valueType.cast(rawValue), origin));
    }

    @Override
    public List<T> values() {
        return values.stream().map(Pair::getLeft).collect(Collectors.toList());
    }

    public Stream<Pair<T, String>> getValuesWithOrigins() {
        return values.stream();
    }

    @Override
    public String toString() {
        return "<" + ClassUtil.getUnqualifiedName(valueType).toLowerCase() + ">*";
    }

    public static class Strings extends LocatableMultiOptionValue<String> {

        private Strings(Strings other) {
            super(other);
        }

        @Override
        public MultiOptionValue<String> createCopy() {
            return new Strings(this);
        }

        public Strings() {
            super(String.class);
        }

        public Strings(List<String> defaultStrings) {
            super(String.class, defaultStrings);
        }
    }
}
