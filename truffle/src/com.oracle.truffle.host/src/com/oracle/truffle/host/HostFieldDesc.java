/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.host;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Objects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownMemberException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import java.lang.invoke.WrongMethodTypeException;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
abstract class HostFieldDesc extends HostBaseObject {

    private final boolean isFinal;
    private final Class<?> type;
    private final Type genericType;
    private final String name;

    private HostFieldDesc(Class<?> type, Type genericType, String name, boolean isFinal) {
        this.isFinal = isFinal;
        this.type = type;
        this.genericType = genericType;
        this.name = name;
    }

    public final boolean isFinal() {
        return isFinal;
    }

    public final Class<?> getType() {
        return type;
    }

    public final Type getGenericType() {
        return genericType;
    }

    public final String getName() {
        return name;
    }

    public abstract Object get(Object receiver) throws UnknownMemberException;

    public abstract void set(Object receiver, Object value) throws ClassCastException, NullPointerException, UnknownMemberException;

    public abstract boolean isStatic();

    // Interop messages

    @ExportMessage
    final boolean isMember() {
        return true;
    }

    @ExportMessage
    final Object getMemberSimpleName() {
        return getName();
    }

    @ExportMessage
    @TruffleBoundary
    final Object getMemberQualifiedName() {
        String className = getDeclaringClass().getCanonicalName();
        if (className == null) {
            className = getDeclaringClass().getName();
        }
        return className + '.' + getMemberSimpleName();
    }

    @ExportMessage
    boolean isMemberKindField() {
        return true;
    }

    @ExportMessage
    boolean isMemberKindMethod() {
        return false;
    }

    @ExportMessage
    boolean isMemberKindMetaObject() {
        return false;
    }

    @ExportMessage
    final boolean hasDeclaringMetaObject() {
        return true;
    }

    @ExportMessage
    final Object getDeclaringMetaObject(@Bind("$node") Node node) {
        return HostObject.forClass(this.getDeclaringClass(), HostContext.get(node));
    }

    @ExportMessage
    final boolean hasMemberSignature() {
        return true;
    }

    @ExportMessage
    final Object getMemberSignature() {
        return new HostObject.MembersArray(new Object[]{new HostSignatureElement(null, type)});
    }

    static HostFieldDesc unreflect(MethodHandles.Lookup methodLookup, Field reflectionField) {
        if (TruffleOptions.AOT) { // use reflection instead of MethodHandle
            return new ReflectImpl(reflectionField);
        } else {
            return new MHImpl(methodLookup, reflectionField);
        }
    }

    private static final class ReflectImpl extends HostFieldDesc {
        private final Field field;

        ReflectImpl(Field field) {
            super(field.getType(), field.getGenericType(), field.getName(), Modifier.isFinal(field.getModifiers()));
            this.field = field;
        }

        @Override
        public Object get(Object receiver) throws UnknownMemberException {
            try {
                return reflectGet(field, receiver);
            } catch (IllegalAccessException e) {
                throw shouldNotReachHere(e);
            } catch (IllegalArgumentException e) {
                throw UnknownMemberException.create(this);
            }
        }

        @Override
        public void set(Object receiver, Object value) throws UnknownMemberException {
            try {
                reflectSet(field, receiver, value);
            } catch (IllegalAccessException e) {
                throw shouldNotReachHere(e);
            } catch (IllegalArgumentException e) {
                throw UnknownMemberException.create(this);
            }
        }

        @Override
        public boolean isStatic() {
            return Modifier.isStatic(field.getModifiers());
        }

        @Override
        public Class<?> getDeclaringClass() {
            return field.getDeclaringClass();
        }

        @TruffleBoundary
        private static Object reflectGet(Field field, Object receiver) throws IllegalArgumentException, IllegalAccessException {
            return field.get(receiver);
        }

        @TruffleBoundary
        private static void reflectSet(Field field, Object receiver, Object value) throws IllegalArgumentException, IllegalAccessException {
            field.set(receiver, value);
        }

        @Override
        public int hashCode() {
            return field.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ReflectImpl other = (ReflectImpl) obj;
            return this.field.equals(other.field);
        }

        @Override
        public String toString() {
            return "Field[" + field.toString() + "]";
        }
    }

    static final class MHImpl extends HostFieldDesc {
        private final MethodHandles.Lookup methodLookup;
        private final Field field;
        @CompilationFinal private MethodHandle getHandle;
        @CompilationFinal private MethodHandle setHandle;

        MHImpl(MethodHandles.Lookup methodLookup, Field field) {
            super(field.getType(), field.getGenericType(), field.getName(), Modifier.isFinal(field.getModifiers()));
            this.methodLookup = methodLookup;
            this.field = field;
        }

