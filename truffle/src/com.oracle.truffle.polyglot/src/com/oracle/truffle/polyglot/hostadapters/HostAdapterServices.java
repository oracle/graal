/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot.hostadapters;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

import org.graalvm.polyglot.Value;

/**
 * Provides static utility services to generated host adapter classes. This class needs to be public
 * and accessible so that its methods can be called from the generated class.
 */
final class HostAdapterServices {
    private static final MethodType VALUE_EXECUTE_METHOD_TYPE = MethodType.methodType(Value.class, Object[].class);
    private static final MethodType VALUE_EXECUTE_VOID_METHOD_TYPE = MethodType.methodType(void.class, Object[].class);
    private static final MethodType VALUE_INVOKE_MEMBER_METHOD_TYPE = MethodType.methodType(Value.class, String.class, Object[].class);
    private static final MethodType VALUE_AS_METHOD_TYPE = MethodType.methodType(Object.class, Class.class);
    private static final MethodType CONCAT_ARRAYS_METHOD_TYPE = MethodType.methodType(Object[].class, Object[].class, Object.class);

    // Keep in sync with constants in HostAdapterBytecodeGenerator
    static final int BOOTSTRAP_VALUE_INVOKE_MEMBER = 1 << 0;
    static final int BOOTSTRAP_VALUE_EXECUTE = 1 << 1;
    static final int BOOTSTRAP_VARARGS = 1 << 2;

