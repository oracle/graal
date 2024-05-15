/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import org.graalvm.collections.Pair;

import com.oracle.svm.common.option.LocatableOption;
import com.oracle.svm.common.option.MultiOptionValue;
import com.oracle.svm.core.util.VMError;

/**
 * This class and its subclasses have the same behavior as most other parts of our option
 * infrastructure as they replace the values. E.g.
 * {@code native-image --gc serial --gc epsilon HelloWorld} will result in {@code epsilon}.
 */
public abstract class ReplacingLocatableMultiOptionValue<T> extends LocatableMultiOptionValue<T> {

    protected ReplacingLocatableMultiOptionValue(Class<T> valueType, String delimiter, List<T> defaults) {
        super(valueType, delimiter, defaults);
    }

    protected ReplacingLocatableMultiOptionValue(ReplacingLocatableMultiOptionValue<T> other) {
        super(other);
    }

    @Override
    public void valueUpdate(Object value) {
        values.clear();

        Object rawValue = LocatableOption.rawValue(value);

        if (rawValue instanceof ReplacingLocatableMultiOptionValue<?>) {
            ReplacingLocatableMultiOptionValue<?> newOptionValues = ((ReplacingLocatableMultiOptionValue<?>) value);
            if (!valueType.isAssignableFrom(newOptionValues.valueType)) {
                VMError.shouldNotReachHere("Cannot update ReplacingLocatableMultiOptionValue of type " + valueType + " with value of type " + newOptionValues.valueType);
            }

            for (Pair<?, String> p : newOptionValues.values) {
                values.add(Pair.create(valueType.cast(p.getLeft()), p.getRight()));
            }
        } else {

            String origin = LocatableOption.valueOrigin(value);
            Class<?> rawValueClass = rawValue.getClass();
            boolean multipleElements = rawValueClass.isArray();
            Class<?> rawValueElementType = multipleElements ? rawValueClass.getComponentType() : rawValueClass;
            if (!valueType.isAssignableFrom(rawValueElementType)) {
                VMError.shouldNotReachHere("Cannot update ReplacingLocatableMultiOptionValue of type " + valueType + " with value of type " + rawValueElementType);
            }
            if (multipleElements) {
                for (Object singleRawValue : (Object[]) rawValue) {
                    values.add(Pair.create(valueType.cast(singleRawValue), origin));
                }
            } else {
                values.add(Pair.create(valueType.cast(rawValue), origin));
            }
        }
    }

    /**
     * See {@link ReplacingLocatableMultiOptionValue} for details.
     */
    public static final class DelimitedString extends ReplacingLocatableMultiOptionValue<String> {
        private DelimitedString(DelimitedString other) {
            super(other);
        }

        @Override
        public MultiOptionValue<String> createCopy() {
            return new DelimitedString(this);
        }

        public boolean contains(String s) {
            return values.stream().map(Pair::getLeft).anyMatch(val -> val.equals(s));
        }

        private DelimitedString(String delimiter, List<String> defaultStrings) {
            super(String.class, delimiter, defaultStrings);
        }

        public static DelimitedString build() {
            return new DelimitedString(NO_DELIMITER, List.of());
        }

        public static DelimitedString buildWithCommaDelimiter() {
            return new DelimitedString(",", List.of());
        }

        public static DelimitedString buildWithCommaDelimiter(String... defaultStrings) {
            return new DelimitedString(",", List.of(defaultStrings));
        }

        public static DelimitedString buildWithDefaults(String... defaultStrings) {
            return new DelimitedString(NO_DELIMITER, List.of(defaultStrings));
        }
    }

}
