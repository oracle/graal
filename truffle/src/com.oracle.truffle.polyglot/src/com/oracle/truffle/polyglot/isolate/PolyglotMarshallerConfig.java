/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot.isolate;

import static com.oracle.truffle.api.source.Source.CONTENT_NONE;
import static com.oracle.truffle.polyglot.isolate.PolyglotIsolateHostSupport.findContextHandleByContextReceiver;
import static com.oracle.truffle.polyglot.isolate.PolyglotIsolateHostSupport.findEngineHandleByEngineReceiver;
import static com.oracle.truffle.polyglot.isolate.PolyglotIsolateGuestSupport.isHost;
import static org.graalvm.nativebridge.BinaryMarshaller.nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.AccessMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.graalvm.nativebridge.BinaryInput;
import org.graalvm.nativebridge.BinaryMarshaller;
import org.graalvm.nativebridge.BinaryOutput;
import org.graalvm.nativebridge.ForeignException;
import org.graalvm.nativebridge.ForeignObject;
import org.graalvm.nativebridge.Isolate;
import org.graalvm.nativebridge.MarshalledException;
import org.graalvm.nativebridge.MarshallerAnnotation;
import org.graalvm.nativebridge.MarshallerConfig;
import org.graalvm.nativebridge.Peer;
import org.graalvm.nativebridge.ReferenceHandles;
import org.graalvm.nativebridge.TypeLiteral;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractSourceDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.IOAccessor;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.polyglot.io.ProcessHandler.ProcessCommand;
import org.graalvm.polyglot.io.ProcessHandler.Redirect;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.TruffleObject;

final class PolyglotMarshallerConfig {

    private static final MarshallerConfig INSTANCE = createMarshallerConfig();

    private PolyglotMarshallerConfig() {
    }

    static MarshallerConfig getInstance() {
        return INSTANCE;
    }

    private static MarshallerConfig createMarshallerConfig() {
        BinaryMarshaller<Set<? extends AccessMode>> accessModesMarshaller = SetMarshaller.covariant(new SetMarshaller<>(new EnumMarshaller<>(AccessMode.class)) {
            @Override
            int inferElementsSize(Set<AccessMode> object) {
                return object.size();
            }
        });
        BinaryMarshaller<Set<? extends OpenOption>> openOptionsMarshaller = SetMarshaller.covariant(
                        new SetMarshaller<>(new ExtensibleEnumMarshaller<>(OpenOption.class, List.of(LinkOption.class, StandardOpenOption.class))) {
                            @Override
                            int inferElementsSize(Set<OpenOption> object) {
                                return 2 * object.size();
                            }
                        });
        BinaryMarshaller<Set<PosixFilePermission>> posixFilePermissionsMarshaller = new SetMarshaller<>(new EnumMarshaller<>(PosixFilePermission.class)) {
            @Override
            int inferElementsSize(Set<PosixFilePermission> object) {
                return object.size();
            }
        };

        FileAttributeValueMarshaller fileAttributeValueMarshaller = new FileAttributeValueMarshaller(posixFilePermissionsMarshaller);

        MarshallerConfig.Builder builder = MarshallerConfig.newBuilder();
        builder.registerMarshaller(ByteBuffer.class, nullable(new ByteBufferMarshaller()));
        builder.registerMarshaller(ByteSequence.class, nullable(new ByteSequenceMarshaller()));
        builder.registerMarshaller(CharSequence.class, nullable(new CharSequenceMarshaller()));
        builder.registerMarshaller(Charset.class, nullable(new CharsetMarshaller()));
        builder.registerMarshaller(CopyOption.class, nullable(new ExtensibleEnumMarshaller<>(CopyOption.class, List.of(LinkOption.class, StandardCopyOption.class))));
        builder.registerMarshaller(Duration.class, nullable(new DurationMarshaller()));
        builder.registerMarshaller(Error.class, nullable(new ExceptionMarshaller<>(Error.class)));
        builder.registerMarshaller(LinkOption.class, new EnumMarshaller<>(LinkOption.class));
        builder.registerMarshaller(LogRecord.class, nullable(new LogRecordMarshaller()));
        builder.registerMarshaller(OptionDescriptor.class, nullable(new OptionDescriptorMarshaller()));
        builder.registerMarshaller(ProcessCommand.class, nullable(new ProcessCommandMarshaller()));
        builder.registerMarshaller(ProcessHandle.class, nullable(new ProcessHandleMarshaller()));
        builder.registerMarshaller(SandboxPolicy.class, nullable(new EnumMarshaller<>(SandboxPolicy.class)));
        builder.registerMarshaller(Throwable.class, nullable(ThrowableMarshaller.INSTANCE));
        builder.registerMarshaller(TimeUnit.class, nullable(new EnumMarshaller<>(TimeUnit.class)));
        builder.registerMarshaller(URI.class, nullable(new URIMarshaller()));
        builder.registerMarshaller(URL.class, nullable(new URLMarshaller()));
        builder.registerMarshaller(Path.class, nullable(new PathMarshaller()));
        builder.registerMarshaller(ZoneId.class, nullable(new ZoneIdMarshaller()));

        builder.registerMarshaller(new TypeLiteral<Map<String, String>>() {
        }, nullable(new StringMapMarshaller()));
        builder.registerMarshaller(new TypeLiteral<Map<String, String[]>>() {
        }, nullable(new StringArrayMapMarshaller()));
        builder.registerMarshaller(new TypeLiteral<Set<String>>() {
        }, nullable(new StringSetMarshaller()));
        builder.registerMarshaller(new TypeLiteral<Set<? extends AccessMode>>() {
        }, nullable(accessModesMarshaller));
        builder.registerMarshaller(new TypeLiteral<FileAttribute<?>>() {
        }, new FileAttributeMarshaller(posixFilePermissionsMarshaller));
        builder.registerMarshaller(new TypeLiteral<Set<? extends OpenOption>>() {
        }, nullable(openOptionsMarshaller));
        builder.registerMarshaller(new TypeLiteral<Map<String, Object>>() {
        }, FileAttributeMap.class, nullable(new FileAttributeMapMarshaller(fileAttributeValueMarshaller)));

        builder.registerMarshaller(ByteBuffer.class, ReadOnly.class, nullable(new ReadOnlyByteBufferMarshaller()));
        builder.registerMarshaller(ByteBuffer.class, WriteOnly.class, nullable(new WriteOnlyByteBufferMarshaller()));
        builder.registerMarshaller(Object.class, ContextReceiver.class, nullable(new ContextReceiverMarshaller()));
        builder.registerMarshaller(Object.class, EngineReceiver.class, nullable(new EngineReceiverMarshaller()));
        builder.registerMarshaller(Object.class, EnvironmentAccessByValue.class, nullable(new EnvironmentAccessMarshaller()));
        builder.registerMarshaller(Object.class, FileAttributeValue.class, nullable(fileAttributeValueMarshaller));
        builder.registerMarshaller(Object.class, HostContext.class, nullable(new HostContextMarshaller()));
        builder.registerMarshaller(Object.class, InteropObject.class, nullable(new InteropObjectMarshaller()));
        builder.registerMarshaller(Object.class, IOAccessByValue.class, nullable(new IOAccessMarshaller()));
        builder.registerMarshaller(Object.class, Null.class, new NullMarshaller());
        builder.registerMarshaller(Object.class, OptionValue.class, nullable(new OptionValueMarshaller()));
        builder.registerMarshaller(Object.class, PolyglotAccessByValue.class, nullable(new PolyglotAccessMarshaller()));
        builder.registerMarshaller(Object.class, SourceByValue.class, nullable(new SourceCopyMarshaller()));
        builder.registerMarshaller(Object.class, SourceReceiver.class, nullable(new SourceMarshaller()));
        builder.registerMarshaller(Object.class, SourceSectionReceiver.class, nullable(new SourceSectionMarshaller()));
        builder.registerMarshaller(Object.class, TruffleFile.class, nullable(new TruffleFileMarshaller()));
        builder.registerMarshaller(Object.class, ValueReceiver.class, nullable(new ValueMarshaller()));
        builder.registerMarshaller(RuntimeException.class, PolyglotExceptionReceiver.class,
                        nullable(new ExceptionMarshaller<>(RuntimeException.class, (e) -> PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().isPolyglotException(e))));

        builder.registerMarshaller(new TypeLiteral<Map<String, Object>>() {
        }, InstrumentPeerMap.class, nullable(new InstrumentPeerMapMarshaller()));
        builder.registerMarshaller(new TypeLiteral<Map<String, Object>>() {
        }, LanguagePeerMap.class, nullable(new LanguagePeerMapMarshaller()));
        builder.registerMarshaller(new TypeLiteral<List<Object>>() {
        }, ValuesList.class, nullable(new ValueListMarshaller()));
        return builder.build();
    }

    private static final class EnumMarshaller<E extends Enum<E>> implements BinaryMarshaller<E> {

        private final E[] values;

        EnumMarshaller(Class<E> enumClass) {
            values = enumClass.getEnumConstants();
            if (values.length > Byte.MAX_VALUE) {
                throw new IllegalArgumentException("Only " + Byte.MAX_VALUE + " enum constants are supported.");
            }
        }

        @Override
        public E read(Isolate<?> isolate, BinaryInput input) {
            return values[input.readByte()];
        }

        @Override
        public void write(BinaryOutput output, E object) {
            output.writeByte(object.ordinal());
        }

        @Override
        public int inferSize(E object) {
            return 1;
        }
    }

    private static final class ExtensibleEnumMarshaller<T> implements BinaryMarshaller<T> {

        private final Class<?>[] implementors;
        private final EnumMarshaller<?>[] marshallers;

        ExtensibleEnumMarshaller(Class<T> extensibleEnumInterface, List<Class<? extends Enum<?>>> implementors) {
            if (implementors.size() > Byte.MAX_VALUE) {
                throw new IllegalArgumentException("Only " + Byte.MAX_VALUE + " implementors are supported.");
            }
            this.implementors = implementors.toArray(new Class<?>[implementors.size()]);
            EnumMarshaller<?>[] tmp = new EnumMarshaller<?>[implementors.size()];
            for (int i = 0; i < tmp.length; i++) {
                Class<? extends Enum<?>> implementor = implementors.get(i);
                if (!extensibleEnumInterface.isAssignableFrom(implementor)) {
                    throw new IllegalArgumentException(implementor + " does not implement " + extensibleEnumInterface);
                }
                tmp[i] = enumMarshaller(implementor);
            }
            this.marshallers = tmp;
        }

        @SuppressWarnings("unchecked")
        private static <E extends Enum<E>> EnumMarshaller<?> enumMarshaller(Class<? extends Enum<?>> implementor) {
            return new EnumMarshaller<>((Class<E>) implementor);
        }

        @Override
        public T read(Isolate<?> isolate, BinaryInput input) {
            int index = input.readByte();
            return asExtensibleEnumInterfaceMarshaller(marshallers[index]).read(isolate, input);
        }

        @Override
        public void write(BinaryOutput output, T object) {
            for (int i = 0; i < implementors.length; i++) {
                if (implementors[i].isInstance(object)) {
                    output.writeByte(i);
                    asExtensibleEnumInterfaceMarshaller(marshallers[i]).write(output, object);
                    return;
                }
            }
            throw new IllegalArgumentException(String.valueOf(object));
        }

        @Override
        public int inferSize(T object) {
            return 2;
        }

        @SuppressWarnings("unchecked")
        private BinaryMarshaller<T> asExtensibleEnumInterfaceMarshaller(EnumMarshaller<?> marshaller) {
            return (BinaryMarshaller<T>) marshaller;
        }
    }

