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
import com.oracle.truffle.api.nodes.NodeUtil;

/**
 * Represents a library factory that allows to create library instances to perform the Truffle
 * library dispatch. Dispatch factory for a library class can be resolved using
 * {@link #resolve(Class)}.
 * <p>
 * This class also serves as base class for generated library classes. It is only sub classable to
 * allow generated code to implement it. Do not implement this class manually.
 *
 * @since 1.0
 */
public abstract class LibraryFactory<T extends Library> {

    private static final ConcurrentHashMap<Class<? extends Library>, LibraryFactory<?>> LIBRARIES = new ConcurrentHashMap<>();

    private final Class<T> libraryClass;
    private final List<Message> messages;
    private final ConcurrentHashMap<Class<?>, LibraryExport<T>> exportCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, T> uncachedCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, T> cachedCache = new ConcurrentHashMap<>();
    private final ProxyExports proxyExports = new ProxyExports();
    final Map<String, Message> nameToMessages;
    private final T uncachedDispatch;

    final DynamicDispatchLibrary dispatchLibrary;

    @SuppressWarnings("unchecked")
    protected LibraryFactory(Class<T> libraryClass, List<Message> messages, T uncachedDispatch) {
        assert this.getClass().getName().endsWith(LibraryExport.GENERATED_CLASS_SUFFIX);
        assert this.getClass().getAnnotation(GeneratedBy.class) != null;
        assert this.getClass().getAnnotation(GeneratedBy.class).value() == libraryClass;
        this.libraryClass = libraryClass;
        this.messages = Collections.unmodifiableList(messages);
        Map<String, Message> messagesMap = new LinkedHashMap<>();
        for (Message message : getMessages()) {
            assert message.library == null;
            message.library = (LibraryFactory<Library>) this;
            messagesMap.put(message.getSimpleName(), message);
        }
        this.nameToMessages = messagesMap;
        this.uncachedDispatch = uncachedDispatch;
        if (libraryClass == DynamicDispatchLibrary.class) {
            this.dispatchLibrary = null;
        } else {
            this.dispatchLibrary = LibraryFactory.resolve(DynamicDispatchLibrary.class).getUncached();
        }
    }

    final Class<T> getLibraryClass() {
        return libraryClass;
    }

    final List<Message> getMessages() {
        return messages;
    }

    /**
     * Returns an uncached and internally dispatched variant of this library. The value of this
     * method can safely be cached in static constants.
     *
     * @since 1.0
     */
    public final T getUncached() {
        return uncachedDispatch;
    }

    /**
     * Create a new cached dispatch of the library.
     *
     * @since 1.0
     */
    public final T createCachedLimit(int limit) {
        if (limit <= 0) {
            return getUncached();
        } else {
            return createCachedDispatchImpl(limit);
        }
    }

    /**
     * Creates a new cached library given a receiver. The returned library implementation only works
     * with the provided receiver or for other receivers that are {@link Library#accepts(Object)
     * accepted} by the returned library. This method is rarely used directly. Use the
     * {@link CachedLibrary} annotation in specializations instead.
     * <p>
     *
     * @see CachedLibrary
     * @since 1.0
     */
    public final T createCached(Object receiver) {
        Class<?> dispatchClass = dispatch(receiver);
        T cached = cachedCache.get(dispatchClass);
        if (cached != null) {
            assert validateExport(receiver, dispatchClass, cached);
            return cached;
        }
        LibraryExport<T> exports = lookupExport(receiver, dispatchClass);
        cached = exports.createCached(receiver);
        assert (cached = createAssertions(cached)) != null;
        if (!NodeUtil.isAdoptable(cached)) {
            assert cached.accepts(receiver) : String.format("Invalid accepts implementation detected in '%s'", dispatchClass.getName());
            cachedCache.putIfAbsent(dispatchClass, cached);
        }
        return cached;
    }

    /**
     * Returns an cached and manually dispatched version of this library.
     *
     * @see Library#getUncached(Class, Object) for further details.
     * @since 1.0
     */
    public final T getUncached(Object receiver) {
        Class<?> dispatchClass = dispatch(receiver);
        T uncached = uncachedCache.get(dispatchClass);
        if (uncached != null) {
            assert validateExport(receiver, dispatchClass, uncached);
            return uncached;
        }
        uncached = lookupExport(receiver, dispatchClass).createUncached(receiver);
        assert validateExport(receiver, dispatchClass, uncached);
        assert uncached.accepts(receiver);
        assert (uncached = createAssertions(uncached)) != null;
        uncachedCache.putIfAbsent(dispatchClass, uncached);
        return uncached;
    }

    private boolean validateExport(Object receiver, Class<?> dispatchClass, T library) {
        validateExport(receiver, dispatchClass, lookupExport(receiver, dispatchClass));

        // this last check should only be a sanity check and not trigger in practice
        assert library.accepts(receiver) : library.getClass().getName();
        return true;
    }

