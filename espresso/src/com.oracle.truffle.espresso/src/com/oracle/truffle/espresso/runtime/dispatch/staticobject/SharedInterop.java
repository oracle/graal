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

import java.math.BigInteger;
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
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
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
import com.oracle.truffle.espresso.runtime.dispatch.messages.ArrayIterator;
import com.oracle.truffle.espresso.runtime.dispatch.messages.HashIterator;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessage;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessageFactories;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

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
 * @see InteropMessageFactories
 */
@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
public class SharedInterop {
    @GenerateUncached
    abstract static class CallSharedInteropMessage extends EspressoNode {
        public final Object call(int sourceDispatchId, InteropMessage.Message message, Object... args) {
            return execute(sourceDispatchId, message, args);
        }

        protected abstract Object execute(int sourceDispatchId, InteropMessage.Message message, Object[] args);

        static final int LIMIT = InteropKlassesDispatch.DISPATCH_TOTAL;

        @SuppressWarnings("unused")
        @Specialization(guards = {"sourceDispatchId == cachedId"}, limit = "LIMIT")
        Object doCached(int sourceDispatchId, InteropMessage.Message message, Object[] args,
                        @Cached("sourceDispatchId") int cachedId,
                        @Cached(value = "create(getTarget(getContext(), sourceDispatchId, message))", allowUncached = true) DirectCallNode call) {
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
        InteropMessage.Message message = InteropMessage.Message.IsNull;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.IsBoolean;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.AsBoolean;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.IsExecutable;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        InteropMessage.Message message = InteropMessage.Message.Execute;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver, arguments);
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
        InteropMessage.Message message = InteropMessage.Message.HasExecutableName;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetExecutableName;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.HasDeclaringMetaObject;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetDeclaringMetaObject;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.IsInstantiable;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        InteropMessage.Message message = InteropMessage.Message.Instantiate;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver, args);
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
        InteropMessage.Message message = InteropMessage.Message.IsString;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.AsString;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (String) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.AsTruffleString;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (TruffleString) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.IsNumber;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.FitsInByte;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.FitsInShort;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.FitsInInt;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.FitsInLong;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.FitsInFloat;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.FitsInDouble;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.FitsInDouble);
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean fitsInBigInteger(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        InteropMessage.Message message = InteropMessage.Message.FitsInBigInteger;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.FitsInBigInteger);
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
        InteropMessage.Message message = InteropMessage.Message.AsByte;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (byte) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.AsShort;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (short) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.AsInt;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (int) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.AsLong;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (long) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.AsFloat;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (float) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.AsDouble;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (double) sharedCallNode.call(dispatchId, message, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.AsDouble);
        if (target != null) {
            return (double) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static BigInteger asBigInteger(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        InteropMessage.Message message = InteropMessage.Message.AsDouble;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (BigInteger) sharedCallNode.call(dispatchId, message, receiver);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.AsDouble);
        if (target != null) {
            return (BigInteger) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean hasMembers(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        InteropMessage.Message message = InteropMessage.Message.HasMembers;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetMembers;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver, includeInternal);
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
        InteropMessage.Message message = InteropMessage.Message.IsMemberReadable;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, member);
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
        InteropMessage.Message message = InteropMessage.Message.ReadMember;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver, member);
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
        InteropMessage.Message message = InteropMessage.Message.IsMemberModifiable;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, member);
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
        InteropMessage.Message message = InteropMessage.Message.IsMemberInsertable;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, member);
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
        InteropMessage.Message message = InteropMessage.Message.WriteMember;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            sharedCallNode.call(dispatchId, message, receiver, member, value);
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
        InteropMessage.Message message = InteropMessage.Message.IsMemberRemovable;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, member);
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
        InteropMessage.Message message = InteropMessage.Message.RemoveMember;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            sharedCallNode.call(dispatchId, message, receiver, member);
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
        InteropMessage.Message message = InteropMessage.Message.IsMemberInvocable;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, member);
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
        InteropMessage.Message message = InteropMessage.Message.InvokeMember;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver, member, arguments);
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
        InteropMessage.Message message = InteropMessage.Message.IsMemberInternal;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, member);
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
        InteropMessage.Message message = InteropMessage.Message.HasMemberReadSideEffects;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, member);
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
        InteropMessage.Message message = InteropMessage.Message.HasMemberWriteSideEffects;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, member);
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
        InteropMessage.Message message = InteropMessage.Message.HasHashEntries;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetHashSize;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (long) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.IsHashEntryReadable;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, key);
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
        InteropMessage.Message message = InteropMessage.Message.ReadHashValue;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver, key);
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
        InteropMessage.Message message = InteropMessage.Message.ReadHashValueOrDefault;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver, key);
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
        InteropMessage.Message message = InteropMessage.Message.IsHashEntryModifiable;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, key);
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
        InteropMessage.Message message = InteropMessage.Message.IsHashEntryInsertable;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, key);
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
        InteropMessage.Message message = InteropMessage.Message.IsHashEntryWritable;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.WriteHashEntry;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            sharedCallNode.call(dispatchId, message, receiver, key, value);
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
        InteropMessage.Message message = InteropMessage.Message.IsHashEntryRemovable;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, key);
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
        InteropMessage.Message message = InteropMessage.Message.RemoveHashEntry;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            sharedCallNode.call(dispatchId, message, receiver, key);
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
        InteropMessage.Message message = InteropMessage.Message.IsHashEntryExisting;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, key);
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
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        InteropMessage.Message message = InteropMessage.Message.GetHashEntriesIterator;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetHashKeysIterator;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetHashValuesIterator;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.HasArrayElements;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.ReadArrayElement;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver, index);
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
        InteropMessage.Message message = InteropMessage.Message.GetArraySize;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (long) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.IsArrayElementReadable;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, index);
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
        InteropMessage.Message message = InteropMessage.Message.WriteArrayElement;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            sharedCallNode.call(dispatchId, message, receiver, index, value);
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
        InteropMessage.Message message = InteropMessage.Message.RemoveArrayElement;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            sharedCallNode.call(dispatchId, message, receiver, index);
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
        InteropMessage.Message message = InteropMessage.Message.IsArrayElementModifiable;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, index);
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
        InteropMessage.Message message = InteropMessage.Message.IsArrayElementInsertable;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, index);
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
        InteropMessage.Message message = InteropMessage.Message.IsArrayElementRemovable;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, index);
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
        InteropMessage.Message message = InteropMessage.Message.HasBufferElements;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.IsBufferWritable;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetBufferSize;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (long) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.ReadBufferByte;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (byte) sharedCallNode.call(dispatchId, message, receiver, byteOffset);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.ReadBufferByte);
        if (target != null) {
            return (byte) callNode.call(target, receiver, byteOffset);
        }
        throw unsupported();
    }

    @ExportMessage
    public static void readBuffer(StaticObject receiver, long byteOffset, byte[] destination, int destinationOffset, int length,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        InteropMessage.Message message = InteropMessage.Message.ReadBuffer;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            sharedCallNode.call(dispatchId, message, receiver, byteOffset, destination, destinationOffset, length);
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.ReadBuffer);
        if (target != null) {
            callNode.call(target, receiver, byteOffset, destination, destinationOffset, length);
        }
        throw unsupported();
    }

    @ExportMessage
    public static void writeBufferByte(StaticObject receiver, long byteOffset, byte value,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        InteropMessage.Message message = InteropMessage.Message.WriteBufferByte;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            sharedCallNode.call(dispatchId, message, receiver, byteOffset, value);
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
        InteropMessage.Message message = InteropMessage.Message.ReadBufferShort;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (short) sharedCallNode.call(dispatchId, message, receiver, order, byteOffset);
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
        InteropMessage.Message message = InteropMessage.Message.WriteBufferShort;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            sharedCallNode.call(dispatchId, message, receiver, order, byteOffset, value);
            return;
        }
        CallTarget target = getTarget(receiver, InteropMessage.Message.WriteBufferShort);
        if (target != null) {
            callNode.call(target, receiver, order, byteOffset, value);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static int readBufferInt(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Cached IndirectCallNode callNode,
                    @Cached CallSharedInteropMessage sharedCallNode) throws UnsupportedMessageException {
        int dispatchId = receiver.getKlass().getDispatchId();
        InteropMessage.Message message = InteropMessage.Message.ReadBufferInt;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (int) sharedCallNode.call(dispatchId, message, receiver, order, byteOffset);
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
        InteropMessage.Message message = InteropMessage.Message.WriteBufferInt;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            sharedCallNode.call(dispatchId, message, receiver, order, byteOffset, value);
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
        InteropMessage.Message message = InteropMessage.Message.ReadBufferLong;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (long) sharedCallNode.call(dispatchId, message, receiver, order, byteOffset);
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
        InteropMessage.Message message = InteropMessage.Message.WriteBufferLong;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            sharedCallNode.call(dispatchId, message, receiver, order, byteOffset, value);
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
        InteropMessage.Message message = InteropMessage.Message.ReadBufferFloat;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (float) sharedCallNode.call(dispatchId, message, receiver, order, byteOffset);
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
        InteropMessage.Message message = InteropMessage.Message.WriteBufferFloat;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            sharedCallNode.call(dispatchId, message, receiver, order, byteOffset, value);
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
        InteropMessage.Message message = InteropMessage.Message.ReadBufferDouble;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (double) sharedCallNode.call(dispatchId, message, receiver, order, byteOffset);
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
        InteropMessage.Message message = InteropMessage.Message.WriteBufferDouble;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            sharedCallNode.call(dispatchId, message, receiver, order, byteOffset, value);
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
        InteropMessage.Message message = InteropMessage.Message.IsPointer;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.AsPointer;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (long) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.ToNative;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.AsInstant;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (Instant) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.IsTimeZone;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.AsTimeZone;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (ZoneId) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.IsDate;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.AsDate;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (LocalDate) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.IsTime;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.AsTime;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (LocalTime) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.IsDuration;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.AsDuration;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (Duration) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.IsException;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.ThrowException;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (RuntimeException) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetExceptionType;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (ExceptionType) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.IsExceptionIncompleteSource;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetExceptionExitStatus;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (int) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.HasExceptionCause;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetExceptionCause;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.HasExceptionMessage;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetExceptionMessage;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.HasExceptionStackTrace;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetExceptionStackTrace;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.HasIterator;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetIterator;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.IsIterator;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.HasIteratorNextElement;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetIteratorNextElement;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.HasSourceLocation;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetSourceLocation;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (SourceSection) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.HasLanguage;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetLanguage;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (Class<? extends TruffleLanguage<?>>) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.HasMetaObject;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetMetaObject;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.ToDisplayString;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver, allowSideEffects);
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
        InteropMessage.Message message = InteropMessage.Message.IsMetaObject;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetMetaQualifiedName;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetMetaSimpleName;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.IsMetaInstance;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, instance);
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
        InteropMessage.Message message = InteropMessage.Message.HasMetaParents;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetMetaParents;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.IsIdenticalOrUndefined;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (TriState) sharedCallNode.call(dispatchId, message, receiver, other);
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
                    @Cached CallSharedInteropMessage sharedCallNode) {
        int dispatchId = receiver.getKlass().getDispatchId();
        InteropMessage.Message message = InteropMessage.Message.IsIdentical;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver, other, otherLib);
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
        InteropMessage.Message message = InteropMessage.Message.IdentityHashCode;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (int) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.IsScope;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.HasScopeParent;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return (boolean) sharedCallNode.call(dispatchId, message, receiver);
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
        InteropMessage.Message message = InteropMessage.Message.GetScopeParent;
        if (InteropMessageFactories.isShareable(dispatchId, message)) {
            dispatchId = InteropMessageFactories.sourceDispatch(dispatchId, message);
            return sharedCallNode.call(dispatchId, message, receiver);
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
