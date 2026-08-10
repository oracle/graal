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

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteOrder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import com.oracle.truffle.api.interop.HeapIsolationException;
import org.graalvm.nativebridge.BinaryInput;
import org.graalvm.nativebridge.BinaryOutput;
import org.graalvm.polyglot.io.ByteSequence;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TriState;

final class BinaryProtocol {

    // Type tags
    private static final byte NULL = 0;
    private static final byte BOOLEAN = 1;
    private static final byte BYTE = 2;
    private static final byte SHORT = 3;
    private static final byte CHAR = 4;
    private static final byte INT = 5;
    private static final byte LONG = 6;
    private static final byte FLOAT = 7;
    private static final byte DOUBLE = 8;
    private static final byte STRING = 9;
    private static final byte TRISTATE = 10;
    private static final byte EXCEPTION_TYPE = 11;
    private static final byte BYTE_ORDER = 12;
    private static final byte ARRAY = 13;
    private static final byte TRUFFLE_LANGUAGE = 14;
    private static final byte GUEST_OBJECT = 15;
    private static final byte HOST_OBJECT = 16;
    private static final byte INTEROP_LIBRARY = 17;
    private static final byte EXCEPTION = 18;
    private static final byte SOURCE_SECTION = 19;
    private static final byte INSTANT = 20;
    private static final byte TRUFFLE_STRING = 21;
    private static final byte LOCAL_DATE = 22;
    private static final byte LOCAL_TIME = 23;
    private static final byte ZONE_ID = 24;
    private static final byte BIG_INTEGER = 25;
    private static final byte BYTE_ARRAY = 26;
    private static final byte HOST_NULL = 27;

    // Exception kinds
    private static final byte EXCEPTION_KIND_INVALID_REFERENCE = 1;
    private static final byte EXCEPTION_KIND_CANCEL_EXECUTION = 2;
    private static final byte EXCEPTION_KIND_INTERRUPT_EXECUTION = 3;
    private static final byte EXCEPTION_KIND_TRUFFLE_EXCEPTION = 4;
    private static final byte EXCEPTION_KIND_INTEROP_UNSUPPORTED_MESSAGE = 5;
    private static final byte EXCEPTION_KIND_INTEROP_UNKNOWN_IDENTIFIER = 6;
    private static final byte EXCEPTION_KIND_INTEROP_UNKNOWN_KEY = 7;
    private static final byte EXCEPTION_KIND_INTEROP_STOP_ITERATION = 8;
    private static final byte EXCEPTION_KIND_INTEROP_ARITY = 9;
    private static final byte EXCEPTION_KIND_INTEROP_UNSUPPORTED_TYPE = 10;
    private static final byte EXCEPTION_KIND_INTEROP_INVALID_ARRAY_INDEX = 11;
    private static final byte EXCEPTION_KIND_INTEROP_INVALID_BUFFER_OFFSET = 12;
    private static final byte EXCEPTION_KIND_INTEROP_HEAP_ISOLATION_EXCEPTION = 13;
    private static final byte EXCEPTION_KIND_EXIT_EXCEPTION = 14;

    private BinaryProtocol() {
    }

    static Object readGuestTypedValue(BinaryInput in, GuestContext context) {
        byte tag = in.readByte();
        switch (tag) {
            case ARRAY:
                int len = in.readInt();
                Object[] arr = new Object[len];
                for (int i = 0; i < len; i++) {
                    arr[i] = readGuestTypedValue(in, context);
                }
                return arr;
            case GUEST_OBJECT:
                return context.hostToGuestObjectReferences.getObject(in.readLong());
            case HOST_NULL:
                return PolyglotIsolateAccessor.ENGINE.getHostNull();
            case HOST_OBJECT:
                return HSTruffleObject.createHostObjectReference(in.readLong(), context);
            case EXCEPTION:
                return readGuestException(in, context);
            case TRUFFLE_LANGUAGE:
                return GuestHostLanguage.class;
            default:
                return readSimpleTypedValue(in, tag);
        }
    }

