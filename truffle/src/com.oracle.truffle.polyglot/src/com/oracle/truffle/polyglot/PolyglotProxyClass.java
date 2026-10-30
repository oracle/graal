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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.graalvm.polyglot.Value.Optimized;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.asm.Type;
import com.oracle.truffle.api.nodes.ExecutionSignature;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValuesNode;
import com.oracle.truffle.polyglot.PolyglotObjectProxyHandler.MemberInvokeNode;
import com.oracle.truffle.polyglot.PolyglotProxyClassFactory.ObjectMethodNodeGen;

/**
 * Metadata for a generated interface implementation. Class lookup and definition are cached per
 * interface; instance creation binds call targets from the current sharing layer.
 * Metadata arrays are shared internally and must not be modified.
 */
final class PolyglotProxyClass {

    private static final String PROXY_PREFIX = "com.oracle.truffle.polyglot.generated.$GeneratedProxy$";
    private static final ClassValue<ProxyClassCache> PROXY_CLASSES = new ClassValue<>() {
        @Override
        protected ProxyClassCache computeValue(Class<?> interfaceType) {
            return new ProxyClassCache();
        }
    };

    private static final class ProxyClassCache {
        volatile PolyglotProxyClass proxyClass;
    }

    private final Class<?> interfaceType;
    private final Method[] methods;
    private final Constructor<?> constructor;
    private final Method[] exactEntryMethods;

    private PolyglotProxyClass(Class<?> interfaceType, Method[] methods, Constructor<?> constructor, Method[] exactEntryMethods) {
        this.interfaceType = interfaceType;
        this.methods = methods;
        this.constructor = constructor;
        this.exactEntryMethods = exactEntryMethods;
    }

    Method[] getMethods() {
        return methods;
    }

    Constructor<?> getConstructor() {
        return constructor;
    }

    Method[] getExactEntryMethods() {
        return exactEntryMethods;
    }

    static boolean isOptimizationRequested(Class<?> type) {
        return type.isAnnotationPresent(Optimized.class);
    }

    static String proxyKey(Class<?> interfaceType) {
        Module module = interfaceType.getModule();
        return (module.isNamed() ? module.getName() : "") + ":" + interfaceType.getName();
    }

