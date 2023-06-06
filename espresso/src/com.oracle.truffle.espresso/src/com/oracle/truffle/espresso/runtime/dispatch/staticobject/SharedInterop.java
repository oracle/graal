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
import java.util.logging.Level;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
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
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.dispatch.messages.ArrayIterator;
import com.oracle.truffle.espresso.runtime.dispatch.messages.HashIterator;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessageFactory;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropNodes;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropNodesCollector;

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
 * @see #getTarget(StaticObject, EspressoContext, String)
 * @see InteropMessageFactory
 */
@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
@SuppressWarnings("truffle-abstract-export") // TODO GR-44080 Adopt BigInteger Interop
public class SharedInterop {
    static {
        for (InteropNodes nodes : InteropNodesCollector.getInstances(InteropNodes.class)) {
            nodes.register();
        }
    }

    @TruffleBoundary
    private static CallTarget getTarget(StaticObject receiver, EspressoContext ctx, String message) {
        assert !StaticObject.isNull(receiver);
        // Find not shared dispatch class.
        Class<?> dispatch = receiver.getKlass().getDispatch();
        ctx.getLogger().log(Level.FINER, () -> "Looking up shared target for : " + message + " for dispatch class " + dispatch.getSimpleName());
        return ctx.getLazyCaches().getInteropMessage(message, dispatch, InteropMessageFactory.getFactory(ctx.getLanguage(), dispatch, message));
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
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isNull");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean isBoolean(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isBoolean");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean asBoolean(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "asBoolean");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean isExecutable(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isExecutable");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object execute(StaticObject receiver, Object[] arguments,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "execute");
        if (target != null) {
            return callNode.call(target, receiver, arguments);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean hasExecutableName(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "hasExecutableName");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getExecutableName(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getExecutableName");
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean hasDeclaringMetaObject(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "hasDeclaringMetaObject");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getDeclaringMetaObject(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getDeclaringMetaObject");
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isInstantiable(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isInstantiable");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object instantiate(StaticObject receiver, Object[] args,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "instantiate");
        if (target != null) {
            return callNode.call(target, receiver, args);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isString(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isString");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static String asString(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "asString");
        if (target != null) {
            return (String) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static TruffleString asTruffleString(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx,
                    @CachedLibrary("receiver") InteropLibrary lib) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "asTruffleString");
        if (target != null) {
            return (TruffleString) callNode.call(target, receiver);
        }
        return TruffleString.fromJavaStringUncached(lib.asString(receiver), TruffleString.Encoding.UTF_16);
    }

    @ExportMessage
    public static boolean isNumber(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isNumber");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean fitsInByte(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "fitsInByte");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean fitsInShort(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "fitsInShort");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean fitsInInt(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "fitsInInt");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean fitsInLong(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "fitsInLong");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean fitsInFloat(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "fitsInFloat");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean fitsInDouble(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "fitsInDouble");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static byte asByte(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "asByte");
        if (target != null) {
            return (byte) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static short asShort(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "asShort");
        if (target != null) {
            return (short) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static int asInt(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "asInt");
        if (target != null) {
            return (int) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static long asLong(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "asLong");
        if (target != null) {
            return (long) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static float asFloat(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "asFloat");
        if (target != null) {
            return (float) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static double asDouble(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "asDouble");
        if (target != null) {
            return (double) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean hasMembers(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "hasMembers");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getMembers(StaticObject receiver, boolean includeInternal,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getMembers");
        if (target != null) {
            return callNode.call(target, receiver, includeInternal);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isMemberReadable(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isMemberReadable");
        if (target != null) {
            return (boolean) callNode.call(target, receiver, member);
        }
        return false;
    }

    @ExportMessage
    public static Object readMember(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "readMember");
        if (target != null) {
            return callNode.call(target, receiver, member);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isMemberModifiable(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isMemberModifiable");
        if (target != null) {
            return (boolean) callNode.call(target, receiver, member);
        }
        return false;
    }

    @ExportMessage
    public static boolean isMemberInsertable(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isMemberInsertable");
        if (target != null) {
            return (boolean) callNode.call(target, receiver, member);
        }
        return false;
    }

    @ExportMessage
    public static void writeMember(StaticObject receiver, String member, Object value,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "writeMember");
        if (target != null) {
            callNode.call(target, receiver, member, value);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isMemberRemovable(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isMemberRemovable");
        if (target != null) {
            return (boolean) callNode.call(target, receiver, member);
        }
        return false;
    }

    @ExportMessage
    public static void removeMember(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "removeMember");
        if (target != null) {
            callNode.call(target, receiver, member);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isMemberInvocable(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isMemberInvocable");
        if (target != null) {
            return (boolean) callNode.call(target, receiver, member);
        }
        return false;
    }

    @ExportMessage
    public static Object invokeMember(StaticObject receiver, String member, Object[] arguments,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "invokeMember");
        if (target != null) {
            return callNode.call(target, receiver, member, arguments);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isMemberInternal(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isMemberInternal");
        if (target != null) {
            return (boolean) callNode.call(target, receiver, member);
        }
        return false;
    }

    @ExportMessage
    public static boolean hasMemberReadSideEffects(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "hasMemberReadSideEffects");
        if (target != null) {
            return (boolean) callNode.call(target, receiver, member);
        }
        return false;
    }

    @ExportMessage
    public static boolean hasMemberWriteSideEffects(StaticObject receiver, String member,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "hasMemberWriteSideEffects");
        if (target != null) {
            return (boolean) callNode.call(target, receiver, member);
        }
        return false;
    }

    @ExportMessage
    public static boolean hasHashEntries(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "hasHashEntries");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static long getHashSize(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getHashSize");
        if (target != null) {
            return (long) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isHashEntryReadable(StaticObject receiver, Object key,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isHashEntryReadable");
        if (target != null) {
            return (boolean) callNode.call(target, receiver, key);
        }
        return false;
    }

    @ExportMessage
    public static Object readHashValue(StaticObject receiver, Object key,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "readHashValue");
        if (target != null) {
            return callNode.call(target, receiver, key);
        }
        throw unsupported();
    }

    @ExportMessage
    public static Object readHashValueOrDefault(StaticObject receiver, Object key, Object defaultValue,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx,
                    @CachedLibrary("receiver") InteropLibrary lib) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "readHashValueOrDefault");
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
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isHashEntryModifiable");
        if (target != null) {
            return (boolean) callNode.call(target, receiver, key);
        }
        return false;
    }

    @ExportMessage
    public static boolean isHashEntryInsertable(StaticObject receiver, Object key,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isHashEntryInsertable");
        if (target != null) {
            return (boolean) callNode.call(target, receiver, key);
        }
        return false;
    }

    @ExportMessage
    public static boolean isHashEntryWritable(StaticObject receiver, Object key,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx,
                    @CachedLibrary("receiver") InteropLibrary lib) {
        CallTarget target = getTarget(receiver, ctx, "isHashEntryWritable");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return lib.isHashEntryModifiable(receiver, key) || lib.isHashEntryModifiable(receiver, key);
    }

    @ExportMessage
    public static void writeHashEntry(StaticObject receiver, Object key, Object value,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "writeHashEntry");
        if (target != null) {
            callNode.call(target, receiver, key, value);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isHashEntryRemovable(StaticObject receiver, Object key,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isHashEntryRemovable");
        if (target != null) {
            return (boolean) callNode.call(target, receiver, key);
        }
        return false;
    }

    @ExportMessage
    public static void removeHashEntry(StaticObject receiver, Object key,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "removeHashEntry");
        if (target != null) {
            callNode.call(target, receiver, key);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isHashEntryExisting(StaticObject receiver, Object key,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx,
                    @CachedLibrary("receiver") InteropLibrary lib) {
        CallTarget target = getTarget(receiver, ctx, "isHashEntryExisting");
        if (target != null) {
            callNode.call(target, receiver);
        }
        return lib.isHashEntryReadable(receiver, key) || lib.isHashEntryModifiable(receiver, key);
    }

    @ExportMessage
    public static Object getHashEntriesIterator(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx,
                    @CachedLibrary("receiver") InteropLibrary lib) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getHashEntriesIterator");
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static Object getHashKeysIterator(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx,
                    @CachedLibrary("receiver") InteropLibrary lib) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getHashKeysIterator");
        if (target != null) {
            return callNode.call(target, receiver);
        }
        Object entriesIterator = lib.getHashEntriesIterator(receiver);
        return HashIterator.keys(entriesIterator);
    }

    @ExportMessage
    public static Object getHashValuesIterator(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx,
                    @CachedLibrary("receiver") InteropLibrary lib) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getHashValuesIterator");
        if (target != null) {
            return callNode.call(target, receiver);
        }
        Object entriesIterator = lib.getHashEntriesIterator(receiver);
        return HashIterator.values(entriesIterator);
    }

    @ExportMessage
    public static boolean hasArrayElements(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "hasArrayElements");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object readArrayElement(StaticObject receiver, long index,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "readArrayElement");
        if (target != null) {
            return callNode.call(target, receiver, index);
        }
        throw unsupported();
    }

    @ExportMessage
    public static long getArraySize(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getArraySize");
        if (target != null) {
            return (long) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isArrayElementReadable(StaticObject receiver, long index,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isArrayElementReadable");
        if (target != null) {
            return (boolean) callNode.call(target, receiver, index);
        }
        return false;
    }

    @ExportMessage
    public static void writeArrayElement(StaticObject receiver, long index, Object value,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "writeArrayElement");
        if (target != null) {
            callNode.call(target, receiver, index, value);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static void removeArrayElement(StaticObject receiver, long index,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "removeArrayElement");
        if (target != null) {
            callNode.call(target, receiver, index);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isArrayElementModifiable(StaticObject receiver, long index,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isArrayElementModifiable");
        if (target != null) {
            return (boolean) callNode.call(target, receiver, index);
        }
        return false;
    }

    @ExportMessage
    public static boolean isArrayElementInsertable(StaticObject receiver, long index,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isArrayElementInsertable");
        if (target != null) {
            return (boolean) callNode.call(target, receiver, index);
        }
        return false;
    }

    @ExportMessage
    public static boolean isArrayElementRemovable(StaticObject receiver, long index,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isArrayElementRemovable");
        if (target != null) {
            return (boolean) callNode.call(target, receiver, index);
        }
        return false;
    }

    @ExportMessage
    public static boolean hasBufferElements(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "hasBufferElements");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean isBufferWritable(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx,
                    @CachedLibrary("receiver") InteropLibrary lib) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "isBufferWritable");
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
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getBufferSize");
        if (target != null) {
            return (long) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static byte readBufferByte(StaticObject receiver, long byteOffset,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "readBufferByte");
        if (target != null) {
            return (byte) callNode.call(target, receiver, byteOffset);
        }
        throw unsupported();
    }

    @ExportMessage
    public static void writeBufferByte(StaticObject receiver, long byteOffset, byte value,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "writeBufferByte");
        if (target != null) {
            callNode.call(target, receiver, byteOffset, value);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static short readBufferShort(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "readBufferShort");
        if (target != null) {
            return (short) callNode.call(target, receiver, order, byteOffset);
        }
        throw unsupported();
    }

    @ExportMessage
    public static void writeBufferShort(StaticObject receiver, ByteOrder order, long byteOffset, short value,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "writeBufferShort");
        if (target != null) {
            callNode.call(target, receiver, order, byteOffset);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static int readBufferInt(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "readBufferInt");
        if (target != null) {
            return (int) callNode.call(target, receiver, order, byteOffset);
        }
        throw unsupported();
    }

    @ExportMessage
    public static void writeBufferInt(StaticObject receiver, ByteOrder order, long byteOffset, int value,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "writeBufferInt");
        if (target != null) {
            callNode.call(target, receiver, order, byteOffset, value);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static long readBufferLong(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "readBufferLong");
        if (target != null) {
            return (long) callNode.call(target, receiver, order, byteOffset);
        }
        throw unsupported();
    }

    @ExportMessage
    public static void writeBufferLong(StaticObject receiver, ByteOrder order, long byteOffset, long value,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "writeBufferLong");
        if (target != null) {
            callNode.call(target, receiver, order, byteOffset, value);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static float readBufferFloat(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "readBufferFloat");
        if (target != null) {
            return (float) callNode.call(target, receiver, order, byteOffset);
        }
        throw unsupported();
    }

    @ExportMessage
    public static void writeBufferFloat(StaticObject receiver, ByteOrder order, long byteOffset, float value,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "writeBufferFloat");
        if (target != null) {
            callNode.call(target, receiver, order, byteOffset, value);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static double readBufferDouble(StaticObject receiver, ByteOrder order, long byteOffset,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "readBufferDouble");
        if (target != null) {
            return (double) callNode.call(target, receiver, order, byteOffset);
        }
        throw unsupported();
    }

    @ExportMessage
    public static void writeBufferDouble(StaticObject receiver, ByteOrder order, long byteOffset, double value,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "writeBufferDouble");
        if (target != null) {
            callNode.call(target, receiver, order, byteOffset, value);
            return;
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isPointer(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isPointer");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static long asPointer(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "asPointer");
        if (target != null) {
            return (long) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static void toNative(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "toNative");
        if (target != null) {
            callNode.call(target, receiver);
        }
    }

    @ExportMessage
    public static Instant asInstant(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx,
                    @CachedLibrary("receiver") InteropLibrary lib) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "asInstant");
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
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isTimeZone");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static ZoneId asTimeZone(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "asTimeZone");
        if (target != null) {
            return (ZoneId) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isDate(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isDate");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static LocalDate asDate(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "asDate");
        if (target != null) {
            return (LocalDate) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isTime(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isTime");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static LocalTime asTime(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "asTime");
        if (target != null) {
            return (LocalTime) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isDuration(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isDuration");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Duration asDuration(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "asDuration");
        if (target != null) {
            return (Duration) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isException(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isException");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static RuntimeException throwException(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "throwException");
        if (target != null) {
            throw (RuntimeException) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static ExceptionType getExceptionType(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getExceptionType");
        if (target != null) {
            return (ExceptionType) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isExceptionIncompleteSource(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isExceptionIncompleteSource");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static int getExceptionExitStatus(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getExceptionExitStatus");
        if (target != null) {
            return (int) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean hasExceptionCause(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "hasExceptionCause");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getExceptionCause(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getExceptionCause");
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean hasExceptionMessage(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "hasExceptionMessage");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getExceptionMessage(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getExceptionMessage");
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @Ignore // Cannot implement getExceptionStackTrace
    public static boolean hasExceptionStackTrace(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "hasExceptionStackTrace");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @Ignore // Needs access to InteropAccessor to implement default behavior.
    public static Object getExceptionStackTrace(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getExceptionStackTrace");
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
                    @Bind("getContext(receiver)") EspressoContext ctx,
                    @CachedLibrary("receiver") InteropLibrary lib) {
        CallTarget target = getTarget(receiver, ctx, "hasIterator");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return lib.hasArrayElements(receiver);
    }

    @ExportMessage
    public static Object getIterator(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx,
                    @CachedLibrary("receiver") InteropLibrary lib) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getIterator");
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
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isIterator");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean hasIteratorNextElement(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "hasIteratorNextElement");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getIteratorNextElement(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getIteratorNextElement");
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @Ignore // We don't expect Espresso objects to implement this.
    public static boolean hasSourceLocation(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "hasSourceLocation");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @Ignore // We don't expect Espresso objects to implement this.
    public static SourceSection getSourceLocation(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getSourceLocation");
        if (target != null) {
            return (SourceSection) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean hasLanguage(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "hasLanguage");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @ExportMessage
    public static Class<? extends TruffleLanguage<?>> getLanguage(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getLanguage");
        if (target != null) {
            return (Class<? extends TruffleLanguage<?>>) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean hasMetaObject(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "hasMetaObject");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getMetaObject(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getMetaObject");
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static Object toDisplayString(StaticObject receiver, boolean allowSideEffects,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "toDisplayString");
        if (target != null) {
            return callNode.call(target, receiver, allowSideEffects);
        }
        return toDisplayStringBoundary(receiver, allowSideEffects);
    }

    @ExportMessage
    public static boolean isMetaObject(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isMetaObject");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getMetaQualifiedName(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getMetaQualifiedName");
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static Object getMetaSimpleName(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getMetaSimpleName");
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isMetaInstance(StaticObject receiver, Object instance,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isMetaInstance");
        if (target != null) {
            return (boolean) callNode.call(target, receiver, instance);
        }
        return false;
    }

    @ExportMessage
    public static boolean hasMetaParents(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "hasMetaParents");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getMetaParents(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getMetaParents");
        if (target != null) {
            return callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static TriState isIdenticalOrUndefined(StaticObject receiver, Object other,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isIdenticalOrUndefined");
        if (target != null) {
            return (TriState) callNode.call(target, receiver, other);
        }
        return TriState.UNDEFINED;
    }

    @Ignore // isIdenticalOrUndefined is protected in InteropLibrary.
    public static boolean isIdentical(StaticObject receiver, Object other, InteropLibrary otherLib,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx,
                    @CachedLibrary("receiver") InteropLibrary lib) {
        CallTarget target = getTarget(receiver, ctx, "isIdentical");
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
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "identityHashCode");
        if (target != null) {
            return (int) callNode.call(target, receiver);
        }
        throw unsupported();
    }

    @ExportMessage
    public static boolean isScope(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "isScope");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static boolean hasScopeParent(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) {
        CallTarget target = getTarget(receiver, ctx, "hasScopeParent");
        if (target != null) {
            return (boolean) callNode.call(target, receiver);
        }
        return false;
    }

    @ExportMessage
    public static Object getScopeParent(StaticObject receiver,
                    @Cached IndirectCallNode callNode,
                    @Bind("getContext(receiver)") EspressoContext ctx) throws UnsupportedMessageException {
        CallTarget target = getTarget(receiver, ctx, "getScopeParent");
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
