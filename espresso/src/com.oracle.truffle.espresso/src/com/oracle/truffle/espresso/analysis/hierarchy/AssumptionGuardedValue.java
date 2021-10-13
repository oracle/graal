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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.meta.EspressoError;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a value whose correctness is determined by the assumption. The value is safe to use if
 * and only if the assumption is valid.
 *
 * The semantics of the setter might vary, therefore setters are defined in the children of the
 * class.
 * 
 * @param <T> Type of the stored value
 */
abstract class AssumptionGuardedValue<T> {
    @CompilationFinal protected AtomicReference<Assumption> hasValue;
    @CompilationFinal protected AtomicReference<T> value;

    AssumptionGuardedValue(Assumption hasValue, T value) {
        this.hasValue = new AtomicReference<>(hasValue);
        this.value = new AtomicReference<>(value);
    }

    @TruffleBoundary
    private static void reportInvalidValueAccess() {
        throw EspressoError.shouldNotReachHere("Accessed the value behind an invalid assumption");
    }

    public T get() {
        if (!hasValue.get().isValid()) {
            reportInvalidValueAccess();
        }
        return value.get();
    }

    public Assumption hasValue() {
        return hasValue.get();
    }
}
