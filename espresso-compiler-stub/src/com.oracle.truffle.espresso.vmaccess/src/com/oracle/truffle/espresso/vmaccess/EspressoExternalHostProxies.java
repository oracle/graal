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

import java.lang.annotation.AnnotationTypeMismatchException;
import java.lang.annotation.IncompleteAnnotationException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueType;
import jdk.graal.compiler.annotation.ElementTypeMismatch;
import jdk.graal.compiler.annotation.EnumElement;
import jdk.graal.compiler.annotation.ErrorElement;
import jdk.graal.compiler.annotation.MissingType;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedJavaType;

import jdk.graal.compiler.vmaccess.AnnotationValueValidation;
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

/**
 * Creates guest-owned dynamic proxies that implement a guest interface by forwarding calls to a
 * host object. For each host class and guest interface pair, this class builds and caches a map of
 * guest method symbols to host {@link ProxyExecutable invocables}. Each invocable is a pre-adapted
 * {@link MethodHandle} pipeline that converts guest arguments represented as {@link Value} objects,
 * invokes the host method, converts its result back to a guest-owned value, and translates boundary
 * exceptions.
 * <p>
 * The actual JDK proxy and invocation handler are created in the guest by
 * {@code GuestHostProxyHandler}. Conversions therefore preserve value semantics across the isolated
 * host and guest heaps rather than sharing object identity. In particular, annotations are converted
 * structurally through {@link AnnotationValue} and materialized as JDK annotation proxies in the
 * receiving context.
 */
final class EspressoExternalHostProxies {
    private final Map<HostProxyMethodMapKey, Value> hostProxyMethodMap = new ConcurrentHashMap<>();
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
    private final MethodHandle guestAnnotationAsAnnotationValueMethodHandle;
    private final MethodHandle annotationValueAsGuestAnnotationMethodHandle;