    static Object readHostTypedValue(BinaryInput in, ForeignContext context) {
        byte tag = in.readByte();
        switch (tag) {
            case ARRAY:
                int len = in.readInt();
                Object[] arr = new Object[len];
                for (int i = 0; i < len; i++) {
                    arr[i] = readHostTypedValue(in, context);
                }
                return arr;
            case GUEST_OBJECT:
                return NativeTruffleObject.createReference(in.readLong(), context);
            case HOST_NULL:
                return PolyglotIsolateAccessor.ENGINE.getHostNull();
            case HOST_OBJECT:
                return context.getGuestToHostReceiver().getHostObject(in.readLong());
            case EXCEPTION:
                return readHostException(in, context);
            default:
                return readSimpleTypedValue(in, tag);
        }
    }

    private static Object readSimpleTypedValue(BinaryInput in) {
        return readSimpleTypedValue(in, in.readByte());
    }

    private static Object readSimpleTypedValue(BinaryInput in, byte tag) {
        switch (tag) {
            case NULL:
                return null;
            case BOOLEAN:
                return in.readBoolean();
            case BYTE:
                return in.readByte();
            case SHORT:
                return in.readShort();
            case CHAR:
                return in.readChar();
            case INT:
                return in.readInt();
            case LONG:
                return in.readLong();
            case FLOAT:
                return in.readFloat();
            case DOUBLE:
                return in.readDouble();
            case STRING:
                return in.readUTF();
            case TRUFFLE_STRING:
                return TruffleString.fromJavaStringUncached(in.readUTF(), TruffleString.Encoding.UTF_16);
            case TRISTATE:
                return TriState.values()[in.readInt()];
            case BYTE_ORDER:
                return in.readBoolean() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
            case EXCEPTION_TYPE:
                return ExceptionType.values()[in.readInt()];
            case INTEROP_LIBRARY:
                return InteropLibrary.getUncached();
            case SOURCE_SECTION:
                return readSourceSection(in);
            case INSTANT:
                return Instant.ofEpochSecond(in.readLong(), in.readInt());
            case LOCAL_DATE:
                return LocalDate.ofEpochDay(in.readLong());
            case LOCAL_TIME:
                return LocalTime.ofNanoOfDay(in.readLong());
            case ZONE_ID:
                return ZoneId.of(in.readUTF());
            case BIG_INTEGER:
                int length = in.readInt();
                byte[] a = new byte[length];
                in.read(a, 0, a.length);
                return new BigInteger(a);
            case BYTE_ARRAY:
                int baLength = in.readInt();
                byte[] ba = new byte[baLength];
                in.read(ba, 0, ba.length);
                return ba;
            default:
                throw new IllegalArgumentException(String.format("Unknown tag %d", tag));
        }
    }

    private static SourceSection readSourceSection(BinaryInput in) {
        Source source = readSource(in);
        boolean available = in.readBoolean();
        if (available) {
            if (in.readBoolean()) {
                int start = in.readInt();
                int len = in.readInt();
                return source.createSection(start, len);
            }
            if (in.readBoolean()) {
                int startLine = in.readInt();
                int endLine = in.readInt();
                if (in.readBoolean()) {
                    int startColumn = in.readInt();
                    int endColumn = in.readInt();
                    return source.createSection(startLine, startColumn, endLine, endColumn);
                } else {
                    return source.createSection(startLine, endLine);
                }
            }
        }
        return source.createUnavailableSection();
    }

    private static Source readSource(BinaryInput in) {
        String name = in.readUTF();
        String lang = in.readUTF();
        String strUri = (String) readSimpleTypedValue(in);
        String mimeType = (String) readSimpleTypedValue(in);
        boolean interactive = in.readBoolean();
        boolean internal = in.readBoolean();
        boolean cached = in.readBoolean();
        boolean hasCharacters = in.readBoolean();
        boolean hasBytes = in.readBoolean();
        Source.SourceBuilder builder;
        if (hasCharacters) {
            builder = Source.newBuilder(lang, in.readUTF(), name);
        } else if (hasBytes) {
            int len = in.readInt();
            byte[] content = new byte[len];
            in.read(content, 0, len);
            builder = Source.newBuilder(lang, ByteSequence.create(content), name);
        } else {
            builder = Source.newBuilder(lang, "", name).content(CONTENT_NONE);
        }
        builder.interactive(interactive);
        builder.internal(internal);
        builder.cached(cached);
        if (strUri != null) {
            builder.uri(URI.create(strUri));
        }
        if (mimeType != null) {
            builder.mimeType(mimeType);
        }
        try {
            return builder.build();
        } catch (IOException ioe) {
            throw CompilerDirectives.shouldNotReachHere("Unexpected IOException", ioe);
        }
    }

