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

import static com.oracle.graal.options.OptionValues.GLOBAL;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ServiceLoader;

/**
 * An option value.
 */
public class OptionKey<T> {
    /**
     * Temporarily changes the value for an option. The {@linkplain OptionKey#getValue() value} of
     * {@code option} is set to {@code value} until {@link OverrideScope#close()} is called on the
     * object returned by this method.
     * <p>
     * Since the returned object is {@link AutoCloseable} the try-with-resource construct can be
     * used:
     *
     * <pre>
     * try (OverrideScope s = OptionKey.override(myOption, myValue)) {
     *     // code that depends on myOption == myValue
     * }
     * </pre>
     */
    public static OverrideScope override(OptionKey<?> option, Object value) {
        OverrideScope current = getOverrideScope();
        if (current == null) {
            if (!value.equals(option.getValue())) {
                return new SingleOverrideScope(option, value);
            }
            Map<OptionKey<?>, Object> overrides = Collections.emptyMap();
            return new MultipleOverridesScope(current, overrides);
        }
        return new MultipleOverridesScope(current, option, value);
    }

    /**
     * Temporarily changes the values for a set of options. The {@linkplain OptionKey#getValue()
     * value} of each {@code option} in {@code overrides} is set to the corresponding {@code value}
     * in {@code overrides} until {@link OverrideScope#close()} is called on the object returned by
     * this method.
     * <p>
     * Since the returned object is {@link AutoCloseable} the try-with-resource construct can be
     * used:
     *
     * <pre>
     * Map&lt;OptionKey, Object&gt; overrides = new HashMap&lt;&gt;();
     * overrides.put(myOption1, myValue1);
     * overrides.put(myOption2, myValue2);
     * try (OverrideScope s = OptionKey.override(overrides)) {
     *     // code that depends on myOption == myValue
     * }
     * </pre>
     */
    public static OverrideScope override(Map<OptionKey<?>, Object> overrides) {
        OverrideScope current = getOverrideScope();
        if (current == null && overrides.size() == 1) {
            Entry<OptionKey<?>, Object> single = overrides.entrySet().iterator().next();
            OptionKey<?> option = single.getKey();
            Object overrideValue = single.getValue();
            return new SingleOverrideScope(option, overrideValue);
        }
        return new MultipleOverridesScope(current, overrides);
    }

    /**
     * Temporarily changes the values for a set of options. The {@linkplain OptionKey#getValue()
     * value} of each {@code option} in {@code overrides} is set to the corresponding {@code value}
     * in {@code overrides} until {@link OverrideScope#close()} is called on the object returned by
     * this method.
     * <p>
     * Since the returned object is {@link AutoCloseable} the try-with-resource construct can be
     * used:
     *
     * <pre>
     * try (OverrideScope s = OptionKey.override(myOption1, myValue1, myOption2, myValue2)) {
     *     // code that depends on myOption == myValue
     * }
     * </pre>
     *
     * @param overrides overrides in the form {@code [option1, override1, option2, override2, ...]}
     */
    public static OverrideScope override(Object... overrides) {
        OverrideScope current = getOverrideScope();
        if (current == null && overrides.length == 2) {
            OptionKey<?> option = (OptionKey<?>) overrides[0];
            Object overrideValue = overrides[1];
            if (!overrideValue.equals(option.getValue())) {
                return new SingleOverrideScope(option, overrideValue);
            }
        }
        Map<OptionKey<?>, Object> map = Collections.emptyMap();
        for (int i = 0; i < overrides.length; i += 2) {
            OptionKey<?> option = (OptionKey<?>) overrides[i];
            Object overrideValue = overrides[i + 1];
            if (!overrideValue.equals(option.getValue())) {
                if (map.isEmpty()) {
                    map = new HashMap<>();
                }
                map.put(option, overrideValue);
            }
        }
        return new MultipleOverridesScope(current, map);
    }

    private static final ThreadLocal<OverrideScope> overrideScopeTL = new ThreadLocal<>();

    protected static OverrideScope getOverrideScope() {
        return overrideScopeTL.get();
    }

    protected static void setOverrideScope(OverrideScope overrideScope) {
        overrideScopeTL.set(overrideScope);
    }

    private T defaultValue;

    private OptionDescriptor descriptor;

