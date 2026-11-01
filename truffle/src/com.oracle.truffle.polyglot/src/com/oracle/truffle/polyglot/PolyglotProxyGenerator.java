/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_FINAL;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_PRIVATE;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_PUBLIC;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_STATIC;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_SUPER;

import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.impl.asm.ClassWriter;
import com.oracle.truffle.api.impl.asm.Label;
import com.oracle.truffle.api.impl.asm.Opcodes;
import com.oracle.truffle.api.impl.asm.Type;
import com.oracle.truffle.api.impl.asm.commons.InstructionAdapter;
import com.oracle.truffle.polyglot.PolyglotGeneratedProxy.DefaultMethodFallbackException;

/**
 * Generates interface implementations extending {@link PolyglotGeneratedProxy}.
 */
final class PolyglotProxyGenerator {

    private static final Type OBJECT_TYPE = Type.getType(Object.class);
    private static final Type CALL_TARGET_TYPE = Type.getType(CallTarget.class);
    private static final Type TARGET_ARRAY_TYPE = Type.getType(CallTarget[].class);
    private static final Type GENERATED_PROXY_TYPE = Type.getType(PolyglotGeneratedProxy.class);
    private static final String CALL_TARGET_NAME = CALL_TARGET_TYPE.getInternalName();
    private static final String GENERATED_PROXY_NAME = GENERATED_PROXY_TYPE.getInternalName();
    private static final String UNSUPPORTED_OPERATION_EXCEPTION_NAME = Type.getInternalName(UnsupportedOperationException.class);
    private static final String DEFAULT_METHOD_FALLBACK_EXCEPTION_NAME = Type.getInternalName(DefaultMethodFallbackException.class);
    private static final String TARGETS_FIELD = "targets";

    private final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    private final String className;
    private final Type classType;
    private final String interfaceName;
    private final Class<?> interfaceType;
    private final String[] exactEntryNames;

    private PolyglotProxyGenerator(String className, Class<?> interfaceType, String[] exactEntryNames) {
        this.className = className;
        classType = Type.getObjectType(className);
        interfaceName = Type.getInternalName(interfaceType);
        this.interfaceType = interfaceType;
        this.exactEntryNames = exactEntryNames;
    }

    static byte[] generate(String className, Class<?> interfaceType, Method[] methods, String[] exactEntryNames) {
        assert methods.length == exactEntryNames.length;
        return new PolyglotProxyGenerator(className, interfaceType, exactEntryNames).generate(methods);
    }

    private byte[] generate(Method[] methods) {
        writer.visit(Opcodes.V17, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, className, null, GENERATED_PROXY_NAME, new String[]{interfaceName});
        writer.visitField(ACC_PRIVATE | ACC_FINAL, TARGETS_FIELD, TARGET_ARRAY_TYPE.getDescriptor(), null, null).visitEnd();
        emitConstructor();
        for (int i = 0; i < methods.length; i++) {
            emitMethod(methods[i], i);
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private void emitConstructor() {
        String descriptor = Type.getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE, OBJECT_TYPE, OBJECT_TYPE, CALL_TARGET_TYPE, CALL_TARGET_TYPE, CALL_TARGET_TYPE, TARGET_ARRAY_TYPE);
        InstructionAdapter method = new InstructionAdapter(writer.visitMethod(ACC_PUBLIC, "<init>", descriptor, null, null));
        method.visitCode();
        method.load(0, classType);
        method.load(1, OBJECT_TYPE);
        method.load(2, OBJECT_TYPE);
        method.load(3, OBJECT_TYPE);
        method.load(4, CALL_TARGET_TYPE);
        method.load(5, CALL_TARGET_TYPE);
        method.load(6, CALL_TARGET_TYPE);
        method.invokespecial(GENERATED_PROXY_NAME, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE, OBJECT_TYPE, OBJECT_TYPE, CALL_TARGET_TYPE, CALL_TARGET_TYPE, CALL_TARGET_TYPE),
                        false);
        method.load(0, classType);
        method.load(7, TARGET_ARRAY_TYPE);
        method.putfield(className, TARGETS_FIELD, TARGET_ARRAY_TYPE.getDescriptor());
        method.areturn(Type.VOID_TYPE);
        endMethod(method);
    }

