/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.vmaccess;

import java.lang.reflect.Type;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedJavaMethod;
import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoSignature;
import com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedInstanceType;

import jdk.graal.compiler.vmaccess.InvocationException;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;

final class EspressoExternalResolvedJavaMethod extends AbstractEspressoResolvedJavaMethod {
    static final EspressoExternalResolvedJavaMethod[] EMPTY_ARRAY = new EspressoExternalResolvedJavaMethod[0];
    private static final ExceptionHandler[] NO_HANDLERS = new ExceptionHandler[0];
    private static final LocalVariableTable EMPTY_LVT = new LocalVariableTable(new Local[0]);

    private final Value methodMirror;
    private final int flags;

    EspressoExternalResolvedJavaMethod(EspressoExternalResolvedInstanceType holder, Value methodMirror) {
        super(holder, methodMirror.getMember("hasPoison").asBoolean());
        this.methodMirror = methodMirror;
        this.flags = methodMirror.getMember("flags").asInt();
    }

    Value getMirror() {
        return methodMirror;
    }

    private EspressoExternalVMAccess getAccess() {
        return ((EspressoExternalResolvedInstanceType) getDeclaringClass()).getAccess();
    }

    @Override
    protected byte[] getCode0() {
        Value value = methodMirror.getMember("code");
        assert !value.isNull() : this;
        assert value.hasBufferElements() : this + " " + value;
        int size = Math.toIntExact(value.getBufferSize());
        byte[] buf = new byte[size];
        value.readBuffer(0, buf, 0, size);
        return buf;
    }

    @Override
    protected int getCodeSize0() {
        return methodMirror.getMember("codeSize").asInt();
    }

    @Override
    protected String getName0() {
        return methodMirror.getMember("name").asString();
    }

    @Override
    protected AbstractEspressoSignature getSignature0() {
        return new EspressoExternalSignature(getAccess(), methodMirror.getMember("rawSignature").asString());
    }

    @Override
    protected boolean isForceInline() {
        return false;
    }

    @Override
    protected int getVtableIndexForInterfaceMethod(EspressoResolvedInstanceType resolved) {
        return 0;
    }

    @Override
    protected int getVtableIndex() {
        return 0;
    }

    @Override
    public int getMaxLocals() {
        return methodMirror.getMember("maxLocals").asInt();
    }

    @Override
    public int getMaxStackSize() {
        return methodMirror.getMember("maxStackSize").asInt();
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        Value handlers = methodMirror.getMember("exceptionHandlers");
        if (handlers.isNull()) {
            return NO_HANDLERS;
        }
        assert handlers.hasArrayElements();
        int size = Math.toIntExact(handlers.getArraySize());
        ExceptionHandler[] result = new ExceptionHandler[size];
        for (int i = 0; i < size; i++) {
            Value handler = handlers.getArrayElement(i);
            int startBCI = handler.getMember("startBCI").asInt();
            int endBCI = handler.getMember("endBCI").asInt();
            int handlerBCI = handler.getMember("handlerBCI").asInt();
            int catchTypeCPI = handler.getMember("catchTypeCPI").asInt();
            String catchTypeName = handler.getMember("catchType").asString();
            JavaType catchType = getAccess().lookupType(catchTypeName, getDeclaringClass(), false);
            result[i] = new ExceptionHandler(startBCI, endBCI, handlerBCI, catchTypeCPI, catchType);
        }
        return result;
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        int line;
        if (isNative()) {
            line = -2;
        } else {
            LineNumberTable lineNumberTable = getLineNumberTable();
            if (lineNumberTable == null) {
                line = -1;
            } else {
                line = lineNumberTable.getLineNumber(bci);
            }
        }
        // Currently missing: module and class loader names
        return new StackTraceElement(getDeclaringClass().getName(), getName(), getDeclaringClass().getSourceFileName(), line);
    }

