/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.runtime.dispatch.staticobject;

import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.espresso.meta.InteropKlassesDispatch;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.dispatch.messages.ArrayIterator;
import com.oracle.truffle.espresso.runtime.dispatch.messages.HashIterator;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessage;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessageFactory;

/**
 * Implementation of Espresso interop in a way that can be safely shared across contexts until code
 * sharing is implemented.
 * 
 * This works by looking up the context of the receiver, then selecting the context-specific
 * implementation for the object, based on the dispatch class that would have been selected when the
 * language is not shared.
 * 
 * In case an implementation cannot be found, the message will return the default value, as defined
 * in {@link InteropLibrary}.
 * 
 * @see #getTarget(StaticObject, InteropMessage.Message)
 * @see InteropMessageFactory
 */
@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
@SuppressWarnings("truffle-abstract-export") // TODO GR-44080 Adopt BigInteger Interop
public class SharedInterop {
    @GenerateUncached
    static abstract class CallSharedInteropMessage extends EspressoNode {
        public abstract Object execute(int dispatchId, InteropMessage.Message message, Object... args);

        static final int LIMIT = InteropKlassesDispatch.DISPATCH_TOTAL;

        @SuppressWarnings("unused")
        @Specialization(guards = {"dispatchId == cachedId"}, limit = "LIMIT")
        Object doCached(int dispatchId, InteropMessage.Message message, Object[] args,
                        @Cached("dispatchId") int cachedId,
                        @Cached(value = "create(getTarget(getContext(), dispatchId, message))", allowUncached = true) DirectCallNode call) {
            return call.call(args);
        }

        static CallTarget getTarget(EspressoContext ctx, int dispatchId, InteropMessage.Message message) {
            return SharedInterop.getTarget(ctx, dispatchId, message);
        }
    }

    static CallTarget getTarget(EspressoContext ctx, int dispatchId, InteropMessage.Message message) {
        return ctx.getLazyCaches().getInteropMessage(message, dispatchId);
    }

    static CallTarget getTarget(StaticObject receiver, InteropMessage.Message message) {
        assert !StaticObject.isNull(receiver);
        // Find not shared dispatch class.
        int dispatch = receiver.getKlass().getDispatchId();
        EspressoContext ctx = getContext(receiver);
        return ctx.getLazyCaches().getInteropMessage(message, dispatch);
    }

    private static UnsupportedMessageException unsupported() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    public static EspressoContext getContext(StaticObject receiver) {
        receiver.checkNotForeign();
        assert !StaticObject.isNull(receiver);
        return receiver.getKlass().getContext();
    }