    private static Throwable readGuestException(BinaryInput in, GuestContext context) {
        byte exceptionKind = in.readByte();
        switch (exceptionKind) {
            case EXCEPTION_KIND_INTEROP_UNKNOWN_KEY: {
                Object key = readGuestTypedValue(in, context);
                return UnknownKeyException.create(key);
            }
            case EXCEPTION_KIND_INTEROP_UNSUPPORTED_TYPE: {
                Object[] types = (Object[]) readGuestTypedValue(in, context);
                return UnsupportedTypeException.create(types);
            }
            case EXCEPTION_KIND_TRUFFLE_EXCEPTION: {
                byte kind = in.readByte();
                long id = in.readLong();
                if (kind == GUEST_OBJECT) {
                    return (Throwable) context.hostToGuestObjectReferences.getObject(id);
                } else if (kind == HOST_OBJECT) {
                    HSTruffleObject receiver = HSTruffleObject.createHostObjectReference(id, context);
                    return HSTruffleException.create(receiver);
                }
                throw new IllegalArgumentException(String.format("Unexpected object type %d", kind));
            }
            default:
                return readCommonException(in, exceptionKind);
        }
    }

    private static Throwable readHostException(BinaryInput in, ForeignContext context) {
        byte exceptionKind = in.readByte();
        switch (exceptionKind) {
            case EXCEPTION_KIND_INTEROP_UNKNOWN_KEY: {
                Object key = readHostTypedValue(in, context);
                return UnknownKeyException.create(key);
            }
            case EXCEPTION_KIND_INTEROP_UNSUPPORTED_TYPE: {
                Object[] types = (Object[]) readHostTypedValue(in, context);
                return UnsupportedTypeException.create(types);
            }
            case EXCEPTION_KIND_TRUFFLE_EXCEPTION: {
                byte kind = in.readByte();
                long id = in.readLong();
                if (kind == GUEST_OBJECT) {
                    long hostExceptionId = in.readLong();
                    NativeTruffleObject receiver = NativeTruffleObject.createReference(id, context);
                    AbstractTruffleException hostException = hostExceptionId == -1 ? null : (AbstractTruffleException) context.getGuestToHostReceiver().getHostObject(hostExceptionId);
                    return NativeTruffleException.create(receiver, hostException);
                } else if (kind == HOST_OBJECT) {
                    return (Throwable) context.getGuestToHostReceiver().getHostObject(id);
                }
                throw new IllegalArgumentException(String.format("Unexpected object type %d", kind));
            }
            default:
                return readCommonException(in, exceptionKind);
        }
    }

    private static Throwable readCommonException(BinaryInput in, byte exceptionKind) {
        switch (exceptionKind) {
            case EXCEPTION_KIND_INTEROP_UNSUPPORTED_MESSAGE:
                return UnsupportedMessageException.create();
            case EXCEPTION_KIND_INTEROP_UNKNOWN_IDENTIFIER:
                return UnknownIdentifierException.create(in.readUTF());
            case EXCEPTION_KIND_INTEROP_INVALID_ARRAY_INDEX: {
                long index = in.readLong();
                return InvalidArrayIndexException.create(index);
            }
            case EXCEPTION_KIND_INTEROP_STOP_ITERATION:
                return StopIterationException.create();
            case EXCEPTION_KIND_INTEROP_INVALID_BUFFER_OFFSET: {
                long offset = in.readLong();
                long length = in.readLong();
                return InvalidBufferOffsetException.create(offset, length);
            }
            case EXCEPTION_KIND_INTEROP_HEAP_ISOLATION_EXCEPTION: {
                return HeapIsolationException.create();
            }
            case EXCEPTION_KIND_INTEROP_ARITY: {
                int min = in.readInt();
                int max = in.readInt();
                int actual = in.readInt();
                return ArityException.create(min, max, actual);
            }
            case EXCEPTION_KIND_CANCEL_EXECUTION: {
                boolean resourceLimit = in.readBoolean();
                String message = in.readUTF();
                SourceSection sourceLocation = (SourceSection) readSimpleTypedValue(in);
                return PolyglotIsolateAccessor.ENGINE.createCancelExecution(sourceLocation, message, resourceLimit);
            }
            case EXCEPTION_KIND_EXIT_EXCEPTION: {
                int exitCode = in.readInt();
                String message = in.readUTF();
                SourceSection sourceLocation = (SourceSection) readSimpleTypedValue(in);
                return PolyglotIsolateAccessor.ENGINE.createExitException(sourceLocation, message, exitCode);
            }
            case EXCEPTION_KIND_INTERRUPT_EXECUTION:
                SourceSection sourceLocation = (SourceSection) readSimpleTypedValue(in);
                return PolyglotIsolateAccessor.ENGINE.createInterruptExecution(sourceLocation);
            case EXCEPTION_KIND_INVALID_REFERENCE: {
                String message = in.readUTF();
                return new ReferenceUnavailableException(message);
            }
            default:
                throw new IllegalArgumentException(String.format("Unsupported exception type %d", exceptionKind));
        }
    }

