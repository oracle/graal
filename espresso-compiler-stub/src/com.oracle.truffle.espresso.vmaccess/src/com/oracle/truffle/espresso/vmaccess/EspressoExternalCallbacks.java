/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.vmaccess.EspressoExternalVMAccess.sneakyThrow;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedJavaType;

import jdk.graal.compiler.vmaccess.InvocationException;
import jdk.graal.compiler.vmaccess.ModuleSupport;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

final class EspressoExternalCallbacks {
    private final Map<CallbackMethodMapKey, Value> callbackMethodMap = new ConcurrentHashMap<>();
    private final EspressoExternalVMAccess access;

    // Class <-> ResolvedJavaType
    private final MethodHandle classAsTypeMethodHandle;
    private final MethodHandle typeAsClassMethodHandle;
    // Method <-> ResolvedJavaMethod
    private final MethodHandle reflectMethodValueToJVMCIMethodHandle;
    private final MethodHandle methodAsReflectExcutableMethodHandle;
    // Field <-> ResolvedJavaField
    private final MethodHandle reflectFieldValueToJVMCIMethodHandle;
    private final MethodHandle fieldAsReflectFieldMethodHandle;

    // host String <-> guest String
    private final MethodHandle valueAsStringMethodHandle;
    private final MethodHandle toGuestStringMethodHandle;

    // JavaConstant <-> guest object
    private final MethodHandle valueAsHostObjectMethodHandle;
    private final MethodHandle objectConstantAsValueMethodHandle;

    // Value -> host primitive
    private final MethodHandle valueAsBooleanMethodHandle;
    private final MethodHandle valueAsByteMethodHandle;
    private final MethodHandle valueAsShortMethodHandle;
    private final MethodHandle valueAsCharMethodHandle;
    private final MethodHandle valueAsIntMethodHandle;
    private final MethodHandle valueAsLongMethodHandle;
    private final MethodHandle valueAsFloatMethodHandle;
    private final MethodHandle valueAsDoubleMethodHandle;

    // Value -> JavaConstant
    private final MethodHandle valueAsBooleanConstantMethodHandle;
    private final MethodHandle valueAsByteConstantMethodHandle;
    private final MethodHandle valueAsShortConstantMethodHandle;
    private final MethodHandle valueAsCharConstantMethodHandle;
    private final MethodHandle valueAsIntConstantMethodHandle;
    private final MethodHandle valueAsLongConstantMethodHandle;
    private final MethodHandle valueAsFloatConstantMethodHandle;
    private final MethodHandle valueAsDoubleConstantMethodHandle;
    private final MethodHandle valueAsObjectConstantMethodHandle;

    // primitive -> JavaConstant
    private final MethodHandle javaConstantAsBooleanMethodHandle;
    private final MethodHandle javaConstantAsByteMethodHandle;
    private final MethodHandle javaConstantAsShortMethodHandle;
    private final MethodHandle javaConstantAsCharMethodHandle;
    private final MethodHandle javaConstantAsIntMethodHandle;
    private final MethodHandle javaConstantAsLongMethodHandle;
    private final MethodHandle javaConstantAsFloatMethodHandle;
    private final MethodHandle javaConstantAsDoubleMethodHandle;

    private final MethodHandle rethrowGuestExceptionMethodHandle;