    /**
     * Emits a public final interface method, for example:
     *
     * <pre>
     * public final int specialized(int value) {
     *     CallTarget target = targets[0];
     *     // Inside the outer try/catch emitted by emitExceptionHandlers:
     *     return specialized$exact(target, languageContext, receiver, value);
     * }
     * </pre>
     *
     * Generic methods call the target directly; default methods use {@link #emitDefaultDispatch}.
     */
    private void emitMethod(Method reflected, int index) {
        String descriptor = Type.getMethodDescriptor(reflected);
        Type[] argumentTypes = Type.getArgumentTypes(descriptor);
        Type returnType = Type.getReturnType(descriptor);
        String[] exceptions = exceptionNames(reflected);
        String exactDescriptor = exactEntryNames[index] != null ? emitExactEntry(index, argumentTypes, returnType, exceptions) : null;

        int flags = ACC_PUBLIC | ACC_FINAL | (reflected.isVarArgs() ? Opcodes.ACC_VARARGS : 0);
        InstructionAdapter method = new InstructionAdapter(writer.visitMethod(flags, reflected.getName(), descriptor, null, exceptions));
        method.visitCode();

        int targetLocal = firstFreeLocal(argumentTypes);
        Label methodStart = new Label();
        method.mark(methodStart);
        method.load(0, classType);
        method.getfield(className, TARGETS_FIELD, TARGET_ARRAY_TYPE.getDescriptor());
        method.iconst(index);
        method.aload(CALL_TARGET_TYPE);
        method.store(targetLocal, CALL_TARGET_TYPE);

        if (reflected.isDefault()) {
            emitDefaultDispatch(method, reflected.getName(), descriptor, index, argumentTypes, returnType, targetLocal, exactDescriptor);
        } else {
            method.load(targetLocal, CALL_TARGET_TYPE);
            emitTargetCall(method, index, argumentTypes, exactDescriptor);
            emitDispatchReturn(method, returnType, exactDescriptor != null);
        }
        emitExceptionHandlers(method, methodStart, exceptions, targetLocal + 2);
        endMethod(method);
    }

    /**
     * Emits the boxed fallback for runtime-specific typed dispatch, for example:
     *
     * <pre>
     * private static int specialized$exact(CallTarget target, Object languageContext, Object receiver, int value) {
     *     return (int) target.call(new Object[]{languageContext, receiver, value});
     * }
     * </pre>
     *
     * For void interface methods, the exact entry returns Object and the caller discards it.
     */
    private String emitExactEntry(int index, Type[] argumentTypes, Type returnType, String[] exceptions) {
        Type[] exactArguments = new Type[argumentTypes.length + 3];
        exactArguments[0] = CALL_TARGET_TYPE;
        exactArguments[1] = OBJECT_TYPE;
        exactArguments[2] = OBJECT_TYPE;
        System.arraycopy(argumentTypes, 0, exactArguments, 3, argumentTypes.length);
        /* A void proxy method discards the Object returned by CallTarget.call. */
        Type exactReturnType = returnType.getSort() == Type.VOID ? OBJECT_TYPE : returnType;
        String descriptor = Type.getMethodDescriptor(exactReturnType, exactArguments);
        InstructionAdapter entry = new InstructionAdapter(writer.visitMethod(ACC_PRIVATE | ACC_STATIC, exactEntryNames[index], descriptor, null, exceptions));
        entry.visitCode();
        entry.load(0, CALL_TARGET_TYPE);
        emitTargetArgumentArray(entry, argumentTypes, 3, true);
        entry.invokeinterface(CALL_TARGET_NAME, "call", Type.getMethodDescriptor(OBJECT_TYPE, Type.getType(Object[].class)));
        if (returnType.getSort() == Type.VOID) {
            entry.areturn(OBJECT_TYPE);
        } else {
            emitReturn(entry, returnType);
        }
        endMethod(entry);
        return descriptor;
    }

    /**
     * Emits a target invocation, with the target already on the operand stack:
     *
     * <pre>
     * // Exact dispatch:
     * specialized$exact(target, languageContext, receiver, value)
     * // Generic dispatch:
     * target.call(new Object[]{languageContext, receiver, value})
     * </pre>
     *
     * {@link #emitDispatchReturn} emits the return, including any cast, unboxing, or discarded result.
     */
    private void emitTargetCall(InstructionAdapter method, int index, Type[] argumentTypes, String exactDescriptor) {
        if (exactDescriptor != null) {
            method.load(0, classType);
            method.getfield(GENERATED_PROXY_NAME, "languageContext", OBJECT_TYPE.getDescriptor());
            method.load(0, classType);
            method.getfield(GENERATED_PROXY_NAME, "receiver", OBJECT_TYPE.getDescriptor());
            loadParameters(method, argumentTypes, 1);
            method.invokestatic(className, exactEntryNames[index], exactDescriptor, false);
        } else {
            emitTargetArgumentArray(method, argumentTypes, 1, false);
            method.invokeinterface(CALL_TARGET_NAME, "call", Type.getMethodDescriptor(OBJECT_TYPE, Type.getType(Object[].class)));
        }
    }

