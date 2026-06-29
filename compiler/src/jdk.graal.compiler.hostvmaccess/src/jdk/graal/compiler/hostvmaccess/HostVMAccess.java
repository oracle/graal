/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hostvmaccess;

import static jdk.graal.compiler.hostvmaccess.HostProxyHandler.computeMethodMap;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.api.runtime.GraalJVMCICompiler;
import jdk.graal.compiler.api.runtime.GraalRuntime;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.hostvmaccess.HostProxyHandler.HostProxyHandlerMethodHandles;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.graal.compiler.vmaccess.InvocationException;
import jdk.graal.compiler.vmaccess.ModuleSupport;
import jdk.graal.compiler.vmaccess.ResolvedJavaModule;
import jdk.graal.compiler.vmaccess.ResolvedJavaModuleLayer;
import jdk.graal.compiler.vmaccess.ResolvedJavaPackage;
import jdk.graal.compiler.vmaccess.VMAccess;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCIRuntime;

/**
 * An implementation of {@link VMAccess} that reflects on the JVM it's currently running inside.
 * There is no isolation between the current JVM and the JVM being accessed through this
 * implementation, it is the same JVM.
 * <p>
 * Note that each instance of this VM access creates a dedicated class loader and module layer that
 * it uses to implement {@link VMAccess#lookupAppClassLoaderType} instead of using the host JVM's
 * {@linkplain ClassLoader#getSystemClassLoader system/app classloader}.
 */
final class HostVMAccess implements VMAccess {
    private final ClassLoader appClassLoader;
    private final Providers providers;
    private final Module hostImplModule;
    private final Map<HostProxyMethodMapKey, Map<Method, MethodHandle>> hostProxyMethodMap = new ConcurrentHashMap<>();
    private final HostProxyHandlerMethodHandles hostProxyMethodHandles;

    HostVMAccess(ClassLoader appClassLoader) {
        this.appClassLoader = appClassLoader;
        JVMCIRuntime runtime = JVMCI.getRuntime();
        this.hostImplModule = runtime.getClass().getModule();
        GraalRuntime graalRuntime = ((GraalJVMCICompiler) runtime.getCompiler()).getGraalRuntime();
        Backend hostBackend = graalRuntime.getCapability(RuntimeProvider.class).getHostBackend();
        providers = hostBackend.getProviders();
        hostProxyMethodHandles = getHostProxyMethodHandles(providers);
    }

    @Override
    public boolean isFullyIsolated() {
        return false;
    }

    @Override
    public Providers getProviders() {
        return providers;
    }

    @Override
    public boolean owns(ResolvedJavaType value) {
        return value.getClass().getModule() == hostImplModule;
    }

    @Override
    public boolean owns(ResolvedJavaMethod value) {
        return value.getClass().getModule() == hostImplModule;
    }

    @Override
    public boolean owns(ResolvedJavaField value) {
        return value.getClass().getModule() == hostImplModule;
    }