    EspressoExternalCallbacks(EspressoExternalVMAccess access) {
        this.access = access;

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            // (Value, EspressoExternalVMAccess) -> EspressoResolvedJavaType
            MethodHandle mh = lookup.findStatic(EspressoExternalConstantReflectionProvider.class, "classAsType",
                            MethodType.methodType(EspressoResolvedJavaType.class, Value.class, EspressoExternalVMAccess.class));
            // (Value) -> EspressoResolvedJavaType
            mh = MethodHandles.insertArguments(mh, 1, access);
            // (Value) -> ResolvedJavaType
            classAsTypeMethodHandle = mh.asType(MethodType.methodType(ResolvedJavaType.class, Value.class));

            // (Value, EspressoExternalVMAccess) -> EspressoExternalResolvedJavaMethod
            mh = lookup.findStatic(EspressoExternalConstantReflectionProvider.class, "methodAsJavaResolvedMethod",
                            MethodType.methodType(EspressoExternalResolvedJavaMethod.class, Value.class, EspressoExternalVMAccess.class));
            // (Value) -> EspressoExternalResolvedJavaMethod
            mh = MethodHandles.insertArguments(mh, 1, access);
            // (Value) -> ResolvedJavaMethod
            reflectMethodValueToJVMCIMethodHandle = mh.asType(MethodType.methodType(ResolvedJavaMethod.class, Value.class));

            // (Value, EspressoExternalVMAccess) -> EspressoExternalResolvedJavaField
            mh = lookup.findStatic(EspressoExternalConstantReflectionProvider.class, "fieldAsJavaResolvedField",
                            MethodType.methodType(EspressoExternalResolvedJavaField.class, Value.class, EspressoExternalVMAccess.class));
            // (Value) -> EspressoExternalResolvedJavaField
            mh = MethodHandles.insertArguments(mh, 1, access);
            // (Value) -> ResolvedJavaField
            reflectFieldValueToJVMCIMethodHandle = mh.asType(MethodType.methodType(ResolvedJavaField.class, Value.class));

            // (EspressoExternalResolvedJavaMethod) -> Value
            mh = lookup.findVirtual(EspressoExternalResolvedJavaMethod.class, "getReflectExecutableMirror", MethodType.methodType(Value.class));
            // (ResolvedJavaMethod) -> Value
            methodAsReflectExcutableMethodHandle = mh.asType(MethodType.methodType(Value.class, ResolvedJavaMethod.class));

            // (EspressoExternalResolvedJavaField) -> Value
            mh = lookup.findVirtual(EspressoExternalResolvedJavaField.class, "getReflectFieldMirror", MethodType.methodType(Value.class));
            // (ResolvedJavaField) -> Value
            fieldAsReflectFieldMethodHandle = mh.asType(MethodType.methodType(Value.class, ResolvedJavaField.class));

            // (Value) -> <primitive type>
            valueAsBooleanMethodHandle = lookup.findVirtual(Value.class, "asBoolean", MethodType.methodType(boolean.class));
            valueAsByteMethodHandle = lookup.findVirtual(Value.class, "asByte", MethodType.methodType(byte.class));
            valueAsShortMethodHandle = lookup.findVirtual(Value.class, "asShort", MethodType.methodType(short.class));
            valueAsIntMethodHandle = lookup.findVirtual(Value.class, "asInt", MethodType.methodType(int.class));
            valueAsLongMethodHandle = lookup.findVirtual(Value.class, "asLong", MethodType.methodType(long.class));
            valueAsFloatMethodHandle = lookup.findVirtual(Value.class, "asFloat", MethodType.methodType(float.class));
            valueAsDoubleMethodHandle = lookup.findVirtual(Value.class, "asDouble", MethodType.methodType(double.class));

            // (Value) -> String
            valueAsStringMethodHandle = lookup.findVirtual(Value.class, "asString", MethodType.methodType(String.class));

            // (String, int) -> char
            MethodHandle charAt = lookup.findVirtual(String.class, "charAt", MethodType.methodType(char.class, int.class));
            // (String) -> char
            MethodHandle charAt0 = MethodHandles.insertArguments(charAt, 1, 0);
            // (Value) -> char
            mh = MethodHandles.filterReturnValue(valueAsStringMethodHandle, charAt0);
            valueAsCharMethodHandle = mh;

            // (EspressoExternalConstantReflectionProvider, ResolvedJavaType) ->
            // EspressoExternalObjectConstant
            mh = lookup.findVirtual(EspressoExternalConstantReflectionProvider.class, "asJavaClass", MethodType.methodType(EspressoExternalObjectConstant.class, ResolvedJavaType.class));
            // (ResolvedJavaType) -> EspressoExternalObjectConstant
            mh = mh.bindTo(access.getProviders().getConstantReflection());
            // (ResolvedJavaType) -> Value
            mh = MethodHandles.filterReturnValue(mh, lookup.findVirtual(EspressoExternalObjectConstant.class, "getValue", MethodType.methodType(Value.class)));
            // TODO mh = mh.asType(MethodType.methodType(Value.class, ResolvedJavaType.class));
            typeAsClassMethodHandle = mh;

            // (Value) -> Object
            valueAsHostObjectMethodHandle = lookup.findVirtual(Value.class, "asHostObject", MethodType.methodType(Object.class));

            // (boolean) -> PrimitiveConstant
            mh = lookup.findStatic(JavaConstant.class, "forBoolean", MethodType.methodType(PrimitiveConstant.class, boolean.class));
            // (Value) -> JavaConstant
            mh = MethodHandles.filterReturnValue(valueAsBooleanMethodHandle, mh.asType(MethodType.methodType(JavaConstant.class, boolean.class)));
            valueAsBooleanConstantMethodHandle = mh;

            // (byte) -> PrimitiveConstant
            mh = lookup.findStatic(JavaConstant.class, "forByte", MethodType.methodType(PrimitiveConstant.class, byte.class));
            // (byte) -> JavaConstant
            mh = MethodHandles.filterReturnValue(valueAsByteMethodHandle, mh.asType(MethodType.methodType(JavaConstant.class, byte.class)));
            valueAsByteConstantMethodHandle = mh;

            // (short) -> PrimitiveConstant
            mh = lookup.findStatic(JavaConstant.class, "forShort", MethodType.methodType(PrimitiveConstant.class, short.class));
            // (short) -> JavaConstant
            mh = MethodHandles.filterReturnValue(valueAsShortMethodHandle, mh.asType(MethodType.methodType(JavaConstant.class, short.class)));
            valueAsShortConstantMethodHandle = mh;

            // (char) -> PrimitiveConstant
            mh = lookup.findStatic(JavaConstant.class, "forChar", MethodType.methodType(PrimitiveConstant.class, char.class));
            // (char) -> JavaConstant
            mh = MethodHandles.filterReturnValue(valueAsCharMethodHandle, mh.asType(MethodType.methodType(JavaConstant.class, char.class)));
            valueAsCharConstantMethodHandle = mh;

            // (int) -> PrimitiveConstant
            mh = lookup.findStatic(JavaConstant.class, "forInt", MethodType.methodType(PrimitiveConstant.class, int.class));
            // (int) -> JavaConstant
            mh = MethodHandles.filterReturnValue(valueAsIntMethodHandle, mh.asType(MethodType.methodType(JavaConstant.class, int.class)));
            valueAsIntConstantMethodHandle = mh;

            // (long) -> PrimitiveConstant
            mh = lookup.findStatic(JavaConstant.class, "forLong", MethodType.methodType(PrimitiveConstant.class, long.class));
            // (long) -> JavaConstant
            mh = MethodHandles.filterReturnValue(valueAsLongMethodHandle, mh.asType(MethodType.methodType(JavaConstant.class, long.class)));
            valueAsLongConstantMethodHandle = mh;

            // (float) -> PrimitiveConstant
            mh = lookup.findStatic(JavaConstant.class, "forFloat", MethodType.methodType(PrimitiveConstant.class, float.class));
            // (float) -> JavaConstant
            mh = MethodHandles.filterReturnValue(valueAsFloatMethodHandle, mh.asType(MethodType.methodType(JavaConstant.class, float.class)));
            valueAsFloatConstantMethodHandle = mh;

            // (double) -> PrimitiveConstant
            mh = lookup.findStatic(JavaConstant.class, "forDouble", MethodType.methodType(PrimitiveConstant.class, double.class));
            // (double) -> JavaConstant
            mh = MethodHandles.filterReturnValue(valueAsDoubleMethodHandle, mh.asType(MethodType.methodType(JavaConstant.class, double.class)));
            valueAsDoubleConstantMethodHandle = mh;

            // (EspressoExternalVMAccess, Value) -> EspressoExternalObjectConstant
            mh = lookup.findConstructor(EspressoExternalObjectConstant.class, MethodType.methodType(void.class, EspressoExternalVMAccess.class, Value.class));
            // (Value) -> EspressoExternalObjectConstant
            mh = mh.bindTo(access);
            // (Value) -> JavaConstant
            valueAsObjectConstantMethodHandle = mh.asType(MethodType.methodType(JavaConstant.class, Value.class));

            // (JavaConstant) -> <primitive type>
            javaConstantAsBooleanMethodHandle = lookup.findVirtual(JavaConstant.class, "asBoolean", MethodType.methodType(boolean.class));
            javaConstantAsIntMethodHandle = lookup.findVirtual(JavaConstant.class, "asInt", MethodType.methodType(int.class));
            javaConstantAsLongMethodHandle = lookup.findVirtual(JavaConstant.class, "asLong", MethodType.methodType(long.class));
            javaConstantAsFloatMethodHandle = lookup.findVirtual(JavaConstant.class, "asFloat", MethodType.methodType(float.class));
            javaConstantAsDoubleMethodHandle = lookup.findVirtual(JavaConstant.class, "asDouble", MethodType.methodType(double.class));

            // (JavaConstant) -> byte
            javaConstantAsByteMethodHandle = MethodHandles.explicitCastArguments(javaConstantAsIntMethodHandle, MethodType.methodType(byte.class, JavaConstant.class));
            // (JavaConstant) -> short
            javaConstantAsShortMethodHandle = MethodHandles.explicitCastArguments(javaConstantAsIntMethodHandle, MethodType.methodType(short.class, JavaConstant.class));
            // (JavaConstant) -> char
            javaConstantAsCharMethodHandle = MethodHandles.explicitCastArguments(javaConstantAsIntMethodHandle, MethodType.methodType(char.class, JavaConstant.class));

            // (EspressoExternalConstantReflectionProvider, String) -> Value
            mh = lookup.findVirtual(EspressoExternalConstantReflectionProvider.class, "valueForString", MethodType.methodType(Value.class, String.class));
            // (String) -> Value
            mh = mh.bindTo(access.getProviders().getConstantReflection());
            toGuestStringMethodHandle = mh;

            // (EspressoExternalObjectConstant) -> Value
            mh = lookup.findVirtual(EspressoExternalObjectConstant.class, "getValue", MethodType.methodType(Value.class));
            // (JavaConstant) -> Value
            mh = mh.asType(MethodType.methodType(Value.class, JavaConstant.class));
            objectConstantAsValueMethodHandle = mh;

            // (InvocationException) -> Object
            mh = lookup.findStatic(EspressoExternalCallbacks.class, "rethrowGuestException", MethodType.methodType(Object.class, InvocationException.class));
            rethrowGuestExceptionMethodHandle = mh;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    private static Object rethrowGuestException(InvocationException e) {
        JavaConstant exceptionObject = e.getExceptionObject();
        if (exceptionObject == null && e.getCause() != null) {
            // rethrow the original host exception
            throw sneakyThrow(e.getCause());
        }
        if ((exceptionObject instanceof EspressoExternalObjectConstant objectConstant)) {
            // rethrow the original guest exception
            throw objectConstant.getValue().throwException();
        }
        throw JVMCIError.shouldNotReachHere(e);
    }

    Value createCallback(Object hostTarget, EspressoExternalResolvedInstanceType espressoGuestType) {
        Value createProxyMirror = access.com_oracle_truffle_espresso_vmaccess_guest_GuestCallbackHandler_createProxy.getMirror();
        Value guestClass = espressoGuestType.getMetaObject().getMember("class");
        return createProxyMirror.execute(hostTarget, getGuestMethodMap(hostTarget.getClass(), espressoGuestType), guestClass);
    }

    private Value getGuestMethodMap(Class<?> hostClass, EspressoExternalResolvedInstanceType guestType) {
        CallbackMethodMapKey key = new CallbackMethodMapKey(hostClass, guestType);
        Value methodMap = callbackMethodMap.get(key);
        if (methodMap != null) {
            return methodMap;
        }
        methodMap = computeGuestMethodMap(hostClass, guestType);
        Value previous = callbackMethodMap.putIfAbsent(key, methodMap);
        return previous == null ? methodMap : previous;
    }

    private record CallbackMethodMapKey(Class<?> hostClass, EspressoExternalResolvedInstanceType guestClass) {
    }

    private Value computeGuestMethodMap(Class<?> hostClass, EspressoExternalResolvedInstanceType guestType) {
        Map<String, ProxyExecutable> map = computeMethodMap(hostClass, guestType);
        ProxyObject invocables = new ProxyObject() {
            @Override
            public Object getMember(String key) {
                return map.get(key);
            }

            @Override
            public Object getMemberKeys() {
                Object[] keys = map.keySet().toArray();
                return new ProxyArray() {
                    @Override
                    public Object get(long index) {
                        try {
                            return keys[Math.toIntExact(index)];
                        } catch (ArithmeticException e) {
                            throw new ArrayIndexOutOfBoundsException("Index should be an int");
                        }
                    }

                    @Override
                    public void set(long index, Value value) {
                        throw new UnsupportedOperationException("set() not supported.");
                    }

                    @Override
                    public long getSize() {
                        return keys.length;
                    }
                };
            }

            @Override
            public boolean hasMember(String key) {
                return map.containsKey(key);
            }

            @Override
            public void putMember(String key, Value value) {
                throw new UnsupportedOperationException("putMember() not supported.");
            }
        };
        Value computeMethodMapMirror = access.com_oracle_truffle_espresso_vmaccess_guest_GuestCallbackHandler_computeMethodMap.getMirror();
        Value guestClass = guestType.getMetaObject().getMember("class");
        return computeMethodMapMirror.execute(guestClass, invocables);
    }

    private Map<String, ProxyExecutable> computeMethodMap(Class<?> hostClass, EspressoExternalResolvedInstanceType guestType) {
        Map<String, ProxyExecutable> map = new HashMap<>();
        Set<EspressoExternalResolvedInstanceType> seen = new HashSet<>();
        addMethods(hostClass, guestType, map, seen);
        return Map.copyOf(map);
    }

    private void addMethods(Class<?> hostClass, EspressoExternalResolvedInstanceType guestType, Map<String, ProxyExecutable> map, Set<EspressoExternalResolvedInstanceType> seen) {
        assert guestType.isInterface();
        if (!seen.add(guestType)) {
            return;
        }
        for (ResolvedJavaMethod method : guestType.getDeclaredMethods()) {
            if (method.isStatic() || !method.isPublic()) {
                continue;
            }
            Method hostMethod = findHostMethod(hostClass, method);
            if (hostMethod == null) {
                continue;
            }
            // e.g., (MyHostType, JavaConstant, int) -> String
            MethodHandle mh = unreflect(hostMethod);
            MethodHandle[] filters = new MethodHandle[method.getSignature().getParameterCount(false) + 1];
            // e.g., (Value) -> MyHostType
            filters[0] = valueAsHostObjectMethodHandle.asType(MethodType.methodType(mh.type().parameterType(0), Value.class));
            for (int i = 0; i < method.getSignature().getParameterCount(false); i++) {
                // e.g., (Value) -> JavaConstant
                MethodHandle filter = getArgumentFilter(method.getSignature().getParameterType(i, null), hostMethod.getParameterTypes()[i]);
                filters[i + 1] = filter;
            }
            // e.g., (Value, Value, Value) -> String
            mh = MethodHandles.filterArguments(mh, 0, filters);
            // e.g., (String) -> Value
            MethodHandle filter = getReturnFilter(method.getSignature().getReturnType(null), hostMethod.getReturnType());
            if (filter != null) {
                // e.g., (Value, Value, Value) -> Value
                mh = MethodHandles.filterReturnValue(mh, filter);
            }
            // converts thrown InvocationException to thrown EspressoException
            mh = MethodHandles.catchException(mh, InvocationException.class, rethrowGuestExceptionMethodHandle.asType(MethodType.methodType(mh.type().returnType(), Throwable.class)));
            // e.g., (Object[]) -> Value
            mh = mh.asSpreader(Object[].class, mh.type().parameterCount());
            // (Object) -> Object
            mh = mh.asType(MethodType.methodType(Object.class, Object.class));
            map.put(getMethodSymbol(method), new CallbackExecutable(mh));
        }
        for (ResolvedJavaType superInterface : guestType.getInterfaces()) {
            addMethods(hostClass, (EspressoExternalResolvedInstanceType) superInterface, map, seen);
        }
    }

    /// See also
    /// `com.oracle.truffle.espresso.vmaccess.guest.GuestCallbackHandler#getMethodSymbol(java.lang.reflect.Method)`
    private static String getMethodSymbol(ResolvedJavaMethod method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getDeclaringClass().getName()).append("#").append(method.getName()).append('(');
        for (int i = 0; i < method.getSignature().getParameterCount(false); i++) {
            sb.append(method.getSignature().getParameterType(i, null).getName());
        }
        sb.append(')').append(method.getSignature().getReturnType(null).getName());
        return sb.toString();
    }

