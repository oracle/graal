/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.script;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Represents a shared environment to execute scripts. Script implementations
 * may use the context object to track/share data.
 * <p/>
 * If script execution is canceled, the environment is not cleared. The caller may provide
 * a subclass which connects scripts to the caller's variables etc.
 *
 * @author sdedic
 */
public abstract class ScriptEnvironment implements AutoCloseable {
    /**
     * Sets a value. Returns the previous definition of the value. Set {@code null}
     * to remove the value from the mapping.
     *
     * @param <T> type of the data
     * @param key key identifier
     * @param val the new value
     * @return the previous value, or {@code null} if no value was defined
     */
    public abstract <T> T setValue(Object key, T val);

    /**
     * Sets a value. Returns the previous definition of the value.
     * Creates a value using the provided Supplied, if there's no value associated
     * for the key. Returns either the existing value, or Supplier's result; the
     * result is also added to the value dictionary under the "key".
     *
     * @param <T> type of the data
     * @param key key identifier
     * @param s   supplied for the new value
     * @param <X> thrown exception
     * @return the previous value, or the result of {@link Supplier#get()}.
     */
    public final <T, X extends Throwable> T createValue(Object key, ValueSupplier<T, X> s) throws X {
        T t = getValue(key);
        if (t == null) {
            t = s.get();
            setValue(key, t);
        }
        return t;
    }

    /**
     * Supplier interface which allows to throw/propagate ane exception
     *
     * @param <T> type that should be created
     * @param <X> exception that can be thrown
     */
    public interface ValueSupplier<T, X extends Throwable> {
        public T get() throws X;
    }

    /**
     * Retrieves a value for the key. Returns {@code null}, if the value
     * is not defined
     *
     * @param <T> type of the data
     * @param key the identifier
     * @return value or {@code null}
     */
    public abstract <T> T getValue(Object key);

    /**
     * Provides read-only access to keys. Returns snapshot; values can be
     * changed without invalidating the set.
     *
     * @return set of current keys
     */
    public abstract Set keys();

    /**
     * Creates a very simple Map-based ScriptEnvironment.
     *
     * @return simple implementation of ScriptEnvironment.
     */
    public static final ScriptEnvironment simple() {
        return new ScriptEnvironment() {
            private final Map values = new HashMap<>();

            @Override
            public <T> T setValue(Object key, T val) {
                return (T) values.put(key, val);
            }

            @Override
            public <T> T getValue(Object key) {
                return (T) values.get(key);
            }

            @Override
            public Iterable values() {
                return values.values();
            }

            @Override
            public Set keys() {
                return new HashSet<>(values.keySet());
            }
        };
    }

    public abstract Iterable values();

    /**
     * Closes the environment. If some of the values held implement {@link Closeable},
     * their {@link Closeable#close} will be called.
     *
     * @throws IOException thrown by called close()s
     */
    public void close() throws IOException {
        for (Object v : values()) {
            if (v instanceof Closeable) {
                ((Closeable) v).close();
            }
        }
    }
}
