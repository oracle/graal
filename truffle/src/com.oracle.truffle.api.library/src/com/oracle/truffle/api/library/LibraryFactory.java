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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.GeneratedBy;

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

    private static final ConcurrentHashMap<Class<?>, LibraryFactory<?>> LIBRARIES;

    static {
        LIBRARIES = new ConcurrentHashMap<>();
    }

    /**
     * Resets the state for native image generation.
     *
     * NOTE: this method is called reflectively by downstream projects.
     */
    @SuppressWarnings("unused")
    private static void resetNativeImageState() {
        assert TruffleOptions.AOT : "Only supported during image generation";
        clearNonTruffleClasses(LIBRARIES);
        clearNonTruffleClasses(ResolvedDispatch.CACHE);
        clearNonTruffleClasses(ResolvedDispatch.REGISTRY);
    }

    private static void clearNonTruffleClasses(Map<Class<?>, ?> map) {
        Class<?>[] classes = map.keySet().toArray(new Class[0]);
        for (Class<?> clazz : classes) {
            // classes on the boot loader should not be cleared
            if (clazz.getClassLoader() != null) {
                map.remove(clazz);
            }
        }
    }

    private final Class<T> libraryClass;
    private final List<Message> messages;
    private final ConcurrentHashMap<Class<?>, LibraryExport<T>> exportCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, T> uncachedCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, T> cachedCache = new ConcurrentHashMap<>();
    private final ProxyExports proxyExports = new ProxyExports();
    final Map<String, Message> nameToMessages;
    @CompilationFinal private T uncachedDispatch;

    final DynamicDispatchLibrary dispatchLibrary;

    /**
     * @since 1.0
     */
    @SuppressWarnings("unchecked")
    protected LibraryFactory(Class<T> libraryClass, List<Message> messages) {
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
        if (libraryClass == DynamicDispatchLibrary.class) {
            this.dispatchLibrary = null;
        } else {
            this.dispatchLibrary = LibraryFactory.resolve(DynamicDispatchLibrary.class).getUncached();
        }
    }

    final void ensureInitialized() {
        if (this.uncachedDispatch == null) {
            this.uncachedDispatch = createUncachedDispatch();
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
    @TruffleBoundary
    public final T createDispatched(int limit) {
        if (limit <= 0) {
            return getUncached();
        } else {
            return createDispatchImpl(limit);
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
    @TruffleBoundary
    public final T create(Object receiver) {
        Class<?> dispatchClass = dispatch(receiver);
        T cached = cachedCache.get(dispatchClass);
        if (cached != null) {
            assert validateExport(receiver, dispatchClass, cached);
            return cached;
        }
        LibraryExport<T> export = lookupExport(receiver, dispatchClass);
        cached = export.createCached(receiver);
        assert (cached = createAssertionsImpl(export, cached)) != null;
        if (!cached.isAdoptable()) {
            assert cached.accepts(receiver) : String.format("Invalid accepts implementation detected in '%s'", dispatchClass.getName());
            T otherCached = cachedCache.putIfAbsent(dispatchClass, cached);
            if (otherCached != null) {
                return otherCached;
            }
        }
        return cached;
    }

    private T createAssertionsImpl(LibraryExport<T> export, T cached) {
        if (needsAssertions(export)) {
            return createAssertions(cached);
        } else {
            return cached;
        }
    }

    private boolean needsAssertions(LibraryExport<T> export) {
        Class<?> registerClass = export.registerClass;
        if (export.isDefaultExport() && registerClass != null && registerClass.getName().equals("com.oracle.truffle.api.interop.DefaultTruffleObjectExports")) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Returns an uncached and specialized version of the library.
     *
     * Returns an cached and manually dispatched version of this library.
     *
     * @since 1.0
     */
    @TruffleBoundary
    public final T getUncached(Object receiver) {
        Class<?> dispatchClass = dispatch(receiver);
        T uncached = uncachedCache.get(dispatchClass);
        if (uncached != null) {
            assert validateExport(receiver, dispatchClass, uncached);
            return uncached;
        }
        LibraryExport<T> export = lookupExport(receiver, dispatchClass);
        uncached = export.createUncached(receiver);
        assert validateExport(receiver, dispatchClass, uncached);
        assert uncached.accepts(receiver);
        assert (uncached = createAssertionsImpl(export, uncached)) != null;
        T otherUncached = uncachedCache.putIfAbsent(dispatchClass, uncached);
        if (otherUncached != null) {
            return otherUncached;
        }
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
    protected abstract T createDispatchImpl(int limit);

    /***
     * @since 1.0
     */
    protected abstract T createUncachedDispatch();

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
    @TruffleBoundary
    public static <T extends Library> LibraryFactory<T> resolve(Class<T> library) {
        Objects.requireNonNull(library);
        return resolveImpl(library, true);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Library> LibraryFactory<T> resolveImpl(Class<T> library, boolean fail) {
        LibraryFactory<?> lib = LIBRARIES.get(library);
        if (lib == null) {
            loadGeneratedClass(library);
            lib = LIBRARIES.get(library);
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
            try {
                Class.forName(generatedClassName, true, libraryClass.getClassLoader());
            } catch (ClassNotFoundException e) {
                return null;
            }
            LibraryFactory<?> lib = LIBRARIES.get(libraryClass);
            if (lib == null) {
                // maybe still initializing?
                return null;
            } else {
                lib.ensureInitialized();
            }
            return lib;
        }
        return null;
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

    /**
     * @since 1.0
     */
    protected static <T extends Library> void register(Class<T> libraryClass, LibraryFactory<T> library) {
        LibraryFactory<?> lib = LIBRARIES.putIfAbsent(libraryClass, library);
        if (lib != null) {
            throw new AssertionError("Reflection cannot be installed for a library twice.");
        }
    }

    /***
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    public String toString() {
        return "LibraryFactory [library=" + libraryClass.getName() + "]";
    }

    final class ProxyExports extends LibraryExport<T> {
        protected ProxyExports() {
            super(libraryClass, Object.class, true);
        }

        @Override
        public T createUncached(Object receiver) {
            return createProxy(ReflectionLibrary.getFactory().getUncached(receiver));
        }

        @Override
        public T createCached(Object receiver) {
            return createProxy(ReflectionLibrary.getFactory().create(receiver));
        }
    }

    /**
     * Helper class representing a single resolved receiver class that exports multiple libraries.
     */
    static final class ResolvedDispatch {

        private static final ConcurrentHashMap<Class<?>, ResolvedDispatch> CACHE = new ConcurrentHashMap<>();
        private static final ConcurrentHashMap<Class<?>, LibraryExport<?>[]> REGISTRY = new ConcurrentHashMap<>();

        // the root of every receiver class chain.
        private static final ResolvedDispatch OBJECT_RECEIVER = new ResolvedDispatch(null, Object.class);
        private final ResolvedDispatch parent;
        private final Class<?> dispatchClass;
        private final Map<Class<?>, LibraryExport<?>> libraries;

        @SuppressWarnings({"hiding", "unchecked"})
        private ResolvedDispatch(ResolvedDispatch parent, Class<?> dispatchClass, LibraryExport<?>... libs) {
            this.parent = parent;
            this.dispatchClass = dispatchClass;
            Map<Class<?>, LibraryExport<?>> libraries = new LinkedHashMap<>();
            for (LibraryExport<?> lib : libs) {
                libraries.put(lib.getLibrary(), lib);
            }
            this.libraries = libraries;
        }

        @SuppressWarnings("unchecked")
        <T extends Library> LibraryExport<T> getLibrary(Class<T> libraryClass) {
            LibraryExport<?> lib = libraries.get(libraryClass);
            if (lib == null && parent != null) {
                lib = parent.getLibrary(libraryClass);
            }
            return (LibraryExport<T>) lib;
        }

        @TruffleBoundary
        static ResolvedDispatch lookup(Class<?> receiverClass) {
            ResolvedDispatch type = CACHE.get(receiverClass);
            if (type == null) {
                type = resolveClass(receiverClass);
            }
            return type;
        }

        static <T extends Library> void register(Class<?> receiverClass, LibraryExport<?>... libs) {
            for (LibraryExport<?> lib : libs) {
                lib.registerClass = receiverClass;
            }
            LibraryExport<?>[] prevLibs = REGISTRY.put(receiverClass, libs);
            if (prevLibs != null) {
                throw new IllegalStateException("Receiver " + receiverClass + " is already registered.");
            }
            // eagerly resolve known receivers in AOT mode
            if (TruffleOptions.AOT) {
                lookup(receiverClass);
            }
        }

        @Override
        public String toString() {
            return "ResolvedDispatch[" + dispatchClass.getName() + "]";
        }

        Set<Class<?>> getLibraries() {
            return libraries.keySet();
        }

        private static boolean hasExports(Class<?> c) {
            return c.getAnnotationsByType(ExportLibrary.class).length > 0;
        }

        private static ResolvedDispatch resolveClass(Class<?> dispatchClass) {
            if (dispatchClass == null) {
                return OBJECT_RECEIVER;
            }
            ResolvedDispatch parent = resolveClass(dispatchClass.getSuperclass());
            ResolvedDispatch resolved;
            LibraryExport<?>[] libs = REGISTRY.get(dispatchClass);
            if (libs == null && hasExports(dispatchClass)) {
                /*
                 * We can omit loading classes in AOT mode as they are resolved eagerly using the
                 * TruffleFeature. We can also omit if the type was already resolved.
                 */
                loadGeneratedClass(dispatchClass);
                libs = REGISTRY.get(dispatchClass);
                if (libs == null) {
                    throw new AssertionError(String.format("Libraries for class '%s' could not be resolved. Not registered?", dispatchClass.getName()));
                }
            }

            if (libs != null) {
                resolved = new ResolvedDispatch(parent, dispatchClass, libs);
            } else {
                resolved = parent;
            }

            ResolvedDispatch concurrent = CACHE.putIfAbsent(dispatchClass, resolved);
            if (concurrent != null) {
                return concurrent;
            } else {
                return resolved;
            }
        }

        static void loadGeneratedClass(Class<?> currentReceiverClass) {
            String generatedClassName = currentReceiverClass.getPackage().getName() + "." + currentReceiverClass.getSimpleName() + "Gen";
            try {
                Class.forName(generatedClassName, true, currentReceiverClass.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(String.format("Generated class '%s' for class '%s' not found. " +
                                "Did the Truffle annotation processor run?", generatedClassName, currentReceiverClass.getName()), e);
            }
        }

    }

}
