/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

    final Object proxy;
    final HostContext context;

    HostProxy(HostContext context, Object proxy) {
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
    boolean isInstantiable(@Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        return cache.api.isProxyInstantiable(proxy);
    }

    @ExportMessage
    @TruffleBoundary
    Object instantiate(Object[] arguments,
                    @CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (cache.api.isProxyInstantiable(proxy)) {
            Object[] convertedArguments = cache.language.access.toValues(context.internalContext, arguments);
            Object result = guestToHostCall(library, cache.instantiate, context, proxy, convertedArguments);
            return context.toGuestValue(library, result);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isExecutable(@Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        return cache.api.isProxyExecutable(proxy);
    }

    @ExportMessage
    @TruffleBoundary
    Object execute(Object[] arguments,
                    @CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (cache.api.isProxyExecutable(proxy)) {
            Object[] convertedArguments = context.language.access.toValues(context.internalContext, arguments);
            Object result = guestToHostCall(library, cache.execute, context, proxy, convertedArguments);
            return context.toGuestValue(library, result);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isPointer(@Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        return cache.api.isProxyNativeObject(proxy);
    }

    @ExportMessage
    @TruffleBoundary
    long asPointer(@CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (cache.api.isProxyNativeObject(proxy)) {
            return (long) guestToHostCall(library, cache.asPointer, context, proxy);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean hasArrayElements(@Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        return cache.api.isProxyArray(proxy);
    }

    @ExportMessage
    @TruffleBoundary
    Object readArrayElement(long index,
                    @CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (cache.api.isProxyArray(proxy)) {
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
        if (cache.api.isProxyArray(proxy)) {
            Object castValue = context.asValue(library, value);
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
        if (cache.api.isProxyArray(proxy)) {
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
        if (cache.api.isProxyArray(proxy)) {
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
        if (cache.api.isProxyArray(proxy)) {
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
        if (cache.api.isProxyArray(proxy)) {
            long size = (long) guestToHostCall(library, cache.arraySize, context, proxy);
            return index < 0 || index >= size;
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean hasMembers(@Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        return cache.api.isProxyObject(proxy);
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal,
                    @CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (cache.api.isProxyObject(proxy)) {
            Object result = guestToHostCall(library, cache.memberKeys, context, proxy);
            assert result != null;
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
        if (cache.api.isProxyObject(proxy)) {
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
        if (cache.api.isProxyObject(proxy)) {
            Object castValue = context.asValue(library, value);
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
        if (cache.api.isProxyObject(proxy)) {
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
        if (cache.api.isProxyObject(proxy)) {
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
        if (cache.api.isProxyObject(proxy)) {
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
        if (cache.api.isProxyObject(proxy)) {
            return (boolean) guestToHostCall(library, cache.hasMember, context, proxy, member);
        } else {
            return false;
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberInsertable(String member, @CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        if (cache.api.isProxyObject(proxy)) {
            return !isMemberExisting(member, library, cache);
        } else {
            return false;
        }
    }

    @TruffleBoundary
    @ExportMessage
    boolean isDate(@Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        return cache.api.isProxyDate(proxy);
    }

    @TruffleBoundary
    @ExportMessage
    boolean isTime(@Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        return cache.api.isProxyTime(proxy);
    }

    @TruffleBoundary
    @ExportMessage
    boolean isTimeZone(@Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        return cache.api.isProxyTimeZone(proxy);
    }

    @ExportMessage
    @TruffleBoundary
    ZoneId asTimeZone(@CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (cache.api.isProxyTimeZone(proxy)) {
            return (ZoneId) guestToHostCall(library, cache.asTimezone, context, proxy);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    LocalDate asDate(@CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (cache.api.isProxyDate(proxy)) {
            return (LocalDate) guestToHostCall(library, cache.asDate, context, proxy);
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    LocalTime asTime(@CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (cache.api.isProxyTime(proxy)) {
            return (LocalTime) guestToHostCall(library, cache.asTime, context, proxy);
        }
        throw UnsupportedMessageException.create();
    }

    @TruffleBoundary
    @ExportMessage
    Instant asInstant(@CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (cache.api.isProxyInstant(proxy)) {
            return (Instant) guestToHostCall(library, cache.asInstant, context, proxy);
        } else if (isDate(cache) && isTime(cache) && isTimeZone(cache)) {
            return ZonedDateTime.of(asDate(library, cache), asTime(library, cache), asTimeZone(library, cache)).toInstant();
        }
        throw UnsupportedMessageException.create();
    }

    @TruffleBoundary
    @ExportMessage
    boolean isDuration(@Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        return cache.api.isProxyDuration(proxy);
    }

    @ExportMessage
    @TruffleBoundary
    Duration asDuration(@CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (cache.api.isProxyDuration(proxy)) {
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
    boolean hasIterator(@Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        return cache.api.isProxyIterable(proxy);
    }

    @ExportMessage
    @TruffleBoundary
    Object getIterator(@CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (cache.api.isProxyIterable(proxy)) {
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
    boolean isIterator(@Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        return cache.api.isProxyIterator(proxy);
    }

    @ExportMessage
    @TruffleBoundary
    boolean hasIteratorNextElement(@CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (cache.api.isProxyIterator(proxy)) {
            return (boolean) guestToHostCall(library, cache.hasIteratorNextElement, context, proxy);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    Object getIteratorNextElement(@CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (cache.api.isProxyIterator(proxy)) {
            Object result = guestToHostCall(library, cache.getIteratorNextElement, context, proxy);
            return context.toGuestValue(library, result);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean hasHashEntries(@Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        return cache.api.isProxyHashMap(proxy);
    }

    @ExportMessage
    @TruffleBoundary
    long getHashSize(@CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (cache.api.isProxyHashMap(proxy)) {
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
        if (cache.api.isProxyHashMap(proxy)) {
            Object keyValue = context.asValue(library, key);
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
        if (cache.api.isProxyHashMap(proxy)) {
            if (!isHashValueExisting(key, library, cache)) {
                throw UnknownKeyException.create(key);
            }
            Object keyValue = context.asValue(library, key);
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
        if (cache.api.isProxyHashMap(proxy)) {
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
        if (cache.api.isProxyHashMap(proxy)) {
            Object keyValue = this.context.asValue(library, key);
            Object valueValue = this.context.asValue(library, value);
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
        if (cache.api.isProxyHashMap(proxy)) {
            if (!isHashValueExisting(key, library, cache)) {
                throw UnknownKeyException.create(key);
            }
            Object keyValue = context.asValue(library, key);
            guestToHostCall(library, cache.removeHashEntry, context, proxy, keyValue);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    Object getHashEntriesIterator(@CachedLibrary("this") InteropLibrary library,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        if (cache.api.isProxyHashMap(proxy)) {
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

    public static Object toProxyHostObject(HostLanguage language, Object value) {
        Object v = HostLanguage.unwrapIfScoped(language, value);
        return ((HostProxy) v).proxy;
    }

    public static TruffleObject toProxyGuestObject(HostContext context, Object receiver) {
        return new HostProxy(context, receiver);
    }

}
