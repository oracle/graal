/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.truffle.polyglot.EngineAccessor.RUNTIME;

import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractValueDispatch;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValueNode;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValuesNode;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToHostValueNode;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.AsDateNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.AsDurationNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.AsInstantNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.AsNativePointerNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.AsTimeNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.AsTimeZoneNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.CanExecuteNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.CanInstantiateNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.CanInvokeNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.GetArrayElementNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.GetArraySizeNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.GetBufferSizeNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.GetHashEntriesIteratorNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.GetHashKeysIteratorNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.GetHashSizeNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.GetHashValueNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.GetHashValueOrDefaultNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.GetHashValuesIteratorNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.GetIteratorNextElementNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.GetMemberKeysNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.GetMemberNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.GetMetaQualifiedNameNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.GetMetaSimpleNameNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.HasArrayElementsNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.HasBufferElementsNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.HasHashEntriesNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.HasHashEntryNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.HasIteratorNextElementNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.HasIteratorNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.HasMemberNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.HasMembersNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.IsBufferWritableNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.IsDateNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.IsDurationNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.IsExceptionNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.IsMetaInstanceNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.IsMetaObjectNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.IsNativePointerNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.IsNullNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.IsTimeNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.IsTimeZoneNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.NewInstanceNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.PutHashEntryNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.PutMemberNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.ReadBufferByteNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.ReadBufferDoubleNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.ReadBufferFloatNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.ReadBufferIntNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.ReadBufferLongNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.ReadBufferShortNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.RemoveArrayElementNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.RemoveHashEntryNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.RemoveMemberNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.SetArrayElementNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.ThrowExceptionNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.WriteBufferByteNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.WriteBufferDoubleNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.WriteBufferFloatNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.WriteBufferIntNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.WriteBufferLongNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueDispatchFactory.InteropValueFactory.WriteBufferShortNodeGen;

abstract class PolyglotValueDispatch extends AbstractValueDispatch {

    private static final String TRUNCATION_SUFFIX = "...";

    private static final String UNKNOWN = "Unknown";

    static final InteropLibrary UNCACHED_INTEROP = InteropLibrary.getFactory().getUncached();

    final PolyglotImpl impl;
    final PolyglotLanguageInstance languageInstance;

    PolyglotValueDispatch(PolyglotImpl impl, PolyglotLanguageInstance languageInstance) {
        super(impl);
        this.impl = impl;
        this.languageInstance = languageInstance;
    }

    @Override
    public final Context getContext(Object context) {
        if (context == null) {
            return null;
        }
        return ((PolyglotLanguageContext) context).context.api;
    }

    static <T extends Throwable> PolyglotException guestToHostException(PolyglotLanguageContext languageContext, T e, boolean entered) {
        throw PolyglotImpl.guestToHostException(languageContext, e, entered);
    }

