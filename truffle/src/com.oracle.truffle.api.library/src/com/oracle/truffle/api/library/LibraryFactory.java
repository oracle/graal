/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.library.LibraryExport.DelegateExport;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.utilities.FinalBitSet;

import sun.misc.Unsafe;

/**
 * Library factories allow to create instances of libraries used to call library messages. A library
 * factory for a library class can be looked up using the static method {@link #resolve(Class)}.
 * <p>
 * Library instances are either <i>automatically dispatched</i> or <i>manually dispatched</i>.
 * Automatically dispatched libraries always return <code>true</code> for
 * {@link Library#accepts(Object)} therefore they can be used with changing receiver values.
 * <p>
 * Manually dispatched libraries are created once for a receiver and are only valid as long as
 * {@link Library#accepts(Object) accepts} returns <code>true</code>. Once accepts was checked for
 * an individual value, it is guaranteed that the accepts continues to return true. It is therefore
 * not necessary to call accepts again for multiple message invocations. To create automatically
 * dispatched versions of libraries use either {@link #createDispatched(int)} or
 * {@link #getUncached()}. For calling manually dispatched libraries it is recommended to use
 * {@link CachedLibrary} instead using the factory manually.
 * <p>
 * Library instances are either <i>cached</i> or <i>uncached</i>. Cached instances are library
 * instances designed to be used in ASTs. Cached instances are typically {@link Node#isAdoptable()
 * adoptable} and store additional profiling information for the cached export. This allows to
 * generate call-site specific profiling information for libray calls. Before a cached instance can
 * be used it must be {@link Node#insert(Node) adopted} by a parent node. Cached instances of
 * libraries have a {@link Node#getCost() cost} of {@link NodeCost#MONOMORPHIC} for each manually
 * cached library.
 * <p>
 * Uncached versions are designed to be used from slow-path runtime methods or whenever call-site
 * specific profiling is not desired. All uncached versions of a library are annotated with
 * {@linkplain TruffleBoundary @TruffleBoundary}. Uncached instances always return
 * <code>false</code> for {@link Node#isAdoptable()}. Uncached instances of libraries have a
 * {@link Node#getCost() cost} of {@link NodeCost#MEGAMORPHIC}.
 * <p>
 * This class is intended to be sub-classed by generated code only. Do not sub-class
 * {@link LibraryFactory} manually.
 *
 * @see Library
 * @see LibraryExport
 * @see CachedLibrary
 * @see Message
 * @since 19.0
 */
public abstract class LibraryFactory<T extends Library> {

    private static final ConcurrentHashMap<Class<?>, LibraryFactory<?>> LIBRARIES;
    private static final DefaultExportProvider[] EMPTY_DEFAULT_EXPORT_ARRAY = new DefaultExportProvider[0];

    static {
        LIBRARIES = new ConcurrentHashMap<>();
    }

    /**
     * Reinitializes the state for native image generation.
     *
     * NOTE: this method is called reflectively by downstream projects.
     */
    @SuppressWarnings("unused")
    private static void reinitializeNativeImageState() {
        for (Map.Entry<Class<?>, LibraryFactory<?>> entry : LIBRARIES.entrySet()) {
            LibraryFactory<?> libraryFactory = entry.getValue();
            /* Trigger re-initialization of default exports. */
            libraryFactory.initDefaultExports();
        }
    }

    /**
     * Resets the state for native image generation.
     *
     * NOTE: this method is called reflectively by downstream projects.
     */
    @SuppressWarnings("unused")
    private static void resetNativeImageState() {
        assert TruffleOptions.AOT : "Only supported during image generation";
        for (Map.Entry<Class<?>, LibraryFactory<?>> entry : LIBRARIES.entrySet()) {
            LibraryFactory<?> libraryFactory = entry.getValue();
            clearNonTruffleClasses(libraryFactory.exportCache);
            clearNonTruffleClasses(libraryFactory.uncachedCache);
            clearNonTruffleClasses(libraryFactory.cachedCache);
            /* Reset the default exports. */
            LibraryFactory.externalDefaultProviders = null;
            libraryFactory.afterBuiltinDefaultExports = null;
            libraryFactory.beforeBuiltinDefaultExports = null;
        }
        clearNonTruffleClasses(LIBRARIES);
        clearNonTruffleClasses(ResolvedDispatch.CACHE);
        clearNonTruffleClasses(ResolvedDispatch.REGISTRY);
    }