    private static class SetMarshaller<T> implements BinaryMarshaller<Set<T>> {

        private final BinaryMarshaller<T> elementMarshaller;

        SetMarshaller(BinaryMarshaller<T> elementMarshaller) {
            this.elementMarshaller = Objects.requireNonNull(elementMarshaller);
        }

        @Override
        public final Set<T> read(Isolate<?> isolate, BinaryInput input) {
            boolean ordered = input.readBoolean();
            int size = input.readInt();
            Set<T> res = ordered ? new LinkedHashSet<>(size) : new HashSet<>(size);
            for (int i = 0; i < size; i++) {
                res.add(elementMarshaller.read(isolate, input));
            }
            return res;
        }

        @Override
        public final void write(BinaryOutput output, Set<T> object) {
            boolean ordered = object instanceof SortedSet || object instanceof LinkedHashSet;
            output.writeBoolean(ordered);
            output.writeInt(object.size());
            for (T element : object) {
                elementMarshaller.write(output, element);
            }
        }

        @Override
        public final int inferSize(Set<T> object) {
            int elementsSize = inferElementsSize(object);
            return 1 + Integer.BYTES + elementsSize;
        }

        /**
         * Calculates an estimate of the number of bytes needed to store the elements of the set.
         * The default implementation delegates to {@code elementMarshaller}, to which it sends the
         * set element. To get the set element it needs to create an iterator. Marshaller for
         * objects that have a fixed length can override this method and calculate the estimate
         * without creating an iterator.
         */
        int inferElementsSize(Set<T> object) {
            int size = object.size();
            if (size == 0) {
                return 0;
            } else {
                return size * elementMarshaller.inferSize(object.iterator().next());
            }
        }

        @SuppressWarnings("unchecked")
        static <T> BinaryMarshaller<Set<? extends T>> covariant(BinaryMarshaller<Set<T>> setMarshaller) {
            return (BinaryMarshaller<Set<? extends T>>) (BinaryMarshaller<?>) setMarshaller;
        }
    }

    private static final class OptionValueMarshaller implements BinaryMarshaller<Object> {

        private static final int OPTION_VALUE_SIZE_ESTIMATE = 16;

        static final int VALUE = 1;
        static final int MAP = 2;
        static final int UNSUPPORTED = 3;