    @Override
    public JavaConstant invoke(ResolvedJavaMethod method, JavaConstant receiver, JavaConstant... arguments) {
        SnippetReflectionProvider snippetReflection = providers.getSnippetReflection();
        Executable executable = snippetReflection.originalMethod(method);
        makeAccessible(executable);
        boolean isConstructor = executable instanceof Constructor;
        Class<?>[] parameterTypes = executable.getParameterTypes();
        if (Modifier.isStatic(executable.getModifiers()) || isConstructor) {
            if (receiver != null) {
                throw new IllegalArgumentException("For static methods or constructor, the receiver argument must be null");
            }
        } else if (receiver == null) {
            throw new NullPointerException("For instance methods, the receiver argument must not be null");
        } else if (receiver.isNull()) {
            throw new IllegalArgumentException("For instance methods, the receiver argument must not represent a null constant");
        }
        if (parameterTypes.length != arguments.length) {
            throw new IllegalArgumentException("Wrong number of arguments: expected " + parameterTypes.length + " but got " + arguments.length);
        }
        Signature signature = method.getSignature();
        Object[] unboxedArguments = new Object[parameterTypes.length];
        for (int i = 0; i < unboxedArguments.length; i++) {
            JavaKind parameterKind = signature.getParameterKind(i);
            JavaConstant argument = arguments[i];
            if (parameterKind.isObject()) {
                if (argument.isNull()) {
                    unboxedArguments[i] = null;
                } else {
                    unboxedArguments[i] = snippetReflection.asObject(parameterTypes[i], argument);
                    if (unboxedArguments[i] == null) {
                        throw new IllegalArgumentException(
                                        "Illegal argument type: arguments[" + i + "] of type " + providers.getMetaAccess().lookupJavaType(arguments[i]).toClassName() +
                                                        " could not be converted to a " + parameterTypes[i]);
                    }
                }
            } else {
                assert parameterKind.isPrimitive();
                unboxedArguments[i] = argument.asBoxedPrimitive();
            }
        }
        try {
            if (isConstructor) {
                Constructor<?> constructor = (Constructor<?>) executable;
                return snippetReflection.forObject(constructor.newInstance(unboxedArguments));
            } else {
                Method reflectionMethod = (Method) executable;
                Object unboxedReceiver;
                if (Modifier.isStatic(reflectionMethod.getModifiers())) {
                    unboxedReceiver = null;
                } else {
                    if (receiver.isNull()) {
                        unboxedReceiver = null;
                    } else {
                        unboxedReceiver = snippetReflection.asObject(reflectionMethod.getDeclaringClass(), receiver);
                        if (unboxedReceiver == null) {
                            throw new IllegalArgumentException(
                                            "Illegal argument type: receiver of type " + providers.getMetaAccess().lookupJavaType(receiver).toClassName() +
                                                            " could not be converted to a " + reflectionMethod.getDeclaringClass());
                        }
                    }
                }
                JavaKind returnKind = method.getSignature().getReturnKind();
                Object result = reflectionMethod.invoke(unboxedReceiver, unboxedArguments);
                if (returnKind == JavaKind.Void) {
                    return null;
                }
                if (returnKind.isObject()) {
                    return snippetReflection.forObject(result);
                } else {
                    return snippetReflection.forBoxed(returnKind, result);
                }
            }
        } catch (InstantiationException e) {
            throw new IllegalArgumentException(e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof HostProxyExceptionImpl) {
                throw new InvocationException(cause.getCause());
            }
            throw new InvocationException(snippetReflection.forObject(cause), cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Should not reach here", e);
        }
    }

    @Override
    public void writeField(ResolvedJavaField field, JavaConstant receiver, JavaConstant value) {
        SnippetReflectionProvider snippetReflection = providers.getSnippetReflection();
        Field reflectionField = snippetReflection.originalField(field);
        makeAccessible(reflectionField);
        var fieldKind = field.getJavaKind();

        if (Modifier.isStatic(reflectionField.getModifiers())) {
            if (receiver != null) {
                throw new IllegalArgumentException("For static fields, the receiver argument must be null");
            }
        } else if (receiver == null) {
            throw new NullPointerException("For instance fields, the receiver argument must not be null");
        } else if (receiver.isNull()) {
            throw new IllegalArgumentException("For instance fields, the receiver argument must not represent a null constant");
        }

        Object unboxedValue;
        if (fieldKind.isObject()) {
            unboxedValue = snippetReflection.asObject(reflectionField.getType(), value);
        } else {
            assert fieldKind.isPrimitive();
            if (fieldKind != value.getJavaKind()) {
                throw new IllegalArgumentException("Expected value kind " + fieldKind + " but got " + value.getJavaKind());
            }
            unboxedValue = value.asBoxedPrimitive();
        }

        Object unboxedReceiver;
        if (Modifier.isStatic(reflectionField.getModifiers())) {
            unboxedReceiver = null;
        } else {
            unboxedReceiver = snippetReflection.asObject(reflectionField.getDeclaringClass(), receiver);
        }

        try {
            reflectionField.set(unboxedReceiver, unboxedValue);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("deprecation")
    static <T extends AccessibleObject & Member> void makeAccessible(T accessibleMember) {
        try {
            if (!accessibleMember.isAccessible()) {
                accessibleMember.setAccessible(true);
            }
        } catch (InaccessibleObjectException e) {
            Class<?> declaringClass = accessibleMember.getDeclaringClass();
            ModuleSupport.addOpens(HostVMAccess.class.getModule(), declaringClass.getModule(), declaringClass.getPackageName());
            accessibleMember.setAccessible(true);
        }
    }

    @Override
    public JavaConstant asArrayConstant(ResolvedJavaType componentType, JavaConstant... elements) {
        SnippetReflectionProvider snippetReflection = providers.getSnippetReflection();
        Class<?> componentClass = snippetReflection.originalClass(componentType);
        if (componentClass == null) {
            throw new IllegalArgumentException("Could not obtain the original class for " + componentType);
        }
        Object array = Array.newInstance(componentClass, elements.length);
        for (int i = 0; i < elements.length; i++) {
            doWriteArrayElement(array, componentType, i, elements[i]);
        }
        return snippetReflection.forObject(array);
    }

    /**
     * Host mode materializes the primitive array with reflection and then wraps it as a
     * {@link JavaConstant} through {@link SnippetReflectionProvider#forObject(Object)}.
     */
    @Override
    public JavaConstant createPrimitiveArray(JavaKind kind, int length) {
        if (kind == null || !kind.isPrimitive() || kind == JavaKind.Void) {
            throw new IllegalArgumentException("Expected a non-void primitive kind, got " + kind);
        }
        Object array = Array.newInstance(kind.toJavaClass(), length);
        return providers.getSnippetReflection().forObject(array);
    }

    @Override
    public JavaConstant clonePrimitiveArray(JavaConstant primitiveArray) {
        ResolvedJavaType arrayType = getProviders().getMetaAccess().lookupJavaType(primitiveArray);
        if (arrayType == null || !arrayType.isArray() || !arrayType.getComponentType().isPrimitive()) {
            throw new IllegalArgumentException("Expected a primitive array constant, got " + primitiveArray);
        }
        Object source = providers.getSnippetReflection().asObject(Object.class, primitiveArray);
        if (source == null) {
            throw new IllegalArgumentException("Could not unwrap a primitive array constant: " + primitiveArray);
        }
        /*
         * Keep cloning explicit by primitive array kind. This avoids reflective clone access and
         * preserves exact array runtime type through each typed clone() call.
         */
        Object copy = switch (source) {
            case boolean[] array -> array.clone();
            case byte[] array -> array.clone();
            case short[] array -> array.clone();
            case char[] array -> array.clone();
            case int[] array -> array.clone();
            case long[] array -> array.clone();
            case float[] array -> array.clone();
            case double[] array -> array.clone();
            default -> throw new IllegalArgumentException("Expected a primitive array constant, got " + primitiveArray);
        };
        return providers.getSnippetReflection().forObject(copy);
    }

    @Override
    public void copyArray(JavaConstant src, int srcPos, JavaConstant dest, int destPos, int length) {
        Object srcArray = providers.getSnippetReflection().asObject(Object.class, src);
        if (srcArray == null || !srcArray.getClass().isArray()) {
            throw new IllegalArgumentException("Expected an array constant for src, got " + src);
        }
        Object destArray = providers.getSnippetReflection().asObject(Object.class, dest);
        if (destArray == null || !destArray.getClass().isArray()) {
            throw new IllegalArgumentException("Expected an array constant for dest, got " + dest);
        }
        System.arraycopy(srcArray, srcPos, destArray, destPos, length);
    }

    private void doWriteArrayElement(Object array, ResolvedJavaType componentType, int index, JavaConstant element) {
        Object unwrappedValue;
        if (componentType.isPrimitive()) {
            if (componentType.getJavaKind() != element.getJavaKind()) {
                throw new IllegalArgumentException("Element " + element + " should be a " + componentType.getJavaKind() + ", got " + element.getJavaKind());
            }
            unwrappedValue = element.asBoxedPrimitive();
        } else {
            if (!element.getJavaKind().isObject()) {
                throw new IllegalArgumentException("Element " + element + " should be an object, got " + componentType);
            }
            unwrappedValue = providers.getSnippetReflection().asObject(Object.class, element);
        }
        Array.set(array, index, unwrappedValue);
    }

    @Override
    public void writeArrayElement(JavaConstant array, int index, JavaConstant element) {
        ResolvedJavaType arrayType = getProviders().getMetaAccess().lookupJavaType(array);
        if (arrayType == null || !arrayType.isArray()) {
            throw new IllegalArgumentException("Expected an array constant, got " + array);
        }
        Object asObject = providers.getSnippetReflection().asObject(Object.class, array);
        if (asObject == null) {
            throw new IllegalArgumentException("Could not unwrap array: " + array);
        }
        doWriteArrayElement(asObject, arrayType.getComponentType(), index, element);
    }

    @Override
    public ResolvedJavaMethod asResolvedJavaMethod(Constant constant) {
        SnippetReflectionProvider snippetReflection = providers.getSnippetReflection();
        Executable executable = snippetReflection.asObject(Executable.class, (JavaConstant) constant);
        if (executable != null) {
            return providers.getMetaAccess().lookupJavaMethod(executable);
        }
        return null;
    }

    @Override
    public JavaConstant asFieldConstant(ResolvedJavaField field) {
        if (field.isInternal()) {
            return null;
        }
        SnippetReflectionProvider snippetReflection = providers.getSnippetReflection();
        return snippetReflection.forObject(snippetReflection.originalField(field));
    }

    @Override
    public JavaConstant asExecutableConstant(ResolvedJavaMethod method) {
        if (method.isClassInitializer()) {
            return null;
        }
        SnippetReflectionProvider snippetReflection = providers.getSnippetReflection();
        return snippetReflection.forObject(snippetReflection.originalMethod(method));
    }

    @Override
    public ResolvedJavaField asResolvedJavaField(Constant constant) {
        SnippetReflectionProvider snippetReflection = providers.getSnippetReflection();
        Field field = snippetReflection.asObject(Field.class, (JavaConstant) constant);
        if (field != null) {
            return providers.getMetaAccess().lookupJavaField(field);
        }
        return null;
    }

    @Override
    public ResolvedJavaType lookupBootClassLoaderType(String name) {
        return lookupType(name, null);
    }

    @Override
    public ResolvedJavaModule getModule(ResolvedJavaType type) {
        return new HostVMResolvedJavaModuleImpl(getOriginalClass(type).getModule());
    }

    @Override
    public ResolvedJavaPackage getPackage(ResolvedJavaType type) {
        Package pkg = getOriginalClass(type).getPackage();
        if (pkg == null) {
            return null;
        }
        return new HostVMResolvedJavaPackageImpl(providers.getMetaAccess(), pkg);
    }

    private Class<?> getOriginalClass(ResolvedJavaType type) {
        Class<?> originalClass = providers.getSnippetReflection().originalClass(type);
        if (originalClass == null) {
            throw new RuntimeException("No original class for type " + type);
        }
        return originalClass;
    }

    @Override
    public ResolvedJavaModuleLayer bootModuleLayer() {
        return new HostVMResolvedJavaModuleLayerImpl(ModuleLayer.boot());
    }

    @Override
    public URL getCodeSourceLocation(ResolvedJavaType type) {
        Class<?> originalClass = providers.getSnippetReflection().originalClass(type);
        ProtectionDomain pd = originalClass.getProtectionDomain();
        CodeSource cs = pd.getCodeSource();
        if (cs == null) {
            return null;
        }
        return cs.getLocation();
    }

    @Override
    public ResolvedJavaType lookupPlatformClassLoaderType(String name) {
        return lookupType(name, ClassLoader.getPlatformClassLoader());
    }

    @Override
    public ResolvedJavaType lookupAppClassLoaderType(String name) {
        return lookupType(name, appClassLoader);
    }

    private ResolvedJavaType lookupType(String name, ClassLoader loader) {
        try {
            Class<?> cls = Class.forName(name, false, loader);
            return providers.getMetaAccess().lookupJavaType(cls);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Override
    public void copyMemory(JavaConstant src, int srcFrom, int srcTo, byte[] dst, int dstFrom) {
        ResolvedJavaType arrayType = getProviders().getMetaAccess().lookupJavaType(src);
        if (arrayType == null || !arrayType.isArray() || !arrayType.getComponentType().isPrimitive()) {
            throw new IllegalArgumentException("Expected a primitive array constant, got " + src);
        }
        var array = providers.getSnippetReflection().asObject(Object.class, src);
        if (array == null) {
            throw new IllegalArgumentException("Could not unwrap an array constant: " + src);
        }
        int sourceArrayEnd = Array.getLength(array) * arrayType.getComponentType().getJavaKind().getByteCount();
        if (srcFrom < 0 || srcTo > sourceArrayEnd || srcTo < srcFrom) {
            throw new IllegalArgumentException(
                            "Invalid input range: " + srcFrom + ".." + srcTo + " for array of length " + Array.getLength(array) + " with kind " + arrayType.getComponentType().getJavaKind());
        }
        int bytesToCopy = srcTo - srcFrom;
        if (dstFrom < 0 || dstFrom > dst.length - bytesToCopy) {
            throw new IllegalArgumentException("Invalid output range: " + dstFrom + ".." + (dstFrom + bytesToCopy) + " for array of length " + dst.length);
        }
        var unsafe = Unsafe.getUnsafe();
        unsafe.copyMemory(array, unsafe.arrayBaseOffset(array.getClass()) + srcFrom, dst, Unsafe.ARRAY_BYTE_BASE_OFFSET + dstFrom, bytesToCopy);
    }

    /**
     * Host mode performs the unaligned read with {@link Unsafe} against the unwrapped hosted array
     * object.
     */
    @Override
    public JavaConstant readPrimitiveArrayUnaligned(JavaConstant primitiveArray, JavaKind kind, int offset) {
        if (kind == null || !kind.isPrimitive() || kind == JavaKind.Void) {
            throw new IllegalArgumentException("Expected a non-void primitive kind, got " + kind);
        }
        ResolvedJavaType arrayType = getProviders().getMetaAccess().lookupJavaType(primitiveArray);
        if (arrayType == null || !arrayType.isArray() || !arrayType.getComponentType().isPrimitive()) {
            throw new IllegalArgumentException("Expected a primitive array constant, got " + primitiveArray);
        }
        Object array = providers.getSnippetReflection().asObject(Object.class, primitiveArray);
        if (array == null) {
            throw new IllegalArgumentException("Could not unwrap an array constant: " + primitiveArray);
        }

        int bytesToRead = kind.getByteCount();
        long sourceArrayEnd = (long) Array.getLength(array) * arrayType.getComponentType().getJavaKind().getByteCount();
        if (offset < 0 || (long) offset + bytesToRead > sourceArrayEnd) {
            throw new IllegalArgumentException(
                            "Invalid input range: " + offset + ".." + (offset + bytesToRead) + " for array of length " + Array.getLength(array) + " with kind " +
                                            arrayType.getComponentType().getJavaKind());
        }

        Unsafe unsafe = Unsafe.getUnsafe();
        long baseOffset = unsafe.arrayBaseOffset(array.getClass());
        long absoluteOffset = baseOffset + offset;
        return switch (kind) {
            case Boolean -> JavaConstant.forBoolean(unsafe.getByte(array, absoluteOffset) != 0);
            case Byte -> JavaConstant.forByte(unsafe.getByte(array, absoluteOffset));
            case Short -> JavaConstant.forShort(unsafe.getShort(array, absoluteOffset));
            case Char -> JavaConstant.forChar(unsafe.getChar(array, absoluteOffset));
            case Int -> JavaConstant.forInt(unsafe.getInt(array, absoluteOffset));
            case Long -> JavaConstant.forLong(unsafe.getLong(array, absoluteOffset));
            case Float -> JavaConstant.forFloat(unsafe.getFloat(array, absoluteOffset));
            case Double -> JavaConstant.forDouble(unsafe.getDouble(array, absoluteOffset));
            default -> throw new IllegalArgumentException("Unsupported kind: " + kind);
        };
    }

    @Override
    public JavaConstant createHostProxy(Object hostTarget, ResolvedJavaType guestType) {
        Objects.requireNonNull(hostTarget);
        Class<?> guestClass = providers.getSnippetReflection().originalClass(Objects.requireNonNull(guestType));
        if (guestClass == null || !guestClass.isInterface()) {
            throw new IllegalArgumentException("Invalid guest type");
        }
        /* There is no fast-path for guestClass == hostClass due to exception handling */
        HostProxyHandler handler = new HostProxyHandler(hostTarget, getHostProxyMethodMap(hostTarget.getClass(), guestClass));
        Object guestHostProxy = Proxy.newProxyInstance(guestClass.getClassLoader(), new Class<?>[]{guestClass}, handler);
        return providers.getSnippetReflection().forObject(guestHostProxy);
    }

    @Override
    public Throwable unwrapHostProxyException(JavaConstant guestWrapper) {
        Objects.requireNonNull(guestWrapper);
        HostProxyExceptionImpl e = providers.getSnippetReflection().asObject(HostProxyExceptionImpl.class, guestWrapper);
        if (e == null) {
            return null;
        }
        return e.getCause();
    }

    private Map<Method, MethodHandle> getHostProxyMethodMap(Class<?> hostClass, Class<?> guestClass) {
        HostProxyMethodMapKey key = new HostProxyMethodMapKey(hostClass, guestClass);
        Map<Method, MethodHandle> methodMap = hostProxyMethodMap.get(key);
        if (methodMap != null) {
            return methodMap;
        }
        methodMap = computeMethodMap(hostClass, guestClass, hostProxyMethodHandles);
        Map<Method, MethodHandle> previous = hostProxyMethodMap.putIfAbsent(key, methodMap);
        return previous == null ? methodMap : previous;
    }

    private record HostProxyMethodMapKey(Class<?> hostClass, Class<?> guestClass) {
    }

    private static HostProxyHandlerMethodHandles getHostProxyMethodHandles(Providers providers) {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            // (SnippetReflectionProvider, Object) -> JavaConstant
            MethodHandle forObject = lookup.findVirtual(SnippetReflectionProvider.class, "forObject", MethodType.methodType(JavaConstant.class, Object.class));
            // (Object) -> JavaConstant
            forObject = forObject.bindTo(providers.getSnippetReflection());

            // (SnippetReflectionProvider, Class, JavaConstant) -> Object
            MethodHandle asObject = lookup.findVirtual(SnippetReflectionProvider.class, "asObject", MethodType.methodType(Object.class, Class.class, JavaConstant.class));
            // (JavaConstant) -> Object
            asObject = MethodHandles.insertArguments(asObject, 0, providers.getSnippetReflection(), Object.class);

            // JavaConstant factories
            // (<primitive type>) -> PrimitiveConstant
            MethodHandle javaConstantForBoolean = lookup.findStatic(JavaConstant.class, "forBoolean", MethodType.methodType(PrimitiveConstant.class, boolean.class));
            MethodHandle javaConstantForByte = lookup.findStatic(JavaConstant.class, "forByte", MethodType.methodType(PrimitiveConstant.class, byte.class));
            MethodHandle javaConstantForShort = lookup.findStatic(JavaConstant.class, "forShort", MethodType.methodType(PrimitiveConstant.class, short.class));
            MethodHandle javaConstantForChar = lookup.findStatic(JavaConstant.class, "forChar", MethodType.methodType(PrimitiveConstant.class, char.class));
            MethodHandle javaConstantForInt = lookup.findStatic(JavaConstant.class, "forInt", MethodType.methodType(PrimitiveConstant.class, int.class));
            MethodHandle javaConstantForLong = lookup.findStatic(JavaConstant.class, "forLong", MethodType.methodType(PrimitiveConstant.class, long.class));
            MethodHandle javaConstantForFloat = lookup.findStatic(JavaConstant.class, "forFloat", MethodType.methodType(PrimitiveConstant.class, float.class));
            MethodHandle javaConstantForDouble = lookup.findStatic(JavaConstant.class, "forDouble", MethodType.methodType(PrimitiveConstant.class, double.class));

            // (<primitive type>) -> JavaConstant
            javaConstantForBoolean = javaConstantForBoolean.asType(MethodType.methodType(JavaConstant.class, boolean.class));
            javaConstantForByte = javaConstantForByte.asType(MethodType.methodType(JavaConstant.class, byte.class));
            javaConstantForShort = javaConstantForShort.asType(MethodType.methodType(JavaConstant.class, short.class));
            javaConstantForChar = javaConstantForChar.asType(MethodType.methodType(JavaConstant.class, char.class));
            javaConstantForInt = javaConstantForInt.asType(MethodType.methodType(JavaConstant.class, int.class));
            javaConstantForLong = javaConstantForLong.asType(MethodType.methodType(JavaConstant.class, long.class));
            javaConstantForFloat = javaConstantForFloat.asType(MethodType.methodType(JavaConstant.class, float.class));
            javaConstantForDouble = javaConstantForDouble.asType(MethodType.methodType(JavaConstant.class, double.class));

            // JavaConstant extractors
            // (JavaConstant) -> <primitive type>
            MethodHandle javaConstantAsBoolean = lookup.findVirtual(JavaConstant.class, "asBoolean", MethodType.methodType(boolean.class));
            MethodHandle javaConstantAsInt = lookup.findVirtual(JavaConstant.class, "asInt", MethodType.methodType(int.class));
            MethodHandle javaConstantAsLong = lookup.findVirtual(JavaConstant.class, "asLong", MethodType.methodType(long.class));
            MethodHandle javaConstantAsFloat = lookup.findVirtual(JavaConstant.class, "asFloat", MethodType.methodType(float.class));
            MethodHandle javaConstantAsDouble = lookup.findVirtual(JavaConstant.class, "asDouble", MethodType.methodType(double.class));

            // (JavaConstant) -> byte
            MethodHandle javaConstantAsByte = MethodHandles.explicitCastArguments(javaConstantAsInt, MethodType.methodType(byte.class, JavaConstant.class));
            // (JavaConstant) -> short
            MethodHandle javaConstantAsShort = MethodHandles.explicitCastArguments(javaConstantAsInt, MethodType.methodType(short.class, JavaConstant.class));
            // (JavaConstant) -> char
            MethodHandle javaConstantAsChar = MethodHandles.explicitCastArguments(javaConstantAsInt, MethodType.methodType(char.class, JavaConstant.class));

            // Field/Executable conversions
            // (MetaAccessProvider, Field) -> ResolvedJavaField
            MethodHandle lookupJavaField = lookup.findVirtual(MetaAccessProvider.class, "lookupJavaField", MethodType.methodType(ResolvedJavaField.class, Field.class));
            // (Field) -> ResolvedJavaField
            lookupJavaField = lookupJavaField.bindTo(providers.getMetaAccess());

            // (MetaAccessProvider, Method) -> ResolvedJavaMethod
            MethodHandle lookupJavaMethod = lookup.findVirtual(MetaAccessProvider.class, "lookupJavaMethod", MethodType.methodType(ResolvedJavaMethod.class, java.lang.reflect.Executable.class));
            // (Method) -> ResolvedJavaMethod
            lookupJavaMethod = lookupJavaMethod.bindTo(providers.getMetaAccess());

            // (MetaAccessProvider, Class) -> ResolvedJavaType
            MethodHandle lookupJavaType = lookup.findVirtual(MetaAccessProvider.class, "lookupJavaType", MethodType.methodType(ResolvedJavaType.class, Class.class));
            // (Class) -> ResolvedJavaType
            lookupJavaType = lookupJavaType.bindTo(providers.getMetaAccess());

            // (SnippetReflectionProvider, ResolvedJavaField) -> Field
            MethodHandle originalField = lookup.findVirtual(SnippetReflectionProvider.class, "originalField", MethodType.methodType(Field.class, ResolvedJavaField.class));
            // (ResolvedJavaField) -> Field
            originalField = originalField.bindTo(providers.getSnippetReflection());

            // (SnippetReflectionProvider, ResolvedJavaMethod) -> Executable
            MethodHandle originalMethod = lookup.findVirtual(SnippetReflectionProvider.class, "originalMethod", MethodType.methodType(java.lang.reflect.Executable.class, ResolvedJavaMethod.class));
            // (ResolvedJavaMethod) -> Executable
            originalMethod = originalMethod.bindTo(providers.getSnippetReflection());

            // (SnippetReflectionProvider, ResolvedJavaType) -> Class
            MethodHandle originalClass = lookup.findVirtual(SnippetReflectionProvider.class, "originalClass", MethodType.methodType(Class.class, ResolvedJavaType.class));
            // (ResolvedJavaType) -> Class
            originalClass = originalClass.bindTo(providers.getSnippetReflection());

            // (SnippetReflectionProvider, Throwable) -> Object
            MethodHandle filterException = lookup.findStatic(HostProxyHandler.class, "filterException", MethodType.methodType(Object.class, SnippetReflectionProvider.class, Throwable.class));
            // (Throwable) -> Object
            filterException = filterException.bindTo(providers.getSnippetReflection());

            return new HostProxyHandlerMethodHandles(
                            forObject, asObject,
                            javaConstantForBoolean, javaConstantForByte, javaConstantForShort, javaConstantForChar, javaConstantForInt, javaConstantForLong, javaConstantForFloat,
                            javaConstantForDouble,
                            javaConstantAsBoolean, javaConstantAsByte, javaConstantAsShort, javaConstantAsChar, javaConstantAsInt, javaConstantAsLong, javaConstantAsFloat, javaConstantAsDouble,
                            lookupJavaField, lookupJavaMethod, lookupJavaType,
                            originalField, originalMethod, originalClass,
                            filterException);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
