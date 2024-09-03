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

package com.oracle.truffle.espresso.runtime.dispatch.messages;

import java.math.BigInteger;
import java.nio.ByteOrder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.espresso.nodes.EspressoNode;

/**
 * Represents interop messages usable by {@link InteropMessageRootNode}.
 * 
 * This class should not be subclassed directly, instead, subclass its defined inner classes, that
 * already implement {@link #execute(Object[])} and {@link #name()}}, and only requires implementing
 * individual execute methods whose signature corresponds to the message they should implement.
 */
public abstract class InteropMessage extends EspressoNode {
    public enum Message {
        IsNull,
        IsBoolean,
        AsBoolean,
        IsExecutable,
        Execute,
        HasExecutableName,
        GetExecutableName,
        HasDeclaringMetaObject,
        GetDeclaringMetaObject,
        IsInstantiable,
        Instantiate,
        IsString,
        AsString,
        AsTruffleString,
        IsNumber,
        FitsInByte,
        FitsInShort,
        FitsInInt,
        FitsInLong,
        FitsInFloat,
        FitsInDouble,
        FitsInBigInteger,
        AsByte,
        AsShort,
        AsInt,
        AsLong,
        AsFloat,
        AsDouble,
        AsBigInteger,
        HasMembers,
        GetMembers,
        IsMemberReadable,
        ReadMember,
        IsMemberModifiable,
        IsMemberInsertable,
        WriteMember,
        IsMemberRemovable,
        RemoveMember,
        IsMemberInvocable,
        InvokeMember,
        IsMemberInternal,
        HasMemberReadSideEffects,
        HasMemberWriteSideEffects,
        HasHashEntries,
        GetHashSize,
        IsHashEntryReadable,
        ReadHashValue,
        ReadHashValueOrDefault,
        IsHashEntryModifiable,
        IsHashEntryInsertable,
        IsHashEntryWritable,
        WriteHashEntry,
        IsHashEntryRemovable,
        RemoveHashEntry,
        IsHashEntryExisting,
        GetHashEntriesIterator,
        GetHashKeysIterator,
        GetHashValuesIterator,
        HasArrayElements,
        ReadArrayElement,
        GetArraySize,
        IsArrayElementReadable,
        WriteArrayElement,
        RemoveArrayElement,
        IsArrayElementModifiable,
        IsArrayElementInsertable,
        IsArrayElementRemovable,
        HasBufferElements,
        IsBufferWritable,
        GetBufferSize,
        ReadBufferByte,
        ReadBuffer,
        WriteBufferByte,
        ReadBufferShort,
        WriteBufferShort,
        ReadBufferInt,
        WriteBufferInt,
        ReadBufferLong,
        WriteBufferLong,
        ReadBufferFloat,
        WriteBufferFloat,
        ReadBufferDouble,
        WriteBufferDouble,
        IsPointer,
        AsPointer,
        ToNative,
        AsInstant,
        IsTimeZone,
        AsTimeZone,
        IsDate,
        AsDate,
        IsTime,
        AsTime,
        IsDuration,
        AsDuration,
        IsException,
        ThrowException,
        GetExceptionType,
        IsExceptionIncompleteSource,
        GetExceptionExitStatus,
        HasExceptionCause,
        GetExceptionCause,
        HasExceptionMessage,
        GetExceptionMessage,
        HasExceptionStackTrace,
        GetExceptionStackTrace,
        HasIterator,
        GetIterator,
        IsIterator,
        HasIteratorNextElement,
        GetIteratorNextElement,
        HasSourceLocation,
        GetSourceLocation,
        HasLanguage,
        GetLanguage,
        HasMetaObject,
        GetMetaObject,
        ToDisplayString,
        IsMetaObject,
        GetMetaQualifiedName,
        GetMetaSimpleName,
        IsMetaInstance,
        HasMetaParents,
        GetMetaParents,
        IsIdenticalOrUndefined,
        IsIdentical,
        IdentityHashCode,
        IsScope,
        HasScopeParent,
        GetScopeParent;

