/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostAccess;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.host.HostAdapterFactory.AdapterResult;
import com.oracle.truffle.host.HostContextFactory.ToGuestValueNodeGen;
import com.oracle.truffle.host.HostLanguage.HostLanguageException;

final class HostContext {

    @CompilationFinal Object internalContext;
    final Map<String, Class<?>> classCache = new HashMap<>();
    final Object topScope = new TopScopeObject(this);
    volatile HostClassLoader classloader;
    final HostLanguage language;
    private ClassLoader contextClassLoader;
    private Predicate<String> classFilter;
    private boolean hostClassLoadingAllowed;
    private boolean hostLookupAllowed;
    final TruffleLanguage.Env env;
    final AbstractHostAccess access;

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

    HostContext(HostLanguage hostLanguage, TruffleLanguage.Env env) {
        this.language = hostLanguage;
        this.access = hostLanguage.access;
        this.env = env;
    }

    /*
     * This method is invoked once during normal creation and then again after context
     * preinitialization.
     */
    @SuppressWarnings("hiding")
    void initialize(Object internalContext, ClassLoader cl, Predicate<String> clFilter, boolean hostCLAllowed, boolean hostLookupAllowed) {
        if (classloader != null && this.classFilter != null || this.hostClassLoadingAllowed || this.hostLookupAllowed) {
            throw new AssertionError("must not be used during context preinitialization");
        }
        this.internalContext = internalContext;
        this.contextClassLoader = cl;
        this.classFilter = clFilter;
        this.hostClassLoadingAllowed = hostCLAllowed;
        this.hostLookupAllowed = hostLookupAllowed;
    }

    public HostClassCache getHostClassCache() {
        return language.hostClassCache;
    }