    static void writeGuestTypedValue(BinaryOutput out, Object value, GuestObjectReferences hostToGuestObjectReceiver) {
        if (value instanceof Object[]) {
            Object[] arr = (Object[]) value;
            out.writeByte(ARRAY);
            out.writeInt(arr.length);
            for (Object arrElement : arr) {
                writeGuestTypedValue(out, arrElement, hostToGuestObjectReceiver);
            }
        } else if (value instanceof HSTruffleObject) {
            out.writeByte(HOST_OBJECT);
            out.writeLong(((HSTruffleObject) value).getHostReferenceId());
        } else if (value instanceof Throwable) {
            writeGuestException(out, (Throwable) value, hostToGuestObjectReceiver);
        } else if (value instanceof TruffleObject) {
            /*
             * The host null materialized on the guest side to getHostNull is a well-known
             * singleton. When it is sent back to the host we translate it to HOST_NULL so the host
             * rematerializes its own host null instead of registering a fresh guest reference.
             */
            if (value == PolyglotIsolateAccessor.ENGINE.getHostNull()) {
                out.writeByte(HOST_NULL);
            } else {
                out.writeByte(GUEST_OBJECT);
                long objId = hostToGuestObjectReceiver.registerGuestObject((TruffleObject) value);
                out.writeLong(objId);
            }
        } else {
            writeSimpleTypedValue(out, value);
        }
    }

    static void writeHostTypedValue(BinaryOutput out, Object value, HostObjectReferences guestToHostObjectReceiver) {
        if (value instanceof Object[]) {
            Object[] arr = (Object[]) value;
            out.writeByte(ARRAY);
            out.writeInt(arr.length);
            for (Object arrElement : arr) {
                writeHostTypedValue(out, arrElement, guestToHostObjectReceiver);
            }
        } else if (value instanceof NativeTruffleObject) {
            out.writeByte(GUEST_OBJECT);
            out.writeLong(((NativeTruffleObject) value).getGuestReferenceId());
        } else if (value instanceof Throwable) {
            writeHostException(out, (Throwable) value, guestToHostObjectReceiver);
        } else if (value instanceof TruffleObject) {
            /*
             * Any TruffleObject that represents null is canonicalized to HOST_NULL rather than
             * exported as a HOST_OBJECT reference, so we must test isNull() here, not identity
             * against getHostNull(). The value need not be the HostObject.NULL singleton: a
             * host-side value may be an OtherContextGuestObject wrapping another context's guest
             * null, which reports isNull() but is not == getHostNull().
             */
            if (InteropLibrary.getUncached().isNull(value)) {
                out.writeByte(HOST_NULL);
            } else {
                out.writeByte(HOST_OBJECT);
                out.writeLong(guestToHostObjectReceiver.registerHostObject((TruffleObject) value));
            }
        } else if (value instanceof Class<?> && TruffleLanguage.class.isAssignableFrom((Class<?>) value)) {
            out.writeByte(TRUFFLE_LANGUAGE);
        } else {
            writeSimpleTypedValue(out, value);
        }
    }

    static boolean isSupportedException(Throwable throwable) {
        return throwable instanceof InteropException || throwable instanceof AbstractTruffleException ||
                        throwable instanceof ReferenceUnavailableException || PolyglotIsolateAccessor.ENGINE.isCancelExecution(throwable) ||
                        PolyglotIsolateAccessor.ENGINE.isExitException(throwable);
    }

