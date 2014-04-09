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

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

/**
 * An option value.
 */
public class OptionValue<T> {

    /**
     * Temporarily changes the value for an option. The {@linkplain OptionValue#getValue() value} of
     * {@code option} is set to {@code value} until {@link OverrideScope#close()} is called on the
     * object returned by this method.
     * <p>
     * Since the returned object is {@link AutoCloseable} the try-with-resource construct can be
     * used:
     *
     * <pre>
     * try (OverrideScope s = OptionValue.override(myOption, myValue) {
     *     // code that depends on myOption == myValue
     * }
     * </pre>
     */
    public static OverrideScope override(OptionValue<?> option, Object value) {
        OverrideScope current = overrideScopes.get();
        if (current == null) {
            if (!value.equals(option.getValue())) {
                return new SingleOverrideScope(option, value);
            }
            Map<OptionValue<?>, Object> overrides = Collections.emptyMap();
            return new MultipleOverridesScope(current, overrides);
        }
        return new MultipleOverridesScope(current, option, value);
    }

    /**
     * Temporarily changes the values for a set of options. The {@linkplain OptionValue#getValue()
     * value} of each {@code option} in {@code overrides} is set to the corresponding {@code value}
     * in {@code overrides} until {@link OverrideScope#close()} is called on the object returned by
     * this method.
     * <p>
     * Since the returned object is {@link AutoCloseable} the try-with-resource construct can be
     * used:
     *
     * <pre>
     * Map&lt;OptionValue, Object&gt; overrides = new HashMap&lt;&gt;();
     * overrides.put(myOption1, myValue1);
     * overrides.put(myOption2, myValue2);
     * try (OverrideScope s = OptionValue.override(overrides) {
     *     // code that depends on myOption == myValue
     * }
     * </pre>
     */
    public static OverrideScope override(Map<OptionValue<?>, Object> overrides) {
        OverrideScope current = overrideScopes.get();
        if (current == null && overrides.size() == 1) {
            Entry<OptionValue<?>, Object> single = overrides.entrySet().iterator().next();
            OptionValue<?> option = single.getKey();
            Object overrideValue = single.getValue();
            if (!overrideValue.equals(option.getValue())) {
                return new SingleOverrideScope(option, overrideValue);
            }
        }
        return new MultipleOverridesScope(current, overrides);
    }

