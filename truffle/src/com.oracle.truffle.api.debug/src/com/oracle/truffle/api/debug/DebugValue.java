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

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
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

    final LanguageInfo preferredLanguage;

    abstract Object get() throws DebugException;

    DebugValue(LanguageInfo preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    /**
     * Sets the value using another {@link DebugValue}. Throws an {@link IllegalStateException} if
     * the value is not writable, the passed value is not readable, this value or the passed value
     * is invalid, or the guest language of the values do not match. Use
     * {@link DebugStackFrame#eval(String)} to evaluate values to be set.
     *
     * @param value the value to set
     * @throws DebugException when guest language code throws an exception
     * @since 0.17
     */
    public abstract void set(DebugValue value) throws DebugException;

    /**
     * Sets a primitive value. Strings and boxed Java primitive types are considered primitive.
     * Throws an {@link IllegalStateException} if the value is not writable and
     * {@link IllegalArgumentException} if the value is not primitive.
     *
     * @param primitiveValue a primitive value to set
     * @throws DebugException when guest language code throws an exception
     * @since 1.0
     */
    public abstract void set(Object primitiveValue) throws DebugException;

    /**
     * Converts the debug value into a Java type. Class conversions which are always supported:
     * <ul>
     * <li>{@link String}.class converts the value to its language specific string representation.
     * </li>
     * <li>{@link Number}.class converts the value to a Number representation, if any.</li>
     * <li>{@link Boolean}.class converts the value to a Boolean representation, if any.</li>
     * </ul>
     * No optional conversions are currently available. If a conversion is not supported then an
     * {@link UnsupportedOperationException} is thrown. If the value is not {@link #isReadable()
     * readable} then an {@link IllegalStateException} is thrown.
     *
     * @param clazz the type to convert to
     * @return the converted Java type, or <code>null</code> when the conversion was not possible.
     * @throws DebugException when guest language code throws an exception
     * @since 0.17
     */
    public abstract <T> T as(Class<T> clazz) throws DebugException;

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
     * @deprecated Use {@link #isWritable()}
     */
    @Deprecated
    public final boolean isWriteable() {
        return isWritable();
    }

    /**
     * Returns <code>true</code> if this value can be written to, else <code>false</code>.
     *
     * @see #as(Class)
     * @since 0.26
     */
    public abstract boolean isWritable();

    /**
     * Returns <code>true</code> if this value represents an internal variable or property,
     * <code>false</code> otherwise.
     * <p>
     * Languages might have extra object properties or extra scope variables that are a part of the
     * runtime, but do not correspond to anything what is an explicit part of the guest language
     * representation. They may represent additional language artifacts, providing more in-depth
     * information that can be valuable during debugging. Language implementors mark these variables
     * as <em>internal</em>. An example of such internal values are internal slots in ECMAScript.
     * </p>
     *
     * @since 0.26
     */
    public abstract boolean isInternal();

    /**
     * Get the scope where this value is declared in. It returns a non-null value for local
     * variables declared on a stack. It's <code>null<code> for object properties and other heap
     * values.
     *
     * @return the scope, or <code>null</code> when this value does not belong into any scope.
     *
     * @since 0.26
     */
    public DebugScope getScope() {
        return null;
    }

    /**
     * Provides properties representing an internal structure of this value. The returned collection
     * is not thread-safe. If the value is not {@link #isReadable() readable} then an
     * {@link IllegalStateException} is thrown.
     *
     * @return a collection of property values, or </code>null</code> when the value does not have
     *         any concept of properties.
     * @throws DebugException when guest language code throws an exception
     * @throws IllegalStateException if the value is not {@link #isReadable() readable}
     * @since 0.19
     */
    public final Collection<DebugValue> getProperties() throws DebugException {
        if (!isReadable()) {
            throw new IllegalStateException("Value is not readable");
        }
        Object value = get();
        try {
            return getProperties(value, getDebugger(), resolveLanguage(), null);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getDebugger(), ex, resolveLanguage(), null, true, null);
        }
    }

    static ValuePropertiesCollection getProperties(Object value, Debugger debugger, LanguageInfo language, DebugScope scope) {
        ValuePropertiesCollection properties = null;
        if (value instanceof TruffleObject) {
            TruffleObject object = (TruffleObject) value;
            Map<Object, Object> map = ObjectStructures.asMap(debugger.getMessageNodes(), object);
            if (map != null) {
                properties = new ValuePropertiesCollection(debugger, language, object, map, map.entrySet(), scope);
            }
        }
        return properties;
    }

    /**
     * Get a property value by its name.
     *
     * @param name name of a property
     * @return the property value, or <code>null</code> if the property does not exist.
     * @throws DebugException when guest language code throws an exception
     * @throws IllegalStateException if the value is not {@link #isReadable() readable}
     * @since 1.0
     */
    public final DebugValue getProperty(String name) throws DebugException {
        if (!isReadable()) {
            throw new IllegalStateException("Value is not readable");
        }
        Object value = get();
        if (value instanceof TruffleObject) {
            TruffleObject object = (TruffleObject) value;
            try {
                int keyInfo = ForeignAccess.sendKeyInfo(getDebugger().getMessageNodes().keyInfo, object, name);
                if (!KeyInfo.isExisting(keyInfo)) {
                    return null;
                } else {
                    Map.Entry<Object, Object> entry = new ObjectStructures.TruffleEntry(getDebugger().getMessageNodes(), object, name);
                    return new DebugValue.PropertyValue(getDebugger(), resolveLanguage(), keyInfo, entry, null);
                }
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getDebugger(), ex, resolveLanguage(), null, true, null);
            }
        } else {
            return null;
        }
    }

    /**
     * Returns <code>true</code> if this value represents an array, <code>false</code> otherwise.
     *
     * @throws DebugException when guest language code throws an exception
     * @since 0.19
     */
    public final boolean isArray() throws DebugException {
        if (!isReadable()) {
            return false;
        }
        Object value = get();
        if (value instanceof TruffleObject) {
            TruffleObject to = (TruffleObject) value;
            return ObjectStructures.isArray(getDebugger().getMessageNodes(), to);
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
     * @throws DebugException when guest language code throws an exception
     * @since 0.19
     */
    public final List<DebugValue> getArray() throws DebugException {
        if (!isReadable()) {
            return null;
        }
        List<DebugValue> arrayList = null;
        Object value = get();
        if (value instanceof TruffleObject) {
            TruffleObject to = (TruffleObject) value;
            List<Object> array = ObjectStructures.asList(getDebugger().getMessageNodes(), to);
            if (array != null) {
                arrayList = new ValueInteropList(getDebugger(), resolveLanguage(), array);
            }
        }
        return arrayList;
    }

    final LanguageInfo resolveLanguage() {
        LanguageInfo languageInfo;
        if (preferredLanguage != null) {
            languageInfo = preferredLanguage;
        } else if (getScope() != null && getScope().getLanguage() != null) {
            languageInfo = getScope().getLanguage();
        } else {
            languageInfo = getOriginalLanguage();
        }
        return languageInfo;
    }

    /**
     * Get a meta-object of this value, if any. The meta-object represents a description of the
     * value, reveals it's kind and it's features.
     *
     * @return a value representing the meta-object, or <code>null</code>
     * @throws DebugException when guest language code throws an exception
     * @since 0.22
     */
    public final DebugValue getMetaObject() throws DebugException {
        if (!isReadable()) {
            return null;
        }
        Object obj = get();
        if (obj == null) {
            return null;
        }
        TruffleInstrument.Env env = getDebugger().getEnv();
        LanguageInfo languageInfo = resolveLanguage();
        if (languageInfo != null) {
            try {
                obj = env.findMetaObject(languageInfo, obj);
                if (obj != null) {
                    return new HeapValue(getDebugger(), languageInfo, null, obj);
                }
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getDebugger(), ex, languageInfo, null, true, null);
            }
        }
        return null;
    }

    /**
     * Get a source location where this value is declared, if any.
     *
     * @return a source location of the object, or <code>null</code>
     * @throws DebugException when guest language code throws an exception
     * @since 0.22
     */
    public final SourceSection getSourceLocation() throws DebugException {
        if (!isReadable()) {
            return null;
        }
        Object obj = get();
        if (obj == null) {
            return null;
        }
        TruffleInstrument.Env env = getDebugger().getEnv();
        LanguageInfo languageInfo = resolveLanguage();
        if (languageInfo != null) {
            try {
                return env.findSourceLocation(languageInfo, obj);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getDebugger(), ex, languageInfo, null, true, null);
            }
        } else {
            return null;
        }
    }

    /**
     * Returns <code>true</code> if this value can be executed (represents a guest language
     * function), else <code>false</code>.
     *
     * @since 1.0
     */
    public final boolean canExecute() throws DebugException {
        Object value = get();
        if (value instanceof TruffleObject) {
            TruffleObject to = (TruffleObject) value;
            try {
                return ObjectStructures.canExecute(getDebugger().getMessageNodes(), to);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getDebugger(), ex, resolveLanguage(), null, true, null);
            }
        } else {
            return false;
        }
    }

    /**
     * Get the original language that created the value, if any. This method will return
     * <code>null</code> for values representing a primitive value, or objects that are not
     * associated with any language.
     *
     * @return the language, or <code>null</code> when no language can be identified as the creator
     *         of the value.
     * @throws DebugException when guest language code throws an exception
     * @since 0.27
     */
    public final LanguageInfo getOriginalLanguage() throws DebugException {
        if (!isReadable()) {
            return null;
        }
        Object obj = get();
        if (obj == null) {
            return null;
        }
        return getDebugger().getEnv().findLanguage(obj);
    }

    /**
     * Returns a debug value that presents itself as seen by the provided language. The language
     * affects the output of {@link #as(java.lang.Class)}, {@link #getMetaObject()} and
     * {@link #getSourceLocation()}. Properties, array elements and other attributes are not
     * affected by a language. The {@link #getOriginalLanguage() original language} of the returned
     * value remains the same as of this value.
     *
     * @param language a language to get the value representation of
     * @return the value as represented in the language
     * @since 0.27
     */
    public final DebugValue asInLanguage(LanguageInfo language) {
        if (preferredLanguage == language) {
            return this;
        }
        return createAsInLanguage(language);
    }

    abstract DebugValue createAsInLanguage(LanguageInfo language);

    abstract Debugger getDebugger();

    /**
     * Returns a string representation of the debug value.
     *
     * @since 0.17
     */
    @Override
    public String toString() {
        return "DebugValue(name=" + getName() + ", value = " + as(String.class) + ")";
    }

    static class HeapValue extends DebugValue {

        // identifies the debugger and engine
        private final Debugger debugger;
        private final String name;
        private final Object value;

        HeapValue(Debugger debugger, String name, Object value) {
            this(debugger, null, name, value);
        }

        HeapValue(Debugger debugger, LanguageInfo preferredLanguage, String name, Object value) {
            super(preferredLanguage);
            this.debugger = debugger;
            this.name = name;
            this.value = value;
        }

        @Override
        public <T> T as(Class<T> clazz) throws DebugException {
            if (!isReadable()) {
                throw new IllegalStateException("Value is not readable");
            }
            try {
                if (clazz == String.class) {
                    Object val = get();
                    LanguageInfo languageInfo = resolveLanguage();
                    String stringValue;
                    if (languageInfo == null) {
                        stringValue = val.toString();
                    } else {
                        stringValue = debugger.getEnv().toString(languageInfo, val);
                    }
                    return clazz.cast(stringValue);
                } else if (clazz == Number.class || clazz == Boolean.class) {
                    return convertToPrimitive(clazz);
                }
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getDebugger(), ex, resolveLanguage(), null, true, null);
            }
            throw new UnsupportedOperationException();
        }

        private <T> T convertToPrimitive(Class<T> clazz) {
            Object val = get();
            if (clazz.isInstance(val)) {
                return clazz.cast(val);
            }
            if (val instanceof TruffleObject) {
                TruffleObject receiver = (TruffleObject) val;
                if (ForeignAccess.sendIsBoxed(debugger.msgNodes.isBoxed, receiver)) {
                    try {
                        Object unboxed = ForeignAccess.sendUnbox(debugger.msgNodes.unbox, receiver);
                        if (clazz.isInstance(unboxed)) {
                            return clazz.cast(unboxed);
                        }
                    } catch (UnsupportedMessageException e) {
                        throw new AssertionError("isBoxed returned true but unbox threw unsupported error.");
                    }
                }
            }
            return null;
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
        public void set(Object primitiveValue) {
            throw new IllegalStateException("Value is not writable");
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isReadable() {
            return true;
        }

        @Override
        public boolean isWritable() {
            return false;
        }

        @Override
        public boolean isInternal() {
            return false;
        }

        @Override
        DebugValue createAsInLanguage(LanguageInfo language) {
            return new HeapValue(debugger, language, name, value);
        }

        @Override
        Debugger getDebugger() {
            return debugger;
        }

    }

    static final class PropertyValue extends HeapValue {

        private final int keyInfo;
        private final Map.Entry<Object, Object> property;
        private final DebugScope scope;

        PropertyValue(Debugger debugger, LanguageInfo language, TruffleObject object, Map.Entry<Object, Object> property, DebugScope scope) {
            this(debugger, language, ForeignAccess.sendKeyInfo(Message.KEY_INFO.createNode(), object, property.getKey()), property, scope);
        }

        PropertyValue(Debugger debugger, LanguageInfo preferredLanguage, int keyInfo, Map.Entry<Object, Object> property, DebugScope scope) {
            super(debugger, preferredLanguage, (property.getKey() instanceof String) ? (String) property.getKey() : null, null);
            this.keyInfo = keyInfo;
            this.property = property;
            this.scope = scope;
        }

        @Override
        Object get() {
            checkValid();
            try {
                return property.getValue();
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getDebugger(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        public String getName() {
            String name = super.getName();
            if (name != null) {
                return name;
            }
            checkValid();
            Object propertyKey = property.getKey();
            // non-String property key
            LanguageInfo languageInfo = resolveLanguage();
            if (languageInfo != null) {
                name = getDebugger().getEnv().toString(languageInfo, propertyKey);
            } else {
                name = Objects.toString(propertyKey);
            }
            return name;
        }

        @Override
        public boolean isReadable() {
            checkValid();
            return KeyInfo.isReadable(keyInfo);
        }

        @Override
        public boolean isWritable() {
            checkValid();
            return KeyInfo.isWritable(keyInfo);
        }

        @Override
        public boolean isInternal() {
            checkValid();
            return KeyInfo.isInternal(keyInfo);
        }

        @Override
        public DebugScope getScope() {
            checkValid();
            return scope;
        }

        @Override
        public void set(DebugValue value) {
            checkValid();
            try {
                property.setValue(value.get());
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getDebugger(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        public void set(Object primitiveValue) {
            checkValid();
            checkPrimitive(primitiveValue);
            try {
                property.setValue(primitiveValue);
            } catch (Throwable ex) {
                throw new DebugException(getDebugger(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        DebugValue createAsInLanguage(LanguageInfo language) {
            return new PropertyValue(getDebugger(), language, keyInfo, property, scope);
        }

        private void checkValid() {
            if (scope != null) {
                scope.verifyValidState();
            }
        }
    }

    static final class PropertyNamedValue extends HeapValue {

        private final int keyInfo;
        private final Map<Object, Object> map;
        private final DebugScope scope;

        PropertyNamedValue(Debugger debugger, LanguageInfo language, TruffleObject object,
                        Map<Object, Object> map, String name, DebugScope scope) {
            this(debugger, language, ForeignAccess.sendKeyInfo(Message.KEY_INFO.createNode(), object, name), map, name, scope);
        }

        private PropertyNamedValue(Debugger debugger, LanguageInfo preferredLanguage,
                        int keyInfo, Map<Object, Object> map, String name, DebugScope scope) {
            super(debugger, preferredLanguage, name, null);
            this.keyInfo = keyInfo;
            this.map = map;
            this.scope = scope;
        }

        @Override
        Object get() {
            checkValid();
            try {
                return map.get(getName());
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getDebugger(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        public DebugScope getScope() {
            checkValid();
            return scope;
        }

        @Override
        public boolean isReadable() {
            checkValid();
            return KeyInfo.isReadable(keyInfo);
        }

        @Override
        public boolean isWritable() {
            checkValid();
            return KeyInfo.isWritable(keyInfo);
        }

        @Override
        public boolean isInternal() {
            checkValid();
            return KeyInfo.isInternal(keyInfo);
        }

        @Override
        public void set(DebugValue value) {
            checkValid();
            try {
                map.put(getName(), value.get());
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getDebugger(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        public void set(Object primitiveValue) {
            checkValid();
            checkPrimitive(primitiveValue);
            try {
                map.put(getName(), primitiveValue);
            } catch (Throwable ex) {
                throw new DebugException(getDebugger(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        DebugValue createAsInLanguage(LanguageInfo language) {
            return new PropertyNamedValue(getDebugger(), language, keyInfo, map, getName(), scope);
        }

        private void checkValid() {
            if (scope != null) {
                scope.verifyValidState();
            }
        }

    }

    private static void checkPrimitive(Object value) {
        Class<?> clazz;
        if (value == null || !((clazz = value.getClass()) == Byte.class ||
                        clazz == Short.class ||
                        clazz == Integer.class ||
                        clazz == Long.class ||
                        clazz == Float.class ||
                        clazz == Double.class ||
                        clazz == Character.class ||
                        clazz == Boolean.class ||
                        clazz == String.class)) {
            throw new IllegalArgumentException(value + " is not primitive.");
        }
    }

}