        @Override
        public Object get(Object receiver) throws UnknownMemberException {
            if (getHandle == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getHandle = makeGetMethodHandle();
            }
            try {
                return invokeGetHandle(getHandle, receiver);
            } catch (UnknownMemberException e) {
                throw e;
            } catch (Throwable e) {
                throw HostInteropReflect.rethrow(e);
            }
        }

        @Override
        public void set(Object receiver, Object value) throws UnknownMemberException {
            if (setHandle == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setHandle = makeSetMethodHandle();
            }
            try {
                invokeSetHandle(setHandle, receiver, value);
            } catch (UnknownMemberException e) {
                throw e;
            } catch (Throwable e) {
                throw HostInteropReflect.rethrow(e);
            }
        }

        @Override
        public boolean isStatic() {
            return Modifier.isStatic(field.getModifiers());
        }

        @Override
        public Class<?> getDeclaringClass() {
            return field.getDeclaringClass();
        }

        @TruffleBoundary(allowInlining = true)
        private Object invokeGetHandle(MethodHandle invokeHandle, Object receiver) throws Throwable {
            try {
                return invokeHandle.invokeExact(receiver);
            } catch (WrongMethodTypeException | ClassCastException e) {
                throw UnknownMemberException.create(this);
            }
        }

        @TruffleBoundary(allowInlining = true)
        private void invokeSetHandle(MethodHandle invokeHandle, Object receiver, Object value) throws Throwable {
            try {
                invokeHandle.invokeExact(receiver, value);
            } catch (WrongMethodTypeException | ClassCastException e) {
                throw UnknownMemberException.create(this);
            }
        }

        private MethodHandle makeGetMethodHandle() {
            CompilerAsserts.neverPartOfCompilation();
            try {
                MethodHandle getter = methodLookup.unreflectGetter(field);
                if (Modifier.isStatic(field.getModifiers())) {
                    return MethodHandles.dropArguments(getter.asType(MethodType.methodType(Object.class)), 0, Object.class);
                } else {
                    return getter.asType(MethodType.methodType(Object.class, Object.class));
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        private MethodHandle makeSetMethodHandle() {
            CompilerAsserts.neverPartOfCompilation();
            try {
                MethodHandle setter = methodLookup.unreflectSetter(field);
                if (Modifier.isStatic(field.getModifiers())) {
                    return MethodHandles.dropArguments(setter.asType(MethodType.methodType(void.class, Object.class)), 0, Object.class);
                } else {
                    return setter.asType(MethodType.methodType(void.class, Object.class, Object.class));
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public int hashCode() {
            return field.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final MHImpl other = (MHImpl) obj;
            return this.field.equals(other.field);
        }

        @Override
        public String toString() {
            return "Field[" + field.toString() + "]";
        }
    }

    static final class SyntheticArrayLengthField extends HostFieldDesc {
        static final SyntheticArrayLengthField SINGLETON = new SyntheticArrayLengthField();

        private SyntheticArrayLengthField() {
            super(int.class, int.class, "length", true /* disallow writes */);
        }

        @Override
        public Object get(Object receiver) throws UnknownMemberException {
            try {
                return Array.getLength(receiver);
            } catch (IllegalArgumentException e) {
                throw UnknownMemberException.create(this);
            }
        }

        @Override
        public void set(Object receiver, Object value) throws UnknownMemberException {
            throw UnknownMemberException.create(this);
        }

        @Override
        public boolean isStatic() {
            return false;
        }

        @Override
        public Class<?> getDeclaringClass() {
            return Object[].class;
        }

        @Override
        public String toString() {
            return "Field[length]";
        }
    }

    static final class SyntheticClassField extends HostFieldDesc {

        private final Class<?> declaringClass;
        private final HostContext context;

        SyntheticClassField(Class<?> declaringClass, String name, HostContext context) {
            super(Class.class, Class.class, name, true);
            this.declaringClass = declaringClass;
            this.context = context;
        }

        @Override
        public Object get(Object receiver) throws UnknownMemberException {
            return switch (getName()) {
                case HostInteropReflect.STATIC_TO_CLASS -> HostObject.forClass(declaringClass, context);
                case HostInteropReflect.CLASS_TO_STATIC -> HostObject.forStaticClass(declaringClass, context);
                default -> {
                    throw UnknownMemberException.create(this);
                }
            };
        }

        @Override
        public void set(Object receiver, Object value) throws UnknownMemberException {
            throw UnknownMemberException.create(this);
        }

        @Override
        public boolean isStatic() {
            return true;
        }

        @Override
        public Class<?> getDeclaringClass() {
            return declaringClass;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 83 * hash + Objects.hashCode(this.declaringClass);
            hash = 83 * hash + Objects.hashCode(this.getName());
            hash = 83 * hash + Objects.hashCode(this.context);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final SyntheticClassField other = (SyntheticClassField) obj;
            if (!this.declaringClass.equals(other.declaringClass)) {
                return false;
            }
            if (!this.getName().equals(other.getName())) {
                return false;
            }
            return this.context == other.context;
        }

    }
}
