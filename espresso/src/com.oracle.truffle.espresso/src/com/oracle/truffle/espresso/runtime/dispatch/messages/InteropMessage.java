package com.oracle.truffle.espresso.runtime.dispatch.messages;

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
    public InteropMessage() {
    }

    public abstract String name();

    public abstract Object execute(Object[] args) throws InteropException;

    @GenerateUncached(inherit = true)
    public static abstract class IsNull extends InteropMessage {
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
    public static abstract class IsBoolean extends InteropMessage {
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
    public static abstract class AsBoolean extends InteropMessage {
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
    public static abstract class IsExecutable extends InteropMessage {
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
    public static abstract class Execute extends InteropMessage {
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
    public static abstract class HasExecutableName extends InteropMessage {
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
    public static abstract class GetExecutableName extends InteropMessage {
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
    public static abstract class HasDeclaringMetaObject extends InteropMessage {
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
    public static abstract class GetDeclaringMetaObject extends InteropMessage {
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
    public static abstract class IsInstantiable extends InteropMessage {
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
    public static abstract class Instantiate extends InteropMessage {
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
    public static abstract class IsString extends InteropMessage {
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
    public static abstract class AsString extends InteropMessage {
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
    public static abstract class AsTruffleString extends InteropMessage {
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
    public static abstract class IsNumber extends InteropMessage {
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
    public static abstract class FitsInByte extends InteropMessage {
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
    public static abstract class FitsInShort extends InteropMessage {
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
    public static abstract class FitsInInt extends InteropMessage {
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
    public static abstract class FitsInLong extends InteropMessage {
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
    public static abstract class FitsInFloat extends InteropMessage {
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
    public static abstract class FitsInDouble extends InteropMessage {
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
    public static abstract class AsByte extends InteropMessage {
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
    public static abstract class AsShort extends InteropMessage {
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
    public static abstract class AsInt extends InteropMessage {
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
    public static abstract class AsLong extends InteropMessage {
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
    public static abstract class AsFloat extends InteropMessage {
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
    public static abstract class AsDouble extends InteropMessage {
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
    public static abstract class HasMembers extends InteropMessage {
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
    public static abstract class GetMembers extends InteropMessage {
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
    public static abstract class IsMemberReadable extends InteropMessage {
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
    public static abstract class ReadMember extends InteropMessage {
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
    public static abstract class IsMemberModifiable extends InteropMessage {
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
    public static abstract class IsMemberInsertable extends InteropMessage {
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
    public static abstract class WriteMember extends InteropMessage {
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
    public static abstract class IsMemberRemovable extends InteropMessage {
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
    public static abstract class RemoveMember extends InteropMessage {
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
    public static abstract class IsMemberInvocable extends InteropMessage {
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
    public static abstract class InvokeMember extends InteropMessage {
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
    public static abstract class IsMemberInternal extends InteropMessage {
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
    public static abstract class HasMemberReadSideEffects extends InteropMessage {
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
    public static abstract class HasMemberWriteSideEffects extends InteropMessage {
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
    public static abstract class HasHashEntries extends InteropMessage {
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
    public static abstract class GetHashSize extends InteropMessage {
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
    public static abstract class IsHashEntryReadable extends InteropMessage {
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
    public static abstract class ReadHashValue extends InteropMessage {
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
    public static abstract class ReadHashValueOrDefault extends InteropMessage {
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
    public static abstract class IsHashEntryModifiable extends InteropMessage {
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
    public static abstract class IsHashEntryInsertable extends InteropMessage {
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
    public static abstract class IsHashEntryWritable extends InteropMessage {
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
    public static abstract class WriteHashEntry extends InteropMessage {
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
    public static abstract class IsHashEntryRemovable extends InteropMessage {
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
    public static abstract class RemoveHashEntry extends InteropMessage {
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
    public static abstract class IsHashEntryExisting extends InteropMessage {
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
    public static abstract class GetHashEntriesIterator extends InteropMessage {
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
    public static abstract class GetHashKeysIterator extends InteropMessage {
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
    public static abstract class GetHashValuesIterator extends InteropMessage {
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
    public static abstract class HasArrayElements extends InteropMessage {
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
    public static abstract class ReadArrayElement extends InteropMessage {
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
    public static abstract class GetArraySize extends InteropMessage {
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
    public static abstract class IsArrayElementReadable extends InteropMessage {
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
    public static abstract class WriteArrayElement extends InteropMessage {
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
    public static abstract class RemoveArrayElement extends InteropMessage {
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
    public static abstract class IsArrayElementModifiable extends InteropMessage {
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
    public static abstract class IsArrayElementInsertable extends InteropMessage {
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
    public static abstract class IsArrayElementRemovable extends InteropMessage {
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
    public static abstract class HasBufferElements extends InteropMessage {
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
    public static abstract class IsBufferWritable extends InteropMessage {
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
    public static abstract class GetBufferSize extends InteropMessage {
        @Override
        public final String name() {
            return "getBufferSize";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 1;
            return execute(args[0]);
        }

        public abstract int execute(Object receiver) throws UnsupportedMessageException;
    }

    @GenerateUncached(inherit = true)
    public static abstract class ReadBufferByte extends InteropMessage {
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
    public static abstract class WriteBufferByte extends InteropMessage {
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
    public static abstract class ReadBufferShort extends InteropMessage {
        @Override
        public final String name() {
            return "readBufferShort";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof Long;
            return execute(args[0], (long) args[1]);
        }

        public abstract short execute(Object receiver, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public static abstract class WriteBufferShort extends InteropMessage {
        @Override
        public final String name() {
            return "writeBufferShort";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 3;
            assert args[1] instanceof Long;
            assert args[2] instanceof Short;
            execute(args[0], (long) args[1], (short) args[2]);
            return null;
        }

        public abstract void execute(Object receiver, long byteOffset, short value) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public static abstract class ReadBufferInt extends InteropMessage {
        @Override
        public final String name() {
            return "readBufferInt";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof Long;
            return execute(args[0], (long) args[1]);
        }

        public abstract int execute(Object receiver, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public static abstract class WriteBufferInt extends InteropMessage {
        @Override
        public final String name() {
            return "writeBufferInt";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 3;
            assert args[1] instanceof Long;
            assert args[2] instanceof Integer;
            execute(args[0], (long) args[1], (int) args[2]);
            return null;
        }

        public abstract void execute(Object receiver, long byteOffset, int value) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public static abstract class ReadBufferLong extends InteropMessage {
        @Override
        public final String name() {
            return "readBufferLong";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof Long;
            return execute(args[0], (long) args[1]);
        }

        public abstract long execute(Object receiver, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public static abstract class WriteBufferLong extends InteropMessage {
        @Override
        public final String name() {
            return "writeBufferLong";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 3;
            assert args[1] instanceof Long;
            assert args[2] instanceof Long;
            execute(args[0], (long) args[1], (long) args[2]);
            return null;
        }

        public abstract void execute(Object receiver, long byteOffset, long value) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public static abstract class ReadBufferFloat extends InteropMessage {
        @Override
        public final String name() {
            return "readBufferFloat";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof Long;
            return execute(args[0], (long) args[1]);
        }

        public abstract float execute(Object receiver, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public static abstract class WriteBufferFloat extends InteropMessage {
        @Override
        public final String name() {
            return "writeBufferFloat";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 3;
            assert args[1] instanceof Long;
            assert args[2] instanceof Float;
            execute(args[0], (long) args[1], (float) args[2]);
            return null;
        }

        public abstract void execute(Object receiver, long byteOffset, float value) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public static abstract class ReadBufferDouble extends InteropMessage {
        @Override
        public final String name() {
            return "readBufferDouble";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 2;
            assert args[1] instanceof Long;
            return execute(args[0], (long) args[1]);
        }

        public abstract double execute(Object receiver, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public static abstract class WriteBufferDouble extends InteropMessage {
        @Override
        public final String name() {
            return "writeBufferDouble";
        }

        @Override
        public final Object execute(Object[] args) throws InteropException {
            assert args.length == 3;
            assert args[1] instanceof Long;
            assert args[2] instanceof Double;
            execute(args[0], (long) args[1], (double) args[2]);
            return null;
        }

        public abstract void execute(Object receiver, long byteOffset, double value) throws UnsupportedMessageException, InvalidBufferOffsetException;
    }

    @GenerateUncached(inherit = true)
    public static abstract class IsPointer extends InteropMessage {
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
    public static abstract class AsPointer extends InteropMessage {
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
    public static abstract class ToNative extends InteropMessage {
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
    public static abstract class AsInstant extends InteropMessage {
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
    public static abstract class IsTimeZone extends InteropMessage {
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
    public static abstract class AsTimeZone extends InteropMessage {
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
    public static abstract class IsDate extends InteropMessage {
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
    public static abstract class AsDate extends InteropMessage {
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
    public static abstract class IsTime extends InteropMessage {
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
    public static abstract class AsTime extends InteropMessage {
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
    public static abstract class IsDuration extends InteropMessage {
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
    public static abstract class AsDuration extends InteropMessage {
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
    public static abstract class IsException extends InteropMessage {
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
    public static abstract class ThrowException extends InteropMessage {
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
    public static abstract class GetExceptionType extends InteropMessage {
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
    public static abstract class IsExceptionIncompleteSource extends InteropMessage {
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
    public static abstract class GetExceptionExitStatus extends InteropMessage {
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
    public static abstract class HasExceptionCause extends InteropMessage {
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
    public static abstract class GetExceptionCause extends InteropMessage {
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
    public static abstract class HasExceptionMessage extends InteropMessage {
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
    public static abstract class GetExceptionMessage extends InteropMessage {
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
    public static abstract class HasExceptionStackTrace extends InteropMessage {
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
    public static abstract class GetExceptionStackTrace extends InteropMessage {
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
    public static abstract class HasIterator extends InteropMessage {
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
    public static abstract class GetIterator extends InteropMessage {
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
    public static abstract class IsIterator extends InteropMessage {
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
    public static abstract class HasIteratorNextElement extends InteropMessage {
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
    public static abstract class GetIteratorNextElement extends InteropMessage {
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
    public static abstract class HasSourceLocation extends InteropMessage {
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
    public static abstract class GetSourceLocation extends InteropMessage {
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
    public static abstract class HasLanguage extends InteropMessage {
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
    public static abstract class GetLanguage extends InteropMessage {
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
    public static abstract class HasMetaObject extends InteropMessage {
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
    public static abstract class GetMetaObject extends InteropMessage {
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
    public static abstract class ToDisplayString extends InteropMessage {
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
    public static abstract class IsMetaObject extends InteropMessage {
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
    public static abstract class GetMetaQualifiedName extends InteropMessage {
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
    public static abstract class GetMetaSimpleName extends InteropMessage {
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
    public static abstract class IsMetaInstance extends InteropMessage {
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
    public static abstract class HasMetaParents extends InteropMessage {
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
    public static abstract class GetMetaParents extends InteropMessage {
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
    public static abstract class IsIdenticalOrUndefined extends InteropMessage {
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
    public static abstract class IsIdentical extends InteropMessage {
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
    public static abstract class IdentityHashCode extends InteropMessage {
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
    public static abstract class IsScope extends InteropMessage {
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
    public static abstract class HasScopeParent extends InteropMessage {
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
    public static abstract class GetScopeParent extends InteropMessage {
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
