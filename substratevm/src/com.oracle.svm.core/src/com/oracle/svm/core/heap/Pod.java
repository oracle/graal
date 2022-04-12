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
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.JavaMemoryUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.nodes.SubstrateDynamicNewHybridInstanceNode;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaKind;

/**
 * A structure of fields, including object references, that is {@linkplain Builder modeled} at image
 * runtime and instances of which are allocated on the Java heap. The name <em>pod</em> refers to it
 * storing "plain old data" without further object-oriented features such as method dispatching or
 * type information, apart from having a Java superclass.
 */
public final class Pod<T> {
    private final Class<? extends T> podClass;
    private final int fieldsSizeWithoutRefMap;
    private final byte[] referenceMap;

    private Pod(Class<? extends T> podClass, int fieldsSize, byte[] referenceMap) {
        this.podClass = podClass;
        this.fieldsSizeWithoutRefMap = fieldsSize;
        this.referenceMap = referenceMap;
    }

    public T newInstance() {
        @SuppressWarnings("unchecked")
        T instance = (T) SubstrateDynamicNewHybridInstanceNode.allocate(podClass, byte.class, fieldsSizeWithoutRefMap + referenceMap.length);

        UnsignedWord podRefMapBase = getArrayBaseOffset(podClass).add(fieldsSizeWithoutRefMap);
        JavaMemoryUtil.copy(referenceMap, getArrayBaseOffset(byte[].class), instance, podRefMapBase, WordFactory.unsigned(referenceMap.length));

        return instance;
    }

    private static UnsignedWord getArrayBaseOffset(Class<?> clazz) {
        DynamicHub hub = DynamicHub.fromClass(clazz);
        return LayoutEncoding.getArrayBaseOffset(hub.getLayoutEncoding());
    }

    public static final class Builder<T> {
        public static Builder<Object> create() {
            return new Builder<>(Object.class, null);
        }

        public static <T> Builder<T> createExtending(Pod<T> superPod) {
            return new Builder<>(null, superPod);
        }

        public static <T> Builder<T> createExtending(Class<T> superClass) {
            return new Builder<>(superClass, null);
        }

        private final Pod<T> superPod;
        private final Class<? extends T> podClass;
        private final List<Field> fields = new ArrayList<>();
        private boolean built = false;

        private Builder(Class<T> superClass, Pod<T> superPod) {
            if (superClass == null && superPod == null) {
                throw new NullPointerException();
            }
            if (superPod != null) {
                this.podClass = superPod.podClass;
            } else {
                this.podClass = ImageSingletons.lookup(RuntimeSupport.class).get(superClass);
                if (this.podClass == null) {
                    throw new IllegalArgumentException("Pod superclass was not registered during image build: " + superClass);
                }
            }
            assert DynamicHub.fromClass(this.podClass).isPodInstanceClass();
            this.superPod = superPod;
        }

        private void guaranteeUnbuilt() {
            if (built) {
                throw new IllegalStateException();
            }
        }

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

        public Pod<T> build() {
            guaranteeUnbuilt();
            built = true;

            Collections.sort(fields);

            UnsignedWord baseOffset = getArrayBaseOffset(podClass);

            byte[] superRefMap = null;
            UnsignedWord nextOffset = baseOffset;
            if (superPod != null) {
                nextOffset = nextOffset.add(superPod.fieldsSizeWithoutRefMap);
                superRefMap = superPod.referenceMap;
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
            return new Pod<>(podClass, UnsignedUtils.safeToInt(nextOffset), referenceMap);
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
        private final EconomicMap<Class<?>, Class<?>> superClasses = ImageHeapMap.create();

        @Platforms(Platform.HOSTED_ONLY.class)
        public RuntimeSupport() {
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        public boolean registerClass(Class<?> superClass, Class<?> podClass) {
            VMError.guarantee(podClass.getSuperclass() == superClass);
            return superClasses.putIfAbsent(superClass, podClass) == null;
        }

        @SuppressWarnings("unchecked")
        public <T> Class<? extends T> get(Class<T> clazz) {
            return (Class<? extends T>) superClasses.get(clazz);
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

        byte[] encode() {
            if (bitset.isEmpty()) {
                return new byte[]{0, 0};
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int previous;
            int index = 0;
            for (;;) { // assume references are placed first
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
