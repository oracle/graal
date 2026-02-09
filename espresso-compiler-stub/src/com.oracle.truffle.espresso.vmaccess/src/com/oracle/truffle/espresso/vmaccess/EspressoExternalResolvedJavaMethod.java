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

import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedInstanceType;
import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedJavaMethod;
import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoSignature;

import jdk.graal.compiler.vmaccess.InvocationException;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;

final class EspressoExternalResolvedJavaMethod extends AbstractEspressoResolvedJavaMethod implements EspressoExternalVMAccess.Element {
    static final EspressoExternalResolvedJavaMethod[] EMPTY_ARRAY = new EspressoExternalResolvedJavaMethod[0];
    private static final ExceptionHandler[] NO_HANDLERS = new ExceptionHandler[0];
    private static final LocalVariableTable EMPTY_LVT = new LocalVariableTable(new Local[0]);

    /**
     * The guest {@code com.oracle.truffle.espresso.impl.Method} value associated with this method.
     */
    private final Value vmMethodMirror;

    /**
     * Value of {@code com.oracle.truffle.espresso.impl.Method.rawFlags}.
     */
    private final int flags;

    /**
     * A guest {@link java.lang.reflect.Executable} value associated with this method.
     */
    private Value reflectExecutableMirror;

    EspressoExternalResolvedJavaMethod(Value vmMethodMirror, EspressoExternalVMAccess access) {
        this(new EspressoExternalResolvedInstanceType(access, vmMethodMirror.getMember("holder")), vmMethodMirror, null);
    }

    EspressoExternalResolvedJavaMethod(EspressoExternalResolvedInstanceType holder, Value vmMethodMirror, Value reflectExecutableMirror) {
        super(holder, vmMethodMirror.getMember("hasPoison").asBoolean());
        this.vmMethodMirror = vmMethodMirror;
        this.reflectExecutableMirror = reflectExecutableMirror;
        this.flags = vmMethodMirror.getMember("flags").asInt();
    }

    Value getMirror() {
        return vmMethodMirror;
    }

    private EspressoExternalVMAccess getAccess() {
        return ((EspressoExternalResolvedInstanceType) getDeclaringClass()).getAccess();
    }

    @Override
    protected byte[] getCode0() {
        Value value = vmMethodMirror.getMember("code");
        int size = Math.toIntExact(value.getBufferSize());
        byte[] buf = new byte[size];
        value.readBuffer(0, buf, 0, size);
        return buf;
    }

    @Override
    protected int getCodeSize0() {
        return vmMethodMirror.getMember("codeSize").asInt();
    }

    @Override
    protected String getName0() {
        return vmMethodMirror.getMember("name").asString();
    }

    @Override
    protected AbstractEspressoSignature getSignature0() {
        return new EspressoExternalSignature(getAccess(), vmMethodMirror.getMember("rawSignature").asString());
    }

    @Override
    protected boolean isForceInline() {
        return false;
    }

    @Override
    protected int getVtableIndexForInterfaceMethod(AbstractEspressoResolvedInstanceType resolved) {
        return getAccess().invokeJVMCIHelper("getVtableIndexForInterfaceMethod", getMirror(), ((EspressoExternalResolvedInstanceType) resolved).getMetaObject()).asInt();
    }

    @Override
    protected int getVtableIndex() {
        return vmMethodMirror.getMember("vtableIndex").asInt();
    }

    @Override
    public int getMaxLocals() {
        return vmMethodMirror.getMember("maxLocals").asInt();
    }

    @Override
    public int getMaxStackSize() {
        return vmMethodMirror.getMember("maxStackSize").asInt();
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        Value handlers = vmMethodMirror.getMember("exceptionHandlers");
        if (handlers.isNull()) {
            return NO_HANDLERS;
        }
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
        return vmMethodMirror.getMember("neverInline").asBoolean();
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        Value rawData = vmMethodMirror.getMember("lineNumberTable");
        if (rawData.isNull()) {
            return null;
        }
        int size = Math.toIntExact(rawData.getArraySize() / 2);
        int[] lineNumbers = new int[size];
        int[] bcis = new int[size];
        for (int i = 0; i < size; i++) {
            lineNumbers[i] = rawData.getArrayElement(i * 2L).asInt();
            bcis[i] = rawData.getArrayElement(i * 2L + 1L).asInt();
        }
        return new LineNumberTable(lineNumbers, bcis);
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        Value table = vmMethodMirror.getMember("localVariableTable");
        if (table.isNull()) {
            return EMPTY_LVT;
        }
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
        Value table = vmMethodMirror.getMember("parameters");
        if (table.isNull()) {
            return NO_PARAMETERS;
        }
        int size = Math.toIntExact(table.getArraySize());
        Parameter[] result = new Parameter[size];
        for (int i = 0; i < size; i++) {
            Value parameter = table.getArrayElement(i);
            String name = parameter.getMember("name").asString();
            int modifiers = parameter.getMember("modifiers").asInt();
            result[i] = new Parameter(name, modifiers, this, i);
        }
        return result;
    }

    @Override
    public boolean isLeafMethod() {
        return vmMethodMirror.getMember("leafMethod").asBoolean();
    }

    @Override
    protected byte[] getRawAnnotationBytes(int category) {
        return getAccess().getRawAnnotationBytes(vmMethodMirror, category);
    }

    @Override
    protected boolean hasAnnotations() {
        return getAccess().hasAnnotations(vmMethodMirror);
    }

    @Override
    protected boolean equals0(AbstractEspressoResolvedJavaMethod that) {
        if (that instanceof EspressoExternalResolvedJavaMethod espressoMethod) {
            return this.vmMethodMirror.equals(espressoMethod.vmMethodMirror);
        }
        return false;
    }

    @Override
    protected int hashCode0() {
        return vmMethodMirror.hashCode();
    }

    /// Adapts a call to a guest Espresso method, converting between [JavaConstant] values and
    /// the polyglot [Value]s, and back. The interop implementation in the Espresso is
    /// `com.oracle.truffle.espresso.impl.Method.Execute#invoke`.
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
            result = vmMethodMirror.execute(args);
        } catch (PolyglotException e) {
            Value guestException = e.getGuestObject();
            if (guestException == null || guestException.isNull()) {
                throw e;
            }
            throw new InvocationException(new EspressoExternalObjectConstant(access, guestException), e);
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

    /**
     * Gets a guest {@link java.lang.reflect.Executable} value associated with this method, creating
     * it first if necessary.
     */
    Value getReflectExecutableMirror() {
        Value value = reflectExecutableMirror;
        if (value == null) {
            value = getAccess().invokeJVMCIHelper("getReflectExecutable", vmMethodMirror);
            reflectExecutableMirror = value;
        }
        return value;
    }
}
