/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.svm.common.option.MultiOptionValue;
import com.oracle.svm.util.ClassUtil;

public abstract class LocatableMultiOptionValue<T> implements MultiOptionValue<T> {

    public record ValueWithOrigin<T>(T value, OptionOrigin origin) {
    }

    protected static final String NO_DELIMITER = "";

    private final String delimiter;
    protected final Class<T> valueType;
    protected final List<ValueWithOrigin<T>> values;

    protected LocatableMultiOptionValue(Class<T> valueType, String delimiter, List<T> defaults) {
        this.valueType = valueType;
        this.delimiter = delimiter;
        values = new ArrayList<>();
        values.addAll(defaults.stream().map(val -> new ValueWithOrigin<>(val, OptionOrigin.from(null))).collect(Collectors.toList()));
    }

    protected LocatableMultiOptionValue(LocatableMultiOptionValue<T> other) {
        this.valueType = other.valueType;
        this.delimiter = other.delimiter;
        this.values = new ArrayList<>(other.values);
    }

    @Override
    public Class<T> getValueType() {
        return valueType;
    }

    @Override
    public String getDelimiter() {
        return delimiter;
    }

    @Override
    public List<T> values() {
        return getValuesWithOrigins().map(ValueWithOrigin::value).collect(Collectors.toList());
    }

    public Set<T> valuesAsSet() {
        return getValuesWithOrigins().map(ValueWithOrigin::value).collect(Collectors.toSet());
    }

    @Override
    public Optional<T> lastValue() {
        return lastValueWithOrigin().map(ValueWithOrigin::value);
    }

    public Optional<ValueWithOrigin<T>> lastValueWithOrigin() {
        return values.isEmpty() ? Optional.empty() : Optional.of(values.getLast());
    }

    public Stream<ValueWithOrigin<T>> getValuesWithOrigins() {
        return values.stream();
    }

    @Override
    public String toString() {
        return "<" + ClassUtil.getUnqualifiedName(valueType).toLowerCase(Locale.ROOT) + ">*";
    }

}
