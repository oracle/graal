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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.Pair;

import com.oracle.svm.common.option.LocatableOption;
import com.oracle.svm.common.option.MultiOptionValue;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ClassUtil;

public abstract class LocatableMultiOptionValue<T> implements MultiOptionValue<T> {

    protected static final String NO_DELIMITER = "";

    private final String delimiter;
    private final Class<T> valueType;
    private final List<Pair<T, String>> values;

    private LocatableMultiOptionValue(Class<T> valueType, String delimiter, List<T> defaults) {
        this.valueType = valueType;
        this.delimiter = delimiter;
        values = new ArrayList<>();
        values.addAll(defaults.stream().map(val -> Pair.<T, String> createLeft(val)).collect(Collectors.toList()));
    }

    private LocatableMultiOptionValue(LocatableMultiOptionValue<T> other) {
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
    public void valueUpdate(Object value) {
        Object rawValue = LocatableOption.rawValue(value);
        String origin = LocatableOption.valueOrigin(value);
        Class<?> rawValueClass = rawValue.getClass();
        boolean multipleElements = rawValueClass.isArray();
        Class<?> rawValueElementType = multipleElements ? rawValueClass.getComponentType() : rawValueClass;
        if (!valueType.isAssignableFrom(rawValueElementType)) {
            VMError.shouldNotReachHere("Cannot update LocatableMultiOptionValue of type " + valueType + " with value of type " + rawValueElementType);
        }
        if (multipleElements) {
            for (Object singleRawValue : (Object[]) rawValue) {
                values.add(Pair.create(valueType.cast(singleRawValue), origin));
            }
        } else {
            values.add(Pair.create(valueType.cast(rawValue), origin));
        }
    }

    @Override
    public List<T> values() {
        return getValuesWithOrigins().map(Pair::getLeft).collect(Collectors.toList());
    }

    @Override
    public Optional<T> lastValue() {
        return lastValueWithOrigin().map(Pair::getLeft);
    }

    public Optional<Pair<T, OptionOrigin>> lastValueWithOrigin() {
        if (values.isEmpty()) {
            return Optional.empty();
        }
        Pair<T, String> pair = values.get(values.size() - 1);
        return Optional.of(Pair.create(pair.getLeft(), OptionOrigin.from(pair.getRight())));
    }

    public Stream<Pair<T, OptionOrigin>> getValuesWithOrigins() {
        if (values.isEmpty()) {
            return Stream.empty();
        }
        return values.stream().map(pair -> Pair.create(pair.getLeft(), OptionOrigin.from(pair.getRight())));
    }

    @Override
    public String toString() {
        return "<" + ClassUtil.getUnqualifiedName(valueType).toLowerCase(Locale.ROOT) + ">*";
    }

    public static final class Strings extends LocatableMultiOptionValue<String> {

        private Strings(Strings other) {
            super(other);
        }

        @Override
        public MultiOptionValue<String> createCopy() {
            return new Strings(this);
        }

        private Strings(String delimiter, List<String> defaultStrings) {
            super(String.class, delimiter, defaultStrings);
        }

        public static Strings build() {
            return new Strings(NO_DELIMITER, List.of());
        }

        public static Strings buildWithCommaDelimiter() {
            return new Strings(",", List.of());
        }

        public static Strings buildWithDefaults(String... defaultStrings) {
            return new Strings(NO_DELIMITER, List.of(defaultStrings));
        }
    }

    public static final class Paths extends LocatableMultiOptionValue<Path> {

        private Paths(Paths other) {
            super(other);
        }

        @Override
        public MultiOptionValue<Path> createCopy() {
            return new Paths(this);
        }

        private Paths(String delimiter, List<Path> defaultPaths) {
            super(Path.class, delimiter, defaultPaths);
        }

        public static Paths build() {
            return new Paths(NO_DELIMITER, List.of());
        }

        public static Paths buildWithCommaDelimiter() {
            return new Paths(",", List.of());
        }

        public static Paths buildWithCustomDelimiter(String delimiter) {
            return new Paths(delimiter, List.of());
        }

        public static Paths buildWithDefaults(Path... defaultPaths) {
            return new Paths(NO_DELIMITER, List.of(defaultPaths));
        }
    }
}