    EspressoExternalHostProxies(EspressoExternalVMAccess access) {
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
            mh = lookup.findStatic(EspressoExternalHostProxies.class, "rethrowGuestException", MethodType.methodType(Object.class, InvocationException.class));
            rethrowGuestExceptionMethodHandle = mh;

            // (Value) -> AnnotationValue
            mh = lookup.findVirtual(EspressoExternalHostProxies.class, "guestAnnotationAsAnnotationValue", MethodType.methodType(AnnotationValue.class, Value.class));
            guestAnnotationAsAnnotationValueMethodHandle = mh.bindTo(this);

            // (ResolvedJavaType, AnnotationValue) -> Value
            mh = lookup.findVirtual(EspressoExternalHostProxies.class, "annotationValueAsGuestAnnotation", MethodType.methodType(Value.class, ResolvedJavaType.class, AnnotationValue.class));
            annotationValueAsGuestAnnotationMethodHandle = mh.bindTo(this);
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

    Value createHostProxy(Object hostTarget, EspressoExternalResolvedInstanceType espressoGuestType) {
        Value createProxyMirror = access.com_oracle_truffle_espresso_vmaccess_guest_GuestHostProxyHandler_createProxy.getMirror();
        Value guestClass = espressoGuestType.getMetaObject().getMember("class");
        return createProxyMirror.execute(hostTarget, getGuestMethodMap(hostTarget.getClass(), espressoGuestType), guestClass);
    }

    private Value getGuestMethodMap(Class<?> hostClass, EspressoExternalResolvedInstanceType guestType) {
        HostProxyMethodMapKey key = new HostProxyMethodMapKey(hostClass, guestType);
        Value methodMap = hostProxyMethodMap.get(key);
        if (methodMap != null) {
            return methodMap;
        }
        methodMap = computeGuestMethodMap(hostClass, guestType);
        Value previous = hostProxyMethodMap.putIfAbsent(key, methodMap);
        return previous == null ? methodMap : previous;
    }

    private record HostProxyMethodMapKey(Class<?> hostClass, EspressoExternalResolvedInstanceType guestClass) {
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
        Value computeMethodMapMirror = access.com_oracle_truffle_espresso_vmaccess_guest_GuestHostProxyHandler_computeMethodMap.getMirror();
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
                MethodHandle filter = getArgumentFilter(method.getSignature().getParameterType(i, null), method.getDeclaringClass(), hostMethod.getParameterTypes()[i]);
                filters[i + 1] = filter;
            }
            // e.g., (Value, Value, Value) -> String
            mh = MethodHandles.filterArguments(mh, 0, filters);
            // e.g., (String) -> Value
            MethodHandle filter = getReturnFilter(method.getSignature().getReturnType(null), method.getDeclaringClass(), hostMethod.getReturnType());
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
            map.put(getMethodSymbol(method), new HostProxyExecutable(mh));
        }
        for (ResolvedJavaType superInterface : guestType.getInterfaces()) {
            addMethods(hostClass, (EspressoExternalResolvedInstanceType) superInterface, map, seen);
        }
    }

    /// See also
    /// `com.oracle.truffle.espresso.vmaccess.guest.GuestHostProxyHandler#getMethodSymbol(java.lang.reflect.Method)`
    private static String getMethodSymbol(ResolvedJavaMethod method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getDeclaringClass().getName()).append("#").append(method.getName()).append('(');
        for (int i = 0; i < method.getSignature().getParameterCount(false); i++) {
            sb.append(method.getSignature().getParameterType(i, null).getName());
        }
        sb.append(')').append(method.getSignature().getReturnType(null).getName());
        return sb.toString();
    }

    private static final class HostProxyExecutable implements ProxyExecutable {
        private final MethodHandle handle;

        private HostProxyExecutable(MethodHandle handle) {
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
            ModuleSupport.addOpens(EspressoExternalHostProxies.class.getModule(), declaringClass.getModule(), declaringClass.getPackageName());
            accessibleMember.setAccessible(true);
        }
    }

    private Method findHostMethod(Class<?> hostClass, ResolvedJavaMethod method) {
        Method hostMethod = null;
        JavaType guestReturnType = method.getSignature().getReturnType(null);
        for (Method hostMethodCandidate : hostClass.getMethods()) {
            if (!hostMethodCandidate.getName().equals(method.getName())) {
                continue;
            }
            if (!argumentsCompatible(method, hostMethodCandidate.getParameterTypes())) {
                continue;
            }
            if (!typesCompatible(guestReturnType, method.getDeclaringClass(), hostMethodCandidate.getReturnType())) {
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

    private boolean argumentsCompatible(ResolvedJavaMethod method, Class<?>[] hostParameterTypes) {
        Signature guestSignature = method.getSignature();
        int parameterCount = guestSignature.getParameterCount(false);
        if (hostParameterTypes.length != parameterCount) {
            return false;
        }
        for (int i = 0; i < parameterCount; i++) {
            JavaType guestParameterType = guestSignature.getParameterType(i, null);
            Class<?> hostParameterType = hostParameterTypes[i];
            if (!typesCompatible(guestParameterType, method.getDeclaringClass(), hostParameterType)) {
                return false;
            }
        }
        return true;
    }

    private boolean typesCompatible(JavaType guestType, ResolvedJavaType accessingClass, Class<?> hostType) {
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
        if (hostType == AnnotationValue.class) {
            return resolveAnnotationType(guestType, accessingClass) != null;
        }
        return false;
    }

    /**
     * Resolves a guest signature type only when testing annotation compatibility. Other proxy
     * mappings can use the unresolved descriptor and should not load unrelated signature types,
     * but annotation assignability requires a {@link ResolvedJavaType}. Returns the resolved type
     * when it denotes {@link java.lang.annotation.Annotation} or a concrete annotation interface,
     * otherwise returns {@code null}.
     */
    private ResolvedJavaType resolveAnnotationType(JavaType guestType, ResolvedJavaType accessingClass) {
        ResolvedJavaType resolvedType = guestType.resolve(accessingClass);
        return access.java_lang_annotation_Annotation.isAssignableFrom(resolvedType) ? resolvedType : null;
    }

    // Host -> Guest
    private MethodHandle getReturnFilter(JavaType guestType, ResolvedJavaType accessingClass, Class<?> hostType) {
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
        if (hostType == AnnotationValue.class) {
            ResolvedJavaType annotationType = resolveAnnotationType(guestType, accessingClass);
            if (annotationType != null) {
                // Bind once while constructing the cached proxy method map; invocations pass only the returned AnnotationValue.
                return MethodHandles.insertArguments(annotationValueAsGuestAnnotationMethodHandle, 0, annotationType);
            }
        }
        throw new RuntimeException("Should not reach here: " + hostType + " -> " + guestType);
    }

    /**
     * Converts an {@link AnnotationValue} returned by a host method into a guest-owned annotation
     * compatible with the guest method's declared return type.
     */
    @SuppressWarnings("unused")
    private Value annotationValueAsGuestAnnotation(ResolvedJavaType expectedAnnotationType, AnnotationValue annotationValue) {
        if (annotationValue == null) {
            return null;
        }
        if (annotationValue.isError()) {
            throw annotationValue.getError();
        }
        ResolvedJavaType actualAnnotationType = annotationValue.getAnnotationType();
        validateAnnotationType(expectedAnnotationType, actualAnnotationType, "guest return type");
        JavaConstant annotationConstant = Objects.requireNonNull(createGuestAnnotation(annotationValue));
        if (!(annotationConstant instanceof EspressoExternalObjectConstant objectConstant)) {
            throw new IllegalArgumentException("Expected guest annotation object, got " + annotationConstant);
        }
        return objectConstant.getValue();
    }

    /**
     * Verifies that an annotation value names an annotation interface assignable to the declared
     * guest type.
     */
    private static void validateAnnotationType(ResolvedJavaType expectedAnnotationType, ResolvedJavaType actualAnnotationType, String expectedTypeDescription) {
        if (!actualAnnotationType.isAnnotation()) {
            throw new IllegalArgumentException("Annotation value type " + actualAnnotationType.toJavaName() + " is not an annotation interface");
        }
        if (!expectedAnnotationType.isAssignableFrom(actualAnnotationType)) {
            throw new IllegalArgumentException(
                            "Annotation value type " + actualAnnotationType.toJavaName() + " is not assignable to " + expectedTypeDescription + " " + expectedAnnotationType.toJavaName());
        }
    }

    /**
     * Materializes a guest-owned JDK annotation proxy from JVMCI annotation metadata. Explicit
     * elements are validated, defaults are supplied from the guest annotation type, absent required
     * members remain absent, and malformed members are represented by deferred guest exception
     * proxies.
     */
    private JavaConstant createGuestAnnotation(AnnotationValue annotationValue) {
        ResolvedJavaType actualAnnotationType = annotationValue.getAnnotationType();
        JavaConstant annotationClass = access.getProviders().getConstantReflection().asJavaClass(actualAnnotationType);
        JavaConstant elements = access.invoke(access.java_util_HashMap_init, null);
        Map<String, Object> annotationElements = annotationValue.getElements();
        AnnotationValueType annotationValueType = AnnotationValueType.getInstance(actualAnnotationType);
        AnnotationValueValidation.validateElements(annotationValue, annotationValueType);
        Map<String, Object> memberDefaults = annotationValueType.memberDefaults();
        for (Map.Entry<String, ResolvedJavaType> member : annotationValueType.memberTypes().entrySet()) {
            String memberNameString = member.getKey();
            Object memberElement = annotationElements.get(memberNameString);
            if (memberElement == null) {
                memberElement = memberDefaults.get(memberNameString);
                if (memberElement == null) {
                    continue;
                }
            }
            JavaConstant memberName = access.getProviders().getConstantReflection().forString(memberNameString);
            JavaConstant memberValue = toGuestAnnotationMemberValue(memberElement, member.getValue(), false, annotationClass, memberName);
            access.invoke(access.java_util_Map_put, elements, memberName, memberValue);
        }
        return access.invoke(access.com_oracle_truffle_espresso_vmaccess_guest_GuestAnnotationProxyBuilder_annotationForMap, null, annotationClass, elements);
    }

    /**
     * Converts one JVMCI annotation element representation into a guest-owned value suitable for a
     * JDK annotation proxy backing map.
     */
    private JavaConstant toGuestAnnotationMemberValue(Object value, JavaType expectedType, boolean arrayElement, JavaConstant annotationClass, JavaConstant memberName) {
        return switch (value) {
            case Boolean booleanValue -> primitiveAnnotationMemberValue(JavaConstant.forBoolean(booleanValue), arrayElement);
            case Byte byteValue -> primitiveAnnotationMemberValue(JavaConstant.forByte(byteValue), arrayElement);
            case Character charValue -> primitiveAnnotationMemberValue(JavaConstant.forChar(charValue), arrayElement);
            case Short shortValue -> primitiveAnnotationMemberValue(JavaConstant.forShort(shortValue), arrayElement);
            case Integer intValue -> primitiveAnnotationMemberValue(JavaConstant.forInt(intValue), arrayElement);
            case Long longValue -> primitiveAnnotationMemberValue(JavaConstant.forLong(longValue), arrayElement);
            case Float floatValue -> primitiveAnnotationMemberValue(JavaConstant.forFloat(floatValue), arrayElement);
            case Double doubleValue -> primitiveAnnotationMemberValue(JavaConstant.forDouble(doubleValue), arrayElement);
            case String stringValue -> access.getProviders().getConstantReflection().forString(stringValue);
            case ResolvedJavaType typeValue -> access.getProviders().getConstantReflection().asJavaClass(typeValue);
            case EnumElement enumElement -> guestEnumValue(enumElement, expectedType);
            case AnnotationValue nestedAnnotation -> guestAnnotationValue(nestedAnnotation, expectedType);
            case List<?> listValue -> guestArrayValue(listValue, expectedType, annotationClass, memberName);
            case MissingType missingType -> access.invoke(access.com_oracle_truffle_espresso_vmaccess_guest_GuestAnnotationProxyBuilder_missingTypeProxy, null,
                            access.getProviders().getConstantReflection().forString(missingType.getTypeName()));
            case ElementTypeMismatch typeMismatch -> access.invoke(access.com_oracle_truffle_espresso_vmaccess_guest_GuestAnnotationProxyBuilder_elementTypeMismatchProxy, null,
                            annotationClass, memberName, access.getProviders().getConstantReflection().forString(typeMismatch.getFoundType()));
            case ErrorElement errorElement -> throw new IllegalArgumentException(errorElement.toString());
            default -> throw new IllegalArgumentException("Unsupported annotation member value: " + value.getClass().getName());
        };
    }

    /**
     * Boxes a scalar primitive annotation member while leaving primitive array elements unboxed for
     * guest array construction.
     */
    private JavaConstant primitiveAnnotationMemberValue(JavaConstant primitive, boolean arrayElement) {
        return arrayElement ? primitive : access.getProviders().getConstantReflection().boxPrimitive(primitive);
    }

    /**
     * Resolves an enum element in the guest context, preserving a missing constant as a deferred
     * guest exception proxy.
     */
    private JavaConstant guestEnumValue(EnumElement enumElement, JavaType expectedType) {
        ResolvedJavaType enumType = enumElement.enumType;
        if (!(expectedType instanceof ResolvedJavaType expectedEnumType) || !expectedEnumType.equals(enumType)) {
            throw new IllegalArgumentException("Unexpected enum type: " + enumType.toJavaName());
        }
        JavaConstant enumClass = access.getProviders().getConstantReflection().asJavaClass(enumType);
        JavaConstant enumName = access.getProviders().getConstantReflection().forString(enumElement.name);
        return access.invoke(access.com_oracle_truffle_espresso_vmaccess_guest_GuestAnnotationProxyBuilder_enumValue, null, enumClass, enumName);
    }

    /**
     * Converts a nested annotation value after verifying its concrete type against the enclosing
     * member declaration.
     */
    private JavaConstant guestAnnotationValue(AnnotationValue annotationValue, JavaType expectedType) {
        if (!(expectedType instanceof ResolvedJavaType expectedAnnotationType)) {
            throw new IllegalArgumentException("Expected resolved annotation member type, got " + expectedType);
        }
        validateAnnotationType(expectedAnnotationType, annotationValue.getAnnotationType(), "annotation member type");
        return createGuestAnnotation(annotationValue);
    }

    /**
     * Constructs a typed guest annotation array, promoting any deferred element failure to the
     * member-level exception proxy required by the JDK annotation handler.
     */
    private JavaConstant guestArrayValue(List<?> values, JavaType expectedType, JavaConstant annotationClass, JavaConstant memberName) {
        if (!(expectedType instanceof ResolvedJavaType arrayType) || !arrayType.isArray()) {
            throw new IllegalArgumentException("Expected array annotation member type, got " + expectedType);
        }
        ResolvedJavaType componentType = arrayType.getComponentType();
        for (Object value : values) {
            if (value instanceof ErrorElement) {
                return toGuestAnnotationMemberValue(value, componentType, true, annotationClass, memberName);
            }
        }
        if (componentType.isEnum()) {
            JavaConstant enumClass = access.getProviders().getConstantReflection().asJavaClass(componentType);
            ResolvedJavaType stringType = access.getProviders().getMetaAccess().lookupJavaType(String.class);
            JavaConstant[] constantNames = new JavaConstant[values.size()];
            for (int i = 0; i < values.size(); i++) {
                if (!(values.get(i) instanceof EnumElement enumElement)) {
                    throw new IllegalArgumentException("Expected enum annotation array element, got " + values.get(i));
                }
                if (!componentType.equals(enumElement.enumType)) {
                    throw new IllegalArgumentException("Unexpected enum type: " + enumElement.enumType.toJavaName());
                }
                constantNames[i] = access.getProviders().getConstantReflection().forString(enumElement.name);
            }
            JavaConstant namesArray = access.asArrayConstant(stringType, constantNames);
            return access.invoke(access.com_oracle_truffle_espresso_vmaccess_guest_GuestAnnotationProxyBuilder_enumArray, null, enumClass, namesArray);
        }
        JavaConstant[] elements = new JavaConstant[values.size()];
        for (int i = 0; i < values.size(); i++) {
            elements[i] = toGuestAnnotationMemberValue(values.get(i), componentType, true, annotationClass, memberName);
        }
        return access.asArrayConstant(componentType, elements);
    }

    // Guest -> Host
    private MethodHandle getArgumentFilter(JavaType guestType, ResolvedJavaType accessingClass, Class<?> hostType) {
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
        if (hostType == AnnotationValue.class && resolveAnnotationType(guestType, accessingClass) != null) {
            return guestAnnotationAsAnnotationValueMethodHandle;
        }
        throw new RuntimeException("Should not reach here: " + guestType + " -> " + hostType);
    }

    /**
     * Converts a guest annotation argument into its JVMCI metadata representation. JDK proxy
     * backing values are inspected without eagerly invoking malformed members.
     */
    @SuppressWarnings("unused")
    private AnnotationValue guestAnnotationAsAnnotationValue(Value annotation) {
        if (annotation == null || annotation.isNull()) {
            return null;
        }
        ResolvedJavaType actualAnnotationType = EspressoExternalConstantReflectionProvider.classAsType(annotation.invokeMember("annotationType"), access);
        Map<String, Object> elements = new HashMap<>();
        AnnotationValueType annotationValueType = AnnotationValueType.getInstance(actualAnnotationType);
        for (Map.Entry<String, ResolvedJavaType> member : annotationValueType.memberTypes().entrySet()) {
            String memberName = member.getKey();
            ResolvedJavaType returnType = member.getValue();
            JavaConstant guestMemberValue;
            try {
                guestMemberValue = access.invoke(access.com_oracle_truffle_espresso_vmaccess_guest_GuestAnnotationProxyBuilder_annotationMemberValue, null,
                                new EspressoExternalObjectConstant(access, annotation), access.getProviders().getConstantReflection().forString(memberName));
            } catch (InvocationException e) {
                JavaConstant exceptionObject = e.getExceptionObject();
                if (!(exceptionObject instanceof EspressoExternalObjectConstant objectConstant) ||
                                !recordGuestAnnotationFailure(elements, memberName, objectConstant.getValue(), returnType)) {
                    throw e;
                }
                continue;
            }
            Value memberValue;
            if (guestMemberValue.isNull()) {
                /* Guest module encapsulation prevented reflection; Espresso interop can invoke public members directly. */
                try {
                    memberValue = annotation.invokeMember(memberName);
                } catch (PolyglotException e) {
                    Value guestException = e.isGuestException() ? e.getGuestObject() : null;
                    if (!recordGuestAnnotationFailure(elements, memberName, guestException, returnType)) {
                        throw e;
                    }
                    continue;
                }
            } else if (guestMemberValue instanceof EspressoExternalObjectConstant objectConstant) {
                memberValue = objectConstant.getValue();
            } else {
                throw new IllegalArgumentException("Expected guest annotation member value object, got " + guestMemberValue);
            }
            elements.put(memberName, guestAnnotationMemberValueAsHost(memberValue, returnType));
        }
        return new AnnotationValue(actualAnnotationType, elements);
    }

    /**
     * Recognizes a standard guest annotation failure and records its JVMCI representation. An
     * incomplete annotation is represented by leaving the member absent. Returns {@code false} for
     * exceptions unrelated to deferred annotation state so the caller can rethrow the original
     * boundary exception.
     */
    private boolean recordGuestAnnotationFailure(Map<String, Object> elements, String memberName, Value guestException, JavaType expectedType) {
        if (guestException == null || guestException.isNull()) {
            return false;
        }
        Value metaObject = guestException.getMetaObject();
        if (metaObject == null || metaObject.isNull()) {
            return false;
        }
        if (isMetaSubtypeOf(metaObject, IncompleteAnnotationException.class.getName())) {
            return true;
        }
        if (isMetaSubtypeOf(metaObject, TypeNotPresentException.class.getName())) {
            String typeName = guestException.invokeMember("typeName").asString();
            elements.put(memberName, new MissingType(typeName, new ClassNotFoundException(typeName)));
            return true;
        }
        if (isMetaSubtypeOf(metaObject, AnnotationTypeMismatchException.class.getName())) {
            elements.put(memberName, new ElementTypeMismatch(guestException.invokeMember("foundType").asString()));
            return true;
        }
        if (isMetaSubtypeOf(metaObject, EnumConstantNotPresentException.class.getName())) {
            ResolvedJavaType enumType = EspressoExternalConstantReflectionProvider.classAsType(guestException.invokeMember("enumType"), access);
            EnumElement enumElement = new EnumElement(enumType, guestException.invokeMember("constantName").asString());
            elements.put(memberName, expectedType instanceof ResolvedJavaType resolvedExpectedType && resolvedExpectedType.isArray() ? List.of(enumElement) : enumElement);
            return true;
        }
        return false;
    }

    /**
     * Recursively determines whether an Espresso meta-object represents the named guest exception
     * type or one of its subclasses.
     */
    private static boolean isMetaSubtypeOf(Value metaObject, String expectedType) {
        if (expectedType.equals(metaObject.getMetaQualifiedName())) {
            return true;
        }
        if (!metaObject.hasMetaParents()) {
            return false;
        }
        Value parents = metaObject.getMetaParents();
        for (long i = 0; i < parents.getArraySize(); i++) {
            if (isMetaSubtypeOf(parents.getArrayElement(i), expectedType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts one guest annotation member value or deferred-error descriptor into the
     * representation used by {@link AnnotationValue}.
     */
    private Object guestAnnotationMemberValueAsHost(Value value, JavaType expectedType) {
        if (expectedType.getJavaKind().isPrimitive()) {
            return switch (expectedType.getJavaKind()) {
                case Boolean -> value.asBoolean();
                case Byte -> value.asByte();
                case Short -> value.asShort();
                case Char -> value.asString().charAt(0);
                case Int -> value.asInt();
                case Long -> value.asLong();
                case Float -> value.asFloat();
                case Double -> value.asDouble();
                default -> throw new RuntimeException("Should not reach here: " + expectedType);
            };
        }
        if ("Ljava/lang/String;".equals(expectedType.getName())) {
            return value.asString();
        }
        if ("Ljava/lang/Class;".equals(expectedType.getName())) {
            return EspressoExternalConstantReflectionProvider.classAsType(value, access);
        }
        if (!(expectedType instanceof ResolvedJavaType resolvedExpectedType)) {
            throw new IllegalArgumentException("Expected resolved annotation member type, got " + expectedType);
        }
        if (resolvedExpectedType.isArray()) {
            return guestAnnotationArrayMemberValueAsHost(value, resolvedExpectedType);
        }
        if (resolvedExpectedType.isEnum()) {
            return new EnumElement(resolvedExpectedType, value.invokeMember("name").asString());
        }
        if (access.java_lang_annotation_Annotation.isAssignableFrom(resolvedExpectedType)) {
            return guestAnnotationAsAnnotationValue(value);
        }
        throw new IllegalArgumentException("Unsupported annotation member type: " + expectedType);
    }

    /**
     * Recursively converts a typed guest annotation array to an immutable list of JVMCI element
     * representations.
     */
    private Object guestAnnotationArrayMemberValueAsHost(Value value, ResolvedJavaType arrayType) {
        ResolvedJavaType componentType = arrayType.getComponentType();
        int length = Math.toIntExact(value.getArraySize());
        List<Object> elements = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            elements.add(guestAnnotationMemberValueAsHost(value.getArrayElement(i), componentType));
        }
        return List.copyOf(elements);
    }

}