    /**
     * Temporarily changes the values for a set of options. The {@linkplain OptionValue#getValue()
     * value} of each {@code option} in {@code overrides} is set to the corresponding {@code value}
     * in {@code overrides} until {@link OverrideScope#close()} is called on the object returned by
     * this method.
     * <p>
     * Since the returned object is {@link AutoCloseable} the try-with-resource construct can be
     * used:
     *
     * <pre>
     * try (OverrideScope s = OptionValue.override(myOption1, myValue1, myOption2, myValue2) {
     *     // code that depends on myOption == myValue
     * }
     * </pre>
     *
     * @param overrides overrides in the form {@code [option1, override1, option2, override2, ...]}
     */
    public static OverrideScope override(Object... overrides) {
        OverrideScope current = overrideScopes.get();
        if (current == null && overrides.length == 2) {
            OptionValue<?> option = (OptionValue<?>) overrides[0];
            Object overrideValue = overrides[1];
            if (!overrideValue.equals(option.getValue())) {
                return new SingleOverrideScope(option, overrideValue);
            }
        }
        Map<OptionValue<?>, Object> map = Collections.emptyMap();
        for (int i = 0; i < overrides.length; i += 2) {
            OptionValue<?> option = (OptionValue<?>) overrides[i];
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

    private static final ThreadLocal<OverrideScope> overrideScopes = new ThreadLocal<>();

    /**
     * The raw option value.
     */
    protected T value;

    private OptionDescriptor descriptor;

    private long reads;
    private OptionValue<?> next;
    private static OptionValue<?> head;

    private static final boolean ShowReadsHistogram = Boolean.getBoolean("graal.showOptionValueReadsHistogram");

    private static void addToHistogram(OptionValue<?> option) {
        if (ShowReadsHistogram) {
            synchronized (OptionValue.class) {
                option.next = head;
                head = option;
            }
        }
    }

    public OptionValue(T value) {
        this.value = value;
        addToHistogram(this);
    }

    private static final Object UNINITIALIZED = "UNINITIALIZED";

    /**
     * Creates an uninitialized option value for a subclass that initializes itself
     * {@link #initialValue() lazily}.
     */
    @SuppressWarnings("unchecked")
    protected OptionValue() {
        this.value = (T) UNINITIALIZED;
        addToHistogram(this);
    }

    /**
     * Lazy initialization of value.
     */
    protected T initialValue() {
        throw new InternalError("Uninitialized option value must override initialValue()");
    }

    /**
     * Sets the descriptor for this option.
     */
    public void setDescriptor(OptionDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    /**
     * Gets the name of this option. The name for an option value with a null
     * {@linkplain #setDescriptor(OptionDescriptor) descriptor} is the value of
     * {@link Object#toString()}.
     */
    public String getName() {
        return descriptor == null ? super.toString() : (descriptor.getDeclaringClass().getName() + "." + descriptor.getName());
    }

    @Override
    public String toString() {
        return getName() + "=" + getValue();
    }

    /**
     * Gets the value of this option.
     */
    public T getValue() {
        if (ShowReadsHistogram) {
            reads++;
        }
        if (!(this instanceof StableOptionValue)) {
            OverrideScope overrideScope = overrideScopes.get();
            if (overrideScope != null) {
                T override = overrideScope.getOverride(this);
                if (override != null) {
                    return override;
                }
            }
        }
        if (value == UNINITIALIZED) {
            value = initialValue();
        }
        return value;
    }

    /**
     * Gets the values of this option including overridden values.
     *
     * @param c the collection to which the values are added. If null, one is allocated.
     * @return the collection to which the values were added in order from most overridden to
     *         current value
     */
    @SuppressWarnings("unchecked")
    public Collection<T> getValues(Collection<T> c) {
        Collection<T> values = c == null ? new ArrayList<>() : c;
        if (!(this instanceof StableOptionValue)) {
            OverrideScope overrideScope = overrideScopes.get();
            if (overrideScope != null) {
                overrideScope.getOverrides(this, (Collection<Object>) values);
            }
        }
        values.add(value);
        return values;
    }

    /**
     * Sets the value of this option.
     */
    @SuppressWarnings("unchecked")
    public void setValue(Object v) {
        this.value = (T) v;
    }

    /**
     * An object whose {@link #close()} method reverts the option value overriding initiated by
     * {@link OptionValue#override(OptionValue, Object)} or {@link OptionValue#override(Map)}.
     */
    public abstract static class OverrideScope implements AutoCloseable {
        abstract void addToInherited(Map<OptionValue<?>, Object> inherited);

        abstract <T> T getOverride(OptionValue<T> option);

        abstract void getOverrides(OptionValue<?> option, Collection<Object> c);

        public abstract void close();
    }

    static class SingleOverrideScope extends OverrideScope {

        private final OptionValue<?> option;
        private final Object value;

        public SingleOverrideScope(OptionValue<?> option, Object value) {
            if (option instanceof StableOptionValue) {
                throw new IllegalArgumentException("Cannot override stable option " + option);
            }
            this.option = option;
            this.value = value;
            overrideScopes.set(this);
        }

        @Override
        void addToInherited(Map<OptionValue<?>, Object> inherited) {
            inherited.put(option, value);
        }

        @SuppressWarnings("unchecked")
        @Override
        <T> T getOverride(OptionValue<T> key) {
            if (key == this.option) {
                return (T) value;
            }
            return null;
        }

        @Override
        void getOverrides(OptionValue<?> key, Collection<Object> c) {
            if (key == this.option) {
                c.add(value);
            }
        }

        @Override
        public void close() {
            overrideScopes.set(null);
        }
    }

    static class MultipleOverridesScope extends OverrideScope {
        final OverrideScope parent;
        final Map<OptionValue<?>, Object> overrides;

        public MultipleOverridesScope(OverrideScope parent, OptionValue<?> option, Object value) {
            this.parent = parent;
            this.overrides = new HashMap<>();
            if (parent != null) {
                parent.addToInherited(overrides);
            }
            if (option instanceof StableOptionValue) {
                throw new IllegalArgumentException("Cannot override stable option " + option);
            }
            if (!value.equals(option.getValue())) {
                this.overrides.put(option, value);
            }
            if (!overrides.isEmpty()) {
                overrideScopes.set(this);
            }
        }

        MultipleOverridesScope(OverrideScope parent, Map<OptionValue<?>, Object> overrides) {
            this.parent = parent;
            if (overrides.isEmpty() && parent == null) {
                this.overrides = Collections.emptyMap();
                return;
            }
            this.overrides = new HashMap<>();
            if (parent != null) {
                parent.addToInherited(this.overrides);
            }
            for (Map.Entry<OptionValue<?>, Object> e : overrides.entrySet()) {
                OptionValue<?> option = e.getKey();
                if (option instanceof StableOptionValue) {
                    throw new IllegalArgumentException("Cannot override stable option " + option);
                }
                if (!e.getValue().equals(option.getValue())) {
                    this.overrides.put(option, e.getValue());
                }
            }
            if (!this.overrides.isEmpty()) {
                overrideScopes.set(this);
            }
        }

        @Override
        void addToInherited(Map<OptionValue<?>, Object> inherited) {
            if (parent != null) {
                parent.addToInherited(inherited);
            }
            inherited.putAll(overrides);
        }

        @SuppressWarnings("unchecked")
        @Override
        <T> T getOverride(OptionValue<T> option) {
            return (T) overrides.get(option);
        }

        @Override
        void getOverrides(OptionValue<?> option, Collection<Object> c) {
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
                overrideScopes.set(parent);
            }
        }
    }

    static {
        if (ShowReadsHistogram) {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    ArrayList<OptionValue<?>> options = new ArrayList<>();
                    for (OptionValue<?> option = head; option != null; option = option.next) {
                        options.add(option);
                    }
                    Collections.sort(options, new Comparator<OptionValue<?>>() {

                        public int compare(OptionValue<?> o1, OptionValue<?> o2) {
                            if (o1.reads < o2.reads) {
                                return -1;
                            } else if (o1.reads > o2.reads) {
                                return 1;
                            } else {
                                return o1.getName().compareTo(o2.getName());
                            }
                        }
                    });
                    PrintStream out = System.out;
                    out.println("=== OptionValue reads histogram ===");
                    for (OptionValue<?> option : options) {
                        out.println(option.reads + "\t" + option);
                    }
                }
            });
        }
    }
}
