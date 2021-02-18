/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownHashKeyException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValueNode;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValuesNode;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToHostValueNode;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.AsDateNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.AsDurationNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.AsInstantNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.AsNativePointerNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.AsTimeNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.AsTimeZoneNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.CanExecuteNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.CanInstantiateNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.CanInvokeNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.GetArrayElementNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.GetArraySizeNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.GetBufferSizeNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.GetHashEntriesIteratorNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.GetHashEntryKeyNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.GetHashEntryValueNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.GetHashValueNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.GetIteratorNextElementNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.GetIteratorNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.GetMemberKeysNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.GetMemberNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.GetMetaQualifiedNameNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.GetMetaSimpleNameNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.HasArrayElementsNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.HasBufferElementsNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.HasHashEntriesNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.HasHashEntryNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.HasMemberNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.HasMembersNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.IsBufferWritableNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.HasIteratorNextElementNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.HasIteratorNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.IsDateNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.IsDurationNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.IsExceptionNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.IsHashEntryNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.IsIteratorNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.IsMetaInstanceNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.IsMetaObjectNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.IsNativePointerNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.IsNullNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.IsTimeNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.IsTimeZoneNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.NewInstanceNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.PutHashEntryNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.PutMemberNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.ReadBufferFloatNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.ReadBufferIntNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.RemoveArrayElementNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.RemoveHashEntryNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.RemoveMemberNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.SetArrayElementNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.SetHashEntryValueNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.ThrowExceptionNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.WriteBufferByteNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.WriteBufferDoubleNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.WriteBufferFloatNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.WriteBufferIntNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.WriteBufferLongNodeGen;
import com.oracle.truffle.polyglot.PolyglotValueFactory.InteropCodeCacheFactory.WriteBufferShortNodeGen;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractValueImpl;

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
import java.util.Objects;
import java.util.Set;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;
import static com.oracle.truffle.polyglot.EngineAccessor.RUNTIME;

abstract class PolyglotValue extends AbstractValueImpl {

    private static final String TRUNCATION_SUFFIX = "...";

    private static final String UNKNOWN = "Unknown";

    protected final PolyglotLanguageContext languageContext;

    static final InteropLibrary UNCACHED_INTEROP = InteropLibrary.getFactory().getUncached();

    PolyglotValue(PolyglotLanguageContext languageContext) {
        super(languageContext.getEngine().impl);
        this.languageContext = languageContext;
    }

    PolyglotValue(PolyglotImpl polyglot, PolyglotLanguageContext languageContext) {
        super(polyglot);
        this.languageContext = languageContext;
    }

    @Override
    public final Context getContext() {
        if (languageContext == null) {
            return null;
        }
        return languageContext.context.currentApi;
    }

    @Override
    public Value getArrayElement(Object receiver, long index) {
        Object prev = hostEnter(languageContext);
        try {
            return getArrayElementUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static Value getArrayElementUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "getArrayElement(long)", "hasArrayElements()");
    }

    @Override
    public void setArrayElement(Object receiver, long index, Object value) {
        Object prev = hostEnter(languageContext);
        try {
            setArrayElementUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static void setArrayElementUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "setArrayElement(long, Object)", "hasArrayElements()");
    }

    @Override
    public boolean removeArrayElement(Object receiver, long index) {
        Object prev = hostEnter(languageContext);
        try {
            throw removeArrayElementUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException removeArrayElementUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "removeArrayElement(long, Object)", null);
    }