    /** Generates, defines and installs a proxy for native-image preparation. */
    static PolyglotProxyClass registerGeneratedProxy(Class<?> interfaceType, Predicate<Method> exact) {
        PolyglotProxyClass proxyClass = prepareProxy(interfaceType, exact);
        install(proxyClass);
        return proxyClass;
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static PolyglotProxyClass prepareProxy(Class<?> interfaceType, Predicate<Method> exact) {
        requireSupportedInterface(interfaceType);
        String binaryName = PROXY_PREFIX + digest(proxyKey(interfaceType));
        GeneratedProxyClassLoader loader = new GeneratedProxyClassLoader(interfaceType.getClassLoader());
        Method[] methods = methodPlan(interfaceType);
        Module proxyModule = loader.getUnnamedModule();
        requireAccessibleType(interfaceType, proxyModule, interfaceType.getTypeName());
        for (Method method : methods) {
            String location = method.toString();
            requireAccessibleType(method.getReturnType(), proxyModule, location);
            for (Class<?> parameter : method.getParameterTypes()) {
                requireAccessibleType(parameter, proxyModule, location);
            }
            for (Class<?> exception : method.getExceptionTypes()) {
                requireAccessibleType(exception, proxyModule, location);
            }
        }
        String[] exactEntryNames = exactEntryNames(methods, exact);
        byte[] bytecode = PolyglotProxyGenerator.generate(binaryName.replace('.', '/'), interfaceType, methods, exactEntryNames);
        PolyglotGeneratedProxy.class.getModule().addExports(PolyglotGeneratedProxy.class.getPackageName(), proxyModule);
        Class<?> generatedClass = loader.define(binaryName, bytecode);
        assert generatedClass != null : "Class definer returned null";
        assert binaryName.equals(generatedClass.getName()) : generatedClass;
        assert interfaceType.isAssignableFrom(generatedClass) : generatedClass;
        try {
            Constructor<?> constructor = generatedClass.getConstructor(Object.class, Object.class, Object.class, CallTarget.class, CallTarget.class, CallTarget.class, CallTarget[].class);
            Method[] entries = new Method[methods.length];
            for (int i = 0; i < methods.length; i++) {
                if (exactEntryNames[i] != null) {
                    Class<?>[] original = methods[i].getParameterTypes();
                    Class<?>[] parameters = new Class<?>[original.length + 3];
                    parameters[0] = CallTarget.class;
                    parameters[1] = Object.class;
                    parameters[2] = Object.class;
                    System.arraycopy(original, 0, parameters, 3, original.length);
                    entries[i] = generatedClass.getDeclaredMethod(exactEntryNames[i], parameters);
                }
            }
            return new PolyglotProxyClass(interfaceType, methods, constructor, entries);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Missing generated proxy member in " + binaryName, e);
        } catch (LinkageError e) {
            throw new IllegalArgumentException("Cannot resolve generated proxy " + binaryName + " for " + interfaceType.getTypeName(), e);
        }
    }

    private static void install(PolyglotProxyClass proxyClass) {
        /* Hosted image builds may share interface classes but install different proxy layouts. */
        PROXY_CLASSES.get(proxyClass.interfaceType).proxyClass = proxyClass;
    }

    /** Returns generated proxy metadata for the interface in the current runtime mode. */
    @TruffleBoundary
    static PolyglotProxyClass get(Class<?> type) {
        try {
            requireSupportedInterface(type);
            ProxyClassCache cache = PROXY_CLASSES.get(type);
            PolyglotProxyClass proxyClass = cache.proxyClass;
            if (proxyClass == null) {
                if (TruffleOptions.AOT) {
                    throw unsupported(type, new IllegalStateException("No native-image proxy metadata was installed"));
                }
                synchronized (cache) {
                    proxyClass = cache.proxyClass;
                    if (proxyClass == null) {
                        try {
                            proxyClass = prepareProxy(type, method -> false);
                            cache.proxyClass = proxyClass;
                        } catch (IllegalArgumentException | SecurityException | LinkageError e) {
                            throw unsupported(type, e);
                        }
                    }
                }
            }
            return proxyClass;
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            throw PolyglotEngineException.unsupported(e.getMessage(), e);
        }
    }

    @TruffleBoundary
    Object newInstance(PolyglotLanguageContext languageContext, Object receiver, java.lang.reflect.Type genericType, Method functionMethod) {
        try {
            CallTarget[] targets = new CallTarget[methods.length];
            boolean function = functionMethod != null;
            for (int i = 0; i < methods.length; i++) {
                Method method = methods[i];
                if (!function || !method.isDefault()) {
                    Method exactEntry = exactEntryMethods[i];
                    ExecutionSignature signature = exactEntry == null ? null : exactExecutionSignature(method);
                    targets[i] = function
                                    ? PolyglotFunctionProxyHandler.FunctionProxyNode.lookup(languageContext, receiver.getClass(), functionMethod, genericType, true, signature)
                                    : PolyglotObjectProxyHandler.ObjectProxyNode.lookup(languageContext, receiver.getClass(), interfaceType, genericType, method, signature);
                }
            }
            CallTarget equalsTarget = null;
            CallTarget hashCodeTarget = null;
            CallTarget toStringTarget = null;
            if (!function) {
                ObjectMethodTargets objectTargets = ObjectMethodTargets.lookup(languageContext, receiver.getClass());
                equalsTarget = objectTargets.equalsTarget;
                hashCodeTarget = objectTargets.hashCodeTarget;
                toStringTarget = objectTargets.toStringTarget;
            }
            return constructor.newInstance(languageContext, receiver, languageContext.context.getContextAPI(),
                            equalsTarget, hashCodeTarget, toStringTarget, targets);
        } catch (ReflectiveOperationException | SecurityException | LinkageError e) {
            throw PolyglotEngineException.unsupported("Cannot instantiate @Value.Optimized implementation for " + interfaceType.getTypeName(), e);
        }
    }

    private static Method[] methodPlan(Class<?> interfaceType) {
        Map<MethodKey, Method> bySignature = new HashMap<>();
        /* Match JDK Proxy's named-member declaration selection before ordering emitted methods. */
        for (Method method : interfaceType.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) && !PolyglotFunctionProxyHandler.isObjectMethodOverride(method)) {
                MethodKey signature = new MethodKey(method.getName(), Type.getMethodDescriptor(method));
                bySignature.putIfAbsent(signature, method);
            }
        }
        ArrayList<Method> methods = new ArrayList<>(bySignature.values());
        methods.sort(Comparator.comparing(Method::getName).thenComparing(method -> Type.getMethodDescriptor(method)));
        return methods.toArray(Method[]::new);
    }

    private record MethodKey(String name, String descriptor) {
    }

    private static String[] exactEntryNames(Method[] methods, Predicate<Method> exact) {
        Set<ExactEntryKey> signatures = new HashSet<>();
        for (Method method : methods) {
            signatures.add(new ExactEntryKey(method.getName(), Arrays.asList(method.getParameterTypes())));
        }
        String[] names = new String[methods.length];
        for (int i = 0; i < methods.length; i++) {
            if (!exact.test(methods[i])) {
                continue;
            }
            Method method = methods[i];
            List<Class<?>> parameters = new ArrayList<>();
            parameters.add(CallTarget.class);
            parameters.add(Object.class);
            parameters.add(Object.class);
            parameters.addAll(Arrays.asList(method.getParameterTypes()));
            String baseName = method.getName() + "$exact";
            String name = baseName;
            int suffix = 0;
            /* Reflection cannot distinguish return-only overloads. Avoid interface members too. */
            while (!signatures.add(new ExactEntryKey(name, parameters))) {
                name = baseName + "$" + ++suffix;
            }
            names[i] = name;
        }
        return names;
    }

    private record ExactEntryKey(String name, List<Class<?>> parameters) {
    }

    private static void requireSupportedInterface(Class<?> type) {
        if (type == null || !type.isInterface() || !Modifier.isPublic(type.getModifiers()) || type.isSealed() || !type.isAnnotationPresent(Optimized.class)) {
            throw new IllegalArgumentException("Unsupported @Value.Optimized interface " + (type == null ? "null" : type.getTypeName()) +
                            ": expected a public, non-sealed interface annotated with @Value.Optimized");
        }
    }

    private static void requireAccessibleType(Class<?> signatureType, Module proxyModule, String location) {
        Class<?> type = signatureType;
        while (type.isArray()) {
            type = type.getComponentType();
        }
        if (!type.isPrimitive()) {
            if (!Modifier.isPublic(type.getModifiers())) {
                throw new IllegalArgumentException("Unsupported @Value.Optimized signature type " + signatureType.getTypeName() + " in " + location + ": type must be public");
            }
            Module module = type.getModule();
            if (!module.isExported(type.getPackageName(), proxyModule)) {
                throw new IllegalArgumentException("Unsupported @Value.Optimized signature type " + signatureType.getTypeName() + " in " + location +
                                ": package must be exported to generated proxies in unnamed modules, even when Truffle runs as a named module. " +
                                "Add 'exports " + type.getPackageName() + ";' to module-info.java or use --add-exports=" + module.getName() + "/" + type.getPackageName() + "=ALL-UNNAMED");
            }
        }
    }

    private static ExecutionSignature exactExecutionSignature(Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        Class<?>[] argumentTypes = new Class<?>[parameters.length + 2];
        for (int i = 0; i < parameters.length; i++) {
            Class<?> parameter = parameters[i];
            argumentTypes[i + 2] = parameter.isPrimitive() ? boxedType(parameter) : null;
        }
        Class<?> returnType = method.getReturnType();
        return ExecutionSignature.create(returnType == void.class || !returnType.isPrimitive() ? Object.class : boxedType(returnType), argumentTypes);
    }

    private static Class<?> boxedType(Class<?> primitiveType) {
        if (primitiveType == boolean.class) {
            return Boolean.class;
        } else if (primitiveType == byte.class) {
            return Byte.class;
        } else if (primitiveType == short.class) {
            return Short.class;
        } else if (primitiveType == char.class) {
            return Character.class;
        } else if (primitiveType == int.class) {
            return Integer.class;
        } else if (primitiveType == long.class) {
            return Long.class;
        } else if (primitiveType == float.class) {
            return Float.class;
        } else if (primitiveType == double.class) {
            return Double.class;
        }
        throw new AssertionError(primitiveType);
    }

    private static UnsupportedOperationException unsupported(Class<?> type, Throwable cause) {
        return new UnsupportedOperationException("Cannot generate @Value.Optimized implementation for " + type.getTypeName() + ": " + cause.getMessage(), cause);
    }

    private record ObjectMethodTargets(CallTarget equalsTarget, CallTarget hashCodeTarget, CallTarget toStringTarget) {
        static ObjectMethodTargets lookup(PolyglotLanguageContext languageContext, Class<?> receiverClass) {
            ObjectMethodKey key = new ObjectMethodKey(receiverClass);
            ObjectMethodTargets targets = HostToGuestRootNode.lookupHostCodeCache(languageContext, key, ObjectMethodTargets.class);
            if (targets == null) {
                targets = new ObjectMethodTargets(new ObjectMethodRootNode(languageContext, receiverClass, "equals", boolean.class).getCallTarget(),
                                new ObjectMethodRootNode(languageContext, receiverClass, "hashCode", int.class).getCallTarget(),
                                new ObjectMethodRootNode(languageContext, receiverClass, "toString", String.class).getCallTarget());
                targets = HostToGuestRootNode.installHostCodeCache(languageContext, key, targets, ObjectMethodTargets.class);
            }
            return targets;
        }
    }

    private record ObjectMethodKey(Class<?> receiverClass) {
    }

    /* The guest child handles context entry; Java fallbacks do not go through this root. */
    static final class ObjectMethodRootNode extends RootNode {
        final String name;
        @Child private ObjectMethodNode guest;

        ObjectMethodRootNode(PolyglotLanguageContext languageContext, Class<?> receiverClass, String name, Class<?> returnClass) {
            super(null);
            EngineAccessor.NODES.setSharingLayer(this, languageContext.getLanguageInstance().sharing);
            this.name = name;
            this.guest = ObjectMethodNodeGen.create(receiverClass, name, returnClass);
        }

        @Override
        public String getName() {
            return "GeneratedProxy." + name;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return guest.invoke(frame.getArguments());
        }
    }

    abstract static class ObjectMethodNode extends Node {
        final Class<?> receiverClass;
        final String name;
        final Class<?> returnClass;

        ObjectMethodNode(Class<?> receiverClass, String name, Class<?> returnClass) {
            this.receiverClass = receiverClass;
            this.name = name;
            this.returnClass = returnClass;
        }

        @TruffleBoundary
        final Object invoke(Object[] arguments) {
            PolyglotLanguageContext languageContext = (PolyglotLanguageContext) arguments[0];
            Object previous = PolyglotValueDispatch.hostEnter(languageContext);
            try {
                return execute(languageContext, receiverClass.cast(arguments[1]), arguments);
            } catch (Throwable failure) {
                throw PolyglotImpl.guestToHostException(languageContext, failure, true);
            } finally {
                PolyglotValueDispatch.hostLeave(languageContext, previous);
            }
        }

        abstract Object execute(PolyglotLanguageContext languageContext, Object receiver, Object[] arguments);

        @Specialization
        final Object doDefault(PolyglotLanguageContext languageContext, Object receiver, Object[] arguments,
                        @Cached ToGuestValuesNode toGuests,
                        @Cached MemberInvokeNode invoke) {
            return invoke.execute(languageContext, receiver, name, false, returnClass, returnClass, toGuests.execute(this, arguments, HostToGuestRootNode.ARGUMENT_OFFSET));
        }
    }

    private static final class GeneratedProxyClassLoader extends ClassLoader {
        GeneratedProxyClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            try {
                return super.loadClass(name, resolve);
            } catch (ClassNotFoundException e) {
                Class<?> loaded = PolyglotProxyClass.class.getClassLoader().loadClass(name);
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        Class<?> define(String binaryName, byte[] bytecode) {
            return defineClass(binaryName, bytecode, 0, bytecode.length);
        }
    }
}
