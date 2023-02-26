/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.analysis.hierarchy;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;

/**
 * Represents an immutable value whose correctness is determined by the assumption. The value is
 * safe to use if and only if the assumption is valid.
 *
 * @param <T> Type of the stored value
 */
public final class AssumptionGuardedValue<T> {
    private final Assumption hasValue;
    final T value;

    public static <T> AssumptionGuardedValue<T> create(T value) {
        CompilerAsserts.neverPartOfCompilation();
        if (value == null) {
            throw reportInvalidValue();
        }
        return new AssumptionGuardedValue<>(Truffle.getRuntime().createAssumption(), value);
    }

    private static NullPointerException reportInvalidValue() {
        throw new NullPointerException("null is reserved for invalid value");
    }

    public static <T> AssumptionGuardedValue<T> createInvalid() {
        CompilerAsserts.neverPartOfCompilation();
        return new AssumptionGuardedValue<>(Assumption.NEVER_VALID, null);
    }

    private AssumptionGuardedValue(Assumption hasValue, T value) {
        this.hasValue = hasValue;
        this.value = value;
    }

    public Assumption hasValue() {
        return hasValue;
    }

    public T get() {
        if (!hasValue.isValid()) {
            return null;
        }
        return value;
    }
}