    public OptionKey(T value) {
        this.defaultValue = value;
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

    /**
     * Lazy initialization of default value.
     */
    protected T defaultValue() {
        throw new InternalError("Option without a default value value must override defaultValue()");
    }

    /**
     * Sets the descriptor for this option.
     */
    public void setDescriptor(OptionDescriptor descriptor) {
        assert this.descriptor == null : "Overwriting existing descriptor";
        this.descriptor = descriptor;
    }

    /**
     * Returns the descriptor for this option, if it has been set by
     * {@link #setDescriptor(OptionDescriptor)}.
     */
    public OptionDescriptor getDescriptor() {
        return descriptor;
    }

    /**
     * Mechanism for lazily loading all available options which has the side effect of assigning
     * names to the options.
     */
    static class Lazy {
        static void init() {
            ServiceLoader<OptionDescriptors> loader = ServiceLoader.load(OptionDescriptors.class, OptionDescriptors.class.getClassLoader());
            for (OptionDescriptors opts : loader) {
                for (OptionDescriptor desc : opts) {
                    desc.getName();
                }
            }
        }
    }

    /**
     * Gets the name of this option. The name for an option value with a null
     * {@linkplain #setDescriptor(OptionDescriptor) descriptor} is the value of
     * {@link Object#toString()}.
     */
    public String getName() {
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
     * The initial value specified in source code. The returned value is not affected by calls to
     * {@link #setValue(Object)} or registering {@link OverrideScope}s. Therefore, it is also not
     * affected by options set on the command line.
     */
    public T getDefaultValue() {
        if (defaultValue == UNINITIALIZED) {
            defaultValue = defaultValue();
        }
        return defaultValue;
    }

    /**
     * Returns true if the option has been set in any way. Note that this doesn't mean that the
     * current value is different than the default.
     */
    public boolean hasBeenSet() {
        if (!(this instanceof StableOptionKey)) {
            getValue(); // ensure initialized

            OverrideScope overrideScope = getOverrideScope();
            if (overrideScope != null) {
                T override = overrideScope.getOverride(this);
                if (override != null) {
                    return true;
                }
            }
        }
        return GLOBAL.containsKey(this);
    }

    /**
     * Gets the value of this option.
     */
    @SuppressWarnings("unchecked")
    public T getValue() {
        if (!(this instanceof StableOptionKey)) {
            OverrideScope overrideScope = getOverrideScope();
            if (overrideScope != null) {
                T override = overrideScope.getOverride(this);
                if (override != null) {
                    return override;
                }
            }
        }
        if (GLOBAL.containsKey(this)) {
            return (T) GLOBAL.get(this);
        }
        return getDefaultValue();
    }

    /**
     * Sets the value of this option.
     */
    public void setValue(Object v) {
        GLOBAL.set(this, v);
    }

    /**
     * An object whose {@link #close()} method reverts the option value overriding initiated by
     * {@link OptionKey#override(OptionKey, Object)} or {@link OptionKey#override(Map)}.
     */
    public abstract static class OverrideScope implements AutoCloseable {

        private Map<DerivedOptionValue<?>, Object> derivedCache = null;

        public <T> T getDerived(DerivedOptionValue<T> key) {
            if (derivedCache == null) {
                derivedCache = new HashMap<>();
            }
            @SuppressWarnings("unchecked")
            T ret = (T) derivedCache.get(key);
            if (ret == null) {
                ret = key.createValue();
                derivedCache.put(key, ret);
            }
            return ret;
        }

        abstract void addToInherited(Map<OptionKey<?>, Object> inherited);

        abstract <T> T getOverride(OptionKey<T> option);

        abstract void getOverrides(OptionKey<?> option, Collection<Object> c);

        @Override
        public abstract void close();
    }

    static class SingleOverrideScope extends OverrideScope {

        private final OptionKey<?> option;
        private final Object value;

        SingleOverrideScope(OptionKey<?> option, Object value) {
            if (option instanceof StableOptionKey) {
                throw new IllegalArgumentException("Cannot override stable option " + option);
            }
            this.option = option;
            this.value = value;
            setOverrideScope(this);
        }

        @Override
        void addToInherited(Map<OptionKey<?>, Object> inherited) {
            inherited.put(option, value);
        }

        @SuppressWarnings("unchecked")
        @Override
        <T> T getOverride(OptionKey<T> key) {
            if (key == this.option) {
                return (T) value;
            }
            return null;
        }

        @Override
        void getOverrides(OptionKey<?> key, Collection<Object> c) {
            if (key == this.option) {
                c.add(value);
            }
        }

        @Override
        public void close() {
            setOverrideScope(null);
        }
    }

    static class MultipleOverridesScope extends OverrideScope {
        final OverrideScope parent;
        final Map<OptionKey<?>, Object> overrides;

        MultipleOverridesScope(OverrideScope parent, OptionKey<?> option, Object value) {
            this.parent = parent;
            this.overrides = new HashMap<>();
            if (parent != null) {
                parent.addToInherited(overrides);
            }
            if (option instanceof StableOptionKey) {
                throw new IllegalArgumentException("Cannot override stable option " + option);
            }
            if (!value.equals(option.getValue())) {
                this.overrides.put(option, value);
            }
            if (!overrides.isEmpty()) {
                setOverrideScope(this);
            }
        }

        MultipleOverridesScope(OverrideScope parent, Map<OptionKey<?>, Object> overrides) {
            this.parent = parent;
            if (overrides.isEmpty() && parent == null) {
                this.overrides = Collections.emptyMap();
                return;
            }
            this.overrides = new HashMap<>();
            if (parent != null) {
                parent.addToInherited(this.overrides);
            }
            for (Map.Entry<OptionKey<?>, Object> e : overrides.entrySet()) {
                OptionKey<?> option = e.getKey();
                if (option instanceof StableOptionKey) {
                    throw new IllegalArgumentException("Cannot override stable option " + option);
                }
                this.overrides.put(option, e.getValue());
            }
            if (!this.overrides.isEmpty()) {
                setOverrideScope(this);
            }
        }

        @Override
        void addToInherited(Map<OptionKey<?>, Object> inherited) {
            if (parent != null) {
                parent.addToInherited(inherited);
            }
            inherited.putAll(overrides);
        }

        @SuppressWarnings("unchecked")
        @Override
        <T> T getOverride(OptionKey<T> option) {
            return (T) overrides.get(option);
        }

        @Override
        void getOverrides(OptionKey<?> option, Collection<Object> c) {
            Object v = overrides.get(option);
            if (v != null) {
                c.add(v);
            }
            if (parent != null) {
                parent.getOverrides(option, c);
            }
        }

        @Override
        public void close() {
            if (!overrides.isEmpty()) {
                setOverrideScope(parent);
            }
        }
    }
}
