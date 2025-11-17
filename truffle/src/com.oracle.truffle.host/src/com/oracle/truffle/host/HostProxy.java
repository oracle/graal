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
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedExactClassProfile;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.host.HostContext.ToGuestValueNode;

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
    boolean isInstantiable(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType) {
        Object p = proxyType.profile(node, this.proxy);
        return p instanceof ProxyInstantiable;
    }

    @ExportMessage
    Object instantiate(Object[] arguments,
                    @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached(inline = true) ToGuestValueNode toGuest,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyInstantiable) {
            Object[] convertedArguments = cache.language.access.toValues(context.internalContext, arguments);
            Object result = guestToHostCall(node, cache.instantiate, p, convertedArguments);
            return toGuest.execute(node, result);
        }
        errorProfile.enter(node);
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isExecutable(
                    @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType) {
        Object p = proxyType.profile(node, this.proxy);
        return p instanceof ProxyExecutable;
    }

    @ExportMessage
    Object execute(Object[] arguments,
                    @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached(inline = true) ToGuestValueNode toGuest,
                    @Exclusive @Cached HostToValuesNode toGuestValues,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyExecutable) {
            Object[] convertedArguments = toGuestValues.execute(node, context, arguments);
            Object result = guestToHostCall(node, cache.execute, p, convertedArguments);
            return toGuest.execute(node, result);
        }
        errorProfile.enter(node);
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isPointer(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType) {
        Object p = proxyType.profile(node, this.proxy);
        return p instanceof ProxyNativeObject;
    }

    @ExportMessage
    long asPointer(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyNativeObject pn) {
            return (long) guestToHostCall(node, cache.asPointer, pn);
        }
        errorProfile.enter(node);
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean hasArrayElements(
                    @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType) {
        return proxyType.profile(node, this.proxy) instanceof ProxyArray;
    }

    @ExportMessage
    Object readArrayElement(long index,
                    @Bind Node node,
                    @Shared @Cached(inline = true) ToGuestValueNode toGuest,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyArray pa) {
            Object result = guestToHostCall(node, cache.arrayGet, pa, index);
            return toGuest.execute(node, result);
        } else {
            errorProfile.enter(node);
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    void writeArrayElement(long index, Object value,
                    @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached HostToValueNode toValueNode,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyArray pa) {
            Object castValue = toValueNode.execute(context, value);
            guestToHostCall(node, cache.arraySet, pa, index, castValue);
        } else {
            errorProfile.enter(node);
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    void removeArrayElement(long index, @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache)
                    throws UnsupportedMessageException, InvalidArrayIndexException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyArray pa) {
            boolean result = (boolean) guestToHostCall(node, cache.arrayRemove, pa, index);
            if (!result) {
                errorProfile.enter(node);
                throw InvalidArrayIndexException.create(index);
            }
        } else {
            errorProfile.enter(node);
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    long getArraySize(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyArray pa) {
            return (long) guestToHostCall(node, cache.arraySize, pa);
        } else {
            errorProfile.enter(node);
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage(name = "isArrayElementReadable")
    @ExportMessage(name = "isArrayElementModifiable")
    @ExportMessage(name = "isArrayElementRemovable")
    boolean isArrayElementExisting(long index, @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyArray pa) {
            long size = (long) guestToHostCall(node, cache.arraySize, pa);
            return index >= 0 && index < size;
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean isArrayElementInsertable(long index, @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyArray pa) {
            long size = (long) guestToHostCall(node, cache.arraySize, pa);
            return index < 0 || index >= size;
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean hasMembers(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType) {
        Object p = proxyType.profile(node, this.proxy);
        return p instanceof ProxyObject;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal,
                    @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared @Cached(inline = true) ToGuestValueNode toGuest,
                    @Shared @CachedLibrary(limit = "3") InteropLibrary sharedInterop,
                    @Exclusive @CachedLibrary(limit = "3") InteropLibrary sharedInterop2,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyObject) {
            Object result = guestToHostCall(node, cache.memberKeys, p);
            assert result != null;
            Object guestValue = toGuest.execute(node, result);
            if (!sharedInterop.hasArrayElements(guestValue)) {
                errorProfile.enter(node);
                throw failInvalidMembers(node, guestValue);
            }
            for (int i = 0; i < sharedInterop.getArraySize(guestValue); i++) {
                try {
                    Object element = sharedInterop.readArrayElement(guestValue, i);
                    if (!sharedInterop2.isString(element)) {
                        errorProfile.enter(node);
                        throw illegalProxy(context, "getMemberKeys() returned invalid value %s but must return an array of member key Strings.",
                                        valueToString(node, element));
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

    private RuntimeException failInvalidMembers(Node node, Object guestValue) {
        if (guestValue instanceof HostObject) {
            HostObject hostObject = (HostObject) guestValue;
            if (hostObject.obj.getClass().isArray() && !hostObject.getHostClassCache().isArrayAccess()) {
                throw illegalProxy(context, "getMemberKeys() returned a Java array %s, but allowArrayAccess in HostAccess is false.", valueToString(node, guestValue));
            } else if (hostObject.obj instanceof List && !hostObject.getHostClassCache().isListAccess()) {
                throw illegalProxy(context, "getMemberKeys() returned a Java List %s, but allowListAccess in HostAccess is false.", valueToString(node, guestValue));
            }
        }
        throw illegalProxy(context, "getMemberKeys() returned invalid value %s but must return an array of member key Strings.",
                        valueToString(node, guestValue));
    }

    @TruffleBoundary
    static RuntimeException illegalProxy(HostContext context, String message, Object... parameters) {
        throw context.hostToGuestException(new IllegalStateException(String.format(message, parameters)));
    }

    @ExportMessage
    Object readMember(String member,
                    @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached(inline = true) ToGuestValueNode toGuest,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache)
                    throws UnsupportedMessageException, UnknownIdentifierException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyObject) {
            if (!isMemberExisting(member, node, proxyType, cache)) {
                errorProfile.enter(node);
                throw UnknownIdentifierException.create(member);
            }
            Object result = guestToHostCall(node, cache.getMember, p, member);
            return toGuest.execute(node, result);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    void writeMember(String member, Object value,
                    @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached HostToValueNode toValueNode,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyObject) {
            Object castValue = toValueNode.execute(context, value);
            guestToHostCall(node, cache.putMember, p, member, castValue);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    Object invokeMember(String member, Object[] arguments, @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached(inline = true) ToGuestValueNode toGuest,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("executables") @CachedLibrary(limit = "LIMIT") InteropLibrary executables,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache)
                    throws UnsupportedMessageException, UnsupportedTypeException, ArityException, UnknownIdentifierException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyObject) {
            if (!isMemberExisting(member, node, proxyType, cache)) {
                throw UnknownIdentifierException.create(member);
            }
            Object memberObject;
            try {
                memberObject = readMember(member, node, proxyType, toGuest, errorProfile, cache);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
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
    boolean isMemberInvocable(String member, @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached(inline = true) ToGuestValueNode toGuest,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("executables") @CachedLibrary(limit = "LIMIT") InteropLibrary executables,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyObject) {
            if (isMemberExisting(member, node, proxyType, cache)) {
                try {
                    return executables.isExecutable(readMember(member, node, proxyType, toGuest, errorProfile, cache));
                } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                    return false;
                }
            }
        }
        return false;
    }

    @ExportMessage
    void removeMember(String member, @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache)
                    throws UnsupportedMessageException, UnknownIdentifierException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyObject) {
            if (!isMemberExisting(member, node, proxyType, cache)) {
                errorProfile.enter(node);
                throw UnknownIdentifierException.create(member);
            }
            boolean result = (boolean) guestToHostCall(node, cache.removeMember, p, member);
            if (!result) {
                errorProfile.enter(node);
                throw UnknownIdentifierException.create(member);
            }
        } else {
            errorProfile.enter(node);
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage(name = "isMemberReadable")
    @ExportMessage(name = "isMemberModifiable")
    @ExportMessage(name = "isMemberRemovable")
    boolean isMemberExisting(String member, @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyObject) {
            return (boolean) guestToHostCall(node, cache.hasMember, p, member);
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean isMemberInsertable(String member, @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyObject) {
            return !isMemberExisting(member, node, proxyType, cache);
        } else {
            return false;
        }
    }

    @ExportMessage
    boolean isDate(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType) {
        Object p = proxyType.profile(node, this.proxy);
        return p instanceof ProxyDate;
    }

    @ExportMessage
    boolean isTime(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType) {
        Object p = proxyType.profile(node, this.proxy);
        return p instanceof ProxyTime;
    }

    @ExportMessage
    boolean isTimeZone(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType) {
        Object p = proxyType.profile(node, this.proxy);
        return p instanceof ProxyTimeZone;
    }

    @ExportMessage
    ZoneId asTimeZone(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyTimeZone) {
            return (ZoneId) guestToHostCall(node, cache.asTimezone, p);
        }
        errorProfile.enter(node);
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    LocalDate asDate(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyDate) {
            return (LocalDate) guestToHostCall(node, cache.asDate, p);
        }
        errorProfile.enter(node);
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    LocalTime asTime(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyTime) {
            return (LocalTime) guestToHostCall(node, cache.asTime, p);
        }
        errorProfile.enter(node);
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    Instant asInstant(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyInstant) {
            return (Instant) guestToHostCall(node, cache.asInstant, p);
        } else if (p instanceof ProxyDate && p instanceof ProxyTime && p instanceof ProxyTimeZone) {
            LocalDate date = asDate(node, proxyType, errorProfile, cache);
            LocalTime time = asTime(node, proxyType, errorProfile, cache);
            ZoneId zone = asTimeZone(node, proxyType, errorProfile, cache);
            return createInstant(date, time, zone);
        }
        errorProfile.enter(node);
        throw UnsupportedMessageException.create();
    }

    @TruffleBoundary
    private static Instant createInstant(LocalDate date, LocalTime time, ZoneId zone) {
        return ZonedDateTime.of(date, time, zone).toInstant();
    }

    @ExportMessage
    boolean isDuration(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType) {
        Object p = proxyType.profile(node, this.proxy);
        return p instanceof ProxyDuration;
    }

    @ExportMessage
    Duration asDuration(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyDuration) {
            return (Duration) guestToHostCall(node, cache.asDuration, p);
        }
        errorProfile.enter(node);
        throw UnsupportedMessageException.create();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasLanguageId() {
        return true;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    String getLanguageId() {
        return HostLanguage.ID;
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
    boolean hasIterator(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType) {
        Object p = proxyType.profile(node, this.proxy);
        return p instanceof ProxyIterable;
    }

    @ExportMessage
    Object getIterator(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared @Cached(inline = true) ToGuestValueNode toGuest,
                    @Shared @CachedLibrary(limit = "3") InteropLibrary sharedInterop,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyIterable) {
            Object result = guestToHostCall(node, cache.getIterator, p);
            Object guestValue = toGuest.execute(node, result);
            if (!sharedInterop.isIterator(guestValue)) {
                errorProfile.enter(node);
                throw illegalProxy(context, "getIterator() returned an invalid value %s but must return an iterator.",
                                valueToString(node, guestValue));
            }
            return guestValue;
        } else {
            errorProfile.enter(node);
            throw UnsupportedMessageException.create();
        }
    }

    @TruffleBoundary
    private String valueToString(Node node, Object guestValue) {
        return context.asValue(node, guestValue).toString();
    }

    @ExportMessage
    boolean isIterator(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType) {
        Object p = proxyType.profile(node, this.proxy);
        return p instanceof ProxyIterator;
    }

    @ExportMessage
    boolean hasIteratorNextElement(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyIterator) {
            return (boolean) guestToHostCall(node, cache.hasIteratorNextElement, p);
        } else {
            errorProfile.enter(node);
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    Object getIteratorNextElement(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached(inline = true) ToGuestValueNode toGuest,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyIterator) {
            Object result = guestToHostCall(node, cache.getIteratorNextElement, p);
            return toGuest.execute(node, result);
        } else {
            errorProfile.enter(node);
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean hasHashEntries(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType) {
        Object p = proxyType.profile(node, this.proxy);
        return p instanceof ProxyHashMap;
    }

    @ExportMessage
    long getHashSize(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyHashMap) {
            return (long) guestToHostCall(node, cache.getHashSize, p);
        } else {
            errorProfile.enter(node);
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage(name = "isHashEntryReadable")
    @ExportMessage(name = "isHashEntryModifiable")
    @ExportMessage(name = "isHashEntryRemovable")
    boolean isHashValueExisting(Object key,
                    @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached HostToValueNode toValueNode,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyHashMap) {
            Object keyValue = toValueNode.execute(context, key);
            return (boolean) guestToHostCall(node, cache.hasHashEntry, p, keyValue);
        } else {
            return false;
        }
    }

    @ExportMessage
    Object readHashValue(Object key,
                    @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached HostToValueNode toValueNode,
                    @Shared @Cached(inline = true) ToGuestValueNode toGuest,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException, UnknownKeyException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyHashMap) {
            if (!isHashValueExisting(key, node, proxyType, toValueNode, cache)) {
                errorProfile.enter(node);
                throw UnknownKeyException.create(key);
            }
            Object keyValue = context.asValue(node, key);
            Object result = guestToHostCall(node, cache.getHashValue, p, keyValue);
            return toGuest.execute(node, result);
        } else {
            errorProfile.enter(node);
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    boolean isHashEntryInsertable(Object key,
                    @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached HostToValueNode toValueNode,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyHashMap) {
            return !isHashValueExisting(key, node, proxyType, toValueNode, cache);
        } else {
            return false;
        }
    }

    @ExportMessage
    void writeHashEntry(Object key, Object value,
                    @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached HostToValueNode toValueNode,
                    @Exclusive @Cached HostToValueNode toValueNode2,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyHashMap) {
            Object keyValue = toValueNode.execute(context, key);
            Object valueValue = toValueNode2.execute(context, value);
            guestToHostCall(node, cache.putHashEntry, p, keyValue, valueValue);
        } else {
            errorProfile.enter(node);
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    void removeHashEntry(Object key,
                    @Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @Cached HostToValueNode toValueNode,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException, UnknownKeyException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyHashMap) {
            if (!isHashValueExisting(key, node, proxyType, toValueNode, cache)) {
                errorProfile.enter(node);
                throw UnknownKeyException.create(key);
            }
            Object keyValue = toValueNode.execute(context, key);
            guestToHostCall(node, cache.removeHashEntry, p, keyValue);
        } else {
            errorProfile.enter(node);
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    Object getHashEntriesIterator(@Bind Node node,
                    @Shared @Cached InlinedExactClassProfile proxyType,
                    @Shared @CachedLibrary(limit = "3") InteropLibrary sharedInterop,
                    @Shared @Cached(inline = true) ToGuestValueNode toGuest,
                    @Shared @Cached InlinedBranchProfile errorProfile,
                    @Shared("cache") @Cached(value = "this.context.getGuestToHostCache()", allowUncached = true) GuestToHostCodeCache cache) throws UnsupportedMessageException {
        Object p = proxyType.profile(node, this.proxy);
        if (p instanceof ProxyHashMap) {
            Object result = guestToHostCall(node, cache.getHashEntriesIterator, p);
            Object guestValue = toGuest.execute(node, result);
            if (!sharedInterop.isIterator(guestValue)) {
                errorProfile.enter(node);
                throw illegalProxy(context, "getHashEntriesIterator() returned an invalid value %s but must return an iterator.",
                                valueToString(node, guestValue));
            }
            return guestValue;
        } else {
            errorProfile.enter(node);
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
