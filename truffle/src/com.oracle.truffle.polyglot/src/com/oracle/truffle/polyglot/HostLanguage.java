/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.polyglot.HostLanguage.HostContext;

/*
 * Java host language implementation.
 */
final class HostLanguage extends TruffleLanguage<HostContext> {

    @CompilationFinal private volatile PolyglotEngineImpl internalEngine;

    HostToGuestCodeCache getHostToGuestCache() {
        return internalEngine.getHostToGuestCodeCache();
    }

    static final class HostContext {

        @CompilationFinal volatile PolyglotLanguageContext internalContext;
        final Map<String, Class<?>> classCache = new HashMap<>();
        private volatile Iterable<Scope> topScopes;
        private volatile HostClassLoader classloader;
        private final HostLanguage language;

        HostContext(HostLanguage language) {
            this.language = language;
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
            if (!internalContext.context.config.hostLookupAllowed) {
                throw new HostLanguageException(String.format("Host class access is not allowed."));
            }
        }

        private HostClassLoader getClassloader() {
            if (classloader == null) {
                ClassLoader parentClassLoader = internalContext.context.config.hostClassLoader != null ? internalContext.context.config.hostClassLoader
                                : internalContext.getEngine().contextClassLoader;
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
            Predicate<String> classFilter = internalContext.context.config.classFilter;
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

        public void addToHostClasspath(TruffleFile classpathEntry) {
            checkHostAccessAllowed();
            if (TruffleOptions.AOT) {
                throw new HostLanguageException(String.format("Cannot add classpath entry %s in native mode.", classpathEntry.getName()));
            }
            if (!internalContext.context.config.hostClassLoadingAllowed) {
                throw new HostLanguageException(String.format("Host class loading is not allowed."));
            }
            if (FileSystems.hasNoIOFileSystem(classpathEntry)) {
                throw new HostLanguageException("Host class loading is disabled without IO permissions.");
            }
            getClassloader().addClasspathRoot(classpathEntry);
        }

        void initializeInternal(PolyglotLanguageContext hostContext) {
            this.internalContext = hostContext;
            PolyglotEngineImpl engine = this.language.internalEngine;
            if (engine != null) {
                assert engine == hostContext.getEngine();
            }
            this.language.internalEngine = hostContext.getEngine();
        }
    }

    @SuppressWarnings("serial")
    private static class HostLanguageException extends RuntimeException implements TruffleException {

        HostLanguageException(String message) {
            super(message);
        }

        public Node getLocation() {
            return null;
        }
    }

    @Override
    @TruffleBoundary
    protected Object getLanguageView(HostContext context, Object value) {
        Object wrapped;
        if (value instanceof TruffleObject) {
            InteropLibrary lib = InteropLibrary.getFactory().getUncached(value);
            try {
                assert !lib.hasLanguage(value) || lib.getLanguage(value) != HostLanguage.class;
            } catch (UnsupportedMessageException e) {
                throw shouldNotReachHere(e);
            }
            wrapped = ToHostNode.convertToObject(value, context.internalContext, lib);
        } else {
            wrapped = value;
        }
        return HostObject.forObject(wrapped, context.internalContext);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        String sourceString = request.getSource().getCharacters().toString();
        return Truffle.getRuntime().createCallTarget(new RootNode(this) {

            @CompilationFinal ContextReference<HostContext> contextRef;

            @Override
            public Object execute(VirtualFrame frame) {
                if (contextRef == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    contextRef = lookupContextReference(HostLanguage.class);
                }
                HostContext context = contextRef.get();
                Class<?> allTarget = context.findClass(sourceString);
                return context.internalContext.toGuestValue(allTarget);
            }
        });
    }

    @Override
    protected void disposeContext(HostContext context) {
        HostClassLoader cl = context.classloader;
        if (cl != null) {
            try {
                cl.close();
            } catch (IOException e) {
                // lets ignore that
            }
            context.classloader = null;
        }
        super.disposeContext(context);
    }

    @Override
    protected HostContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
        return new HostContext(this);
    }

    @Override
    protected Iterable<Scope> findTopScopes(HostContext context) {
        Iterable<Scope> topScopes = context.topScopes;
        if (topScopes == null) {
            synchronized (context) {
                topScopes = context.topScopes;
                if (topScopes == null) {
                    topScopes = Collections.singleton(Scope.newBuilder("Hosting top scope", new TopScopeObject(context)).build());
                    context.topScopes = topScopes;
                }
            }
        }
        return topScopes;
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }

    @ExportLibrary(InteropLibrary.class)
    static final class TopScopeObject implements TruffleObject {

        private final HostContext context;

        private TopScopeObject(HostContext context) {
            this.context = context;
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
            return HostObject.forStaticClass(context.findClass(member), context.internalContext);
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

}
