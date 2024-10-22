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
import java.util.List;

import com.oracle.svm.common.option.LocatableOption;
import com.oracle.svm.common.option.MultiOptionValue;
import com.oracle.svm.core.util.VMError;

public abstract class AccumulatingLocatableMultiOptionValue<T> extends LocatableMultiOptionValue<T> {

    protected AccumulatingLocatableMultiOptionValue(Class<T> valueType, String delimiter, List<T> defaults) {
        super(valueType, delimiter, defaults);
    }

    private AccumulatingLocatableMultiOptionValue(AccumulatingLocatableMultiOptionValue<T> other) {
        super(other);
    }

    @Override
    public void valueUpdate(Object value) {
        Object rawValue = LocatableOption.rawValue(value);
        OptionOrigin origin = OptionOrigin.from(LocatableOption.valueOrigin(value));
        Class<?> rawValueClass = rawValue.getClass();
        boolean multipleElements = rawValueClass.isArray();
        Class<?> rawValueElementType = multipleElements ? rawValueClass.getComponentType() : rawValueClass;
        if (!valueType.isAssignableFrom(rawValueElementType)) {
            VMError.shouldNotReachHere("Cannot update LocatableMultiOptionValue of type " + valueType + " with value of type " + rawValueElementType);
        }
        if (multipleElements) {
            for (Object singleRawValue : (Object[]) rawValue) {
                values.add(new ValueWithOrigin<>(valueType.cast(singleRawValue), origin));
            }
        } else {
            values.add(new ValueWithOrigin<>(valueType.cast(rawValue), origin));
        }
    }

    public static final class Strings extends AccumulatingLocatableMultiOptionValue<String> {

        private Strings(Strings other) {
            super(other);
        }

        @Override
        public MultiOptionValue<String> createCopy() {
            return new Strings(this);
        }

        @Override
        public void valueUpdate(Object value) {
            if (value instanceof Strings) {
                values.addAll(((Strings) value).values);
                return;
            }
            super.valueUpdate(value);
        }

        public boolean contains(String s) {
            return values.stream().map(ValueWithOrigin::value).anyMatch(val -> val.equals(s));
        }

        public void removeFirst() {
            values.removeFirst();
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

        public static Strings buildWithCommaDelimiter(String... defaultStrings) {
            return new Strings(",", List.of(defaultStrings));
        }

        public static Strings buildWithDefaults(String... defaultStrings) {
            return new Strings(NO_DELIMITER, List.of(defaultStrings));
        }
    }

    public static final class Paths extends AccumulatingLocatableMultiOptionValue<Path> {

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