    private static void emitDispatchReturn(InstructionAdapter method, Type returnType, boolean exact) {
        if (exact) {
            if (returnType.getSort() == Type.VOID) {
                method.pop();
            }
            method.areturn(returnType);
        } else {
            emitReturn(method, returnType);
        }
    }

    /**
     * Emits default-method dispatch, shown here for a generic method returning Object:
     *
     * <pre>
     * if (target == null) {
     *     return Interface.super.defaultMethod(value);
     * }
     * try {
     *     return target.call(new Object[]{languageContext, receiver, value});
     * } catch (DefaultMethodFallbackException | UnsupportedOperationException original) {
     *     try {
     *         return Interface.super.defaultMethod(value);
     *     } catch (Exception fallbackFailure) {
     *         throw PolyglotGeneratedProxy.fallbackFailure(original, "defaultMethod", fallbackFailure);
     *     }
     * }
     * </pre>
     *
     * A null target selects the Java default for functional proxies. Object proxies try guest dispatch
     * first. Never mutate the shared fallback signal: if the Java default also fails,
     * {@code fallbackFailure} replaces the signal with a fresh guest failure before suppressing the
     * default exception. Errors propagate without suppression. Exact dispatch uses an exact entry
     * instead of calling the target directly.
     */
    private void emitDefaultDispatch(InstructionAdapter method, String name, String descriptor, int index, Type[] argumentTypes, Type returnType, int targetLocal,
                    String exactDescriptor) {
        Label targetDispatch = new Label();
        method.load(targetLocal, CALL_TARGET_TYPE);
        method.ifnonnull(targetDispatch);
        emitDefaultCall(method, name, descriptor, argumentTypes, returnType);
        method.mark(targetDispatch);
        Label targetStart = new Label();
        Label targetEnd = new Label();
        Label defaultFallback = new Label();
        method.visitTryCatchBlock(targetStart, targetEnd, defaultFallback, DEFAULT_METHOD_FALLBACK_EXCEPTION_NAME);
        method.visitTryCatchBlock(targetStart, targetEnd, defaultFallback, UNSUPPORTED_OPERATION_EXCEPTION_NAME);
        method.mark(targetStart);
        method.load(targetLocal, CALL_TARGET_TYPE);
        emitTargetCall(method, index, argumentTypes, exactDescriptor);
        method.mark(targetEnd);
        emitDispatchReturn(method, returnType, exactDescriptor != null);

        method.mark(defaultFallback);
        method.store(targetLocal + 1, OBJECT_TYPE);
        Label defaultStart = new Label();
        Label defaultEnd = new Label();
        Label defaultFailure = new Label();
        method.visitTryCatchBlock(defaultStart, defaultEnd, defaultFailure, Type.getInternalName(Exception.class));
        method.mark(defaultStart);
        emitDefaultCall(method, name, descriptor, argumentTypes, returnType);
        method.mark(defaultEnd);
        method.mark(defaultFailure);
        method.store(targetLocal + 2, OBJECT_TYPE);
        method.load(targetLocal + 1, OBJECT_TYPE);
        method.aconst(name);
        method.load(targetLocal + 2, OBJECT_TYPE);
        method.invokestatic(GENERATED_PROXY_NAME, "fallbackFailure",
                        Type.getMethodDescriptor(Type.getType(RuntimeException.class), Type.getType(RuntimeException.class), Type.getType(String.class), Type.getType(Exception.class)), false);
        method.athrow();
    }

