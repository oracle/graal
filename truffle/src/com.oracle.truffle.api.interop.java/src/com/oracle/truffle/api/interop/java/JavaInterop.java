/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java;

import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * @since 0.9
 * @deprecated embedders should use the polyglot API instead, languages will find replacements in
 *             {@link Env}. See the individual methods for migration details.
 */
@Deprecated
public final class JavaInterop {

    private JavaInterop() {
    }

    /**
     * @since 0.9
     * @deprecated embedders should use {@link Value#as(Class)} instead. Languages should use
     *             {@link Env#asHostObject(Object, Class)}.
     */
    @Deprecated
    public static <T> T asJavaObject(Class<T> type, TruffleObject foreignObject) {
        if (foreignObject instanceof JavaObject) {
            JavaObject javaObject = (JavaObject) foreignObject;
            if (type.isInstance(javaObject.obj)) {
                return type.cast(javaObject.obj);
            }
        }
        return convertToJavaObject(type, foreignObject);
    }

    /**
     * @since 0.27
     * @deprecated use {@link Value#asHostObject()} or
     *             {@link TruffleLanguage.Env#asHostObject(Object)} instead.
     */
    @Deprecated
    public static Object asJavaObject(TruffleObject foreignObject) {
        JavaObject javaObject = (JavaObject) foreignObject;
        return javaObject.obj;
    }

    @CompilerDirectives.TruffleBoundary
    @SuppressWarnings("unchecked")
    private static <T> T convertToJavaObject(Class<T> type, TruffleObject foreignObject) {
        RootNode root = new TemporaryConvertRoot(ToJavaNode.create(), foreignObject, type);
        Object convertedValue = Truffle.getRuntime().createCallTarget(root).call();
        return (T) convertedValue;
    }

    /**
     * @since 0.24
     * @deprecated use {@link Value#isHostObject()} or
     *             {@link TruffleLanguage.Env#isHostObject(Object)} instead. For checking individual
     *             types use {@link TruffleLanguage.Env#asHostObject(Object)} and use instanceof on
     *             the result value.
     */
    @Deprecated
    public static boolean isJavaObject(Class<?> type, TruffleObject foreignObject) {
        if (foreignObject instanceof JavaObject) {
            JavaObject javaObject = (JavaObject) foreignObject;
            return type.isInstance(javaObject.obj);
        }
        return false;
    }

    /**
     * @since 0.27
     * @deprecated use {@link Value#isHostObject()} or
     *             {@link TruffleLanguage.Env#isHostObject(Object)} instead.
     */
    @Deprecated
    public static boolean isJavaObject(TruffleObject foreignObject) {
        return foreignObject instanceof JavaObject;
    }

    /**
     * @since 0.28
     * @deprecated use {@link Value#isHostObject()} or
     *             {@link TruffleLanguage.Env#isHostObject(Object)} instead.
     */
    @Deprecated
    public static boolean isJavaObject(Object object) {
        return object instanceof JavaObject;
    }

    /**
     * @since 0.9
     * @deprecated use {@link Context#asValue(Object)} or
     *             {@link TruffleLanguage.Env#asGuestValue(Object)} instead.
     */
    @Deprecated
    public static TruffleObject asTruffleObject(Object obj) {
        // legacy behavior: treat class as static class
        return asTruffleObject(obj, currentPolyglotContext(), true);
    }

    static TruffleObject asTruffleObject(Object obj, Object languageContext) {
        return asTruffleObject(obj, languageContext, false);
    }

    /**
     * Exports a Java object for use in any {@link TruffleLanguage}.
     *
     * @param obj a Java object to convert into one suitable for <em>Truffle</em> languages
     * @return converted object
     */
    static TruffleObject asTruffleObject(Object obj, Object languageContext, boolean asStaticClass) {
        if (obj instanceof TruffleObject) {
            return ((TruffleObject) obj);
        } else if (obj instanceof Class) {
            if (asStaticClass) {
                return JavaObject.forStaticClass((Class<?>) obj, languageContext);
            } else {
                return JavaObject.forClass((Class<?>) obj, languageContext);
            }
        } else if (obj == null) {
            return JavaObject.NULL;
        } else if (obj.getClass().isArray()) {
            return JavaObject.forObject(obj, languageContext);
        } else if (obj instanceof TruffleList) {
            return ((TruffleList<?>) obj).guestObject;
        } else if (obj instanceof TruffleMap) {
            return ((TruffleMap<?, ?>) obj).guestObject;
        } else if (obj instanceof TruffleFunction) {
            return ((TruffleFunction<?, ?>) obj).guestObject;
        } else if (TruffleOptions.AOT) {
            return JavaObject.forObject(obj, languageContext);
        } else {
            return JavaInteropReflect.asTruffleViaReflection(obj, languageContext);
        }
    }

    /**
     * @since 0.18
     * @deprecated embedders should use {@link Context#asValue(Object)} instead. Truffle guest
     *             languages may use {@link TruffleLanguage.Env#lookupHostSymbol(String)} to get to
     *             the host meta class and then send a NEW message to instantiate it. For existing
     *             java values the should use {@link TruffleLanguage.Env#asGuestValue(Object)}
     *             instead.
     */
    @Deprecated
    public static Object asTruffleValue(Object obj) {
        return isPrimitive(obj) ? obj : asTruffleObject(obj);
    }