    @Override
    public long getArraySize(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            return getArraySizeUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static long getArraySizeUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "getArraySize()", "hasArrayElements()");
    }

    // region Buffer Methods

    @Override
    public boolean isBufferWritable(Object receiver) throws UnsupportedOperationException {
        final Object prev = hostEnter(languageContext);
        try {
            throw isBufferWritableUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException isBufferWritableUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "isBufferWritable()", "hasBufferElements()");
    }

    @Override
    public long getBufferSize(Object receiver) throws UnsupportedOperationException {
        final Object prev = hostEnter(languageContext);
        try {
            throw getBufferSizeUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException getBufferSizeUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "getBufferSize()", "hasBufferElements()");
    }

    @Override
    public byte readBufferByte(Object receiver, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
        final Object prev = hostEnter(languageContext);
        try {
            throw readBufferByteUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException readBufferByteUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "readBufferByte()", "hasBufferElements()");
    }

    @Override
    public void writeBufferByte(Object receiver, long byteOffset, byte value) throws UnsupportedOperationException, IndexOutOfBoundsException {
        final Object prev = hostEnter(languageContext);
        try {
            throw writeBufferByteUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException writeBufferByteUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "writeBufferByte()", "hasBufferElements()");
    }

    @Override
    public short readBufferShort(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
        final Object prev = hostEnter(languageContext);
        try {
            throw readBufferShortUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException readBufferShortUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "readBufferShort()", "hasBufferElements()");
    }

    @Override
    public void writeBufferShort(Object receiver, ByteOrder order, long byteOffset, short value) throws UnsupportedOperationException, IndexOutOfBoundsException {
        final Object prev = hostEnter(languageContext);
        try {
            throw writeBufferShortUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException writeBufferShortUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "writeBufferShort()", "hasBufferElements()");
    }

    @Override
    public int readBufferInt(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
        final Object prev = hostEnter(languageContext);
        try {
            throw readBufferIntUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException readBufferIntUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "readBufferInt()", "hasBufferElements()");
    }

    @Override
    public void writeBufferInt(Object receiver, ByteOrder order, long byteOffset, int value) throws UnsupportedOperationException, IndexOutOfBoundsException {
        final Object prev = hostEnter(languageContext);
        try {
            throw writeBufferIntUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException writeBufferIntUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "writeBufferInt()", "hasBufferElements()");
    }

    @Override
    public long readBufferLong(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
        final Object prev = hostEnter(languageContext);
        try {
            throw readBufferLongUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException readBufferLongUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "readBufferLong()", "hasBufferElements()");
    }

    @Override
    public void writeBufferLong(Object receiver, ByteOrder order, long byteOffset, long value) throws UnsupportedOperationException, IndexOutOfBoundsException {
        final Object prev = hostEnter(languageContext);
        try {
            throw writeBufferLongUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException writeBufferLongUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "writeBufferLong()", "hasBufferElements()");
    }

    @Override
    public float readBufferFloat(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
        final Object prev = hostEnter(languageContext);
        try {
            throw readBufferFloatUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException readBufferFloatUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "readBufferFloat()", "hasBufferElements()");
    }

    @Override
    public void writeBufferFloat(Object receiver, ByteOrder order, long byteOffset, float value) throws UnsupportedOperationException, IndexOutOfBoundsException {
        final Object prev = hostEnter(languageContext);
        try {
            throw writeBufferFloatUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException writeBufferFloatUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "writeBufferFloat()", "hasBufferElements()");
    }

    @Override
    public double readBufferDouble(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
        final Object prev = hostEnter(languageContext);
        try {
            throw readBufferDoubleUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException readBufferDoubleUnsupported(PolyglotLanguageContext context, Object receiver) {
        return unsupported(context, receiver, "readBufferDouble()", "hasBufferElements()");
    }

    @Override
    public void writeBufferDouble(Object receiver, ByteOrder order, long byteOffset, double value) throws UnsupportedOperationException, IndexOutOfBoundsException {
        final Object prev = hostEnter(languageContext);
        try {
            throw writeBufferDoubleUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
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
    public Value getMember(Object receiver, String key) {
        Object prev = hostEnter(languageContext);
        try {
            return getMemberUnsupported(languageContext, receiver, key);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static Value getMemberUnsupported(PolyglotLanguageContext context, Object receiver, @SuppressWarnings("unused") String key) {
        throw unsupported(context, receiver, "getMember(String)", "hasMembers()");
    }

    @Override
    public void putMember(Object receiver, String key, Object member) {
        Object prev = hostEnter(languageContext);
        try {
            putMemberUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException putMemberUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "putMember(String, Object)", "hasMembers()");
    }

    @Override
    public boolean removeMember(Object receiver, String key) {
        Object prev = hostEnter(languageContext);
        try {
            throw removeMemberUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException removeMemberUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "removeMember(String, Object)", null);
    }

    @Override
    public Value execute(Object receiver, Object[] arguments) {
        Object prev = hostEnter(languageContext);
        try {
            throw executeUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @Override
    public Value execute(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            throw executeUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException executeUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "execute(Object...)", "canExecute()");
    }

    @Override
    public Value newInstance(Object receiver, Object[] arguments) {
        Object prev = hostEnter(languageContext);
        try {
            return newInstanceUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static Value newInstanceUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "newInstance(Object...)", "canInstantiate()");
    }

    @Override
    public void executeVoid(Object receiver, Object[] arguments) {
        Object prev = hostEnter(languageContext);
        try {
            executeVoidUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @Override
    public void executeVoid(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            executeVoidUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static void executeVoidUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "executeVoid(Object...)", "canExecute()");
    }

    @Override
    public Value invoke(Object receiver, String identifier, Object[] arguments) {
        Object prev = hostEnter(languageContext);
        try {
            throw invokeUnsupported(languageContext, receiver, identifier);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @Override
    public Value invoke(Object receiver, String identifier) {
        Object prev = hostEnter(languageContext);
        try {
            throw invokeUnsupported(languageContext, receiver, identifier);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static RuntimeException invokeUnsupported(PolyglotLanguageContext context, Object receiver, String identifier) {
        throw unsupported(context, receiver, "invoke(" + identifier + ", Object...)", "canInvoke(String)");
    }

    @Override
    public String asString(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            return asStringUnsupported(receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    protected final String asStringUnsupported(Object receiver) {
        return invalidCastPrimitive(receiver, String.class, "asString()", "isString()", "Invalid coercion.");
    }

    @Override
    public boolean asBoolean(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            return asBooleanUnsupported(receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    private static boolean isNullUncached(Object receiver) {
        return InteropLibrary.getFactory().getUncached().isNull(receiver);
    }

    protected final boolean asBooleanUnsupported(Object receiver) {
        return invalidCastPrimitive(receiver, boolean.class, "asBoolean()", "isBoolean()", "Invalid or lossy primitive coercion.");
    }

    private <T> T invalidCastPrimitive(Object receiver, Class<T> clazz, String asMethodName, String isMethodName, String detail) {
        assert languageContext == null || !languageContext.context.engine.needsEnter(languageContext.context);
        if (isNullUncached(receiver)) {
            throw nullCoercion(languageContext, receiver, clazz, asMethodName, isMethodName);
        } else {
            throw cannotConvert(languageContext, receiver, clazz, asMethodName, isMethodName, detail);
        }
    }

    @Override
    public int asInt(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            return asIntUnsupported(receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    protected final int asIntUnsupported(Object receiver) {
        return invalidCastPrimitive(receiver, int.class, "asInt()", "fitsInInt()", "Invalid or lossy primitive coercion.");
    }

    @Override
    public long asLong(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            return asLongUnsupported(receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    protected final long asLongUnsupported(Object receiver) {
        return invalidCastPrimitive(receiver, long.class, "asLong()", "fitsInLong()", "Invalid or lossy primitive coercion.");
    }

    @Override
    public double asDouble(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            return asDoubleUnsupported(receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    protected final double asDoubleUnsupported(Object receiver) {
        return invalidCastPrimitive(receiver, double.class, "asDouble()", "fitsInDouble()", "Invalid or lossy primitive coercion.");
    }

    @Override
    public float asFloat(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            return asFloatUnsupported(receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    protected final float asFloatUnsupported(Object receiver) {
        return invalidCastPrimitive(receiver, float.class, "asFloat()", "fitsInFloat()", "Invalid or lossy primitive coercion.");
    }

    @Override
    public byte asByte(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            return asByteUnsupported(receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    protected final byte asByteUnsupported(Object receiver) {
        return invalidCastPrimitive(receiver, byte.class, "asByte()", "fitsInByte()", "Invalid or lossy primitive coercion.");
    }

    @Override
    public short asShort(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            return asShortUnsupported(receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    protected final short asShortUnsupported(Object receiver) {
        return invalidCastPrimitive(receiver, short.class, "asShort()", "fitsInShort()", "Invalid or lossy primitive coercion.");
    }

    @Override
    public long asNativePointer(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            return asNativePointerUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    static long asNativePointerUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw cannotConvert(context, receiver, long.class, "asNativePointer()", "isNativeObject()", "Value cannot be converted to a native pointer.");
    }

    @Override
    public Object asHostObject(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            return asHostObjectUnsupported(receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    protected final Object asHostObjectUnsupported(Object receiver) {
        throw cannotConvert(languageContext, receiver, null, "asHostObject()", "isHostObject()", "Value is not a host object.");
    }

    @Override
    public Object asProxyObject(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            return asProxyObjectUnsupported(receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    protected final Object asProxyObjectUnsupported(Object receiver) {
        throw cannotConvert(languageContext, receiver, null, "asProxyObject()", "isProxyObject()", "Value is not a proxy object.");
    }

    @Override
    public LocalDate asDate(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            if (isNullUncached(receiver)) {
                return null;
            } else {
                throw cannotConvert(languageContext, receiver, null, "asDate()", "isDate()", "Value does not contain date information.");
            }
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @Override
    public LocalTime asTime(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            if (isNullUncached(receiver)) {
                return null;
            } else {
                throw cannotConvert(languageContext, receiver, null, "asTime()", "isTime()", "Value does not contain time information.");
            }
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @Override
    public ZoneId asTimeZone(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            if (isNullUncached(receiver)) {
                return null;
            } else {
                throw cannotConvert(languageContext, receiver, null, "asTimeZone()", "isTimeZone()", "Value does not contain time zone information.");
            }
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @Override
    public Instant asInstant(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            if (isNullUncached(receiver)) {
                return null;
            } else {
                throw cannotConvert(languageContext, receiver, null, "asInstant()", "isInstant()", "Value does not contain instant information.");
            }
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @Override
    public Duration asDuration(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            if (isNullUncached(receiver)) {
                return null;
            } else {
                throw cannotConvert(languageContext, receiver, null, "asDuration()", "isDuration()", "Value does not contain duration information.");
            }
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @Override
    public RuntimeException throwException(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            throw unsupported(languageContext, receiver, "throwException()", "isException()");
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @Override
    public final Value getMetaObject(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            return getMetaObjectImpl(receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @Override
    public Value getIterator(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            return getIteratorUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static final Value getIteratorUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "getIterator()", "hasIterator()");
    }

    @Override
    public boolean hasIteratorNextElement(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            return hasIteratorNextElementUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static final boolean hasIteratorNextElementUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "hasIteratorNextElement()", "isIterator()");
    }

    @Override
    public Value getIteratorNextElement(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            return getIteratorNextElementUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static final Value getIteratorNextElementUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "getIteratorNextElement()", "isIterator()");
    }

    @Override
    public Value getHashValue(Object receiver, Object key) {
        Object prev = hostEnter(languageContext);
        try {
            throw getHashValueUnsupported(languageContext, receiver, key);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static final RuntimeException getHashValueUnsupported(PolyglotLanguageContext context, Object receiver, @SuppressWarnings("unused") Object key) {
        throw unsupported(context, receiver, "getHashValue(Object)", "hasHashEntries()");
    }

    @Override
    public void putHashEntry(Object receiver, Object key, Object value) {
        Object prev = hostEnter(languageContext);
        try {
            putHashEntryUnsupported(languageContext, receiver, key, value);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static final RuntimeException putHashEntryUnsupported(PolyglotLanguageContext context, Object receiver,
                    @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value) {
        throw unsupported(context, receiver, "putHashEntry(Object, Object)", "hasHashEntries()");
    }

    @Override
    public boolean removeHashEntry(Object receiver, Object key) {
        Object prev = hostEnter(languageContext);
        try {
            throw removeHashEntryUnsupported(languageContext, receiver, key);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static final RuntimeException removeHashEntryUnsupported(PolyglotLanguageContext context, Object receiver, @SuppressWarnings("unused") Object key) {
        throw unsupported(context, receiver, "removeHashEntry(Object)", "hasHashEntries()");
    }

    @Override
    public Value getHashEntriesIterator(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            throw getHashEntriesIteratorUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static final RuntimeException getHashEntriesIteratorUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "getHashEntriesIterator()", "hasHashEntries()");
    }

    @Override
    public Value getHashEntryKey(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            throw getHashEntryKeyUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static final RuntimeException getHashEntryKeyUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "getHashEntryKey()", "isHashEntry()");
    }

    @Override
    public Value getHashEntryValue(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            throw getHashEntryValueUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static final RuntimeException getHashEntryValueUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "getHashEntryValue()", "isHashEntry()");
    }

    @Override
    public void setHashEntryValue(Object receiver, Object value) {
        Object prev = hostEnter(languageContext);
        try {
            throw setHashEntryValueUnsupported(languageContext, receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @TruffleBoundary
    static final RuntimeException setHashEntryValueUnsupported(PolyglotLanguageContext context, Object receiver) {
        throw unsupported(context, receiver, "setHashEntryValue()", "isHashEntry()");
    }

    protected Value getMetaObjectImpl(Object receiver) {
        InteropLibrary lib = InteropLibrary.getFactory().getUncached(receiver);
        if (lib.hasMetaObject(receiver)) {
            try {
                return asValue(lib.getMetaObject(receiver));
            } catch (UnsupportedMessageException e) {
                throw shouldNotReachHere("Unexpected unsupported message.", e);
            }
        }
        return null;
    }

    private Value asValue(Object value) {
        if (languageContext == null) {
            return PolyglotImpl.getInstance().asValue(PolyglotContextImpl.currentNotEntered(), value);
        } else {
            return languageContext.asValue(value);
        }
    }

    static Object hostEnter(PolyglotLanguageContext languageContext) {
        if (languageContext == null) {
            return null;
        }
        try {
            return languageContext.context.engine.enterIfNeeded(languageContext.context);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(languageContext, t, false);
        }
    }

    static void hostLeave(PolyglotLanguageContext languageContext, Object prev) {
        if (languageContext == null) {
            return;
        }
        try {
            languageContext.context.engine.leaveIfNeeded(prev, languageContext.context);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(languageContext, t, false);
        }
    }

    @TruffleBoundary
    protected static RuntimeException unsupported(PolyglotLanguageContext languageContext, Object receiver, String message, String useToCheck) {
        assert languageContext == null || !languageContext.context.engine.needsEnter(languageContext.context);
        String polyglotMessage;
        if (useToCheck != null) {
            polyglotMessage = String.format("Unsupported operation %s.%s for %s. You can ensure that the operation is supported using %s.%s.",
                            Value.class.getSimpleName(), message, getValueInfo(languageContext, receiver), Value.class.getSimpleName(), useToCheck);
        } else {
            polyglotMessage = String.format("Unsupported operation %s.%s for %s.",
                            Value.class.getSimpleName(), message, getValueInfo(languageContext, receiver));
        }
        return PolyglotEngineException.unsupported(polyglotMessage);
    }

    private static final int CHARACTER_LIMIT = 140;

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    @TruffleBoundary
    static String getValueInfo(PolyglotLanguageContext languageContext, Object receiver) {
        if (languageContext == null) {
            return receiver.toString();
        } else if (receiver == null) {
            assert false : "receiver should never be null";
            return "null";
        }
        PolyglotContextImpl context = languageContext.context;
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
    protected static RuntimeException nullCoercion(PolyglotLanguageContext languageContext, Object receiver, Class<?> targetType, String message, String useToCheck) {
        assert languageContext == null || !languageContext.context.engine.needsEnter(languageContext.context);
        String valueInfo = getValueInfo(languageContext, receiver);
        throw PolyglotEngineException.nullPointer(String.format("Cannot convert null value %s to Java type '%s' using %s.%s. " +
                        "You can ensure that the operation is supported using %s.%s.",
                        valueInfo, targetType, Value.class.getSimpleName(), message, Value.class.getSimpleName(), useToCheck));
    }

    @TruffleBoundary
    protected static RuntimeException cannotConvert(PolyglotLanguageContext languageContext, Object receiver, Class<?> targetType, String message, String useToCheck, String reason) {
        assert languageContext == null || !languageContext.context.engine.needsEnter(languageContext.context);
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
    protected static RuntimeException invalidMemberKey(PolyglotLanguageContext context, Object receiver, String identifier) {
        String message = String.format("Invalid member key '%s' for object %s.", identifier, getValueInfo(context, receiver));
        throw PolyglotEngineException.illegalArgument(message);
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
    protected static RuntimeException invalidHashKey(PolyglotLanguageContext context, Object receiver, Object key) {
        String message = String.format("Invalid hash key %s for object %s.", getValueInfo(context, key), getValueInfo(context, receiver));
        throw PolyglotEngineException.illegalArgument(message);
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
    protected static RuntimeException invalidHashEntryValue(PolyglotLanguageContext context, Object receiver, Object value) {
        String message = String.format("Invalid hash entry value %s for object %s.",
                        getValueInfo(context, value),
                        getValueInfo(context, receiver));
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
    protected static RuntimeException invalidInstantiateArity(PolyglotLanguageContext context, Object receiver, Object[] arguments, int expected, int actual) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument count when instantiating %s with arguments %s. Expected %d argument(s) but got %d.",
                        getValueInfo(context, receiver), Arrays.asList(formattedArgs), expected, actual);
        throw PolyglotEngineException.illegalArgument(message);
    }

    @TruffleBoundary
    protected static RuntimeException invalidExecuteArity(PolyglotLanguageContext context, Object receiver, Object[] arguments, int expected, int actual) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument count when executing %s with arguments %s. Expected %d argument(s) but got %d.",
                        getValueInfo(context, receiver), Arrays.asList(formattedArgs), expected, actual);
        throw PolyglotEngineException.illegalArgument(message);
    }

    @TruffleBoundary
    protected static RuntimeException invalidInvokeArity(PolyglotLanguageContext context, Object receiver, String member, Object[] arguments, int expected, int actual) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument count when invoking '%s' on %s with arguments %s. Expected %d argument(s) but got %d.",
                        member,
                        getValueInfo(context, receiver), Arrays.asList(formattedArgs), expected, actual);
        throw PolyglotEngineException.illegalArgument(message);
    }

    private static String[] formatArgs(PolyglotLanguageContext context, Object[] arguments) {
        String[] formattedArgs = new String[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            formattedArgs[i] = getValueInfo(context, arguments[i]);
        }
        return formattedArgs;
    }

    @Override
    public final String toString(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            return toStringImpl(receiver);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    protected String toStringImpl(Object receiver) throws AssertionError {
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
    public SourceSection getSourceLocation(Object receiver) {
        if (languageContext == null) {
            return null;
        }
        Object prev = hostEnter(languageContext);
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
            return languageContext.getImpl().getPolyglotSourceSection(result);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @Override
    public boolean isMetaObject(Object receiver) {
        return false;
    }

    @Override
    public boolean equalsImpl(Object receiver, Object obj) {
        if (receiver == obj) {
            return true;
        }
        return HostWrapper.equals(languageContext, receiver, obj);
    }

    @Override
    public int hashCodeImpl(Object receiver) {
        return HostWrapper.hashCode(languageContext, receiver);
    }

    @Override
    public boolean isMetaInstance(Object receiver, Object instance) {
        Object prev = hostEnter(languageContext);
        try {
            throw unsupported(languageContext, receiver, "isMetaInstance(Object)", "isMetaObject()");
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @Override
    public String getMetaQualifiedName(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            throw unsupported(languageContext, receiver, "getMetaQualifiedName()", "isMetaObject()");
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    @Override
    public String getMetaSimpleName(Object receiver) {
        Object prev = hostEnter(languageContext);
        try {
            throw unsupported(languageContext, receiver, "getMetaSimpleName()", "isMetaObject()");
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    static CallTarget createTarget(InteropNode root) {
        CallTarget target = Truffle.getRuntime().createCallTarget(root);
        Class<?>[] types = root.getArgumentTypes();
        if (types != null) {
            RUNTIME.initializeProfile(target, types);
        }
        return target;
    }

    static PolyglotValue createInteropValue(PolyglotLanguageContext languageContext, TruffleObject receiver, Class<?> receiverType) {
        PolyglotLanguageInstance languageInstance = languageContext.getLanguageInstance();
        InteropCodeCache cache = languageInstance.valueCodeCache.get(receiverType);
        if (cache == null) {
            cache = new InteropCodeCache(languageInstance, receiver, receiverType);
            languageInstance.valueCodeCache.put(receiverType, cache);
        }
        return new InteropValue(languageContext, cache);
    }

    static PolyglotValue createHostNull(PolyglotImpl polyglot) {
        return new HostNull(polyglot);
    }

    static void createDefaultValues(PolyglotImpl polyglot, PolyglotLanguageContext context, Map<Class<?>, PolyglotValue> valueCache) {
        addDefaultValue(polyglot, context, valueCache, false);
        addDefaultValue(polyglot, context, valueCache, "");
        addDefaultValue(polyglot, context, valueCache, 'a');
        addDefaultValue(polyglot, context, valueCache, (byte) 0);
        addDefaultValue(polyglot, context, valueCache, (short) 0);
        addDefaultValue(polyglot, context, valueCache, 0);
        addDefaultValue(polyglot, context, valueCache, 0L);
        addDefaultValue(polyglot, context, valueCache, 0F);
        addDefaultValue(polyglot, context, valueCache, 0D);
    }

    static void addDefaultValue(PolyglotImpl polyglot, PolyglotLanguageContext context, Map<Class<?>, PolyglotValue> valueCache, Object primitive) {
        valueCache.put(primitive.getClass(), new PrimitiveValue(polyglot, context, primitive));
    }

    @SuppressWarnings("unused")
    static class InteropCodeCache {

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
        final CallTarget hasIterator;
        final CallTarget getIterator;
        final CallTarget isIterator;
        final CallTarget hasIteratorNextElement;
        final CallTarget getIteratorNextElement;
        final CallTarget hasHashEntries;
        final CallTarget hasHashEntry;
        final CallTarget getHashValue;
        final CallTarget putHashEntry;
        final CallTarget removeHashEntry;
        final CallTarget getHashEntriesIterator;
        final CallTarget isHashEntry;
        final CallTarget getHashEntryKey;
        final CallTarget getHashEntryValue;
        final CallTarget setHashEntryValue;

        final boolean isProxy;
        final boolean isHost;

        final CallTarget asClassLiteral;
        final CallTarget asTypeLiteral;
        final Class<?> receiverType;
        final PolyglotLanguageInstance languageInstance;

        InteropCodeCache(PolyglotLanguageInstance languageInstance, TruffleObject receiverObject, Class<?> receiverType) {
            Objects.requireNonNull(receiverType);
            this.languageInstance = languageInstance;
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
            this.readBufferByte = createTarget(PolyglotValueFactory.InteropCodeCacheFactory.ReadBufferByteNodeGen.create(this));
            this.writeBufferByte = createTarget(WriteBufferByteNodeGen.create(this));
            this.readBufferShort = createTarget(PolyglotValueFactory.InteropCodeCacheFactory.ReadBufferShortNodeGen.create(this));
            this.writeBufferShort = createTarget(WriteBufferShortNodeGen.create(this));
            this.readBufferInt = createTarget(ReadBufferIntNodeGen.create(this));
            this.writeBufferInt = createTarget(WriteBufferIntNodeGen.create(this));
            this.readBufferLong = createTarget(PolyglotValueFactory.InteropCodeCacheFactory.ReadBufferLongNodeGen.create(this));
            this.writeBufferLong = createTarget(WriteBufferLongNodeGen.create(this));
            this.readBufferFloat = createTarget(ReadBufferFloatNodeGen.create(this));
            this.writeBufferFloat = createTarget(WriteBufferFloatNodeGen.create(this));
            this.readBufferDouble = createTarget(PolyglotValueFactory.InteropCodeCacheFactory.ReadBufferDoubleNodeGen.create(this));
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
            this.isProxy = PolyglotProxy.isProxyGuestObject(receiverObject);
            this.isHost = HostObject.isInstance(receiverObject);
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
            this.hasIterator = createTarget(HasIteratorNodeGen.create(this));
            this.getIterator = createTarget(GetIteratorNodeGen.create(this));
            this.isIterator = createTarget(IsIteratorNodeGen.create(this));
            this.hasIteratorNextElement = createTarget(HasIteratorNextElementNodeGen.create(this));
            this.getIteratorNextElement = createTarget(GetIteratorNextElementNodeGen.create(this));
            this.hasHashEntries = createTarget(HasHashEntriesNodeGen.create(this));
            this.hasHashEntry = createTarget(HasHashEntryNodeGen.create(this));
            this.getHashValue = createTarget(GetHashValueNodeGen.create(this));
            this.putHashEntry = createTarget(PutHashEntryNodeGen.create(this));
            this.removeHashEntry = createTarget(RemoveHashEntryNodeGen.create(this));
            this.getHashEntriesIterator = createTarget(GetHashEntriesIteratorNodeGen.create(this));
            this.isHashEntry = createTarget(IsHashEntryNodeGen.create(this));
            this.getHashEntryKey = createTarget(GetHashEntryKeyNodeGen.create(this));
            this.getHashEntryValue = createTarget(GetHashEntryValueNodeGen.create(this));
            this.setHashEntryValue = createTarget(SetHashEntryValueNodeGen.create(this));
        }

        abstract static class IsDateNode extends InteropNode {

            protected IsDateNode(InteropCodeCache interop) {
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

            protected AsDateNode(InteropCodeCache interop) {
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

            protected IsTimeNode(InteropCodeCache interop) {
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

            protected AsTimeNode(InteropCodeCache interop) {
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

            protected IsTimeZoneNode(InteropCodeCache interop) {
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

            protected AsTimeZoneNode(InteropCodeCache interop) {
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

            protected IsDurationNode(InteropCodeCache interop) {
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

            protected AsDurationNode(InteropCodeCache interop) {
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

            protected AsInstantNode(InteropCodeCache interop) {
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

            @Child ToHostNode toHost = ToHostNodeGen.create();

            protected AsClassLiteralNode(InteropCodeCache interop) {
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
                return toHost.execute(receiver, (Class<?>) args[ARGUMENT_OFFSET], null, context, true);
            }

        }

        private static class AsTypeLiteralNode extends InteropNode {

            @Child ToHostNode toHost = ToHostNodeGen.create();

            protected AsTypeLiteralNode(InteropCodeCache interop) {
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
                return toHost.execute(receiver, typeLiteral.getRawType(), typeLiteral.getType(), context, true);
            }

        }

        abstract static class IsNativePointerNode extends InteropNode {

            protected IsNativePointerNode(InteropCodeCache interop) {
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

            protected AsNativePointerNode(InteropCodeCache interop) {
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

            protected HasArrayElementsNode(InteropCodeCache interop) {
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

            protected GetMemberKeysNode(InteropCodeCache interop) {
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

            protected GetArrayElementNode(InteropCodeCache interop) {
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
            protected SetArrayElementNode(InteropCodeCache interop) {
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

            protected RemoveArrayElementNode(InteropCodeCache interop) {
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

            protected GetArraySizeNode(InteropCodeCache interop) {
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

            protected HasBufferElementsNode(InteropCodeCache interop) {
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

            protected IsBufferWritableNode(InteropCodeCache interop) {
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

            protected GetBufferSizeNode(InteropCodeCache interop) {
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

            protected ReadBufferByteNode(InteropCodeCache interop) {
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
            protected WriteBufferByteNode(InteropCodeCache interop) {
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

            protected ReadBufferShortNode(InteropCodeCache interop) {
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
            protected WriteBufferShortNode(InteropCodeCache interop) {
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

            protected ReadBufferIntNode(InteropCodeCache interop) {
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
            protected WriteBufferIntNode(InteropCodeCache interop) {
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

            protected ReadBufferLongNode(InteropCodeCache interop) {
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
            protected WriteBufferLongNode(InteropCodeCache interop) {
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

            protected ReadBufferFloatNode(InteropCodeCache interop) {
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
            protected WriteBufferFloatNode(InteropCodeCache interop) {
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

            protected ReadBufferDoubleNode(InteropCodeCache interop) {
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
            protected WriteBufferDoubleNode(InteropCodeCache interop) {
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

            protected GetMemberNode(InteropCodeCache interop) {
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

            protected PutMemberNode(InteropCodeCache interop) {
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
                    throw invalidMemberKey(context, receiver, key);
                } catch (UnsupportedTypeException e) {
                    invalidValue.enter();
                    throw invalidMemberValue(context, receiver, key, value);
                }
                return null;
            }
        }

        abstract static class RemoveMemberNode extends InteropNode {

            protected RemoveMemberNode(InteropCodeCache interop) {
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
                    if (!objects.hasMembers(receiver) || objects.isMemberExisting(receiver, key)) {
                        throw removeMemberUnsupported(context, receiver);
                    } else {
                        value = Boolean.FALSE;
                    }
                } catch (UnknownIdentifierException e) {
                    unknown.enter();
                    value = Boolean.FALSE;
                }
                return value;
            }

        }

        abstract static class IsNullNode extends InteropNode {

            protected IsNullNode(InteropCodeCache interop) {
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

            protected HasMembersNode(InteropCodeCache interop) {
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

            protected AbstractMemberInfoNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected final Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, String.class};
            }

        }

        abstract static class HasMemberNode extends AbstractMemberInfoNode {

            protected HasMemberNode(InteropCodeCache interop) {
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

            protected CanInvokeNode(InteropCodeCache interop) {
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

            protected CanExecuteNode(InteropCodeCache interop) {
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

            protected CanInstantiateNode(InteropCodeCache interop) {
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
            private final ToGuestValuesNode toGuestValues = ToGuestValuesNode.create();
            private final BranchProfile invalidArgument = BranchProfile.create();
            private final BranchProfile arity = BranchProfile.create();
            private final BranchProfile unsupported = BranchProfile.create();

            protected AbstractExecuteNode(InteropCodeCache interop) {
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
                    throw invalidExecuteArity(context, receiver, guestArguments, e.getExpectedArity(), e.getActualArity());
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw executeUnsupported(context, receiver);
                }
            }

        }

        private static class ExecuteVoidNode extends AbstractExecuteNode {

            protected ExecuteVoidNode(InteropCodeCache interop) {
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

            protected ExecuteVoidNoArgsNode(InteropCodeCache interop) {
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

            protected ExecuteNode(InteropCodeCache interop) {
                super(interop);
                this.toHostValue = ToHostValueNode.create(interop.languageInstance.language.getImpl());
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

            protected ExecuteNoArgsNode(InteropCodeCache interop) {
                super(interop);
                this.toHostValue = ToHostValueNode.create(interop.languageInstance.language.getImpl());
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

            private final ToGuestValuesNode toGuestValues = ToGuestValuesNode.create();
            private final ToHostValueNode toHostValue;

            protected NewInstanceNode(InteropCodeCache interop) {
                super(interop);
                this.toHostValue = ToHostValueNode.create(interop.languageInstance.language.getImpl());
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
                    throw invalidInstantiateArity(context, receiver, instantiateArguments, e.getExpectedArity(), e.getActualArity());
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

            protected AbstractInvokeNode(InteropCodeCache interop) {
                super(interop);
                this.toHostValue = ToHostValueNode.create(interop.languageInstance.language.getImpl());
            }

            protected final Object executeShared(PolyglotLanguageContext context, Object receiver, String key, Object[] guestArguments) {
                try {
                    return toHostValue.execute(context, objects.invokeMember(receiver, key, guestArguments));
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw invokeUnsupported(context, receiver, key);
                } catch (UnknownIdentifierException e) {
                    unknownIdentifier.enter();
                    throw invalidMemberKey(context, receiver, key);
                } catch (UnsupportedTypeException e) {
                    invalidArgument.enter();
                    throw invalidInvokeArgumentType(context, receiver, key, e);
                } catch (ArityException e) {
                    arity.enter();
                    throw invalidInvokeArity(context, receiver, key, guestArguments, e.getExpectedArity(), e.getActualArity());
                }
            }

        }

        private static class InvokeNode extends AbstractInvokeNode {

            private final ToGuestValuesNode toGuestValues = ToGuestValuesNode.create();

            protected InvokeNode(InteropCodeCache interop) {
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

            protected InvokeNoArgsNode(InteropCodeCache interop) {
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

            protected IsExceptionNode(InteropCodeCache interop) {
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

            protected ThrowExceptionNode(InteropCodeCache interop) {
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

            protected IsMetaObjectNode(InteropCodeCache interop) {
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

            protected GetMetaQualifiedNameNode(InteropCodeCache interop) {
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
                    throw unsupported(context, receiver, "throwException()", "isException()");
                }
            }
        }

        abstract static class GetMetaSimpleNameNode extends InteropNode {

            protected GetMetaSimpleNameNode(InteropCodeCache interop) {
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
                    throw unsupported(context, receiver, "throwException()", "isException()");
                }
            }
        }

        abstract static class IsMetaInstanceNode extends InteropNode {

            protected IsMetaInstanceNode(InteropCodeCache interop) {
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
                    throw unsupported(context, receiver, "throwException()", "isException()");
                }
            }
        }

        abstract static class HasIteratorNode extends InteropNode {

            protected HasIteratorNode(InteropCodeCache interop) {
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

            protected GetIteratorNode(InteropCodeCache interop) {
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

            protected IsIteratorNode(InteropCodeCache interop) {
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

            protected HasIteratorNextElementNode(InteropCodeCache interop) {
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

            protected GetIteratorNextElementNode(InteropCodeCache interop) {
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

            protected HasHashEntriesNode(InteropCodeCache interop) {
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

        abstract static class HasHashEntryNode extends InteropNode {

            protected HasHashEntryNode(InteropCodeCache interop) {
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

            protected GetHashValueNode(InteropCodeCache interop) {
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
                Value value;
                try {
                    value = toHost.execute(context, hashes.readHashValue(receiver, key));
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    if (hashes.hasHashEntries(receiver)) {
                        // Todo: Shouldn't we rather throw UnsupportedOperationException
                        value = null;
                    } else {
                        throw getHashValueUnsupported(context, receiver, key);
                    }
                } catch (UnknownHashKeyException e) {
                    invalidKey.enter();
                    throw invalidHashKey(context, receiver, key);
                }
                return value;
            }
        }

        abstract static class PutHashEntryNode extends InteropNode {

            protected PutHashEntryNode(InteropCodeCache interop) {
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
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw putHashEntryUnsupported(context, receiver, key, value);
                } catch (UnknownHashKeyException e) {
                    invalidKey.enter();
                    throw invalidHashKey(context, receiver, key);
                } catch (UnsupportedTypeException e) {
                    invalidValue.enter();
                    throw invalidHashValue(context, receiver, key, value);
                }
                return null;
            }
        }

        abstract static class RemoveHashEntryNode extends InteropNode {

            protected RemoveHashEntryNode(InteropCodeCache interop) {
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
                } catch (UnknownHashKeyException e) {
                    invalidKey.enter();
                    result = Boolean.FALSE;
                }
                return result;
            }
        }

        abstract static class GetHashEntriesIteratorNode extends InteropNode {

            GetHashEntriesIteratorNode(InteropCodeCache interop) {
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

        abstract static class IsHashEntryNode extends InteropNode {

            IsHashEntryNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "isHashEntry";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary hashEntries) {
                return hashEntries.isHashEntry(receiver);
            }
        }

        abstract static class GetHashEntryKeyNode extends InteropNode {

            GetHashEntryKeyNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "getHashEntryKey";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary hashEntries,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported) {
                try {
                    return toHost.execute(context, hashEntries.getHashEntryKey(receiver));
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw getHashEntryKeyUnsupported(context, receiver);
                }
            }
        }

        abstract static class GetHashEntryValueNode extends InteropNode {

            GetHashEntryValueNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType};
            }

            @Override
            protected String getOperationName() {
                return "getHashEntryValue";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary hashEntries,
                            @Cached("createToHost()") ToHostValueNode toHost,
                            @Cached BranchProfile unsupported) {
                try {
                    return toHost.execute(context, hashEntries.getHashEntryValue(receiver));
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw getHashEntryValueUnsupported(context, receiver);
                }
            }
        }

        abstract static class SetHashEntryValueNode extends InteropNode {

            SetHashEntryValueNode(InteropCodeCache interop) {
                super(interop);
            }

            @Override
            protected Class<?>[] getArgumentTypes() {
                return new Class<?>[]{PolyglotLanguageContext.class, polyglot.receiverType, Object.class};
            }

            @Override
            protected String getOperationName() {
                return "setHashEntryValue";
            }

            @Specialization(limit = "CACHE_LIMIT")
            static Object doCached(PolyglotLanguageContext context, Object receiver, Object[] args, //
                            @CachedLibrary("receiver") InteropLibrary hashEntries,
                            @Cached ToGuestValueNode toGuest,
                            @Cached BranchProfile unsupported,
                            @Cached BranchProfile invalidValue) {

                Object hostValue = args[ARGUMENT_OFFSET];
                Object value = toGuest.execute(context, hostValue);
                try {
                    hashEntries.setHashEntryValue(receiver, value);
                    return null;
                } catch (UnsupportedMessageException e) {
                    unsupported.enter();
                    throw setHashEntryValueUnsupported(context, receiver);
                } catch (UnsupportedTypeException e) {
                    invalidValue.enter();
                    throw invalidHashEntryValue(context, receiver, value);
                }
            }
        }
    }

    static final class PrimitiveValue extends PolyglotValue {

        private final InteropLibrary interop;

        PrimitiveValue(PolyglotImpl polyglot, PolyglotLanguageContext context, Object primitiveValue) {
            super(polyglot, context);
            /*
             * No caching needed for primitives. We do that to avoid the overhead of crossing a
             * Truffle call boundary.
             */
            this.interop = InteropLibrary.getFactory().getUncached(primitiveValue);
        }

        @Override
        public boolean isString(Object receiver) {
            return interop.isString(receiver);
        }

        @Override
        public boolean isBoolean(Object receiver) {
            return interop.isBoolean(receiver);
        }

        @Override
        public boolean asBoolean(Object receiver) {
            try {
                return interop.asBoolean(receiver);
            } catch (UnsupportedMessageException e) {
                return super.asBoolean(receiver);
            }
        }

        @Override
        public String asString(Object receiver) {
            try {
                return interop.asString(receiver);
            } catch (UnsupportedMessageException e) {
                return super.asString(receiver);
            }
        }

        @Override
        public boolean isNumber(Object receiver) {
            return interop.isNumber(receiver);
        }

        @Override
        public boolean fitsInByte(Object receiver) {
            return interop.fitsInByte(receiver);
        }

        @Override
        public boolean fitsInShort(Object receiver) {
            return interop.fitsInShort(receiver);
        }

        @Override
        public boolean fitsInInt(Object receiver) {
            return interop.fitsInInt(receiver);
        }

        @Override
        public boolean fitsInLong(Object receiver) {
            return interop.fitsInLong(receiver);
        }

        @Override
        public boolean fitsInFloat(Object receiver) {
            return interop.fitsInFloat(receiver);
        }

        @Override
        public boolean fitsInDouble(Object receiver) {
            return interop.fitsInDouble(receiver);
        }

        @Override
        public byte asByte(Object receiver) {
            try {
                return interop.asByte(receiver);
            } catch (UnsupportedMessageException e) {
                return super.asByte(receiver);
            }
        }

        @Override
        public short asShort(Object receiver) {
            try {
                return interop.asShort(receiver);
            } catch (UnsupportedMessageException e) {
                return super.asShort(receiver);
            }
        }

        @Override
        public int asInt(Object receiver) {
            try {
                return interop.asInt(receiver);
            } catch (UnsupportedMessageException e) {
                return super.asInt(receiver);
            }
        }

        @Override
        public long asLong(Object receiver) {
            try {
                return interop.asLong(receiver);
            } catch (UnsupportedMessageException e) {
                return super.asLong(receiver);
            }
        }

        @Override
        public float asFloat(Object receiver) {
            try {
                return interop.asFloat(receiver);
            } catch (UnsupportedMessageException e) {
                return super.asFloat(receiver);
            }
        }

        @Override
        public double asDouble(Object receiver) {
            try {
                return interop.asDouble(receiver);
            } catch (UnsupportedMessageException e) {
                return super.asDouble(receiver);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T as(Object receiver, Class<T> targetType) {
            Object prev = hostEnter(languageContext);
            try {
                return (T) ToHostNodeGen.getUncached().execute(receiver, targetType, targetType, languageContext, true);
            } catch (Throwable e) {
                throw PolyglotImpl.guestToHostException((languageContext), e, true);
            } finally {
                hostLeave(languageContext, prev);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T as(Object receiver, TypeLiteral<T> targetType) {
            return as(receiver, targetType.getRawType());
        }

        @Override
        public Value getMetaObjectImpl(Object receiver) {
            return super.getMetaObjectImpl(getLanguageView(receiver));
        }

        @Override
        protected String toStringImpl(Object receiver) throws AssertionError {
            return super.toStringImpl(getLanguageView(receiver));
        }

        private Object getLanguageView(Object receiver) {
            if (languageContext == null) {
                return receiver;
            }
            return languageContext.getLanguageViewNoCheck(receiver);
        }

    }

    private static final class HostNull extends PolyglotValue {

        private final PolyglotImpl polyglot;

        HostNull(PolyglotImpl polyglot) {
            super(polyglot, null);
            this.polyglot = polyglot;
        }

        @Override
        public boolean isNull(Object receiver) {
            return true;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T as(Object receiver, Class<T> targetType) {
            if (targetType == Value.class) {
                return (T) polyglot.hostNull;
            }
            return null;
        }

        @SuppressWarnings("cast")
        @Override
        public <T> T as(Object receiver, TypeLiteral<T> targetType) {
            return as(receiver, (Class<T>) targetType.getRawType());
        }

    }

    abstract static class InteropNode extends HostToGuestRootNode {

        protected static final int CACHE_LIMIT = 5;

        protected final InteropCodeCache polyglot;

        protected abstract String getOperationName();

        protected InteropNode(InteropCodeCache polyglot) {
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

        protected final PolyglotImpl getImpl() {
            return polyglot.languageInstance.language.getImpl();
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
    static final class HostValue extends PolyglotValue {

        HostValue(PolyglotImpl polyglot) {
            super(polyglot, null);
        }

        @Override
        public boolean isHostObject(Object receiver) {
            return HostObject.isInstance(receiver);
        }

        @Override
        public Object asHostObject(Object receiver) {
            return HostObject.valueOf(receiver);
        }

        @Override
        public boolean isProxyObject(Object receiver) {
            return PolyglotProxy.isProxyGuestObject(receiver);
        }

        @Override
        public Object asProxyObject(Object receiver) {
            return PolyglotProxy.toProxyHostObject((TruffleObject) receiver);
        }

        @Override
        public <T> T as(Object receiver, Class<T> targetType) {
            return asImpl(receiver, targetType);
        }

        @SuppressWarnings("cast")
        @Override
        public <T> T as(Object receiver, TypeLiteral<T> targetType) {
            return asImpl(receiver, (Class<T>) targetType.getRawType());
        }

        <T> T asImpl(Object receiver, Class<T> targetType) {
            Object hostValue;
            if (isProxyObject(receiver)) {
                hostValue = asProxyObject(receiver);
            } else if (isHostObject(receiver)) {
                hostValue = asHostObject(receiver);
            } else {
                throw new ClassCastException();
            }
            return targetType.cast(hostValue);
        }

    }

    private static final class InteropValue extends PolyglotValue {

        private final InteropCodeCache cache;

        InteropValue(PolyglotLanguageContext context, InteropCodeCache codeCache) {
            super(context);
            this.cache = codeCache;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T as(Object receiver, Class<T> targetType) {
            return (T) RUNTIME.callProfiled(cache.asClassLiteral, languageContext, receiver, targetType);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T as(Object receiver, TypeLiteral<T> targetType) {
            return (T) RUNTIME.callProfiled(cache.asTypeLiteral, languageContext, receiver, targetType);
        }

        @Override
        public boolean isNativePointer(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.isNativePointer, languageContext, receiver);
        }

        @Override
        public boolean hasArrayElements(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.hasArrayElements, languageContext, receiver);
        }

        @Override
        public Value getArrayElement(Object receiver, long index) {
            return (Value) RUNTIME.callProfiled(cache.getArrayElement, languageContext, receiver, index);
        }

        @Override
        public void setArrayElement(Object receiver, long index, Object value) {
            RUNTIME.callProfiled(cache.setArrayElement, languageContext, receiver, index, value);
        }

        @Override
        public boolean removeArrayElement(Object receiver, long index) {
            return (boolean) RUNTIME.callProfiled(cache.removeArrayElement, languageContext, receiver, index);
        }

        @Override
        public long getArraySize(Object receiver) {
            return (long) RUNTIME.callProfiled(cache.getArraySize, languageContext, receiver);
        }

        // region Buffer Methods

        @Override
        public boolean hasBufferElements(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.hasBufferElements, languageContext, receiver);
        }

        @Override
        public boolean isBufferWritable(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.isBufferWritable, languageContext, receiver);
        }

        @Override
        public long getBufferSize(Object receiver) throws UnsupportedOperationException {
            return (long) RUNTIME.callProfiled(cache.getBufferSize, languageContext, receiver);
        }

        @Override
        public byte readBufferByte(Object receiver, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
            return (byte) RUNTIME.callProfiled(cache.readBufferByte, languageContext, receiver, byteOffset);
        }

        @Override
        public void writeBufferByte(Object receiver, long byteOffset, byte value) throws UnsupportedOperationException, IndexOutOfBoundsException {
            RUNTIME.callProfiled(cache.writeBufferByte, languageContext, receiver, byteOffset, value);
        }

        @Override
        public short readBufferShort(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
            return (short) RUNTIME.callProfiled(cache.readBufferShort, languageContext, receiver, order, byteOffset);
        }

        @Override
        public void writeBufferShort(Object receiver, ByteOrder order, long byteOffset, short value) throws UnsupportedOperationException, IndexOutOfBoundsException {
            RUNTIME.callProfiled(cache.writeBufferShort, languageContext, receiver, order, byteOffset, value);
        }

        @Override
        public int readBufferInt(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
            return (int) RUNTIME.callProfiled(cache.readBufferInt, languageContext, receiver, order, byteOffset);
        }

        @Override
        public void writeBufferInt(Object receiver, ByteOrder order, long byteOffset, int value) throws UnsupportedOperationException, IndexOutOfBoundsException {
            RUNTIME.callProfiled(cache.writeBufferInt, languageContext, receiver, order, byteOffset, value);
        }

        @Override
        public long readBufferLong(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
            return (long) RUNTIME.callProfiled(cache.readBufferLong, languageContext, receiver, order, byteOffset);
        }

        @Override
        public void writeBufferLong(Object receiver, ByteOrder order, long byteOffset, long value) throws UnsupportedOperationException, IndexOutOfBoundsException {
            RUNTIME.callProfiled(cache.writeBufferLong, languageContext, receiver, order, byteOffset, value);
        }

        @Override
        public float readBufferFloat(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
            return (float) RUNTIME.callProfiled(cache.readBufferFloat, languageContext, receiver, order, byteOffset);
        }

        @Override
        public void writeBufferFloat(Object receiver, ByteOrder order, long byteOffset, float value) throws UnsupportedOperationException, IndexOutOfBoundsException {
            RUNTIME.callProfiled(cache.writeBufferFloat, languageContext, receiver, order, byteOffset, value);
        }

        @Override
        public double readBufferDouble(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedOperationException, IndexOutOfBoundsException {
            return (double) RUNTIME.callProfiled(cache.readBufferDouble, languageContext, receiver, order, byteOffset);
        }

        @Override
        public void writeBufferDouble(Object receiver, ByteOrder order, long byteOffset, double value) throws UnsupportedOperationException, IndexOutOfBoundsException {
            RUNTIME.callProfiled(cache.writeBufferDouble, languageContext, receiver, order, byteOffset, value);
        }

        // endregion

        @Override
        public boolean hasMembers(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.hasMembers, languageContext, receiver);
        }

        @Override
        public Value getMember(Object receiver, String key) {
            return (Value) RUNTIME.callProfiled(cache.getMember, languageContext, receiver, key);
        }

        @Override
        public boolean hasMember(Object receiver, String key) {
            return (boolean) RUNTIME.callProfiled(cache.hasMember, languageContext, receiver, key);
        }

        @Override
        public void putMember(Object receiver, String key, Object member) {
            RUNTIME.callProfiled(cache.putMember, languageContext, receiver, key, member);
        }

        @Override
        public boolean removeMember(Object receiver, String key) {
            return (boolean) RUNTIME.callProfiled(cache.removeMember, languageContext, receiver, key);
        }

        @Override
        public Set<String> getMemberKeys(Object receiver) {
            Value keys = (Value) RUNTIME.callProfiled(cache.getMemberKeys, languageContext, receiver);
            if (keys == null) {
                // unsupported
                return Collections.emptySet();
            }
            return new MemberSet(receiver, keys);
        }

        @Override
        public long asNativePointer(Object receiver) {
            return (long) RUNTIME.callProfiled(cache.asNativePointer, languageContext, receiver);
        }

        @Override
        public boolean isDate(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.isDate, languageContext, receiver);
        }

        @Override
        public LocalDate asDate(Object receiver) {
            return (LocalDate) RUNTIME.callProfiled(cache.asDate, languageContext, receiver);
        }

        @Override
        public boolean isTime(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.isTime, languageContext, receiver);
        }

        @Override
        public LocalTime asTime(Object receiver) {
            return (LocalTime) RUNTIME.callProfiled(cache.asTime, languageContext, receiver);
        }

        @Override
        public boolean isTimeZone(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.isTimeZone, languageContext, receiver);
        }

        @Override
        public ZoneId asTimeZone(Object receiver) {
            return (ZoneId) RUNTIME.callProfiled(cache.asTimeZone, languageContext, receiver);
        }

        @Override
        public Instant asInstant(Object receiver) {
            return (Instant) RUNTIME.callProfiled(cache.asInstant, languageContext, receiver);
        }

        @Override
        public boolean isDuration(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.isDuration, languageContext, receiver);
        }

        @Override
        public Duration asDuration(Object receiver) {
            return (Duration) RUNTIME.callProfiled(cache.asDuration, languageContext, receiver);
        }

        @Override
        public boolean isHostObject(Object receiver) {
            return cache.isHost;
        }

        @Override
        public boolean isProxyObject(Object receiver) {
            return cache.isProxy;
        }

        @Override
        public Object asProxyObject(Object receiver) {
            if (cache.isProxy) {
                return PolyglotProxy.toProxyHostObject((TruffleObject) receiver);
            } else {
                return super.asProxyObject(receiver);
            }
        }

        @Override
        public Object asHostObject(Object receiver) {
            if (cache.isHost) {
                return HostObject.valueOf(receiver);
            } else {
                return super.asHostObject(receiver);
            }
        }

        @Override
        public boolean isNull(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.isNull, languageContext, receiver);
        }

        @Override
        public boolean canExecute(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.canExecute, languageContext, receiver);
        }

        @Override
        public void executeVoid(Object receiver, Object[] arguments) {
            RUNTIME.callProfiled(cache.executeVoid, languageContext, receiver, arguments);
        }

        @Override
        public void executeVoid(Object receiver) {
            RUNTIME.callProfiled(cache.executeVoidNoArgs, languageContext, receiver);
        }

        @Override
        public Value execute(Object receiver, Object[] arguments) {
            return (Value) RUNTIME.callProfiled(cache.execute, languageContext, receiver, arguments);
        }

        @Override
        public Value execute(Object receiver) {
            return (Value) RUNTIME.callProfiled(cache.executeNoArgs, languageContext, receiver);
        }

        @Override
        public boolean canInstantiate(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.canInstantiate, languageContext, receiver);
        }

        @Override
        public Value newInstance(Object receiver, Object[] arguments) {
            return (Value) RUNTIME.callProfiled(cache.newInstance, languageContext, receiver, arguments);
        }

        @Override
        public boolean canInvoke(String identifier, Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.canInvoke, languageContext, receiver, identifier);
        }

        @Override
        public Value invoke(Object receiver, String identifier, Object[] arguments) {
            return (Value) RUNTIME.callProfiled(cache.invoke, languageContext, receiver, identifier, arguments);
        }

        @Override
        public Value invoke(Object receiver, String identifier) {
            return (Value) RUNTIME.callProfiled(cache.invokeNoArgs, languageContext, receiver, identifier);
        }

        @Override
        public boolean isException(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.isException, languageContext, receiver);
        }

        @Override
        public RuntimeException throwException(Object receiver) {
            RUNTIME.callProfiled(cache.throwException, languageContext, receiver);
            throw super.throwException(receiver);
        }

        @Override
        public boolean isNumber(Object receiver) {
            Object c = hostEnter(languageContext);
            try {
                return UNCACHED_INTEROP.isNumber(receiver);
            } catch (Throwable e) {
                throw PolyglotImpl.guestToHostException(languageContext, e, true);
            } finally {
                hostLeave(languageContext, c);
            }
        }

        @Override
        public boolean fitsInByte(Object receiver) {
            Object c = hostEnter(languageContext);
            try {
                return UNCACHED_INTEROP.fitsInByte(receiver);
            } catch (Throwable e) {
                throw PolyglotImpl.guestToHostException(languageContext, e, true);
            } finally {
                hostLeave(languageContext, c);
            }
        }

        @Override
        public byte asByte(Object receiver) {
            Object c = hostEnter(languageContext);
            try {
                try {
                    return UNCACHED_INTEROP.asByte(receiver);
                } catch (UnsupportedMessageException e) {
                    return asByteUnsupported(receiver);
                }
            } catch (Throwable e) {
                throw PolyglotImpl.guestToHostException(languageContext, e, true);
            } finally {
                hostLeave(languageContext, c);
            }
        }

        @Override
        public boolean isString(Object receiver) {
            Object c = hostEnter(languageContext);
            try {
                return UNCACHED_INTEROP.isString(receiver);
            } catch (Throwable e) {
                throw PolyglotImpl.guestToHostException(languageContext, e, true);
            } finally {
                hostLeave(languageContext, c);
            }
        }

        @Override
        public String asString(Object receiver) {
            Object c = hostEnter(languageContext);
            try {
                try {
                    if (isNullUncached(receiver)) {
                        return null;
                    }
                    return UNCACHED_INTEROP.asString(receiver);
                } catch (UnsupportedMessageException e) {
                    return asStringUnsupported(receiver);
                }
            } catch (Throwable e) {
                throw PolyglotImpl.guestToHostException(languageContext, e, true);
            } finally {
                hostLeave(languageContext, c);
            }
        }

        @Override
        public boolean fitsInInt(Object receiver) {
            Object c = hostEnter(languageContext);
            try {
                return UNCACHED_INTEROP.fitsInInt(receiver);
            } catch (Throwable e) {
                throw PolyglotImpl.guestToHostException(languageContext, e, true);
            } finally {
                hostLeave(languageContext, c);
            }
        }

        @Override
        public int asInt(Object receiver) {
            Object c = hostEnter(languageContext);
            try {
                try {
                    return UNCACHED_INTEROP.asInt(receiver);
                } catch (UnsupportedMessageException e) {
                    return asIntUnsupported(receiver);
                }
            } catch (Throwable e) {
                throw PolyglotImpl.guestToHostException(languageContext, e, true);
            } finally {
                hostLeave(languageContext, c);
            }
        }

        @Override
        public boolean isBoolean(Object receiver) {
            Object c = hostEnter(languageContext);
            try {
                return InteropLibrary.getFactory().getUncached().isBoolean(receiver);
            } catch (Throwable e) {
                throw PolyglotImpl.guestToHostException(languageContext, e, true);
            } finally {
                hostLeave(languageContext, c);
            }
        }

        @Override
        public boolean asBoolean(Object receiver) {
            Object c = hostEnter(languageContext);
            try {
                try {
                    return InteropLibrary.getFactory().getUncached().asBoolean(receiver);
                } catch (UnsupportedMessageException e) {
                    return asBooleanUnsupported(receiver);
                }
            } catch (Throwable e) {
                throw PolyglotImpl.guestToHostException(languageContext, e, true);
            } finally {
                hostLeave(languageContext, c);
            }
        }

        @Override
        public boolean fitsInFloat(Object receiver) {
            Object c = hostEnter(languageContext);
            try {
                return InteropLibrary.getFactory().getUncached().fitsInFloat(receiver);
            } catch (Throwable e) {
                throw PolyglotImpl.guestToHostException(languageContext, e, true);
            } finally {
                hostLeave(languageContext, c);
            }
        }

        @Override
        public float asFloat(Object receiver) {
            Object c = hostEnter(languageContext);
            try {
                try {
                    return UNCACHED_INTEROP.asFloat(receiver);
                } catch (UnsupportedMessageException e) {
                    return asFloatUnsupported(receiver);
                }
            } catch (Throwable e) {
                throw PolyglotImpl.guestToHostException(languageContext, e, true);
            } finally {
                hostLeave(languageContext, c);
            }
        }

        @Override
        public boolean fitsInDouble(Object receiver) {
            Object c = hostEnter(languageContext);
            try {
                return UNCACHED_INTEROP.fitsInDouble(receiver);
            } catch (Throwable e) {
                throw PolyglotImpl.guestToHostException(languageContext, e, true);
            } finally {
                hostLeave(languageContext, c);
            }
        }

        @Override
        public double asDouble(Object receiver) {
            Object c = hostEnter(languageContext);
            try {
                try {
                    return UNCACHED_INTEROP.asDouble(receiver);
                } catch (UnsupportedMessageException e) {
                    return asDoubleUnsupported(receiver);
                }
            } catch (Throwable e) {
                throw PolyglotImpl.guestToHostException(languageContext, e, true);
            } finally {
                hostLeave(languageContext, c);
            }
        }

        @Override
        public boolean fitsInLong(Object receiver) {
            Object c = hostEnter(languageContext);
            try {
                return UNCACHED_INTEROP.fitsInLong(receiver);
            } catch (Throwable e) {
                throw PolyglotImpl.guestToHostException(languageContext, e, true);
            } finally {
                hostLeave(languageContext, c);
            }
        }

        @Override
        public long asLong(Object receiver) {
            Object c = hostEnter(languageContext);
            try {
                try {
                    return UNCACHED_INTEROP.asLong(receiver);
                } catch (UnsupportedMessageException e) {
                    return asLongUnsupported(receiver);
                }
            } catch (Throwable e) {
                throw PolyglotImpl.guestToHostException(languageContext, e, true);
            } finally {
                hostLeave(languageContext, c);
            }
        }

        @Override
        public boolean fitsInShort(Object receiver) {
            Object c = hostEnter(languageContext);
            try {
                return UNCACHED_INTEROP.fitsInShort(receiver);
            } catch (Throwable e) {
                throw PolyglotImpl.guestToHostException(languageContext, e, true);
            } finally {
                hostLeave(languageContext, c);
            }
        }

        @Override
        public short asShort(Object receiver) {
            Object c = hostEnter(languageContext);
            try {
                try {
                    return UNCACHED_INTEROP.asShort(receiver);
                } catch (UnsupportedMessageException e) {
                    return asShortUnsupported(receiver);
                }
            } catch (Throwable e) {
                throw PolyglotImpl.guestToHostException(languageContext, e, true);
            } finally {
                hostLeave(languageContext, c);
            }
        }

        @Override
        public boolean isMetaObject(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.isMetaObject, languageContext, receiver);
        }

        @Override
        public boolean isMetaInstance(Object receiver, Object instance) {
            return (boolean) RUNTIME.callProfiled(cache.isMetaInstance, languageContext, receiver, instance);
        }

        @Override
        public String getMetaQualifiedName(Object receiver) {
            return (String) RUNTIME.callProfiled(cache.getMetaQualifiedName, languageContext, receiver);
        }

        @Override
        public String getMetaSimpleName(Object receiver) {
            return (String) RUNTIME.callProfiled(cache.getMetaSimpleName, languageContext, receiver);
        }

        @Override
        public boolean hasIterator(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.hasIterator, languageContext, receiver);
        }

        @Override
        public Value getIterator(Object receiver) {
            return (Value) RUNTIME.callProfiled(cache.getIterator, languageContext, receiver);
        }

        @Override
        public boolean isIterator(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.isIterator, languageContext, receiver);
        }

        @Override
        public boolean hasIteratorNextElement(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.hasIteratorNextElement, languageContext, receiver);
        }

        @Override
        public Value getIteratorNextElement(Object receiver) {
            return (Value) RUNTIME.callProfiled(cache.getIteratorNextElement, languageContext, receiver);
        }

        @Override
        public boolean hasHashEntries(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.hasHashEntries, languageContext, receiver);
        }

        @Override
        public boolean hasHashEntry(Object receiver, Object key) {
            return (boolean) RUNTIME.callProfiled(cache.hasHashEntry, languageContext, receiver, key);
        }

        @Override
        public Value getHashValue(Object receiver, Object key) {
            return (Value) RUNTIME.callProfiled(cache.getHashValue, languageContext, receiver, key);
        }

        @Override
        public void putHashEntry(Object receiver, Object key, Object value) {
            RUNTIME.callProfiled(cache.putHashEntry, languageContext, receiver, key, value);
        }

        @Override
        public boolean removeHashEntry(Object receiver, Object key) {
            return (boolean) RUNTIME.callProfiled(cache.removeHashEntry, languageContext, receiver, key);
        }

        @Override
        public Value getHashEntriesIterator(Object receiver) {
            return (Value) RUNTIME.callProfiled(cache.getHashEntriesIterator, languageContext, receiver);
        }

        @Override
        public boolean isHashEntry(Object receiver) {
            return (boolean) RUNTIME.callProfiled(cache.isHashEntry, languageContext, receiver);
        }

        @Override
        public Value getHashEntryKey(Object receiver) {
            return (Value) RUNTIME.callProfiled(cache.getHashEntryKey, languageContext, receiver);
        }

        @Override
        public Value getHashEntryValue(Object receiver) {
            return (Value) RUNTIME.callProfiled(cache.getHashEntryValue, languageContext, receiver);
        }

        @Override
        public void setHashEntryValue(Object receiver, Object value) {
            RUNTIME.callProfiled(cache.setHashEntryValue, languageContext, receiver, value);
        }

        private final class MemberSet extends AbstractSet<String> {

            private final Object receiver;
            private final Value keys;
            private int cachedSize = -1;

            MemberSet(Object receiver, Value keys) {
                this.receiver = receiver;
                this.keys = keys;
            }

            @Override
            public boolean contains(Object o) {
                if (!(o instanceof String)) {
                    return false;
                }
                return hasMember(receiver, (String) o);
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

    }

}
