/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata.serialization;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

public interface SerializationContext {

    int UNKNOWN_REFERENCE_INDEX = Integer.MIN_VALUE;
    int NULL_REFERENCE_INDEX = 0; // index 0 == null
    int CLASS_REFERENCE_INDEX = 1; // index 1 == Class.class

    <T> int recordReference(T value);

    interface Reader extends SerializationContext {

        <T> T indexToReference(int refIndex);

        <T> ValueReader<T> readerFor(Class<T> targetClass);

        default <T> T readReference(DataInput in) throws IOException {
            int refIndex = LEB128.readUnsignedInt(in);
            return indexToReference(refIndex);
        }

        default <T> T readValue(DataInput in) throws IOException {
            Class<T> targetClass = readReference(in);
            T value = readerFor(targetClass).read(this, in);
            recordReference(value);
            return value;
        }

    }

    @Platforms(Platform.HOSTED_ONLY.class)
    interface Writer extends SerializationContext {
        <T> ValueWriter<T> writerFor(Class<T> targetClass);

        <T> int referenceToIndex(T value);

        default <T> void writeReference(DataOutput out, T value) throws IOException {
            int refIndex = referenceToIndex(value);
            if (refIndex == UNKNOWN_REFERENCE_INDEX) {
                writeValue(out, value);
                refIndex = referenceToIndex(value);
                if (refIndex == UNKNOWN_REFERENCE_INDEX) {
                    throw new IllegalStateException("Written object was not registered properly");
                }
            }
            LEB128.writeUnsignedInt(out, refIndex);
        }

        @SuppressWarnings("unchecked")
        default <T> void writeValue(DataOutput out, T value) throws IOException {
            Class<T> targetClass = (Class<T>) value.getClass();
            ValueWriter<T> valueWriter = writerFor(targetClass);
            try (ForkedDataOutput fork = new ForkedDataOutput(out)) {
                writeReference(fork, targetClass);
                valueWriter.write(this, fork, value);
            }
            recordReference(value);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static Builder newBuilder() {
        return new Builder();
    }

    final class Builder {

        private List<Class<?>> knownClasses = List.of();

        public List<Class<?>> getKnownClasses() {
            return knownClasses;
        }

        public Builder setKnownClasses(List<Class<?>> knownClasses) {
            this.knownClasses = knownClasses;
            return this;
        }

        private final EconomicMap<Class<?>, ValueReader<?>> valueReaders = EconomicMap.create();

        @Platforms(Platform.HOSTED_ONLY.class) //
        private final EconomicMap<Class<?>, ValueWriter<?>> valueWriters = EconomicMap.create();

        @Platforms(Platform.HOSTED_ONLY.class)
        public <T> Builder registerSerializer(boolean overrideExisting, Class<T> targetClass, ValueSerializer<? extends T> valueSerializer) {
            registerReader(overrideExisting, targetClass, valueSerializer.getReader());
            registerWriter(overrideExisting, targetClass, valueSerializer.getWriter());
            return this;
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        public <T> Builder registerSerializer(Class<T> targetClass, ValueSerializer<? extends T> valueSerializer) {
            return registerSerializer(false, targetClass, valueSerializer);
        }

        public <T> Builder registerReader(boolean overrideExisting, Class<T> targetClass, ValueReader<? extends T> valueReader) {
            if (!overrideExisting && valueReaders.containsKey(targetClass)) {
                throw new IllegalArgumentException("ValueReader already exists for " + targetClass);
            }
            valueReaders.put(targetClass, valueReader);
            return this;
        }

        public <T> Builder registerReader(Class<T> targetClass, ValueReader<? extends T> valueReader) {
            return registerReader(false, targetClass, valueReader);
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        public <T> Builder registerWriter(boolean overrideExisting, Class<T> targetClass, ValueWriter<? extends T> valueWriter) {
            if (!overrideExisting && valueWriters.containsKey(targetClass)) {
                throw new IllegalArgumentException("ValueWriter already exists for " + targetClass);
            }
            valueWriters.put(targetClass, valueWriter);
            return this;
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        public <T> Builder registerWriter(Class<T> targetClass, ValueWriter<? extends T> valueWriter) {
            return registerWriter(false, targetClass, valueWriter);
        }

        public SerializationContext.Reader buildReader() {
            return new ReaderImpl(knownClasses, new ValueReader.Resolver() {

                @SuppressWarnings("unchecked")
                @Override
                public <T> ValueReader<T> resolve(Class<T> targetClass) {
                    return (ValueReader<T>) valueReaders.get(targetClass);
                }
            });
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        public SerializationContext.Writer buildWriter() {
            return new WriterImpl(knownClasses, new ValueWriter.Resolver() {

                @SuppressWarnings("unchecked")
                @Override
                public <T> ValueWriter<T> resolve(Class<T> targetClass) {
                    return (ValueWriter<T>) valueWriters.get(targetClass);
                }
            });
        }
    }
}
