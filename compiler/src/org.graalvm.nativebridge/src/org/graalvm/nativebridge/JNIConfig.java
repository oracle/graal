/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.TypeLiteral;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongBinaryOperator;
import java.util.function.LongUnaryOperator;

public final class JNIConfig {

    private final Map<Type, JNIHotSpotMarshaller<?>> hotSpotMarshallers;
    private final Map<Type, JNINativeMarshaller<?>> nativeMarshallers;
    private final Map<Class<? extends Annotation>, List<Pair<Class<?>, JNIHotSpotMarshaller<?>>>> annotationHotSpotMarshallers;
    private final Map<Class<? extends Annotation>, List<Pair<Class<?>, JNINativeMarshaller<?>>>> annotationNativeMarshallers;
    private final LongUnaryOperator attachThreadAction;
    private final LongUnaryOperator detachThreadAction;
    private final LongBinaryOperator shutDownIsolateAction;
    private final LongBinaryOperator releaseNativeObjectAction;

    JNIConfig(Map<Type, JNIHotSpotMarshaller<?>> hotSpotMarshallers,
                    Map<Type, JNINativeMarshaller<?>> nativeMarshallers,
                    Map<Class<? extends Annotation>, List<Pair<Class<?>, JNIHotSpotMarshaller<?>>>> annotationHotSpotMarshallers,
                    Map<Class<? extends Annotation>, List<Pair<Class<?>, JNINativeMarshaller<?>>>> annotationNativeMarshallers,
                    LongUnaryOperator attachThreadAction, LongUnaryOperator detachThreadAction,
                    LongBinaryOperator shutDownIsolateAction, LongBinaryOperator releaseNativeObjectAction) {
        this.hotSpotMarshallers = hotSpotMarshallers;
        this.nativeMarshallers = nativeMarshallers;
        this.annotationHotSpotMarshallers = annotationHotSpotMarshallers;
        this.annotationNativeMarshallers = annotationNativeMarshallers;
        this.attachThreadAction = attachThreadAction;
        this.detachThreadAction = detachThreadAction;
        this.shutDownIsolateAction = shutDownIsolateAction;
        this.releaseNativeObjectAction = releaseNativeObjectAction;
    }

    @SuppressWarnings("unchecked")
    @SafeVarargs
    public final <T> JNIHotSpotMarshaller<T> lookupHotSpotMarshaller(Class<T> type, Class<? extends Annotation>... annotationTypes) {
        JNIHotSpotMarshaller<?> res = lookupHotSpotMarshallerImpl(type, annotationTypes);
        if (res != null) {
            return (JNIHotSpotMarshaller<T>) res;
        } else {
            throw unsupported(type);
        }
    }

    @SuppressWarnings("unchecked")
    @SafeVarargs
    public final <T> JNIHotSpotMarshaller<T> lookupHotSpotMarshaller(TypeLiteral<T> type, Class<? extends Annotation>... annotationTypes) {
        JNIHotSpotMarshaller<?> res = lookupHotSpotMarshallerImpl(type.getType(), annotationTypes);
        if (res != null) {
            return (JNIHotSpotMarshaller<T>) res;
        } else {
            throw unsupported(type.getType());
        }
    }

    @SuppressWarnings("unchecked")
    @SafeVarargs
    public final <T> JNINativeMarshaller<T> lookupNativeMarshaller(Class<T> type, Class<? extends Annotation>... annotationTypes) {
        JNINativeMarshaller<?> res = lookupNativeMarshallerImpl(type, annotationTypes);
        if (res != null) {
            return (JNINativeMarshaller<T>) res;
        } else {
            throw unsupported(type);
        }
    }

    @SuppressWarnings("unchecked")
    @SafeVarargs
    public final <T> JNINativeMarshaller<T> lookupNativeMarshaller(TypeLiteral<T> type, Class<? extends Annotation>... annotationTypes) {
        JNINativeMarshaller<?> res = lookupNativeMarshallerImpl(type.getType(), annotationTypes);
        if (res != null) {
            return (JNINativeMarshaller<T>) res;
        } else {
            throw unsupported(type.getType());
        }
    }

    long attachThread(long isolate) {
        return attachThreadAction.applyAsLong(isolate);
    }

    boolean detachThread(long isolateThread) {
        return detachThreadAction.applyAsLong(isolateThread) == 0;
    }

    boolean releaseNativeObject(long isolateThread, long handle) {
        return releaseNativeObjectAction.applyAsLong(isolateThread, handle) == 0;
    }