    @ExportMessage
    public static boolean isNull(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsNull)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsNull, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsNull);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean isBoolean(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsBoolean)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsBoolean, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsBoolean);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean asBoolean(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.AsBoolean)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.AsBoolean, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.AsBoolean);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean isExecutable(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsExecutable)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsExecutable, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsExecutable);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object execute(StaticObject receiver, Object[] arguments,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.Execute)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.Execute, receiver, arguments);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.Execute);
        if (target != null) {
            return callNode.call(target, receiver, arguments);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean hasExecutableName(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.HasExecutableName)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.HasExecutableName, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.HasExecutableName);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getExecutableName(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetExecutableName)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.GetExecutableName, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetExecutableName);
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean hasDeclaringMetaObject(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.HasDeclaringMetaObject)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.HasDeclaringMetaObject, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.HasDeclaringMetaObject);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getDeclaringMetaObject(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetDeclaringMetaObject)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.GetDeclaringMetaObject, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetDeclaringMetaObject);
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isInstantiable(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsInstantiable)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsInstantiable, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsInstantiable);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object instantiate(StaticObject receiver, Object[] args,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.Instantiate)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.Instantiate, receiver, args);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.Instantiate);
        if (target != null) {
            return callNode.call(target, receiver, args);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isString(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsString)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsString, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsString);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static String asString(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.AsString)) {
            return (String) sharedCallNode.execute(dispatchId, InteropMessage.Message.AsString, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.AsString);
        if (target != null) {
            return (String) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static TruffleString asTruffleString(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode,
                    @CachedLibrary("receiver") InteropLibrary lib) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.AsTruffleString)) {
            return (TruffleString) sharedCallNode.execute(dispatchId, InteropMessage.Message.AsTruffleString, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.AsTruffleString);
        if (target != null) {
            return (TruffleString) callNode.call(target, receiver);
        }
        return TruffleString.fromJavaStringUncached(lib.asString(receiver), TruffleString.Encoding.UTF_16);
    }

    @ExportMessage
    public static boolean isNumber(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsNumber)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsNumber, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsNumber);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean fitsInByte(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.FitsInByte)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.FitsInByte, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.FitsInByte);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean fitsInShort(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.FitsInShort)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.FitsInShort, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.FitsInShort);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean fitsInInt(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.FitsInInt)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.FitsInInt, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.FitsInInt);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean fitsInLong(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.FitsInLong)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.FitsInLong, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.FitsInLong);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean fitsInFloat(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.FitsInFloat)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.FitsInFloat, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.FitsInFloat);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean fitsInDouble(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.FitsInDouble)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.FitsInDouble, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.FitsInDouble);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static byte asByte(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.AsByte)) {
            return (byte) sharedCallNode.execute(dispatchId, InteropMessage.Message.AsByte, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.AsByte);
        if (target != null) {
            return (byte) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static short asShort(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.AsShort)) {
            return (short) sharedCallNode.execute(dispatchId, InteropMessage.Message.AsShort, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.AsShort);
        if (target != null) {
            return (short) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static int asInt(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.AsInt)) {
            return (int) sharedCallNode.execute(dispatchId, InteropMessage.Message.AsInt, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.AsInt);
        if (target != null) {
            return (int) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static long asLong(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.AsLong)) {
            return (long) sharedCallNode.execute(dispatchId, InteropMessage.Message.AsLong, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.AsLong);
        if (target != null) {
            return (long) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static float asFloat(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.AsFloat)) {
            return (float) sharedCallNode.execute(dispatchId, InteropMessage.Message.AsFloat, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.AsFloat);
        if (target != null) {
            return (float) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static double asDouble(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.AsDouble)) {
            return (double) sharedCallNode.execute(dispatchId, InteropMessage.Message.AsDouble, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.AsDouble);
        if (target != null) {
            return (double) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean hasMembers(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.HasMembers)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.HasMembers, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.HasMembers);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getMembers(StaticObject receiver, boolean includeInternal,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetMembers)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.GetMembers, receiver, includeInternal);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetMembers);
        if (target != null) {
            return callNode.call(target, receiver, includeInternal);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isMemberReadable(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsMemberReadable)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsMemberReadable, receiver, member);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsMemberReadable);
        if (target != null) {
            return (boolean) callNode.call(target, receiver, member);
        }
        return false;
    }

    @ExportMessage
    public static Object readMember(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.ReadMember)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.ReadMember, receiver, member);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.ReadMember);
        if (target != null) {
            return callNode.call(target, receiver, member);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isMemberModifiable(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsMemberModifiable)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsMemberModifiable, receiver, member);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsMemberModifiable);
        if (target != null) {
            return (boolean) callNode.call(target, receiver, member);
        }
        return false;
    }

    @ExportMessage
    public static boolean isMemberInsertable(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsMemberInsertable)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsMemberInsertable, receiver, member);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsMemberInsertable);
        if (target != null) {
            return (boolean) callNode.call(target, receiver, member);
        }
        return false;
    }

    @ExportMessage
    public static void writeMember(StaticObject receiver, String member, Object value,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.WriteMember)) {
            sharedCallNode.execute(dispatchId, InteropMessage.Message.WriteMember, receiver, member, value);
            return;
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.WriteMember);
        if (target != null) {
            callNode.call(target, receiver, member, value);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isMemberRemovable(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsMemberRemovable)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsMemberRemovable, receiver, member);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsMemberRemovable);
        if (target != null) {
            return (boolean) callNode.call(target, receiver, member);
        }
        return false;
    }

    @ExportMessage
    public static void removeMember(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.RemoveMember)) {
            sharedCallNode.execute(dispatchId, InteropMessage.Message.RemoveMember, receiver, member);
            return;
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.RemoveMember);
        if (target != null) {
            callNode.call(target, receiver, member);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isMemberInvocable(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsMemberInvocable)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsMemberInvocable, receiver, member);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsMemberInvocable);
        if (target != null) {
            return (boolean) callNode.call(target, receiver, member);
        }
        return false;
    }

    @ExportMessage
    public static Object invokeMember(StaticObject receiver, String member, Object[] arguments,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.InvokeMember)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.InvokeMember, receiver, member, arguments);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.InvokeMember);
        if (target != null) {
            return callNode.call(target, receiver, member, arguments);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isMemberInternal(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsMemberInternal)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsMemberInternal, receiver, member);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsMemberInternal);
        if (target != null) {
            return (boolean) callNode.call(target, receiver, member);
        }
        return false;
    }

    @ExportMessage
    public static boolean hasMemberReadSideEffects(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.HasMemberReadSideEffects)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.HasMemberReadSideEffects, receiver, member);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.HasMemberReadSideEffects);
        if (target != null) {
            return (boolean) callNode.call(target, receiver, member);
        }
        return false;
    }

    @ExportMessage
    public static boolean hasMemberWriteSideEffects(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.HasMemberWriteSideEffects)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.HasMemberWriteSideEffects, receiver, member);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.HasMemberWriteSideEffects);
        if (target != null) {
            return (boolean) callNode.call(target, receiver, member);
        }
        return false;
    }

    @ExportMessage
    public static boolean hasHashEntries(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.HasHashEntries)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.HasHashEntries, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.HasHashEntries);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static long getHashSize(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetHashSize)) {
            return (long) sharedCallNode.execute(dispatchId, InteropMessage.Message.GetHashSize, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetHashSize);
        if (target != null) {
            return (long) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isHashEntryReadable(StaticObject receiver, Object key,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsHashEntryReadable)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsHashEntryReadable, receiver, key);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsHashEntryReadable);
        if (target != null) {
            return (boolean) callNode.call(target, receiver, key);
        }
        return false;
    }

    @ExportMessage
    public static Object readHashValue(StaticObject receiver, Object key,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.ReadHashValue)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.ReadHashValue, receiver, key);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.ReadHashValue);
        if (target != null) {
            return callNode.call(target, receiver, key);
        }
        throw unsupported();
    }

    @ExportMessage
    public static Object readHashValueOrDefault(StaticObject receiver, Object key, Object defaultValue,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode,
                    @CachedLibrary("receiver") InteropLibrary lib) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.ReadHashValueOrDefault)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.ReadHashValueOrDefault, receiver, key);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.ReadHashValueOrDefault);
        if (target != null) {
            return callNode.call(target, receiver, key);
        }
        try {
            return lib.readHashValue(receiver, key);
        } catch (UnknownKeyException e) {
            return defaultValue;
        }
    }

    @ExportMessage
    public static boolean isHashEntryModifiable(StaticObject receiver, Object key,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsHashEntryModifiable)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsHashEntryModifiable, receiver, key);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsHashEntryModifiable);
        if (target != null) {
            return (boolean) callNode.call(target, receiver, key);
        }
        return false;
    }

    @ExportMessage
    public static boolean isHashEntryInsertable(StaticObject receiver, Object key,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsHashEntryInsertable)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsHashEntryInsertable, receiver, key);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsHashEntryInsertable);
        if (target != null) {
            return (boolean) callNode.call(target, receiver, key);
        }
        return false;
    }

    @ExportMessage
    public static boolean isHashEntryWritable(StaticObject receiver, Object key,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode,
                    @CachedLibrary("receiver") InteropLibrary lib) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsHashEntryWritable)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsHashEntryWritable, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsHashEntryWritable);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return lib.isHashEntryModifiable(receiver, key) || lib.isHashEntryModifiable(receiver, key);
    }

    @ExportMessage
    public static void writeHashEntry(StaticObject receiver, Object key, Object value,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.WriteHashEntry)) {
            sharedCallNode.execute(dispatchId, InteropMessage.Message.WriteHashEntry, receiver, key, value);
            return;
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.WriteHashEntry);
        if (target != null) {
            callNode.call(target, receiver, key, value);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isHashEntryRemovable(StaticObject receiver, Object key,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsHashEntryRemovable)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsHashEntryRemovable, receiver, key);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsHashEntryRemovable);
        if (target != null) {
            return (boolean) callNode.call(target, receiver, key);
        }
        return false;
    }

    @ExportMessage
    public static void removeHashEntry(StaticObject receiver, Object key,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.RemoveHashEntry)) {
            sharedCallNode.execute(dispatchId, InteropMessage.Message.RemoveHashEntry, receiver, key);
            return;
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.RemoveHashEntry);
        if (target != null) {
            callNode.call(target, receiver, key);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isHashEntryExisting(StaticObject receiver, Object key,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode,
                    @CachedLibrary("receiver") InteropLibrary lib) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsHashEntryExisting)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsHashEntryExisting, receiver, key);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsHashEntryExisting);
        if (target != null) {
            callNode.call(target, receiver, key);
        }
        return lib.isHashEntryReadable(receiver, key) || lib.isHashEntryModifiable(receiver, key);
    }

    @ExportMessage
    public static Object getHashEntriesIterator(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode,
                    @CachedLibrary("receiver") InteropLibrary lib) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetHashEntriesIterator)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.GetHashEntriesIterator, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetHashEntriesIterator);
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static Object getHashKeysIterator(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode,
                    @CachedLibrary("receiver") InteropLibrary lib) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetHashKeysIterator)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.GetHashKeysIterator, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetHashKeysIterator);
        if (target != null) {
            return callNode.call(target, receiver);
        }
        Object entriesIterator = lib.getHashEntriesIterator(receiver);
        return HashIterator.keys(entriesIterator);
    }

    @ExportMessage
    public static Object getHashValuesIterator(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode,
                    @CachedLibrary("receiver") InteropLibrary lib) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetHashValuesIterator)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.GetHashValuesIterator, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetHashValuesIterator);
        if (target != null) {
            return callNode.call(target, receiver);
        }
        Object entriesIterator = lib.getHashEntriesIterator(receiver);
        return HashIterator.values(entriesIterator);
    }

    @ExportMessage
    public static boolean hasArrayElements(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.HasArrayElements)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.HasArrayElements, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.HasArrayElements);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object readArrayElement(StaticObject receiver, long index,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.ReadArrayElement)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.ReadArrayElement, receiver, index);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.ReadArrayElement);
        if (target != null) {
            return callNode.call(target, receiver, index);
        }
        throw unsupported();
    }

    @ExportMessage
    public static long getArraySize(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetArraySize)) {
            return (long) sharedCallNode.execute(dispatchId, InteropMessage.Message.GetArraySize, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetArraySize);
        if (target != null) {
            return (long) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isArrayElementReadable(StaticObject receiver, long index,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsArrayElementReadable)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsArrayElementReadable, receiver, index);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsArrayElementReadable);
        if (target != null) {
            return (boolean) callNode.call(target, receiver, index);
        }
        return false;
    }

    @ExportMessage
    public static void writeArrayElement(StaticObject receiver, long index, Object value,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.WriteArrayElement)) {
            sharedCallNode.execute(dispatchId, InteropMessage.Message.WriteArrayElement, receiver, index, value);
            return;
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.WriteArrayElement);
        if (target != null) {
            callNode.call(target, receiver, index, value);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static void removeArrayElement(StaticObject receiver, long index,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.RemoveArrayElement)) {
            sharedCallNode.execute(dispatchId, InteropMessage.Message.RemoveArrayElement, receiver, index);
            return;
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.RemoveArrayElement);
        if (target != null) {
            callNode.call(target, receiver, index);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isArrayElementModifiable(StaticObject receiver, long index,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsArrayElementModifiable)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsArrayElementModifiable, receiver, index);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsArrayElementModifiable);
        if (target != null) {
            return (boolean) callNode.call(target, receiver, index);
        }
        return false;
    }

    @ExportMessage
    public static boolean isArrayElementInsertable(StaticObject receiver, long index,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsArrayElementInsertable)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsArrayElementInsertable, receiver, index);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsArrayElementInsertable);
        if (target != null) {
            return (boolean) callNode.call(target, receiver, index);
        }
        return false;
    }

    @ExportMessage
    public static boolean isArrayElementRemovable(StaticObject receiver, long index,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsArrayElementRemovable)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsArrayElementRemovable, receiver, index);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsArrayElementRemovable);
        if (target != null) {
            return (boolean) callNode.call(target, receiver, index);
        }
        return false;
    }

    @ExportMessage
    public static boolean hasBufferElements(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.HasBufferElements)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.HasBufferElements, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.HasBufferElements);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean isBufferWritable(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode,
                    @CachedLibrary("receiver") InteropLibrary lib) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsBufferWritable)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsBufferWritable, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsBufferWritable);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        if (lib.hasBufferElements(receiver)) {
            return false;
        } else {
            throw unsupported();
        }
    }

    @ExportMessage
    public static long getBufferSize(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetBufferSize)) {
            return (long) sharedCallNode.execute(dispatchId, InteropMessage.Message.GetBufferSize, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetBufferSize);
        if (target != null) {
            return (long) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static byte readBufferByte(StaticObject receiver, long byteOffset,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.ReadBufferByte)) {
            return (byte) sharedCallNode.execute(dispatchId, InteropMessage.Message.ReadBufferByte, receiver, byteOffset);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.ReadBufferByte);
        if (target != null) {
            return (byte) callNode.call(target, receiver, byteOffset);
        }
        throw unsupported();
    }

    @ExportMessage
    public static void writeBufferByte(StaticObject receiver, long byteOffset, byte value,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.WriteBufferByte)) {
            sharedCallNode.execute(dispatchId, InteropMessage.Message.WriteBufferByte, receiver, byteOffset, value);
            return;
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.WriteBufferByte);
        if (target != null) {
            callNode.call(target, receiver, byteOffset, value);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static short readBufferShort(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.ReadBufferShort)) {
            return (short) sharedCallNode.execute(dispatchId, InteropMessage.Message.ReadBufferShort, receiver, order, byteOffset);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.ReadBufferShort);
        if (target != null) {
            return (short) callNode.call(target, receiver, order, byteOffset);
        }
        throw unsupported();
    }

    @ExportMessage
    public static void writeBufferShort(StaticObject receiver, ByteOrder order, long byteOffset, short value,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.WriteBufferShort)) {
            sharedCallNode.execute(dispatchId, InteropMessage.Message.WriteBufferShort, receiver, order, byteOffset);
            return;
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.WriteBufferShort);
        if (target != null) {
            callNode.call(target, receiver, order, byteOffset);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static int readBufferInt(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.ReadBufferInt)) {
            return (int) sharedCallNode.execute(dispatchId, InteropMessage.Message.ReadBufferInt, receiver, order, byteOffset);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.ReadBufferInt);
        if (target != null) {
            return (int) callNode.call(target, receiver, order, byteOffset);
        }
        throw unsupported();
    }

    @ExportMessage
    public static void writeBufferInt(StaticObject receiver, ByteOrder order, long byteOffset, int value,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.WriteBufferInt)) {
            sharedCallNode.execute(dispatchId, InteropMessage.Message.WriteBufferInt, receiver, order, byteOffset, value);
            return;
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.WriteBufferInt);
        if (target != null) {
            callNode.call(target, receiver, order, byteOffset, value);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static long readBufferLong(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.ReadBufferLong)) {
            return (long) sharedCallNode.execute(dispatchId, InteropMessage.Message.ReadBufferLong, receiver, order, byteOffset);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.ReadBufferLong);
        if (target != null) {
            return (long) callNode.call(target, receiver, order, byteOffset);
        }
        throw unsupported();
    }

    @ExportMessage
    public static void writeBufferLong(StaticObject receiver, ByteOrder order, long byteOffset, long value,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.WriteBufferLong)) {
            sharedCallNode.execute(dispatchId, InteropMessage.Message.WriteBufferLong, receiver, order, byteOffset, value);
            return;
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.WriteBufferLong);
        if (target != null) {
            callNode.call(target, receiver, order, byteOffset, value);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static float readBufferFloat(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.ReadBufferFloat)) {
            return (float) sharedCallNode.execute(dispatchId, InteropMessage.Message.ReadBufferFloat, receiver, order, byteOffset);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.ReadBufferFloat);
        if (target != null) {
            return (float) callNode.call(target, receiver, order, byteOffset);
        }
        throw unsupported();
    }

    @ExportMessage
    public static void writeBufferFloat(StaticObject receiver, ByteOrder order, long byteOffset, float value,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.WriteBufferFloat)) {
            sharedCallNode.execute(dispatchId, InteropMessage.Message.WriteBufferFloat, receiver, order, byteOffset, value);
            return;
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.WriteBufferFloat);
        if (target != null) {
            callNode.call(target, receiver, order, byteOffset, value);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static double readBufferDouble(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.ReadBufferDouble)) {
            return (double) sharedCallNode.execute(dispatchId, InteropMessage.Message.ReadBufferDouble, receiver, order, byteOffset);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.ReadBufferDouble);
        if (target != null) {
            return (double) callNode.call(target, receiver, order, byteOffset);
        }
        throw unsupported();
    }

    @ExportMessage
    public static void writeBufferDouble(StaticObject receiver, ByteOrder order, long byteOffset, double value,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.WriteBufferDouble)) {
            sharedCallNode.execute(dispatchId, InteropMessage.Message.WriteBufferDouble, receiver, order, byteOffset, value);
            return;
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.WriteBufferDouble);
        if (target != null) {
            callNode.call(target, receiver, order, byteOffset, value);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isPointer(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsPointer)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsPointer, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsPointer);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static long asPointer(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.AsPointer)) {
            return (long) sharedCallNode.execute(dispatchId, InteropMessage.Message.AsPointer, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.AsPointer);
        if (target != null) {
            return (long) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static void toNative(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.ToNative)) {
            sharedCallNode.execute(dispatchId, InteropMessage.Message.ToNative, receiver);
            return;
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.ToNative);
        if (target != null) {
            callNode.call(target, receiver);
        }
    }

    @ExportMessage
    public static Instant asInstant(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode,
                    @CachedLibrary("receiver") InteropLibrary lib) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.AsInstant)) {
            return (Instant) sharedCallNode.execute(dispatchId, InteropMessage.Message.AsInstant, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.AsInstant);
        if (target != null) {
            return (Instant) callNode.call(target, receiver);
        }
        if (lib.isDate(receiver) && lib.isTime(receiver) && lib.isTimeZone(receiver)) {
            LocalDate date = lib.asDate(receiver);
            LocalTime time = lib.asTime(receiver);
            ZoneId zone = lib.asTimeZone(receiver);
            return toInstant(date, time, zone);
        }
        throw unsupported();
    }

    @TruffleBoundary
    private static Instant toInstant(LocalDate date, LocalTime time, ZoneId zone) {
        return ZonedDateTime.of(date, time, zone).toInstant();
    }

    @ExportMessage
    public static boolean isTimeZone(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsTimeZone)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsTimeZone, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsTimeZone);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static ZoneId asTimeZone(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.AsTimeZone)) {
            return (ZoneId) sharedCallNode.execute(dispatchId, InteropMessage.Message.AsTimeZone, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.AsTimeZone);
        if (target != null) {
            return (ZoneId) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isDate(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsDate)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsDate, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsDate);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static LocalDate asDate(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.AsDate)) {
            return (LocalDate) sharedCallNode.execute(dispatchId, InteropMessage.Message.AsDate, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.AsDate);
        if (target != null) {
            return (LocalDate) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isTime(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsTime)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsTime, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsTime);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static LocalTime asTime(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.AsTime)) {
            return (LocalTime) sharedCallNode.execute(dispatchId, InteropMessage.Message.AsTime, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.AsTime);
        if (target != null) {
            return (LocalTime) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isDuration(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsDuration)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsDuration, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsDuration);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Duration asDuration(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.AsDuration)) {
            return (Duration) sharedCallNode.execute(dispatchId, InteropMessage.Message.AsDuration, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.AsDuration);
        if (target != null) {
            return (Duration) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isException(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsException)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsException, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsException);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static RuntimeException throwException(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.ThrowException)) {
            return (RuntimeException) sharedCallNode.execute(dispatchId, InteropMessage.Message.ThrowException, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.ThrowException);
        if (target != null) {
            throw (RuntimeException) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static ExceptionType getExceptionType(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetExceptionType)) {
            return (ExceptionType) sharedCallNode.execute(dispatchId, InteropMessage.Message.GetExceptionType, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetExceptionType);
        if (target != null) {
            return (ExceptionType) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isExceptionIncompleteSource(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsExceptionIncompleteSource)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsExceptionIncompleteSource, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsExceptionIncompleteSource);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static int getExceptionExitStatus(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetExceptionExitStatus)) {
            return (int) sharedCallNode.execute(dispatchId, InteropMessage.Message.GetExceptionExitStatus, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetExceptionExitStatus);
        if (target != null) {
            return (int) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean hasExceptionCause(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.HasExceptionCause)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.HasExceptionCause, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.HasExceptionCause);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getExceptionCause(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetExceptionCause)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.GetExceptionCause, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetExceptionCause);
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean hasExceptionMessage(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.HasExceptionMessage)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.HasExceptionMessage, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.HasExceptionMessage);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getExceptionMessage(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetExceptionMessage)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.GetExceptionMessage, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetExceptionMessage);
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @Ignore // Cannot implement getExceptionStackTrace
    public static boolean hasExceptionStackTrace(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.HasExceptionStackTrace)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.HasExceptionStackTrace, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.HasExceptionStackTrace);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @Ignore // Needs access to InteropAccessor to implement default behavior.
    public static Object getExceptionStackTrace(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetExceptionStackTrace)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.GetExceptionStackTrace, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetExceptionStackTrace);
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
        /*-
        if (InteropAccessor.EXCEPTION.isException(receiver)) {
            return InteropAccessor.EXCEPTION.getExceptionStackTrace(receiver);
        } else {
            throw UnsupportedMessageException.create();
        }
         */
    }

    @ExportMessage
    public static boolean hasIterator(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode,
                    @CachedLibrary("receiver") InteropLibrary lib) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.HasIterator)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.HasIterator, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.HasIterator);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return lib.hasArrayElements(receiver);
    }

    @ExportMessage
    public static Object getIterator(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode,
                    @CachedLibrary("receiver") InteropLibrary lib) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetIterator)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.GetIterator, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetIterator);
        if (target != null) {
            return callNode.call(target, receiver);
        }
        if (!lib.hasIterator(receiver)) {
            throw unsupported();
        }
        return new ArrayIterator(receiver);
    }

    @ExportMessage
    public static boolean isIterator(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsIterator)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsIterator, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsIterator);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean hasIteratorNextElement(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.HasIteratorNextElement)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.HasIteratorNextElement, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.HasIteratorNextElement);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getIteratorNextElement(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetIteratorNextElement)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.GetIteratorNextElement, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetIteratorNextElement);
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @Ignore // We don't expect Espresso objects to implement this.
    public static boolean hasSourceLocation(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.HasSourceLocation)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.HasSourceLocation, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.HasSourceLocation);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @Ignore // We don't expect Espresso objects to implement this.
    public static SourceSection getSourceLocation(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetSourceLocation)) {
            return (SourceSection) sharedCallNode.execute(dispatchId, InteropMessage.Message.GetSourceLocation, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetSourceLocation);
        if (target != null) {
            return (SourceSection) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean hasLanguage(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.HasLanguage)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.HasLanguage, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.HasLanguage);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @ExportMessage
    public static Class<? extends TruffleLanguage<?>> getLanguage(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetLanguage)) {
            return (Class<? extends TruffleLanguage<?>>) sharedCallNode.execute(dispatchId, InteropMessage.Message.GetLanguage, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetLanguage);
        if (target != null) {
            return (Class<? extends TruffleLanguage<?>>) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean hasMetaObject(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.HasMetaObject)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.HasMetaObject, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.HasMetaObject);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getMetaObject(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetMetaObject)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.GetMetaObject, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetMetaObject);
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static Object toDisplayString(StaticObject receiver, boolean allowSideEffects,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.ToDisplayString)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.ToDisplayString, receiver, allowSideEffects);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.ToDisplayString);
        if (target != null) {
            return callNode.call(target, receiver, allowSideEffects);
        }
        return toDisplayStringBoundary(receiver, allowSideEffects);
    }

    @ExportMessage
    public static boolean isMetaObject(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsMetaObject)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsMetaObject, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsMetaObject);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getMetaQualifiedName(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetMetaQualifiedName)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.GetMetaQualifiedName, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetMetaQualifiedName);
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static Object getMetaSimpleName(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetMetaSimpleName)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.GetMetaSimpleName, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetMetaSimpleName);
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isMetaInstance(StaticObject receiver, Object instance,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsMetaInstance)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsMetaInstance, receiver, instance);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsMetaInstance);
        if (target != null) {
            return (boolean) callNode.call(target, receiver, instance);
        }
        return false;
    }

    @ExportMessage
    public static boolean hasMetaParents(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.HasMetaParents)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.HasMetaParents, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.HasMetaParents);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getMetaParents(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetMetaParents)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.GetMetaParents, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetMetaParents);
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static TriState isIdenticalOrUndefined(StaticObject receiver, Object other,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsIdenticalOrUndefined)) {
            return (TriState) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsIdenticalOrUndefined, receiver, other);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsIdenticalOrUndefined);
        if (target != null) {
            return (TriState) callNode.call(target, receiver, other);
        }
        return TriState.UNDEFINED;
    }

    @Ignore // isIdenticalOrUndefined is protected in InteropLibrary.
    public static boolean isIdentical(StaticObject receiver, Object other, InteropLibrary otherLib,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode,
                    @CachedLibrary("receiver") InteropLibrary lib) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsIdentical)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsIdentical, receiver, other, otherLib);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsIdentical);
        if (target != null) {
            return (boolean) callNode.call(target, receiver, other, otherLib);
        }
        return false;
        /*-
        TriState result = lib.isIdenticalOrUndefined(receiver, other);
        if (result == TriState.UNDEFINED) {
            result = otherInterop.isIdenticalOrUndefined(other, receiver);
        }
        return result == TriState.TRUE;
         */
    }

    @ExportMessage
    public static int identityHashCode(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IdentityHashCode)) {
            return (int) sharedCallNode.execute(dispatchId, InteropMessage.Message.IdentityHashCode, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IdentityHashCode);
        if (target != null) {
            return (int) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isScope(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.IsScope)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.IsScope, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.IsScope);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean hasScopeParent(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.HasScopeParent)) {
            return (boolean) sharedCallNode.execute(dispatchId, InteropMessage.Message.HasScopeParent, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.HasScopeParent);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getScopeParent(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        if (InteropMessageFactory.isShareable(dispatchId, InteropMessage.Message.GetScopeParent)) {
            return sharedCallNode.execute(dispatchId, InteropMessage.Message.GetScopeParent, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.GetScopeParent);
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    // region boundary methods

    @TruffleBoundary
    private static Object toDisplayStringBoundary(Object receiver, boolean allowSideEffects) {
        if (allowSideEffects) {
            return Objects.toString(receiver);
        } else {
            return receiver.getClass().getTypeName() + "@" + Integer.toHexString(System.identityHashCode(receiver));
        }
    }
}