    static void writeTruffleExceptionWithoutUnboxing(BinaryOutput out, HSTruffleException foreignHostException, GuestObjectReferences hostToGuestObjectReceiver) {
        out.writeByte(EXCEPTION);
        out.writeByte(EXCEPTION_KIND_TRUFFLE_EXCEPTION);
        out.writeByte(GUEST_OBJECT);
        out.writeLong(hostToGuestObjectReceiver.registerGuestObject(foreignHostException));
        out.writeLong(foreignHostException.reference.getHostReferenceId());
    }

    private static void writeSimpleTypedValue(BinaryOutput out, Object value) {
        if (value == null) {
            out.writeByte(NULL);
        } else if (value instanceof Boolean) {
            out.writeByte(BOOLEAN);
            out.writeBoolean((boolean) value);
        } else if (value instanceof Byte) {
            out.writeByte(BYTE);
            out.writeByte((byte) value);
        } else if (value instanceof Short) {
            out.writeByte(SHORT);
            out.writeShort((short) value);
        } else if (value instanceof Character) {
            out.writeByte(CHAR);
            out.writeChar((char) value);
        } else if (value instanceof Integer) {
            out.writeByte(INT);
            out.writeInt((int) value);
        } else if (value instanceof Long) {
            out.writeByte(LONG);
            out.writeLong((long) value);
        } else if (value instanceof Float) {
            out.writeByte(FLOAT);
            out.writeFloat((float) value);
        } else if (value instanceof Double) {
            out.writeByte(DOUBLE);
            out.writeDouble((double) value);
        } else if (value instanceof String) {
            out.writeByte(STRING);
            out.writeUTF((String) value);
        } else if (value instanceof TruffleString) {
            out.writeByte(TRUFFLE_STRING);
            out.writeUTF(((TruffleString) value).toJavaStringUncached());
        } else if (value instanceof TriState) {
            out.writeByte(TRISTATE);
            out.writeInt(((TriState) value).ordinal());
        } else if (value instanceof ByteOrder) {
            out.writeByte(BYTE_ORDER);
            out.writeBoolean(value == ByteOrder.BIG_ENDIAN);
        } else if (value instanceof ExceptionType) {
            out.writeByte(EXCEPTION_TYPE);
            out.writeInt(((ExceptionType) value).ordinal());
        } else if (value instanceof InteropLibrary) {
            out.writeByte(INTEROP_LIBRARY);
        } else if (value instanceof SourceSection) {
            writeSourceSection(out, (SourceSection) value);
        } else if (value instanceof Instant) {
            out.writeByte(INSTANT);
            out.writeLong(((Instant) value).getEpochSecond());
            out.writeInt(((Instant) value).getNano());
        } else if (value instanceof LocalDate) {
            out.writeByte(LOCAL_DATE);
            out.writeLong(((LocalDate) value).toEpochDay());
        } else if (value instanceof LocalTime) {
            out.writeByte(LOCAL_TIME);
            out.writeLong(((LocalTime) value).toNanoOfDay());
        } else if (value instanceof ZoneId) {
            out.writeByte(ZONE_ID);
            out.writeUTF(((ZoneId) value).getId());
        } else if (value instanceof BigInteger) {
            out.writeByte(BIG_INTEGER);
            byte[] a = ((BigInteger) value).toByteArray();
            out.writeInt(a.length);
            out.write(a, 0, a.length);
        } else if (value instanceof byte[]) {
            out.writeByte(BYTE_ARRAY);
            byte[] a = (byte[]) value;
            out.writeInt(a.length);
            out.write(a, 0, a.length);
        } else {
            throw new IllegalArgumentException(String.format("Unsupported type %s", value.getClass()));
        }
    }

