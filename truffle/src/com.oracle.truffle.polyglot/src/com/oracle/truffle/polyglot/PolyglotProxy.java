/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.polyglot.GuestToHostRootNode.createGuestToHost;
import static com.oracle.truffle.polyglot.GuestToHostRootNode.guestToHostCall;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyDate;
import org.graalvm.polyglot.proxy.ProxyDuration;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyInstant;
import org.graalvm.polyglot.proxy.ProxyInstantiable;
import org.graalvm.polyglot.proxy.ProxyNativeObject;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.polyglot.proxy.ProxyTime;
import org.graalvm.polyglot.proxy.ProxyTimeZone;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@SuppressWarnings("deprecation")
@ExportLibrary(InteropLibrary.class)
final class PolyglotProxy implements TruffleObject {

    static final int LIMIT = 5;

    private static final ProxyArray EMPTY = new ProxyArray() {

        public void set(long index, Value value) {
            throw new ArrayIndexOutOfBoundsException();
        }

        public long getSize() {
            return 0;
        }

        public Object get(long index) {
            throw new ArrayIndexOutOfBoundsException();
        }
    };
    final PolyglotLanguageContext languageContext;
    final Proxy proxy;

    PolyglotProxy(PolyglotLanguageContext context, Proxy proxy) {
        this.languageContext = context;
        this.proxy = proxy;
    }

    @ExportMessage
    boolean isInstantiable() {
        return proxy instanceof ProxyInstantiable;
    }

    static class InstantiateNode extends GuestToHostRootNode {