    @Override
    public Value getArrayElement(Object languageContext, Object receiver, long index) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return getArrayElementUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static Value getArrayElementUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "getArrayElement(long)", "hasArrayElements()");
    }

    @Override
    public void setArrayElement(Object languageContext, Object receiver, long index, Object value) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            setArrayElementUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static void setArrayElementUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "setArrayElement(long, Object)", "hasArrayElements()");
    }

    @Override
    public boolean removeArrayElement(Object languageContext, Object receiver, long index) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw removeArrayElementUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException removeArrayElementUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "removeArrayElement(long, Object)", null);
    }

    @Override
    public long getArraySize(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return getArraySizeUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static long getArraySizeUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "getArraySize()", "hasArrayElements()");
    }

    // region Buffer Methods

    @Override
    public boolean isBufferWritable(Object languageContext, Object receiver) throws UnsupportedOperationException {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        final Object prev = hostEnter(context);
        try {
            throw isBufferWritableUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException isBufferWritableUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "isBufferWritable()", "hasBufferElements()");
    }

    @Override
    public long getBufferSize(Object languageContext, Object receiver) throws UnsupportedOperationException {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        final Object prev = hostEnter(context);
        try {
            throw getBufferSizeUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException getBufferSizeUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "getBufferSize()", "hasBufferElements()");
    }

    @Override
    public byte readBufferByte(Object languageContext, Object receiver, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        final Object prev = hostEnter(context);
        try {
            throw readBufferByteUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException readBufferByteUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "readBufferByte()", "hasBufferElements()");
    }

    @Override
    public void writeBufferByte(Object languageContext, Object receiver, long byteOffset, byte value) throws UnsupportedOperationException, IndexOutOfBoundsException {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        final Object prev = hostEnter(context);
        try {
            throw writeBufferByteUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException writeBufferByteUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "writeBufferByte()", "hasBufferElements()");
    }

    @Override
    public short readBufferShort(Object languageContext, Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        final Object prev = hostEnter(context);
        try {
            throw readBufferShortUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException readBufferShortUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "readBufferShort()", "hasBufferElements()");
    }

    @Override
    public void writeBufferShort(Object languageContext, Object receiver, ByteOrder order, long byteOffset, short value) throws UnsupportedOperationException, IndexOutOfBoundsException {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        final Object prev = hostEnter(context);
        try {
            throw writeBufferShortUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException writeBufferShortUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "writeBufferShort()", "hasBufferElements()");
    }

    @Override
    public int readBufferInt(Object languageContext, Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        final Object prev = hostEnter(context);
        try {
            throw readBufferIntUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException readBufferIntUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "readBufferInt()", "hasBufferElements()");
    }

    @Override
    public void writeBufferInt(Object languageContext, Object receiver, ByteOrder order, long byteOffset, int value) throws UnsupportedOperationException, IndexOutOfBoundsException {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        final Object prev = hostEnter(context);
        try {
            throw writeBufferIntUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException writeBufferIntUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "writeBufferInt()", "hasBufferElements()");
    }

    @Override
    public long readBufferLong(Object languageContext, Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        final Object prev = hostEnter(context);
        try {
            throw readBufferLongUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException readBufferLongUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "readBufferLong()", "hasBufferElements()");
    }

    @Override
    public void writeBufferLong(Object languageContext, Object receiver, ByteOrder order, long byteOffset, long value) throws UnsupportedOperationException, IndexOutOfBoundsException {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        final Object prev = hostEnter(context);
        try {
            throw writeBufferLongUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException writeBufferLongUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "writeBufferLong()", "hasBufferElements()");
    }

    @Override
    public float readBufferFloat(Object languageContext, Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        final Object prev = hostEnter(context);
        try {
            throw readBufferFloatUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException readBufferFloatUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "readBufferFloat()", "hasBufferElements()");
    }

    @Override
    public void writeBufferFloat(Object languageContext, Object receiver, ByteOrder order, long byteOffset, float value) throws UnsupportedOperationException, IndexOutOfBoundsException {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        final Object prev = hostEnter(context);
        try {
            throw writeBufferFloatUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException writeBufferFloatUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "writeBufferFloat()", "hasBufferElements()");
    }

    @Override
    public double readBufferDouble(Object languageContext, Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw readBufferDoubleUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException readBufferDoubleUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "readBufferDouble()", "hasBufferElements()");
    }

    @Override
    public void writeBufferDouble(Object languageContext, Object receiver, ByteOrder order, long byteOffset, double value) throws UnsupportedOperationException, IndexOutOfBoundsException {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw writeBufferDoubleUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException writeBufferDoubleUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "writeBufferDouble()", "hasBufferElements()");
    }

    @TruffleBoundary
    protected static RuntimeException invalidBufferIndex(PolyglotLanguageContext context, Object receiver, long byteOffset, long size) {
        final String message = String.format("Invalid buffer access of length %d at byte offset %d for buffer %s.", size, byteOffset, getValueInfo(context, receiver));
        throw PolyglotEngineException.bufferIndexOutOfBounds(message);
    }

    // endregion

    @Override
    public Value getMember(Object languageContext, Object receiver, String key) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return getMemberUnsupported(context, receiver, key);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static Value getMemberUnsupported(PolyglotLanguageContext context, Object receiver, @SuppressWarnings("unused") String key) {
        throw unsupported(context, receiver, "getMember(String)", "hasMembers()");
    }

    @Override
    public void putMember(Object languageContext, Object receiver, String key, Object member) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            putMemberUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException putMemberUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "putMember(String, Object)", "hasMembers()");
    }

    @Override
    public boolean removeMember(Object languageContext, Object receiver, String key) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw removeMemberUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException removeMemberUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "removeMember(String, Object)", null);
    }

    @Override
    public Value execute(Object languageContext, Object receiver, Object[] arguments) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw executeUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @Override
    public Value execute(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw executeUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException executeUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "execute(Object...)", "canExecute()");
    }

    @Override
    public Value newInstance(Object languageContext, Object receiver, Object[] arguments) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return newInstanceUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static Value newInstanceUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "newInstance(Object...)", "canInstantiate()");
    }

    @Override
    public void executeVoid(Object languageContext, Object receiver, Object[] arguments) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            executeVoidUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @Override
    public void executeVoid(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            executeVoidUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static void executeVoidUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "executeVoid(Object...)", "canExecute()");
    }

    @Override
    public Value invoke(Object languageContext, Object receiver, String identifier, Object[] arguments) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw invokeUnsupported(context, receiver, identifier);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @Override
    public Value invoke(Object languageContext, Object receiver, String identifier) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw invokeUnsupported(context, receiver, identifier);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException invokeUnsupported(PolyglotLanguageContext context, Object receiver, String identifier) {
        throw unsupported(context, receiver, "invoke(" + identifier + ", Object...)", "canInvoke(String)");
    }

    @Override
    public String asString(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return asStringUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    protected static String asStringUnsupported(PolyglotLanguageContext context, Object receiver) {
        return invalidCastPrimitive(context, receiver, String.class, "asString()", "isString()", "Invalid coercion.");
    }

    @Override
    public boolean asBoolean(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return asBooleanUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    private static boolean isNullUncached(Object receiver) {
        return InteropLibrary.getFactory().getUncached().isNull(receiver);
    }

    protected static boolean asBooleanUnsupported(PolyglotLanguageContext context, Object receiver) {
        return invalidCastPrimitive(context, receiver, boolean.class, "asBoolean()", "isBoolean()", "Invalid or lossy primitive coercion.");
    }

    private static <T> T invalidCastPrimitive(PolyglotLanguageContext context, Object receiver, Class<T> clazz, String asMethodName, String isMethodName, String detail) {
        if (isNullUncached(receiver)) {
            throw nullCoercion(context, receiver, clazz, asMethodName, isMethodName);
        } else {
            throw cannotConvert(context, receiver, clazz, asMethodName, isMethodName, detail);
        }
    }

    @Override
    public int asInt(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return asIntUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    protected static int asIntUnsupported(PolyglotLanguageContext context, Object receiver) {
        return invalidCastPrimitive(context, receiver, int.class, "asInt()", "fitsInInt()", "Invalid or lossy primitive coercion.");
    }

    @Override
    public long asLong(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return asLongUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    protected static long asLongUnsupported(PolyglotLanguageContext context, Object receiver) {
        return invalidCastPrimitive(context, receiver, long.class, "asLong()", "fitsInLong()", "Invalid or lossy primitive coercion.");
    }

    @Override
    public double asDouble(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return asDoubleUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    protected static double asDoubleUnsupported(PolyglotLanguageContext context, Object receiver) {
        return invalidCastPrimitive(context, receiver, double.class, "asDouble()", "fitsInDouble()", "Invalid or lossy primitive coercion.");
    }

    @Override
    public float asFloat(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return asFloatUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    protected static float asFloatUnsupported(PolyglotLanguageContext context, Object receiver) {
        return invalidCastPrimitive(context, receiver, float.class, "asFloat()", "fitsInFloat()", "Invalid or lossy primitive coercion.");
    }

    @Override
    public byte asByte(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return asByteUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    protected static byte asByteUnsupported(PolyglotLanguageContext context, Object receiver) {
        return invalidCastPrimitive(context, receiver, byte.class, "asByte()", "fitsInByte()", "Invalid or lossy primitive coercion.");
    }

    @Override
    public short asShort(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return asShortUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    protected static short asShortUnsupported(PolyglotLanguageContext context, Object receiver) {
        return invalidCastPrimitive(context, receiver, short.class, "asShort()", "fitsInShort()", "Invalid or lossy primitive coercion.");
    }

    @Override
    public long asNativePointer(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return asNativePointerUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    static long asNativePointerUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw cannotConvert(context, receiver, long.class, "asNativePointer()", "isNativeObject()", "Value cannot be converted to a native pointer.");
    }

    @Override
    public Object asHostObject(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return asHostObjectUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    protected static Object asHostObjectUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw cannotConvert(context, receiver, null, "asHostObject()", "isHostObject()", "Value is not a host object.");
    }

    @Override
    public Object asProxyObject(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return asProxyObjectUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    protected static Object asProxyObjectUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw cannotConvert(context, receiver, null, "asProxyObject()", "isProxyObject()", "Value is not a proxy object.");
    }

    @Override
    public LocalDate asDate(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            if (isNullUncached(receiver)) {
                return null;
            } else {
                throw cannotConvert(context, receiver, null, "asDate()", "isDate()", "Value does not contain date information.");
            }
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @Override
    public LocalTime asTime(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            if (isNullUncached(receiver)) {
                return null;
            } else {
                throw cannotConvert(context, receiver, null, "asTime()", "isTime()", "Value does not contain time information.");
            }
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @Override
    public ZoneId asTimeZone(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            if (isNullUncached(receiver)) {
                return null;
            } else {
                throw cannotConvert(context, receiver, null, "asTimeZone()", "isTimeZone()", "Value does not contain time zone information.");
            }
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @Override
    public Instant asInstant(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            if (isNullUncached(receiver)) {
                return null;
            } else {
                throw cannotConvert(context, receiver, null, "asInstant()", "isInstant()", "Value does not contain instant information.");
            }
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @Override
    public Duration asDuration(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            if (isNullUncached(receiver)) {
                return null;
            } else {
                throw cannotConvert(context, receiver, null, "asDuration()", "isDuration()", "Value does not contain duration information.");
            }
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @Override
    public RuntimeException throwException(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw unsupported(context, receiver, "throwException()", "isException()");
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @Override
    public final Value getMetaObject(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return getMetaObjectImpl(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @Override
    public Value getIterator(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return getIteratorUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static final Value getIteratorUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "getIterator()", "hasIterator()");
    }

    @Override
    public boolean hasIteratorNextElement(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return hasIteratorNextElementUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static final boolean hasIteratorNextElementUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "hasIteratorNextElement()", "isIterator()");
    }

    @Override
    public Value getIteratorNextElement(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return getIteratorNextElementUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static final Value getIteratorNextElementUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "getIteratorNextElement()", "isIterator()");
    }

    @Override
    public long getHashSize(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw getHashSizeUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static final RuntimeException getHashSizeUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "getHashSize()", "hasHashEntries()");
    }

    @Override
    public Value getHashValue(Object languageContext, Object receiver, Object key) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw getHashValueUnsupported(context, receiver, key);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static final RuntimeException getHashValueUnsupported(PolyglotLanguageContext context, Object receiver, @SuppressWarnings("unused") Object key) {
        throw unsupported(context, receiver, "getHashValue(Object)", "hasHashEntries()");
    }

    @Override
    public Value getHashValueOrDefault(Object languageContext, Object receiver, Object key, Object defaultValue) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw getHashValueOrDefaultUnsupported(context, receiver, key, defaultValue);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    @SuppressWarnings("unused")
    static final RuntimeException getHashValueOrDefaultUnsupported(PolyglotLanguageContext context, Object receiver, Object key, Object defaultValue) {
        throw unsupported(context, receiver, "getHashValueOrDefault(Object, Object)", "hasHashEntries()");
    }

    @Override
    public void putHashEntry(Object languageContext, Object receiver, Object key, Object value) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            putHashEntryUnsupported(context, receiver, key, value);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static final RuntimeException putHashEntryUnsupported(PolyglotLanguageContext context, Object receiver,
                    @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value) {
        throw unsupported(context, receiver, "putHashEntry(Object, Object)", "hasHashEntries()");
    }

    @Override
    public boolean removeHashEntry(Object languageContext, Object receiver, Object key) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw removeHashEntryUnsupported(context, receiver, key);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static final RuntimeException removeHashEntryUnsupported(PolyglotLanguageContext context, Object receiver, @SuppressWarnings("unused") Object key) {
        throw unsupported(context, receiver, "removeHashEntry(Object)", "hasHashEntries()");
    }

    @Override
    public Value getHashEntriesIterator(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw getHashEntriesIteratorUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static final RuntimeException getHashEntriesIteratorUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "getHashEntriesIterator()", "hasHashEntries()");
    }

    @Override
    public Value getHashKeysIterator(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw getHashKeysIteratorUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static final RuntimeException getHashKeysIteratorUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "getHashKeysIterator()", "hasHashEntries()");
    }

    @Override
    public Value getHashValuesIterator(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw getHashValuesIteratorUnsupported(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @Override
    public void pin(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            languageInstance.sharing.engine.host.pin(receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @TruffleBoundary
    static final RuntimeException getHashValuesIteratorUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "getHashValuesIterator()", "hasHashEntries()");
    }

    protected Value getMetaObjectImpl(PolyglotLanguageContext context, Object receiver) {
        InteropLibrary lib = InteropLibrary.getFactory().getUncached(receiver);
        if (lib.hasMetaObject(receiver)) {
            try {
                return asValue(context, lib.getMetaObject(receiver));
            } catch (UnsupportedMessageException e) {
                throw shouldNotReachHere("Unexpected unsupported message.", e);
            }
        }
        return null;
    }

    private static Value asValue(PolyglotLanguageContext context, Object value) {
        if (context == null) {
            return PolyglotImpl.getInstance().asValue(PolyglotFastThreadLocals.getContext(null), value);
        } else {
            return context.asValue(value);
        }
    }

    static Object hostEnter(Object languageContext) {
        if (languageContext == null) {
            return null;
        }
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        PolyglotContextImpl c = context.context;
        try {
            return c.engine.enterIfNeeded(c, true);
        } catch (Throwable t) {
            throw guestToHostException(context, t, false);
        }
    }

    static void hostLeave(Object languageContext, Object prev) {
        if (languageContext == null) {
            return;
        }
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        try {
            PolyglotContextImpl c = context.context;
            c.engine.leaveIfNeeded(prev, c);
        } catch (Throwable t) {
            throw guestToHostException(context, t, false);
        }
    }

    @TruffleBoundary
    protected static RuntimeException unsupported(PolyglotLanguageContext context, Object receiver, String message, String useToCheck) {
        String polyglotMessage;
        if (useToCheck != null) {
            polyglotMessage = String.format("Unsupported operation %s.%s for %s. You can ensure that the operation is supported using %s.%s.",
                            Value.class.getSimpleName(), message, getValueInfo(context, receiver), Value.class.getSimpleName(), useToCheck);
        } else {
            polyglotMessage = String.format("Unsupported operation %s.%s for %s.",
                            Value.class.getSimpleName(), message, getValueInfo(context, receiver));
        }
        return PolyglotEngineException.unsupported(polyglotMessage);
    }

    private static final int CHARACTER_LIMIT = 140;

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    @TruffleBoundary
    static String getValueInfo(Object languageContext, Object receiver) {
        PolyglotContextImpl context = languageContext != null ? ((PolyglotLanguageContext) languageContext).context : null;
        return getValueInfo(context, receiver);
    }

    @TruffleBoundary
    static String getValueInfo(PolyglotContextImpl context, Object receiver) {
        if (context == null) {
            return receiver.toString();
        } else if (receiver == null) {
            assert false : "receiver should never be null";
            return "null";
        }
        PolyglotLanguage displayLanguage = EngineAccessor.EngineImpl.findObjectLanguage(context.engine, receiver);
        Object view;
        if (displayLanguage == null) {
            displayLanguage = context.engine.hostLanguage;
            view = context.getHostContext().getLanguageView(receiver);
        } else {
            view = receiver;
        }

        String valueToString;
        String metaObjectToString = UNKNOWN;
        try {
            InteropLibrary uncached = InteropLibrary.getFactory().getUncached(view);
            if (uncached.hasMetaObject(view)) {
                Object qualifiedName = INTEROP.getMetaQualifiedName(uncached.getMetaObject(view));
                metaObjectToString = truncateString(INTEROP.asString(qualifiedName), CHARACTER_LIMIT);
            }
            valueToString = truncateString(INTEROP.asString(uncached.toDisplayString(view)), CHARACTER_LIMIT);
        } catch (UnsupportedMessageException e) {
            throw shouldNotReachHere(e);
        }
        String languageName = null;
        boolean hideType = false;
        if (displayLanguage.isHost()) {
            languageName = "Java"; // java is our host language for now

            // hide meta objects of null
            if (UNKNOWN.equals(metaObjectToString) && INTEROP.isNull(receiver)) {
                hideType = true;
            }
        } else {
            languageName = displayLanguage.getName();
        }
        if (hideType) {
            return String.format("'%s'(language: %s)", valueToString, languageName);
        } else {
            return String.format("'%s'(language: %s, type: %s)", valueToString, languageName, metaObjectToString);
        }
    }

    private static String truncateString(String s, int i) {
        if (s.length() > i) {
            return s.substring(0, i - TRUNCATION_SUFFIX.length()) + TRUNCATION_SUFFIX;
        } else {
            return s;
        }
    }

    @TruffleBoundary
    protected static RuntimeException nullCoercion(Object languageContext, Object receiver, Class<?> targetType, String message, String useToCheck) {
        assert isEnteredOrNull(languageContext);
        String valueInfo = getValueInfo(languageContext, receiver);
        throw PolyglotEngineException.nullPointer(String.format("Cannot convert null value %s to Java type '%s' using %s.%s. " +
                        "You can ensure that the operation is supported using %s.%s.",
                        valueInfo, targetType, Value.class.getSimpleName(), message, Value.class.getSimpleName(), useToCheck));
    }

    static boolean isEnteredOrNull(Object languageContext) {
        if (languageContext == null) {
            return true;
        }
        PolyglotContextImpl context = ((PolyglotLanguageContext) languageContext).context;
        return !context.engine.needsEnter(context);
    }

    @TruffleBoundary
    protected static RuntimeException cannotConvert(Object languageContext, Object receiver, Class<?> targetType, String message, String useToCheck, String reason) {
        assert isEnteredOrNull(languageContext);
        String valueInfo = getValueInfo(languageContext, receiver);
        String targetTypeString = "";
        if (targetType != null) {
            targetTypeString = String.format("to Java type '%s'", targetType.getTypeName());
        }
        throw PolyglotEngineException.classCast(
                        String.format("Cannot convert %s %s using %s.%s: %s You can ensure that the value can be converted using %s.%s.",
                                        valueInfo, targetTypeString, Value.class.getSimpleName(), message, reason, Value.class.getSimpleName(), useToCheck));
    }

    @TruffleBoundary
    protected static RuntimeException invalidArrayIndex(PolyglotLanguageContext context, Object receiver, long index) {
        String message = String.format("Invalid array index %s for array %s.", index, getValueInfo(context, receiver));
        throw PolyglotEngineException.arrayIndexOutOfBounds(message);
    }

    @TruffleBoundary
    protected static RuntimeException invalidArrayValue(PolyglotLanguageContext context, Object receiver, long identifier, Object value) {
        throw PolyglotEngineException.classCast(
                        String.format("Invalid array value %s for array %s and index %s.",
                                        getValueInfo(context, value), getValueInfo(context, receiver), identifier));
    }

    @TruffleBoundary
    protected static RuntimeException nonReadableMemberKey(PolyglotLanguageContext context, Object receiver, String identifier) {
        String message = String.format("Non readable or non-existent member key '%s' for object %s.", identifier, getValueInfo(context, receiver));
        throw PolyglotEngineException.unsupported(message);
    }

    @TruffleBoundary
    protected static RuntimeException nonWritableMemberKey(PolyglotLanguageContext context, Object receiver, String identifier) {
        String message = String.format("Non writable or non-existent member key '%s' for object %s.", identifier, getValueInfo(context, receiver));
        throw PolyglotEngineException.unsupported(message);
    }

    @TruffleBoundary
    protected static RuntimeException nonRemovableMemberKey(PolyglotLanguageContext context, Object receiver, String identifier) {
        String message = String.format("Non removable or non-existent member key '%s' for object %s.", identifier, getValueInfo(context, receiver));
        throw PolyglotEngineException.unsupported(message);
    }

    @TruffleBoundary
    protected static RuntimeException invalidMemberValue(PolyglotLanguageContext context, Object receiver, String identifier, Object value) {
        String message = String.format("Invalid member value %s for object %s and member key '%s'.", getValueInfo(context, value), getValueInfo(context, receiver), identifier);
        throw PolyglotEngineException.illegalArgument(message);
    }

    @TruffleBoundary
    protected static RuntimeException stopIteration(PolyglotLanguageContext context, Object receiver) {
        String message = String.format("Iteration was stopped for iterator %s.", getValueInfo(context, receiver));
        throw PolyglotEngineException.noSuchElement(message);
    }

    @TruffleBoundary
    protected static RuntimeException nonReadableIteratorElement() {
        throw PolyglotEngineException.unsupported("Iterator element is not readable.");
    }

    @TruffleBoundary
    protected static RuntimeException invalidHashValue(PolyglotLanguageContext context, Object receiver, Object key, Object value) {
        String message = String.format("Invalid hash value %s for object %s and hash key %s.",
                        getValueInfo(context, value),
                        getValueInfo(context, receiver),
                        getValueInfo(context, key));
        throw PolyglotEngineException.illegalArgument(message);
    }

    @TruffleBoundary
    protected static RuntimeException invalidExecuteArgumentType(PolyglotLanguageContext context, Object receiver, UnsupportedTypeException e) {
        String originalMessage = e.getMessage() == null ? "" : e.getMessage() + " ";
        String[] formattedArgs = formatArgs(context, e.getSuppliedValues());
        throw PolyglotEngineException.illegalArgument(String.format("Invalid argument when executing %s. %sProvided arguments: %s.",
                        getValueInfo(context, receiver),
                        originalMessage,
                        Arrays.asList(formattedArgs)));
    }

    @TruffleBoundary
    protected static RuntimeException invalidInvokeArgumentType(PolyglotLanguageContext context, Object receiver, String member, UnsupportedTypeException e) {
        String originalMessage = e.getMessage() == null ? "" : e.getMessage();
        String[] formattedArgs = formatArgs(context, e.getSuppliedValues());
        String message = String.format("Invalid argument when invoking '%s' on %s. %sProvided arguments: %s.",
                        member,
                        getValueInfo(context, receiver),
                        originalMessage,
                        Arrays.asList(formattedArgs));
        throw PolyglotEngineException.illegalArgument(message);
    }

    @TruffleBoundary
    protected static RuntimeException invalidInstantiateArgumentType(PolyglotLanguageContext context, Object receiver, Object[] arguments) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument when instantiating %s with arguments %s.", getValueInfo(context, receiver), Arrays.asList(formattedArgs));
        throw PolyglotEngineException.illegalArgument(message);
    }

    @TruffleBoundary
    protected static RuntimeException invalidInstantiateArity(PolyglotLanguageContext context, Object receiver, Object[] arguments, int expectedMin, int expectedMax, int actual) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument count when instantiating %s with arguments %s. %s",
                        getValueInfo(context, receiver), Arrays.asList(formattedArgs), formatExpectedArguments(expectedMin, expectedMax, actual));
        throw PolyglotEngineException.illegalArgument(message);
    }

    @TruffleBoundary
    protected static RuntimeException invalidExecuteArity(PolyglotLanguageContext context, Object receiver, Object[] arguments, int expectedMin, int expectedMax, int actual) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument count when executing %s with arguments %s. %s",
                        getValueInfo(context, receiver), Arrays.asList(formattedArgs), formatExpectedArguments(expectedMin, expectedMax, actual));
        throw PolyglotEngineException.illegalArgument(message);
    }

    @TruffleBoundary
    protected static RuntimeException invalidInvokeArity(PolyglotLanguageContext context, Object receiver, String member, Object[] arguments, int expectedMin, int expectedMax, int actual) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument count when invoking '%s' on %s with arguments %s. %s",
                        member,
                        getValueInfo(context, receiver), Arrays.asList(formattedArgs), formatExpectedArguments(expectedMin, expectedMax, actual));
        throw PolyglotEngineException.illegalArgument(message);
    }

    static String formatExpectedArguments(int expectedMinArity, int expectedMaxArity, int actualArity) {
        String actual;
        if (actualArity < 0) {
            actual = "unknown";
        } else {
            actual = String.valueOf(actualArity);
        }
        String expected;
        if (expectedMinArity == expectedMaxArity) {
            expected = String.valueOf(expectedMinArity);
        } else {
            if (expectedMaxArity < 0) {
                expected = expectedMinArity + "+";
            } else {
                expected = expectedMinArity + "-" + expectedMaxArity;
            }
        }
        return String.format("Expected %s argument(s) but got %s.", expected, actual);
    }

    private static String[] formatArgs(Object languageContext, Object[] arguments) {
        String[] formattedArgs = new String[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            formattedArgs[i] = getValueInfo(languageContext, arguments[i]);
        }
        return formattedArgs;
    }

    @Override
    public final String toString(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            return toStringImpl(context, receiver);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    protected String toStringImpl(@SuppressWarnings("unused") Object languageContext, Object receiver) throws AssertionError {
        InteropLibrary lib = InteropLibrary.getFactory().getUncached(receiver);
        Object result = lib.toDisplayString(receiver);
        InteropLibrary resultLib = InteropLibrary.getFactory().getUncached(result);
        try {
            return resultLib.asString(result);
        } catch (UnsupportedMessageException e) {
            throw shouldNotReachHere("toDisplayString must be coercible to java.lang.String, but is not.", e);
        }
    }

    @Override
    public SourceSection getSourceLocation(Object languageContext, Object receiver) {
        if (languageContext == null) {
            return null;
        }
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            InteropLibrary lib = InteropLibrary.getFactory().getUncached(receiver);
            com.oracle.truffle.api.source.SourceSection result = null;
            if (lib.hasSourceLocation(receiver)) {
                try {
                    result = lib.getSourceLocation(receiver);
                } catch (UnsupportedMessageException e) {
                }
            }
            if (result == null) {
                return null;
            }
            return PolyglotImpl.getPolyglotSourceSection(impl, result);
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @Override
    public boolean isMetaObject(Object languageContext, Object receiver) {
        return false;
    }

    @Override
    public boolean equalsImpl(Object languageContext, Object receiver, Object obj) {
        if (receiver == obj) {
            return true;
        }
        return PolyglotWrapper.equals(languageContext, receiver, obj);
    }

    @Override
    public int hashCodeImpl(Object languageContext, Object receiver) {
        return PolyglotWrapper.hashCode(languageContext, receiver);
    }

    @Override
    public boolean isMetaInstance(Object languageContext, Object receiver, Object instance) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw unsupported(context, receiver, "isMetaInstance(Object)", "isMetaObject()");
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @Override
    public String getMetaQualifiedName(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw unsupported(context, receiver, "getMetaQualifiedName()", "isMetaObject()");
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @Override
    public String getMetaSimpleName(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw unsupported(context, receiver, "getMetaSimpleName()", "isMetaObject()");
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    @Override
    public boolean hasMetaParents(Object languageContext, Object receiver) {
        return false;
    }

    @Override
    public Value getMetaParents(Object languageContext, Object receiver) {
        PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
        Object prev = hostEnter(context);
        try {
            throw unsupported(context, receiver, "getMetaParents()", "hasMetaParents()");
        } catch (Throwable e) {
            throw guestToHostException(context, e, true);
        } finally {
            hostLeave(context, prev);
        }
    }

    static CallTarget createTarget(InteropNode root) {
        CallTarget target = root.getCallTarget();
        Class<?>[] types = root.getArgumentTypes();
        if (types != null) {
            RUNTIME.initializeProfile(target, types);
        }
        return target;
    }

    static PolyglotValueDispatch createInteropValue(PolyglotLanguageInstance languageInstance, TruffleObject receiver, Class<?> receiverType) {
        return new InteropValue(languageInstance.getImpl(), languageInstance, receiver, receiverType);
    }

    static PolyglotValueDispatch createHostNull(PolyglotImpl polyglot) {
        return new HostNull(polyglot);
    }

    static void createDefaultValues(PolyglotImpl polyglot, PolyglotLanguageInstance languageInstance, Map<Class<?>, PolyglotValueDispatch> valueCache) {
        addDefaultValue(polyglot, languageInstance, valueCache, false);
        addDefaultValue(polyglot, languageInstance, valueCache, "");
        addDefaultValue(polyglot, languageInstance, valueCache, TruffleString.fromJavaStringUncached("", TruffleString.Encoding.UTF_16));
        addDefaultValue(polyglot, languageInstance, valueCache, 'a');
        addDefaultValue(polyglot, languageInstance, valueCache, (byte) 0);
        addDefaultValue(polyglot, languageInstance, valueCache, (short) 0);
        addDefaultValue(polyglot, languageInstance, valueCache, 0);
        addDefaultValue(polyglot, languageInstance, valueCache, 0L);
        addDefaultValue(polyglot, languageInstance, valueCache, 0F);
        addDefaultValue(polyglot, languageInstance, valueCache, 0D);
    }

    static void addDefaultValue(PolyglotImpl polyglot, PolyglotLanguageInstance languageInstance, Map<Class<?>, PolyglotValueDispatch> valueCache, Object primitive) {
        valueCache.put(primitive.getClass(), new PrimitiveValue(polyglot, languageInstance, primitive));
    }

    static final class PrimitiveValue extends PolyglotValueDispatch {

        private final InteropLibrary interop;
        private final PolyglotLanguage language;

        private PrimitiveValue(PolyglotImpl impl, PolyglotLanguageInstance instance, Object primitiveValue) {
            super(impl, instance);
            /*
             * No caching needed for primitives. We do that to avoid the overhead of crossing a
             * Truffle call boundary.
             */
            this.interop = InteropLibrary.getFactory().getUncached(primitiveValue);
            this.language = instance != null ? instance.language : null;
        }

        @Override
        public boolean isString(Object languageContext, Object receiver) {
            return interop.isString(receiver);
        }

        @Override
        public boolean isBoolean(Object languageContext, Object receiver) {
            return interop.isBoolean(receiver);
        }

        @Override
        public boolean asBoolean(Object languageContext, Object receiver) {
            try {
                return interop.asBoolean(receiver);
            } catch (UnsupportedMessageException e) {
                return super.asBoolean(languageContext, receiver);
            }
        }

        @Override
        public String asString(Object languageContext, Object receiver) {
            try {
                return interop.asString(receiver);
            } catch (UnsupportedMessageException e) {
                return super.asString(languageContext, receiver);
            }
        }

        @Override
        public boolean isNumber(Object languageContext, Object receiver) {
            return interop.isNumber(receiver);
        }

        @Override
        public boolean fitsInByte(Object languageContext, Object receiver) {
            return interop.fitsInByte(receiver);
        }

        @Override
        public boolean fitsInShort(Object languageContext, Object receiver) {
            return interop.fitsInShort(receiver);
        }

        @Override
        public boolean fitsInInt(Object languageContext, Object receiver) {
            return interop.fitsInInt(receiver);
        }

        @Override
        public boolean fitsInLong(Object languageContext, Object receiver) {
            return interop.fitsInLong(receiver);
        }

        @Override
        public boolean fitsInFloat(Object languageContext, Object receiver) {
            return interop.fitsInFloat(receiver);
        }

        @Override
        public boolean fitsInDouble(Object languageContext, Object receiver) {
            return interop.fitsInDouble(receiver);
        }

        @Override
        public byte asByte(Object languageContext, Object receiver) {
            try {
                return interop.asByte(receiver);
            } catch (UnsupportedMessageException e) {
                return super.asByte(languageContext, receiver);
            }
        }

        @Override
        public short asShort(Object languageContext, Object receiver) {
            try {
                return interop.asShort(receiver);
            } catch (UnsupportedMessageException e) {
                return super.asShort(languageContext, receiver);
            }
        }

        @Override
        public int asInt(Object languageContext, Object receiver) {
            try {
                return interop.asInt(receiver);
            } catch (UnsupportedMessageException e) {
                return super.asInt(languageContext, receiver);
            }
        }

        @Override
        public long asLong(Object languageContext, Object receiver) {
            try {
                return interop.asLong(receiver);
            } catch (UnsupportedMessageException e) {
                return super.asLong(languageContext, receiver);
            }
        }

        @Override
        public float asFloat(Object languageContext, Object receiver) {
            try {
                return interop.asFloat(receiver);
            } catch (UnsupportedMessageException e) {
                return super.asFloat(languageContext, receiver);
            }
        }

        @Override
        public double asDouble(Object languageContext, Object receiver) {
            try {
                return interop.asDouble(receiver);
            } catch (UnsupportedMessageException e) {
                return super.asDouble(languageContext, receiver);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T as(Object languageContext, Object receiver, Class<T> targetType) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object prev = hostEnter(context);
            try {
                if (context != null) {
                    return language.engine.host.toHostType(null, context.context.getHostContextImpl(), receiver, targetType, targetType);
                } else {
                    // disconnected primitive value
                    T result = (T) EngineAccessor.HOST.convertPrimitiveLossy(receiver, targetType);
                    if (result == null) {
                        throw PolyglotInteropErrors.cannotConvertPrimitive(null, receiver, targetType);
                    }
                    return result;
                }
            } catch (Throwable e) {
                throw guestToHostException((context), e, true);
            } finally {
                hostLeave(context, prev);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T as(Object languageContext, Object receiver, TypeLiteral<T> targetType) {
            return as(languageContext, receiver, targetType.getRawType());
        }

        @Override
        public Value getMetaObjectImpl(PolyglotLanguageContext languageContext, Object receiver) {
            return super.getMetaObjectImpl(languageContext, getLanguageView(languageContext, receiver));
        }

        @Override
        protected String toStringImpl(Object languageContext, Object receiver) throws AssertionError {
            return super.toStringImpl(languageContext, getLanguageView(languageContext, receiver));
        }

        private Object getLanguageView(Object languageContext, Object receiver) {
            if (languageContext == null || language == null) {
                return receiver;
            }
            PolyglotContextImpl c = ((PolyglotLanguageContext) languageContext).context;
            return c.getContext(language).getLanguageViewNoCheck(receiver);
        }

    }

    private static final class HostNull extends PolyglotValueDispatch {

        private final PolyglotImpl polyglot;

        HostNull(PolyglotImpl polyglot) {
            super(polyglot, null);
            this.polyglot = polyglot;
        }

        @Override
        public boolean isNull(Object languageContext, Object receiver) {
            return true;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T as(Object languageContext, Object receiver, Class<T> targetType) {
            if (targetType == Value.class) {
                return (T) polyglot.hostNull;
            }
            return null;
        }

        @SuppressWarnings("cast")
        @Override
        public <T> T as(Object languageContext, Object receiver, TypeLiteral<T> targetType) {
            return as(languageContext, receiver, (Class<T>) targetType.getRawType());
        }

    }

    abstract static class InteropNode extends HostToGuestRootNode {

        protected static final int CACHE_LIMIT = 5;

        protected final InteropValue polyglot;

        protected abstract String getOperationName();

        protected InteropNode(InteropValue polyglot) {
            super(polyglot.languageInstance);
            this.polyglot = polyglot;
        }

        protected abstract Class<?>[] getArgumentTypes();

        @Override
        protected Class<? extends Object> getReceiverType() {
            return polyglot.receiverType;
        }

        protected final ToHostValueNode createToHost() {
            return ToHostValueNode.create(getImpl());
        }

        @Override
        public final String getName() {
            return "org.graalvm.polyglot.Value<" + polyglot.receiverType.getSimpleName() + ">." + getOperationName();
        }

        protected final AbstractPolyglotImpl getImpl() {
            return polyglot.impl;
        }

        @Override
        public final String toString() {
            return getName();
        }

    }

    /**
     * Host value implementation used when a Value needs to be created but not context is available.
     * If a context is available the normal interop value implementation is used.
     */
    static final class HostValue extends PolyglotValueDispatch {

        HostValue(PolyglotImpl polyglot) {
            super(polyglot, null);
        }

        @Override
        public boolean isHostObject(Object languageContext, Object receiver) {
            return EngineAccessor.HOST.isDisconnectedHostObject(receiver);
        }

        @Override
        public Object asHostObject(Object languageContext, Object receiver) {
            return EngineAccessor.HOST.unboxDisconnectedHostObject(receiver);
        }

        @Override
        public boolean isProxyObject(Object languageContext, Object receiver) {
            return EngineAccessor.HOST.isDisconnectedHostProxy(receiver);
        }

        @Override
        public Object asProxyObject(Object languageContext, Object receiver) {
            return EngineAccessor.HOST.unboxDisconnectedHostProxy(receiver);
        }

        @Override
        public <T> T as(Object languageContext, Object receiver, Class<T> targetType) {
            return asImpl(languageContext, receiver, targetType);
        }

        @SuppressWarnings("cast")
        @Override
        public <T> T as(Object languageContext, Object receiver, TypeLiteral<T> targetType) {
            return asImpl(languageContext, receiver, (Class<T>) targetType.getRawType());
        }

        <T> T asImpl(Object languageContext, Object receiver, Class<T> targetType) {
            Object hostValue;
            if (isProxyObject(languageContext, receiver)) {
                hostValue = asProxyObject(languageContext, receiver);
            } else if (isHostObject(languageContext, receiver)) {
                hostValue = asHostObject(languageContext, receiver);
            } else {
                throw new ClassCastException();
            }
            return targetType.cast(hostValue);
        }

    }

    @SuppressWarnings("unused")
    static final class InteropValue extends PolyglotValueDispatch {

        final CallTarget isNativePointer;
        final CallTarget asNativePointer;
        final CallTarget hasArrayElements;
        final CallTarget getArrayElement;
        final CallTarget setArrayElement;
        final CallTarget removeArrayElement;
        final CallTarget getArraySize;
        final CallTarget hasBufferElements;
        final CallTarget isBufferWritable;
        final CallTarget getBufferSize;
        final CallTarget readBufferByte;
        final CallTarget writeBufferByte;
        final CallTarget readBufferShort;
        final CallTarget writeBufferShort;
        final CallTarget readBufferInt;
        final CallTarget writeBufferInt;
        final CallTarget readBufferLong;
        final CallTarget writeBufferLong;
        final CallTarget readBufferFloat;
        final CallTarget writeBufferFloat;
        final CallTarget readBufferDouble;
        final CallTarget writeBufferDouble;
        final CallTarget hasMembers;
        final CallTarget hasMember;
        final CallTarget getMember;
        final CallTarget putMember;
        final CallTarget removeMember;
        final CallTarget isNull;
        final CallTarget canExecute;
        final CallTarget execute;
        final CallTarget canInstantiate;
        final CallTarget newInstance;
        final CallTarget executeNoArgs;
        final CallTarget executeVoid;
        final CallTarget executeVoidNoArgs;
        final CallTarget canInvoke;
        final CallTarget invoke;
        final CallTarget invokeNoArgs;
        final CallTarget getMemberKeys;
        final CallTarget isDate;
        final CallTarget asDate;
        final CallTarget isTime;
        final CallTarget asTime;
        final CallTarget isTimeZone;
        final CallTarget asTimeZone;
        final CallTarget asInstant;
        final CallTarget isDuration;
        final CallTarget asDuration;
        final CallTarget isException;
        final CallTarget throwException;
        final CallTarget isMetaObject;
        final CallTarget isMetaInstance;
        final CallTarget getMetaQualifiedName;
        final CallTarget getMetaSimpleName;
        final CallTarget hasMetaParents;
        final CallTarget getMetaParents;
        final CallTarget hasIterator;
        final CallTarget getIterator;
        final CallTarget isIterator;
        final CallTarget hasIteratorNextElement;
        final CallTarget getIteratorNextElement;
        final CallTarget hasHashEntries;
        final CallTarget getHashSize;
        final CallTarget hasHashEntry;
        final CallTarget getHashValue;
        final CallTarget getHashValueOrDefault;
        final CallTarget putHashEntry;
        final CallTarget removeHashEntry;
        final CallTarget getHashEntriesIterator;
        final CallTarget getHashKeysIterator;
        final CallTarget getHashValuesIterator;

        final CallTarget asClassLiteral;
        final CallTarget asTypeLiteral;
        final Class<?> receiverType;

        InteropValue(PolyglotImpl polyglot, PolyglotLanguageInstance languageInstance, Object receiverObject, Class<?> receiverType) {
            super(polyglot, languageInstance);
            this.receiverType = receiverType;
            this.asClassLiteral = createTarget(new AsClassLiteralNode(this));
            this.asTypeLiteral = createTarget(new AsTypeLiteralNode(this));
            this.isNativePointer = createTarget(IsNativePointerNodeGen.create(this));
            this.asNativePointer = createTarget(AsNativePointerNodeGen.create(this));
            this.hasArrayElements = createTarget(HasArrayElementsNodeGen.create(this));
            this.getArrayElement = createTarget(GetArrayElementNodeGen.create(this));
            this.setArrayElement = createTarget(SetArrayElementNodeGen.create(this));
            this.removeArrayElement = createTarget(RemoveArrayElementNodeGen.create(this));
            this.getArraySize = createTarget(GetArraySizeNodeGen.create(this));
            this.hasBufferElements = createTarget(HasBufferElementsNodeGen.create(this));
            this.isBufferWritable = createTarget(IsBufferWritableNodeGen.create(this));
            this.getBufferSize = createTarget(GetBufferSizeNodeGen.create(this));
            this.readBufferByte = createTarget(ReadBufferByteNodeGen.create(this));
            this.writeBufferByte = createTarget(WriteBufferByteNodeGen.create(this));
            this.readBufferShort = createTarget(ReadBufferShortNodeGen.create(this));
            this.writeBufferShort = createTarget(WriteBufferShortNodeGen.create(this));
            this.readBufferInt = createTarget(ReadBufferIntNodeGen.create(this));
            this.writeBufferInt = createTarget(WriteBufferIntNodeGen.create(this));
            this.readBufferLong = createTarget(ReadBufferLongNodeGen.create(this));
            this.writeBufferLong = createTarget(WriteBufferLongNodeGen.create(this));
            this.readBufferFloat = createTarget(ReadBufferFloatNodeGen.create(this));
            this.writeBufferFloat = createTarget(WriteBufferFloatNodeGen.create(this));
            this.readBufferDouble = createTarget(ReadBufferDoubleNodeGen.create(this));
            this.writeBufferDouble = createTarget(WriteBufferDoubleNodeGen.create(this));
            this.hasMember = createTarget(HasMemberNodeGen.create(this));
            this.getMember = createTarget(GetMemberNodeGen.create(this));
            this.putMember = createTarget(PutMemberNodeGen.create(this));
            this.removeMember = createTarget(RemoveMemberNodeGen.create(this));
            this.isNull = createTarget(IsNullNodeGen.create(this));
            this.execute = createTarget(new ExecuteNode(this));
            this.executeNoArgs = createTarget(new ExecuteNoArgsNode(this));
            this.executeVoid = createTarget(new ExecuteVoidNode(this));
            this.executeVoidNoArgs = createTarget(new ExecuteVoidNoArgsNode(this));
            this.newInstance = createTarget(NewInstanceNodeGen.create(this));
            this.canInstantiate = createTarget(CanInstantiateNodeGen.create(this));
            this.canExecute = createTarget(CanExecuteNodeGen.create(this));
            this.canInvoke = createTarget(CanInvokeNodeGen.create(this));
            this.invoke = createTarget(new InvokeNode(this));
            this.invokeNoArgs = createTarget(new InvokeNoArgsNode(this));
            this.hasMembers = createTarget(HasMembersNodeGen.create(this));
            this.getMemberKeys = createTarget(GetMemberKeysNodeGen.create(this));
            this.isDate = createTarget(IsDateNodeGen.create(this));
            this.asDate = createTarget(AsDateNodeGen.create(this));
            this.isTime = createTarget(IsTimeNodeGen.create(this));
            this.asTime = createTarget(AsTimeNodeGen.create(this));
            this.isTimeZone = createTarget(IsTimeZoneNodeGen.create(this));
            this.asTimeZone = createTarget(AsTimeZoneNodeGen.create(this));
            this.asInstant = createTarget(AsInstantNodeGen.create(this));
            this.isDuration = createTarget(IsDurationNodeGen.create(this));
            this.asDuration = createTarget(AsDurationNodeGen.create(this));
            this.isException = createTarget(IsExceptionNodeGen.create(this));
            this.throwException = createTarget(ThrowExceptionNodeGen.create(this));
            this.isMetaObject = createTarget(IsMetaObjectNodeGen.create(this));
            this.isMetaInstance = createTarget(IsMetaInstanceNodeGen.create(this));
            this.getMetaQualifiedName = createTarget(GetMetaQualifiedNameNodeGen.create(this));
            this.getMetaSimpleName = createTarget(GetMetaSimpleNameNodeGen.create(this));
            this.hasMetaParents = createTarget(PolyglotValueDispatchFactory.InteropValueFactory.HasMetaParentsNodeGen.create(this));
            this.getMetaParents = createTarget(PolyglotValueDispatchFactory.InteropValueFactory.GetMetaParentsNodeGen.create(this));
            this.hasIterator = createTarget(HasIteratorNodeGen.create(this));
            this.getIterator = createTarget(PolyglotValueDispatchFactory.InteropValueFactory.GetIteratorNodeGen.create(this));
            this.isIterator = createTarget(PolyglotValueDispatchFactory.InteropValueFactory.IsIteratorNodeGen.create(this));
            this.hasIteratorNextElement = createTarget(HasIteratorNextElementNodeGen.create(this));
            this.getIteratorNextElement = createTarget(GetIteratorNextElementNodeGen.create(this));
            this.hasHashEntries = createTarget(HasHashEntriesNodeGen.create(this));
            this.getHashSize = createTarget(GetHashSizeNodeGen.create(this));
            this.hasHashEntry = createTarget(HasHashEntryNodeGen.create(this));
            this.getHashValue = createTarget(GetHashValueNodeGen.create(this));
            this.getHashValueOrDefault = createTarget(GetHashValueOrDefaultNodeGen.create(this));
            this.putHashEntry = createTarget(PutHashEntryNodeGen.create(this));
            this.removeHashEntry = createTarget(RemoveHashEntryNodeGen.create(this));
            this.getHashEntriesIterator = createTarget(GetHashEntriesIteratorNodeGen.create(this));
            this.getHashKeysIterator = createTarget(GetHashKeysIteratorNodeGen.create(this));
            this.getHashValuesIterator = createTarget(GetHashValuesIteratorNodeGen.create(this));

        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T as(Object languageContext, Object receiver, Class<T> targetType) {
            return (T) RUNTIME.callProfiled(this.asClassLiteral, languageContext, receiver, targetType);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T as(Object languageContext, Object receiver, TypeLiteral<T> targetType) {
            return (T) RUNTIME.callProfiled(this.asTypeLiteral, languageContext, receiver, targetType);
        }

        @Override
        public boolean isNativePointer(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.isNativePointer, languageContext, receiver);
        }

        @Override
        public boolean hasArrayElements(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.hasArrayElements, languageContext, receiver);
        }

        @Override
        public Value getArrayElement(Object languageContext, Object receiver, long index) {
            return (Value) RUNTIME.callProfiled(this.getArrayElement, languageContext, receiver, index);
        }

        @Override
        public void setArrayElement(Object languageContext, Object receiver, long index, Object value) {
            RUNTIME.callProfiled(this.setArrayElement, languageContext, receiver, index, value);
        }

        @Override
        public boolean removeArrayElement(Object languageContext, Object receiver, long index) {
            return (boolean) RUNTIME.callProfiled(this.removeArrayElement, languageContext, receiver, index);
        }

        @Override
        public long getArraySize(Object languageContext, Object receiver) {
            return (long) RUNTIME.callProfiled(this.getArraySize, languageContext, receiver);
        }

        // region Buffer Methods

        @Override
        public boolean hasBufferElements(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.hasBufferElements, languageContext, receiver);
        }

        @Override
        public boolean isBufferWritable(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.isBufferWritable, languageContext, receiver);
        }

        @Override
        public long getBufferSize(Object languageContext, Object receiver) throws UnsupportedOperationException {
            return (long) RUNTIME.callProfiled(this.getBufferSize, languageContext, receiver);
        }

        @Override
        public byte readBufferByte(Object languageContext, Object receiver, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
            return (byte) RUNTIME.callProfiled(this.readBufferByte, languageContext, receiver, byteOffset);
        }

        @Override
        public void writeBufferByte(Object languageContext, Object receiver, long byteOffset, byte value) throws UnsupportedOperationException, IndexOutOfBoundsException {
            RUNTIME.callProfiled(this.writeBufferByte, languageContext, receiver, byteOffset, value);
        }

        @Override
        public short readBufferShort(Object languageContext, Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
            return (short) RUNTIME.callProfiled(this.readBufferShort, languageContext, receiver, order, byteOffset);
        }

        @Override
        public void writeBufferShort(Object languageContext, Object receiver, ByteOrder order, long byteOffset, short value) throws UnsupportedOperationException, IndexOutOfBoundsException {
            RUNTIME.callProfiled(this.writeBufferShort, languageContext, receiver, order, byteOffset, value);
        }

        @Override
        public int readBufferInt(Object languageContext, Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
            return (int) RUNTIME.callProfiled(this.readBufferInt, languageContext, receiver, order, byteOffset);
        }

        @Override
        public void writeBufferInt(Object languageContext, Object receiver, ByteOrder order, long byteOffset, int value) throws UnsupportedOperationException, IndexOutOfBoundsException {
            RUNTIME.callProfiled(this.writeBufferInt, languageContext, receiver, order, byteOffset, value);
        }

        @Override
        public long readBufferLong(Object languageContext, Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
            return (long) RUNTIME.callProfiled(this.readBufferLong, languageContext, receiver, order, byteOffset);
        }

        @Override
        public void writeBufferLong(Object languageContext, Object receiver, ByteOrder order, long byteOffset, long value) throws UnsupportedOperationException, IndexOutOfBoundsException {
            RUNTIME.callProfiled(this.writeBufferLong, languageContext, receiver, order, byteOffset, value);
        }

        @Override
        public float readBufferFloat(Object languageContext, Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
            return (float) RUNTIME.callProfiled(this.readBufferFloat, languageContext, receiver, order, byteOffset);
        }

        @Override
        public void writeBufferFloat(Object languageContext, Object receiver, ByteOrder order, long byteOffset, float value) throws UnsupportedOperationException, IndexOutOfBoundsException {
            RUNTIME.callProfiled(this.writeBufferFloat, languageContext, receiver, order, byteOffset, value);
        }

        @Override
        public double readBufferDouble(Object languageContext, Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
            return (double) RUNTIME.callProfiled(this.readBufferDouble, languageContext, receiver, order, byteOffset);
        }

        @Override
        public void writeBufferDouble(Object languageContext, Object receiver, ByteOrder order, long byteOffset, double value) throws UnsupportedOperationException, IndexOutOfBoundsException {
            RUNTIME.callProfiled(this.writeBufferDouble, languageContext, receiver, order, byteOffset, value);
        }

        // endregion

        @Override
        public boolean hasMembers(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.hasMembers, languageContext, receiver);
        }

        @Override
        public Value getMember(Object languageContext, Object receiver, String key) {
            return (Value) RUNTIME.callProfiled(this.getMember, languageContext, receiver, key);
        }

        @Override
        public boolean hasMember(Object languageContext, Object receiver, String key) {
            return (boolean) RUNTIME.callProfiled(this.hasMember, languageContext, receiver, key);
        }

        @Override
        public void putMember(Object languageContext, Object receiver, String key, Object member) {
            RUNTIME.callProfiled(this.putMember, languageContext, receiver, key, member);
        }

        @Override
        public boolean removeMember(Object languageContext, Object receiver, String key) {
            return (boolean) RUNTIME.callProfiled(this.removeMember, languageContext, receiver, key);
        }

        @Override
        public Set<String> getMemberKeys(Object languageContext, Object receiver) {
            Value keys = (Value) RUNTIME.callProfiled(this.getMemberKeys, languageContext, receiver);
            if (keys == null) {
                // unsupported
                return Collections.emptySet();
            }
            return new MemberSet(languageContext, receiver, keys);
        }

        @Override
        public long asNativePointer(Object languageContext, Object receiver) {
            return (long) RUNTIME.callProfiled(this.asNativePointer, languageContext, receiver);
        }

        @Override
        public boolean isDate(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.isDate, languageContext, receiver);
        }

        @Override
        public LocalDate asDate(Object languageContext, Object receiver) {
            return (LocalDate) RUNTIME.callProfiled(this.asDate, languageContext, receiver);
        }

        @Override
        public boolean isTime(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.isTime, languageContext, receiver);
        }

        @Override
        public LocalTime asTime(Object languageContext, Object receiver) {
            return (LocalTime) RUNTIME.callProfiled(this.asTime, languageContext, receiver);
        }

        @Override
        public boolean isTimeZone(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.isTimeZone, languageContext, receiver);
        }

        @Override
        public ZoneId asTimeZone(Object languageContext, Object receiver) {
            return (ZoneId) RUNTIME.callProfiled(this.asTimeZone, languageContext, receiver);
        }

        @Override
        public Instant asInstant(Object languageContext, Object receiver) {
            return (Instant) RUNTIME.callProfiled(this.asInstant, languageContext, receiver);
        }

        @Override
        public boolean isDuration(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.isDuration, languageContext, receiver);
        }

        @Override
        public Duration asDuration(Object languageContext, Object receiver) {
            return (Duration) RUNTIME.callProfiled(this.asDuration, languageContext, receiver);
        }

        @Override
        public boolean isHostObject(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object prev = hostEnter(context);
            try {
                return getEngine().host.isHostObject(receiver);
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, prev);
            }
        }

        private PolyglotEngineImpl getEngine() {
            return languageInstance.sharing.engine;
        }

        @Override
        public boolean isProxyObject(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object prev = hostEnter(context);
            try {
                return getEngine().host.isHostProxy(receiver);
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, prev);
            }
        }

        @Override
        public Object asProxyObject(Object languageContext, Object receiver) {
            if (isProxyObject(languageContext, receiver)) {
                return getEngine().host.unboxProxyObject(receiver);
            } else {
                return super.asProxyObject(languageContext, receiver);
            }
        }

        @Override
        public Object asHostObject(Object languageContext, Object receiver) {
            if (isHostObject(languageContext, receiver)) {
                return getEngine().host.unboxHostObject(receiver);
            } else {
                return super.asHostObject(languageContext, receiver);
            }
        }

        @Override
        public boolean isNull(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.isNull, languageContext, receiver);
        }

        @Override
        public boolean canExecute(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.canExecute, languageContext, receiver);
        }

        @Override
        public void executeVoid(Object languageContext, Object receiver, Object[] arguments) {
            RUNTIME.callProfiled(this.executeVoid, languageContext, receiver, arguments);
        }

        @Override
        public void executeVoid(Object languageContext, Object receiver) {
            RUNTIME.callProfiled(this.executeVoidNoArgs, languageContext, receiver);
        }

        @Override
        public Value execute(Object languageContext, Object receiver, Object[] arguments) {
            return (Value) RUNTIME.callProfiled(this.execute, languageContext, receiver, arguments);
        }

        @Override
        public Value execute(Object languageContext, Object receiver) {
            return (Value) RUNTIME.callProfiled(this.executeNoArgs, languageContext, receiver);
        }

        @Override
        public boolean canInstantiate(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.canInstantiate, languageContext, receiver);
        }

        @Override
        public Value newInstance(Object languageContext, Object receiver, Object[] arguments) {
            return (Value) RUNTIME.callProfiled(this.newInstance, languageContext, receiver, arguments);
        }

        @Override
        public boolean canInvoke(Object languageContext, String identifier, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.canInvoke, languageContext, receiver, identifier);
        }

        @Override
        public Value invoke(Object languageContext, Object receiver, String identifier, Object[] arguments) {
            return (Value) RUNTIME.callProfiled(this.invoke, languageContext, receiver, identifier, arguments);
        }

        @Override
        public Value invoke(Object languageContext, Object receiver, String identifier) {
            return (Value) RUNTIME.callProfiled(this.invokeNoArgs, languageContext, receiver, identifier);
        }

        @Override
        public boolean isException(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.isException, languageContext, receiver);
        }

        @Override
        public RuntimeException throwException(Object languageContext, Object receiver) {
            RUNTIME.callProfiled(this.throwException, languageContext, receiver);
            throw super.throwException(languageContext, receiver);
        }

        @Override
        public boolean isNumber(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object c = hostEnter(context);
            try {
                return UNCACHED_INTEROP.isNumber(receiver);
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, c);
            }
        }

        @Override
        public boolean fitsInByte(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object c = hostEnter(context);
            try {
                return UNCACHED_INTEROP.fitsInByte(receiver);
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, c);
            }
        }

        @Override
        public byte asByte(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object c = hostEnter(context);
            try {
                try {
                    return UNCACHED_INTEROP.asByte(receiver);
                } catch (UnsupportedMessageException e) {
                    return asByteUnsupported(context, receiver);
                }
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, c);
            }
        }

        @Override
        public boolean isString(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object c = hostEnter(context);
            try {
                return UNCACHED_INTEROP.isString(receiver);
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, c);
            }
        }

        @Override
        public String asString(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object c = hostEnter(context);
            try {
                try {
                    if (isNullUncached(receiver)) {
                        return null;
                    }
                    return UNCACHED_INTEROP.asString(receiver);
                } catch (UnsupportedMessageException e) {
                    return asStringUnsupported(context, receiver);
                }
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, c);
            }
        }

        @Override
        public boolean fitsInInt(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object c = hostEnter(context);
            try {
                return UNCACHED_INTEROP.fitsInInt(receiver);
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, c);
            }
        }

        @Override
        public int asInt(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object c = hostEnter(context);
            try {
                try {
                    return UNCACHED_INTEROP.asInt(receiver);
                } catch (UnsupportedMessageException e) {
                    return asIntUnsupported(context, receiver);
                }
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, c);
            }
        }

        @Override
        public boolean isBoolean(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object c = hostEnter(context);
            try {
                return InteropLibrary.getFactory().getUncached().isBoolean(receiver);
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, c);
            }
        }

        @Override
        public boolean asBoolean(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object c = hostEnter(context);
            try {
                try {
                    return InteropLibrary.getFactory().getUncached().asBoolean(receiver);
                } catch (UnsupportedMessageException e) {
                    return asBooleanUnsupported(context, receiver);
                }
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, c);
            }
        }

        @Override
        public boolean fitsInFloat(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object c = hostEnter(context);
            try {
                return InteropLibrary.getFactory().getUncached().fitsInFloat(receiver);
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, c);
            }
        }

        @Override
        public float asFloat(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object c = hostEnter(context);
            try {
                try {
                    return UNCACHED_INTEROP.asFloat(receiver);
                } catch (UnsupportedMessageException e) {
                    return asFloatUnsupported(context, receiver);
                }
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, c);
            }
        }

        @Override
        public boolean fitsInDouble(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object c = hostEnter(context);
            try {
                return UNCACHED_INTEROP.fitsInDouble(receiver);
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, c);
            }
        }

        @Override
        public double asDouble(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object c = hostEnter(context);
            try {
                try {
                    return UNCACHED_INTEROP.asDouble(receiver);
                } catch (UnsupportedMessageException e) {
                    return asDoubleUnsupported(context, receiver);
                }
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, c);
            }
        }

        @Override
        public boolean fitsInLong(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object c = hostEnter(context);
            try {
                return UNCACHED_INTEROP.fitsInLong(receiver);
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, c);
            }
        }

        @Override
        public long asLong(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object c = hostEnter(context);
            try {
                try {
                    return UNCACHED_INTEROP.asLong(receiver);
                } catch (UnsupportedMessageException e) {
                    return asLongUnsupported(context, receiver);
                }
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, c);
            }
        }

        @Override
        public boolean fitsInShort(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object c = hostEnter(context);
            try {
                return UNCACHED_INTEROP.fitsInShort(receiver);
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, c);
            }
        }

        @Override
        public short asShort(Object languageContext, Object receiver) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) languageContext;
            Object c = hostEnter(context);
            try {
                try {
                    return UNCACHED_INTEROP.asShort(receiver);
                } catch (UnsupportedMessageException e) {
                    return asShortUnsupported(context, receiver);
                }
            } catch (Throwable e) {
                throw guestToHostException(context, e, true);
            } finally {
                hostLeave(context, c);
            }
        }

        @Override
        public boolean isMetaObject(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.isMetaObject, languageContext, receiver);
        }

        @Override
        public boolean isMetaInstance(Object languageContext, Object receiver, Object instance) {
            return (boolean) RUNTIME.callProfiled(this.isMetaInstance, languageContext, receiver, instance);
        }

        @Override
        public String getMetaQualifiedName(Object languageContext, Object receiver) {
            return (String) RUNTIME.callProfiled(this.getMetaQualifiedName, languageContext, receiver);
        }

        @Override
        public String getMetaSimpleName(Object languageContext, Object receiver) {
            return (String) RUNTIME.callProfiled(this.getMetaSimpleName, languageContext, receiver);
        }

        @Override
        public boolean hasMetaParents(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.hasMetaParents, languageContext, receiver);
        }

        @Override
        public Value getMetaParents(Object languageContext, Object receiver) {
            return (Value) RUNTIME.callProfiled(this.getMetaParents, languageContext, receiver);
        }

        @Override
        public boolean hasIterator(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.hasIterator, languageContext, receiver);
        }

        @Override
        public Value getIterator(Object languageContext, Object receiver) {
            return (Value) RUNTIME.callProfiled(this.getIterator, languageContext, receiver);
        }

        @Override
        public boolean isIterator(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.isIterator, languageContext, receiver);
        }

        @Override
        public boolean hasIteratorNextElement(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.hasIteratorNextElement, languageContext, receiver);
        }

        @Override
        public Value getIteratorNextElement(Object languageContext, Object receiver) {
            return (Value) RUNTIME.callProfiled(this.getIteratorNextElement, languageContext, receiver);
        }

        @Override
        public boolean hasHashEntries(Object languageContext, Object receiver) {
            return (boolean) RUNTIME.callProfiled(this.hasHashEntries, languageContext, receiver);
        }

        @Override
        public long getHashSize(Object languageContext, Object receiver) {
            return (long) RUNTIME.callProfiled(this.getHashSize, languageContext, receiver);
        }

        @Override
        public boolean hasHashEntry(Object languageContext, Object receiver, Object key) {
            return (boolean) RUNTIME.callProfiled(this.hasHashEntry, languageContext, receiver, key);
        }

        @Override
        public Value getHashValue(Object languageContext, Object receiver, Object key) {
            return (Value) RUNTIME.callProfiled(this.getHashValue, languageContext, receiver, key);
        }

        @Override
        public Value getHashValueOrDefault(Object languageContext, Object receiver, Object key, Object defaultValue) {
            return (Value) RUNTIME.callProfiled(this.getHashValueOrDefault, languageContext, receiver, key, defaultValue);
        }

        @Override
        public void putHashEntry(Object languageContext, Object receiver, Object key, Object value) {
            RUNTIME.callProfiled(this.putHashEntry, languageContext, receiver, key, value);
        }

        @Override
        public boolean removeHashEntry(Object languageContext, Object receiver, Object key) {
            return (boolean) RUNTIME.callProfiled(this.removeHashEntry, languageContext, receiver, key);
        }

        @Override
        public Value getHashEntriesIterator(Object languageContext, Object receiver) {
            return (Value) RUNTIME.callProfiled(this.getHashEntriesIterator, languageContext, receiver);
        }

        @Override
        public Value getHashKeysIterator(Object languageContext, Object receiver) {
            return (Value) RUNTIME.callProfiled(this.getHashKeysIterator, languageContext, receiver);
        }

        @Override
        public Value getHashValuesIterator(Object languageContext, Object receiver) {
            return (Value) RUNTIME.callProfiled(this.getHashValuesIterator, languageContext, receiver);
        }

        private final class MemberSet extends AbstractSet<String> {

            private final Object context;
            private final Object receiver;
            private final Value keys;
            private int cachedSize = -1;

            MemberSet(Object languageContext, Object receiver, Value keys) {
                this.context = languageContext;
                this.receiver = receiver;
                this.keys = keys;
            }

            @Override
            public boolean contains(Object o) {
                if (!(o instanceof String)) {
                    return false;
                }
                return hasMember(this.context, receiver, (String) o);
            }

            @Override
            public Iterator<String> iterator() {
                return new Iterator<String>() {

                    int index = 0;

                    public boolean hasNext() {
                        return index < size();
                    }

                    public String next() {
                        if (index >= size()) {
                            throw new NoSuchElementException();
                        }
                        Value arrayElement = keys.getArrayElement(index++);
                        if (arrayElement.isString()) {
                            return arrayElement.asString();
                        } else {
                            return null;
                        }
                    }
                };
            }

            @Override
            public int size() {
                int size = this.cachedSize;
                if (size != -1) {
                    return size;
                }
                cachedSize = size = (int) keys.getArraySize();
                return size;
            }

        }

        abstract static class IsDateNode extends InteropNode {

            protected IsDateNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "isDate";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary objects) {
                return objects.isDate(receiver);
            }
        }

        abstract static class AsDateNode extends InteropNode {

            protected AsDateNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "asDate";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary objects,
                            @Cached BranchProfile unsupported) {
                try {
                    return objects.asDate(receiver);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    if (objects.isNull(receiver)) {
                        return null;
                    } else {
                        throw cannotConvert(context, receiver, null, "asDate()", "isDate()", "Value does not contain date information.");
                    }
                }
            }
        }

        abstract static class IsTimeNode extends InteropNode {

            protected IsTimeNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "isTime";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary objects) {
                return objects.isTime(receiver);
            }
        }

        abstract static class AsTimeNode extends InteropNode {

            protected AsTimeNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "asTime";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary objects,
                            @Cached BranchProfile unsupported) {
                try {
                    return objects.asTime(receiver);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    if (objects.isNull(receiver)) {
                        return null;
                    } else {
                        throw cannotConvert(context, receiver, null, "asTime()", "isTime()", "Value does not contain time information.");
                    }
                }
            }
        }

        abstract static class IsTimeZoneNode extends InteropNode {

            protected IsTimeZoneNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "isTimeZone";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary objects) {
                return objects.isTimeZone(receiver);
            }
        }

        abstract static class AsTimeZoneNode extends InteropNode {

            protected AsTimeZoneNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "asTimeZone";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary objects,
                            @Cached BranchProfile unsupported) {
                try {
                    return objects.asTimeZone(receiver);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    if (objects.isNull(receiver)) {
                        return null;
                    } else {
                        throw cannotConvert(context, receiver, null, "asTimeZone()", "isTimeZone()", "Value does not contain time-zone information.");
                    }
                }
            }
        }

        abstract static class IsDurationNode extends InteropNode {

            protected IsDurationNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "isDuration";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary objects) {
                return objects.isDuration(receiver);
            }
        }

        abstract static class AsDurationNode extends InteropNode {

            protected AsDurationNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "asDuration";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary objects,
                            @Cached BranchProfile unsupported) {
                try {
                    return objects.asDuration(receiver);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    if (objects.isNull(receiver)) {
                        return null;
                    } else {
                        throw cannotConvert(context, receiver, null, "asDuration()", "isDuration()", "Value does not contain duration information.");
                    }
                }
            }
        }

        abstract static class AsInstantNode extends InteropNode {

            protected AsInstantNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "getInstant";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary objects,
                            @Cached BranchProfile unsupported) {
                try {
                    return objects.asInstant(receiver);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    if (objects.isNull(receiver)) {
                        return null;
                    } else {
                        throw cannotConvert(context, receiver, null, "asInstant()", "hasInstant()", "Value does not contain instant information.");
                    }
                }
            }
        }

        private static class AsClassLiteralNode extends InteropNode {

            @Child PolyglotToHostNode toHost = PolyglotToHostNodeGen.create();

            protected AsClassLiteralNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Class.class};
            }

            @Override
            protected String getOperationName() {
                return "as";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                return toHost.execute(context, receiver, (Class<?>) args[ARGUMENT_OFFSET], null);
            }

        }

        private static class AsTypeLiteralNode extends InteropNode {

            @Child PolyglotToHostNode toHost = PolyglotToHostNodeGen.create();

            protected AsTypeLiteralNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, TypeLiteral.class};
            }

            @Override
            protected String getOperationName() {
                return "as";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                TypeLiteral<?> typeLiteral = (TypeLiteral<?>) args[ARGUMENT_OFFSET];
                return toHost.execute(context, receiver, typeLiteral.getRawType(), typeLiteral.getType());
            }

        }

        abstract static class IsNativePointerNode extends InteropNode {

            protected IsNativePointerNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "isNativePointer";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary natives) {
                return natives.isPointer(receiver);
            }

        }

        abstract static class AsNativePointerNode extends InteropNode {

            protected AsNativePointerNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "asNativePointer";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary natives,
                            @Cached BranchProfile unsupported) {
                try {
                    return natives.asPointer(receiver);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw cannotConvert(context, receiver, long.class, "asNativePointer()", "isNativeObject()", "Value cannot be converted to a native pointer.");
                }
            }

        }

        abstract static class HasArrayElementsNode extends InteropNode {

            protected HasArrayElementsNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "hasArrayElements";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary arrays) {
                return arrays.hasArrayElements(receiver);
            }

        }

        abstract static class GetMemberKeysNode extends InteropNode {

            protected GetMemberKeysNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "getMemberKeys";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary objects,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported) {
                try {
                    return toHost.execute(context, objects.getMembers(receiver));
                } catch (UnsupportedMessageException e) {
                    return null;
                }
            }
        }

        abstract static class GetArrayElementNode extends InteropNode {

            protected GetArrayElementNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Long.class};
            }

            @Override
            protected String getOperationName() {
                return "getArrayElement";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary arrays,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile unknown) {
                long index = (long) args[ARGUMENT_OFFSET];
                try {
                    return toHost.execute(context, arrays.readArrayElement(receiver, index));
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    return getArrayElementUnsupported(context, receiver);
                } catch (InvalidArrayIndexException e) {
                    unknown.enter();
                    throw invalidArrayIndex(context, receiver, index);
                }
            }
        }

        abstract static class SetArrayElementNode extends InteropNode {
            protected SetArrayElementNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Long.class, null};
            }

            @Override
            protected String getOperationName() {
                return "setArrayElement";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary arrays,
                            @Cached ToGuestValueNode toGuestValue,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile invalidIndex,
                            @Cached BranchProfile invalidValue) {
                long index = (long) args[ARGUMENT_OFFSET];
                Object value = toGuestValue.execute(context, args[ARGUMENT_OFFSET + 1]);
                try {
                    arrays.writeArrayElement(receiver, index, value);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    setArrayElementUnsupported(context, receiver);
                } catch (UnsupportedTypeException e) {
                    invalidValue.enter();
                    throw invalidArrayValue(context, receiver, index, value);
                } catch (InvalidArrayIndexException e) {
                    invalidIndex.enter();
                    throw invalidArrayIndex(context, receiver, index);
                }
                return null;
            }
        }

        abstract static class RemoveArrayElementNode extends InteropNode {

            protected RemoveArrayElementNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Long.class};
            }

            @Override
            protected String getOperationName() {
                return "removeArrayElement";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary arrays,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile invalidIndex) {
                long index = (long) args[ARGUMENT_OFFSET];
                Object value;
                try {
                    arrays.removeArrayElement(receiver, index);
                    value = Boolean.TRUE;
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw removeArrayElementUnsupported(context, receiver);
                } catch (InvalidArrayIndexException e) {
                    invalidIndex.enter();
                    throw invalidArrayIndex(context, receiver, index);
                }
                return value;
            }

        }

        abstract static class GetArraySizeNode extends InteropNode {

            protected GetArraySizeNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "getArraySize";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary arrays,
                            @Cached BranchProfile unsupported) {
                try {
                    return arrays.getArraySize(receiver);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    return getArraySizeUnsupported(context, receiver);
                }
            }

        }

        // region Buffer nodes

        abstract static class HasBufferElementsNode extends InteropNode {

            protected HasBufferElementsNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "hasBufferElements";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary buffers) {
                return buffers.hasBufferElements(receiver);
            }

        }

        abstract static class IsBufferWritableNode extends InteropNode {

            protected IsBufferWritableNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "isBufferWritable";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary buffers,
                            @Cached BranchProfile unsupported) {
                try {
                    return buffers.isBufferWritable(receiver);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw getBufferSizeUnsupported(context, receiver);
                }
            }

        }

        abstract static class GetBufferSizeNode extends InteropNode {

            protected GetBufferSizeNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "getBufferSize";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary buffers,
                            @Cached BranchProfile unsupported) {
                try {
                    return buffers.getBufferSize(receiver);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw getBufferSizeUnsupported(context, receiver);
                }
            }

        }

        abstract static class ReadBufferByteNode extends InteropNode {

            protected ReadBufferByteNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Long.class};
            }

            @Override
            protected String getOperationName() {
                return "readBufferByte";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary buffers,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile unknown) {
                final long byteOffset = (long) args[ARGUMENT_OFFSET];
                try {
                    return buffers.readBufferByte(receiver, byteOffset);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw readBufferByteUnsupported(context, receiver);
                } catch (InvalidBufferOffsetException e) {
                    unknown.enter();
                    throw invalidBufferIndex(context, receiver, e.getByteOffset(), e.getLength());
                }
            }

        }

        abstract static class WriteBufferByteNode extends InteropNode {
            protected WriteBufferByteNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Long.class, Byte.class};
            }

            @Override
            protected String getOperationName() {
                return "writeBufferByte";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary buffers,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile invalidIndex,
                            @Cached BranchProfile invalidValue) {
                final long byteOffset = (long) args[ARGUMENT_OFFSET];
                final byte value = (byte) args[ARGUMENT_OFFSET + 1];
                try {
                    buffers.writeBufferByte(receiver, byteOffset, value);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    if (buffers.hasBufferElements(receiver)) {
                        throw unsupported(context, receiver, "writeBufferByte()", "isBufferWritable()");
                    }
                    throw writeBufferByteUnsupported(context, receiver);
                } catch (InvalidBufferOffsetException e) {
                    invalidIndex.enter();
                    throw invalidBufferIndex(context, receiver, e.getByteOffset(), e.getLength());
                }
                return null;
            }

        }

        abstract static class ReadBufferShortNode extends InteropNode {

            protected ReadBufferShortNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, ByteOrder.class, Long.class};
            }

            @Override
            protected String getOperationName() {
                return "readBufferShort";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary buffers,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile unknown) {
                final ByteOrder order = (ByteOrder) args[ARGUMENT_OFFSET];
                final long byteOffset = (long) args[ARGUMENT_OFFSET + 1];
                try {
                    return buffers.readBufferShort(receiver, order, byteOffset);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw readBufferShortUnsupported(context, receiver);
                } catch (InvalidBufferOffsetException e) {
                    unknown.enter();
                    throw invalidBufferIndex(context, receiver, e.getByteOffset(), e.getLength());
                }
            }

        }

        abstract static class WriteBufferShortNode extends InteropNode {
            protected WriteBufferShortNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, ByteOrder.class, Long.class, Short.class};
            }

            @Override
            protected String getOperationName() {
                return "writeBufferShort";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary buffers,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile invalidIndex,
                            @Cached BranchProfile invalidValue) {
                final ByteOrder order = (ByteOrder) args[ARGUMENT_OFFSET];
                final long byteOffset = (long) args[ARGUMENT_OFFSET + 1];
                final short value = (short) args[ARGUMENT_OFFSET + 2];
                try {
                    buffers.writeBufferShort(receiver, order, byteOffset, value);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    if (buffers.hasBufferElements(receiver)) {
                        throw unsupported(context, receiver, "writeBufferShort()", "isBufferWritable()");
                    }
                    throw writeBufferShortUnsupported(context, receiver);
                } catch (InvalidBufferOffsetException e) {
                    invalidIndex.enter();
                    throw invalidBufferIndex(context, receiver, e.getByteOffset(), e.getLength());
                }
                return null;
            }

        }

        abstract static class ReadBufferIntNode extends InteropNode {

            protected ReadBufferIntNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, ByteOrder.class, Long.class};
            }

            @Override
            protected String getOperationName() {
                return "readBufferInt";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary buffers,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile unknown) {
                final ByteOrder order = (ByteOrder) args[ARGUMENT_OFFSET];
                final long byteOffset = (long) args[ARGUMENT_OFFSET + 1];
                try {
                    return buffers.readBufferInt(receiver, order, byteOffset);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw readBufferIntUnsupported(context, receiver);
                } catch (InvalidBufferOffsetException e) {
                    unknown.enter();
                    throw invalidBufferIndex(context, receiver, e.getByteOffset(), e.getLength());
                }
            }

        }

        abstract static class WriteBufferIntNode extends InteropNode {
            protected WriteBufferIntNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, ByteOrder.class, Long.class, Integer.class};
            }

            @Override
            protected String getOperationName() {
                return "writeBufferInt";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary buffers,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile invalidIndex,
                            @Cached BranchProfile invalidValue) {
                final ByteOrder order = (ByteOrder) args[ARGUMENT_OFFSET];
                final long byteOffset = (long) args[ARGUMENT_OFFSET + 1];
                final int value = (int) args[ARGUMENT_OFFSET + 2];
                try {
                    buffers.writeBufferInt(receiver, order, byteOffset, value);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    if (buffers.hasBufferElements(receiver)) {
                        throw unsupported(context, receiver, "writeBufferInt()", "isBufferWritable()");
                    }
                    throw writeBufferIntUnsupported(context, receiver);
                } catch (InvalidBufferOffsetException e) {
                    invalidIndex.enter();
                    throw invalidBufferIndex(context, receiver, e.getByteOffset(), e.getLength());
                }
                return null;
            }

        }

        abstract static class ReadBufferLongNode extends InteropNode {

            protected ReadBufferLongNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, ByteOrder.class, Long.class};
            }

            @Override
            protected String getOperationName() {
                return "readBufferLong";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary buffers,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile unknown) {
                final ByteOrder order = (ByteOrder) args[ARGUMENT_OFFSET];
                final long byteOffset = (long) args[ARGUMENT_OFFSET + 1];
                try {
                    return buffers.readBufferLong(receiver, order, byteOffset);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw readBufferLongUnsupported(context, receiver);
                } catch (InvalidBufferOffsetException e) {
                    unknown.enter();
                    throw invalidBufferIndex(context, receiver, e.getByteOffset(), e.getLength());
                }
            }

        }

        abstract static class WriteBufferLongNode extends InteropNode {
            protected WriteBufferLongNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, ByteOrder.class, Long.class, Long.class};
            }

            @Override
            protected String getOperationName() {
                return "writeBufferLong";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary buffers,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile invalidIndex,
                            @Cached BranchProfile invalidValue) {
                final ByteOrder order = (ByteOrder) args[ARGUMENT_OFFSET];
                final long byteOffset = (long) args[ARGUMENT_OFFSET + 1];
                final long value = (long) args[ARGUMENT_OFFSET + 2];
                try {
                    buffers.writeBufferLong(receiver, order, byteOffset, value);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    if (buffers.hasBufferElements(receiver)) {
                        throw unsupported(context, receiver, "writeBufferLong()", "isBufferWritable()");
                    }
                    throw writeBufferLongUnsupported(context, receiver);
                } catch (InvalidBufferOffsetException e) {
                    invalidIndex.enter();
                    throw invalidBufferIndex(context, receiver, e.getByteOffset(), e.getLength());
                }
                return null;
            }

        }

        abstract static class ReadBufferFloatNode extends InteropNode {

            protected ReadBufferFloatNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, ByteOrder.class, Long.class};
            }

            @Override
            protected String getOperationName() {
                return "readBufferFloat";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary buffers,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile unknown) {
                final ByteOrder order = (ByteOrder) args[ARGUMENT_OFFSET];
                final long byteOffset = (long) args[ARGUMENT_OFFSET + 1];
                try {
                    return buffers.readBufferFloat(receiver, order, byteOffset);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw readBufferFloatUnsupported(context, receiver);
                } catch (InvalidBufferOffsetException e) {
                    unknown.enter();
                    throw invalidBufferIndex(context, receiver, e.getByteOffset(), e.getLength());
                }
            }

        }

        abstract static class WriteBufferFloatNode extends InteropNode {
            protected WriteBufferFloatNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, ByteOrder.class, Long.class, Float.class};
            }

            @Override
            protected String getOperationName() {
                return "writeBufferFloat";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary buffers,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile invalidIndex,
                            @Cached BranchProfile invalidValue) {
                final ByteOrder order = (ByteOrder) args[ARGUMENT_OFFSET];
                final long byteOffset = (long) args[ARGUMENT_OFFSET + 1];
                final float value = (float) args[ARGUMENT_OFFSET + 2];
                try {
                    buffers.writeBufferFloat(receiver, order, byteOffset, value);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    if (buffers.hasBufferElements(receiver)) {
                        throw unsupported(context, receiver, "writeBufferFloat()", "isBufferWritable()");
                    }
                    throw writeBufferFloatUnsupported(context, receiver);
                } catch (InvalidBufferOffsetException e) {
                    invalidIndex.enter();
                    throw invalidBufferIndex(context, receiver, e.getByteOffset(), e.getLength());
                }
                return null;
            }

        }

        abstract static class ReadBufferDoubleNode extends InteropNode {

            protected ReadBufferDoubleNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, ByteOrder.class, Long.class};
            }

            @Override
            protected String getOperationName() {
                return "readBufferDouble";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary buffers,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile unknown) {
                final ByteOrder order = (ByteOrder) args[ARGUMENT_OFFSET];
                final long byteOffset = (long) args[ARGUMENT_OFFSET + 1];
                try {
                    return buffers.readBufferDouble(receiver, order, byteOffset);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw readBufferDoubleUnsupported(context, receiver);
                } catch (InvalidBufferOffsetException e) {
                    unknown.enter();
                    throw invalidBufferIndex(context, receiver, e.getByteOffset(), e.getLength());
                }
            }

        }

        abstract static class WriteBufferDoubleNode extends InteropNode {
            protected WriteBufferDoubleNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, ByteOrder.class, Long.class, Double.class};
            }

            @Override
            protected String getOperationName() {
                return "writeBufferDouble";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary buffers,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile invalidIndex,
                            @Cached BranchProfile invalidValue) {
                final ByteOrder order = (ByteOrder) args[ARGUMENT_OFFSET];
                final long byteOffset = (long) args[ARGUMENT_OFFSET + 1];
                final double value = (double) args[ARGUMENT_OFFSET + 2];
                try {
                    buffers.writeBufferDouble(receiver, order, byteOffset, value);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    if (buffers.hasBufferElements(receiver)) {
                        throw unsupported(context, receiver, "writeBufferDouble()", "isBufferWritable()");
                    }
                    throw writeBufferDoubleUnsupported(context, receiver);
                } catch (InvalidBufferOffsetException e) {
                    invalidIndex.enter();
                    throw invalidBufferIndex(context, receiver, e.getByteOffset(), e.getLength());
                }
                return null;
            }

        }

        // endregion

        abstract static class GetMemberNode extends InteropNode {

            protected GetMemberNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, String.class};
            }

            @Override
            protected String getOperationName() {
                return "getMember";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary objects,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile unknown) {
                String key = (String) args[ARGUMENT_OFFSET];
                Object value;
                try {
                    assert key != null : "should be handled already";
                    value = toHost.execute(context, objects.readMember(receiver, key));
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    if (objects.hasMembers(receiver)) {
                        value = null;
                    } else {
                        return getMemberUnsupported(context, receiver, key);
                    }
                } catch (UnknownIdentifierException e) {
                    unknown.enter();
                    value = null;
                }
                return value;
            }

        }

        abstract static class PutMemberNode extends InteropNode {

            protected PutMemberNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "putMember";
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, String.class, null};
            }

            @Specialization
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary(limit = "CACHE_LIMIT") InteropLibrary objects,
                            @Cached ToGuestValueNode toGuestValue,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile invalidValue,
                            @Cached BranchProfile unknown) {
                String key = (String) args[ARGUMENT_OFFSET];
                Object originalValue = args[ARGUMENT_OFFSET + 1];
                Object value = toGuestValue.execute(context, originalValue);
                assert key != null;
                try {
                    objects.writeMember(receiver, key, value);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw putMemberUnsupported(context, receiver);
                } catch (UnknownIdentifierException e) {
                    unknown.enter();
                    throw nonWritableMemberKey(context, receiver, key);
                } catch (UnsupportedTypeException e) {
                    invalidValue.enter();
                    throw invalidMemberValue(context, receiver, key, value);
                }
                return null;
            }
        }

        abstract static class RemoveMemberNode extends InteropNode {

            protected RemoveMemberNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "removeMember";
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, String.class};
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary objects,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile unknown) {
                String key = (String) args[ARGUMENT_OFFSET];
                Object value;
                try {
                    assert key != null : "should be handled already";
                    objects.removeMember(receiver, key);
                    value = Boolean.TRUE;
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    if (!objects.hasMembers(receiver)) {
                        throw removeMemberUnsupported(context, receiver);
                    } else if (objects.isMemberExisting(receiver, key)) {
                        throw nonRemovableMemberKey(context, receiver, key);
                    } else {
                        value = Boolean.FALSE;
                    }
                } catch (UnknownIdentifierException e) {
                    unknown.enter();
                    if (objects.isMemberExisting(receiver, key)) {
                        throw nonRemovableMemberKey(context, receiver, key);
                    } else {
                        value = Boolean.FALSE;
                    }
                }
                return value;
            }

        }

        abstract static class IsNullNode extends InteropNode {

            protected IsNullNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "isNull";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary values) {
                return values.isNull(receiver);
            }

        }

        abstract static class HasMembersNode extends InteropNode {

            protected HasMembersNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "hasMembers";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary objects) {
                return objects.hasMembers(receiver);
            }

        }

        private abstract static class AbstractMemberInfoNode extends InteropNode {

            protected AbstractMemberInfoNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected final Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, String.class};
            }

        }

        abstract static class HasMemberNode extends AbstractMemberInfoNode {

            protected HasMemberNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "hasMember";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary objects) {
                String key = (String) args[ARGUMENT_OFFSET];
                return objects.isMemberExisting(receiver, key);
            }
        }

        abstract static class CanInvokeNode extends AbstractMemberInfoNode {

            protected CanInvokeNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "canInvoke";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary objects) {
                String key = (String) args[ARGUMENT_OFFSET];
                return objects.isMemberInvocable(receiver, key);
            }

        }

        abstract static class CanExecuteNode extends InteropNode {

            protected CanExecuteNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected String getOperationName() {
                return "canExecute";
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary executables) {
                return executables.isExecutable(receiver);
            }

        }

        abstract static class CanInstantiateNode extends InteropNode {

            protected CanInstantiateNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "canInstantiate";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary instantiables) {
                return instantiables.isInstantiable(receiver);
            }

        }

        private abstract static class AbstractExecuteNode extends InteropNode {

            @Child private InteropLibrary executables = InteropLibrary.getFactory().createDispatched(CACHE_LIMIT);
            @Child private ToGuestValuesNode toGuestValues = ToGuestValuesNode.create();
            private final BranchProfile invalidArgument = BranchProfile.create();
            private final BranchProfile arity = BranchProfile.create();
            private final BranchProfile unsupported = BranchProfile.create();

            protected AbstractExecuteNode(InteropValue interop) {
                super(interop);
            }

            protected final Object executeShared(PolyglotLanguageContext context, Object receiver, Object[] args) {
                Object[] guestArguments = toGuestValues.apply(context, args);
                try {
                    return executables.execute(receiver, guestArguments);
                } catch (UnsupportedTypeException e) {
                    invalidArgument.enter();
                    throw invalidExecuteArgumentType(context, receiver, e);
                } catch (ArityException e) {
                    arity.enter();
                    throw invalidExecuteArity(context, receiver, guestArguments, e.getExpectedMinArity(), e.getExpectedMaxArity(), e.getActualArity());
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw executeUnsupported(context, receiver);
                }
            }

        }

        private static class ExecuteVoidNode extends AbstractExecuteNode {

            protected ExecuteVoidNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Object[].class};
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                executeShared(context, receiver, (Object[]) args[ARGUMENT_OFFSET]);
                return null;
            }

            @Override
            protected String getOperationName() {
                return "executeVoid";
            }

        }

        private static class ExecuteVoidNoArgsNode extends AbstractExecuteNode {

            private static final Object[] NO_ARGS = new Object[0];

            protected ExecuteVoidNoArgsNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                executeShared(context, receiver, NO_ARGS);
                return null;
            }

            @Override
            protected String getOperationName() {
                return "executeVoid";
            }

        }

        private static class ExecuteNode extends AbstractExecuteNode {

            private final ToHostValueNode toHostValue;

            protected ExecuteNode(InteropValue interop) {
                super(interop);
                this.toHostValue = ToHostValueNode.create(interop.impl);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Object[].class};
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                return toHostValue.execute(context, executeShared(context, receiver, (Object[]) args[ARGUMENT_OFFSET]));
            }

            @Override
            protected String getOperationName() {
                return "execute";
            }

        }

        private static class ExecuteNoArgsNode extends AbstractExecuteNode {

            private final ToHostValueNode toHostValue;

            protected ExecuteNoArgsNode(InteropValue interop) {
                super(interop);
                this.toHostValue = ToHostValueNode.create(interop.impl);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                return toHostValue.execute(context, executeShared(context, receiver, ExecuteVoidNoArgsNode.NO_ARGS));
            }

            @Override
            protected String getOperationName() {
                return "execute";
            }

        }

        abstract static class NewInstanceNode extends InteropNode {

            @Child private ToGuestValuesNode toGuestValues = ToGuestValuesNode.create();
            private final ToHostValueNode toHostValue;

            protected NewInstanceNode(InteropValue interop) {
                super(interop);
                this.toHostValue = ToHostValueNode.create(interop.impl);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Object[].class};
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary instantiables,
                            @Cached ToGuestValuesNode toGuestValues,
                            @Cached("createToHost()") ToHostValueNode toHostValue,
                            @Cached BranchProfile arity,
                            @Cached BranchProfile invalidArgument,
                            @Cached BranchProfile unsupported) {
                Object[] instantiateArguments = toGuestValues.apply(context, (Object[]) args[ARGUMENT_OFFSET]);
                try {
                    return toHostValue.execute(context, instantiables.instantiate(receiver, instantiateArguments));
                } catch (UnsupportedTypeException e) {
                    invalidArgument.enter();
                    throw invalidInstantiateArgumentType(context, receiver, instantiateArguments);
                } catch (ArityException e) {
                    arity.enter();
                    throw invalidInstantiateArity(context, receiver, instantiateArguments, e.getExpectedMinArity(), e.getExpectedMaxArity(), e.getActualArity());
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    return newInstanceUnsupported(context, receiver);
                }
            }

            @Override
            protected String getOperationName() {
                return "newInstance";
            }

        }

        private abstract static class AbstractInvokeNode extends InteropNode {

            @Child private InteropLibrary objects = InteropLibrary.getFactory().createDispatched(CACHE_LIMIT);
            private final ToHostValueNode toHostValue;
            private final BranchProfile invalidArgument = BranchProfile.create();
            private final BranchProfile arity = BranchProfile.create();
            private final BranchProfile unsupported = BranchProfile.create();
            private final BranchProfile unknownIdentifier = BranchProfile.create();

            protected AbstractInvokeNode(InteropValue interop) {
                super(interop);
                this.toHostValue = ToHostValueNode.create(interop.impl);
            }

            protected final Object executeShared(PolyglotLanguageContext context, Object receiver, String key, Object[] guestArguments) {
                try {
                    return toHostValue.execute(context, objects.invokeMember(receiver, key, guestArguments));
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw invokeUnsupported(context, receiver, key);
                } catch (UnknownIdentifierException e) {
                    unknownIdentifier.enter();
                    throw nonReadableMemberKey(context, receiver, key);
                } catch (UnsupportedTypeException e) {
                    invalidArgument.enter();
                    throw invalidInvokeArgumentType(context, receiver, key, e);
                } catch (ArityException e) {
                    arity.enter();
                    throw invalidInvokeArity(context, receiver, key, guestArguments, e.getExpectedMinArity(), e.getExpectedMaxArity(), e.getActualArity());
                }
            }

        }

        private static class InvokeNode extends AbstractInvokeNode {

            @Child private ToGuestValuesNode toGuestValues = ToGuestValuesNode.create();

            protected InvokeNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, String.class, Object[].class};
            }

            @Override
            protected String getOperationName() {
                return "invoke";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                String key = (String) args[ARGUMENT_OFFSET];
                Object[] guestArguments = toGuestValues.apply(context, (Object[]) args[ARGUMENT_OFFSET + 1]);
                return executeShared(context, receiver, key, guestArguments);
            }

        }

        private static class InvokeNoArgsNode extends AbstractInvokeNode {

            protected InvokeNoArgsNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, String.class};
            }

            @Override
            protected String getOperationName() {
                return "invoke";
            }

            @Override
            protected Object executeImpl(PolyglotLanguageContext context, Object receiver, Object[] args) {
                String key = (String) args[ARGUMENT_OFFSET];
                return executeShared(context, receiver, key, ExecuteVoidNoArgsNode.NO_ARGS);
            }

        }

        abstract static class IsExceptionNode extends InteropNode {

            protected IsExceptionNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "isException";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary objects) {
                return objects.isException(receiver);
            }
        }

        abstract static class ThrowExceptionNode extends InteropNode {

            protected ThrowExceptionNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "throwException";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary objects,
                            @Cached BranchProfile unsupported) {
                try {
                    throw objects.throwException(receiver);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw unsupported(context, receiver, "throwException()", "isException()");
                }
            }
        }

        abstract static class IsMetaObjectNode extends InteropNode {

            protected IsMetaObjectNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "isMetaObject";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static boolean doCached(PolyglotLanguageContext context, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary objects) {
                return objects.isMetaObject(receiver);
            }
        }

        abstract static class GetMetaQualifiedNameNode extends InteropNode {

            protected GetMetaQualifiedNameNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "getMetaQualifiedName";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static String doCached(PolyglotLanguageContext context, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary objects,
                            @CachedLibrary(limit = "1") InteropLibrary toString,
                            @Cached BranchProfile unsupported) {
                try {
                    return toString.asString(objects.getMetaQualifiedName(receiver));
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw unsupported(context, receiver, "getMetaQualifiedName()", "isMetaObject()");
                }
            }
        }

        abstract static class GetMetaSimpleNameNode extends InteropNode {

            protected GetMetaSimpleNameNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "getMetaSimpleName";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static String doCached(PolyglotLanguageContext context, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary objects,
                            @CachedLibrary(limit = "1") InteropLibrary toString,
                            @Cached BranchProfile unsupported) {
                try {
                    return toString.asString(objects.getMetaSimpleName(receiver));
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw unsupported(context, receiver, "getMetaSimpleName()", "isMetaObject()");
                }
            }
        }

        abstract static class IsMetaInstanceNode extends InteropNode {

            protected IsMetaInstanceNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, null};
            }

            @Override
            protected String getOperationName() {
                return "isMetaInstance";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static boolean doCached(PolyglotLanguageContext context, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary objects,
                            @Cached ToGuestValueNode toGuest,
                            @Cached BranchProfile unsupported) {
                try {
                    return objects.isMetaInstance(receiver, toGuest.execute(context, args[ARGUMENT_OFFSET]));
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw unsupported(context, receiver, "isMetaInstance()", "isMetaObject()");
                }
            }
        }

        abstract static class HasMetaParentsNode extends InteropNode {

            protected HasMetaParentsNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "hasMetaParents";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static boolean doCached(PolyglotLanguageContext context, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary objects,
                            @Cached BranchProfile unsupported) {
                return objects.hasMetaParents(receiver);
            }
        }

        abstract static class GetMetaParentsNode extends InteropNode {

            protected GetMetaParentsNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "getMetaParents";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args,
                            @CachedLibrary("receiver") InteropLibrary objects,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported) {
                try {
                    return toHost.execute(context, objects.getMetaParents(receiver));
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw unsupported(context, receiver, "getMetaParents()", "hasMetaParents()");
                }
            }
        }

        abstract static class HasIteratorNode extends InteropNode {

            protected HasIteratorNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "hasIterator";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary iterators) {
                return iterators.hasIterator(receiver);
            }
        }

        abstract static class GetIteratorNode extends InteropNode {

            protected GetIteratorNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "getIterator";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary iterators,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported) {
                try {
                    return toHost.execute(context, iterators.getIterator(receiver));
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    return getIteratorUnsupported(context, receiver);
                }
            }
        }

        abstract static class IsIteratorNode extends InteropNode {

            protected IsIteratorNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "isIterator";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary iterators) {
                return iterators.isIterator(receiver);
            }
        }

        abstract static class HasIteratorNextElementNode extends InteropNode {

            protected HasIteratorNextElementNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "hasIteratorNextElement";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary iterators,
                            @Cached BranchProfile unsupported) {
                try {
                    return iterators.hasIteratorNextElement(receiver);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    return hasIteratorNextElementUnsupported(context, receiver);
                }
            }
        }

        abstract static class GetIteratorNextElementNode extends InteropNode {

            protected GetIteratorNextElementNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "getIteratorNextElement";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary iterators,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile stop) {
                try {
                    return toHost.execute(context, iterators.getIteratorNextElement(receiver));
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw nonReadableIteratorElement();
                } catch (StopIterationException e) {
                    stop.enter();
                    throw stopIteration(context, receiver);
                }
            }
        }

        abstract static class HasHashEntriesNode extends InteropNode {

            protected HasHashEntriesNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "hasHashEntries";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary hashes) {
                return hashes.hasHashEntries(receiver);
            }
        }

        abstract static class GetHashSizeNode extends InteropNode {

            protected GetHashSizeNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "getHashSize";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary hashes,
                            @Cached BranchProfile unsupported) {
                try {
                    return hashes.getHashSize(receiver);
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw getHashSizeUnsupported(context, receiver);
                }
            }
        }

        abstract static class HasHashEntryNode extends InteropNode {

            protected HasHashEntryNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Object.class};
            }

            @Override
            protected String getOperationName() {
                return "hasHashEntry";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary hashes,
                            @Cached ToGuestValueNode toGuestKey) {
                Object hostKey = args[ARGUMENT_OFFSET];
                Object key = toGuestKey.execute(context, hostKey);
                return hashes.isHashEntryExisting(receiver, key);
            }
        }

        abstract static class GetHashValueNode extends InteropNode {

            protected GetHashValueNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Object.class};
            }

            @Override
            protected String getOperationName() {
                return "getHashValue";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary hashes,
                            @Cached ToGuestValueNode toGuestKey,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile invalidKey) {
                Object hostKey = args[ARGUMENT_OFFSET];
                Object key = toGuestKey.execute(context, hostKey);
                try {
                    return toHost.execute(context, hashes.readHashValue(receiver, key));
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw getHashValueUnsupported(context, receiver, key);
                } catch (UnknownKeyException e) {
                    invalidKey.enter();
                    if (hashes.isHashEntryExisting(receiver, key)) {
                        throw getHashValueUnsupported(context, receiver, key);
                    } else {
                        return null;
                    }
                }
            }
        }

        abstract static class GetHashValueOrDefaultNode extends InteropNode {

            protected GetHashValueOrDefaultNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Object.class, Object.class};
            }

            @Override
            protected String getOperationName() {
                return "getHashValueOrDefault";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary hashes,
                            @Cached ToGuestValueNode toGuestKey,
                            @Cached ToGuestValueNode toGuestDefaultValue,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile invalidKey) {
                Object hostKey = args[ARGUMENT_OFFSET];
                Object hostDefaultValue = args[ARGUMENT_OFFSET + 1];
                Object key = toGuestKey.execute(context, hostKey);
                Object defaultValue = toGuestDefaultValue.execute(context, hostDefaultValue);
                try {
                    return toHost.execute(context, hashes.readHashValueOrDefault(receiver, key, hostDefaultValue));
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw getHashValueUnsupported(context, receiver, key);
                }
            }
        }

        abstract static class PutHashEntryNode extends InteropNode {

            protected PutHashEntryNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Object.class, Object.class};
            }

            @Override
            protected String getOperationName() {
                return "putHashEntry";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary hashes,
                            @Cached ToGuestValueNode toGuestKey,
                            @Cached ToGuestValueNode toGuestValue,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile invalidKey,
                            @Cached BranchProfile invalidValue) {
                Object hostKey = args[ARGUMENT_OFFSET];
                Object hostValue = args[ARGUMENT_OFFSET + 1];
                Object key = toGuestKey.execute(context, hostKey);
                Object value = toGuestValue.execute(context, hostValue);
                try {
                    hashes.writeHashEntry(receiver, key, value);
                } catch (UnsupportedMessageException | UnknownKeyException e) {
                    unsupported.enter();
                    throw putHashEntryUnsupported(context, receiver, key, value);
                } catch (UnsupportedTypeException e) {
                    invalidValue.enter();
                    throw invalidHashValue(context, receiver, key, value);
                }
                return null;
            }
        }

        abstract static class RemoveHashEntryNode extends InteropNode {

            protected RemoveHashEntryNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Object.class};
            }

            @Override
            protected String getOperationName() {
                return "removeHashEntry";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary hashes,
                            @Cached ToGuestValueNode toGuestKey,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile invalidKey) {
                Object hostKey = args[ARGUMENT_OFFSET];
                Object key = toGuestKey.execute(context, hostKey);
                Boolean result;
                try {
                    hashes.removeHashEntry(receiver, key);
                    result = Boolean.TRUE;
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    if (!hashes.hasHashEntries(receiver) || hashes.isHashEntryExisting(receiver, key)) {
                        throw removeHashEntryUnsupported(context, receiver, key);
                    } else {
                        result = Boolean.FALSE;
                    }
                } catch (UnknownKeyException e) {
                    invalidKey.enter();
                    result = Boolean.FALSE;
                }
                return result;
            }
        }

        abstract static class GetHashEntriesIteratorNode extends InteropNode {

            GetHashEntriesIteratorNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "getHashEntriesIterator";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary hashes,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported) {
                try {
                    return toHost.execute(context, hashes.getHashEntriesIterator(receiver));
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw getHashEntriesIteratorUnsupported(context, receiver);
                }
            }
        }

        abstract static class GetHashKeysIteratorNode extends InteropNode {

            GetHashKeysIteratorNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "getHashKeysIterator";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary hashes,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported) {
                try {
                    return toHost.execute(context, hashes.getHashKeysIterator(receiver));
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw getHashEntriesIteratorUnsupported(context, receiver);
                }
            }
        }

        abstract static class GetHashValuesIteratorNode extends InteropNode {

            GetHashValuesIteratorNode(InteropValue interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "getHashValuesIterator";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary hashes,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported) {
                try {
                    return toHost.execute(context, hashes.getHashValuesIterator(receiver));
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw getHashEntriesIteratorUnsupported(context, receiver);
                }
            }
        }

    }

}
