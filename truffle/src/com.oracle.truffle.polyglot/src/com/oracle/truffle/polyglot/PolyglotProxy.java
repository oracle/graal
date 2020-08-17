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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
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
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.polyglot.HostLanguage.HostContext;

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

    final Proxy proxy;

    PolyglotProxy(Proxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PolyglotProxy)) {
            return false;
        }
        return proxy == ((PolyglotProxy) obj).proxy;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(proxy);
    }

    @ExportMessage
    boolean isInstantiable() {
        return proxy instanceof ProxyInstantiable;
    }

    @ExportMessage
    @TruffleBoundary
    Object instantiate(Object[] arguments,
                    @CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) throws UnsupportedMessageException {
        if (proxy instanceof ProxyInstantiable) {
            PolyglotLanguageContext languageContext = context.get().internalContext;
            Value[] convertedArguments = languageContext.toHostValues(arguments, 0);
            Object result = guestToHostCall(library, language.getHostToGuestCache().instantiate, languageContext, proxy, convertedArguments);
            return languageContext.toGuestValue(result);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isExecutable() {
        return proxy instanceof ProxyExecutable;
    }

    @ExportMessage
    @TruffleBoundary
    Object execute(Object[] arguments,
                    @CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) throws UnsupportedMessageException {
        if (proxy instanceof ProxyExecutable) {
            PolyglotLanguageContext languageContext = context.get().internalContext;
            Value[] convertedArguments = languageContext.toHostValues(arguments, 0);
            Object result = guestToHostCall(library, language.getHostToGuestCache().execute, languageContext, proxy, convertedArguments);
            return languageContext.toGuestValue(result);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isPointer() {
        return proxy instanceof ProxyNativeObject;
    }

    @ExportMessage
    @TruffleBoundary
    long asPointer(@CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) throws UnsupportedMessageException {
        if (proxy instanceof ProxyNativeObject) {
            PolyglotLanguageContext languageContext = context.get().internalContext;
            return (long) guestToHostCall(library, language.getHostToGuestCache().asPointer, languageContext, proxy);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean hasArrayElements() {
        return proxy instanceof ProxyArray;
    }

    @ExportMessage
    @TruffleBoundary
    Object readArrayElement(long index,
                    @CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) throws UnsupportedMessageException {
        if (proxy instanceof ProxyArray) {
            PolyglotLanguageContext languageContext = context.get().internalContext;
            Object result = guestToHostCall(library, language.getHostToGuestCache().arrayGet, languageContext, proxy, index);
            return languageContext.toGuestValue(result);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    void writeArrayElement(long index, Object value,
                    @CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) throws UnsupportedMessageException {
        if (proxy instanceof ProxyArray) {
            PolyglotLanguageContext languageContext = context.get().internalContext;
            Value castValue = languageContext.asValue(value);
            guestToHostCall(library, language.getHostToGuestCache().arraySet, languageContext, proxy, index, castValue);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    void removeArrayElement(long index, @CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) throws UnsupportedMessageException, InvalidArrayIndexException {
        if (proxy instanceof ProxyArray) {
            PolyglotLanguageContext languageContext = context.get().internalContext;
            boolean result = (boolean) guestToHostCall(library, language.getHostToGuestCache().arrayRemove, languageContext, proxy, index);
            if (!result) {
                throw InvalidArrayIndexException.create(index);
            }
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    long getArraySize(@CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) throws UnsupportedMessageException {
        if (proxy instanceof ProxyArray) {
            PolyglotLanguageContext languageContext = context.get().internalContext;
            return (long) guestToHostCall(library, language.getHostToGuestCache().arraySize, languageContext, proxy);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage(name = "isArrayElementReadable")
    @ExportMessage(name = "isArrayElementModifiable")
    @ExportMessage(name = "isArrayElementRemovable")
    @TruffleBoundary
    boolean isArrayElementExisting(long index, @CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) {
        if (proxy instanceof ProxyArray) {
            PolyglotLanguageContext languageContext = context.get().internalContext;
            long size = (long) guestToHostCall(library, language.getHostToGuestCache().arraySize, languageContext, proxy);
            return index >= 0 && index < size;
        } else {
            return false;
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean isArrayElementInsertable(long index, @CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) {
        if (proxy instanceof ProxyArray) {
            PolyglotLanguageContext languageContext = context.get().internalContext;
            long size = (long) guestToHostCall(library, language.getHostToGuestCache().arraySize, languageContext, proxy);
            return index < 0 || index >= size;
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean hasMembers() {
        return proxy instanceof ProxyObject;
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal,
                    @CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) throws UnsupportedMessageException {
        if (proxy instanceof ProxyObject) {
            PolyglotLanguageContext languageContext = context.get().internalContext;
            Object result = guestToHostCall(library, language.getHostToGuestCache().memberKeys, languageContext, proxy);
            if (result == null) {
                result = EMPTY;
            }
            Object guestValue = languageContext.toGuestValue(result);
            if (!InteropLibrary.getFactory().getUncached().hasArrayElements(guestValue)) {
                throw illegalProxy(languageContext, "getMemberKeys() returned invalid value %s but must return an array of member key Strings.",
                                languageContext.asValue(guestValue).toString());
            }
            return guestValue;
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary
    static RuntimeException illegalProxy(PolyglotLanguageContext languageContext, String message, Object... parameters) {
        throw PolyglotImpl.hostToGuestException(languageContext, new IllegalStateException(
                        String.format(message, parameters)));
    }

    @ExportMessage
    @TruffleBoundary
    Object readMember(String member,
                    @CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) throws UnsupportedMessageException, UnknownIdentifierException {
        if (proxy instanceof ProxyObject) {
            if (!isMemberExisting(member, library, context, language)) {
                throw UnknownIdentifierException.create(member);
            }
            PolyglotLanguageContext languageContext = context.get().internalContext;
            Object result = guestToHostCall(library, language.getHostToGuestCache().getMember, languageContext, proxy, member);
            return languageContext.toGuestValue(result);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    void writeMember(String member, Object value,
                    @CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) throws UnsupportedMessageException {
        if (proxy instanceof ProxyObject) {
            PolyglotLanguageContext languageContext = context.get().internalContext;
            Value castValue = languageContext.asValue(value);
            guestToHostCall(library, language.getHostToGuestCache().putMember, languageContext, proxy, member, castValue);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    Object invokeMember(String member, Object[] arguments, @CachedLibrary("this") InteropLibrary library,
                    @Shared("executables") @CachedLibrary(limit = "LIMIT") InteropLibrary executables,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language)
                    throws UnsupportedMessageException, UnsupportedTypeException, ArityException, UnknownIdentifierException {
        if (proxy instanceof ProxyObject) {
            if (!isMemberExisting(member, library, context, language)) {
                throw UnknownIdentifierException.create(member);
            }
            Object memberObject;
            try {
                memberObject = readMember(member, library, context, language);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
            PolyglotLanguageContext languageContext = context.get().internalContext;
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
                    @Shared("executables") @CachedLibrary(limit = "LIMIT") InteropLibrary executables,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) {
        if (proxy instanceof ProxyObject) {
            if (isMemberExisting(member, library, context, language)) {
                try {
                    return executables.isExecutable(readMember(member, library, context, language));
                } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                    return false;
                }
            }
        }
        return false;
    }

    @ExportMessage
    @TruffleBoundary
    void removeMember(String member, @CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) throws UnsupportedMessageException, UnknownIdentifierException {
        if (proxy instanceof ProxyObject) {
            if (!isMemberExisting(member, library, context, language)) {
                throw UnknownIdentifierException.create(member);
            }
            PolyglotLanguageContext languageContext = context.get().internalContext;
            boolean result = (boolean) guestToHostCall(library, language.getHostToGuestCache().removeMember, languageContext, proxy, member);
            if (!result) {
                throw UnknownIdentifierException.create(member);
            }
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage(name = "isMemberReadable")
    @ExportMessage(name = "isMemberModifiable")
    @ExportMessage(name = "isMemberRemovable")
    @TruffleBoundary
    boolean isMemberExisting(String member, @CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) {
        if (proxy instanceof ProxyObject) {
            PolyglotLanguageContext languageContext = context.get().internalContext;
            return (boolean) guestToHostCall(library, language.getHostToGuestCache().hasMember, languageContext, proxy, member);
        } else {
            return false;
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberInsertable(String member, @CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) {
        if (proxy instanceof ProxyObject) {
            return !isMemberExisting(member, library, context, language);
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

    @ExportMessage
    @TruffleBoundary
    ZoneId asTimeZone(@CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) throws UnsupportedMessageException {
        if (proxy instanceof ProxyTimeZone) {
            PolyglotLanguageContext languageContext = context.get().internalContext;
            return (ZoneId) guestToHostCall(library, language.getHostToGuestCache().asTimezone, languageContext, proxy);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    LocalDate asDate(@CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) throws UnsupportedMessageException {
        if (proxy instanceof ProxyDate) {
            PolyglotLanguageContext languageContext = context.get().internalContext;
            return (LocalDate) guestToHostCall(library, language.getHostToGuestCache().asDate, languageContext, proxy);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    LocalTime asTime(@CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) throws UnsupportedMessageException {
        if (proxy instanceof ProxyTime) {
            PolyglotLanguageContext languageContext = context.get().internalContext;
            return (LocalTime) guestToHostCall(library, language.getHostToGuestCache().asTime, languageContext, proxy);
        }
        throw UnsupportedMessageException.create();
    }

    @TruffleBoundary
    @ExportMessage
    Instant asInstant(@CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) throws UnsupportedMessageException {
        if (proxy instanceof ProxyInstant) {
            PolyglotLanguageContext languageContext = context.get().internalContext;
            return (Instant) guestToHostCall(library, language.getHostToGuestCache().asInstant, languageContext, proxy);
        } else if (isDate() && isTime() && isTimeZone()) {
            return ZonedDateTime.of(asDate(library, context, language), asTime(library, context, language), asTimeZone(library, context, language)).toInstant();
        }
        throw UnsupportedMessageException.create();
    }

    @TruffleBoundary
    @ExportMessage
    boolean isDuration() {
        return proxy instanceof ProxyDuration;
    }

    @ExportMessage
    @TruffleBoundary
    Duration asDuration(@CachedLibrary("this") InteropLibrary library,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context,
                    @CachedLanguage HostLanguage language) throws UnsupportedMessageException {
        if (proxy instanceof ProxyDuration) {
            PolyglotLanguageContext languageContext = context.get().internalContext;
            return (Duration) guestToHostCall(library, language.getHostToGuestCache().asDuration, languageContext, proxy);
        }
        throw UnsupportedMessageException.create();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return HostLanguage.class;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    @TruffleBoundary
    Object toDisplayString(@SuppressWarnings("unused") boolean config,
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context) {
        try {
            return this.proxy.toString();
        } catch (Throwable t) {
            PolyglotLanguageContext languageContext = context.get().internalContext;
            throw PolyglotImpl.hostToGuestException(languageContext, t);
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    Object getMetaObject(
                    @CachedContext(HostLanguage.class) ContextReference<HostContext> context) {
        Class<?> javaObject = this.proxy.getClass();
        PolyglotLanguageContext languageContext = context.get().internalContext;
        return HostObject.forClass(javaObject, languageContext);
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static final class IsIdenticalOrUndefined {
        @Specialization
        static TriState doHostObject(PolyglotProxy receiver, PolyglotProxy other) {
            return receiver.proxy == other.proxy ? TriState.TRUE : TriState.FALSE;
        }

        @Fallback
        static TriState doOther(PolyglotProxy receiver, Object other) {
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage
    @TruffleBoundary
    static int identityHashCode(PolyglotProxy receiver) {
        return System.identityHashCode(receiver.proxy);
    }

    public static boolean isProxyGuestObject(TruffleObject value) {
        return value instanceof PolyglotProxy;
    }

    public static boolean isProxyGuestObject(Object value) {
        return value instanceof PolyglotProxy;
    }

    public static Proxy toProxyHostObject(TruffleObject value) {
        return ((PolyglotProxy) value).proxy;
    }

    public static TruffleObject toProxyGuestObject(Proxy receiver) {
        return new PolyglotProxy(receiver);
    }

}
