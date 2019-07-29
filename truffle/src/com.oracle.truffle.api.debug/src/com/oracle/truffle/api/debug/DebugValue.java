/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.debug;

import java.util.Collection;
import java.util.List;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
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

    static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

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
     * @since 19.0
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
     * Returns the {@link String} value if this value represents a string. This method returns
     * <code>null</code> otherwise.
     *
     * @throws DebugException when guest language code throws an exception
     * @since 19.0
     */
    public abstract String asString() throws DebugException;

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
     * Returns <code>true</code> if reading of this value can have side-effects, else
     * <code>false</code>. Read has side-effects if it changes runtime state.
     *
     * @since 19.0
     */
    public abstract boolean hasReadSideEffects();

    /**
     * Returns <code>true</code> if setting a new value can have side-effects, else
     * <code>false</code>. Write has side-effects if it changes runtime state besides this value.
     *
     * @since 19.0
     */
    public abstract boolean hasWriteSideEffects();

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
     * Test if the value represents 'null'.
     *
     * @since 19.0
     */
    public final boolean isNull() {
        if (!isReadable()) {
            return false;
        }
        Object value = get();
        return INTEROP.isNull(value);
    }

    /**
     * Provides properties representing an internal structure of this value. The returned collection
     * is not thread-safe. If the value is not {@link #isReadable() readable} then <code>null</code>
     * is returned.
     *
     * @return a collection of property values, or </code>null</code> when the value does not have
     *         any concept of properties.
     * @throws DebugException when guest language code throws an exception
     * @since 0.19
     */
    public final Collection<DebugValue> getProperties() throws DebugException {
        if (!isReadable()) {
            return null;
        }
        Object value = get();
        try {
            return getProperties(value, getSession(), resolveLanguage(), null);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    static ValuePropertiesCollection getProperties(Object value, DebuggerSession session, LanguageInfo language, DebugScope scope) {
        if (INTEROP.hasMembers(value)) {
            Object keys;
            try {
                keys = INTEROP.getMembers(value, true);
            } catch (UnsupportedMessageException e) {
                return null;
            }
            return new ValuePropertiesCollection(session, language, value, keys, scope);
        }
        return null;
    }

    /**
     * Get a property value by its name.
     *
     * @param name name of a property
     * @return the property value, or <code>null</code> if the property does not exist.
     * @throws DebugException when guest language code throws an exception
     * @since 19.0
     */
    public final DebugValue getProperty(String name) throws DebugException {
        if (!isReadable()) {
            return null;
        }
        Object value = get();
        if (value != null) {
            try {
                if (!INTEROP.isMemberExisting(value, name)) {
                    return null;
                } else {
                    return new DebugValue.ObjectMemberValue(getSession(), resolveLanguage(), null, value, name);
                }
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
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
        return INTEROP.hasArrayElements(get());
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
        Object value = get();
        if (INTEROP.hasArrayElements(value)) {
            return new ValueInteropList(getSession(), resolveLanguage(), value);
        }
        return null;
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
                    return new HeapValue(getSession(), languageInfo, null, obj, true);
                }
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, languageInfo, null, true, null);
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
                SourceSection location = env.findSourceLocation(languageInfo, obj);
                return getSession().resolveSection(location);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, languageInfo, null, true, null);
            }
        } else {
            return null;
        }
    }

    /**
     * Returns <code>true</code> if this value can be executed (represents a guest language
     * function), else <code>false</code>.
     *
     * @since 19.0
     */
    public final boolean canExecute() throws DebugException {
        if (!isReadable()) {
            return false;
        }
        Object value = get();
        try {
            return INTEROP.isExecutable(value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
        }
    }

    /**
     * Executes the executable represented by this value.
     *
     * @param arguments Arguments passed to the executable
     * @return the result of the execution
     * @throws DebugException when guest language code throws an exception
     * @see #canExecute()
     * @since 19.0
     */
    public final DebugValue execute(DebugValue... arguments) throws DebugException {
        Object value = get();
        Object[] args = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            args[i] = arguments[i].get();
        }
        try {
            Object retValue = INTEROP.execute(value, args);
            return new HeapValue(getSession(), null, retValue);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
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

    abstract DebuggerSession getSession();

    final Debugger getDebugger() {
        return getSession().getDebugger();
    }

    /**
     * Returns a string representation of the debug value.
     *
     * @since 0.17
     */
    @Override
    public String toString() {
        return "DebugValue(name=" + getName() + ", value = " + (isReadable() ? as(String.class) : "<not readable>") + ")";
    }

    abstract static class AbstractDebugValue extends DebugValue {

        final DebuggerSession session;

        AbstractDebugValue(DebuggerSession session, LanguageInfo preferredLanguage) {
            super(preferredLanguage);
            this.session = session;
        }

        @Override
        public final <T> T as(Class<T> clazz) throws DebugException {
            if (!isReadable()) {
                throw new IllegalStateException("Value is not readable");
            }
            try {
                if (clazz == String.class) {
                    Object val = get();
                    String stringValue;
                    if (isMeta() && val instanceof String) {
                        stringValue = (String) val;
                    } else {
                        LanguageInfo languageInfo = resolveLanguage();
                        if (languageInfo == null) {
                            stringValue = val.toString();
                        } else {
                            stringValue = getDebugger().getEnv().toString(languageInfo, val);
                        }
                    }
                    return clazz.cast(stringValue);
                } else if (clazz == Number.class || clazz == Boolean.class) {
                    return convertToPrimitive(clazz);
                }
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public String asString() throws DebugException {
            if (!isReadable()) {
                throw new IllegalStateException("Value is not readable");
            }
            try {
                Object val = get();
                if (INTEROP.isString(val)) {
                    return INTEROP.asString(val);
                } else {
                    return null;
                }
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
        }

        protected boolean isMeta() {
            return false;
        }

        private <T> T convertToPrimitive(Class<T> clazz) {
            Object val = get();
            if (clazz.isInstance(val)) {
                return clazz.cast(val);
            }
            return clazz.cast(Debugger.ACCESSOR.engineSupport().convertPrimitive(val, clazz));
        }

        @Override
        final DebuggerSession getSession() {
            return session;
        }
    }

    static class HeapValue extends AbstractDebugValue {

        private final String name;
        private final boolean isMeta;
        private Object value;

        HeapValue(DebuggerSession session, String name, Object value) {
            this(session, null, name, value);
        }

        HeapValue(DebuggerSession session, LanguageInfo preferredLanguage, String name, Object value) {
            this(session, preferredLanguage, name, value, false);
        }

        HeapValue(DebuggerSession session, LanguageInfo preferredLanguage, String name, Object value, boolean isMeta) {
            super(session, preferredLanguage);
            this.name = name;
            this.isMeta = isMeta;
            this.value = value;
        }

        @Override
        protected boolean isMeta() {
            return this.isMeta;
        }

        @Override
        Object get() {
            return value;
        }

        @Override
        public void set(DebugValue expression) {
            value = expression.get();
        }

        @Override
        public void set(Object primitiveValue) {
            checkPrimitive(primitiveValue);
            value = primitiveValue;
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
            return true;
        }

        @Override
        public boolean hasReadSideEffects() {
            return false;
        }

        @Override
        public boolean hasWriteSideEffects() {
            return false;
        }

        @Override
        public boolean isInternal() {
            return false;
        }

        @Override
        DebugValue createAsInLanguage(LanguageInfo language) {
            return new HeapValue(session, language, name, value, isMeta);
        }

    }

    static final class ObjectPropertyValue extends AbstractDebugValue {

        private final Object object;
        private final String member;
        private final DebugScope scope;

        ObjectPropertyValue(DebuggerSession session, LanguageInfo preferredLanguage, DebugScope scope, Object array, String member) {
            super(session, preferredLanguage);
            this.object = array;
            this.member = member;
            this.scope = scope;
        }

        @Override
        Object get() {
            checkValid();
            try {
                return INTEROP.readMember(object, member);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        public String getName() {
            return String.valueOf(member);
        }

        @Override
        public boolean isReadable() {
            checkValid();
            return INTEROP.isMemberReadable(object, member);
        }

        @Override
        public boolean isWritable() {
            checkValid();
            return INTEROP.isMemberWritable(object, member);
        }

        @Override
        public boolean hasReadSideEffects() {
            checkValid();
            return INTEROP.hasMemberReadSideEffects(object, member);
        }

        @Override
        public boolean hasWriteSideEffects() {
            checkValid();
            return INTEROP.hasMemberWriteSideEffects(object, member);
        }

        @Override
        public boolean isInternal() {
            checkValid();
            return INTEROP.isMemberInternal(object, member);
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
                INTEROP.writeMember(object, member, value.get());
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        public void set(Object primitiveValue) {
            checkValid();
            checkPrimitive(primitiveValue);
            try {
                INTEROP.writeMember(object, member, primitiveValue);
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        DebugValue createAsInLanguage(LanguageInfo language) {
            return new ObjectPropertyValue(session, language, scope, object, member);
        }

        private void checkValid() {
            if (scope != null) {
                scope.verifyValidState();
            }
        }
    }

    static final class ObjectMemberValue extends AbstractDebugValue {

        private final Object object;
        private final String member;
        private final DebugScope scope;

        ObjectMemberValue(DebuggerSession session, LanguageInfo preferredLanguage, DebugScope scope, Object object, String member) {
            super(session, preferredLanguage);
            this.object = object;
            this.member = member;
            this.scope = scope;
        }

        @Override
        Object get() {
            checkValid();
            try {
                return INTEROP.readMember(object, member);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        public String getName() {
            return String.valueOf(member);
        }

        @Override
        public boolean isReadable() {
            checkValid();
            return INTEROP.isMemberReadable(object, member);
        }

        @Override
        public boolean isWritable() {
            checkValid();
            return INTEROP.isMemberWritable(object, member);
        }

        @Override
        public boolean hasReadSideEffects() {
            checkValid();
            return INTEROP.hasMemberReadSideEffects(object, member);
        }

        @Override
        public boolean hasWriteSideEffects() {
            checkValid();
            return INTEROP.hasMemberWriteSideEffects(object, member);
        }

        @Override
        public boolean isInternal() {
            checkValid();
            return INTEROP.isMemberInternal(object, member);
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
                INTEROP.writeMember(object, member, value.get());
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        public void set(Object primitiveValue) {
            checkValid();
            checkPrimitive(primitiveValue);
            try {
                INTEROP.writeMember(object, member, primitiveValue);
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        DebugValue createAsInLanguage(LanguageInfo language) {
            return new ObjectMemberValue(session, language, scope, object, member);
        }

        private void checkValid() {
            if (scope != null) {
                scope.verifyValidState();
            }
        }
    }

    static final class ArrayElementValue extends AbstractDebugValue {

        private final Object array;
        private final long index;
        private final DebugScope scope;

        ArrayElementValue(DebuggerSession session, LanguageInfo preferredLanguage, DebugScope scope, Object array, long index) {
            super(session, preferredLanguage);
            this.array = array;
            this.index = index;
            this.scope = scope;
        }

        @Override
        Object get() {
            checkValid();
            try {
                return INTEROP.readArrayElement(array, index);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        public String getName() {
            return String.valueOf(index);
        }

        @Override
        public boolean isReadable() {
            checkValid();
            return INTEROP.isArrayElementReadable(array, index);
        }

        @Override
        public boolean isWritable() {
            checkValid();
            return INTEROP.isArrayElementWritable(array, index);
        }

        @Override
        public boolean hasReadSideEffects() {
            checkValid();
            return false;
        }

        @Override
        public boolean hasWriteSideEffects() {
            checkValid();
            return false;
        }

        @Override
        public boolean isInternal() {
            checkValid();
            return false;
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
                INTEROP.writeArrayElement(array, index, value.get());
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        public void set(Object primitiveValue) {
            checkValid();
            checkPrimitive(primitiveValue);
            try {
                INTEROP.writeArrayElement(array, index, primitiveValue);
            } catch (Throwable ex) {
                throw new DebugException(getSession(), ex, resolveLanguage(), null, true, null);
            }
        }

        @Override
        DebugValue createAsInLanguage(LanguageInfo language) {
            return new ArrayElementValue(session, language, scope, array, index);
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