        protected InstantiateNode() {
            super(ProxyInstantiable.class, "newInstance");
        }

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) throws UnsupportedMessageException {
            try {
                return ((ProxyInstantiable) proxy).newInstance((Value[]) arguments[ARGUMENT_OFFSET]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }
    }

    private static final CallTarget INSTANTIATE = createGuestToHost(new InstantiateNode());

    @ExportMessage
    @TruffleBoundary
    Object instantiate(Object[] arguments, @CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyInstantiable) {
            Value[] convertedArguments = languageContext.toHostValues(arguments, 0);
            Object result = guestToHostCall(library, INSTANTIATE, languageContext, proxy, convertedArguments);
            return languageContext.toGuestValue(result);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isExecutable() {
        return proxy instanceof ProxyExecutable;
    }

    static class ExecuteNode extends GuestToHostRootNode {

        protected ExecuteNode() {
            super(ProxyExecutable.class, "execute");
        }

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) throws UnsupportedMessageException {
            try {
                return ((ProxyExecutable) proxy).execute((Value[]) arguments[ARGUMENT_OFFSET]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }
    }

    private static final CallTarget EXECUTE = createGuestToHost(new ExecuteNode());

    @ExportMessage
    Object execute(Object[] arguments, @CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyExecutable) {
            Value[] convertedArguments = languageContext.toHostValues(arguments, 0);
            Object result = guestToHostCall(library, EXECUTE, languageContext, proxy, convertedArguments);
            return languageContext.toGuestValue(result);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isPointer() {
        return proxy instanceof ProxyNativeObject;
    }

    static class AsPointerNode extends GuestToHostRootNode {

        protected AsPointerNode() {
            super(ProxyNativeObject.class, "asPointer");
        }

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return ((ProxyNativeObject) proxy).asPointer();
        }
    }

    private static final CallTarget AS_POINTER = createGuestToHost(new AsPointerNode());

    @ExportMessage
    @TruffleBoundary
    long asPointer(@CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyNativeObject) {
            return (long) guestToHostCall(library, AS_POINTER, languageContext, proxy);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean hasArrayElements() {
        return proxy instanceof ProxyArray;
    }

    static class ArrayGetNode extends GuestToHostRootNode {

        protected ArrayGetNode() {
            super(ProxyArray.class, "get");
        }

        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws InvalidArrayIndexException, UnsupportedMessageException {
            long index = (long) arguments[ARGUMENT_OFFSET];
            try {
                return boundaryGet((ProxyArray) proxy, index);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw InvalidArrayIndexException.create(index);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }

        @TruffleBoundary
        private static Object boundaryGet(ProxyArray proxy, long index) {
            return proxy.get(index);
        }
    }

    private static final CallTarget ARRAY_GET = createGuestToHost(new ArrayGetNode());

    @ExportMessage
    @TruffleBoundary
    Object readArrayElement(long index, @CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyArray) {
            Object result = guestToHostCall(library, ARRAY_GET, languageContext, proxy, index);
            return languageContext.toGuestValue(result);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    static class ArraySetNode extends GuestToHostRootNode {

        protected ArraySetNode() {
            super(ProxyArray.class, "set");
        }

        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws InvalidArrayIndexException, UnsupportedMessageException {
            long index = (long) arguments[ARGUMENT_OFFSET];
            try {
                boundarySet((ProxyArray) proxy, index, (Value) arguments[ARGUMENT_OFFSET + 1]);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw InvalidArrayIndexException.create(index);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
            return null;
        }

        @TruffleBoundary
        private static void boundarySet(ProxyArray proxy, long index, Value value) {
            proxy.set(index, value);
        }
    }

    private static final CallTarget ARRAY_SET = createGuestToHost(new ArraySetNode());

    @ExportMessage
    @TruffleBoundary
    void writeArrayElement(long index, Object value, @CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyArray) {
            Value castValue = languageContext.asValue(value);
            guestToHostCall(library, ARRAY_SET, languageContext, proxy, index, castValue);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    static class ArrayRemoveNode extends GuestToHostRootNode {

        protected ArrayRemoveNode() {
            super(ProxyArray.class, "remove");
        }

        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws InvalidArrayIndexException, UnsupportedMessageException {
            long index = (long) arguments[ARGUMENT_OFFSET];
            try {
                return boundaryRemove((ProxyArray) proxy, index);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw InvalidArrayIndexException.create(index);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }

        @TruffleBoundary
        private static boolean boundaryRemove(ProxyArray proxy, long index) {
            return proxy.remove(index);
        }
    }

    private static final CallTarget ARRAY_REMOVE = createGuestToHost(new ArrayRemoveNode());

    @ExportMessage
    @TruffleBoundary
    void removeArrayElement(long index, @CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException, InvalidArrayIndexException {
        if (proxy instanceof ProxyArray) {
            boolean result = (boolean) guestToHostCall(library, ARRAY_REMOVE, languageContext, proxy, index);
            if (!result) {
                throw InvalidArrayIndexException.create(index);
            }
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    static class ArraySizeNode extends GuestToHostRootNode {

        protected ArraySizeNode() {
            super(ProxyArray.class, "getSize");
        }

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return ((ProxyArray) proxy).getSize();
        }
    }

    private static final CallTarget ARRAY_SIZE = createGuestToHost(new ArraySizeNode());

    @ExportMessage
    @TruffleBoundary
    long getArraySize(@CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyArray) {
            return (long) guestToHostCall(library, ARRAY_SIZE, languageContext, proxy);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage(name = "isArrayElementReadable")
    @ExportMessage(name = "isArrayElementModifiable")
    @ExportMessage(name = "isArrayElementRemovable")
    @TruffleBoundary
    boolean isArrayElementExisting(long index, @CachedLibrary("this") InteropLibrary library) {
        if (proxy instanceof ProxyArray) {
            long size = (long) guestToHostCall(library, ARRAY_SIZE, languageContext, proxy);
            return index >= 0 && index < size;
        } else {
            return false;
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean isArrayElementInsertable(long index, @CachedLibrary("this") InteropLibrary library) {
        if (proxy instanceof ProxyArray) {
            long size = (long) guestToHostCall(library, ARRAY_SIZE, languageContext, proxy);
            return index < 0 || index >= size;
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean hasMembers() {
        return proxy instanceof ProxyObject;
    }

    static class GetMemberKeysNode extends GuestToHostRootNode {

        protected GetMemberKeysNode() {
            super(ProxyObject.class, "getMemberKeys");
        }

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return ((ProxyObject) proxy).getMemberKeys();
        }
    }

    private static final CallTarget MEMBER_KEYS = createGuestToHost(new GetMemberKeysNode());

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal, @CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyObject) {
            Object result = guestToHostCall(library, MEMBER_KEYS, languageContext, proxy);
            if (result == null) {
                result = EMPTY;
            }
            Object guestValue = languageContext.toGuestValue(result);
            if (!InteropLibrary.getFactory().getUncached().hasArrayElements(guestValue)) {
                throw illegalProxy("getMemberKeys() returned invalid value %s but must return an array of member key Strings.",
                                languageContext.asValue(guestValue).toString());
            }
            return guestValue;
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary
    RuntimeException illegalProxy(String message, Object... parameters) {
        throw PolyglotImpl.wrapHostException(languageContext, new IllegalStateException(
                        String.format(message, parameters)));
    }

    static class GetMemberNode extends GuestToHostRootNode {

        protected GetMemberNode() {
            super(ProxyObject.class, "getMember");
        }

        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws UnsupportedMessageException {
            try {
                return boundaryGetMember((ProxyObject) proxy, (String) arguments[ARGUMENT_OFFSET]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }

        @TruffleBoundary
        private static Object boundaryGetMember(ProxyObject proxy, String argument) {
            return proxy.getMember(argument);
        }
    }

    private static final CallTarget GET_MEMBER = createGuestToHost(new GetMemberNode());

    @ExportMessage
    @TruffleBoundary
    Object readMember(String member, @CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException, UnknownIdentifierException {
        if (proxy instanceof ProxyObject) {
            if (!isMemberExisting(member, library)) {
                throw UnknownIdentifierException.create(member);
            }
            Object result = guestToHostCall(library, GET_MEMBER, languageContext, proxy, member);
            return languageContext.toGuestValue(result);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    static class PutMemberNode extends GuestToHostRootNode {

        protected PutMemberNode() {
            super(ProxyObject.class, "putMember");
        }

        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws UnsupportedMessageException {
            try {
                boundaryPutMember((ProxyObject) proxy, (String) arguments[ARGUMENT_OFFSET], (Value) arguments[ARGUMENT_OFFSET + 1]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
            return null;
        }

        @TruffleBoundary
        private static void boundaryPutMember(ProxyObject proxy, String member, Value value) {
            proxy.putMember(member, value);
        }
    }

    private static final CallTarget PUT_MEMBER = createGuestToHost(new PutMemberNode());

    @ExportMessage
    @TruffleBoundary
    void writeMember(String member, Object value, @CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyObject) {
            Value castValue = languageContext.asValue(value);
            guestToHostCall(library, PUT_MEMBER, languageContext, proxy, member, castValue);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    Object invokeMember(String member, Object[] arguments, @CachedLibrary("this") InteropLibrary library,
                    @Shared("executables") @CachedLibrary(limit = "LIMIT") InteropLibrary executables)
                    throws UnsupportedMessageException, UnsupportedTypeException, ArityException, UnknownIdentifierException {
        if (proxy instanceof ProxyObject) {
            if (!isMemberExisting(member, library)) {
                throw UnknownIdentifierException.create(member);
            }
            Object memberObject;
            try {
                memberObject = readMember(member, library);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
            memberObject = languageContext.toGuestValue(memberObject);
            if (executables.isExecutable(memberObject)) {
                return executables.execute(memberObject, arguments);
            } else {
                throw UnsupportedMessageException.create();
            }
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberInvocable(String member, @CachedLibrary("this") InteropLibrary library,
                    @Shared("executables") @CachedLibrary(limit = "LIMIT") InteropLibrary executables) {
        if (proxy instanceof ProxyObject) {
            if (isMemberExisting(member, library)) {
                try {
                    return executables.isExecutable(readMember(member, library));
                } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                    return false;
                }
            }
        }
        return false;
    }

    static class RemoveMemberNode extends GuestToHostRootNode {

        protected RemoveMemberNode() {
            super(ProxyObject.class, "removeMember");
        }

        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws UnsupportedMessageException {
            try {
                return removeBoundary((ProxyObject) proxy, (String) arguments[ARGUMENT_OFFSET]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }

        @TruffleBoundary
        private static boolean removeBoundary(ProxyObject proxy, String member) {
            return proxy.removeMember(member);
        }
    }

    private static final CallTarget REMOVE_MEMBER = createGuestToHost(new RemoveMemberNode());

    @ExportMessage
    @TruffleBoundary
    void removeMember(String member, @CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException, UnknownIdentifierException {
        if (proxy instanceof ProxyObject) {
            if (!isMemberExisting(member, library)) {
                throw UnknownIdentifierException.create(member);
            }
            boolean result = (boolean) guestToHostCall(library, REMOVE_MEMBER, languageContext, proxy, member);
            if (!result) {
                throw UnknownIdentifierException.create(member);
            }
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    static class HasMemberNode extends GuestToHostRootNode {

        protected HasMemberNode() {
            super(ProxyObject.class, "hasMember");
        }

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return ((ProxyObject) proxy).hasMember((String) arguments[ARGUMENT_OFFSET]);
        }
    }

    private static final CallTarget HAS_MEMBER = createGuestToHost(new HasMemberNode());

    @ExportMessage(name = "isMemberReadable")
    @ExportMessage(name = "isMemberModifiable")
    @ExportMessage(name = "isMemberRemovable")
    @TruffleBoundary
    boolean isMemberExisting(String member, @CachedLibrary("this") InteropLibrary library) {
        if (proxy instanceof ProxyObject) {
            return (boolean) guestToHostCall(library, HAS_MEMBER, languageContext, proxy, member);
        } else {
            return false;
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberInsertable(String member, @CachedLibrary("this") InteropLibrary library) {
        if (proxy instanceof ProxyObject) {
            return !isMemberExisting(member, library);
        } else {
            return false;
        }
    }

    @TruffleBoundary
    @ExportMessage
    boolean isDate() {
        return proxy instanceof ProxyDate;
    }

    @TruffleBoundary
    @ExportMessage
    boolean isTime() {
        return proxy instanceof ProxyTime;
    }

    @TruffleBoundary
    @ExportMessage
    boolean isTimeZone() {
        return proxy instanceof ProxyTimeZone;
    }

    static class AsTimeZoneNode extends GuestToHostRootNode {

        protected AsTimeZoneNode() {
            super(ProxyTimeZone.class, "asTimeZone");
        }

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            ZoneId zone = ((ProxyTimeZone) proxy).asTimeZone();
            if (zone == null) {
                throw new AssertionError("The returned zone must not be null.");
            }
            return zone;
        }
    }

    private static final CallTarget AS_TIMEZONE = createGuestToHost(new AsTimeZoneNode());

    @ExportMessage
    @TruffleBoundary
    ZoneId asTimeZone(@CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyTimeZone) {
            return (ZoneId) guestToHostCall(library, AS_TIMEZONE, languageContext, proxy);
        }
        throw UnsupportedMessageException.create();
    }

    static class AsDateNode extends GuestToHostRootNode {

        protected AsDateNode() {
            super(ProxyDate.class, "asDate");
        }

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            LocalDate date = ((ProxyDate) proxy).asDate();
            if (date == null) {
                throw new AssertionError("The returned date must not be null.");
            }
            return date;
        }
    }

    private static final CallTarget AS_DATE = createGuestToHost(new AsDateNode());

    @ExportMessage
    @TruffleBoundary
    LocalDate asDate(@CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyDate) {
            return (LocalDate) guestToHostCall(library, AS_DATE, languageContext, proxy);
        }
        throw UnsupportedMessageException.create();
    }

    static class AsTimeNode extends GuestToHostRootNode {

        protected AsTimeNode() {
            super(ProxyTime.class, "asTime");
        }

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            LocalTime time = ((ProxyTime) proxy).asTime();
            if (time == null) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("The returned time must not be null.");
            }
            return time;
        }
    }

    private static final CallTarget AS_TIME = createGuestToHost(new AsTimeNode());

    @ExportMessage
    @TruffleBoundary
    LocalTime asTime(@CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyTime) {
            return (LocalTime) guestToHostCall(library, AS_TIME, languageContext, proxy);
        }
        throw UnsupportedMessageException.create();
    }

    static class AsInstantNode extends GuestToHostRootNode {

        protected AsInstantNode() {
            super(ProxyInstant.class, "asInstant");
        }

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            Instant instant = ((ProxyInstant) proxy).asInstant();
            if (instant == null) {
                throw new AssertionError("The returned instant must not be null.");
            }
            return instant;
        }
    }

    private static final CallTarget AS_INSTANT = createGuestToHost(new AsInstantNode());

    @TruffleBoundary
    @ExportMessage
    Instant asInstant(@CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyInstant) {
            return (Instant) guestToHostCall(library, AS_INSTANT, languageContext, proxy);
        } else if (isDate() && isTime() && isTimeZone()) {
            return ZonedDateTime.of(asDate(library), asTime(library), asTimeZone(library)).toInstant();
        }
        throw UnsupportedMessageException.create();
    }

    @TruffleBoundary
    @ExportMessage
    boolean isDuration() {
        return proxy instanceof ProxyDuration;
    }

    static class AsDurationNode extends GuestToHostRootNode {

        protected AsDurationNode() {
            super(ProxyDuration.class, "asDuration");
        }

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            Duration duration = ((ProxyDuration) proxy).asDuration();
            if (duration == null) {
                throw new AssertionError("The returned duration must not be null.");
            }
            return duration;
        }
    }

    private static final CallTarget AS_DURATION = createGuestToHost(new AsDurationNode());

    @ExportMessage
    @TruffleBoundary
    Duration asDuration(@CachedLibrary("this") InteropLibrary library) throws UnsupportedMessageException {
        if (proxy instanceof ProxyDuration) {
            return (Duration) guestToHostCall(library, AS_DURATION, languageContext, proxy);
        }
        throw UnsupportedMessageException.create();
    }

    public static boolean isProxyGuestObject(TruffleObject value) {
        return value instanceof PolyglotProxy;
    }

    public static boolean isProxyGuestObject(Object value) {
        return value instanceof PolyglotProxy;
    }

    public static Object withContext(PolyglotLanguageContext context, Object valueReceiver) {
        return new PolyglotProxy(context, ((PolyglotProxy) valueReceiver).proxy);
    }

    public static Proxy toProxyHostObject(TruffleObject value) {
        return ((PolyglotProxy) value).proxy;
    }

    public static TruffleObject toProxyGuestObject(PolyglotLanguageContext context, Proxy receiver) {
        return new PolyglotProxy(context, receiver);
    }

}