    private static void writeSourceSection(BinaryOutput out, SourceSection sourceSection) {
        out.writeByte(SOURCE_SECTION);
        writeSource(out, sourceSection.getSource());    // TODO: We can do more here, use id from
                                                        // IsolateSourceCache when available
        boolean available = sourceSection.isAvailable();
        out.writeBoolean(available);
        if (available) {
            boolean hasCharIndex = sourceSection.hasCharIndex();
            out.writeBoolean(hasCharIndex);
            if (hasCharIndex) {
                out.writeInt(sourceSection.getCharIndex());
                out.writeInt(sourceSection.getCharLength());
            } else {
                boolean hasLines = sourceSection.hasLines();
                out.writeBoolean(hasLines);
                if (hasLines) {
                    out.writeInt(sourceSection.getStartLine());
                    out.writeInt(sourceSection.getEndLine());
                    boolean hasColumns = sourceSection.hasColumns();
                    out.writeBoolean(hasColumns);
                    if (hasColumns) {
                        out.writeInt(sourceSection.getStartColumn());
                        out.writeInt(sourceSection.getEndColumn());
                    }
                }
            }
        }
    }

    private static void writeSource(BinaryOutput out, Source source) {
        out.writeUTF(source.getName());
        out.writeUTF(source.getLanguage());
        URI uri = source.getURI();
        writeSimpleTypedValue(out, uri == null ? null : uri.toString());
        writeSimpleTypedValue(out, source.getMimeType());
        out.writeBoolean(source.isInteractive());
        out.writeBoolean(source.isInternal());
        out.writeBoolean(source.isCached());
        boolean hasCharacters = source.hasCharacters();
        boolean hasBytes = source.hasBytes();
        out.writeBoolean(hasCharacters);
        out.writeBoolean(hasBytes);
        if (hasCharacters) {
            out.writeUTF(source.getCharacters().toString());
        } else if (hasBytes) {
            ByteSequence content = source.getBytes();
            out.writeInt(content.length());
            out.write(content.toByteArray(), 0, content.length());
        }
    }

    private static void writeHostException(BinaryOutput out, Throwable throwable, HostObjectReferences guestToHostObjectReceiver) {
        out.writeByte(EXCEPTION);
        if (throwable instanceof InteropException) {
            if (throwable instanceof UnknownKeyException) {
                out.writeByte(EXCEPTION_KIND_INTEROP_UNKNOWN_KEY);
                writeHostTypedValue(out, ((UnknownKeyException) throwable).getUnknownKey(), guestToHostObjectReceiver);
            } else if (throwable instanceof UnsupportedTypeException) {
                out.writeByte(EXCEPTION_KIND_INTEROP_UNSUPPORTED_TYPE);
                writeHostTypedValue(out, ((UnsupportedTypeException) throwable).getSuppliedValues(), guestToHostObjectReceiver);
            } else {
                writeCommonInteropException(out, (InteropException) throwable);
            }
        } else if (throwable instanceof TruffleObject) {
            out.writeByte(EXCEPTION_KIND_TRUFFLE_EXCEPTION);
            if (throwable instanceof NativeTruffleException) {
                out.writeByte(GUEST_OBJECT);
                out.writeLong(((NativeTruffleException) throwable).reference.getGuestReferenceId());
            } else {
                out.writeByte(HOST_OBJECT);
                out.writeLong(guestToHostObjectReceiver.registerHostObject((TruffleObject) throwable));
            }
        } else {
            writeCommonException(out, throwable);
        }
    }

    private static void writeGuestException(BinaryOutput out, Throwable throwable, GuestObjectReferences hostToGuestObjectReceiver) {
        out.writeByte(EXCEPTION);
        if (throwable instanceof InteropException) {
            if (throwable instanceof UnknownKeyException) {
                out.writeByte(EXCEPTION_KIND_INTEROP_UNKNOWN_KEY);
                writeGuestTypedValue(out, ((UnknownKeyException) throwable).getUnknownKey(), hostToGuestObjectReceiver);
            } else if (throwable instanceof UnsupportedTypeException) {
                out.writeByte(EXCEPTION_KIND_INTEROP_UNSUPPORTED_TYPE);
                writeGuestTypedValue(out, ((UnsupportedTypeException) throwable).getSuppliedValues(), hostToGuestObjectReceiver);
            } else {
                writeCommonInteropException(out, (InteropException) throwable);
            }
        } else if (throwable instanceof TruffleObject) {
            out.writeByte(EXCEPTION_KIND_TRUFFLE_EXCEPTION);
            if (throwable instanceof HSTruffleException) {
                out.writeByte(HOST_OBJECT);
                out.writeLong(((HSTruffleException) throwable).reference.getHostReferenceId());
            } else {
                out.writeByte(GUEST_OBJECT);
                out.writeLong(hostToGuestObjectReceiver.registerGuestObject((TruffleObject) throwable));
                out.writeLong(-1);
            }
        } else {
            writeCommonException(out, throwable);
        }
    }

