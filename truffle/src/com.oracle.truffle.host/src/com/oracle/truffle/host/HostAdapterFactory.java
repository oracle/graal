/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostAccess;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;

/**
 * A factory class that generates host adapter classes.
 */
final class HostAdapterFactory {

    @TruffleBoundary
    static AdapterResult getAdapterClassFor(HostContext hostContext, Class<?>[] types, Object classOverrides) {
        assert types.length > 0;
        HostClassCache hostClassCache = hostContext.getHostClassCache();
        HostClassLoader hostClassLoader = hostContext.getClassloader();
        if (classOverrides == null) {
            if (types.length == 1) {
                HostClassDesc classDesc = HostClassDesc.forClass(hostClassCache, types[0]);
                return classDesc.getAdapter(hostContext);
            } else {
                Map<List<Class<?>>, AdapterResult> map = hostContext.adapterCache.get(getTypeForCache(types));
                List<Class<?>> cacheKey = Arrays.asList(types);
                AdapterResult result = map.get(cacheKey);
                if (result == null) {
                    result = makeAdapterClassFor(hostClassCache, types, hostClassLoader, classOverrides);
                    if (result.isSuccess()) {
                        AdapterResult prev = map.putIfAbsent(cacheKey, result);
                        if (prev != null) {
                            result = prev;
                        }
                    }
                }
                return result;
            }
        }
        return HostAdapterFactory.makeAdapterClassFor(hostClassCache, types, hostClassLoader, classOverrides);
    }

    @TruffleBoundary
    static AdapterResult makeAdapterClassFor(HostClassCache hostClassCache, Class<?>[] types, ClassLoader classLoader, Object classOverrides) {
        return makeAdapterClassForCommon(hostClassCache, types, classLoader, classOverrides);
    }

    @TruffleBoundary
    static AdapterResult makeAdapterClassFor(HostClassCache hostClassCache, Class<?> type, ClassLoader classLoader) {
        return makeAdapterClassForCommon(hostClassCache, new Class<?>[]{type}, classLoader, null);
    }

    private static AdapterResult makeAdapterClassForCommon(HostClassCache hostClassCache, Class<?>[] types, ClassLoader classLoader, Object classOverrides) {
        assert types.length > 0;
        CompilerAsserts.neverPartOfCompilation();

        AbstractHostAccess polyglotAccess = hostClassCache.polyglotHostAccess;
        if (TruffleOptions.AOT) {
            throw HostEngineException.unsupported(polyglotAccess, String.format(
                            "Cannot create host adapter class to extend %s. Generating new classes at run time is not supported on Native Image.",
                            types.length == 1 ? types[0] : Arrays.toString(types)));
        }

        Class<?> superClass = null;
        final List<Class<?>> interfaces = new ArrayList<>();
        for (final Class<?> t : types) {
            if (!t.isInterface()) {
                if (superClass != null) {
                    throw HostEngineException.illegalArgument(polyglotAccess,
                                    String.format("Can not extend multiple classes %s and %s. At most one of the specified types can be a class, the rest must all be interfaces.",
                                                    t.getCanonicalName(), superClass.getCanonicalName()));
                } else if (Modifier.isFinal(t.getModifiers())) {
                    throw HostEngineException.illegalArgument(polyglotAccess, String.format("Can not extend final class %s.", t.getCanonicalName()));
                } else {
                    superClass = t;
                }
            } else {
                if (interfaces.size() >= 65535) {
                    throw HostEngineException.illegalArgument(polyglotAccess, "interface limit exceeded");
                }

                interfaces.add(t);
            }
            if (!Modifier.isPublic(t.getModifiers())) {
                throw HostEngineException.illegalArgument(polyglotAccess, String.format("Class not public: %s.", t.getCanonicalName()));
            }
            if (!HostInteropReflect.isExtensibleType(t) || !hostClassCache.allowsImplementation(t)) {
                throw HostEngineException.illegalArgument(polyglotAccess, String.format("Implementation not allowed for %s", t));
            }
        }
        superClass = superClass != null ? superClass : Object.class;

        // If the superclass is an adapter class, we need to use its class loader as the parent.
        ClassLoader commonLoader = getCommonClassLoader(classLoader, superClass);

        // Fail early if the class loader cannot load all supertypes.
        if (!classLoaderCanSee(commonLoader, types)) {
            throw HostEngineException.illegalArgument(polyglotAccess, String.format("Could not determine a class loader that can see all types: %s", Arrays.toString(types)));
        }

        Class<?> adapterClass;
        try {
            adapterClass = generateAdapterClassFor(superClass, interfaces, commonLoader, hostClassCache, classOverrides);
        } catch (IllegalArgumentException ex) {
            return new AdapterResult(HostEngineException.illegalArgument(polyglotAccess, ex));
        } catch (RuntimeException ex) {
            if (polyglotAccess.isEngineException(ex)) {
                return new AdapterResult(ex);
            }
            throw ex;
        }

        HostClassDesc classDesc = hostClassCache.forClass(adapterClass);
        HostMethodDesc constructor = classDesc.lookupConstructor();
        HostMethodDesc.SingleMethod valueConstructor = null;
        if (constructor != null) {
            for (HostMethodDesc.SingleMethod overload : constructor.getOverloads()) {
                if (overload.getParameterCount() == 1 && overload.getParameterTypes()[0] == hostClassCache.apiAccess.getValueClass()) {
                    valueConstructor = overload;
                    break;
                }
            }
            return new AdapterResult(adapterClass, constructor, valueConstructor);
        } else {
            return new AdapterResult(HostEngineException.illegalArgument(polyglotAccess, String.format("No accessible constructor: %s", superClass.getCanonicalName())));
        }
    }

