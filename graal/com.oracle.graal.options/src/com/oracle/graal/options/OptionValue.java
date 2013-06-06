/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.options;

import java.util.*;

/**
 * A settable option value.
 * <p>
 * To access {@link OptionProvider} instances via a {@link ServiceLoader} for working with options,
 * instances of this class should be assigned to static final fields that are annotated with
 * {@link Option}.
 */
public class OptionValue<T> {

    /**
     * The raw option value.
     */
    protected T value;

    private OptionValue(boolean stable, T value) {
        this.value = value;
        this.stable = stable;
    }

    /**
     * Used to assert the invariant for {@link #isStable()} options. Without using locks, this check
     * is not safe against races and so it's only an assertion.
     */
    private boolean getValueCalled;

    /**
     * Creates a {@link #isStable() non-stable} option value.
     * 
     * @param value the initial/default value of the option
     */
    public static <T> OptionValue<T> newOption(T value) {
        return new OptionValue<>(false, value);
    }

    /**
     * Creates a {@link #isStable() stable} option value.
     * 
     * @param value the initial/default value of the option
     */
    public static <T> OptionValue<T> newStableOption(T value) {
        return new OptionValue<>(true, value);
    }

    private static final Object UNINITIALIZED = "UNINITIALIZED";

    private final boolean stable;

    /**
     * Creates an uninitialized option value for a subclass that initializes itself
     * {@link #initialValue() lazily}.
     */
    @SuppressWarnings("unchecked")
    protected OptionValue(boolean stable) {
        this.value = (T) UNINITIALIZED;
        this.stable = stable;
    }

    /**
     * Lazy initialization of value.
     */
    protected T initialValue() {
        throw new InternalError("Uninitialized option value must override initialValue()");
    }

    /**
     * Gets the value of this option.
     */
    public final T getValue() {
        if (value == UNINITIALIZED) {
            value = initialValue();
        }
        assert initGetValueCalled();
        return value;
    }

    private boolean initGetValueCalled() {
        getValueCalled = true;
        return true;
    }

    /**
     * Determines if this option always returns the same {@linkplain #getValue() value}.
     */
    public boolean isStable() {
        return stable;
    }

    /**
     * Sets the value of this option. This can only be called for a {@linkplain #isStable() stable}
     * option if {@link #getValue()} has never been called.
     */
    @SuppressWarnings("unchecked")
    public final void setValue(Object v) {
        assert !getValueCalled || !stable;
        this.value = (T) v;
    }
}