        @Override
        public Object read(Isolate<?> isolate, BinaryInput in) {
            int kind = in.readByte();
            switch (kind) {
                case UNSUPPORTED:
                    return null;
                case VALUE:
                    return readValue(in);
                case MAP:
                    int size = in.readInt();
                    Map<String, Object> map = new HashMap<>();
                    for (int i = 0; i < size; i++) {
                        String key = in.readUTF();
                        Object value = readValue(in);
                        if (value == null) {
                            // Enum class cannot be loaded.
                            return null;
                        }
                        map.put(key, value);
                    }
                    return map;
                default:
                    throw new UnsupportedOperationException("Unknown kind: " + kind);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void write(BinaryOutput out, Object object) {
            if (object instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) object;
                if (isMapSupported(map)) {
                    out.writeByte(MAP);
                    out.writeInt(map.size());
                    for (Entry<String, Object> e : map.entrySet()) {
                        out.writeUTF(e.getKey());
                        writeValue(out, e.getValue());
                    }
                } else {
                    out.writeByte(UNSUPPORTED);
                }
            } else if (isValueSupported(object)) {
                out.writeByte(VALUE);
                writeValue(out, object);
            } else {
                out.writeByte(UNSUPPORTED);
            }
        }

        private static void writeValue(BinaryOutput out, Object object) {
            if (isValue(object)) {
                out.writeBoolean(false);
                out.writeTypedValue(object);
            } else {
                out.writeBoolean(true);
                out.writeUTF(object.getClass().getName());
                out.writeInt(((Enum<?>) object).ordinal());
            }
        }

        private static Object readValue(BinaryInput in) {
            boolean isEnum = in.readBoolean();
            if (!isEnum) {
                return in.readTypedValue();
            } else {
                String enumClzName = in.readUTF();
                int enumOrdinal = in.readInt();
                try {
                    Class<?> clz = Class.forName(enumClzName);
                    return clz.getEnumConstants()[enumOrdinal];
                } catch (ClassNotFoundException cnf) {
                    return null;
                }
            }
        }

        private static boolean isValue(Object object) {
            if (object == null) {
                return false;
            }
            Class<?> clz = object.getClass();
            return clz == String.class || clz == Boolean.class || clz == Byte.class || clz == Integer.class || clz == Long.class || clz == Float.class || clz == Double.class;
        }

        private static boolean isEnum(Object object) {
            return object instanceof Enum;
        }

        private static boolean isValueSupported(Object object) {
            return isValue(object) || isEnum(object);
        }

        private static boolean isMapSupported(Map<String, Object> map) {
            for (Object value : map.values()) {
                if (!isValueSupported(value)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int inferSize(Object object) {
            return OPTION_VALUE_SIZE_ESTIMATE;
        }
    }

    private static final class OptionDescriptorMarshaller implements BinaryMarshaller<OptionDescriptor> {

        private final OptionValueMarshaller optionValueMarshaller = new OptionValueMarshaller();

        @Override
        public OptionDescriptor read(Isolate<?> isolate, BinaryInput in) {
            String name = in.readUTF();
            OptionCategory category = OptionCategory.values()[in.readInt()];
            OptionStability stability = OptionStability.values()[in.readInt()];
            String help = in.readUTF();
            boolean deprecated = in.readBoolean();
            String deprecationMessage = in.readUTF();
            String usageSyntax = (String) in.readTypedValue();
            Object defaultValue = optionValueMarshaller.read(isolate, in);
            String typeName = in.readUTF();
            long convertorHandle = in.readLong();
            long validatorHandle = in.readLong();
            Peer convertorPeer = Peer.create(isolate, convertorHandle);
            Peer validatorPeer = Peer.create(isolate, validatorHandle);
            Function<String, Object> convertor = ForeignOptionConverterGen.create(convertorPeer);
            Consumer<Object> validator = ForeignOptionValidatorGen.create(validatorPeer);
            OptionKey<Object> key = new OptionKey<>(defaultValue, new OptionType<>(typeName, convertor, validator));
            return OptionDescriptor.newBuilder(key, name).category(category).stability(stability).help(help).deprecated(deprecated).deprecationMessage(deprecationMessage).usageSyntax(
                            usageSyntax).build();
        }

        @Override
        public void write(BinaryOutput out, OptionDescriptor object) {
            if (isHost()) {
                throw new UnsupportedOperationException("Not supported for HotSpot to Native.");
            }
            out.writeUTF(object.getName());
            out.writeInt(object.getCategory().ordinal());
            out.writeInt(object.getStability().ordinal());
            out.writeUTF(object.getHelp());
            out.writeBoolean(object.isDeprecated());
            out.writeUTF(object.getDeprecationMessage());
            out.writeTypedValue(object.getUsageSyntax());
            OptionKey<?> key = object.getKey();
            optionValueMarshaller.write(out, key.getDefaultValue());
            out.writeUTF(key.getType().getName());
            out.writeLong(ReferenceHandles.create(new OptionConvertor(key.getType())));
            out.writeLong(ReferenceHandles.create(new OptionValidator(key.getType())));
        }

        @Override
        public int inferSize(OptionDescriptor object) {
            OptionKey<?> key = object.getKey();
            return strsize(object.getName()) + 2 * Integer.BYTES + strsize(object.getHelp()) + 1 + strsize(object.getDeprecationMessage()) + optstrsize(object.getUsageSyntax()) +
                            optionValueMarshaller.inferSize(key.getDefaultValue()) + strsize(key.getType().getName()) + 3 * Long.BYTES;
        }

        private static final class OptionConvertor implements Function<String, Object> {

            private final OptionType<?> delegate;

            OptionConvertor(OptionType<?> delegate) {
                this.delegate = delegate;
            }

            @Override
            public Object apply(String s) {
                return delegate.convert(s);
            }
        }

        private static final class OptionValidator implements Consumer<Object> {

            private final OptionType<Object> delegate;

            @SuppressWarnings("unchecked")
            OptionValidator(OptionType<?> delegate) {
                this.delegate = (OptionType<Object>) delegate;
            }

            @Override
            public void accept(Object o) {
                delegate.validate(o);
            }
        }
    }

    private static final class InstrumentPeerMapMarshaller implements BinaryMarshaller<Map<String, Object>> {

        private static final int INSTRUMENT_SIZE_ESTIMATE = 24;

        @Override
        public Map<String, Object> read(Isolate<?> isolate, BinaryInput in) {
            int size = in.readInt();
            Map<String, Object> res = new LinkedHashMap<>(size);
            for (int i = 0; i < size; i++) {
                String name = in.readUTF();
                long objectHandle = in.readLong();
                Peer peer = Peer.create(isolate, objectHandle);
                res.put(name, peer);
            }
            return res;
        }

        @Override
        public void write(BinaryOutput out, Map<String, Object> object) {
            if (isHost()) {
                throw new UnsupportedOperationException("Not supported for HotSpot to Native.");
            }
            out.writeInt(object.size());
            for (Entry<String, Object> e : object.entrySet()) {
                out.writeUTF(e.getKey());
                out.writeLong(ReferenceHandles.create(e.getValue()));
            }
        }

        @Override
        public int inferSize(Map<String, Object> object) {
            return Integer.BYTES + Long.BYTES + object.size() * INSTRUMENT_SIZE_ESTIMATE;
        }
    }

    private static final class LanguagePeerMapMarshaller implements BinaryMarshaller<Map<String, Object>> {

        private static final int LANGUAGE_SIZE_ESTIMATE = 24;

        @Override
        public Map<String, Object> read(Isolate<?> isolate, BinaryInput in) {
            int size = in.readInt();
            Map<String, Object> res = new LinkedHashMap<>(size);
            for (int i = 0; i < size; i++) {
                String name = in.readUTF();
                long objectHandle = in.readLong();
                Peer peer = Peer.create(isolate, objectHandle);
                res.put(name, peer);
            }
            return res;
        }

        @Override
        public void write(BinaryOutput out, Map<String, Object> object) {
            if (isHost()) {
                throw new UnsupportedOperationException("Not supported for HotSpot to Native.");
            }
            out.writeInt(object.size());
            for (Entry<String, Object> e : object.entrySet()) {
                out.writeUTF(e.getKey());
                out.writeLong(ReferenceHandles.create(e.getValue()));
            }
        }

        @Override
        public int inferSize(Map<String, Object> object) {
            return Integer.BYTES + Long.BYTES + object.size() * LANGUAGE_SIZE_ESTIMATE;
        }
    }

    private static final class StringMapMarshaller implements BinaryMarshaller<Map<String, String>> {

        private static final int STRING_SIZE_ESTIMATE = 32;

        @Override
        public Map<String, String> read(Isolate<?> isolate, BinaryInput in) {
            int size = in.readInt();
            Map<String, String> result = new HashMap<>(size);
            for (int i = 0; i < size; i++) {
                String key = in.readUTF();
                String value = in.readUTF();
                result.put(key, value);
            }
            return result;
        }

        @Override
        public void write(BinaryOutput out, Map<String, String> map) {
            out.writeInt(map.size());
            for (Entry<String, String> e : map.entrySet()) {
                out.writeUTF(e.getKey());
                out.writeUTF(e.getValue());
            }
        }

        @Override
        public int inferSize(Map<String, String> object) {
            return Integer.BYTES + object.size() * STRING_SIZE_ESTIMATE * 2;
        }
    }

    private static final class StringArrayMapMarshaller implements BinaryMarshaller<Map<String, String[]>> {

        private static final int STRING_SIZE_ESTIMATE = 32;
        private static final int STRING_ARRAY_SIZE_ESTIMATE = 32;

        @Override
        public Map<String, String[]> read(Isolate<?> isolate, BinaryInput in) {
            int size = in.readInt();
            Map<String, String[]> result = new HashMap<>(size);
            for (int i = 0; i < size; i++) {
                String key = in.readUTF();
                int len = in.readInt();
                String[] arr = new String[len];
                for (int j = 0; j < len; j++) {
                    arr[j] = in.readUTF();
                }
                result.put(key, arr);
            }
            return result;
        }

        @Override
        public void write(BinaryOutput out, Map<String, String[]> map) {
            out.writeInt(map.size());
            for (Entry<String, String[]> e : map.entrySet()) {
                out.writeUTF(e.getKey());
                String[] value = e.getValue();
                out.writeInt(value.length);
                for (String str : value) {
                    out.writeUTF(str);
                }
            }
        }

        @Override
        public int inferSize(Map<String, String[]> object) {
            return Integer.BYTES + object.size() * (STRING_SIZE_ESTIMATE + STRING_ARRAY_SIZE_ESTIMATE + Integer.BYTES);
        }
    }

    private static final class StringSetMarshaller implements BinaryMarshaller<Set<String>> {

        private static final int STRING_SIZE_ESTIMATE = 32;

        @Override
        public Set<String> read(Isolate<?> isolate, BinaryInput in) {
            int len = in.readInt();
            Set<String> res = new HashSet<>(len);
            for (int i = 0; i < len; i++) {
                res.add(in.readUTF());
            }
            return res;
        }

        @Override
        public void write(BinaryOutput out, Set<String> object) {
            out.writeInt(object.size());
            for (String str : object) {
                out.writeUTF(str);
            }
        }

        @Override
        public int inferSize(Set<String> object) {
            return Integer.BYTES + object.size() * STRING_SIZE_ESTIMATE;
        }
    }

    private static final class NullMarshaller implements BinaryMarshaller<Object> {

        @Override
        public Object read(Isolate<?> isolate, BinaryInput input) {
            return null;
        }

        @Override
        public void write(BinaryOutput output, Object object) {
        }

        @Override
        public int inferSize(Object object) {
            return 0;
        }
    }

    private static final class DurationMarshaller implements BinaryMarshaller<Duration> {

        @Override
        public Duration read(Isolate<?> isolate, BinaryInput in) {
            long seconds = in.readLong();
            int nanos = in.readInt();
            return Duration.ofSeconds(seconds, nanos);
        }

        @Override
        public void write(BinaryOutput out, Duration object) {
            out.writeLong(object.getSeconds());
            out.writeInt(object.getNano());
        }

        @Override
        public int inferSize(Duration object) {
            return Long.BYTES + Integer.BYTES;
        }
    }

    private static final class URIMarshaller implements BinaryMarshaller<URI> {

        @Override
        public URI read(Isolate<?> isolate, BinaryInput in) {
            try {
                return new URI(in.readUTF());
            } catch (URISyntaxException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Override
        public void write(BinaryOutput out, URI uri) {
            out.writeUTF(uri.toString());
        }

        @Override
        public int inferSize(URI uri) {
            return strsize(uri.toString());
        }
    }

    private static final class PathMarshaller implements BinaryMarshaller<Path> {

        @Override
        public Path read(Isolate<?> isolate, BinaryInput in) {
            return Path.of(in.readUTF());
        }

        @Override
        public void write(BinaryOutput out, Path url) {
            out.writeUTF(url.toString());
        }

        @Override
        public int inferSize(Path path) {
            // unix paths cache internal string representations
            // so calling toString() should be cheap.
            return strsize(path.toString());
        }
    }

    private static final class URLMarshaller implements BinaryMarshaller<URL> {

        @SuppressWarnings("deprecation")
        @Override
        public URL read(Isolate<?> isolate, BinaryInput in) {
            // Walnut forbids URL::<init>(String), we need to use URL::<init>(String, String, int,
            // String)
            try {
                String protocol = in.readUTF();
                String host = (String) in.readTypedValue();
                int port = in.readInt();
                String path = in.readUTF();
                return new URL(protocol, host, port, path);
            } catch (MalformedURLException e) {
                return null;
            }
        }

        @Override
        public void write(BinaryOutput out, URL url) {
            // Walnut forbids URL::<init>(String), we need to use URL::<init>(String, String, int,
            // String)
            out.writeUTF(url.getProtocol());
            out.writeTypedValue(url.getHost());
            out.writeInt(url.getPort());
            out.writeUTF(url.getPath());
        }

        @Override
        public int inferSize(URL url) {
            return strsize(url.getProtocol()) + optstrsize(url.getHost()) + Integer.BYTES + strsize(url.getPath());
        }
    }

    private static final class LogRecordMarshaller implements BinaryMarshaller<LogRecord> {

        private static final int PARAMETER_SIZE_ESTIMATE = 48;

        @Override
        public LogRecord read(Isolate<?> isolate, BinaryInput in) {
            Level level = Level.parse(Integer.toString(in.readInt()));
            String loggerName = (String) in.readTypedValue();
            String message = (String) in.readTypedValue();
            boolean isCallerClassSet = in.readBoolean();
            String className = isCallerClassSet ? (String) in.readTypedValue() : null;
            boolean isCallerMethodSet = in.readBoolean();
            String methodName = isCallerMethodSet ? (String) in.readTypedValue() : null;
            String formatKind = in.readUTF();
            Object[] params = (Object[]) in.readTypedValue();
            return PolyglotIsolateAccessor.ENGINE.createLogRecord(level, loggerName, message, className, methodName, params, null, formatKind);
        }

        @Override
        public void write(BinaryOutput out, LogRecord rec) {
            out.writeInt(rec.getLevel().intValue());
            out.writeTypedValue(rec.getLoggerName());
            out.writeTypedValue(rec.getMessage());
            if (PolyglotIsolateAccessor.ENGINE.isLogRecordCallerClassSet(rec)) {
                out.writeBoolean(true);
                out.writeTypedValue(rec.getSourceClassName());
            } else {
                out.writeBoolean(false);
            }
            if (PolyglotIsolateAccessor.ENGINE.isLogRecordCallerMethodSet(rec)) {
                out.writeBoolean(true);
                out.writeTypedValue(rec.getSourceMethodName());
            } else {
                out.writeBoolean(false);
            }
            out.writeUTF(PolyglotIsolateAccessor.ENGINE.getFormatKind(rec));
            out.writeTypedValue(rec.getParameters());
        }

        @Override
        public int inferSize(LogRecord rec) {
            Object[] params = rec.getParameters();
            return Integer.BYTES + optstrsize(rec.getLoggerName()) + optstrsize(rec.getMessage()) + optstrsize(rec.getSourceClassName()) + optstrsize(rec.getSourceMethodName()) + 10 +
                            (params == null ? 0 : params.length) * PARAMETER_SIZE_ESTIMATE;
        }
    }

    private static final class CharSequenceMarshaller implements BinaryMarshaller<CharSequence> {

        @Override
        public CharSequence read(Isolate<?> isolate, BinaryInput in) {
            return in.readUTF();
        }

        @Override
        public void write(BinaryOutput out, CharSequence object) {
            out.writeUTF(object.toString());
        }

        @Override
        public int inferSize(CharSequence object) {
            return Integer.BYTES + object.length();
        }
    }

    private static final class ByteSequenceMarshaller implements BinaryMarshaller<ByteSequence> {

        @Override
        public ByteSequence read(Isolate<?> isolate, BinaryInput in) {
            int len = in.readInt();
            byte[] bytes = new byte[len];
            in.read(bytes, 0, len);
            return ByteSequence.create(bytes);
        }

        @Override
        public void write(BinaryOutput out, ByteSequence object) {
            int len = object.length();
            out.writeInt(len);
            out.write(object.toByteArray(), 0, len);
        }

        @Override
        public int inferSize(ByteSequence object) {
            return Integer.BYTES + object.length();
        }
    }

    private static final class ByteBufferMarshaller implements BinaryMarshaller<ByteBuffer> {

        @Override
        public ByteBuffer read(Isolate<?> isolate, BinaryInput in) {
            int capacity = in.readInt();
            int limit = in.readInt();
            int position = in.readInt();
            byte[] array = new byte[capacity];
            in.read(array, 0, capacity);
            ByteBuffer buffer = ByteBuffer.wrap(array);
            buffer.limit(limit);
            buffer.position(position);
            return buffer;
        }

        @Override
        public void write(BinaryOutput out, ByteBuffer object) {
            // We can do better for direct ByteBuffers passed from HotSpot to Native where we can
            // just reuse the CCharPointer, unfortunately we cannot do it in Native to HotSpot
            // direction.
            int capacity = object.capacity();
            int limit = object.limit();
            int position = object.position();
            out.writeInt(capacity);
            out.writeInt(limit);
            out.writeInt(position);
            try {
                object.limit(capacity);
                for (int i = 0; i < object.capacity(); i++) {
                    out.writeByte(object.get(i));
                }
            } finally {
                object.limit(limit);
            }
        }

        @Override
        public void readUpdate(Isolate<?> isolate, BinaryInput in, ByteBuffer buffer) {
            int capacity = in.readInt();
            assert capacity == buffer.capacity() : "Invalid buffer instance";
            int limit = in.readInt();
            int position = in.readInt();
            if (!buffer.isReadOnly()) {
                if (buffer.hasArray()) {
                    byte[] array = buffer.array();
                    in.read(array, 0, capacity);
                } else {
                    byte[] array = new byte[capacity];
                    in.read(array, 0, capacity);
                    buffer.limit(capacity);
                    buffer.position(0);
                    buffer.put(array);
                }
            }
            buffer.limit(limit);
            buffer.position(position);
        }

        @Override
        public void writeUpdate(BinaryOutput output, ByteBuffer object) {
            write(output, object);
        }

        @Override
        public int inferSize(ByteBuffer object) {
            return Integer.BYTES * 3 + object.capacity();
        }

        @Override
        public int inferUpdateSize(ByteBuffer object) {
            return inferSize(object);
        }
    }

    /**
     * A marshaller for pure write operations. The marshaller transfers the buffer content only from
     * caller to callee, the buffer content is not transferred back from callee to caller.
     */
    private static final class WriteOnlyByteBufferMarshaller implements BinaryMarshaller<ByteBuffer> {

        @Override
        public ByteBuffer read(Isolate<?> isolate, BinaryInput in) {
            int len = in.readInt();
            return in.asByteBuffer(len);
        }

        @Override
        public void write(BinaryOutput out, ByteBuffer buffer) {
            int pos = buffer.position();
            int limit = buffer.limit();
            int len = limit - pos;
            out.writeInt(len);
            if (buffer.hasArray()) {
                byte[] array = buffer.array();
                out.write(array, pos, limit);
            } else {
                byte[] array = new byte[len];
                try {
                    buffer.get(array);
                } finally {
                    buffer.position(pos);
                }
                out.write(array, 0, len);
            }
        }

        @Override
        public void writeUpdate(BinaryOutput out, ByteBuffer object) {
            int pos = object.position();
            out.writeInt(pos);
        }

        @Override
        public void readUpdate(Isolate<?> isolate, BinaryInput in, ByteBuffer buffer) {
            int len = in.readInt();
            buffer.position(buffer.position() + len);
        }

        @Override
        public int inferSize(ByteBuffer object) {
            int len = object.limit() - object.position();
            return Integer.BYTES + len;
        }

        @Override
        public int inferUpdateSize(ByteBuffer object) {
            return Integer.BYTES;
        }
    }

    /**
     * A marshaller for pure read operations. The marshaller does not transfer the buffer content
     * from caller to callee, the content is transferred only back from callee to caller.
     */
    private static final class ReadOnlyByteBufferMarshaller implements BinaryMarshaller<ByteBuffer> {

        @Override
        public ByteBuffer read(Isolate<?> isolate, BinaryInput in) {
            int limit = in.readInt();
            return ByteBuffer.allocate(limit);
        }

        @Override
        public void write(BinaryOutput out, ByteBuffer object) {
            out.writeInt(object.limit() - object.position());
        }

        @Override
        public void writeUpdate(BinaryOutput out, ByteBuffer object) {
            int pos = object.position();
            out.writeInt(pos);
            out.write(object.array(), 0, object.position());
        }

        @Override
        public void readUpdate(Isolate<?> isolate, BinaryInput in, ByteBuffer buffer) {
            int len = in.readInt();
            if (!buffer.isReadOnly()) {
                if (buffer.hasArray()) {
                    int pos = buffer.position();
                    byte[] array = buffer.array();
                    in.read(array, pos, len);
                    buffer.position(pos + len);
                } else {
                    byte[] array = new byte[len];
                    in.read(array, 0, len);
                    buffer.put(array);
                }
            }
        }

        @Override
        public int inferSize(ByteBuffer object) {
            return Integer.BYTES;
        }

        @Override
        public int inferUpdateSize(ByteBuffer object) {
            return Integer.BYTES + object.position();
        }
    }

    private static final class EnvironmentAccessMarshaller implements BinaryMarshaller<Object> {

        private static final int NONE = 0;
        private static final int INHERIT = 1;

        @Override
        public Object read(Isolate<?> isolate, BinaryInput in) {
            int index = in.readInt();
            APIAccess apiAccess = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess();
            return switch (index) {
                case NONE -> apiAccess.getEnvironmentAccessNone();
                case INHERIT -> apiAccess.getEnvironmentAccessInherit();
                default -> throw new IllegalArgumentException("Unsupported environment access " + index);
            };
        }

        @Override
        public void write(BinaryOutput out, Object object) {
            APIAccess apiAccess = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess();
            int index;
            if (object == apiAccess.getEnvironmentAccessNone()) {
                index = NONE;
            } else if (object == apiAccess.getEnvironmentAccessInherit()) {
                index = INHERIT;
            } else {
                throw new IllegalArgumentException("Unsupported environment access " + object);
            }
            out.writeInt(index);
        }

        @Override
        public int inferSize(Object object) {
            return Integer.BYTES;
        }
    }

    private static final class SourceCopyMarshaller implements BinaryMarshaller<Object> {

        @SuppressWarnings("deprecation")
        @Override
        public Object read(Isolate<?> isolate, BinaryInput in) {
            try {
                String name = in.readUTF();
                String path = (String) in.readTypedValue();
                String lang = in.readUTF();
                String strUri = (String) in.readTypedValue();
                URI uri = strUri == null ? null : URI.create(strUri);
                String strUrl = (String) in.readTypedValue();
                URL url = strUrl == null ? null : new URL(strUrl);
                String mimeType = (String) in.readTypedValue();
                boolean interactive = in.readBoolean();
                boolean internal = in.readBoolean();
                boolean cached = in.readBoolean();

                Map<String, String> options = new LinkedHashMap<>();
                int size = in.readInt();
                for (int i = 0; i < size; i++) {
                    String key = in.readUTF();
                    String value = in.readUTF();
                    options.put(key, value);
                }

                boolean hasCharacters = in.readBoolean();
                boolean hasBytes = in.readBoolean();
                Object content;
                if (hasCharacters) {
                    content = in.readUTF();
                } else if (hasBytes) {
                    int len = in.readInt();
                    byte[] bytes = new byte[len];
                    in.read(bytes, 0, len);
                    content = ByteSequence.create(bytes);
                } else {
                    content = CONTENT_NONE;
                }
                return PolyglotIsolateHostSupport.getPolyglot().buildSource(lang, content, uri, name, mimeType, content, interactive, internal, cached, null, url, path, options);
            } catch (IOException ioe) {
                throw CompilerDirectives.shouldNotReachHere(ioe);
            }
        }

        @Override
        public void write(BinaryOutput out, Object source) {
            AbstractPolyglotImpl polyglot = PolyglotIsolateHostSupport.getPolyglot();
            AbstractSourceDispatch sourceDispatch = polyglot.getAPIAccess().getSourceDispatch(source);
            Object sourceReceiver = polyglot.getAPIAccess().getSourceReceiver(source);
            out.writeUTF(sourceDispatch.getName(sourceReceiver));
            out.writeTypedValue(sourceDispatch.getPath(sourceReceiver));
            out.writeUTF(sourceDispatch.getLanguage(sourceReceiver));
            /*
             * We want to copy just the original user-specified URI, if there is one. If the
             * original URI is null, calling the getURI method triggers URI computation that we want
             * to execute only when the URI is really needed.
             */
            URI uri = sourceDispatch.getOriginalURI(sourceReceiver);
            String strUri = uri == null ? null : uri.toString();
            out.writeTypedValue(strUri);
            URL url = sourceDispatch.getURL(sourceReceiver);
            String strUrl = url == null ? null : url.toString();
            out.writeTypedValue(strUrl);
            String mimeType = sourceDispatch.getMimeType(sourceReceiver);
            out.writeTypedValue(mimeType);
            out.writeBoolean(sourceDispatch.isInteractive(sourceReceiver));
            out.writeBoolean(sourceDispatch.isInternal(sourceReceiver));
            out.writeBoolean(sourceDispatch.isCached(sourceReceiver));

            Map<String, String> options = sourceDispatch.getOptions(sourceReceiver);
            out.writeInt(options.size());
            for (var entry : options.entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeUTF(entry.getValue());
            }

            boolean hasCharacters = sourceDispatch.hasCharacters(sourceReceiver);
            boolean hasBytes = sourceDispatch.hasBytes(sourceReceiver);
            out.writeBoolean(hasCharacters);
            out.writeBoolean(hasBytes);
            if (hasCharacters) {
                out.writeUTF(sourceDispatch.getCharacters(sourceReceiver).toString());
            } else if (hasBytes) {
                byte[] bytes = sourceDispatch.getByteArray(sourceReceiver);
                out.writeInt(bytes.length);
                out.write(bytes, 0, bytes.length);
            }
        }

        @Override
        public int inferSize(Object source) {
            int size = 0;
            AbstractPolyglotImpl polyglot = PolyglotIsolateHostSupport.getPolyglot();
            AbstractSourceDispatch sourceDispatch = polyglot.getAPIAccess().getSourceDispatch(source);
            Object sourceReceiver = polyglot.getAPIAccess().getSourceReceiver(source);
            size += strsize(sourceDispatch.getName(sourceReceiver));
            size += strsize(sourceDispatch.getLanguage(sourceReceiver));
            /*
             * We want to copy just the original user-specified URI, if there is one. If the
             * original URI is null, calling the getURI method triggers URI computation that we want
             * to execute only when the URI is really needed.
             */
            URI uri = sourceDispatch.getOriginalURI(sourceReceiver);
            String strUri = uri == null ? "" : uri.toString();
            size += strsize(strUri);
            String mimeType = sourceDispatch.getMimeType(sourceReceiver);
            mimeType = mimeType == null ? "" : mimeType;
            size += strsize(mimeType);
            size += 5;
            if (sourceDispatch.hasCharacters(sourceReceiver) || sourceDispatch.hasBytes(sourceReceiver)) {
                size += Integer.BYTES + sourceDispatch.getLength(sourceReceiver);
            }
            return size;
        }
    }

    private static final class SourceMarshaller implements BinaryMarshaller<Object> {

        @Override
        public Object read(Isolate<?> isolate, BinaryInput in) {
            return deserializeSource(in, isolate, PolyglotIsolateHostSupport.getPolyglot().getAPIAccess());
        }

        @Override
        public void write(BinaryOutput out, Object object) {
            if (isHost()) {
                throw new UnsupportedOperationException("Not supported for HotSpot to Native.");
            }
            serializeSource(out, object);
        }

        static void serializeSource(BinaryOutput out, Object source) {
            // We can do more here:
            // 1) We can detect host sources and do not use them by reference. We can use the host
            // source
            // 2) We can canonicalize sources
            out.writeLong(ReferenceHandles.create(source));
        }

        static Object deserializeSource(BinaryInput in, Isolate<?> isolate, APIAccess api) {
            long sourceHandle = in.readLong();
            ForeignObject sourceReceiver = ForeignObject.createUnbound(Peer.create(isolate, sourceHandle));
            return api.newSource(PolyglotIsolateHostSupport.getSourceDispatch(sourceReceiver), sourceReceiver);
        }

        @Override
        public int inferSize(Object object) {
            return 2 * Long.BYTES;
        }
    }

    private static final class SourceSectionMarshaller implements BinaryMarshaller<Object> {

        @Override
        public Object read(Isolate<?> isolate, BinaryInput in) {
            APIAccess api = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess();
            Object source = SourceMarshaller.deserializeSource(in, isolate, api);
            long sourceSectionHandle = in.readLong();
            ForeignObject sourceSectionReceiver = ForeignObject.createUnbound(Peer.create(isolate, sourceSectionHandle));
            return api.newSourceSection(source, PolyglotIsolateHostSupport.getSourceSectionDispatch(sourceSectionReceiver), sourceSectionReceiver);
        }

        @Override
        public void write(BinaryOutput out, Object sourceSection) {
            if (isHost()) {
                throw new UnsupportedOperationException("Not supported for HotSpot to Native.");
            }
            APIAccess api = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess();
            SourceMarshaller.serializeSource(out, api.getSourceSectionSource(sourceSection));
            out.writeLong(ReferenceHandles.create(sourceSection));
        }

        @Override
        public int inferSize(Object object) {
            return 3 * Long.BYTES;
        }
    }

    private static final class CharsetMarshaller implements BinaryMarshaller<Charset> {

        @Override
        public Charset read(Isolate<?> isolate, BinaryInput input) {
            String name = input.readUTF();
            return Charset.forName(name);
        }

        @Override
        public void write(BinaryOutput output, Charset object) {
            output.writeUTF(object.name());
        }

        @Override
        public int inferSize(Charset object) {
            return strsize(object.name());
        }
    }

    private static final class FileAttributeMarshaller implements BinaryMarshaller<FileAttribute<?>> {

        private static final int ATTRIBUTE_SIZE_ESTIMATE = 14;
        private static final byte POSIX_PERMISSIONS = 1;
        private final BinaryMarshaller<Set<PosixFilePermission>> posixFilePermissionsMarshaller;

        FileAttributeMarshaller(BinaryMarshaller<Set<PosixFilePermission>> posixFilePermissionsMarshaller) {
            this.posixFilePermissionsMarshaller = Objects.requireNonNull(posixFilePermissionsMarshaller);
        }

        @Override
        public FileAttribute<?> read(Isolate<?> isolate, BinaryInput input) {
            byte type = input.readByte();
            if (type == POSIX_PERMISSIONS) {
                return PosixFilePermissions.asFileAttribute(posixFilePermissionsMarshaller.read(isolate, input));
            }
            throw new IllegalArgumentException(String.valueOf(type));
        }

        @Override
        public void write(BinaryOutput output, FileAttribute<?> attr) {
            if ("posix:permissions".equals(attr.name())) {
                output.writeByte(POSIX_PERMISSIONS);
                posixFilePermissionsMarshaller.write(output, asPosixFilePermissionsSet(attr.value()));
            } else {
                throw new IllegalArgumentException(String.valueOf(attr));
            }
        }

        @SuppressWarnings("unchecked")
        static Set<PosixFilePermission> asPosixFilePermissionsSet(Object object) {
            return (Set<PosixFilePermission>) object;
        }

        @Override
        public int inferSize(FileAttribute<?> object) {
            return ATTRIBUTE_SIZE_ESTIMATE;
        }
    }

    private static final class FileAttributeValueMarshaller implements BinaryMarshaller<Object> {

        static final int ATTRIBUTE_SIZE_ESTIMATE = 20;
        private static final byte NULL = 0;
        private static final byte BOOLEAN = 1;
        private static final byte INT = 2;
        private static final byte LONG = 3;
        private static final byte FILE_TIME = 4;
        private static final byte GROUP_PRINCIPAL = 5;
        private static final byte USER_PRINCIPAL = 6;
        private static final byte POSIX_PERMISSIONS = 7;

        private final BinaryMarshaller<Set<PosixFilePermission>> posixFilePermissionsMarshaller;

        FileAttributeValueMarshaller(BinaryMarshaller<Set<PosixFilePermission>> posixFilePermissionsMarshaller) {
            this.posixFilePermissionsMarshaller = Objects.requireNonNull(posixFilePermissionsMarshaller);
        }

        @Override
        public Object read(Isolate<?> isolate, BinaryInput input) {
            byte type = input.readByte();
            switch (type) {
                case NULL:
                    return null;
                case BOOLEAN:
                    return input.readBoolean();
                case INT:
                    return input.readInt();
                case LONG:
                    return input.readLong();
                case FILE_TIME:
                    long sec = input.readLong();
                    long nano = input.readLong();
                    return FileTime.from(Instant.ofEpochSecond(sec, nano));
                case GROUP_PRINCIPAL:
                    int gid = input.readInt();
                    String groupName = input.readUTF();
                    return new Group(gid, groupName);
                case USER_PRINCIPAL:
                    int uid = input.readInt();
                    String userName = input.readUTF();
                    return new User(uid, userName);
                case POSIX_PERMISSIONS:
                    return posixFilePermissionsMarshaller.read(isolate, input);
                default:
                    throw new IllegalArgumentException(String.valueOf(type));
            }
        }

        @Override
        public void write(BinaryOutput output, Object object) {
            if (object == null) {
                output.writeByte(NULL);
            } else if (object instanceof Boolean) {
                output.writeByte(BOOLEAN);
                output.writeBoolean((boolean) object);
            } else if (object instanceof Integer) {
                output.writeByte(INT);
                output.writeInt((int) object);
            } else if (object instanceof Long) {
                output.writeByte(LONG);
                output.writeLong((long) object);
            } else if (object instanceof FileTime) {
                output.writeByte(FILE_TIME);
                Instant instant = ((FileTime) object).toInstant();
                output.writeLong(instant.getEpochSecond());
                output.writeLong(instant.getNano());
            } else if (object instanceof GroupPrincipal) {
                output.writeByte(GROUP_PRINCIPAL);
                output.writeInt(object.hashCode());
                output.writeUTF(((GroupPrincipal) object).getName());
            } else if (object instanceof UserPrincipal) {
                output.writeByte(USER_PRINCIPAL);
                output.writeInt(object.hashCode());
                output.writeUTF(((UserPrincipal) object).getName());
            } else if (isPosixFilePermissionsSet(object)) {
                output.writeByte(POSIX_PERMISSIONS);
                posixFilePermissionsMarshaller.write(output, asPosixFilePermissionsSet(object));
            } else {
                throw new IllegalArgumentException(object + ": " + object.getClass());
            }
        }

        private static boolean isPosixFilePermissionsSet(Object object) {
            if (object instanceof Set<?> set) {
                return set.isEmpty() || set.iterator().next() instanceof PosixFilePermission;
            }
            return false;
        }

        @SuppressWarnings("unchecked")
        private static Set<PosixFilePermission> asPosixFilePermissionsSet(Object object) {
            return (Set<PosixFilePermission>) object;
        }

        @Override
        public int inferSize(Object object) {
            return ATTRIBUTE_SIZE_ESTIMATE;
        }

        private static class User implements UserPrincipal {

            private final int id;
            private final String name;

            User(int id, String name) {
                this.id = id;
                this.name = name;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public int hashCode() {
                return id;
            }

            @Override
            public boolean equals(Object another) {
                if (another == this) {
                    return true;
                }
                if (another instanceof UserPrincipal) {
                    return hashCode() == another.hashCode() && Objects.equals(name, ((UserPrincipal) another).getName());
                }
                return false;
            }

            @Override
            public String toString() {
                return name;
            }
        }

        private static final class Group extends User implements GroupPrincipal {
            Group(int id, String name) {
                super(id, name);
            }
        }
    }

    private static final class FileAttributeMapMarshaller implements BinaryMarshaller<Map<String, Object>> {

        private static final int KEY_SIZE_ESTIMATE = 20;
        private final FileAttributeValueMarshaller fileAttributeValueMarshaller;

        FileAttributeMapMarshaller(FileAttributeValueMarshaller fileAttributeValueMarshaller) {
            this.fileAttributeValueMarshaller = Objects.requireNonNull(fileAttributeValueMarshaller);
        }

        @Override
        public Map<String, Object> read(Isolate<?> isolate, BinaryInput input) {
            int size = input.readInt();
            Map<String, Object> res = new HashMap<>(size);
            for (int i = 0; i < size; i++) {
                String key = input.readUTF();
                Object value = fileAttributeValueMarshaller.read(isolate, input);
                res.put(key, value);
            }
            return res;
        }

        @Override
        public void write(BinaryOutput output, Map<String, Object> map) {
            output.writeInt(map.size());
            for (Entry<String, Object> e : map.entrySet()) {
                String attrName = e.getKey();
                Object attrValue = e.getValue();
                if ("fileKey".equals(attrName)) {
                    // TruffleFile#visit reads all "basic:*" attributes. We cannot serialize FS
                    // specific "basic:fileKey" attribute, we replace it by null.
                    attrValue = null;
                }
                output.writeUTF(attrName);
                fileAttributeValueMarshaller.write(output, attrValue);
            }
        }

        @Override
        public int inferSize(Map<String, Object> map) {
            return Integer.BYTES + map.size() * (FileAttributeValueMarshaller.ATTRIBUTE_SIZE_ESTIMATE + KEY_SIZE_ESTIMATE);
        }
    }

    private static final class ZoneIdMarshaller implements BinaryMarshaller<ZoneId> {

        @Override
        public ZoneId read(Isolate<?> isolate, BinaryInput in) {
            String id = in.readUTF();
            return ZoneId.of(id);
        }

        @Override
        public void write(BinaryOutput out, ZoneId object) {
            out.writeUTF(object.getId());
        }

        @Override
        public int inferSize(ZoneId object) {
            return strsize(object.getId());
        }
    }

    private static final class ValueMarshaller implements BinaryMarshaller<Object> {

        private static final int VALUE_SIZE_ESTIMATE = 128;

        @Override
        public Object read(Isolate<?> isolate, BinaryInput in) {
            if (isHost()) {
                long contextHandle = in.readLong();
                ForeignContext foreignContext = (ForeignContext) PolyglotIsolateHostSupport.findContextByHandle(isolate, contextHandle);
                Object localContextReceiver = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().getContextReceiver(foreignContext.getLocalContext());
                return PolyglotIsolateAccessor.ENGINE.asValue(localContextReceiver, BinaryProtocol.readHostTypedValue(in, foreignContext));
            } else {
                throw new UnsupportedOperationException("Not supported on the guest.");
            }
        }

        @Override
        public void write(BinaryOutput out, Object value) {
            if (!ImageSingletons.contains(PolyglotIsolateGuestFeatureEnabled.class)) {
                // Don't include guest classes when PolyglotIsolateGuestFeature is not enabled
                throw new UnsupportedOperationException("Not supported on the guest when the PolyglotIsolateGuestFeature is not enabled.");
            } else if (isHost()) {
                throw new UnsupportedOperationException("Not supported on the host.");
            }
            APIAccess apiAccess = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess();
            Object contextReceiver = apiAccess.getContextReceiver(((Value) value).getContext());
            long contextId = findContextHandleByContextReceiver(contextReceiver);
            out.writeLong(contextId);
            Object valueReceiver = apiAccess.getValueReceiver(value);
            GuestContext guestContext = ReferenceHandles.resolve(contextId, GuestContext.class);
            BinaryProtocol.writeGuestTypedValue(out, valueReceiver, guestContext.hostToGuestObjectReferences);
        }

        @Override
        public int inferSize(Object object) {
            return VALUE_SIZE_ESTIMATE;
        }
    }

    private static final class ValueListMarshaller implements BinaryMarshaller<List<Object>> {

        @Override
        public List<Object> read(Isolate<?> isolate, BinaryInput in) {
            if (isHost()) {
                int size = in.readInt();
                if (size == 0) {
                    return Collections.emptyList();
                }
                long contextHandle = in.readLong();
                ForeignContext foreignContext = (ForeignContext) PolyglotIsolateHostSupport.findContextByHandle(isolate, contextHandle);
                Object localContextReceiver = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().getContextReceiver(foreignContext.getLocalContext());

                List<Object> result = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    Object value = PolyglotIsolateAccessor.ENGINE.asValue(localContextReceiver, BinaryProtocol.readHostTypedValue(in, foreignContext));
                    result.add(value);
                }
                return result;
            } else {
                throw new UnsupportedOperationException("Not supported on the guest.");
            }
        }

        @Override
        public void write(BinaryOutput out, List<Object> values) {
            if (!ImageSingletons.contains(PolyglotIsolateGuestFeatureEnabled.class)) {
                // Don't include guest classes when PolyglotIsolateGuestFeature is not enabled
                throw new UnsupportedOperationException("Not supported on the guest when the PolyglotIsolateGuestFeature is not enabled.");
            } else if (isHost()) {
                throw new UnsupportedOperationException("Not supported on the host.");
            }
            int size = values.size();
            out.writeInt(size);
            if (size > 0) {
                APIAccess apiAccess = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess();
                Object contextReceiver = apiAccess.getContextReceiver(((Value) values.get(0)).getContext());
                long contextId = findContextHandleByContextReceiver(contextReceiver);
                out.writeLong(contextId);
                GuestContext guestContext = ReferenceHandles.resolve(contextId, GuestContext.class);
                GuestObjectReferences hostToGuestObjectReceiver = guestContext.hostToGuestObjectReferences;
                for (Object value : values) {
                    Object valueReceiver = apiAccess.getValueReceiver(value);
                    BinaryProtocol.writeGuestTypedValue(out, valueReceiver, hostToGuestObjectReceiver);
                }
            }
        }

        @Override
        public int inferSize(List<Object> object) {
            return Integer.BYTES + 2 * Long.BYTES + object.size() * (Long.BYTES + 1);
        }
    }

    private static final class PolyglotAccessMarshaller implements BinaryMarshaller<Object> {

        private static final int BINDING_SIZE_ESTIMATE = 48;

        @Override
        public Object read(Isolate<?> isolate, BinaryInput in) {
            APIAccess apiAccess = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess();
            boolean allAccess = in.readBoolean();
            if (allAccess) {
                return apiAccess.getPolyglotAccessAll();
            }
            int bindingsSize = in.readInt();
            int evalSize = in.readInt();
            if (bindingsSize == 0 && evalSize == 0) {
                return apiAccess.getPolyglotAccessNone();
            }
            Set<String> bindingsAccess = new HashSet<>(bindingsSize);
            for (int i = 0; i < bindingsSize; i++) {
                String lang = in.readUTF();
                bindingsAccess.add(lang);
            }
            Map<String, Set<String>> evalAccess = new HashMap<>(evalSize);
            for (int i = 0; i < evalSize; i++) {
                String fromLang = in.readUTF();
                int valuesSize = in.readInt();
                Set<String> to = new HashSet<>(valuesSize);
                for (int j = 0; j < valuesSize; j++) {
                    String toLang = in.readUTF();
                    to.add(toLang);
                }
                evalAccess.put(fromLang, to);
            }
            return apiAccess.createPolyglotAccess(bindingsAccess, evalAccess);
        }

        @Override
        public void write(BinaryOutput out, Object polyglotAccess) {
            APIAccess apiAccess = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess();
            Set<String> bindingsAccess = apiAccess.getBindingsAccess(polyglotAccess);
            boolean allAccess = bindingsAccess == null;
            Map<String, Set<String>> evalAccess = apiAccess.getEvalAccess(polyglotAccess);
            assert !allAccess || evalAccess == null;
            out.writeBoolean(allAccess);
            if (!allAccess) {
                out.writeInt(bindingsAccess.size());
                out.writeInt(evalAccess.size());
                for (String lang : bindingsAccess) {
                    out.writeUTF(lang);
                }
                for (Entry<String, Set<String>> entry : evalAccess.entrySet()) {
                    out.writeUTF(entry.getKey());
                    Set<String> value = entry.getValue();
                    out.writeInt(value.size());
                    for (String lang : value) {
                        out.writeUTF(lang);
                    }
                }
            }
        }

        @Override
        public int inferSize(Object polyglotAccess) {
            APIAccess apiAccess = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess();
            Set<String> bindingsAccess = apiAccess.getBindingsAccess(polyglotAccess);
            int size = 1;
            if (bindingsAccess != null) {
                size += bindingsAccess.size() * BINDING_SIZE_ESTIMATE;
            }
            return size;
        }
    }

    private static final class IOAccessMarshaller implements BinaryMarshaller<Object> {

        @Override
        public Object read(Isolate<?> isolate, BinaryInput input) {
            boolean allowHostFileAccess = input.readBoolean();
            boolean allowSocketAccess = input.readBoolean();
            // IOAccess#fileSystem cannot be serialized, it must be passed by reference as a
            // separate parameter.
            IOAccessor ioAccessor = PolyglotIsolateHostSupport.getPolyglot().getIO();
            return ioAccessor.createIOAccess(null, allowHostFileAccess, allowSocketAccess, null);
        }

        @Override
        public void write(BinaryOutput output, Object ioAccess) {
            IOAccessor ioAccessor = PolyglotIsolateHostSupport.getPolyglot().getIO();
            output.writeBoolean(ioAccessor.hasHostFileAccess(ioAccess));
            output.writeBoolean(ioAccessor.hasHostSocketAccess(ioAccess));
            // IOAccess#fileSystem cannot be serialized, it must be passed by reference as a
            // separate parameter.
        }

        @Override
        public int inferSize(Object object) {
            return 2;
        }
    }

    private static final class InteropObjectMarshaller implements BinaryMarshaller<Object> {

        @Override
        public Object read(Isolate<?> isolate, BinaryInput in) {
            if (isHost()) {
                // Host
                AbstractPolyglotImpl polyglot = PolyglotIsolateHostSupport.getPolyglot();
                ForeignContext foreignContext = (ForeignContext) polyglot.getAPIAccess().getContextReceiver(polyglot.getCurrentContext());
                return BinaryProtocol.readHostTypedValue(in, foreignContext);
            } else if (ImageSingletons.contains(PolyglotIsolateGuestFeatureEnabled.class)) {
                // Guest
                long contextId = in.readLong();
                return BinaryProtocol.readGuestTypedValue(in, ReferenceHandles.resolve(contextId, GuestContext.class));
            } else {
                // Don't include guest classes when PolyglotIsolateGuestFeature is not enabled
                throw new UnsupportedOperationException("Not supported on the guest when the PolyglotIsolateGuestFeature is not enabled.");
            }
        }

        @Override
        public void write(BinaryOutput out, Object object) {
            if (!(object instanceof TruffleObject)) {
                throw CompilerDirectives.shouldNotReachHere("Must be a truffle object");
            }
            AbstractPolyglotImpl polyglot = PolyglotIsolateHostSupport.getPolyglot();
            if (isHost()) {
                // Host
                ForeignContext foreignContext = (ForeignContext) polyglot.getAPIAccess().getContextReceiver(polyglot.getCurrentContext());
                long contextHandle = foreignContext.getPeer().getHandle();
                out.writeLong(contextHandle);
                BinaryProtocol.writeHostTypedValue(out, object, foreignContext.getGuestToHostReceiver());
            } else if (ImageSingletons.contains(PolyglotIsolateGuestFeatureEnabled.class)) {
                // Guest
                Object contextImpl = polyglot.getAPIAccess().getContextReceiver(polyglot.getCurrentContext());
                GuestContext guestContext = ReferenceHandles.resolve(findContextHandleByContextReceiver(contextImpl), GuestContext.class);
                BinaryProtocol.writeGuestTypedValue(out, object, guestContext.hostToGuestObjectReferences);
            } else {
                // Don't include guest classes when PolyglotIsolateGuestFeature is not enabled
                throw new UnsupportedOperationException("Not supported on the guest when the PolyglotIsolateGuestFeature is not enabled.");
            }
        }

        @Override
        public int inferSize(Object object) {
            return 2 * Long.BYTES + 1;
        }
    }

    /**
     * We don't have bridge for the {@link com.oracle.truffle.api.TruffleFile} yet, the
     * {@link com.oracle.truffle.api.TruffleFile} is passed as a path string and recreated in the
     * host side.
     */
    private static final class TruffleFileMarshaller implements BinaryMarshaller<Object> {

        @Override
        public Object read(Isolate<?> isolate, BinaryInput in) {
            boolean internal = in.readBoolean();
            String path = in.readUTF();
            Object hostLanguageContext = PolyglotIsolateAccessor.ENGINE.getCurrentHostContext();
            Object fsContext = internal ? PolyglotIsolateAccessor.ENGINE.getInternalFileSystemContext(hostLanguageContext)
                            : PolyglotIsolateAccessor.ENGINE.getPublicFileSystemContext(hostLanguageContext);
            return PolyglotIsolateAccessor.LANGUAGE.getTruffleFile(path, fsContext);
        }

        @Override
        public void write(BinaryOutput out, Object object) {
            com.oracle.truffle.api.TruffleFile file = (com.oracle.truffle.api.TruffleFile) object;
            out.writeBoolean(PolyglotIsolateAccessor.ENGINE.isInternal(file));
            out.writeUTF(file.getPath());
        }

        @Override
        public int inferSize(Object object) {
            return 1 + strsize(((com.oracle.truffle.api.TruffleFile) object).getPath());
        }
    }

    private static final class HostContextMarshaller implements BinaryMarshaller<Object> {

        @Override
        public Object read(Isolate<?> isolate, BinaryInput in) {
            long contextId = in.readLong();
            ForeignContext foreignContext = (ForeignContext) PolyglotIsolateHostSupport.findContextByHandle(isolate, contextId);
            Object localContext = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().getContextReceiver(foreignContext.getLocalContext());
            return PolyglotIsolateAccessor.ENGINE.getHostContext(localContext);
        }

        @Override
        public void write(BinaryOutput out, Object object) {
            GuestHostLanguage.GuestHostLanguageContext hostContext = (GuestHostLanguage.GuestHostLanguageContext) object;
            out.writeLong(PolyglotIsolateHostSupport.findContextHandleByContextReceiver(hostContext.internalOuterContext));
        }

        @Override
        public int inferSize(Object object) {
            return 2 * Long.BYTES;
        }
    }

    private static final class ContextReceiverMarshaller implements BinaryMarshaller<Object> {

        @Override
        public Object read(Isolate<?> isolate, BinaryInput in) {
            if (isHost()) {
                long contextId = in.readLong();
                return PolyglotIsolateHostSupport.findContextByHandle(isolate, contextId);
            } else {
                throw new UnsupportedOperationException("Not supported on the guest.");
            }
        }

        @Override
        public void write(BinaryOutput out, Object contextReceiver) {
            if (!ImageSingletons.contains(PolyglotIsolateGuestFeatureEnabled.class)) {
                // Don't include guest classes when PolyglotIsolateGuestFeature is not enabled
                throw new UnsupportedOperationException("Not supported on the guest when the PolyglotIsolateGuestFeature is not enabled.");
            } else if (isHost()) {
                throw new UnsupportedOperationException("Not supported on the host.");
            }
            assert contextReceiver == PolyglotIsolateAccessor.ENGINE.getOuterContext(contextReceiver);
            long contextHandle = 0L;
            if (!PolyglotIsolateGuestSupport.lazy.disposed) {
                contextHandle = PolyglotIsolateHostSupport.findContextHandleByContextReceiver(contextReceiver);
            }
            out.writeLong(contextHandle);
        }

        @Override
        public int inferSize(Object object) {
            return 2 * Long.BYTES;
        }
    }

    private static final class EngineReceiverMarshaller implements BinaryMarshaller<Object> {

        @Override
        public Object read(Isolate<?> isolate, BinaryInput in) {
            if (isHost()) {
                long engineId = in.readLong();
                return PolyglotIsolateHostSupport.findEngineReceiverByHandle(isolate, engineId);
            } else {
                throw new UnsupportedOperationException("Not supported on the guest.");
            }
        }

        @Override
        public void write(BinaryOutput out, Object engineReceiver) {
            if (!ImageSingletons.contains(PolyglotIsolateGuestFeatureEnabled.class)) {
                // Don't include guest classes when PolyglotIsolateGuestFeature is not enabled
                throw new UnsupportedOperationException("Not supported on the guest when the PolyglotIsolateGuestFeature is not enabled.");
            } else if (isHost()) {
                throw new UnsupportedOperationException("Not supported on the host.");
            }
            PolyglotIsolateGuestSupport.Lazy l = PolyglotIsolateGuestSupport.lazy;
            long engineHandle = 0L;
            if (!l.disposed) {
                engineHandle = PolyglotIsolateHostSupport.findEngineHandleByEngineReceiver(engineReceiver);
            }
            out.writeLong(engineHandle);
        }

        @Override
        public int inferSize(Object object) {
            return 2 * Long.BYTES;
        }
    }

    private static final class ProcessCommandMarshaller implements BinaryMarshaller<ProcessCommand> {

        private static final byte PIPE = 0;
        private static final byte INHERIT = 1;
        private static final byte REDIRECT = 2;

        @Override
        public void write(BinaryOutput out, ProcessCommand command) {
            List<String> cmdLine = command.getCommand();
            out.writeInt(cmdLine.size());
            for (String cmd : cmdLine) {
                out.writeUTF(cmd);
            }
            out.writeTypedValue(command.getDirectory());
            Map<String, String> env = command.getEnvironment();
            out.writeInt(env.size());
            for (Entry<String, String> envKeyValuePair : env.entrySet()) {
                out.writeUTF(envKeyValuePair.getKey());
                out.writeUTF(envKeyValuePair.getValue());
            }
            out.writeBoolean(command.isRedirectErrorStream());
            serializeRedirect(out, command.getInputRedirect());
            serializeRedirect(out, command.getOutputRedirect());
            serializeRedirect(out, command.getErrorRedirect());
        }

        @Override
        public ProcessCommand read(Isolate<?> isolate, BinaryInput in) {
            int size = in.readInt();
            List<String> cmdLine = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                cmdLine.add(in.readUTF());
            }
            String cwd = (String) in.readTypedValue();
            size = in.readInt();
            Map<String, String> env = new HashMap<>();
            for (int i = 0; i < size; i++) {
                String key = in.readUTF();
                String value = in.readUTF();
                env.put(key, value);
            }
            boolean isRedirectErrorStream = in.readBoolean();
            Redirect inputRedirect = deserializeRedirect(isolate, in);
            Redirect outputRedirect = deserializeRedirect(isolate, in);
            Redirect errorRedirect = deserializeRedirect(isolate, in);
            return ProcessCommand.create(cmdLine, cwd, env, isRedirectErrorStream, inputRedirect, outputRedirect, errorRedirect);
        }

        private static void serializeRedirect(BinaryOutput out, Redirect redirect) {
            if (redirect == Redirect.PIPE) {
                out.writeByte(PIPE);
            } else if (redirect == Redirect.INHERIT) {
                out.writeByte(INHERIT);
            } else {
                // Redirect into stream.
                out.writeByte(REDIRECT);
                OutputStream outputStream = redirect.getOutputStream();
                out.writeLong(ReferenceHandles.create(outputStream));
            }
        }

        private static Redirect deserializeRedirect(Isolate<?> isolate, BinaryInput in) {
            byte kind = in.readByte();
            switch (kind) {
                case PIPE:
                    return Redirect.PIPE;
                case INHERIT:
                    return Redirect.INHERIT;
                case REDIRECT:
                    long objectHandle = in.readLong();
                    OutputStream outputStream = ForeignOutputStreamGen.create(Peer.create(isolate, objectHandle));
                    return Redirect.createRedirectToStream(outputStream);
                default:
                    throw new IllegalArgumentException("Unknown redirect " + kind);
            }
        }

        @Override
        public int inferSize(ProcessCommand command) {
            int size = Integer.BYTES;
            for (String cmd : command.getCommand()) {
                size += strsize(cmd);
            }
            size += optstrsize(command.getDirectory());
            size += Integer.BYTES;
            for (Entry<String, String> envKeyValuePair : command.getEnvironment().entrySet()) {
                size += strsize(envKeyValuePair.getKey());
                size += strsize(envKeyValuePair.getValue());
            }
            size += 1 + 2 * Long.BYTES;
            return size;
        }
    }

    private static final class ProcessHandleMarshaller implements BinaryMarshaller<ProcessHandle> {

        @Override
        public void write(BinaryOutput out, ProcessHandle processHandle) {
            out.writeLong(processHandle.pid());
        }

        @Override
        public ProcessHandle read(Isolate<?> isolate, BinaryInput in) {
            long pid = in.readLong();
            return ProcessHandle.of(pid).orElse(null);
        }
    }

    private static final class ThrowableMarshaller implements BinaryMarshaller<Throwable> {

        private static final int INTEROP_EXCEPTION_SIZE_ESTIMATE = 64;
        private static final int STACK_SIZE_ESTIMATE = 1024;

        static final ThrowableMarshaller INSTANCE = new ThrowableMarshaller();

        private final BinaryMarshaller<StackTraceElement[]> stackTraceMarshaller;

        private ThrowableMarshaller() {
            this.stackTraceMarshaller = MarshallerConfig.defaultStackTraceMarshaller();
        }

        @Override
        public void write(BinaryOutput out, Throwable t) {
            Throwable exception = t;
            ExceptionType et = ExceptionType.match(t);
            Object polyglotContextReceiver = null;
            long contextId = 0L;
            APIAccess apiAccess = PolyglotIsolateHostSupport.getPolyglot().getAPIAccess();
            if (et == ExceptionType.PolyglotException) {
                out.writeByte(ExceptionType.PolyglotException.id);
                RuntimeException polyglotException = (RuntimeException) exception;
                exception = PolyglotIsolateAccessor.ENGINE.getPolyglotExceptionCause(apiAccess.getPolyglotExceptionReceiver(polyglotException));
                et = ExceptionType.match(exception);
                Object engine = apiAccess.getPolyglotExceptionAPIEngine(polyglotException);
                Object context = apiAccess.getPolyglotExceptionAPIContext(polyglotException);
                long engineId = engine == null ? 0L : findEngineHandleByEngineReceiver(apiAccess.getEngineReceiver(engine));
                if (context != null) {
                    polyglotContextReceiver = apiAccess.getContextReceiver(context);
                    contextId = findContextHandleByContextReceiver(polyglotContextReceiver);
                }
                out.writeLong(engineId);
                out.writeLong(contextId);
            } else if (et == ExceptionType.PolyglotEngineException) {
                out.writeByte(ExceptionType.PolyglotEngineException.id);
                exception = PolyglotIsolateAccessor.ENGINE.getPolyglotEngineExceptionCause(t);
                et = ExceptionType.match(exception);
            }
            if (et == ExceptionType.AbstractTruffleExceptionReference) {
                if (contextId == 0L) {
                    if (isHost()) {
                        // Not a polyglot exception, try to find current context.
                        polyglotContextReceiver = findForeignContext(null, 0L);
                        contextId = polyglotContextReceiver != null ? ((ForeignContext) polyglotContextReceiver).getPeer().getHandle() : 0L;
                    } else if (PolyglotIsolateAccessor.ENGINE.hasCurrentContext()) {
                        Object contextReceiver = apiAccess.getContextReceiver(Context.getCurrent());
                        contextId = PolyglotIsolateHostSupport.findContextHandleByContextReceiver(contextReceiver);
                    }
                }
                if (contextId == 0L) {
                    writeDefault(out, ExceptionType.AbstractTruffleException, exception);
                } else {
                    out.writeByte(et.id);
                    if (isHost()) {
                        out.writeLong(contextId);
                        BinaryProtocol.writeHostTypedValue(out, exception, ((ForeignContext) polyglotContextReceiver).getGuestToHostReceiver());
                    } else if (ImageSingletons.contains(PolyglotIsolateGuestFeatureEnabled.class)) {
                        GuestContext guestContext = ReferenceHandles.resolve(contextId, GuestContext.class);
                        if (exception instanceof HSTruffleException hsTruffleException) {
                            /*
                             * Prevent unboxing of the host exception and instead pass its isolate
                             * proxy. The isolate proxy preserves the correct guest stack, while the
                             * host exception lacks guest language frames. For interleaved stacks
                             * such as guest_code, host_code, guest_callback, host_code, we must
                             * also ensure that the exception remains alive while unwinding through
                             * the guest_callback.
                             */
                            BinaryProtocol.writeTruffleExceptionWithoutUnboxing(out, hsTruffleException, guestContext.hostToGuestObjectReferences);
                        } else {
                            BinaryProtocol.writeGuestTypedValue(out, exception, guestContext.hostToGuestObjectReferences);
                        }
                    } else {
                        /*
                         * Don't include guest classes when PolyglotIsolateGuestFeature is not
                         * enabled
                         */
                        throw new UnsupportedOperationException("Not supported on the guest when the PolyglotIsolateGuestFeature is not enabled.");
                    }
                }
            } else if (et == ExceptionType.BinaryProtocolException) {
                out.writeByte(et.id);
                if (isHost()) {
                    BinaryProtocol.writeHostTypedValue(out, exception, null);
                } else if (ImageSingletons.contains(PolyglotIsolateGuestFeatureEnabled.class)) {
                    BinaryProtocol.writeGuestTypedValue(out, exception, null);
                } else {
                    // Don't include guest classes when PolyglotIsolateGuestFeature is not enabled
                    throw new UnsupportedOperationException("Not supported on the guest when the PolyglotIsolateGuestFeature is not enabled.");
                }
            } else if (et.isFileSystemException) {
                writeFileSystemException(out, et, (FileSystemException) exception);
            } else {
                writeDefault(out, et, exception);
            }
        }

        private void writeFileSystemException(BinaryOutput out, ExceptionType et, FileSystemException exception) {
            out.writeByte(et.id);
            out.writeTypedValue(exception.getFile());
            out.writeTypedValue(exception.getOtherFile());
            out.writeTypedValue(exception.getReason());
            stackTraceMarshaller.write(out, exception.getStackTrace());
        }

        private void writeDefault(BinaryOutput out, ExceptionType et, Throwable exception) {
            String message = exception.getMessage();
            String className = et == ExceptionType.UnknownException ? getExceptionClassName(exception) : null;
            out.writeByte(et.id);
            if (className != null) {
                out.writeUTF(className);
            }
            out.writeTypedValue(message);
            stackTraceMarshaller.write(out, exception.getStackTrace());
        }

        @Override
        public Throwable read(Isolate<?> isolate, BinaryInput in) {
            ExceptionType et = ExceptionType.forId(in.readByte());
            boolean polyglotException = false;
            boolean polyglotEngineException = false;
            long contextId = 0;
            long engineId = 0;
            if (et == ExceptionType.PolyglotException) {
                polyglotException = true;
                engineId = in.readLong();
                contextId = in.readLong();
                et = ExceptionType.forId(in.readByte());
            } else if (et == ExceptionType.PolyglotEngineException) {
                polyglotEngineException = true;
                et = ExceptionType.forId(in.readByte());
            }

            Throwable res;
            if (et == ExceptionType.AbstractTruffleExceptionReference) {
                if (isHost()) {
                    res = (Throwable) BinaryProtocol.readHostTypedValue(in, findForeignContext(isolate, contextId));
                } else if (ImageSingletons.contains(PolyglotIsolateGuestFeatureEnabled.class)) {
                    res = (Throwable) BinaryProtocol.readGuestTypedValue(in, ReferenceHandles.resolve(in.readLong(), GuestContext.class));
                } else {
                    // Don't include guest classes when PolyglotIsolateGuestFeature is not enabled
                    throw new UnsupportedOperationException("Not supported on the guest when the PolyglotIsolateGuestFeature is not enabled.");
                }
            } else if (et == ExceptionType.BinaryProtocolException) {
                if (isHost()) {
                    res = (Throwable) BinaryProtocol.readHostTypedValue(in, null);
                } else if (ImageSingletons.contains(PolyglotIsolateGuestFeatureEnabled.class)) {
                    res = (Throwable) BinaryProtocol.readGuestTypedValue(in, null);
                } else {
                    // Don't include guest classes when PolyglotIsolateGuestFeature is not enabled
                    throw new UnsupportedOperationException("Not supported on the guest when the PolyglotIsolateGuestFeature is not enabled.");
                }
            } else if (et.isFileSystemException) {
                String file = (String) in.readTypedValue();
                String otherFile = (String) in.readTypedValue();
                String reason = (String) in.readTypedValue();
                StackTraceElement[] stackTrace = ForeignException.mergeStackTrace(isolate, stackTraceMarshaller.read(isolate, in));
                res = et.instantiate(null, new String[]{file, otherFile, reason}, stackTrace);
            } else {
                String className = null;
                if (et == ExceptionType.UnknownException) {
                    className = in.readUTF();
                } else if (et == ExceptionType.AbstractTruffleException) {
                    engineId = 0L;
                }
                String message = (String) in.readTypedValue();
                StackTraceElement[] stackTrace = ForeignException.mergeStackTrace(isolate, stackTraceMarshaller.read(isolate, in));
                res = et.instantiate(className, new String[]{message}, stackTrace);
            }
            if (polyglotException) {
                res = createPolyglotException(PolyglotIsolateHostSupport.getPolyglot(), isolate, engineId, contextId, res);
                /*
                 * Materialize the frames of the PolyglotException so they remain available after
                 * the isolate is closed. Without this, the following try-with-resources pattern
                 * would fail.
                 *
                 * try (Context ctx = Context.create()) { ... } catch (PolyglotException e) {
                 * e.printStackTrace(); }
                 */
                PolyglotIsolateAccessor.ENGINE.materializePolyglotException((RuntimeException) res);
            } else if (polyglotEngineException) {
                res = PolyglotIsolateAccessor.ENGINE.createPolyglotEngineException((RuntimeException) res);
            }
            return res;
        }

        private static String getExceptionClassName(Throwable exception) {
            return exception instanceof MarshalledException ? ((MarshalledException) exception).getForeignExceptionClassName() : exception.getClass().getName();
        }

        private static Object findPolyglotEngineImplByHandle(AbstractPolyglotImpl polyglot, Isolate<?> isolate, long engineId) {
            Object engineReceiver = PolyglotIsolateHostSupport.findEngineReceiverByHandle(isolate, engineId);
            if (engineReceiver instanceof ForeignEngine) {
                engineReceiver = polyglot.getAPIAccess().getEngineReceiver(((ForeignEngine) engineReceiver).getLocalEngine());
            }
            return engineReceiver;
        }

        private static Object findPolyglotContextImplByHandle(AbstractPolyglotImpl polyglot, Isolate<?> isolate, long contextId) {
            Object contextReceiver = PolyglotIsolateHostSupport.findContextByHandle(isolate, contextId);
            if (contextReceiver instanceof ForeignContext) {
                contextReceiver = polyglot.getAPIAccess().getContextReceiver(((ForeignContext) contextReceiver).getLocalContext());
            }
            return contextReceiver;
        }

        private static ForeignContext findForeignContext(Isolate<?> isolate, long contextId) {
            if (contextId == 0L) {
                // Not a polyglot exception use the current context
                if (PolyglotIsolateAccessor.ENGINE.hasCurrentContext()) {
                    AbstractPolyglotImpl polyglot = PolyglotIsolateHostSupport.getPolyglot();
                    return (ForeignContext) polyglot.getAPIAccess().getContextReceiver(polyglot.getCurrentContext());
                } else {
                    return null;
                }
            } else {
                // The context is taken from the polyglot exception
                return (ForeignContext) PolyglotIsolateHostSupport.findContextByHandle(isolate, contextId);
            }
        }

        private static RuntimeException createPolyglotException(AbstractPolyglotImpl polyglot, Isolate<?> isolate, long engineId, long contextId, Throwable cause) {
            Object contextImpl = findPolyglotContextImplByHandle(polyglot, isolate, contextId);
            if (contextImpl != null) {
                return PolyglotIsolateAccessor.ENGINE.wrapGuestException(contextImpl, cause);
            }
            Object engineImpl = findPolyglotEngineImplByHandle(polyglot, isolate, engineId);
            if (engineImpl != null) {
                return PolyglotIsolateAccessor.ENGINE.wrapGuestException(engineImpl, cause);
            }
            return PolyglotIsolateAccessor.ENGINE.wrapGuestException(polyglot, cause);
        }

        private enum ExceptionType {
            IllegalArgumentException(1, false),
            IllegalStateException(2, false),
            BinaryProtocolException(3, false),
            IOException(4, false),
            FileSystemException(5, true),
            AtomicMoveNotSupportedException(6, true),
            DirectoryNotEmptyException(7, true),
            FileAlreadyExistsException(8, true),
            NoSuchFileException(9, true),
            NotDirectoryException(10, true),
            NotLinkException(11, true),
            OutOfMemoryError(12, false),
            PolyglotEngineException(13, false),
            PolyglotException(14, false),
            SecurityException(15, false),
            StackOverflowError(16, false),
            VetoException(17, false),
            AbstractTruffleExceptionReference(18, false),
            AbstractTruffleException(19, false),
            UnsupportedOperationException(20, false),
            UnknownException(0, false);

            private static final ExceptionType[] valuesById = createValuesById();

            final int id;
            final boolean isFileSystemException;

            ExceptionType(int id, boolean isFileSystemException) {
                this.id = id;
                this.isFileSystemException = isFileSystemException;
            }

            Throwable instantiate(String className, String[] arguments, StackTraceElement[] stackTrace) {
                boolean updateStackTrace = true;
                Throwable t;
                switch (this) {
                    case IllegalArgumentException:
                        t = new IllegalArgumentException(arguments[0]);
                        break;
                    case IllegalStateException:
                        t = new IllegalStateException(arguments[0]);
                        break;
                    case UnsupportedOperationException:
                        t = new UnsupportedOperationException(arguments[0]);
                        break;
                    case AtomicMoveNotSupportedException:
                        t = new AtomicMoveNotSupportedException(arguments[0], arguments[1], arguments[2]);
                        break;
                    case DirectoryNotEmptyException:
                        t = new DirectoryNotEmptyException(arguments[0]);
                        break;
                    case FileAlreadyExistsException:
                        t = new FileAlreadyExistsException(arguments[0], arguments[1], arguments[2]);
                        break;
                    case NoSuchFileException:
                        t = new NoSuchFileException(arguments[0], arguments[1], arguments[2]);
                        break;
                    case NotDirectoryException:
                        t = new NotDirectoryException(arguments[0]);
                        break;
                    case NotLinkException:
                        t = new NotLinkException(arguments[0], arguments[1], arguments[2]);
                        break;
                    case FileSystemException:
                        t = new FileSystemException(arguments[0], arguments[1], arguments[2]);
                        break;
                    case IOException:
                        t = new IOException(arguments[0]);
                        break;
                    case OutOfMemoryError:
                        t = new OutOfMemoryError(arguments[0]);
                        break;
                    case SecurityException:
                        t = new SecurityException(arguments[0]);
                        break;
                    case StackOverflowError:
                        t = new StackOverflowError(arguments[0]);
                        break;
                    case VetoException:
                        t = PolyglotIsolateHostSupport.getPolyglot().getIO().createVetoException(arguments[0]);
                        break;
                    case AbstractTruffleException:
                        t = new AbstractTruffleExceptionImpl(arguments[0]);
                        break;
                    case UnknownException:
                        t = new MarshalledException(className, arguments[0], stackTrace);
                        updateStackTrace = false;
                        break;
                    default:
                        throw new UnsupportedOperationException(toString());
                }
                if (updateStackTrace) {
                    t.setStackTrace(stackTrace);
                }
                return t;
            }

            static ExceptionType forId(int id) {
                return valuesById[id];
            }

            static ExceptionType match(Throwable throwable) {
                if (PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().isPolyglotException(throwable)) {
                    return PolyglotException;
                } else if (PolyglotIsolateAccessor.ENGINE.isPolyglotEngineException(throwable)) {
                    return PolyglotEngineException;
                } else if (throwable instanceof AbstractTruffleException) {
                    return AbstractTruffleExceptionReference;
                } else if (BinaryProtocol.isSupportedException(throwable)) {
                    return BinaryProtocolException;
                } else if (throwable instanceof IllegalArgumentException) {
                    // Needed by polyglot SDK
                    return IllegalArgumentException;
                } else if (throwable instanceof IllegalStateException) {
                    // Needed by polyglot SDK
                    return IllegalStateException;
                } else if (throwable instanceof UnsupportedOperationException) {
                    // Needed by TruffleFile
                    return UnsupportedOperationException;
                } else if (throwable instanceof AtomicMoveNotSupportedException) {
                    // Needed by TruffleFile
                    return AtomicMoveNotSupportedException;
                } else if (throwable instanceof DirectoryNotEmptyException) {
                    // Needed by TruffleFile
                    return DirectoryNotEmptyException;
                } else if (throwable instanceof FileAlreadyExistsException) {
                    // Needed by TruffleFile
                    return FileAlreadyExistsException;
                } else if (throwable instanceof NoSuchFileException) {
                    // Needed by TruffleFile
                    return NoSuchFileException;
                } else if (throwable instanceof NotDirectoryException) {
                    // Needed by TruffleFile
                    return NotDirectoryException;
                } else if (throwable instanceof NotLinkException) {
                    // Needed by TruffleFile
                    return NotLinkException;
                } else if (throwable instanceof FileSystemException) {
                    // Needed by MessageTransport and TruffleFile
                    return FileSystemException;
                } else if (throwable instanceof IOException) {
                    // Needed by MessageTransport and TruffleFile
                    return IOException;
                } else if (PolyglotIsolateHostSupport.getPolyglot().getIO().isVetoException(throwable)) {
                    // Needed by MessageTransport
                    return VetoException;
                } else if (throwable instanceof SecurityException) {
                    // Needed by PolyglotAccess and TruffleFile
                    return SecurityException;
                } else if (throwable instanceof OutOfMemoryError) {
                    // Needed by AbstractHostService#toHostResourceError
                    return OutOfMemoryError;
                } else if (throwable instanceof StackOverflowError) {
                    // Needed by AbstractHostService#toHostResourceError
                    return StackOverflowError;
                } else {
                    return UnknownException;
                }
            }

            private static ExceptionType[] createValuesById() {
                ExceptionType[] types = values();
                ExceptionType[] res = new ExceptionType[types.length];
                for (ExceptionType type : types) {
                    assert res[type.id] == null : String.format("Both %s and %s have the same id %d.", res[type.id], type, type.id);
                    res[type.id] = type;
                }
                return res;
            }
        }

        @Override
        public int inferSize(Throwable exception) {
            int size = 2 + 4 * Long.BYTES;
            if (BinaryProtocol.isSupportedException(exception)) {
                size += INTEROP_EXCEPTION_SIZE_ESTIMATE;
            } else {
                size += strsize(exception.getClass().getName());
                size += optstrsize(exception.getMessage());
                size += STACK_SIZE_ESTIMATE;
            }
            return size;
        }

        @SuppressWarnings("serial")
        private static final class AbstractTruffleExceptionImpl extends AbstractTruffleException {

            AbstractTruffleExceptionImpl(String message) {
                super(message);
            }
        }
    }

    private static final class ExceptionMarshaller<T extends Throwable> implements BinaryMarshaller<T> {

        private final ThrowableMarshaller delegate;
        private final Class<T> exceptionType;
        private final Predicate<T> filter;

        ExceptionMarshaller(Class<T> exceptionType) {
            this(exceptionType, exceptionType::isInstance);
        }

        ExceptionMarshaller(Class<T> exceptionType, Predicate<T> filter) {
            this.delegate = ThrowableMarshaller.INSTANCE;
            this.exceptionType = Objects.requireNonNull(exceptionType, "ExceptionType must be non null.");
            this.filter = Objects.requireNonNull(filter, "Filter must be non null.");
        }

        @Override
        public void write(BinaryOutput output, T object) {
            if (!filter.test(object)) {
                throw new IllegalArgumentException("Object not accepted by a filter.");
            }
            delegate.write(output, object);
        }

        @Override
        public T read(Isolate<?> isolate, BinaryInput input) {
            return exceptionType.cast(delegate.read(isolate, input));
        }

        @Override
        public int inferSize(T object) {
            return delegate.inferSize(object);
        }
    }

    static int strsize(String string) {
        return Integer.BYTES + string.length();
    }

    static int optstrsize(String string) {
        return 1 + (string == null ? 0 : strsize(string));
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface OptionValue {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface Null {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface SourceByValue {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface PolyglotAccessByValue {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface IOAccessByValue {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface EnvironmentAccessByValue {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface InteropObject {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface TruffleFile {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface HostContext {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface ContextReceiver {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface SourceReceiver {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface SourceSectionReceiver {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface PolyglotExceptionReceiver {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface ValueReceiver {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface EngineReceiver {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface FileAttributeValue {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface FileAttributeMap {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface InstrumentPeerMap {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface LanguagePeerMap {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    @interface ValuesList {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER})
    @interface ReadOnly {
    }

    @MarshallerAnnotation
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER})
    @interface WriteOnly {
    }

}