    private static Class<?> generateAdapterClassFor(Class<?> superClass, List<Class<?>> interfaces, ClassLoader commonLoader, HostClassCache hostClassCache, Object classOverrides) {
        boolean classOverride = classOverrides != null;
        HostAdapterBytecodeGenerator bytecodeGenerator = new HostAdapterBytecodeGenerator(superClass, interfaces, commonLoader, hostClassCache, classOverride);
        HostAdapterClassLoader generatedClassLoader = bytecodeGenerator.createAdapterClassLoader();
        return generatedClassLoader.generateClass(hostClassCache, commonLoader, classOverrides);
    }

    @TruffleBoundary
    static Object getSuperAdapter(HostObject adapter) {
        assert isAdapterInstance(adapter.obj);
        return new HostAdapterSuperMembers(adapter);
    }

    @TruffleBoundary
    static String getSuperMethodName(String methodName) {
        assert !methodName.startsWith(HostAdapterBytecodeGenerator.SUPER_PREFIX);
        return HostAdapterBytecodeGenerator.SUPER_PREFIX.concat(methodName);
    }

    @TruffleBoundary
    static boolean isAdapterInstance(Object adapter) {
        return HostAdapterClassLoader.isAdapterInstance(adapter);
    }

    private static boolean classLoaderCanSee(ClassLoader loader, Class<?> clazz) {
        if (clazz.getClassLoader() == loader) {
            return true;
        }
        try {
            return Class.forName(clazz.getName(), false, loader) == clazz;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean classLoaderCanSee(ClassLoader loader, Class<?>[] classes) {
        for (Class<?> c : classes) {
            if (!classLoaderCanSee(loader, c)) {
                return false;
            }
        }
        return true;
    }

    private static ClassLoader getCommonClassLoader(ClassLoader classLoader, Class<?> superclass) {
        if (superclass != Object.class) {
            if (HostAdapterClassLoader.isGeneratedClass(superclass)) {
                return superclass.getClassLoader();
            }
        }
        return classLoader;
    }

    private static Class<?> getTypeForCache(Class<?>[] types) {
        return types[0];
    }

    static final class AdapterResult {
        private final Class<?> adapterClass;
        private final HostMethodDesc constructor;
        private final HostMethodDesc.SingleMethod valueConstructor;
        private final RuntimeException exception;

        AdapterResult(Class<?> adapterClass, HostMethodDesc constructor, HostMethodDesc.SingleMethod valueConstructor) {
            this.adapterClass = Objects.requireNonNull(adapterClass);
            this.constructor = constructor;
            this.valueConstructor = valueConstructor;
            this.exception = null;
        }

        AdapterResult(RuntimeException exception) {
            this.adapterClass = null;
            this.constructor = null;
            this.valueConstructor = null;
            this.exception = exception;
        }

        Class<?> getAdapterClass() {
            return adapterClass;
        }

        HostMethodDesc getConstructor() {
            return constructor;
        }

        HostMethodDesc.SingleMethod getValueConstructor() {
            return valueConstructor;
        }

        boolean isSuccess() {
            return constructor != null;
        }

        boolean isAutoConvertible() {
            return valueConstructor != null;
        }

        RuntimeException throwException() {
            throw exception;
        }
    }

}