    /**
     * @since 0.18
     * @deprecated use <code>value.{@link Value#isString() isString()} || value.
     *             {@link Value#isNumber() isNumber()} || value.{@link Value#isBoolean()
     *              isBoolean()}</code> instead.
     */
    @Deprecated
    public static boolean isPrimitive(Object obj) {
        if (obj instanceof TruffleObject) {
            // Someone tried to pass a TruffleObject in
            return false;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof Boolean ||
                        obj instanceof Byte ||
                        obj instanceof Short ||
                        obj instanceof Integer ||
                        obj instanceof Long ||
                        obj instanceof Float ||
                        obj instanceof Double ||
                        obj instanceof Character ||
                        obj instanceof String) {
            return true;
        }
        return false;
    }

    /**
     * @since 0.9
     * @deprecated use {@link Value#as(Class)} with a functional interface instead.
     */
    @Deprecated
    public static <T> T asJavaFunction(Class<T> functionalType, TruffleObject function) {
        RootNode root = new TemporaryConvertRoot(ToJavaNode.create(), function, functionalType);
        return functionalType.cast(Truffle.getRuntime().createCallTarget(root).call());
    }

    /**
     * @since 0.9
     * @deprecated use {@link Context#asValue(Object) asValue(value)}.{@link Value#as(Class) as}
     *             (functionalType) instead.
     */
    @Deprecated
    public static <T> TruffleObject asTruffleFunction(Class<T> functionalType, T implementation) {
        if (TruffleOptions.AOT) {
            throw new IllegalArgumentException();
        }
        return JavaInteropReflect.asTruffleFunction(functionalType, implementation, currentPolyglotContext());
    }

    /**
     * @since 0.26
     * @deprecated use value.{@link Value#as(Class) as(Map.class)} instead. No replacement for
     *             languages.
     */
    @Deprecated
    public static <K, V> Map<K, V> getMapView(Map<K, V> map, boolean includeInternal) throws IllegalArgumentException {
        if (!(map instanceof TruffleMap)) {
            throw new IllegalArgumentException(map.getClass().getCanonicalName());
        }
        TruffleMap<K, V> tmap = (TruffleMap<K, V>) map;
        return tmap.cloneInternal(includeInternal);
    }

    /**
     * @since 0.26
     * @deprecated use {@link Value#getMetaObject()} or
     *             {@link TruffleLanguage.Env#findMetaObject(Object)} from the guest language
     *             instead.
     */
    @Deprecated
    public static TruffleObject toJavaClass(TruffleObject obj) {
        if (obj instanceof JavaObject) {
            JavaObject receiver = (JavaObject) obj;
            if (receiver.obj == null) {
                return JavaObject.NULL;
            } else {
                return JavaObject.forClass(receiver.obj.getClass(), receiver.languageContext);
            }
        } else {
            return null;
        }
    }

    /**
     * @since 0.31
     * @deprecated embedders should use {@link PolyglotException#isHostException()}, languages
     *             {@link TruffleLanguage.Env#isHostException(Throwable)} instead
     */
    @Deprecated
    public static boolean isHostException(Throwable exception) {
        EngineSupport engine = JavaInteropAccessor.ACCESSOR.engine();
        if (engine == null) {
            return false;
        }
        return engine.isHostException(exception);
    }

    /**
     * @since 0.31
     * @deprecated embedders should use {@link PolyglotException#asHostException()}, languages
     *             {@link TruffleLanguage.Env#asHostException(Throwable)} instead
     */
    @Deprecated
    public static Throwable asHostException(Throwable exception) {
        EngineSupport engine = JavaInteropAccessor.ACCESSOR.engine();
        if (engine != null && engine.isHostException(exception)) {
            return engine.asHostException(exception);
        }
        throw new IllegalArgumentException("Not a HostException");
    }

    private static class TemporaryConvertRoot extends RootNode {
        @Child private ToJavaNode node;
        private final Object value;
        private final Class<?> type;

        TemporaryConvertRoot(ToJavaNode node, Object value, Class<?> type) {
            super(null);
            this.node = node;
            this.value = value;
            this.type = type;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.execute(value, type, null, currentPolyglotContext());
        }
    }

    static boolean isJavaFunction(Object o) {
        if (TruffleOptions.AOT) {
            return false;
        }
        return o instanceof JavaFunctionObject;
    }

    static Value toHostValue(Object obj, Object languageContext) {
        return JavaInteropAccessor.ACCESSOR.engine().toHostValue(obj, languageContext);
    }

    static Object toGuestValue(Object obj, Object languageContext) {
        if (isPrimitive(obj)) {
            return obj;
        }
        return toGuestObject(obj, languageContext);
    }

    static Object toGuestObject(Object obj, Object languageContext) {
        assert !isPrimitive(obj);
        EngineSupport engine = JavaInteropAccessor.ACCESSOR.engine();
        if (engine == null || languageContext == null) {
            assert !(obj instanceof Value || obj instanceof Proxy);
            return asTruffleObject(obj, languageContext);
        }
        return engine.toGuestValue(obj, languageContext);
    }

    static Object findOriginalObject(Object truffleObject) {
        EngineSupport engine = JavaInteropAccessor.ACCESSOR.engine();
        if (engine == null) {
            return truffleObject;
        }
        return engine.findOriginalObject(truffleObject);
    }

    static Throwable wrapHostException(Object languageContext, Throwable exception) {
        EngineSupport engine = JavaInteropAccessor.ACCESSOR.engine();
        if (engine == null) {
            return exception;
        }
        if (exception instanceof TruffleException) {
            return exception;
        }
        return engine.wrapHostException(languageContext, exception);
    }

    static Object currentPolyglotContext() {
        EngineSupport engine = JavaInteropAccessor.ACCESSOR.engine();
        if (engine == null) {
            return null;
        }
        return engine.getCurrentHostContext();
    }

}