    @Override
    public Type[] getGenericParameterTypes() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public boolean hasNeverInlineDirective() {
        return methodMirror.getMember("neverInline").asBoolean();
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        Value rawData = methodMirror.getMember("lineNumberTable");
        if (rawData.isNull()) {
            return null;
        }
        assert rawData.hasArrayElements() && rawData.getArraySize() % 2 == 0;
        int size = Math.toIntExact(rawData.getArraySize() / 2);
        int[] lineNumbers = new int[size];
        int[] bcis = new int[size];
        assert size * 2L + 1L < Integer.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            lineNumbers[i] = rawData.getArrayElement(i * 2L).asInt();
            bcis[i] = rawData.getArrayElement(i * 2L + 1L).asInt();
        }
        return new LineNumberTable(lineNumbers, bcis);
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        Value table = methodMirror.getMember("localVariableTable");
        if (table.isNull()) {
            return EMPTY_LVT;
        }
        assert table.hasArrayElements();
        int size = Math.toIntExact(table.getArraySize());
        Local[] result = new Local[size];
        for (int i = 0; i < size; i++) {
            Value handler = table.getArrayElement(i);
            String name = handler.getMember("name").asString();
            int startBCI = handler.getMember("startBCI").asInt();
            int endBCI = handler.getMember("endBCI").asInt();
            int slot = handler.getMember("slot").asInt();
            String typeName = handler.getMember("catchType").asString();
            JavaType type = getAccess().lookupType(typeName, getDeclaringClass(), false);
            result[i] = new Local(name, type, startBCI, endBCI, slot);
        }
        return new LocalVariableTable(result);
    }

    @Override
    protected int getFlags() {
        return flags;
    }

    @Override
    public Parameter[] getParameters() {
        if (getSignature().getParameterCount(false) == 0) {
            return NO_PARAMETERS;
        }
        throw JVMCIError.unimplemented();
    }

    @Override
    public boolean isLeafMethod() {
        return methodMirror.getMember("leafMethod").asBoolean();
    }

    @Override
    protected byte[] getRawAnnotationBytes(int category) {
        return getAccess().getRawAnnotationBytes(methodMirror, category);
    }

    @Override
    protected boolean hasAnnotations() {
        throw JVMCIError.unimplemented();
    }

    @Override
    protected boolean equals0(AbstractEspressoResolvedJavaMethod that) {
        if (that instanceof EspressoExternalResolvedJavaMethod espressoMethod) {
            return this.methodMirror.equals(espressoMethod.methodMirror);
        }
        return false;
    }

    @Override
    protected int hashCode0() {
        return methodMirror.hashCode();
    }

    JavaConstant invoke(JavaConstant receiver, JavaConstant... arguments) {
        if (isStatic() || isConstructor()) {
            if (receiver != null) {
                throw new IllegalArgumentException("For static methods or constructors, the receiver argument must be null");
            }
        } else if (receiver == null) {
            throw new NullPointerException("For instance methods, the receiver argument must not be null");
        } else if (receiver.isNull()) {
            throw new IllegalArgumentException("For instance methods, the receiver argument must not represent a null constant");
        }
        AbstractEspressoSignature signature = getSignature();
        int parameterCount = signature.getParameterCount(false);
        if (parameterCount != arguments.length) {
            throw new IllegalArgumentException("Expected " + parameterCount + " arguments, got " + arguments.length);
        }
        Object[] args = new Object[arguments.length + (isConstructor() || !isStatic() ? 1 : 0)];
        int outputArgumentOffset = 0;
        EspressoExternalVMAccess access = getAccess();
        if (isConstructor()) {
            EspressoExternalResolvedInstanceType type = (EspressoExternalResolvedInstanceType) getDeclaringClass();
            args[0] = access.unsafeAllocateInstance(type).getValue();
            outputArgumentOffset = 1;
        } else if (!isStatic()) {
            if (!(receiver instanceof EspressoExternalObjectConstant objectConstant)) {
                throw new IllegalArgumentException("Bad receiver: expected Object, got " + arguments[0].getJavaKind());
            }
            args[0] = objectConstant.getValue();
            outputArgumentOffset = 1;
        }
        for (int i = 0; i < parameterCount; i++) {
            JavaConstant argument = arguments[i];
            JavaKind argumentKind = argument.getJavaKind();
            /*
             * Perform widening primitive conversions (JLS 5.1.2) in order to implement strict
             * method invocation conversions (JLS 5.3). Also promote to stack kind.
             */
            args[i + outputArgumentOffset] = switch (signature.getParameterKind(i)) {
                case Boolean -> switch (argumentKind) {
                    case Boolean -> argument.asBoolean() ? 1 : 0;
                    default ->
                        throw new IllegalArgumentException("Bad argument kind at index " + i + ": expected Boolean, got " + argumentKind);
                };
                case Byte -> switch (argumentKind) {
                    case Byte -> argument.asInt();
                    default ->
                        throw new IllegalArgumentException("Bad argument kind at index " + i + ": expected Byte, got " + argumentKind);
                };
                case Char -> switch (argumentKind) {
                    case Char -> argument.asInt();
                    default ->
                        throw new IllegalArgumentException("Bad argument kind at index " + i + ": expected Char, got " + argumentKind);
                };
                case Short -> switch (argumentKind) {
                    case Short, Byte -> argument.asInt();
                    default ->
                        throw new IllegalArgumentException("Bad argument kind at index " + i + ": expected Short, got " + argumentKind);
                };
                case Int -> switch (argumentKind) {
                    case Int, Char, Short, Byte -> argument.asInt();
                    default ->
                        throw new IllegalArgumentException("Bad argument kind at index " + i + ": expected Int, got " + argumentKind);
                };
                case Long -> switch (argumentKind) {
                    case Long, Int, Char, Short, Byte -> argument.asLong();
                    default ->
                        throw new IllegalArgumentException("Bad argument kind at index " + i + ": expected Long, got " + argumentKind);
                };
                case Float -> switch (argumentKind) {
                    case Long, Int, Char, Short, Byte -> (float) argument.asLong();
                    case Float -> argument.asFloat();
                    default ->
                        throw new IllegalArgumentException("Bad argument kind at index " + i + ": expected Float, got " + argumentKind);
                };
                case Double -> switch (argumentKind) {
                    case Long, Int, Char, Short, Byte -> (double) argument.asLong();
                    case Float -> (double) argument.asFloat();
                    case Double -> argument.asDouble();
                    default ->
                        throw new IllegalArgumentException("Bad argument kind at index " + i + ": expected Double, got " + argumentKind);
                };
                case Object -> {
                    if (argument.isNull()) {
                        yield null;
                    }
                    if (!(argument instanceof EspressoExternalObjectConstant objectConstant)) {
                        throw new IllegalArgumentException(
                                        "Bad argument kind at index " + i + ": expected Object, got " + argumentKind + " wrapped in a " + argument.getClass().getName());
                    }
                    yield objectConstant.getValue();
                }
                default -> JVMCIError.shouldNotReachHere(signature.getParameterKind(i).toString());
            };
        }
        Value result;
        try {
            result = methodMirror.execute(args);
        } catch (PolyglotException e) {
            throw new InvocationException(new EspressoExternalObjectConstant(access, e.getGuestObject()), e);
        }
        if (isConstructor()) {
            return new EspressoExternalObjectConstant(access, (Value) args[0]);
        }
        JavaKind returnKind = signature.getReturnKind();
        if (returnKind == JavaKind.Void) {
            return null;
        }
        return EspressoExternalConstantReflectionProvider.asJavaConstant(result, returnKind, access);
    }
}