    boolean shutDownIsolate(long isolate, long isolateThread) {
        return shutDownIsolateAction.applyAsLong(isolate, isolateThread) == 0;
    }

    private static RuntimeException unsupported(Type type) {
        throw new UnsupportedOperationException(String.format("Marshalling of %s is not supported", type));
    }

    @SafeVarargs
    private final JNIHotSpotMarshaller<?> lookupHotSpotMarshallerImpl(Type type, Class<? extends Annotation>... annotationTypes) {
        for (Class<? extends Annotation> annotationType : annotationTypes) {
            JNIHotSpotMarshaller<?> res = lookup(annotationHotSpotMarshallers, type, annotationType);
            if (res != null) {
                return res;
            }
        }
        return hotSpotMarshallers.get(type);
    }

    @SafeVarargs
    private final JNINativeMarshaller<?> lookupNativeMarshallerImpl(Type type, Class<? extends Annotation>... annotationTypes) {
        for (Class<? extends Annotation> annotationType : annotationTypes) {
            JNINativeMarshaller<?> res = lookup(annotationNativeMarshallers, type, annotationType);
            if (res != null) {
                return res;
            }
        }
        return nativeMarshallers.get(type);
    }

    private static <T> T lookup(Map<Class<? extends Annotation>, List<Pair<Class<?>, T>>> marshallers, Type type, Class<? extends Annotation> annotationType) {
        List<Pair<Class<?>, T>> marshallersForAnnotation = marshallers.get(annotationType);
        if (marshallersForAnnotation != null) {
            Class<?> rawType = erasure(type);
            for (Pair<Class<?>, T> marshaller : marshallersForAnnotation) {
                if (marshaller.getLeft().isAssignableFrom(rawType)) {
                    return marshaller.getRight();
                }
            }
        }
        return null;
    }