    private static void writeCommonException(BinaryOutput out, Throwable throwable) {
        if (throwable instanceof ReferenceUnavailableException) {
            out.writeByte(EXCEPTION_KIND_INVALID_REFERENCE);
            out.writeUTF(throwable.getMessage());
        } else if (PolyglotIsolateAccessor.ENGINE.isCancelExecution(throwable)) {
            out.writeByte(EXCEPTION_KIND_CANCEL_EXECUTION);
            out.writeBoolean(PolyglotIsolateAccessor.ENGINE.isResourceLimitCancelExecution(throwable));
            out.writeUTF(throwable.getMessage());
            writeSimpleTypedValue(out, PolyglotIsolateAccessor.ENGINE.getCancelExecutionSourceLocation(throwable));
        } else if (PolyglotIsolateAccessor.ENGINE.isExitException(throwable)) {
            out.writeByte(EXCEPTION_KIND_EXIT_EXCEPTION);
            out.writeInt(PolyglotIsolateAccessor.ENGINE.getExitExceptionExitCode(throwable));
            out.writeUTF(throwable.getMessage());
            writeSimpleTypedValue(out, PolyglotIsolateAccessor.ENGINE.getExitExceptionSourceLocation(throwable));
        } else if (PolyglotIsolateAccessor.ENGINE.isInterruptExecution(throwable)) {
            out.writeByte(EXCEPTION_KIND_INTERRUPT_EXECUTION);
            InteropLibrary interop = InteropLibrary.getUncached();
            SourceSection sourceSection = null;
            if (interop.hasSourceLocation(throwable)) {
                try {
                    sourceSection = interop.getSourceLocation(throwable);
                } catch (UnsupportedMessageException um) {
                    throw CompilerDirectives.shouldNotReachHere(um);
                }
            }
            writeSimpleTypedValue(out, sourceSection);
        } else {
            IllegalArgumentException illegalArgumentException = new IllegalArgumentException(String.format("Unsupported exception %s", throwable));
            illegalArgumentException.addSuppressed(throwable);
            throw illegalArgumentException;
        }
    }

    private static void writeCommonInteropException(BinaryOutput out, InteropException interopException) {
        if (interopException instanceof UnsupportedMessageException) {
            out.writeByte(EXCEPTION_KIND_INTEROP_UNSUPPORTED_MESSAGE);
        } else if (interopException instanceof UnknownIdentifierException) {
            out.writeByte(EXCEPTION_KIND_INTEROP_UNKNOWN_IDENTIFIER);
            out.writeUTF(((UnknownIdentifierException) interopException).getUnknownIdentifier());
        } else if (interopException instanceof InvalidArrayIndexException) {
            out.writeByte(EXCEPTION_KIND_INTEROP_INVALID_ARRAY_INDEX);
            out.writeLong(((InvalidArrayIndexException) interopException).getInvalidIndex());
        } else if (interopException instanceof StopIterationException) {
            out.writeByte(EXCEPTION_KIND_INTEROP_STOP_ITERATION);
        } else if (interopException instanceof InvalidBufferOffsetException) {
            out.writeByte(EXCEPTION_KIND_INTEROP_INVALID_BUFFER_OFFSET);
            InvalidBufferOffsetException invalidBufferOffsetException = (InvalidBufferOffsetException) interopException;
            out.writeLong(invalidBufferOffsetException.getByteOffset());
            out.writeLong(invalidBufferOffsetException.getLength());
        } else if (interopException instanceof HeapIsolationException) {
            out.writeByte(EXCEPTION_KIND_INTEROP_HEAP_ISOLATION_EXCEPTION);
        } else if (interopException instanceof ArityException) {
            out.writeByte(EXCEPTION_KIND_INTEROP_ARITY);
            ArityException arityException = (ArityException) interopException;
            out.writeInt(arityException.getExpectedMinArity());
            out.writeInt(arityException.getExpectedMaxArity());
            out.writeInt(arityException.getActualArity());
        } else {
            throw new IllegalArgumentException(String.format("Unsupported interop exception %s", interopException));
        }
    }
}
