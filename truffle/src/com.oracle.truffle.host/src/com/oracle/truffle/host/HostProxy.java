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
package com.oracle.truffle.host;

import static com.oracle.truffle.host.GuestToHostRootNode.guestToHostCall;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyDate;
import org.graalvm.polyglot.proxy.ProxyDuration;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyHashMap;
import org.graalvm.polyglot.proxy.ProxyInstant;
import org.graalvm.polyglot.proxy.ProxyInstantiable;
import org.graalvm.polyglot.proxy.ProxyIterable;
import org.graalvm.polyglot.proxy.ProxyIterator;
import org.graalvm.polyglot.proxy.ProxyNativeObject;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.polyglot.proxy.ProxyTime;
import org.graalvm.polyglot.proxy.ProxyTimeZone;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.utilities.TriState;

@SuppressWarnings("deprecation")
@ExportLibrary(InteropLibrary.class)
final class HostProxy implements TruffleObject {

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
    final HostContext context;

    HostProxy(HostContext context, Proxy proxy) {
        this.context = context;
        this.proxy = proxy;
    }

    static Object withContext(Object obj, HostContext context) {
        if (obj instanceof HostProxy) {
            HostProxy hostObject = (HostProxy) obj;
            return new HostProxy(context, hostObject.proxy);
        } else {
            throw CompilerDirectives.shouldNotReachHere("Parameter must be HostProxy.");
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof HostProxy)) {
            return false;
        }
        return proxy == ((HostProxy) obj).proxy;
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
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyInstantiable) {
            Value[] convertedArguments = cache.language.access.toValues(context.internalContext, arguments);
            Object result = guestToHostCall(library, cache.instantiate, context, proxy, convertedArguments);
            return context.toGuestValue(library, result);
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
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyExecutable) {
            Value[] convertedArguments = context.language.access.toValues(context.internalContext, arguments);
            Object result = guestToHostCall(library, cache.execute, context, proxy, convertedArguments);
            return context.toGuestValue(library, result);
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
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyNativeObject) {
            return (long) guestToHostCall(library, cache.asPointer, context, proxy);
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
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyArray) {
            Object result = guestToHostCall(library, cache.arrayGet, context, proxy, index);
            return context.toGuestValue(library, result);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    void writeArrayElement(long index, Object value,
                    @CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyArray) {
            Value castValue = context.asValue(library, value);
            guestToHostCall(library, cache.arraySet, context, proxy, index, castValue);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    void removeArrayElement(long index, @CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache)
                    throws UnsupportedMessageException, InvalidArrayIndexException {
        if (proxy instanceof ProxyArray) {
            boolean result = (boolean) guestToHostCall(library, cache.arrayRemove, context, proxy, index);
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
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyArray) {
            return (long) guestToHostCall(library, cache.arraySize, context, proxy);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage(name = "isArrayElementReadable")
    @ExportMessage(name = "isArrayElementModifiable")
    @ExportMessage(name = "isArrayElementRemovable")
    @TruffleBoundary
    boolean isArrayElementExisting(long index, @CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        if (proxy instanceof ProxyArray) {
            long size = (long) guestToHostCall(library, cache.arraySize, context, proxy);
            return index >= 0 && index < size;
        } else {
            return false;
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean isArrayElementInsertable(long index, @CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        if (proxy instanceof ProxyArray) {
            long size = (long) guestToHostCall(library, cache.arraySize, context, proxy);
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
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyObject) {
            Object result = guestToHostCall(library, cache.memberKeys, context, proxy);
            if (result == null) {
                result = EMPTY;
            }
            Object guestValue = context.toGuestValue(library, result);
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            if (!interop.hasArrayElements(guestValue)) {
                if (guestValue instanceof HostObject) {
                    HostObject hostObject = (HostObject) guestValue;
                    if (hostObject.obj.getClass().isArray() && !hostObject.getHostClassCache().isArrayAccess()) {
                        throw illegalProxy(context, "getMemberKeys() returned a Java array %s, but allowArrayAccess in HostAccess is false.", context.asValue(library, guestValue).toString());
                    } else if (hostObject.obj instanceof List && !hostObject.getHostClassCache().isListAccess()) {
                        throw illegalProxy(context, "getMemberKeys() returned a Java List %s, but allowListAccess in HostAccess is false.", context.asValue(library, guestValue).toString());
                    }
                }
                throw illegalProxy(context, "getMemberKeys() returned invalid value %s but must return an array of member key Strings.",
                                context.asValue(library, guestValue).toString());
            }
            // Todo: Use interop to determine an array element type when the GR-5737 is resolved.
            for (int i = 0; i < interop.getArraySize(guestValue); i++) {
                try {
                    Object element = interop.readArrayElement(guestValue, i);
                    if (!interop.isString(element)) {
                        throw illegalProxy(context, "getMemberKeys() returned invalid value %s but must return an array of member key Strings.",
                                        context.asValue(library, guestValue).toString());
                    }
                } catch (UnsupportedOperationException e) {
                    CompilerDirectives.shouldNotReachHere(e);
                } catch (InvalidArrayIndexException e) {
                    continue;
                }
            }
            return guestValue;
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary
    static RuntimeException illegalProxy(HostContext context, String message, Object... parameters) {
        throw context.hostToGuestException(new IllegalStateException(String.format(message, parameters)));
    }

    @ExportMessage
    @TruffleBoundary
    Object readMember(String member,
                    @CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache)
                    throws UnsupportedMessageException, UnknownIdentifierException {
        if (proxy instanceof ProxyObject) {
            if (!isMemberExisting(member, library, cache)) {
                throw UnknownIdentifierException.create(member);
            }
            Object result = guestToHostCall(library, cache.getMember, context, proxy, member);
            return context.toGuestValue(library, result);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    void writeMember(String member, Object value,
                    @CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyObject) {
            Value castValue = context.asValue(library, value);
            guestToHostCall(library, cache.putMember, context, proxy, member, castValue);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    Object invokeMember(String member, Object[] arguments, @CachedLibrary("this") InteropLibrary library,
                    @Shared("executables") @CachedLibrary(limit = "LIMIT") InteropLibrary executables,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache)
                    throws UnsupportedMessageException, UnsupportedTypeException, ArityException, UnknownIdentifierException {
        if (proxy instanceof ProxyObject) {
            if (!isMemberExisting(member, library, cache)) {
                throw UnknownIdentifierException.create(member);
            }
            Object memberObject;
            try {
                memberObject = readMember(member, library, cache);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
            memberObject = context.toGuestValue(library, memberObject);
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
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        if (proxy instanceof ProxyObject) {
            if (isMemberExisting(member, library, cache)) {
                try {
                    return executables.isExecutable(readMember(member, library, cache));
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
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache)
                    throws UnsupportedMessageException, UnknownIdentifierException {
        if (proxy instanceof ProxyObject) {
            if (!isMemberExisting(member, library, cache)) {
                throw UnknownIdentifierException.create(member);
            }
            boolean result = (boolean) guestToHostCall(library, cache.removeMember, context, proxy, member);
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
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        if (proxy instanceof ProxyObject) {
            return (boolean) guestToHostCall(library, cache.hasMember, context, proxy, member);
        } else {
            return false;
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberInsertable(String member, @CachedLibrary("this") InteropLibrary library,

                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        if (proxy instanceof ProxyObject) {
            return !isMemberExisting(member, library, cache);
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
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyTimeZone) {
            return (ZoneId) guestToHostCall(library, cache.asTimezone, context, proxy);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    LocalDate asDate(@CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyDate) {
            return (LocalDate) guestToHostCall(library, cache.asDate, context, proxy);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    LocalTime asTime(@CachedLibrary("this") InteropLibrary library,

                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyTime) {
            return (LocalTime) guestToHostCall(library, cache.asTime, context, proxy);
        }
        throw UnsupportedMessageException.create();
    }

    @TruffleBoundary
    @ExportMessage
    Instant asInstant(@CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyInstant) {
            return (Instant) guestToHostCall(library, cache.asInstant, context, proxy);
        } else if (isDate() && isTime() && isTimeZone()) {
            return ZonedDateTime.of(asDate(library, cache), asTime(library, cache), asTimeZone(library, cache)).toInstant();
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
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyDuration) {
            return (Duration) guestToHostCall(library, cache.asDuration, context, proxy);
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
    Object toDisplayString(@SuppressWarnings("unused") boolean config) {
        try {
            return this.proxy.toString();
        } catch (Throwable t) {
            throw context.hostToGuestException(t);
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    Object getMetaObject() {
        Class<?> javaObject = this.proxy.getClass();
        return HostObject.forClass(javaObject, context);
    }

    @ExportMessage
    boolean hasIterator() {
        return proxy instanceof ProxyIterable;
    }

    @ExportMessage
    @TruffleBoundary
    Object getIterator(@CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyIterable) {
            Object result = guestToHostCall(library, cache.getIterator, context, proxy);
            Object guestValue = context.toGuestValue(library, result);
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            if (!interop.isIterator(guestValue)) {
                throw illegalProxy(context, "getIterator() returned an invalid value %s but must return an iterator.",
                                context.asValue(library, guestValue).toString());
            }
            return guestValue;
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean isIterator() {
        return proxy instanceof ProxyIterator;
    }

    @ExportMessage
    @TruffleBoundary
    boolean hasIteratorNextElement(@CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyIterator) {
            return (boolean) guestToHostCall(library, cache.hasIteratorNextElement, context, proxy);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    Object getIteratorNextElement(@CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyIterator) {
            Object result = guestToHostCall(library, cache.getIteratorNextElement, context, proxy);
            return context.toGuestValue(library, result);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean hasHashEntries() {
        return proxy instanceof ProxyHashMap;
    }

    @ExportMessage
    @TruffleBoundary
    long getHashSize(@CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyHashMap) {
            return (long) guestToHostCall(library, cache.getHashSize, context, proxy);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage(name = "isHashEntryReadable")
    @ExportMessage(name = "isHashEntryModifiable")
    @ExportMessage(name = "isHashEntryRemovable")
    @TruffleBoundary
    boolean isHashValueExisting(Object key,
                    @CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        if (proxy instanceof ProxyHashMap) {
            Value keyValue = context.asValue(library, key);
            return (boolean) guestToHostCall(library, cache.hasHashEntry, context, proxy, keyValue);
        } else {
            return false;
        }
    }

    @ExportMessage
    @TruffleBoundary
    Object readHashValue(Object key,
                    @CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException, UnknownKeyException {
        if (proxy instanceof ProxyHashMap) {
            if (!isHashValueExisting(key, library, cache)) {
                throw UnknownKeyException.create(key);
            }
            Value keyValue = context.asValue(library, key);
            Object result = guestToHostCall(library, cache.getHashValue, context, proxy, keyValue);
            return context.toGuestValue(library, result);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean isHashEntryInsertable(Object key,
                    @CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        if (proxy instanceof ProxyHashMap) {
            return !isHashValueExisting(key, library, cache);
        } else {
            return false;
        }
    }

    @ExportMessage
    @TruffleBoundary
    void writeHashEntry(Object key, Object value,
                    @CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyHashMap) {
            Value keyValue = this.context.asValue(library, key);
            Value valueValue = this.context.asValue(library, value);
            guestToHostCall(library, cache.putHashEntry, this.context, proxy, keyValue, valueValue);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    void removeHashEntry(Object key,
                    @CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException, UnknownKeyException {
        if (proxy instanceof ProxyHashMap) {
            if (!isHashValueExisting(key, library, cache)) {
                throw UnknownKeyException.create(key);
            }
            Value keyValue = context.asValue(library, key);
            guestToHostCall(library, cache.removeHashEntry, context, proxy, keyValue);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    Object getHashEntriesIterator(@CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (proxy instanceof ProxyHashMap) {
            Object result = guestToHostCall(library, cache.getHashEntriesIterator, context, proxy);
            Object guestValue = context.toGuestValue(library, result);
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            if (!interop.isIterator(guestValue)) {
                throw illegalProxy(context, "getHashEntriesIterator() returned an invalid value %s but must return an iterator.",
                                context.asValue(library, guestValue).toString());
            }
            return guestValue;
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static final class IsIdenticalOrUndefined {
        @Specialization
        static TriState doHostObject(HostProxy receiver, HostProxy other) {
            return receiver.proxy == other.proxy ? TriState.TRUE : TriState.FALSE;
        }

        @Fallback
        static TriState doOther(HostProxy receiver, Object other) {
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage
    @TruffleBoundary
    static int identityHashCode(HostProxy receiver) {
        return System.identityHashCode(receiver.proxy);
    }

    public static boolean isProxyGuestObject(HostLanguage language, TruffleObject value) {
        return isProxyGuestObject(language, (Object) value);
    }

    public static boolean isProxyGuestObject(HostLanguage language, Object value) {
        Object unwrapped = HostLanguage.unwrapIfScoped(language, value);
        return unwrapped instanceof HostProxy;
    }

    public static Proxy toProxyHostObject(HostLanguage language, Object value) {
        Object v = HostLanguage.unwrapIfScoped(language, value);
        return ((HostProxy) v).proxy;
    }

    public static TruffleObject toProxyGuestObject(HostContext context, Proxy receiver) {
        return new HostProxy(context, receiver);
    }

}