    private static final class CallbackExecutable implements ProxyExecutable {
        private final MethodHandle handle;

        private CallbackExecutable(MethodHandle handle) {
            this.handle = handle;
        }

        @Override
        public Object execute(Value... arguments) {
            try {
                return handle.invoke((Object) arguments);
            } catch (Throwable e) {
                throw sneakyThrow(e);
            }
        }
    }

    private static MethodHandle unreflect(Method hostMethod) {
        makeAccessible(hostMethod);
        try {
            return MethodHandles.lookup().unreflect(hostMethod);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("deprecation")
    private static <T extends AccessibleObject & Member> void makeAccessible(T accessibleMember) {
        try {
            if (!accessibleMember.isAccessible()) {
                accessibleMember.setAccessible(true);
            }
        } catch (InaccessibleObjectException e) {
            Class<?> declaringClass = accessibleMember.getDeclaringClass();
            ModuleSupport.addOpens(EspressoExternalCallbacks.class.getModule(), declaringClass.getModule(), declaringClass.getPackageName());
            accessibleMember.setAccessible(true);
        }
    }

    private static Method findHostMethod(Class<?> hostClass, ResolvedJavaMethod method) {
        Method hostMethod = null;
        for (Method hostMethodCandidate : hostClass.getMethods()) {
            if (!hostMethodCandidate.getName().equals(method.getName())) {
                continue;
            }
            if (!argumentsCompatible(method.getSignature(), hostMethodCandidate.getParameterTypes())) {
                continue;
            }
            if (!typesCompatible(method.getSignature().getReturnType(null), hostMethodCandidate.getReturnType())) {
                continue;
            }
            if (hostMethod == null) {
                hostMethod = hostMethodCandidate;
            } else {
                throw new RuntimeException("Unimplemented: most specific mapping: " + hostMethod + " vs. " + hostMethodCandidate);
            }
        }
        if (hostMethod == null && !method.isDefault()) {
            throw new IllegalArgumentException("Method compatible with " + method.format("%r %h.%n(%p)") + " not found in class " + hostClass.getName());
        }
        return hostMethod;
    }

    private static boolean argumentsCompatible(Signature guestSignature, Class<?>[] hostParameterTypes) {
        int parameterCount = guestSignature.getParameterCount(false);
        if (hostParameterTypes.length != parameterCount) {
            return false;
        }
        for (int i = 0; i < parameterCount; i++) {
            JavaType guestParameterType = guestSignature.getParameterType(i, null);
            Class<?> hostParameterType = hostParameterTypes[i];
            if (!typesCompatible(guestParameterType, hostParameterType)) {
                return false;
            }
        }
        return true;
    }

    private static boolean typesCompatible(JavaType guestType, Class<?> hostType) {
        if (hostType == JavaConstant.class) {
            return true;
        }
        if (guestType.getJavaKind().isPrimitive()) {
            return guestType.getJavaKind() == JavaKind.fromJavaClass(hostType);
        }
        if ("Ljava/lang/String;".equals(guestType.getName())) {
            return hostType == String.class;
        }
        if ("Ljava/lang/Class;".equals(guestType.getName())) {
            return hostType == ResolvedJavaType.class;
        }
        if ("Ljava/lang/reflect/Field;".equals(guestType.getName())) {
            return hostType == ResolvedJavaField.class;
        }
        if ("Ljava/lang/reflect/Executable;".equals(guestType.getName()) || "Ljava/lang/reflect/Method;".equals(guestType.getName()) || "Ljava/lang/reflect/Constructor;".equals(guestType.getName())) {
            return hostType == ResolvedJavaMethod.class;
        }
        return false;
    }

    // Host -> Guest
    private MethodHandle getReturnFilter(JavaType guestType, Class<?> hostType) {
        if (hostType == JavaConstant.class) {
            if (guestType.getJavaKind().isPrimitive()) {
                return switch (guestType.getJavaKind()) {
                    case Boolean -> javaConstantAsBooleanMethodHandle;
                    case Byte -> javaConstantAsByteMethodHandle;
                    case Short -> javaConstantAsShortMethodHandle;
                    case Char -> javaConstantAsCharMethodHandle;
                    case Int -> javaConstantAsIntMethodHandle;
                    case Long -> javaConstantAsLongMethodHandle;
                    case Float -> javaConstantAsFloatMethodHandle;
                    case Double -> javaConstantAsDoubleMethodHandle;
                    default -> throw new RuntimeException("unimplemented: " + guestType);
                };
            } else {
                return objectConstantAsValueMethodHandle;
            }
        }
        if (guestType.getJavaKind().isPrimitive()) {
            return null;
        }
        if ("Ljava/lang/String;".equals(guestType.getName())) {
            return toGuestStringMethodHandle;
        }
        if ("Ljava/lang/Class;".equals(guestType.getName())) {
            return typeAsClassMethodHandle;
        }
        if ("Ljava/lang/reflect/Field;".equals(guestType.getName())) {
            return fieldAsReflectFieldMethodHandle;
        }
        if ("Ljava/lang/reflect/Executable;".equals(guestType.getName()) || "Ljava/lang/reflect/Method;".equals(guestType.getName()) || "Ljava/lang/reflect/Constructor;".equals(guestType.getName())) {
            return methodAsReflectExcutableMethodHandle;
        }
        throw new RuntimeException("Should not reach here: " + hostType + " -> " + guestType);
    }

    // Guest -> Host
    private MethodHandle getArgumentFilter(JavaType guestType, Class<?> hostType) {
        if (hostType == JavaConstant.class) {
            if (guestType.getJavaKind().isPrimitive()) {
                return switch (guestType.getJavaKind()) {
                    case Boolean -> valueAsBooleanConstantMethodHandle;
                    case Byte -> valueAsByteConstantMethodHandle;
                    case Short -> valueAsShortConstantMethodHandle;
                    case Char -> valueAsCharConstantMethodHandle;
                    case Int -> valueAsIntConstantMethodHandle;
                    case Long -> valueAsLongConstantMethodHandle;
                    case Float -> valueAsFloatConstantMethodHandle;
                    case Double -> valueAsDoubleConstantMethodHandle;
                    default -> throw new RuntimeException("Should not reach here: " + guestType);
                };
            } else {
                return valueAsObjectConstantMethodHandle;
            }
        }
        if (guestType.getJavaKind().isPrimitive()) {
            return switch (guestType.getJavaKind()) {
                case Boolean -> valueAsBooleanMethodHandle;
                case Byte -> valueAsByteMethodHandle;
                case Short -> valueAsShortMethodHandle;
                case Char -> valueAsCharMethodHandle;
                case Int -> valueAsIntMethodHandle;
                case Long -> valueAsLongMethodHandle;
                case Float -> valueAsFloatMethodHandle;
                case Double -> valueAsDoubleMethodHandle;
                default -> throw new RuntimeException("Should not reach here: : " + guestType);
            };
        }
        if ("Ljava/lang/String;".equals(guestType.getName())) {
            return valueAsStringMethodHandle;
        }
        if ("Ljava/lang/Class;".equals(guestType.getName())) {
            return classAsTypeMethodHandle;
        }
        if ("Ljava/lang/reflect/Field;".equals(guestType.getName())) {
            return reflectFieldValueToJVMCIMethodHandle;
        }
        if ("Ljava/lang/reflect/Executable;".equals(guestType.getName()) || "Ljava/lang/reflect/Method;".equals(guestType.getName()) || "Ljava/lang/reflect/Constructor;".equals(guestType.getName())) {
            return reflectMethodValueToJVMCIMethodHandle;
        }
        throw new RuntimeException("Should not reach here: " + guestType + " -> " + hostType);
    }
}