    private static Class<?> erasure(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        } else if (type instanceof GenericArrayType) {
            return arrayTypeFromComponentType(erasure(((GenericArrayType) type).getGenericComponentType()));
        } else {
            throw new IllegalArgumentException("Unsupported type: " + type);
        }
    }

    private static Class<?> arrayTypeFromComponentType(Class<?> componentType) {
        return Array.newInstance(componentType, 0).getClass();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {

        private static final LongUnaryOperator ATTACH_UNSUPPORTED = (isolate) -> {
            throw new UnsupportedOperationException("Attach is not supported.");
        };
        private static final LongUnaryOperator DETACH_UNSUPPORTED = (isolateThread) -> {
            throw new UnsupportedOperationException("Detach is not supported.");
        };
        private static final LongBinaryOperator SHUTDOWN_UNSUPPORTED = (isolate, isolateThread) -> {
            throw new UnsupportedOperationException("Isolate shutdown is not supported.");
        };
        private static final LongBinaryOperator RELEASE_UNSUPPORTED = (isolateThread, handle) -> {
            throw new UnsupportedOperationException("Native object clean up is not supported.");
        };

        private final Map<Type, JNIHotSpotMarshaller<?>> hotSpotMarshallers;
        private final Map<Type, JNINativeMarshaller<?>> nativeMarshallers;
        private final Map<Class<? extends Annotation>, List<Pair<Class<?>, JNIHotSpotMarshaller<?>>>> annotationHotSpotMarshallers;
        private final Map<Class<? extends Annotation>, List<Pair<Class<?>, JNINativeMarshaller<?>>>> annotationNativeMarshallers;
        private LongUnaryOperator attachThreadAction = ATTACH_UNSUPPORTED;
        private LongUnaryOperator detachThreadAction = DETACH_UNSUPPORTED;
        private LongBinaryOperator shutDownIsolateAction = SHUTDOWN_UNSUPPORTED;
        private LongBinaryOperator releaseNativeObjectAction = RELEASE_UNSUPPORTED;

        Builder() {
            this.hotSpotMarshallers = new HashMap<>();
            this.nativeMarshallers = new HashMap<>();
            this.annotationHotSpotMarshallers = new HashMap<>();
            this.annotationNativeMarshallers = new HashMap<>();
        }

        public <T> Builder registerHotSpotMarshaller(Class<T> type, JNIHotSpotMarshaller<T> marshaller) {
            Objects.requireNonNull(type, "Type must be non null.");
            Objects.requireNonNull(marshaller, "Marshaller must be non null.");
            this.hotSpotMarshallers.put(type, marshaller);
            return this;
        }

        public <T> Builder registerNativeMarshaller(Class<T> type, JNINativeMarshaller<T> marshaller) {
            Objects.requireNonNull(type, "Type must be non null.");
            Objects.requireNonNull(marshaller, "Marshaller must be non null.");
            this.nativeMarshallers.put(type, marshaller);
            return this;
        }

        public <T> Builder registerHotSpotMarshaller(TypeLiteral<T> type, JNIHotSpotMarshaller<T> marshaller) {
            Objects.requireNonNull(type, "Type must be non null.");
            Objects.requireNonNull(marshaller, "Marshaller must be non null.");
            this.hotSpotMarshallers.put(type.getType(), marshaller);
            return this;
        }

        public <T> Builder registerNativeMarshaller(TypeLiteral<T> type, JNINativeMarshaller<T> marshaller) {
            Objects.requireNonNull(type, "Type must be non null.");
            Objects.requireNonNull(marshaller, "Marshaller must be non null.");
            this.nativeMarshallers.put(type.getType(), marshaller);
            return this;
        }

        public <T> Builder registerHotSpotMarshaller(Class<T> type, Class<? extends Annotation> annotationType, JNIHotSpotMarshaller<T> marshaller) {
            Objects.requireNonNull(type, "Type must be non null.");
            Objects.requireNonNull(annotationType, "AnnotationType must be non null.");
            Objects.requireNonNull(marshaller, "Marshaller must be non null.");
            insert(annotationHotSpotMarshallers, type, annotationType, marshaller);
            return this;
        }

        public <T> Builder registerNativeMarshaller(Class<T> type, Class<? extends Annotation> annotationType, JNINativeMarshaller<T> marshaller) {
            Objects.requireNonNull(type, "Type must be non null.");
            Objects.requireNonNull(annotationType, "AnnotationType must be non null.");
            Objects.requireNonNull(marshaller, "Marshaller must be non null.");
            insert(annotationNativeMarshallers, type, annotationType, marshaller);
            return this;
        }

        private static <T> void insert(Map<Class<? extends Annotation>, List<Pair<Class<?>, T>>> into, Class<?> type, Class<? extends Annotation> annotationType, T marshaller) {
            List<Pair<Class<?>, T>> types = into.computeIfAbsent(annotationType, (k) -> new LinkedList<>());
            Pair<Class<?>, T> toInsert = Pair.create(type, marshaller);
            boolean inserted = false;
            for (ListIterator<Pair<Class<?>, T>> it = types.listIterator(); it.hasNext();) {
                Pair<Class<?>, T> current = it.next();
                if (current.getLeft().isAssignableFrom(type)) {
                    it.set(toInsert);
                    it.add(current);
                    inserted = true;
                    break;
                }
            }
            if (!inserted) {
                types.add(toInsert);
            }
        }

        public Builder setAttachThreadAction(LongUnaryOperator action) {
            Objects.requireNonNull(action, "Action must be non null.");
            if (ImageInfo.inImageCode()) {
                throw new IllegalStateException("AttachThreadAction cannot be set in native image.");
            }
            this.attachThreadAction = action;
            return this;
        }

        public Builder setDetachThreadAction(LongUnaryOperator action) {
            Objects.requireNonNull(action, "Action must be non null.");
            if (ImageInfo.inImageCode()) {
                throw new IllegalStateException("DetachThreadAction cannot be set in native image.");
            }
            this.detachThreadAction = action;
            return this;
        }

        public Builder setShutDownIsolateAction(LongBinaryOperator action) {
            Objects.requireNonNull(action, "Action must be non null.");
            if (ImageInfo.inImageCode()) {
                throw new IllegalStateException("DetachThreadAction cannot be set in native image.");
            }
            this.shutDownIsolateAction = action;
            return this;
        }

        public Builder setReleaseNativeObjectAction(LongBinaryOperator action) {
            Objects.requireNonNull(action, "Action must be non null.");
            if (ImageInfo.inImageCode()) {
                throw new IllegalStateException("ReleaseNativeObjectAction cannot be set in native image.");
            }
            this.releaseNativeObjectAction = action;
            return this;
        }

        public JNIConfig build() {
            return new JNIConfig(hotSpotMarshallers, nativeMarshallers,
                            annotationHotSpotMarshallers, annotationNativeMarshallers,
                            attachThreadAction, detachThreadAction, shutDownIsolateAction,
                            releaseNativeObjectAction);
        }
    }
}