    private Class<?> dispatch(Object receiver) {
        if (receiver == null) {
            throw new NullPointerException("Null receiver values are not supported by libraries.");
        }
        if (dispatchLibrary == null) {
            return receiver.getClass();
        } else {
            Class<?> dispatch = dispatchLibrary.dispatch(receiver);
            if (dispatch == null) {
                return receiver.getClass();
            }
            return dispatch;
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
     * Creates an assertion version of this library. An implementation for this method is generated.
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

    final LibraryExport<T> lookupExport(Object receiver, Class<?> dispatchedClass) {
        LibraryExport<T> lib = this.exportCache.get(dispatchedClass);
        if (lib != null) {
            return lib;
        }
        ResolvedDispatch resolvedLibrary = ResolvedDispatch.lookup(dispatchedClass);
        lib = resolvedLibrary.getLibrary(libraryClass);

        if (lib == null) {
            // dynamic dispatch cannot be reflected. it is not supported.
            if (libraryClass != DynamicDispatchLibrary.class && resolvedLibrary.getLibrary(ReflectionLibrary.class) != null) {
                lib = proxyExports;
            } else {
                Class<?> defaultClass = getDefaultClass(receiver);
                lib = ResolvedDispatch.lookup(defaultClass).getLibrary(libraryClass);
            }
        } else {
            assert !lib.isDefaultExport() : String.format("Dynamic dispatch from receiver class '%s' to default export '%s' detected. " +
                            "Use null instead to dispatch to a default export.", receiver.getClass().getName(), dispatchedClass.getName());
            validateExport(receiver, dispatchedClass, lib);
        }

        LibraryExport<T> concurrent = this.exportCache.putIfAbsent(dispatchedClass, lib);
        return concurrent != null ? concurrent : lib;
    }

    private void validateExport(Object receiver, Class<?> dispatchedClass, LibraryExport<T> exports) throws AssertionError {
        if (!exports.getReceiverClass().isInstance(receiver)) {
            throw new AssertionError(
                            String.format("Receiver class %s was dynamically dispatched to incompatible exports %s. Expected receiver class %s.",
                                            receiver.getClass().getName(), dispatchedClass.getName(), exports.getReceiverClass().getName()));
        }
    }

    /**
     * Looks up the resolved library instance for a library class. If a library class was not yet
     * loaded it will be intialized automatically. If the passed library class is not a valid
     * library then a {@link IllegalArgumentException} is thrown. Resolving a library class into
     * constant is useful if performance is a critical requirement, otherwise it is recommended to
     * use the static methods in {@link Library} instead.
     *
     * @see Library
     * @since 1.0
     */
    public static <T extends Library> LibraryFactory<T> resolve(Class<T> library) {
        Objects.requireNonNull(library);
        return resolveImpl(library, true);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Library> LibraryFactory<T> resolveImpl(Class<T> library, boolean fail) {
        LibraryFactory<?> lib = LIBRARIES.get(library);
        if (lib == null) {
            if (!TruffleOptions.AOT) {
                loadGeneratedClass(library);
                lib = LIBRARIES.get(library);
            }
            if (lib == null) {
                if (fail) {
                    throw new IllegalArgumentException(
                                    String.format("Class '%s' is not a registered library. Truffle libraries must be annotated with @%s to be registered. Did the Truffle annotation processor run?",
                                                    library.getName(),
                                                    GenerateLibrary.class.getSimpleName()));
                }
                return null;
            }
        }
        return (LibraryFactory<T>) lib;
    }

    static LibraryFactory<?> loadGeneratedClass(Class<?> libraryClass) {
        if (Library.class.isAssignableFrom(libraryClass)) {
            String generatedClassName = libraryClass.getPackage().getName() + "." + libraryClass.getSimpleName() + "Gen";
            Class<?> loadedClass;
            try {
                loadedClass = Class.forName(generatedClassName);
            } catch (ClassNotFoundException e) {
                return null;
            }
            LibraryFactory<?> lib = LIBRARIES.get(libraryClass);
            if (lib == null) {
                // maybe still initializing?
                boolean isLibrary = LibraryFactory.class.isAssignableFrom(loadedClass);
                if (isLibrary) {
                    throw new AssertionError("Recursive initialization detected. Library cannot use itself in a static initializer.");
                }
            }
            return lib;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static LibraryFactory<?> resolveLibraryByName(String name, boolean fail) {
        try {
            return resolveImpl((Class<? extends Library>) Class.forName(name), fail);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    static Message resolveMessage(Class<? extends Library> library, String message, boolean fail) {
        Objects.requireNonNull(message);
        LibraryFactory<?> lib = resolveImpl(library, fail);
        if (lib == null) {
            assert !fail;
            return null;
        }
        return resolveLibraryMessage(lib, message, fail);
    }

    private static Message resolveLibraryMessage(LibraryFactory<?> lib, String message, boolean fail) {
        Message foundMessage = lib.nameToMessages.get(message);
        if (fail && foundMessage == null) {
            throw new IllegalArgumentException(String.format("Unknown message '%s' for library '%s' specified.", message, lib.getLibraryClass().getName()));
        }
        return foundMessage;
    }

    static Message resolveMessage(String library, String message, boolean fail) {
        Objects.requireNonNull(message);
        LibraryFactory<?> lib = resolveLibraryByName(library, fail);
        if (lib == null) {
            if (fail) {
                throw new IllegalArgumentException(String.format("Unknown library '%s' specified.", library));
            }
            return null;
        } else {
            return resolveLibraryMessage(lib, message, fail);
        }
    }

    protected static <T extends Library> void register(Class<T> libraryClass, LibraryFactory<T> library) {
        LibraryFactory<?> lib = LIBRARIES.putIfAbsent(libraryClass, library);
        if (lib != null) {
            throw new AssertionError("Reflection cannot be installed for a library twice.");
        }
    }

    @Override
    public String toString() {
        return "LibraryFactory [library=" + libraryClass.getName() + "]";
    }

    private static final LibraryFactory<ReflectionLibrary> REFLECTION_FACTORY = LibraryFactory.resolve(ReflectionLibrary.class);

    final class ProxyExports extends LibraryExport<T> {
        protected ProxyExports() {
            super(libraryClass, Object.class, true);
        }

        @Override
        public T createUncached(Object receiver) {
            return createProxy(REFLECTION_FACTORY.getUncached(receiver));
        }

        @Override
        public T createCached(Object receiver) {
            return createProxy(REFLECTION_FACTORY.createCached(receiver));
        }
    }

}