        public static final int MESSAGE_COUNT = Message.values().length;
    }

    public InteropMessage() {
    }

    public abstract String name();

    public abstract Object execute(Object[] args) throws InteropException;

    @GenerateUncached(inherit = true)
    public abstract static class IsNull extends InteropMessage {
        @Override
        public final String name() {
            return "isNull";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsBoolean extends InteropMessage {
        @Override
        public final String name() {
            return "isBoolean";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class AsBoolean extends InteropMessage {
        @Override
        public final String name() {
            return "asBoolean";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsExecutable extends InteropMessage {
        @Override
        public final String name() {
            return "isExecutable";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class Execute extends InteropMessage {
        @Override
        public final String name() {
            return "execute";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            return execute(args[0], args[1]);
        }

        public abstract Object execute(Object receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class HasExecutableName extends InteropMessage {
        @Override
        public final String name() {
            return "hasExecutableName";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetExecutableName extends InteropMessage {
        @Override
        public final String name() {
            return "getExecutableName";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class HasDeclaringMetaObject extends InteropMessage {
        @Override
        public final String name() {
            return "hasDeclaringMetaObject";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetDeclaringMetaObject extends InteropMessage {
        @Override
        public final String name() {
            return "getDeclaringMetaObject";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsInstantiable extends InteropMessage {
        @Override
        public final String name() {
            return "isInstantiable";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class Instantiate extends InteropMessage {
        @Override
        public final String name() {
            return "instantiate";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof Object[];
            return execute(args[0], (Object[]) args[1]);
        }

        public abstract Object execute(Object receiver, Object[] arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsString extends InteropMessage {
        @Override
        public final String name() {
            return "isString";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class AsString extends InteropMessage {
        @Override
        public final String name() {
            return "asString";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract String execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class AsTruffleString extends InteropMessage {
        @Override
        public final String name() {
            return "asTruffleString";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract TruffleString execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsNumber extends InteropMessage {
        @Override
        public final String name() {
            return "isNumber";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class FitsInByte extends InteropMessage {
        @Override
        public final String name() {
            return "fitsInByte";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class FitsInShort extends InteropMessage {
        @Override
        public final String name() {
            return "fitsInShort";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class FitsInInt extends InteropMessage {
        @Override
        public final String name() {
            return "fitsInInt";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class FitsInLong extends InteropMessage {
        @Override
        public final String name() {
            return "fitsInLong";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class FitsInFloat extends InteropMessage {
        @Override
        public final String name() {
            return "fitsInFloat";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class FitsInDouble extends InteropMessage {
        @Override
        public final String name() {
            return "fitsInDouble";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class FitsInBigInteger extends InteropMessage {
        @Override
        public final String name() {
            return "fitsInBigInteger";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class AsByte extends InteropMessage {
        @Override
        public final String name() {
            return "asByte";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract byte execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class AsShort extends InteropMessage {
        @Override
        public final String name() {
            return "asShort";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract short execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class AsInt extends InteropMessage {
        @Override
        public final String name() {
            return "asInt";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract int execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class AsLong extends InteropMessage {
        @Override
        public final String name() {
            return "asLong";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract long execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class AsFloat extends InteropMessage {
        @Override
        public final String name() {
            return "asFloat";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract float execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class AsDouble extends InteropMessage {
        @Override
        public final String name() {
            return "asDouble";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract double execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class AsBigInteger extends InteropMessage {
        @Override
        public final String name() {
            return "asBigInteger";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract BigInteger execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class HasMembers extends InteropMessage {
        @Override
        public final String name() {
            return "hasMembers";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetMembers extends InteropMessage {
        @Override
        public final String name() {
            return "getMembers";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof Boolean;
            return execute(args[0], (boolean) args[1]);
        }

        public abstract Object execute(Object receiver, boolean includeInternal) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsMemberReadable extends InteropMessage {
        @Override
        public final String name() {
            return "isMemberReadable";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof String;
            return execute(args[0], (String) args[1]);
        }

        public abstract boolean execute(Object receiver, String member);
    }

    @GenerateUncached(inherit = true)
    public abstract static class ReadMember extends InteropMessage {
        @Override
        public final String name() {
            return "readMember";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof String;
            return execute(args[0], (String) args[1]);
        }

        public abstract Object execute(Object receiver, String member) throws UnsupportedMessageException, UnknownIdentifierException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsMemberModifiable extends InteropMessage {
        @Override
        public final String name() {
            return "isMemberModifiable";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof String;
            return execute(args[0], (String) args[1]);
        }

        public abstract boolean execute(Object receiver, String member);
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsMemberInsertable extends InteropMessage {
        @Override
        public final String name() {
            return "isMemberInsertable";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof String;
            return execute(args[0], (String) args[1]);
        }

        public abstract boolean execute(Object receiver, String member);
    }

    @GenerateUncached(inherit = true)
    public abstract static class WriteMember extends InteropMessage {
        @Override
        public final String name() {
            return "writeMember";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 3;
            assert args[1] instanceof String;
            execute(args[0], (String) args[1], args[2]);
            return null;
        }

        public abstract void execute(Object receiver, String member, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsMemberRemovable extends InteropMessage {
        @Override
        public final String name() {
            return "isMemberRemovable";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof String;
            return execute(args[0], (String) args[1]);
        }

        public abstract boolean execute(Object receiver, String member);
    }

    @GenerateUncached(inherit = true)
    public abstract static class RemoveMember extends InteropMessage {
        @Override
        public final String name() {
            return "removeMember";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof String;
            execute(args[0], (String) args[1]);
            return null;
        }

        public abstract void execute(Object receiver, String member) throws UnsupportedMessageException, UnknownIdentifierException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsMemberInvocable extends InteropMessage {
        @Override
        public final String name() {
            return "isMemberInvocable";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof String;
            return execute(args[0], (String) args[1]);
        }

        public abstract boolean execute(Object receiver, String member);
    }

    @GenerateUncached(inherit = true)
    public abstract static class InvokeMember extends InteropMessage {
        @Override
        public final String name() {
            return "invokeMember";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 3;
            assert args[1] instanceof String;
            assert args[2] instanceof Object[];
            return execute(args[0], (String) args[1], (Object[]) args[2]);
        }

        public abstract Object execute(Object receiver, String member, Object[] arguments) throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsMemberInternal extends InteropMessage {
        @Override
        public final String name() {
            return "isMemberInternal";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof String;
            return execute(args[0], (String) args[1]);
        }

        public abstract boolean execute(Object receiver, String member);
    }

    @GenerateUncached(inherit = true)
    public abstract static class HasMemberReadSideEffects extends InteropMessage {
        @Override
        public final String name() {
            return "hasMemberReadSideEffects";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof String;
            return execute(args[0], (String) args[1]);
        }

        public abstract boolean execute(Object receiver, String member);
    }

    @GenerateUncached(inherit = true)
    public abstract static class HasMemberWriteSideEffects extends InteropMessage {
        @Override
        public final String name() {
            return "hasMemberWriteSideEffects";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof String;
            return execute(args[0], (String) args[1]);
        }

        public abstract boolean execute(Object receiver, String member);
    }

    @GenerateUncached(inherit = true)
    public abstract static class HasHashEntries extends InteropMessage {
        @Override
        public final String name() {
            return "hasHashEntries";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetHashSize extends InteropMessage {
        @Override
        public final String name() {
            return "getHashSize";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract long execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsHashEntryReadable extends InteropMessage {
        @Override
        public final String name() {
            return "isHashEntryReadable";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            return execute(args[0], args[1]);
        }

        public abstract boolean execute(Object receiver, Object key);
    }

    @GenerateUncached(inherit = true)
    public abstract static class ReadHashValue extends InteropMessage {
        @Override
        public final String name() {
            return "readHashValue";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            return execute(args[0], args[1]);
        }

        public abstract Object execute(Object receiver, Object key) throws UnsupportedMessageException, UnknownKeyException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class ReadHashValueOrDefault extends InteropMessage {
        @Override
        public final String name() {
            return "readHashValueOrDefault";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 3;
            return execute(args[0], args[1], args[2]);
        }

        public abstract Object execute(Object receiver, Object key, Object defaultValue) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsHashEntryModifiable extends InteropMessage {
        @Override
        public final String name() {
            return "isHashEntryModifiable";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            return execute(args[0], args[1]);
        }

        public abstract boolean execute(Object receiver, Object key);
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsHashEntryInsertable extends InteropMessage {
        @Override
        public final String name() {
            return "isHashEntryInsertable";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            return execute(args[0], args[1]);
        }

        public abstract boolean execute(Object receiver, Object key);
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsHashEntryWritable extends InteropMessage {
        @Override
        public final String name() {
            return "isHashEntryWritable";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            return execute(args[0], args[1]);
        }

        public abstract boolean execute(Object receiver, Object key);
    }

    @GenerateUncached(inherit = true)
    public abstract static class WriteHashEntry extends InteropMessage {
        @Override
        public final String name() {
            return "writeHashEntry";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 3;
            execute(args[0], args[1], args[2]);
            return null;
        }

        public abstract void execute(Object receiver, Object key, Object value) throws UnsupportedMessageException, UnknownKeyException, UnsupportedTypeException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsHashEntryRemovable extends InteropMessage {
        @Override
        public final String name() {
            return "isHashEntryRemovable";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            return execute(args[0], args[1]);
        }

        public abstract boolean execute(Object receiver, Object key);
    }

    @GenerateUncached(inherit = true)
    public abstract static class RemoveHashEntry extends InteropMessage {
        @Override
        public final String name() {
            return "removeHashEntry";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            execute(args[0], args[1]);
            return null;
        }

        public abstract void execute(Object receiver, Object key) throws UnsupportedMessageException, UnknownKeyException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsHashEntryExisting extends InteropMessage {
        @Override
        public final String name() {
            return "isHashEntryExisting";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            return execute(args[0], args[1]);
        }

        public abstract boolean execute(Object receiver, Object key);
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetHashEntriesIterator extends InteropMessage {
        @Override
        public final String name() {
            return "getHashEntriesIterator";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetHashKeysIterator extends InteropMessage {
        @Override
        public final String name() {
            return "getHashKeysIterator";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetHashValuesIterator extends InteropMessage {
        @Override
        public final String name() {
            return "getHashValuesIterator";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class HasArrayElements extends InteropMessage {
        @Override
        public final String name() {
            return "hasArrayElements";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class ReadArrayElement extends InteropMessage {
        @Override
        public final String name() {
            return "readArrayElement";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof Long;
            return execute(args[0], (long) args[1]);
        }

        public abstract Object execute(Object receiver, long index) throws UnsupportedMessageException, InvalidArrayIndexException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetArraySize extends InteropMessage {
        @Override
        public final String name() {
            return "getArraySize";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract long execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsArrayElementReadable extends InteropMessage {
        @Override
        public final String name() {
            return "isArrayElementReadable";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof Long;
            return execute(args[0], (long) args[1]);
        }

        public abstract Object execute(Object receiver, long index);
    }

    @GenerateUncached(inherit = true)
    public abstract static class WriteArrayElement extends InteropMessage {
        @Override
        public final String name() {
            return "writeArrayElement";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 3;
            assert args[1] instanceof Long;
            execute(args[0], (long) args[1], args[2]);
            return null;
        }

        public abstract void execute(Object receiver, long index, Object value) throws UnsupportedMessageException, UnsupportedTypeException, InvalidArrayIndexException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class RemoveArrayElement extends InteropMessage {
        @Override
        public final String name() {
            return "removeArrayElement";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof Long;
            execute(args[0], (long) args[1]);
            return null;
        }

        public abstract void execute(Object receiver, long index) throws UnsupportedMessageException, InvalidArrayIndexException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsArrayElementModifiable extends InteropMessage {
        @Override
        public final String name() {
            return "isArrayElementModifiable";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof Long;
            return execute(args[0], (long) args[1]);
        }

        public abstract boolean execute(Object receiver, long index);
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsArrayElementInsertable extends InteropMessage {
        @Override
        public final String name() {
            return "isArrayElementInsertable";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof Long;
            return execute(args[0], (long) args[1]);
        }

        public abstract boolean execute(Object receiver, long index);
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsArrayElementRemovable extends InteropMessage {
        @Override
        public final String name() {
            return "isArrayElementRemovable";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof Long;
            return execute(args[0], (long) args[1]);
        }

        public abstract boolean execute(Object receiver, long index);
    }

    @GenerateUncached(inherit = true)
    public abstract static class HasBufferElements extends InteropMessage {
        @Override
        public final String name() {
            return "hasBufferElements";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsBufferWritable extends InteropMessage {
        @Override
        public final String name() {
            return "isBufferWritable";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetBufferSize extends InteropMessage {
        @Override
        public final String name() {
            return "getBufferSize";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract long execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class ReadBuffer extends InteropMessage {
        @Override
        public final String name() {
            return "readBuffer";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 5;
            assert args[1] instanceof Byte;
            assert args[2] instanceof byte[];
            assert args[3] instanceof Integer;
            assert args[4] instanceof Integer;
            execute(args[0], (byte) args[1], (byte[]) args[2], (int) args[3], (int) args[4]);
            return null;
        }

        public abstract void execute(Object receiver, long byteOffset, byte[] destination, int destinationOffset, int length) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class ReadBufferByte extends InteropMessage {
        @Override
        public final String name() {
            return "readBufferByte";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof Long;
            return execute(args[0], (long) args[1]);
        }

        public abstract byte execute(Object receiver, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class WriteBufferByte extends InteropMessage {
        @Override
        public final String name() {
            return "writeBufferByte";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 3;
            assert args[1] instanceof Long;
            assert args[2] instanceof Byte;
            execute(args[0], (long) args[1], (byte) args[2]);
            return null;
        }

        public abstract void execute(Object receiver, long byteOffset, byte value) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class ReadBufferShort extends InteropMessage {
        @Override
        public final String name() {
            return "readBufferShort";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 3;
            assert args[1] instanceof ByteOrder;
            assert args[2] instanceof Long;
            return execute(args[0], (ByteOrder) args[1], (long) args[2]);
        }

        public abstract short execute(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class WriteBufferShort extends InteropMessage {
        @Override
        public final String name() {
            return "writeBufferShort";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 4;
            assert args[1] instanceof ByteOrder;
            assert args[2] instanceof Long;
            assert args[3] instanceof Short;
            execute(args[0], (ByteOrder) args[1], (long) args[2], (short) args[3]);
            return null;
        }

        public abstract void execute(Object receiver, ByteOrder order, long byteOffset, short value) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class ReadBufferInt extends InteropMessage {
        @Override
        public final String name() {
            return "readBufferInt";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 3;
            assert args[1] instanceof ByteOrder;
            assert args[2] instanceof Long;
            return execute(args[0], (ByteOrder) args[1], (long) args[2]);
        }

        public abstract int execute(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class WriteBufferInt extends InteropMessage {
        @Override
        public final String name() {
            return "writeBufferInt";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 4;
            assert args[1] instanceof ByteOrder;
            assert args[2] instanceof Long;
            assert args[3] instanceof Integer;
            execute(args[0], (ByteOrder) args[1], (long) args[2], (int) args[3]);
            return null;
        }

        public abstract void execute(Object receiver, ByteOrder order, long byteOffset, int value) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class ReadBufferLong extends InteropMessage {
        @Override
        public final String name() {
            return "readBufferLong";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 3;
            assert args[1] instanceof ByteOrder;
            assert args[2] instanceof Long;
            return execute(args[0], (ByteOrder) args[1], (long) args[2]);
        }

        public abstract long execute(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class WriteBufferLong extends InteropMessage {
        @Override
        public final String name() {
            return "writeBufferLong";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 4;
            assert args[1] instanceof ByteOrder;
            assert args[2] instanceof Long;
            assert args[3] instanceof Long;
            execute(args[0], (ByteOrder) args[1], (long) args[2], (long) args[3]);
            return null;
        }

        public abstract void execute(Object receiver, ByteOrder order, long byteOffset, long value) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class ReadBufferFloat extends InteropMessage {
        @Override
        public final String name() {
            return "readBufferFloat";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 3;
            assert args[1] instanceof ByteOrder;
            assert args[2] instanceof Long;
            return execute(args[0], (ByteOrder) args[1], (long) args[2]);
        }

        public abstract float execute(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class WriteBufferFloat extends InteropMessage {
        @Override
        public final String name() {
            return "writeBufferFloat";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 4;
            assert args[1] instanceof ByteOrder;
            assert args[2] instanceof Long;
            assert args[3] instanceof Float;
            execute(args[0], (ByteOrder) args[1], (long) args[2], (float) args[3]);
            return null;
        }

        public abstract void execute(Object receiver, ByteOrder order, long byteOffset, float value) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class ReadBufferDouble extends InteropMessage {
        @Override
        public final String name() {
            return "readBufferDouble";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 3;
            assert args[1] instanceof ByteOrder;
            assert args[2] instanceof Long;
            return execute(args[0], (ByteOrder) args[1], (long) args[2]);
        }

        public abstract double execute(Object receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class WriteBufferDouble extends InteropMessage {
        @Override
        public final String name() {
            return "writeBufferDouble";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 4;
            assert args[1] instanceof ByteOrder;
            assert args[2] instanceof Long;
            assert args[3] instanceof Double;
            execute(args[0], (ByteOrder) args[1], (long) args[2], (short) args[3]);
            return null;
        }

        public abstract void execute(Object receiver, ByteOrder order, long byteOffset, double value) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsPointer extends InteropMessage {
        @Override
        public final String name() {
            return "isPointer";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class AsPointer extends InteropMessage {
        @Override
        public final String name() {
            return "asPointer";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract long execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class ToNative extends InteropMessage {
        @Override
        public final String name() {
            return "toNative";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            execute(args[0]);
            return null;
        }

        public abstract void execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class AsInstant extends InteropMessage {
        @Override
        public final String name() {
            return "asInstant";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Instant execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsTimeZone extends InteropMessage {
        @Override
        public final String name() {
            return "isTimeZone";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class AsTimeZone extends InteropMessage {
        @Override
        public final String name() {
            return "asTimeZone";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract ZoneId execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsDate extends InteropMessage {
        @Override
        public final String name() {
            return "isDate";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class AsDate extends InteropMessage {
        @Override
        public final String name() {
            return "asDate";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract LocalDate execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsTime extends InteropMessage {
        @Override
        public final String name() {
            return "isTime";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class AsTime extends InteropMessage {
        @Override
        public final String name() {
            return "asTime";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsDuration extends InteropMessage {
        @Override
        public final String name() {
            return "isDuration";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class AsDuration extends InteropMessage {
        @Override
        public final String name() {
            return "asDuration";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsException extends InteropMessage {
        @Override
        public final String name() {
            return "isException";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class ThrowException extends InteropMessage {
        @Override
        public final String name() {
            return "throwException";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract RuntimeException execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetExceptionType extends InteropMessage {
        @Override
        public final String name() {
            return "getExceptionType";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract ExceptionType execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsExceptionIncompleteSource extends InteropMessage {
        @Override
        public final String name() {
            return "isExceptionIncompleteSource";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetExceptionExitStatus extends InteropMessage {
        @Override
        public final String name() {
            return "getExceptionExitStatus";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract int execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class HasExceptionCause extends InteropMessage {
        @Override
        public final String name() {
            return "hasExceptionCause";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetExceptionCause extends InteropMessage {
        @Override
        public final String name() {
            return "getExceptionCause";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class HasExceptionMessage extends InteropMessage {
        @Override
        public final String name() {
            return "hasExceptionMessage";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetExceptionMessage extends InteropMessage {
        @Override
        public final String name() {
            return "getExceptionMessage";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class HasExceptionStackTrace extends InteropMessage {
        @Override
        public final String name() {
            return "hasExceptionStackTrace";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetExceptionStackTrace extends InteropMessage {
        @Override
        public final String name() {
            return "getExceptionStackTrace";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class HasIterator extends InteropMessage {
        @Override
        public final String name() {
            return "hasIterator";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetIterator extends InteropMessage {
        @Override
        public final String name() {
            return "getIterator";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsIterator extends InteropMessage {
        @Override
        public final String name() {
            return "isIterator";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class HasIteratorNextElement extends InteropMessage {
        @Override
        public final String name() {
            return "hasIteratorNextElement";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetIteratorNextElement extends InteropMessage {
        @Override
        public final String name() {
            return "getIteratorNextElement";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException, StopIterationException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class HasSourceLocation extends InteropMessage {
        @Override
        public final String name() {
            return "hasSourceLocation";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetSourceLocation extends InteropMessage {
        @Override
        public final String name() {
            return "getSourceLocation";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract SourceSection execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class HasLanguage extends InteropMessage {
        @Override
        public final String name() {
            return "hasLanguage";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetLanguage extends InteropMessage {
        @Override
        public final String name() {
            return "getLanguage";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class HasMetaObject extends InteropMessage {
        @Override
        public final String name() {
            return "hasMetaObject";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetMetaObject extends InteropMessage {
        @Override
        public final String name() {
            return "getMetaObject";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class ToDisplayString extends InteropMessage {
        @Override
        public final String name() {
            return "toDisplayString";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof Boolean;
            return execute(args[0], (boolean) args[1]);
        }

        public abstract Object execute(Object receiver, boolean allowSideEffects);
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsMetaObject extends InteropMessage {
        @Override
        public final String name() {
            return "isMetaObject";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetMetaQualifiedName extends InteropMessage {
        @Override
        public final String name() {
            return "getMetaQualifiedName";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetMetaSimpleName extends InteropMessage {
        @Override
        public final String name() {
            return "getMetaSimpleName";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsMetaInstance extends InteropMessage {
        @Override
        public final String name() {
            return "isMetaInstance";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            return execute(args[0], args[1]);
        }

        public abstract boolean execute(Object receiver, Object instance) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class HasMetaParents extends InteropMessage {
        @Override
        public final String name() {
            return "hasMetaParents";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetMetaParents extends InteropMessage {
        @Override
        public final String name() {
            return "getMetaParents";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsIdenticalOrUndefined extends InteropMessage {
        @Override
        public final String name() {
            return "isIdenticalOrUndefined";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            return execute(args[0], args[1]);
        }

        public abstract TriState execute(Object receiver, Object other);
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsIdentical extends InteropMessage {
        @Override
        public final String name() {
            return "isIdentical";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 3;
            assert args[2] instanceof InteropLibrary;
            return execute(args[0], args[1], (InteropLibrary) args[2]);
        }

        public abstract boolean execute(Object receiver, Object other, InteropLibrary otherInterop);
    }

    @GenerateUncached(inherit = true)
    public abstract static class IdentityHashCode extends InteropMessage {
        @Override
        public final String name() {
            return "identityHashCode";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public abstract static class IsScope extends InteropMessage {
        @Override
        public final String name() {
            return "isScope";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class HasScopeParent extends InteropMessage {
        @Override
        public final String name() {
            return "hasScopeParent";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract boolean execute(Object receiver);
    }

    @GenerateUncached(inherit = true)
    public abstract static class GetScopeParent extends InteropMessage {
        @Override
        public final String name() {
            return "getScopeParent";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract Object execute(Object receiver) throws UnsupportedMessageException;
    }
}
