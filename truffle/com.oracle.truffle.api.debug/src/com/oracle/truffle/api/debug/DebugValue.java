/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Represents a value accessed using the debugger API. Please note that values can become invalid
 * depending on the context in which they are used. For example stack values will only remain valid
 * as long as the current stack element is active. Heap values on the other hand remain valid. If a
 * value becomes invalid then setting or getting a value will throw an {@link IllegalStateException}
 * . {@link DebugValue} instances neither support equality or preserve identity.
 * <p>
 * Clients can access the debug value only on the execution thread where the suspended event of the
 * stack frame was created and notification received; access from other threads will throw
 * {@link IllegalStateException}.
 *
 * @since 0.17
 */
public abstract class DebugValue /* TODO: future API implements Iterable<DebugValue> */ {

    @SuppressWarnings("rawtypes")
    abstract Class<? extends TruffleLanguage> getLanguage();

    abstract Object get();

    DebugValue() {
    }

    /**
     * Sets the value using another {@link DebugValue}. Throws an {@link IllegalStateException} if
     * the value is not writable, the passed value is not readable, this value or the passed value
     * is invalid, or the guest language of the values do not match. Use
     * {@link DebugStackFrame#eval(String)} to evaluate values to be set.
     *
     * @param value the value to set
     * @since 0.17
     */
    public abstract void set(DebugValue value);

    /**
     * Converts the debug value into a Java type. Class conversions which are always supported:
     * <ul>
     * <li>{@link String}.class converts the value to its language specific string representation.
     * </li>
     * </ul>
     * No optional conversions are currently available. If a conversion is not supported then an
     * {@link UnsupportedOperationException} is thrown. If the value is not {@link #isReadable()
     * readable} then an {@link IllegalStateException} is thrown.
     *
     * @param clazz the type to convert to
     * @return the converted Java type
     * @since 0.17
     */
    public abstract <T> T as(Class<T> clazz);

    /**
     * Returns the name of this value as it is referred to from its origin. If this value is
     * originated from the stack it returns the name of the local variable. If the value was
     * returned from another objects then it returns the name of the property or field it is
     * contained in. If no name is available <code>null</code> is returned.
     *
     * @since 0.17
     */
    public abstract String getName();

    /**
     * Returns <code>true</code> if this value can be read else <code>false</code>.
     *
     * @see #as(Class)
     * @since 0.17
     */
    public abstract boolean isReadable();

    /**
     * Returns <code>true</code> if this value can be read else <code>false</code>.
     *
     * @see #as(Class)
     * @since 0.17
     */
    public abstract boolean isWriteable();

    /**
     * Returns a string representation of the debug value.
     *
     * @since 0.17
     */
    @Override
    public String toString() {
        return "DebugValue(name=" + getName() + ", value = " + as(String.class) + ")";
    }

    /*
     * TODO future API: public abstract Value getType(); For this we would need to have an interop
     * message to receive the type object.
     */

    /*
     * TODO future API: public abstract boolean isInternal(); For this we need a notion of internal
     * on FrameSlot.
     */

    /*
     * TODO future API: public abstract int getLength(); We could already implement this using
     * interop, but without beeing able to iterate the values it does not make much sense.
     */

    /*
     * TODO future API: public Iterator<DebugValue> iterator() for this we need a way to get key
     * names of an object using interop.
     */
    /* TODO future API: public abstract boolean getValue(String); */

    @SuppressWarnings("rawtypes")
    static final class HeapValue extends DebugValue {

        // identifies the debugger and engine
        private final Debugger debugger;
        // identifiers the original root this value originates from
        private final RootNode sourceRoot;
        private final Object value;

        HeapValue(Debugger debugger, RootNode root, Object value) {
            this.debugger = debugger;
            this.sourceRoot = root;
            this.value = value;
        }

        @Override
        Class<? extends TruffleLanguage> getLanguage() {
            return Debugger.ACCESSOR.findLanguage(sourceRoot);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T as(Class<T> clazz) {
            if (!isReadable()) {
                throw new IllegalStateException("Value is not readable");
            }
            if (clazz == String.class) {
                String stringValue;
                if (sourceRoot == null) {
                    stringValue = value.toString();
                } else {
                    stringValue = debugger.getEnv().toString(sourceRoot, value);
                }
                return (T) stringValue;
            }
            throw new UnsupportedOperationException();
        }

        @Override
        Object get() {
            return value;
        }

        @Override
        public void set(DebugValue expression) {
            throw new IllegalStateException("Value is not writable");
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public boolean isReadable() {
            return true;
        }

        @Override
        public boolean isWriteable() {
            return false;
        }

    }

    static final class StackValue extends DebugValue {

        protected final DebugStackFrame origin;
        private final FrameSlot slot;

        StackValue(DebugStackFrame frame, FrameSlot slot) {
            this.origin = frame;
            this.slot = slot;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T as(Class<T> clazz) {
            origin.verifyValidState(false);
            if (!isReadable()) {
                throw new IllegalStateException("Value is not readable");
            }
            if (clazz == String.class) {
                RootNode root = origin.findCurrentRoot();
                Object value = get();
                String stringValue;
                if (root == null) {
                    stringValue = value.toString();
                } else {
                    stringValue = origin.event.getSession().getDebugger().getEnv().toString(root, get());
                }
                return (T) stringValue;
            }
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("rawtypes")
        @Override
        Class<? extends TruffleLanguage> getLanguage() {
            return origin.findCurrentLanguage();
        }

        @Override
        Object get() {
            origin.verifyValidState(false);
            return origin.findTruffleFrame().getValue(slot);
        }

        @Override
        public void set(DebugValue value) {
            origin.verifyValidState(false);
            if (value.getLanguage() != getLanguage()) {
                throw new IllegalStateException(String.format("Languages of set values do not match %s != %s.", value.getLanguage(), getLanguage()));
            }
            MaterializedFrame frame = origin.findTruffleFrame();
            frame.setObject(slot, value.get());
        }

        @Override
        public String getName() {
            origin.verifyValidState(false);
            return slot.getIdentifier().toString();
        }

        @Override
        public boolean isReadable() {
            origin.verifyValidState(false);
            return true;
        }

        @Override
        public boolean isWriteable() {
            origin.verifyValidState(false);
            return true;
        }

    }

}