    private static void clearNonTruffleClasses(Map<Class<?>, ?> map) {
        Class<?>[] classes = map.keySet().toArray(new Class<?>[0]);
        for (Class<?> clazz : classes) {
            if (LibraryAccessor.jdkServicesAccessor().isNonTruffleClass(clazz)) {
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
    @CompilationFinal private volatile T uncachedDispatch;

    final DynamicDispatchLibrary dispatchLibrary;

    DefaultExportProvider[] beforeBuiltinDefaultExports;
    DefaultExportProvider[] afterBuiltinDefaultExports;

    /**
     * Constructor for generated subclasses. Do not sub-class {@link LibraryFactory} manually.
     *
     * @since 19.0
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
            GenerateLibrary annotation = libraryClass.getAnnotation(GenerateLibrary.class);
            boolean dynamicDispatchEnabled = annotation == null || libraryClass.getAnnotation(GenerateLibrary.class).dynamicDispatchEnabled();
            if (dynamicDispatchEnabled) {
                this.dispatchLibrary = LibraryFactory.resolve(DynamicDispatchLibrary.class).getUncached();
            } else {
                this.dispatchLibrary = null;
            }
        }

        initDefaultExports();
    }

    /** Lazyily init the default exports data structures. */
    private void initDefaultExports() {
        List<DefaultExportProvider> providers = getExternalDefaultProviders().get(libraryClass.getName());
        List<DefaultExportProvider> beforeBuiltin = null;
        List<DefaultExportProvider> afterBuiltin = null;
        if (providers != null && !providers.isEmpty()) {
            for (DefaultExportProvider provider : providers) {
                List<DefaultExportProvider> providerList = new ArrayList<>();
                if (provider.getPriority() > 0) {
                    if (beforeBuiltin == null) {
                        beforeBuiltin = new ArrayList<>();
                    }
                    providerList = beforeBuiltin;
                } else {
                    if (afterBuiltin == null) {
                        afterBuiltin = new ArrayList<>();
                    }
                    providerList = afterBuiltin;
                }
                providerList.add(provider);
            }
        }

        if (beforeBuiltin != null) {
            beforeBuiltinDefaultExports = beforeBuiltin.toArray(new DefaultExportProvider[beforeBuiltin.size()]);
        } else {
            beforeBuiltinDefaultExports = EMPTY_DEFAULT_EXPORT_ARRAY;
        }

        if (afterBuiltin != null) {
            afterBuiltinDefaultExports = afterBuiltin.toArray(new DefaultExportProvider[afterBuiltin.size()]);
        } else {
            afterBuiltinDefaultExports = EMPTY_DEFAULT_EXPORT_ARRAY;
        }
    }

    /**
     * Creates a new cached and automatically dispatched library given a limit. The limit specifies
     * the number of cached instances that will be automatically dispatched until the dispatched
     * library rewrites itself to an uncached version of the library. If the limit is zero then the
     * library will use an uncached version from the start. Negative values will throw an
     * {@link IllegalArgumentException}. It is discouraged to use {@link Integer#MAX_VALUE} as
     * parameter to this method. Reasonable values for the limit range from zero to ten.
     * <p>
     * If possible it is recommended to not use this method manually but to use
     * {@link CachedLibrary} instead.
     * <p>
     * Whenever the limit is reached for a node and the uncached version is in use, the current
     * enclosing node will be available to the uncached library export of the library using
     * {@link EncapsulatingNodeReference}.
     *
     * @see EncapsulatingNodeReference
     * @see CachedLibrary
     * @since 19.0
     */
    @TruffleBoundary
    public final T createDispatched(int limit) {
        if (limit <= 0) {
            return getUncached();
        } else {
            ensureLibraryInitialized();
            return createDispatchImpl(limit);
        }
    }

    /**
     * Creates a new manually dispatched cached library for a given receiver. The receiver must not
     * be <code>null</code>. The returned library must be adopted before used. For calling manually
     * dispatched libraries it is recommended to use {@link CachedLibrary} instead using the factory
     * manually.
     *
     * @see CachedLibrary
     * @since 19.0
     */
    @TruffleBoundary
    public final T create(Object receiver) {
        Class<?> dispatchClass = dispatch(receiver);
        T cached = cachedCache.get(dispatchClass);
        if (cached != null) {
            assert validateExport(receiver, dispatchClass, cached);
            return cached;
        }
        ensureLibraryInitialized();
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

    /**
     * Returns an uncached automatically dispatched version of the library. This is version of a
     * library is used for slow-path calls.
     *
     * @since 19.0
     */
    public final T getUncached() {
        T dispatch = this.uncachedDispatch;
        if (dispatch == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            ensureLibraryInitialized();
            dispatch = createUncachedDispatch();
            T otherDispatch = this.uncachedDispatch;
            if (otherDispatch != null) {
                dispatch = otherDispatch;
            } else {
                this.uncachedDispatch = dispatch;
            }
        }
        return dispatch;
    }

    private void ensureLibraryInitialized() {
        CompilerAsserts.neverPartOfCompilation();
        /*
         * This is needed to enforce initialization order. This way the library class is always
         * initialized before any of the export subclasses. So this method must be invoked before
         * any instantiation of a library export.
         */
        UNSAFE.ensureClassInitialized(libraryClass);
    }

    /**
     * Returns an uncached manually dispatched library for a given receiver. The receiver must not
     * be <code>null</code>.
     *
     * @see CachedLibrary
     * @since 19.0
     */
    @TruffleBoundary
    public final T getUncached(Object receiver) {
        Class<?> dispatchClass = dispatch(receiver);
        T uncached = uncachedCache.get(dispatchClass);
        if (uncached != null) {
            assert validateExport(receiver, dispatchClass, uncached);
            return uncached;
        }
        ensureLibraryInitialized();
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

    private static volatile Map<String, List<DefaultExportProvider>> externalDefaultProviders;

    private static Map<String, List<DefaultExportProvider>> getExternalDefaultProviders() {
        Map<String, List<DefaultExportProvider>> providers = externalDefaultProviders;
        if (providers == null) {
            synchronized (LibraryFactory.class) {
                providers = externalDefaultProviders;
                if (providers == null) {
                    providers = loadExternalDefaultProviders();
                }
            }
        }
        return providers;
    }

    private static Map<String, List<DefaultExportProvider>> loadExternalDefaultProviders() {
        Map<String, List<DefaultExportProvider>> providers;
        providers = new LinkedHashMap<>();
        for (DefaultExportProvider provider : LibraryAccessor.engineAccessor().loadServices(DefaultExportProvider.class)) {
            String libraryClassName = provider.getLibraryClassName();
            List<DefaultExportProvider> providerList = providers.get(libraryClassName);
            if (providerList == null) {
                providerList = new ArrayList<>();
                providers.put(libraryClassName, providerList);
            }
            providerList.add(provider);
        }
        for (List<DefaultExportProvider> providerList : providers.values()) {
            Collections.sort(providerList, new Comparator<DefaultExportProvider>() {
                public int compare(DefaultExportProvider o1, DefaultExportProvider o2) {
                    return Integer.compare(o2.getPriority(), o1.getPriority());
                }
            });
        }
        return providers;
    }

    final Class<T> getLibraryClass() {
        return libraryClass;
    }

    final List<Message> getMessages() {
        return messages;
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
     * Creates a cached automatically dispatched version of this library. An implementation for this
     * method is generated, do not implement or call manually.
     *
     * @since 19.0
     */
    protected abstract T createDispatchImpl(int limit);

    /***
     * Creates a uncached automatically dispatched version of this library. An implementation for
     * this method is generated, do not implement or call manually.
     *
     * @since 19.0
     */
    protected abstract T createUncachedDispatch();

    /**
     * Creates a proxy version of this library. A proxy version is responsible for dispatching to
     * reflective implementations of messages. An implementation for this method is generated, do
     * not implement manually.
     *
     * @since 19.0
     */
    protected abstract T createProxy(ReflectionLibrary lib);

    /**
     * Creates a delegate version of a library. May be used for cached or uncached versions of a
     * library. Intended to be used by generated code only, do not use manually.
     *
     * @since 20.0
     */
    protected T createDelegate(T original) {
        return original;
    }

    /**
     * Creates an assertion version of this library. An implementation for this method is generated,
     * do not implement manually.
     *
     * @since 19.0
     */
    protected T createAssertions(T delegate) {
        return delegate;
    }

    /**
     * Returns default exported used for a given receiver. An implementation for this method is
     * generated, do not implement manually.
     *
     * @since 19.0
     */
    protected abstract Class<?> getDefaultClass(Object receiver);

    private Class<?> getDefaultClassImpl(Object receiver) {
        for (DefaultExportProvider defaultExport : beforeBuiltinDefaultExports) {
            if (defaultExport.getReceiverClass().isInstance(receiver)) {
                return defaultExport.getDefaultExport();
            }
        }

        Class<?> defaultClass = getDefaultClass(receiver);
        if (defaultClass != getLibraryClass()) {
            return defaultClass;
        }

        for (DefaultExportProvider defaultExport : afterBuiltinDefaultExports) {
            if (defaultExport.getReceiverClass().isInstance(receiver)) {
                return defaultExport.getDefaultExport();
            }
        }

        return defaultClass;
    }

    /**
     * Performs a generic dispatch for this library. An implementation for this method is generated,
     * do not implement manually.
     *
     * @since 19.0
     */
    protected abstract Object genericDispatch(Library library, Object receiver, Message message, Object[] arguments, int parameterOffset) throws Exception;

    /**
     * Creates a final bitset of the given messages. Uses an internal index for the messages. An
     * implementation for this method is generated, do not implement manually.
     *
     * @since 20.0
     */
    protected FinalBitSet createMessageBitSet(@SuppressWarnings({"unused", "hiding"}) Message... enabledMessages) {
        throw shouldNotReachHere("should be generated");
    }

    /**
     * Returns <code>true</code> if a message is delegated, otherwise <code>false</code>. Intended
     * to be used by generated code only, do not use manually.
     *
     * @since 20.0
     */
    protected static boolean isDelegated(Library lib, int index) {
        boolean result = ((DelegateExport) lib).getDelegateExportMessages().get(index);
        CompilerAsserts.partialEvaluationConstant(result);
        return !result;
    }

    /**
     * Reads the delegate for a receiver. Intended to be used by generated code only, do not use
     * manually.
     *
     * @since 20.0
     */
    protected static Object readDelegate(Library lib, Object receiver) {
        return ((DelegateExport) lib).readDelegateExport(receiver);
    }

    /**
     * Returns the delegated library to use when messages are delegated. Intended to be used by
     * generated code only, do not use manually.
     *
     * @since 20.0
     */
    @SuppressWarnings("unchecked")
    protected static <T extends Library> T getDelegateLibrary(T lib, Object delegate) {
        return (T) ((DelegateExport) lib).getDelegateExportLibrary(delegate);
    }

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
                Class<?> defaultClass = getDefaultClassImpl(receiver);
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
            throw shouldNotReachHere(
                            String.format("Receiver class %s was dynamically dispatched to incompatible exports %s. Expected receiver class %s.",
                                            receiver.getClass().getName(), dispatchedClass.getName(), exports.getReceiverClass().getName()));
        }
    }

    /**
     * Looks up the resolved library instance for a library class. If a library class was not yet
     * loaded it will be initialized automatically. If the passed library class is not a valid
     * library then a {@link IllegalArgumentException} is thrown.
     *
     * @see Library
     * @since 19.0
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

    private static final sun.misc.Unsafe UNSAFE;

    static {
        Unsafe unsafe;
        try {
            unsafe = Unsafe.getUnsafe();
        } catch (SecurityException e) {
            try {
                Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafeInstance.setAccessible(true);
                unsafe = (Unsafe) theUnsafeInstance.get(Unsafe.class);
            } catch (Exception e2) {
                throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e2);
            }
        }
        UNSAFE = unsafe;
    }

    static LibraryFactory<?> loadGeneratedClass(Class<?> libraryClass) {
        if (Library.class.isAssignableFrom(libraryClass)) {
            String generatedClassName = libraryClass.getPackage().getName() + "." + libraryClass.getSimpleName() + "Gen";
            try {
                Class.forName(generatedClassName, true, libraryClass.getClassLoader());
            } catch (ClassNotFoundException e) {
                return null;
            }
            return LIBRARIES.get(libraryClass);
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
     * @since 19.0
     */
    protected static <T extends Library> void register(Class<T> libraryClass, LibraryFactory<T> library) {
        LibraryFactory<?> lib = LIBRARIES.putIfAbsent(libraryClass, library);
        if (lib != null) {
            throw shouldNotReachHere("Reflection cannot be installed for a library twice.");
        }
    }

    /***
     * {@inheritDoc}
     *
     * @since 19.0
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
        protected T createCached(Object receiver) {
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
                    throw shouldNotReachHere(String.format("Libraries for class '%s' could not be resolved. Not registered?", dispatchClass.getName()));
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
                throw shouldNotReachHere(String.format("Generated class '%s' for class '%s' not found. " +
                                "Did the Truffle annotation processor run?", generatedClassName, currentReceiverClass.getName()), e);
            }
        }

    }

}