    /**
     * Annotation used by the to mark public constructors, methods and fields in generated classes
     * that should be accessible by the guest application.
     *
     * @see org.graalvm.polyglot.HostAccess.Export
     */
    @Target({ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Export {
    }

    private HostAdapterServices() {
    }

    /**
     * Returns the class-level overrides guest object used to define methods for the adapter class
     * being initialized. The object is retrieved from the class loader.
     *
     * This method is public solely for implementation reasons, so the adapter classes can invoke it
     * from their static initializers.
     *
     * @return the guest object used to define methods for the class being initialized.
     */
    @SuppressWarnings("unchecked")
    public static Value getClassOverrides(ClassLoader classLoader) {
        return Objects.requireNonNull(((Supplier<Value>) classLoader).get());
    }

    /**
     * Given a guest object and a method name, checks if a method can be invoked.
     *
     * This method is public mainly for implementation reasons, so the adapter classes can invoke
     * it.
     *
     * @param obj the guest object
     * @param name the name of the invokable member
     * @return {@code true} if the {@link Value} has an invokable member function, {@code false} if
     *         the member does not exist or is not invokable.
     */
    public static boolean hasMethod(final Value obj, final String name) {
        return obj.canInvokeMember(name);
    }

    /**
     * Checks if the given guest object has an own (non-inherited) member with the given name.
     *
     * This method is public mainly for implementation reasons, so the adapter classes can invoke
     * it.
     *
     * @param obj the guest object
     * @param name the name of the invokable member
     * @return {@code true} if and only if the {@link Value} has an own member with the given name.
     */
    public static boolean hasOwnMethod(final Value obj, final String name) {
        return obj.canInvokeMember(name);
    }

    /**
     * Checks if the {@link Value} is executable.
     */
    public static boolean isFunction(final Object obj) {
        return obj instanceof Value && ((Value) obj).canExecute();
    }

    /**
     * Creates and returns a new {@link UnsupportedOperationException}. Makes generated bytecode
     * smaller by doing {@code INVOKESTATIC} to this method rather than the {@code NEW}, {@code DUP}
     * ({@code DUP_X1}, {@code SWAP}), {@code INVOKESPECIAL <init>} sequence.
     *
     * @return a newly created {@link UnsupportedOperationException}.
     */
    public static UnsupportedOperationException unsupported(String methodName) {
        return new UnsupportedOperationException(methodName);
    }

    /**
     * Returns a new {@link RuntimeException} wrapping the passed throwable if necessary. Makes
     * generated bytecode smaller by doing an {@code INVOKESTATIC} to this method rather than the
     * {@code NEW}, {@code DUP_X1}, {@code SWAP}, {@code INVOKESPECIAL <init>} sequence.
     *
     * @param t the original throwable to wrap
     * @return a newly created runtime exception wrapping the passed throwable.
     */
    public static RuntimeException wrapThrowable(final Throwable t) {
        return new RuntimeException(t);
    }

    private static MethodHandle createReturnValueConverter(MethodHandles.Lookup lookup, Class<?> returnType) throws NoSuchMethodException, IllegalAccessException {
        return MethodHandles.insertArguments(lookup.findVirtual(Value.class, "as", VALUE_AS_METHOD_TYPE), 1, returnType);
    }

    /**
     * Bootstrap a typed method handle for {@link Value#invokeMember} or {@link Value#execute}.
     *
     * This method is public solely for implementation reasons, so the adapter classes can use it.
     *
     * @param methodName the method's name.
     * @param type the method's type with a leading receiver {@link Value} parameter.
     * @param flags 0 for {@link Value#invokeMember}, 1 for {@link Value#execute}.
     * @return a CallSite for invoking the member of, or executing a {@link Value}.
     */
    public static CallSite bootstrap(MethodHandles.Lookup lookup, String methodName, MethodType type, int flags) throws NoSuchMethodException, IllegalAccessException {
        MethodHandle target;
        if ((flags & BOOTSTRAP_VALUE_INVOKE_MEMBER) != 0) {
            target = lookup.findVirtual(Value.class, "invokeMember", VALUE_INVOKE_MEMBER_METHOD_TYPE);
            // insert method name parameter
            target = MethodHandles.insertArguments(target, 1, methodName);
        } else {
            assert (flags & BOOTSTRAP_VALUE_EXECUTE) != 0;
            if (type.returnType() == void.class) {
                target = lookup.findVirtual(Value.class, "executeVoid", VALUE_EXECUTE_VOID_METHOD_TYPE);
            } else {
                target = lookup.findVirtual(Value.class, "execute", VALUE_EXECUTE_METHOD_TYPE);
            }
        }

        boolean varargs = (flags & BOOTSTRAP_VARARGS) != 0;
        if (varargs) {
            Class<?> varargsParameter = type.parameterType(type.parameterCount() - 1);
            if (type.parameterCount() == 2 && varargsParameter == Object[].class) {
                // easy case: no need to collect anything, just pass through
            } else {
                // collect non-varargs arguments into an Object[]
                MethodHandle fixedCollector = MethodHandles.identity(Object[].class).asCollector(Object[].class, type.parameterCount() - 2);
                MethodType fixedCollectorType = MethodType.methodType(Object[].class, Arrays.copyOfRange(type.parameterArray(), 1, type.parameterCount() - 1));
                fixedCollector = fixedCollector.asType(fixedCollectorType);

                // concatenate fixed Object[] and varargs array
                MethodHandle concatArray = lookup.findStatic(HostAdapterServices.class, "concatArrays", CONCAT_ARRAYS_METHOD_TYPE);
                concatArray = concatArray.asType(CONCAT_ARRAYS_METHOD_TYPE.changeParameterType(1, varargsParameter));

                // combine collectors => Object[](fixed..., varargs)
                MethodHandle collector = MethodHandles.collectArguments(concatArray, 0, fixedCollector);

                // apply collector
                target = MethodHandles.collectArguments(target, 1, collector);
            }
        } else {
            // collect arguments
            target = target.asCollector(Object[].class, type.parameterCount() - 1);
        }

        if (type.returnType() != void.class) {
            target = MethodHandles.filterReturnValue(target, createReturnValueConverter(lookup, type.returnType()));
        }

        target = target.asType(type);
        return new ConstantCallSite(target);
    }

    public static Object[] concatArrays(Object[] fixed, Object va) {
        int fixedLen = fixed.length;
        int vaLen = Array.getLength(va);
        Object[] concat = Arrays.copyOf(fixed, fixedLen + vaLen);
        for (int i = 0; i < vaLen; i++) {
            concat[fixedLen + i] = Array.get(va, i);
        }
        return concat;
    }
}
