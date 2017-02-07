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
package org.graalvm.compiler.options;

import java.util.ServiceLoader;

import org.graalvm.util.EconomicMap;

/**
 * A key for an option. The value for an option is obtained from an {@link OptionValues} object.
 */
public class OptionKey<T> {

    private T defaultValue;

    private OptionDescriptor descriptor;

    public OptionKey(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    private static final Object UNINITIALIZED = "UNINITIALIZED";

    /**
     * Creates an uninitialized option value for a subclass that initializes itself
     * {@link #defaultValue() lazily}.
     */
    @SuppressWarnings("unchecked")
    protected OptionKey() {
        this.defaultValue = (T) UNINITIALIZED;
    }

    private static final Error NO_DEFAULT_VALUE = new InternalError("Option without a default value value must override defaultValue()");

    /**
     * Lazy initialization of default value.
     */
    protected T defaultValue() {
        /* We want this method to be allocation free. */
        throw NO_DEFAULT_VALUE;
    }

    /**
     * Sets the descriptor for this option.
     */
    public final void setDescriptor(OptionDescriptor descriptor) {
        assert this.descriptor == null : "Overwriting existing descriptor";
        this.descriptor = descriptor;
    }

    /**
     * Returns the descriptor for this option, if it has been set by
     * {@link #setDescriptor(OptionDescriptor)}.
     */
    public final OptionDescriptor getDescriptor() {
        return descriptor;
    }

    /**
     * Mechanism for lazily loading all available options which has the side effect of assigning
     * names to the options.
     */
    static class Lazy {
        static {
            ServiceLoader<OptionDescriptors> loader = ServiceLoader.load(OptionDescriptors.class, OptionDescriptors.class.getClassLoader());
            for (OptionDescriptors opts : loader) {
                for (OptionDescriptor desc : opts) {
                    desc.getName();
                }
            }
        }

        static void init() {
            /* Running the static class initializer does all the initialization. */
        }
    }

    /**
     * Gets the name of this option. The name for an option value with a null
     * {@linkplain #setDescriptor(OptionDescriptor) descriptor} is the value of
     * {@link Object#toString()}.
     */
    public final String getName() {
        if (descriptor == null) {
            // Trigger initialization of OptionsLoader to ensure all option values have
            // a descriptor which is required for them to have meaningful names.
            Lazy.init();
        }
        return descriptor == null ? super.toString() : descriptor.getName();
    }

    @Override
    public String toString() {
        return getName();
    }

    /**
     * The initial value specified in source code. The returned value is not affected by options set
     * on the command line.
     */
    public final T getDefaultValue() {
        if (defaultValue == UNINITIALIZED) {
            defaultValue = defaultValue();
        }
        return defaultValue;
    }

    /**
     * Returns true if the option has been set in any way. Note that this doesn't mean that the
     * current value is different than the default.
     */
    public boolean hasBeenSet(OptionValues values) {
        return values.containsKey(this);
    }

    /**
     * Gets the value of this option in {@code values}.
     */
    public T getValue(OptionValues values) {
        return values.get(this);
    }

    /**
     * Sets the value of this option in a given map. The
     * {@link #onValueUpdate(EconomicMap, Object, Object)} method is called once the value is set.
     *
     * @param values map of option values
     * @param v the value to set for this key in {@code map}
     */
    @SuppressWarnings("unchecked")
    public void update(EconomicMap<OptionKey<?>, Object> values, Object v) {
        T oldValue = (T) values.put(this, v);
        onValueUpdate(values, oldValue, (T) v);
    }

    /**
     * Notifies this object when a value associated with this key is set or updated in
     * {@code values}.
     *
     * @param values
     * @param oldValue
     * @param newValue
     */
    protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, T oldValue, T newValue) {
    }
}
