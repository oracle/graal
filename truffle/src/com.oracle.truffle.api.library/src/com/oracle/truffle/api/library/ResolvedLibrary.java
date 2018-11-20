/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.library;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.GeneratedBy;

/**
 * Represents a resolved library. Library classes may be resolved using {@link #resolve(Class)}.
 * Resolving a library class into a constant is useful if performance is a critical requirement,
 * otherwise it is recommended to use the static methods in {@link Library} instead.
 * <p>
 * This class also serves as base class for generated library classes. It is only open to allow
 * generated code to implement it. Do not implement this class manually.
 *
 * @see Library#createCached(Class, Object)
 * @see Library#createCachedDispatch(Class, int)
 * @see Library#getUncached(Class, Object)
 * @see Library#getUncachedDispatch(Class)
 * @since 1.0
 */
public abstract class ResolvedLibrary<T extends Library> {

    private static final ConcurrentHashMap<Class<? extends Library>, ResolvedLibrary<?>> LIBRARIES = new ConcurrentHashMap<>();

    private final Class<T> libraryClass;
    private final List<Message> messages;
    private final ConcurrentHashMap<Class<?>, ResolvedExports<T>> exportCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, T> uncachedCache = new ConcurrentHashMap<>();
    private final ProxyExports proxyExports = new ProxyExports();
    final Map<String, Message> nameToMessages;
    private final T uncachedDispatch;

    @SuppressWarnings("unchecked")
    protected ResolvedLibrary(Class<T> libraryClass, List<Message> messages, T uncachedDispatch) {
        assert this.getClass().getName().endsWith("Gen");
        assert this.getClass().getAnnotation(GeneratedBy.class) != null;
        assert this.getClass().getAnnotation(GeneratedBy.class).value() == libraryClass;
        this.libraryClass = libraryClass;
        this.messages = Collections.unmodifiableList(messages);
        Map<String, Message> messagesMap = new LinkedHashMap<>();
        for (Message message : getMessages()) {
            assert message.library == null;
            message.library = (ResolvedLibrary<Library>) this;
            messagesMap.put(message.getSimpleName(), message);
        }
        this.nameToMessages = messagesMap;
        this.uncachedDispatch = uncachedDispatch;
    }

    final Class<T> getLibraryClass() {
        return libraryClass;
    }

    final List<Message> getMessages() {
        return messages;
    }

    /**
     * Returns an uncached and dispatched version of this library.
     *
     * @see Library#getUncachedDispatch(Class) for further details.
     * @since 1.0
     */
    public final T getUncachedDispatch() {
        return uncachedDispatch;
    }

    /**
     * Returns an cached and dispatched version of this library.
     *
     * @see Library#createCachedDispatch(Class, int) for further details.
     * @since 1.0
     */
    public final T createCachedDispatch(int limit) {
        if (limit <= 0) {
            return getUncachedDispatch();
        } else {
            return createCachedDispatchImpl(limit);
        }
    }

    /**
     * Returns an cached and manually dispatched version of this library.
     *
     * @see Library#createCached(Class, Object) for further details.
     * @since 1.0
     */
    public final T createCached(Object receiver) {
        T cached = lookupExport(receiver).createCached(receiver);
        assert cached.accepts(receiver) : String.format("Invalid accepts implementation for receiver class %s.", receiver.getClass());
        assert (cached = createAssertions(cached)) != null;
        return cached;
    }

    /**
     * Returns an cached and manually dispatched version of this library.
     *
     * @see Library#getUncached(Class, Object) for further details.
     * @since 1.0
     */
    public final T getUncached(Object receiver) {
        Class<?> receiverClass = getReceiverClass(receiver);
        T uncached = uncachedCache.get(receiverClass);
        if (uncached != null) {
            assert uncached.accepts(receiver);
            return uncached;
        }
        uncached = lookupExport(receiver).createUncached(receiver);
        assert uncached.accepts(receiver);
        assert (uncached = createAssertions(uncached)) != null;
        uncachedCache.putIfAbsent(receiverClass, uncached);
        return uncached;
    }

    private static Class<?> getReceiverClass(Object receiver) {
        if (receiver instanceof DynamicDispatch) {
            Class<?> receiverClass = ((DynamicDispatch) receiver).dispatch();
            if (receiverClass == null) {
                receiverClass = receiver.getClass();
            }
            return receiverClass;
        } else {
            return receiver.getClass();
        }
    }

    /**
     * Creates a cached dispatched version of this library. An implementation for this method is
     * generated, do not implement manually.
     *
     * @since 1.0
     */
    protected abstract T createCachedDispatchImpl(int limit);

    /**
     * Creates a proxy version of this library. An implementation for this method is generated, do
     * not implement manually.
     *
     * @since 1.0
     */
    protected abstract T createProxy(ReflectionLibrary lib);