    GuestToHostCodeCache getGuestToHostCache() {
        GuestToHostCodeCache cache = (GuestToHostCodeCache) HostAccessor.ENGINE.getGuestToHostCodeCache(internalContext);
        if (cache == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            cache = (GuestToHostCodeCache) HostAccessor.ENGINE.installGuestToHostCodeCache(internalContext, new GuestToHostCodeCache(language));
        }
        return cache;
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
            Object currentModule = HostAccessor.JDKSERVICES.getUnnamedModule(classLoader);
            if (HostAccessor.JDKSERVICES.verifyModuleVisibility(currentModule, foundClass)) {
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

    /**
     * Performs necessary conversions for exceptions coming from the polyglot embedding API and
     * thrown to the language or engine. The conversion must happen exactly once per API call, that
     * is why this coercion should only be used in the catch block at the outermost API call.
     */
    @TruffleBoundary
    <T extends Throwable> RuntimeException hostToGuestException(T e) {
        assert !(e instanceof HostException) : "host exceptions not expected here";

        if (e instanceof ThreadDeath) {
            throw (ThreadDeath) e;
        } else if (e instanceof PolyglotException) {
            // this will rethrow if the guest exception in the polyglot exception can be rethrown.
            language.access.rethrowPolyglotException(internalContext, (PolyglotException) e);

            // fall-through and treat it as any other host exception
        }
        try {
            return new HostException(e, this);
        } catch (StackOverflowError stack) {
            /*
             * Cannot create a new host exception. Use a readily prepared instance.
             */
            return stackoverflowError;
        }
    }

    Value asValue(Node node, Object value) {
        // make language lookup fold if possible
        HostLanguage l = HostLanguage.get(node);
        return l.access.toValue(internalContext, value);
    }

    Object toGuestValue(Class<?> receiver) {
        return HostObject.forClass(receiver, this);
    }

    Object toGuestValue(Node node, Object hostValue) {
        HostLanguage l = HostLanguage.get(node);
        HostContext context = HostContext.get(node);
        assert context == this;
        Object result = l.access.toGuestValue(context.internalContext, hostValue);
        return l.service.toGuestValue(context, result, false);
    }

    static boolean isGuestPrimitive(Object receiver) {
        return receiver instanceof Integer || receiver instanceof Double //
                        || receiver instanceof Long || receiver instanceof Float //
                        || receiver instanceof Boolean || receiver instanceof Character //
                        || receiver instanceof Byte || receiver instanceof Short //
                        || receiver instanceof String || receiver instanceof TruffleString;
    }

    private static final ContextReference<HostContext> REFERENCE = ContextReference.create(HostLanguage.class);

    static HostContext get(Node node) {
        return REFERENCE.get(node);
    }

    @GenerateUncached
    abstract static class ToGuestValueNode extends Node {

        abstract Object execute(HostContext context, Object receiver);

        @Specialization(guards = "receiver == null")
        Object doNull(HostContext context, @SuppressWarnings("unused") Object receiver) {
            return context.toGuestValue(this, receiver);
        }

        @Specialization(guards = {"receiver != null", "receiver.getClass() == cachedReceiver"}, limit = "3")
        Object doCached(HostContext context, Object receiver, @Cached("receiver.getClass()") Class<?> cachedReceiver) {
            return context.toGuestValue(this, cachedReceiver.cast(receiver));
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        Object doUncached(HostContext context, Object receiver) {
            return context.toGuestValue(this, receiver);
        }
    }

    static final class ToGuestValuesNode extends Node {

        @Children private volatile ToGuestValueNode[] toGuestValue;
        @CompilationFinal private volatile boolean needsCopy = false;
        @CompilationFinal private volatile boolean generic = false;

        private ToGuestValuesNode() {
        }

        public Object[] apply(HostContext context, Object[] args) {
            ToGuestValueNode[] nodes = this.toGuestValue;
            if (nodes == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nodes = new ToGuestValueNode[args.length];
                for (int i = 0; i < nodes.length; i++) {
                    nodes[i] = ToGuestValueNodeGen.create();
                }
                toGuestValue = insert(nodes);
            }
            if (args.length == nodes.length) {
                // fast path
                if (nodes.length == 0) {
                    return args;
                } else {
                    Object[] newArgs = fastToGuestValuesUnroll(nodes, context, args);
                    return newArgs;
                }
            } else {
                if (!generic || nodes.length != 1) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    nodes = Arrays.copyOf(nodes, 1);
                    if (nodes[0] == null) {
                        nodes[0] = ToGuestValueNodeGen.create();
                    }
                    this.toGuestValue = insert(nodes);
                    this.generic = true;
                }
                if (args.length == 0) {
                    return args;
                }
                return fastToGuestValues(nodes[0], context, args);
            }
        }

        /*
         * Specialization for constant number of arguments. Uses a profile for each argument.
         */
        @ExplodeLoop
        private Object[] fastToGuestValuesUnroll(ToGuestValueNode[] nodes, HostContext context, Object[] args) {
            Object[] newArgs = needsCopy ? new Object[nodes.length] : args;
            for (int i = 0; i < nodes.length; i++) {
                Object arg = args[i];
                Object newArg = nodes[i].execute(context, arg);
                if (needsCopy) {
                    newArgs[i] = newArg;
                } else if (arg != newArg) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    newArgs = new Object[nodes.length];
                    System.arraycopy(args, 0, newArgs, 0, args.length);
                    newArgs[i] = newArg;
                    needsCopy = true;
                }
            }
            return newArgs;
        }

        /*
         * Specialization that supports multiple argument lengths but uses a single profile for all
         * arguments.
         */
        private Object[] fastToGuestValues(ToGuestValueNode node, HostContext context, Object[] args) {
            assert toGuestValue[0] != null;
            Object[] newArgs = needsCopy ? new Object[args.length] : args;
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                Object newArg = node.execute(context, arg);
                if (needsCopy) {
                    newArgs[i] = newArg;
                } else if (arg != newArg) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    newArgs = new Object[args.length];
                    System.arraycopy(args, 0, newArgs, 0, args.length);
                    newArgs[i] = newArg;
                    needsCopy = true;
                }
            }
            return newArgs;
        }

        public static ToGuestValuesNode create() {
            return new ToGuestValuesNode();
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
