/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import java.io.ByteArrayOutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.UnsignedUtils;

import jdk.vm.ci.meta.JavaKind;

/**
 * A structure of fields, including object references, that is {@linkplain Builder modeled} at image
 * runtime and instances of which are allocated on the Java heap. The name <em>pod</em> refers to it
 * storing "plain old data" without further object-oriented features such as method dispatching or
 * type information, apart from having a Java superclass.
 *
 * @param <T> The interface of the {@linkplain #getFactory() factory} that allocates instances.
 */
public final class Pod<T> {
    private final RuntimeSupport.PodInfo podInfo;
    private final int arrayLength;
    private final byte[] referenceMap;
    private final T factory;

    private Pod(RuntimeSupport.PodInfo podInfo, int arrayLength, byte[] referenceMap) {
        this.podInfo = podInfo;
        try {
            @SuppressWarnings("unchecked")
            T factoryInstance = (T) podInfo.factoryCtor.newInstance(this);
            this.factory = factoryInstance;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        this.arrayLength = arrayLength;
        this.referenceMap = referenceMap;
    }

    public T getFactory() {
        return factory;
    }

    /**
     * A builder for constructing pods with a specific superpod or superclass and factory interface
     * and {@linkplain #addField additional fields}.
     */
    public static final class Builder<T> {
        /**
         * Create a builder for a pod that is derived directly from {@link Object} and
         * {@link Supplier} as factory interface. This is supported without explicitly registering
         * Object as superclass with {@code PodSupport}.
         */
        public static Builder<Supplier<Object>> create() {
            return new Builder<>(Object.class, Supplier.class, null);
        }

        /**
         * Create a builder for a pod derived from the given superpod, which has the same
         * superclass, uses the same factory interface for initialization, and has the same fields
         * and in addition fields that are added with {@link #addField}. There are no guarantees
         * with regard to Java type checks other than that {@link Object#getClass} on instances of
         * the pods will return a {@link Class} to which the superclass of the two pods
         * {@linkplain Class#isAssignableFrom is assignable from}.
         */
        public static <T> Builder<T> createExtending(Pod<T> superPod) {
            return new Builder<>(null, null, superPod);
        }

        /**
         * Create a builder for a pod that extends the given superclass and instances of which can
         * be allocated via the given factory interface. The specific pair of superclass and factory
         * interface must have been registered during the image build with {@code PodSupport}.
         */
        public static <T> Builder<T> createExtending(Class<?> superClass, Class<T> factoryInterface) {
            return new Builder<>(superClass, factoryInterface, null);
        }

        private final Pod<T> superPod;
        private final RuntimeSupport.PodInfo podInfo;
        private final List<Field> fields = new ArrayList<>();
        private boolean built = false;

        private Builder(Class<?> superClass, Class<?> factoryInterface, Pod<T> superPod) {
            assert superPod == null || (superClass == null && factoryInterface == null);
            if (!RuntimeSupport.isPresent()) {
                throw new UnsupportedOperationException("Pods are not available in this native image.");
            }
            if (superPod != null) {
                this.podInfo = superPod.podInfo;
            } else if (superClass != null && factoryInterface != null) {
                RuntimeSupport.PodSpec spec = new RuntimeSupport.PodSpec(superClass, factoryInterface);
                this.podInfo = RuntimeSupport.singleton().getInfo(spec);
                if (this.podInfo == null) {
                    throw new IllegalArgumentException("Pod superclass/factory interface pair was not registered during image build: " + superClass + ", " + factoryInterface);
                }
            } else {
                throw new NullPointerException();
            }
            assert DynamicHub.fromClass(this.podInfo.podClass).isPodInstanceClass();
            this.superPod = superPod;
        }

        private void guaranteeUnbuilt() {
            if (built) {
                throw new IllegalStateException();
            }
        }

        /**
         * Add a field of a specified type to a pod. Once the pod has been {@linkplain #build
         * built}, its offset is available via {@link Field#getOffset} and values of the given type
         * can be written to instances, for example using {@code Unsafe}.
         */
        public Field addField(Class<?> type) {
            guaranteeUnbuilt();
            Objects.requireNonNull(type);
            if (type == void.class) {
                throw new IllegalArgumentException("void is an illegal field type");
            }

            JavaKind kind = JavaKind.fromJavaClass(type);
            int size = ConfigurationValues.getObjectLayout().sizeInBytes(kind);
            Field f = new Field(size, kind.isObject());
            fields.add(f);
            return f;
        }

        /**
         * Create and return a pod with the superclass/superpod given during builder creation and
         * the {@linkplain #addField added fields}. This method can be called only once, after which
         * the builder can no longer be used.
         */
        public Pod<T> build() {
            guaranteeUnbuilt();
            built = true;

            /*
             * We layout the requested fields in the hybrid object's array part in a similar fashion
             * as UniverseBuilder does for regular instance fields, putting reference fields at the
             * beginning and trying to put narrow fields in alignment gaps between wider fields. The
             * entire layout of a pod might look as follows:
             *
             * [ hub pointer | identity hashcode | array length | superclass instance fields |
             * instance fields | monitor | pod fields (ref, short, byte, long) | pod reference map ]
             *
             * The array length part would provide the combined length of the pod fields and pod
             * reference map excluding any alignment padding at their beginning or the object's end.
             */

            Collections.sort(fields);

            UnsignedWord baseOffset = LayoutEncoding.getArrayBaseOffset(
                            DynamicHub.fromClass(podInfo.podClass).getLayoutEncoding());

            byte[] superRefMap = null;
            UnsignedWord nextOffset = baseOffset;
            if (superPod != null) {
                superRefMap = superPod.referenceMap;
                nextOffset = nextOffset.add(superPod.arrayLength - superRefMap.length);
            }
            ReferenceMapEncoder refMapEncoder = new ReferenceMapEncoder(superRefMap);
            while (!fields.isEmpty()) {
                boolean progress = false;
                for (int i = 0; i < fields.size(); i++) {
                    Field field = fields.get(i);

                    if (nextOffset.unsignedRemainder(field.size).equal(0)) {
                        field.initOffset(UnsignedUtils.safeToInt(nextOffset));

                        if (field.isReference) {
                            refMapEncoder.add(UnsignedUtils.safeToInt(nextOffset.subtract(baseOffset)), field.size);
                        }

                        fields.remove(i);
                        nextOffset = nextOffset.add(field.size);
                        progress = true;
                        break;
                    }
                }
                if (!progress) {
                    nextOffset = nextOffset.add(1); // insert padding byte and retry
                }
            }

            byte[] referenceMap = refMapEncoder.encode();
            int arrayLength = UnsignedUtils.safeToInt(nextOffset) + referenceMap.length;
            return new Pod<>(podInfo, arrayLength, referenceMap);
        }
    }

    public static final class Field implements Comparable<Field> {
        private final int size;
        private final boolean isReference;
        private int offset = -1;

        Field(int size, boolean isReference) {
            assert size > 0;
            this.size = size;
            this.isReference = isReference;
        }

        public int getSize() {
            return size;
        }

        public boolean isReference() {
            return isReference;
        }

        public int getOffset() {
            if (offset == -1) {
                throw new IllegalStateException("Pod must be built before field offsets are assigned");
            }
            return offset;
        }

        void initOffset(int value) {
            assert this.offset == -1;
            assert value >= 0;
            this.offset = value;
        }

        @Override
        public int compareTo(Field f) {
            if (isReference != f.isReference) { // references first
                return Boolean.compare(f.isReference, isReference);
            }
            return f.size - size; // larger fields first
        }
    }

    public static final class RuntimeSupport {
        @Fold
        public static boolean isPresent() {
            return ImageSingletons.contains(RuntimeSupport.class);
        }

        @Fold
        public static RuntimeSupport singleton() {
            return ImageSingletons.lookup(RuntimeSupport.class);
        }

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.TYPE)
        public @interface PodFactory {
            Class<?> podClass();
        }

        public static final class PodSpec {
            final Class<?> superClass;
            final Class<?> factoryInterface;

            public PodSpec(Class<?> superClass, Class<?> factoryInterface) {
                assert superClass != null && factoryInterface != null;
                this.superClass = superClass;
                this.factoryInterface = factoryInterface;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj != this && getClass() == obj.getClass()) {
                    PodSpec other = (PodSpec) obj;
                    return superClass.equals(other.superClass) && factoryInterface.equals(other.factoryInterface);
                }
                return (obj == this);
            }

            @Override
            public int hashCode() {
                return 31 * superClass.hashCode() + factoryInterface.hashCode();
            }
        }