    /**
     * Creates an assertion version of this library. An implementation for this method is generated,
     * do not implement manually.
     *
     * @since 1.0
     */
    protected T createAssertions(T delegate) {
        return delegate;
    }

    /**
     * Returns the implementation type that should be used for a given receiver. An implementation
     * for this method is generated, do not implement manually.
     *
     * @since 1.0
     */
    protected abstract Class<?> getDefaultClass(Object receiver);

    /**
     * Performs a generic dispatch for this library. An implementation for this method is generated,
     * do not implement manually.
     *
     * @since 1.0
     */
    protected abstract Object genericDispatch(Library library, Object receiver, Message message, Object[] arguments, int parameterOffset) throws Exception;

    final ResolvedExports<T> lookupExport(Object receiver) {
        Class<?> receiverClass = getReceiverClass(receiver);
        ResolvedExports<T> lib = this.exportCache.get(receiverClass);
        if (lib != null) {
            return lib;
        }
        ResolvedReceiver resolvedLibrary = ResolvedReceiver.lookup(receiverClass);
        lib = resolvedLibrary.getLibrary(libraryClass);
        if (lib == null) {
            if (resolvedLibrary.getLibrary(ReflectionLibrary.class) != null) {
                lib = proxyExports;
            } else {
                Class<?> defaultClass = getDefaultClass(receiver);
                lib = ResolvedReceiver.lookup(defaultClass).getLibrary(libraryClass);
            }
        }

        ResolvedExports<T> concurrent = this.exportCache.putIfAbsent(receiverClass, lib);
        return concurrent != null ? concurrent : lib;
    }

    /**
     * Looks up the resolved library instance for a library class. If a library class was not yet
     * loaded it will be loaded automatically. If the passed library class is not a valid libray
     * then a {@link IllegalArgumentException} is thrown. Resolving a library class into constant is
     * useful if performance is a critical requirement, otherwise it is recommended to use the
     * static methods in {@link Library} instead.
     *
     * @see Library
     * @since 1.0
     */
    @SuppressWarnings("unchecked")
    public static <T extends Library> ResolvedLibrary<T> resolve(Class<T> library) {
        Objects.requireNonNull(library);
        ResolvedLibrary<?> lib = LIBRARIES.get(library);
        if (lib == null) {
            if (!TruffleOptions.AOT) {
                loadGeneratedClass(library);
                lib = LIBRARIES.get(library);
            }
            if (lib == null) {
                throw new IllegalArgumentException(String.format("Class %s is not a registered library.", library.getName()));
            }
        }
        return (ResolvedLibrary<T>) lib;
    }

    static ResolvedLibrary<?> loadGeneratedClass(Class<?> libraryClass) {
        if (Library.class.isAssignableFrom(libraryClass)) {
            String generatedClassName = libraryClass.getPackage().getName() + "." + libraryClass.getSimpleName() + "Gen";
            Class<?> loadedClass;
            try {
                loadedClass = Class.forName(generatedClassName);
            } catch (ClassNotFoundException e) {
                return null;
            }
            ResolvedLibrary<?> lib = LIBRARIES.get(libraryClass);
            if (lib == null) {
                // maybe still initializing?
                boolean isLibrary = ResolvedLibrary.class.isAssignableFrom(loadedClass);
                if (isLibrary) {
                    throw new AssertionError("Recursive initialization detected. Library cannot use itself in a static initializer.");
                }
            }
            return lib;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static ResolvedLibrary<?> resolveLibraryByName(String name) {
        try {
            return resolve((Class<? extends Library>) Class.forName(name));
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    static Message resolveMessage(Class<? extends Library> library, String message) {
        ResolvedLibrary<?> lib = resolve(library);
        if (lib == null) {
            return null;
        } else {
            return lib.nameToMessages.get(message);
        }
    }

    static Message resolveMessage(String library, String message) {
        ResolvedLibrary<?> lib = resolveLibraryByName(library);
        if (lib == null) {
            return null;
        } else {
            return lib.nameToMessages.get(message);
        }
    }

    protected static <T extends Library> void register(Class<T> libraryClass, ResolvedLibrary<T> library) {
        ResolvedLibrary<?> lib = LIBRARIES.putIfAbsent(libraryClass, library);
        if (lib != null) {
            throw new AssertionError("Reflection cannot be installed for a library twice.");
        }
    }

    private static final ResolvedLibrary<ReflectionLibrary> REFLECTION_LIBRARY = ResolvedLibrary.resolve(ReflectionLibrary.class);

    final class ProxyExports extends ResolvedExports<T> {
        protected ProxyExports() {
            super(libraryClass);
        }

        @Override
        public T createUncached(Object receiver) {
            return createProxy(REFLECTION_LIBRARY.getUncached(receiver));
        }

        @Override
        public T createCached(Object receiver) {
            return createProxy(REFLECTION_LIBRARY.createCached(receiver));
        }
    }

}