    /**
     * Emits outer handlers preserving declared checked exceptions and wrapping undeclared ones, like
     * JDK proxies. The checked handlers use the intersection of matching interface declarations:
     *
     * <pre>
     * try {
     *     // Exact, generic, or default-method dispatch.
     * } catch (RuntimeException | Error failure) {
     *     throw failure;
     * } catch (DeclaredCheckedException failure) {
     *     throw failure;
     * } catch (Throwable failure) {
     *     throw new UndeclaredThrowableException(failure);
     * }
     * </pre>
     */
    private static void emitExceptionHandlers(InstructionAdapter method, Label methodStart, String[] exceptions, int failureLocal) {
        Label methodEnd = new Label();
        Label rethrow = new Label();
        Label wrap = new Label();
        method.mark(methodEnd);
        /* Register outer handlers after the guest/default fallback handlers. */
        method.visitTryCatchBlock(methodStart, methodEnd, rethrow, Type.getInternalName(RuntimeException.class));
        method.visitTryCatchBlock(methodStart, methodEnd, rethrow, Type.getInternalName(Error.class));
        for (String exception : exceptions) {
            method.visitTryCatchBlock(methodStart, methodEnd, rethrow, exception);
        }
        method.visitTryCatchBlock(methodStart, methodEnd, wrap, Type.getInternalName(Throwable.class));
        method.mark(rethrow);
        method.athrow();
        method.mark(wrap);
        method.store(failureLocal, OBJECT_TYPE);
        Type wrapper = Type.getType(UndeclaredThrowableException.class);
        method.anew(wrapper);
        method.dup();
        method.load(failureLocal, OBJECT_TYPE);
        method.invokespecial(wrapper.getInternalName(), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Throwable.class)), false);
        method.athrow();
    }

    private void emitDefaultCall(InstructionAdapter method, String name, String descriptor, Type[] argumentTypes, Type returnType) {
        method.load(0, classType);
        loadParameters(method, argumentTypes, 1);
        method.invokespecial(interfaceName, name, descriptor, true);
        method.areturn(returnType);
    }

    private void emitTargetArgumentArray(InstructionAdapter method, Type[] types, int firstParameterLocal, boolean exactEntry) {
        method.iconst(types.length + 2);
        method.newarray(OBJECT_TYPE);
        method.dup();
        method.iconst(0);
        if (exactEntry) {
            method.load(1, OBJECT_TYPE);
        } else {
            method.load(0, classType);
            method.getfield(GENERATED_PROXY_NAME, "languageContext", OBJECT_TYPE.getDescriptor());
        }
        method.astore(OBJECT_TYPE);
        method.dup();
        method.iconst(1);
        if (exactEntry) {
            method.load(2, OBJECT_TYPE);
        } else {
            method.load(0, classType);
            method.getfield(GENERATED_PROXY_NAME, "receiver", OBJECT_TYPE.getDescriptor());
        }
        method.astore(OBJECT_TYPE);
        int local = firstParameterLocal;
        for (int i = 0; i < types.length; i++) {
            Type type = types[i];
            method.dup();
            method.iconst(i + 2);
            method.load(local, type);
            box(method, type);
            method.astore(OBJECT_TYPE);
            local += type.getSize();
        }
    }

    private static void loadParameters(InstructionAdapter method, Type[] types, int firstLocal) {
        int local = firstLocal;
        for (Type type : types) {
            method.load(local, type);
            local += type.getSize();
        }
    }

    private static int firstFreeLocal(Type[] types) {
        int local = 1;
        for (Type type : types) {
            local += type.getSize();
        }
        return local;
    }

    private static void box(InstructionAdapter method, Type type) {
        Type wrapper = wrapper(type);
        if (wrapper != null) {
            method.invokestatic(wrapper.getInternalName(), "valueOf", Type.getMethodDescriptor(wrapper, type), false);
        }
    }

    private static void unbox(InstructionAdapter method, Type type) {
        if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
            method.checkcast(type);
            return;
        }
        Type wrapper = wrapper(type);
        method.checkcast(wrapper);
        method.invokevirtual(wrapper.getInternalName(), type.getClassName() + "Value", Type.getMethodDescriptor(type), false);
    }

    private static Type wrapper(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
                return Type.getType(Boolean.class);
            case Type.BYTE:
                return Type.getType(Byte.class);
            case Type.CHAR:
                return Type.getType(Character.class);
            case Type.SHORT:
                return Type.getType(Short.class);
            case Type.INT:
                return Type.getType(Integer.class);
            case Type.FLOAT:
                return Type.getType(Float.class);
            case Type.LONG:
                return Type.getType(Long.class);
            case Type.DOUBLE:
                return Type.getType(Double.class);
            default:
                return null;
        }
    }

    private static void emitReturn(InstructionAdapter method, Type type) {
        if (type.getSort() == Type.VOID) {
            method.pop();
        } else {
            unbox(method, type);
        }
        method.areturn(type);
    }

    private String[] exceptionNames(Method method) {
        ArrayList<Class<?>> exceptions = new ArrayList<>(Arrays.asList(method.getExceptionTypes()));
        String descriptor = Type.getMethodDescriptor(method);
        for (Method other : interfaceType.getMethods()) {
            if (!other.getName().equals(method.getName()) || !Type.getMethodDescriptor(other).equals(descriptor)) {
                continue;
            }
            ArrayList<Class<?>> intersection = new ArrayList<>();
            for (Class<?> exception : exceptions) {
                for (Class<?> otherException : other.getExceptionTypes()) {
                    Class<?> allowed = exception.isAssignableFrom(otherException) ? otherException : otherException.isAssignableFrom(exception) ? exception : null;
                    if (allowed != null && !intersection.contains(allowed)) {
                        intersection.add(allowed);
                    }
                }
            }
            exceptions = intersection;
        }
        String[] names = new String[exceptions.size()];
        for (int i = 0; i < exceptions.size(); i++) {
            names[i] = Type.getInternalName(exceptions.get(i));
        }
        return names;
    }

    private static void endMethod(InstructionAdapter method) {
        method.visitMaxs(0, 0);
        method.visitEnd();
    }
}
