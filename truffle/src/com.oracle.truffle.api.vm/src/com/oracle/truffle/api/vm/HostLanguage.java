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
package com.oracle.truffle.api.vm;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.vm.HostLanguage.HostContext;

/*
 * Java host language implementation.
 */
class HostLanguage extends TruffleLanguage<HostContext> {

    static final class HostContext {

        final Env env;
        final PolyglotLanguageContext internalContext;
        final Map<String, Class<?>> classCache = new HashMap<>();
        private volatile Iterable<Scope> topScopes;

        HostContext(Env env, PolyglotLanguageContext context) {
            this.env = env;
            this.internalContext = context;
        }

        Class<?> findClass(String className) {
            if (!internalContext.context.hostAccessAllowed) {
                throw new HostLanguageException(String.format("Host class access is not allowed."));
            }
            if (TruffleOptions.AOT) {
                throw new HostLanguageException(String.format("The host class %s is not accessible in native mode.", className));
            }
            Class<?> loadedClass = classCache.computeIfAbsent(className, new Function<String, Class<?>>() {
                public Class<?> apply(String cn) {
                    return loadClass(cn);
                }
            });
            assert loadedClass != null;
            return loadedClass;
        }

        Class<?> loadClass(String className) {
            Predicate<String> classFilter = internalContext.context.classFilter;
            if (classFilter != null && !classFilter.test(className)) {
                throw new HostLanguageException(String.format("Access to host class %s is not allowed.", className));
            }
            if (className.endsWith("[]")) {
                Class<?> componentType = findClass(className.substring(0, className.length() - 2));
                return Array.newInstance(componentType, 0).getClass();
            }
            Class<?> primitiveType = getPrimitiveTypeByName(className);
            if (primitiveType != null) {
                return primitiveType;
            }
            try {
                return internalContext.getEngine().contextClassLoader.loadClass(className);
            } catch (ClassNotFoundException e) {
                throw new HostLanguageException(String.format("Access to host class %s is not allowed or does not exist.", className));
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
            return PolyglotProxy.isProxyGuestObject((TruffleObject) object) || JavaInterop.isJavaObject((TruffleObject) object);
        } else {
            return false;
        }
    }

    @Override
    protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
        Class<?> allTarget = getContextReference().get().findClass(request.getSource().getCharacters().toString());
        return Truffle.getRuntime().createCallTarget(new RootNode(this) {
            @Override
            public Object execute(VirtualFrame frame) {
                return JavaInterop.asTruffleObject(allTarget);
            }
        });
    }

    @Override
    protected Object getLanguageGlobal(HostContext context) {
        return null;
    }

    @Override
    protected HostContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
        return new HostContext(env, PolyglotContextImpl.current().getHostContext());
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

    @Override
    protected String toString(HostContext context, Object value) {
        if (value instanceof TruffleObject) {
            TruffleObject to = (TruffleObject) value;
            if (JavaInterop.isJavaObject(to)) {
                Object javaObject = JavaInterop.asJavaObject(to);
                try {
                    return javaObject.toString();
                } catch (Throwable t) {
                    throw PolyglotImpl.wrapHostException(t);
                }
            } else if (PolyglotProxy.isProxyGuestObject(to)) {
                Proxy proxy = PolyglotProxy.toProxyHostObject(to);
                try {
                    return proxy.toString();
                } catch (Throwable t) {
                    throw PolyglotImpl.wrapHostException(t);
                }
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
            if (JavaInterop.isJavaObject(to)) {
                Object javaObject = JavaInterop.asJavaObject(to);
                return JavaInterop.asTruffleValue(javaObject.getClass());
            } else if (PolyglotProxy.isProxyGuestObject(to)) {
                Proxy proxy = PolyglotProxy.toProxyHostObject(to);
                return JavaInterop.asTruffleValue(proxy.getClass());
            } else {
                return "Foreign Object";
            }
        } else {
            return JavaInterop.asTruffleValue(value.getClass());
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

            @Resolve(message = "KEYS")
            abstract static class VarsMapKeysNode extends Node {

                @TruffleBoundary
                public Object access(TopScopeObject ts) {
                    return new ClassNamesObject(ts.context.classCache.keySet());
                }
            }

            @Resolve(message = "KEY_INFO")
            abstract static class VarsMapInfoNode extends Node {

                private static final int EXISTING_INFO = KeyInfo.newBuilder().setReadable(true).build();

                @TruffleBoundary
                public Object access(TopScopeObject ts, String name) {
                    Class<?> clazz = ts.context.findClass(name);
                    if (clazz != null) {
                        return EXISTING_INFO;
                    } else {
                        return 0;
                    }
                }
            }

            @Resolve(message = "READ")
            abstract static class VarsMapReadNode extends Node {

                @TruffleBoundary
                public Object access(TopScopeObject ts, String name) {
                    return JavaInterop.asTruffleObject(ts.context.findClass(name));
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