        public static final class PodInfo {
            public final Class<?> podClass;
            public final Constructor<?> factoryCtor;

            public PodInfo(Class<?> podClass, Constructor<?> factoryCtor) {
                this.podClass = podClass;
                this.factoryCtor = factoryCtor;
            }
        }

        private final EconomicMap<PodSpec, PodInfo> pods = ImageHeapMap.create();

        @Platforms(Platform.HOSTED_ONLY.class)
        public RuntimeSupport() {
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        public boolean registerPod(PodSpec spec, PodInfo info) {
            return pods.putIfAbsent(spec, info) == null;
        }

        PodInfo getInfo(PodSpec spec) {
            return pods.get(spec);
        }
    }

    private static final class ReferenceMapEncoder {
        private final BitSet bitset = new BitSet();
        private final int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();

        ReferenceMapEncoder(byte[] referenceMap) {
            if (referenceMap != null) {
                decode(referenceMap);
            }
        }

        void add(int offset, int size) {
            assert offset % referenceSize == 0;
            assert size == referenceSize;

            int index = offset / referenceSize;
            assert !bitset.get(index);
            bitset.set(index);
        }

        /**
         * Generates a reference map composed of unsigned byte pairs of {@code nrefs} (sequence of n
         * references) and {@code gaps} (number of sequential reference-sized primitives). When a
         * single lengthy sequence of references or primitives cannot be encoded in an unsigned
         * byte, it is encoded in multiple pairs with either gaps or nrefs being 0. The reference
         * map covers only the part of the array from the beginning until the last reference. The
         * end of the reference map is indicated by a pair with {@code gaps} == 0, unless
         * {@code nrefs} is 0xff which could also mean that the pair is part of an ongoing sequence,
         * in which case an extra zero pair is added at the end.
         *
         * We try to place reference fields at the beginning, so the reference map begins with
         * {@code nrefs} indicating the number of references right at the start of the array. If
         * there are primitive fields at the beginning, this first value will be zero.
         */
        byte[] encode() {
            if (bitset.isEmpty()) {
                return new byte[]{0, 0};
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int previous;
            int index = 0;
            for (;;) {
                previous = index;
                index = bitset.nextClearBit(previous);
                int nrefs = index - previous;
                putUV(buffer, nrefs);

                previous = index;
                index = bitset.nextSetBit(index);
                if (index != -1) {
                    putUV(buffer, index - previous); // gap
                } else {
                    buffer.write(0);
                    if ((nrefs & 0xff) == 0) { // needs an explicit end marker
                        buffer.write(0);
                        buffer.write(0);
                    }
                    break;
                }
            }

            // reverse so we can decode backwards from the known end
            byte[] bytes = buffer.toByteArray();
            for (int i = 0, j = bytes.length - 1; i < j; i++, j--) {
                byte t = bytes[i];
                bytes[i] = bytes[j];
                bytes[j] = t;
            }

            assert bitset.equals(new ReferenceMapEncoder(bytes).bitset);
            return bytes;
        }

        private static void putUV(ByteArrayOutputStream buffer, int value) {
            int v = value;
            for (; v > 0xff; v -= 0xff) { // should be rare
                buffer.write(0xff);
                buffer.write(0);
            }
            buffer.write(v);
        }

        private void decode(byte[] encoded) {
            int nrefs;
            int gap;
            int bit = 0;
            int i = encoded.length - 1;
            do {
                nrefs = Byte.toUnsignedInt(encoded[i]);
                gap = Byte.toUnsignedInt(encoded[i - 1]);
                i -= 2;
                bitset.set(bit, bit + nrefs);
                bit += nrefs + gap;
            } while (gap != 0 || nrefs == 0xff);
        }
    }
}
