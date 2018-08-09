/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.polyglot.HostLanguage.HostContext;

/*
 * Java host language implementation.
 */
class HostLanguage extends TruffleLanguage<HostContext> {

    static final class HostContext {

        volatile PolyglotLanguageContext internalContext;
        final Map<String, Class<?>> classCache = new HashMap<>();
        private volatile Iterable<Scope> topScopes;
        private volatile HostClassLoader classloader;

        HostContext() {
        }

        @TruffleBoundary
        Class<?> findClass(String className) {
            checkHostAccessAllowed();
            if (TruffleOptions.AOT) {
                throw new HostLanguageException(String.format("The host class %s is not accessible in native mode.", className));
            }

            Class<?> loadedClass = classCache.get(className);
            if (loadedClass == null) {
                loadedClass = findClassImpl(className);
                classCache.put(className, loadedClass);
            }
            assert loadedClass != null;
            return loadedClass;
        }

        private void checkHostAccessAllowed() {
            if (!internalContext.context.config.hostAccessAllowed) {
                throw new HostLanguageException(String.format("Host class access is not allowed."));
            }
        }

        private HostClassLoader getClassloader() {
            if (classloader == null) {
                classloader = new HostClassLoader(this, internalContext.getEngine().contextClassLoader);
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
                return getClassloader().loadClass(className);
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
            URL url;
            try {
                url = classpathEntry.toUri().toURL();
            } catch (MalformedURLException e) {
                throw new HostLanguageException("Invalid host classpath entry " + classpathEntry.getPath() + ".");
            }
            getClassloader().addURL(url);
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
    protected boolean isObjectOfLanguage(Object object) {
        if (object instanceof TruffleObject) {
            return PolyglotProxy.isProxyGuestObject((TruffleObject) object) || HostObject.isInstance(object) || isHostFunction(object);
        } else {
            return false;
        }
    }

    private static boolean isHostFunction(Object object) {
        if (TruffleOptions.AOT) {
            return false;
        }
        return HostFunction.isInstance(object);
    }

    @Override
    protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
        String sourceString = request.getSource().getCharacters().toString();
        return Truffle.getRuntime().createCallTarget(new RootNode(this) {
            @Override
            public Object execute(VirtualFrame frame) {
                HostContext context = getContextReference().get();
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
        return new HostContext();
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

    private String arrayToString(HostContext context, Object array, int level) {
        if (array == null) {
            return "null";
        }
        if (level > 0) {
            // avoid recursions all together
            return "[...]";
        }
        int iMax = Array.getLength(array) - 1;
        if (iMax == -1) {
            return "[]";
        }

        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0;; i++) {
            b.append(toStringImpl(context, Array.get(array, i), level + 1));
            if (i == iMax) {
                return b.append(']').toString();
            }
            b.append(", ");
        }
    }

    @Override
    protected String toString(HostContext context, Object value) {
        return toStringImpl(context, value, 0);
    }

    private String toStringImpl(HostContext context, Object value, int level) {
        if (value instanceof TruffleObject) {
            TruffleObject to = (TruffleObject) value;
            if (HostObject.isInstance(to)) {
                Object javaObject = ((HostObject) to).obj;
                try {
                    if (javaObject == null) {
                        return "null";
                    } else if (javaObject.getClass().isArray()) {
                        return arrayToString(context, javaObject, level);
                    } else if (javaObject instanceof Class) {
                        return ((Class<?>) javaObject).getTypeName();
                    } else {
                        return Objects.toString(javaObject);
                    }
                } catch (Throwable t) {
                    throw PolyglotImpl.wrapHostException(context.internalContext, t);
                }
            } else if (PolyglotProxy.isProxyGuestObject(to)) {
                Proxy proxy = PolyglotProxy.toProxyHostObject(to);
                try {
                    return proxy.toString();
                } catch (Throwable t) {
                    throw PolyglotImpl.wrapHostException(context.internalContext, t);
                }
            } else if (isHostFunction(value)) {
                if (TruffleOptions.AOT) {
                    return "";
                }
                return ((HostFunction) value).getDescription();
            } else {
                return "Foreign Object";
            }
        } else {
            return value.toString();
        }
    }

    @Override
    protected Object findMetaObject(HostContext context, Object value) {
        if (value instanceof TruffleObject) {
            TruffleObject to = (TruffleObject) value;
            if (HostObject.isInstance(to)) {
                Object javaObject = ((HostObject) to).obj;
                Class<?> javaType;
                if (javaObject == null) {
                    javaType = Void.class;
                } else {
                    javaType = javaObject.getClass();
                }
                return context.internalContext.toGuestValue(javaType);
            } else if (PolyglotProxy.isProxyGuestObject(to)) {
                Proxy proxy = PolyglotProxy.toProxyHostObject(to);
                return context.internalContext.toGuestValue(proxy.getClass());
            } else if (isHostFunction(to)) {
                return "Bound Method";
            } else {
                return "Foreign Object";
            }
        } else {
            return context.internalContext.toGuestValue(value.getClass());
        }
    }

    static final class TopScopeObject implements TruffleObject {

        private final HostContext context;

        private TopScopeObject(HostContext context) {
            this.context = context;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return TopScopeObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof TopScopeObject;
        }

        @MessageResolution(receiverType = TopScopeObject.class)
        static class TopScopeObjectMessageResolution {

            @Resolve(message = "HAS_KEYS")
            abstract static class VarsMapHasKeysNode extends Node {

                @SuppressWarnings("unused")
                public Object access(TopScopeObject ts) {
                    return true;
                }
            }

            @Resolve(message = "KEYS")
            abstract static class VarsMapKeysNode extends Node {

                @TruffleBoundary
                public Object access(TopScopeObject ts) {
                    return new ClassNamesObject(ts.context.classCache.keySet());
                }
            }

            @Resolve(message = "KEY_INFO")
            abstract static class VarsMapInfoNode extends Node {

                @TruffleBoundary
                public Object access(TopScopeObject ts, String name) {
                    Class<?> clazz = ts.context.findClass(name);
                    if (clazz != null) {
                        return KeyInfo.READABLE;
                    } else {
                        return 0;
                    }
                }
            }

            @Resolve(message = "READ")
            abstract static class VarsMapReadNode extends Node {

                @TruffleBoundary
                public Object access(TopScopeObject ts, String name) {
                    return HostObject.forStaticClass(ts.context.findClass(name), ts.context.internalContext);
                }
            }
        }
    }

    static final class ClassNamesObject implements TruffleObject {

        final List<String> names;

        private ClassNamesObject(Set<String> names) {
            this.names = new ArrayList<>(names);
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return ClassNamesMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof ClassNamesObject;
        }

        @MessageResolution(receiverType = ClassNamesObject.class)
        static final class ClassNamesMessageResolution {

            @Resolve(message = "HAS_SIZE")
            abstract static class ClassNamesHasSizeNode extends Node {

                @SuppressWarnings("unused")
                public Object access(ClassNamesObject varNames) {
                    return true;
                }
            }

            @Resolve(message = "GET_SIZE")
            abstract static class ClassNamesGetSizeNode extends Node {

                public Object access(ClassNamesObject varNames) {
                    return varNames.names.size();
                }
            }

            @Resolve(message = "READ")
            abstract static class ClassNamesReadNode extends Node {

                @TruffleBoundary
                public Object access(ClassNamesObject varNames, int index) {
                    try {
                        return varNames.names.get(index);
                    } catch (IndexOutOfBoundsException ioob) {
                        throw UnknownIdentifierException.raise(Integer.toString(index));
                    }
                }
            }

        }
    }

}
