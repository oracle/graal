/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.polyglot.HostAdapterFactory.AdapterResult;
import com.oracle.truffle.polyglot.HostLanguage.HostLanguageException;

final class HostContext {

    @CompilationFinal volatile PolyglotContextImpl internalContext;
    final Map<String, Class<?>> classCache = new HashMap<>();
    final Object topScope = new TopScopeObject(this);
    volatile HostClassLoader classloader;
    private final HostLanguage hostLanguage;
    private ClassLoader contextClassLoader;
    private Predicate<String> classFilter;
    private boolean hostClassLoadingAllowed;
    private boolean hostLookupAllowed;

    @SuppressWarnings("serial") final HostException stackoverflowError = new HostException(new StackOverflowError() {
        @SuppressWarnings("sync-override")
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }, this);

    final ClassValue<Map<List<Class<?>>, AdapterResult>> adapterCache = new ClassValue<Map<List<Class<?>>, AdapterResult>>() {
        @Override
        protected Map<List<Class<?>>, AdapterResult> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    HostContext(HostLanguage hostLanguage) {
        this.hostLanguage = hostLanguage;
    }

    @SuppressWarnings("hiding")
    void initialize(ClassLoader cl, Predicate<String> clFilter, boolean hostCLAllowed, boolean hostLookupAllowed) {
        assert classloader == null : "must not be used during context preinitialization";
        // if assertions are not enabled. dispose the previous class loader to be on the safe side
        disposeClassLoader();
        this.contextClassLoader = cl;

        assert this.classFilter == null : "must not be used during context preinitialization";
        this.classFilter = clFilter;

        assert !this.hostClassLoadingAllowed : "must not be used during context preinitialization";
        this.hostClassLoadingAllowed = hostCLAllowed;

        assert !this.hostLookupAllowed : "must not be used during context preinitialization";
        this.hostLookupAllowed = hostLookupAllowed;
    }

    public HostClassCache getHostClassCache() {
        return hostLanguage.hostClassCache;
    }

    GuestToHostCodeCache getGuestToHostCache() {
        return hostLanguage.getGuestToHostCache();
    }

    @TruffleBoundary
    Class<?> findClass(String className) {
        checkHostAccessAllowed();

        Class<?> loadedClass = classCache.get(className);
        if (loadedClass == null) {
            loadedClass = findClassImpl(className);
            classCache.put(className, loadedClass);
        }
        assert loadedClass != null;
        return loadedClass;
    }

    private void checkHostAccessAllowed() {
        if (!hostLookupAllowed) {
            throw new HostLanguageException(String.format("Host class access is not allowed."));
        }
    }

    HostClassLoader getClassloader() {
        if (classloader == null) {
            ClassLoader parentClassLoader = contextClassLoader;
            classloader = new HostClassLoader(this, parentClassLoader);
        }
        return classloader;
    }

    private Class<?> findClassImpl(String className) {
        validateClass(className);
        if (className.endsWith("[]")) {
            Class<?> componentType = findClass(className.substring(0, className.length() - 2));
            return Array.newInstance(componentType, 0).getClass();
        }
        Class<?> primitiveType = getPrimitiveTypeByName(className);
        if (primitiveType != null) {
            return primitiveType;
        }
        try {
            ClassLoader classLoader = getClassloader();
            Class<?> foundClass = classLoader.loadClass(className);
            Object currentModule = EngineAccessor.JDKSERVICES.getUnnamedModule(classLoader);
            if (EngineAccessor.JDKSERVICES.verifyModuleVisibility(currentModule, foundClass)) {
                return foundClass;
            } else {
                throw new HostLanguageException(String.format("Access to host class %s is not allowed or does not exist.", className));
            }
        } catch (ClassNotFoundException e) {
            throw new HostLanguageException(String.format("Access to host class %s is not allowed or does not exist.", className));
        }
    }

    void validateClass(String className) {
        if (classFilter != null && !classFilter.test(className)) {
            throw new HostLanguageException(String.format("Access to host class %s is not allowed.", className));
        }
    }

    private static Class<?> getPrimitiveTypeByName(String className) {
        switch (className) {
            case "boolean":
                return boolean.class;
            case "byte":
                return byte.class;
            case "char":
                return char.class;
            case "double":
                return double.class;
            case "float":
                return float.class;
            case "int":
                return int.class;
            case "long":
                return long.class;
            case "short":
                return short.class;
            default:
                return null;
        }
    }

    void addToHostClasspath(TruffleFile classpathEntry) {
        checkHostAccessAllowed();
        if (TruffleOptions.AOT) {
            throw new HostLanguageException(String.format("Cannot add classpath entry %s in native mode.", classpathEntry.getName()));
        }
        if (!hostClassLoadingAllowed) {
            throw new HostLanguageException(String.format("Host class loading is not allowed."));
        }
        getClassloader().addClasspathRoot(classpathEntry);
    }

    void initializeInternal(PolyglotContextImpl context) {
        this.internalContext = context;
    }

    <T extends Throwable> RuntimeException hostToGuestException(T e) {
        return PolyglotImpl.hostToGuestException(this, e);
    }

    public Value asValue(Object value) {
        return internalContext.asValue(value);
    }

    Object toGuestValue(Class<?> receiver) {
        return HostObject.forClass(receiver, this);
    }

    private APIAccess getAPIAccess() {
        return hostLanguage.polyglot.getAPIAccess();
    }

    Object toGuestValue(Node parentNode, Object hostValue) {
        if (hostValue instanceof Value) {
            Value receiverValue = (Value) hostValue;
            PolyglotLanguageContext languageContext = (PolyglotLanguageContext) getAPIAccess().getContext(receiverValue);
            PolyglotContextImpl valueContext = languageContext != null ? languageContext.context : null;
            Object valueReceiver = getAPIAccess().getReceiver(receiverValue);
            if (valueContext != this.internalContext) {
                valueReceiver = internalContext.migrateValue(parentNode, valueReceiver, valueContext);
            }
            return valueReceiver;
        } else if (HostWrapper.isInstance(hostValue)) {
            return internalContext.migrateHostWrapper(parentNode, HostWrapper.asInstance(hostValue));
        } else if (PolyglotImpl.isGuestPrimitive(hostValue)) {
            return hostValue;
        } else if (hostValue instanceof Proxy) {
            return PolyglotProxy.toProxyGuestObject(this, (Proxy) hostValue);
        } else if (hostValue instanceof TruffleObject) {
            return hostValue;
        } else if (hostValue instanceof Class) {
            return HostObject.forClass((Class<?>) hostValue, this);
        } else if (hostValue == null) {
            return HostObject.NULL;
        } else if (hostValue.getClass().isArray()) {
            return HostObject.forObject(hostValue, this);
        } else {
            return HostInteropReflect.asTruffleViaReflection(hostValue, this);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class TopScopeObject implements TruffleObject {

        private final HostContext context;

        private TopScopeObject(HostContext context) {
            this.context = context;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasLanguage() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return HostLanguage.class;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isScope() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new ClassNamesObject(context.classCache.keySet());
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberReadable(String member) {
            return context.findClass(member) != null;
        }

        @ExportMessage
        @TruffleBoundary
        Object readMember(String member) {
            return HostObject.forStaticClass(context.findClass(member), context);
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "Static Scope";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ClassNamesObject implements TruffleObject {

        final ArrayList<String> names;

        private ClassNamesObject(Set<String> names) {
            this.names = new ArrayList<>(names);
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (index < 0L || index > Integer.MAX_VALUE) {
                throw InvalidArrayIndexException.create(index);
            }
            try {
                return names.get((int) index);
            } catch (IndexOutOfBoundsException ioob) {
                throw InvalidArrayIndexException.create(index);
            }
        }

        @ExportMessage
        @TruffleBoundary
        long getArraySize() {
            return names.size();
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < getArraySize();
        }
    }

    public void disposeClassLoader() {
        HostClassLoader cl = classloader;
        if (cl != null) {
            try {
                cl.close();
            } catch (IOException e) {
                // lets ignore that
            }
            classloader = null;
        }
    }

}
