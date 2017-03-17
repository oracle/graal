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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.oracle.truffle.api.LanguageInfo;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Represents a value accessed using the debugger API. Please note that values can become invalid
 * depending on the context in which they are used. For example stack values will only remain valid
 * as long as the current stack element is active. Heap values on the other hand remain valid. If a
 * value becomes invalid then setting or getting a value will throw an {@link IllegalStateException}
 * . {@link DebugValue} instances neither support equality or preserve identity.
 * <p>
 * Clients may access the debug value only on the execution thread where the suspended event of the
 * stack frame was created and notification received; access from other threads will throw
 * {@link IllegalStateException}.
 *
 * @since 0.17
 */
public abstract class DebugValue {

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
     * Provides properties representing an internal structure of this value. The returned collection
     * is not thread-safe. If the value is not {@link #isReadable() readable} then an
     * {@link IllegalStateException} is thrown.
     *
     * @return a collection of property values, or </code>null</code> when the value does not have
     *         any concept of properties.
     * @since 0.19
     */
    @SuppressWarnings("unchecked")
    public final Collection<DebugValue> getProperties() {
        if (!isReadable()) {
            throw new IllegalStateException("Value is not readable");
        }
        Object value = get();
        Collection<DebugValue> properties = null;
        if (value instanceof TruffleObject) {
            Map<Object, Object> map = JavaInterop.asJavaObject(Map.class, (TruffleObject) value);
            if (map != null) {
                try {
                    properties = new ValuePropertiesCollection(getDebugger(), getSourceRoot(), map.entrySet());
                } catch (Exception ex) {
                    if (isUnsupportedException(ex)) {
                        // Not supported, no properties
                    } else {
                        throw ex;
                    }
                }
            }
        }
        return properties;
    }

    private static boolean isUnsupportedException(Throwable ex) {
        return ex instanceof InteropException || ex.getCause() != null && isUnsupportedException(ex.getCause());
    }

    /*
     * TODO future API: Find a property value based on a String name. In general, not all properties
     * may have String names. Use this for lookup of a value of some known String-based property.
     * DebugValue findProperty(String name)
     */

    /**
     * Returns <code>true</code> if this value represents an array, <code>false</code> otherwise.
     *
     * @since 0.19
     */
    public final boolean isArray() {
        Object value = get();
        if (value instanceof TruffleObject) {
            TruffleObject to = (TruffleObject) value;
            return JavaInterop.isArray(to);
        } else {
            return false;
        }
    }

    /**
     * Provides array elements when this value represents an array. To test if this value represents
     * an array, check {@link #isArray()}.
     *
     * @return a list of array elements, or <code>null</code> when the value does not represent an
     *         array.
     * @since 0.19
     */
    @SuppressWarnings("unchecked")
    public final List<DebugValue> getArray() {
        List<DebugValue> arrayList = null;
        Object value = get();
        if (value instanceof TruffleObject) {
            TruffleObject to = (TruffleObject) value;
            if (JavaInterop.isArray(to)) {
                List<Object> array = JavaInterop.asJavaObject(List.class, (TruffleObject) value);
                arrayList = new ValueInteropList(getDebugger(), getSourceRoot(), array);
            }
        }
        return arrayList;
    }

    /**
     * Get a meta-object of this value, if any. The meta-object represents a description of the
     * value, reveals it's kind and it's features.
     *
     * @return a value representing the meta-object, or <code>null</code>
     * @since 0.22
     */
    public final DebugValue getMetaObject() {
        Object obj = get();
        if (obj == null) {
            return null;
        }
        obj = getDebugger().getEnv().findMetaObject(getSourceRoot(), obj);
        if (obj == null) {
            return null;
        } else {
            return new HeapValue(getDebugger(), getSourceRoot(), obj);
        }
    }

    /**
     * Get a source location where this value is declared, if any.
     *
     * @return a source location of the object, or <code>null</code>
     * @since 0.22
     */
    public final SourceSection getSourceLocation() {
        Object obj = get();
        if (obj == null) {
            return null;
        }
        return getDebugger().getEnv().findSourceLocation(getSourceRoot(), obj);
    }

    abstract Debugger getDebugger();

    abstract RootNode getSourceRoot();

    final LanguageInfo getLanguageInfo() {
        RootNode root = getSourceRoot();
        if (root != null) {
            return root.getLanguageInfo();
        }
        return null;
    }

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

    static class HeapValue extends DebugValue {

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

        @SuppressWarnings("unchecked")
        @Override
        public <T> T as(Class<T> clazz) {
            if (!isReadable()) {
                throw new IllegalStateException("Value is not readable");
            }
            if (clazz == String.class) {
                Object val = get();
                String stringValue;
                if (sourceRoot == null) {
                    stringValue = val.toString();
                } else {
                    stringValue = debugger.getEnv().toString(sourceRoot, val);
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

        @Override
        Debugger getDebugger() {
            return debugger;
        }

        @Override
        RootNode getSourceRoot() {
            return sourceRoot;
        }

    }

    static final class PropertyValue extends HeapValue {

        private final Map.Entry<Object, Object> property;

        PropertyValue(Debugger debugger, RootNode root, Map.Entry<Object, Object> property) {
            super(debugger, root, null);
            this.property = property;
        }

        @Override
        Object get() {
            return property.getValue();
        }

        @Override
        public String getName() {
            String name;
            RootNode sourceRoot = getSourceRoot();
            Object propertyKey = property.getKey();
            if (propertyKey instanceof String) {
                name = (String) propertyKey;
            } else {
                if (sourceRoot == null) {
                    name = Objects.toString(propertyKey);
                } else {
                    name = getDebugger().getEnv().toString(sourceRoot, propertyKey);
                }
            }
            return name;
        }

        @Override
        public boolean isWriteable() {
            return true; // Suppose that yes...
        }

        @Override
        public void set(DebugValue value) {
            property.setValue(value.get());
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

        @Override
        Object get() {
            origin.verifyValidState(false);
            return origin.findTruffleFrame().getValue(slot);
        }

        @Override
        public void set(DebugValue value) {
            origin.verifyValidState(false);
            if (value.getLanguageInfo() != getLanguageInfo()) {
                throw new IllegalStateException(String.format("Languages of set values do not match %s != %s.", value.getLanguageInfo(), getLanguageInfo()));
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

        @Override
        Debugger getDebugger() {
            return origin.event.getSession().getDebugger();
        }

        @Override
        RootNode getSourceRoot() {
            return origin.findCurrentRoot();
        }

    }

}
