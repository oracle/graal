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
package com.oracle.svm.core.util.json;

import java.io.IOException;
import java.util.ConcurrentModificationException;

/**
 * Builds a single well-structured JSON object step-by-step without having to care about the correct
 * use of separators and remembering to close brackets.
 * <p>
 * All data is directly written to the underlying {@link JsonWriter}. No in-memory JSON objects are
 * built.
 * <p>
 * The JSON structure is enforced through the use of nested builders which are responsible for
 * producing a valid JSON value. The heavy-lifting is done by these instances
 * ({@link ObjectBuilder}, {@link ArrayBuilder}, and {@link ValueBuilder}), which allow users to
 * gradually build complete JSON values without first gathering all data in a temporary
 * datastructure.
 * <p>
 * Only the innermost nested builder can be used. Using any outer instances may produce malformed
 * JSON. If that contract is violated (e.g. by not properly closing the builders or using one of the
 * outer builders), a {@link ConcurrentModificationException} is thrown.
 * <p>
 * Example:
 *
 * <pre>{@code
 * try (var objectBuilder = jsonWriter.objectBuilder()) {
 *     objectBuilder.append("key", "value");
 *     try (var arrayBuilder = objectBuilder.append("arrayKey").array()) {
 *         // Using objectBuilder in here would cause an exception
 *         arrayBuilder.append(1).append(4);
 *     }
 * }
 * }</pre>
 *
 * Produces (pretty-printed for readability):
 *
 * <pre>{@code
 * {
 *     "key": "value",
 *     "arrayKey": [1, 4]
 * }
 * }</pre>
 */
public final class JsonBuilder {

    private final JsonWriter writer;

    /**
     * Only a single {@link ExclusiveBuilder} instance can use {@link #writer} at any time; holds a
     * reference to that instance.
     * <p>
     * If {@code null}, there is currently no partial writes in progress (e.g. fresh instance or a
     * complete top-level JSON value was written).
     * <p>
     * If another instance attempts to write, a {@link ConcurrentModificationException} is thrown
     * (see {@link ExclusiveBuilder#checkAccess()}).
     */
    private ExclusiveBuilder currentBuilder = null;

    private JsonBuilder(JsonWriter writer) {
        this.writer = writer;
    }

    /**
     * Start building a JSON object.
     *
     * @see ObjectBuilder
     */
    static ObjectBuilder object(JsonWriter writer) throws IOException {
        return value(writer).object();
    }

    /**
     * Start building a JSON array.
     *
     * @see ArrayBuilder
     */
    static ArrayBuilder array(JsonWriter writer) throws IOException {
        return value(writer).array();
    }

    /**
     * Start building an arbitrary JSON value.
     *
     * @see ValueBuilder
     */
    static ValueBuilder value(JsonWriter writer) {
        return new JsonBuilder(writer).new ValueBuilder(null);
    }

    private void transferAccess(ExclusiveBuilder from, ExclusiveBuilder to) {
        assert currentBuilder == from && from != to;
        currentBuilder = to;
    }

    private abstract class ExclusiveBuilder {

        private final ExclusiveBuilder parent;

        private boolean closed = false;

        private ExclusiveBuilder(ExclusiveBuilder parent) {
            this.parent = parent;
            transferAccess(parent, this);
        }

        protected final void checkAccess() {
            if (closed) {
                throw new IllegalStateException("%s instance is already closed".formatted(this.getClass()));
            }

            if (this != currentBuilder) {
                throw new ConcurrentModificationException("%s instance is not currently responsible for printing".formatted(this.getClass()));
            }
        }

        /**
         * Called when this instance has finished writing a complete JSON value.
         * <p>
         * Exclusive access is transferred to the parent again. If the parent is a
         * {@link ValueBuilder}, its {@code finish} method is also called since it can only produce
         * a single value.
         */
        protected void finish() throws IOException {
            checkAccess();
            transferAccess(this, parent);
            closed = true;

            if (parent instanceof ValueBuilder) {
                parent.finish();
            }
        }
    }

    /**
     * Builds a single well-formed JSON object.
     * <p>
     * Instance must be closed when all key-values are written.
     * <p>
     * Entries can be appended using either {@link #append(String, Object)} or
     * {@link #append(String)}.
     */
    public final class ObjectBuilder extends ExclusiveBuilder implements AutoCloseable {

        private boolean isFirst = true;

        ObjectBuilder(ExclusiveBuilder parent) throws IOException {
            super(parent);
            writer.appendObjectStart();
        }

        /**
         * Writes a constant key and constant value into the object.
         */
        public ObjectBuilder append(String key, Object value) throws IOException {
            append(key).value(value);
            return this;
        }

        /**
         * Writes the given key into the object and returns a {@link ValueBuilder} responsible for
         * producing the key's value.
         */
        public ValueBuilder append(String key) throws IOException {
            checkAccess();
            if (!isFirst) {
                writer.appendSeparator();
            } else {
                isFirst = false;
            }
            writer.quote(key).appendFieldSeparator();
            return new ValueBuilder(this);
        }

        @Override
        public void finish() throws IOException {
            writer.appendObjectEnd();
            super.finish();
        }

        @Override
        public void close() throws IOException {
            finish();
        }
    }

    /**
     * Builds a single well-formed JSON array.
     * <p>
     * Instance must be closed when all elements are written.
     * <p>
     * Entries can be appended using either {@link #append(Object)} or {@link #nextEntry()}.
     */
    public final class ArrayBuilder extends ExclusiveBuilder implements AutoCloseable {
        private boolean isFirst = true;

        ArrayBuilder(ExclusiveBuilder parent) throws IOException {
            super(parent);
            writer.appendArrayStart();
        }

        /**
         * Appends a constant value to the array.
         */
        public ArrayBuilder append(Object value) throws IOException {
            nextEntry().value(value);
            return this;
        }

        /**
         * Prepares the array for a new element and returns a {@link ValueBuilder} responsible to
         * produce that new element.
         */
        public ValueBuilder nextEntry() throws IOException {
            checkAccess();
            if (!isFirst) {
                writer.appendSeparator();
            } else {
                isFirst = false;
            }
            return new ValueBuilder(this);
        }

        @Override
        protected void finish() throws IOException {
            writer.appendArrayEnd();
            super.finish();
        }

        @Override
        public void close() throws IOException {
            finish();
        }
    }

    /**
     * Builder responsible for writing exactly one JSON value.
     * <p>
     * Instance does not have to closed (and does not expose any such functionality). It performs
     * clean up automatically when it has produced a complete JSON value.
     * <p>
     * Either an object (see {@link #object()}), an array (see {@link #array()}), or an arbitrary
     * Java object (is converted to a JSON object, see {@link #value(Object)}).
     * <p>
     * Exceptions are produced if an instance tries to write no or multiple values (only single
     * values are allowed).
     * <p>
     * Instances of this type or not {@link AutoCloseable} and cannot be manually closed. The
     * instance is automatically cleaned up once it or its child finished writing.
     */
    public final class ValueBuilder extends ExclusiveBuilder {
        boolean wroteSomething = false;

        private ValueBuilder(ExclusiveBuilder parent) {
            super(parent);
        }

        public ObjectBuilder object() throws IOException {
            performSingleWrite();
            return new ObjectBuilder(this);
        }

        public ArrayBuilder array() throws IOException {
            performSingleWrite();
            return new ArrayBuilder(this);
        }

        /**
         * Directly produces the given object as a JSON value.
         * <p>
         * The value is directly passed to {@link JsonWriter#print(Object)} and can also be a
         * {@link java.util.Map} (JSON object) or an
         * {@link java.util.Iterator}/{@link java.util.List} (JSON array). All other values are
         * treated as JSON primitive values.
         */
        public void value(Object value) throws IOException {
            checkAccess();
            performSingleWrite();
            writer.print(value);
            finish();
        }

        /**
         * Register that this instance has started writing a value.
         * <p>
         * Ensures that the instance is not used to write multiple values and is later used to check
         * that at least one value was written.
         */
        private void performSingleWrite() {
            if (wroteSomething) {
                throw new IllegalStateException("%s instance attempted to write a second value".formatted(this.getClass()));
            }

            wroteSomething = true;
        }

        @Override
        protected void finish() throws IOException {
            if (!wroteSomething) {
                throw new IllegalStateException("%s instance was closed before writing anything".formatted(this.getClass()));
            }
            super.finish();
        }
    }
}
