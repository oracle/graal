/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.NoSuchElementException;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.host.HostMethodDesc.SingleMethod;
import com.oracle.truffle.host.HostMethodDesc.SingleMethod.MHBase;

final class GuestToHostCodeCache extends GuestToHostCodeCacheBase {

    final HostLanguage language;

    GuestToHostCodeCache(HostLanguage language) {
        super(language.polyglot.getAPIAccess());
        this.language = language;
    }

    static class GuestToHostInvokeHandle extends GuestToHostRootNode {

        protected GuestToHostInvokeHandle() {
            super(HostObject.class, "doInvoke");
        }

        @Override
        protected Object executeImpl(Object receiver, Object[] callArguments) {
            if (TruffleOptions.AOT) {
                throw CompilerDirectives.shouldNotReachHere("MHBase.invokeHandle can only be used in non AOT mode.");
            }
            MethodHandle methodHandle = (MethodHandle) callArguments[ARGUMENT_OFFSET];
            Object[] arguments = (Object[]) callArguments[ARGUMENT_OFFSET + 1];
            Object ret;
            try {
                ret = MHBase.invokeHandle(methodHandle, receiver, arguments);
            } catch (Throwable e) {
                throw HostInteropReflect.rethrow(e);
            }
            return ret;
        }
    }

    final CallTarget methodHandleHostInvoke = new GuestToHostInvokeHandle().getCallTarget();

    static class GuestToHostInvokeReflect extends GuestToHostRootNode {

        protected GuestToHostInvokeReflect() {
            super(HostObject.class, "doInvoke");
        }

        @Override
        protected Object executeImpl(Object obj, Object[] callArguments) {
            SingleMethod.ReflectBase method = (SingleMethod.ReflectBase) callArguments[ARGUMENT_OFFSET];
            Object[] arguments = (Object[]) callArguments[ARGUMENT_OFFSET + 1];
            Object ret;
            try {
                ret = method.invoke(obj, arguments);
            } catch (Throwable e) {
                throw HostInteropReflect.rethrow(e);
            }
            return ret;
        }
    }

    final CallTarget reflectionHostInvoke = new GuestToHostInvokeReflect().getCallTarget();

    final CallTarget execute = new GuestToHostRootNode(api.getProxyExecutableClass(), "execute") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) throws UnsupportedMessageException {
            try {
                return api.callProxyExecutableExecute(proxy, (Object[]) arguments[ARGUMENT_OFFSET]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }
    }.getCallTarget();

    final CallTarget asPointer = new GuestToHostRootNode(api.getProxyNativeObjectClass(), "asPointer") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return api.callProxyNativeObjectAsPointer(proxy);
        }
    }.getCallTarget();

    final CallTarget instantiate = new GuestToHostRootNode(api.getProxyInstantiableClass(), "newInstance") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) throws UnsupportedMessageException {
            try {
                return api.callProxyInstantiableNewInstance(proxy, (Object[]) arguments[ARGUMENT_OFFSET]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }
    }.getCallTarget();

    final CallTarget arrayGet = new GuestToHostRootNode(api.getProxyArrayClass(), "get") {

        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws InvalidArrayIndexException, UnsupportedMessageException {
            long index = (long) arguments[ARGUMENT_OFFSET];
            try {
                return boundaryGet(proxy, index);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw InvalidArrayIndexException.create(index);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }

        @TruffleBoundary
        private Object boundaryGet(Object proxy, long index) {
            return api.callProxyArrayGet(proxy, index);
        }
    }.getCallTarget();

    final CallTarget arraySet = new GuestToHostRootNode(api.getProxyArrayClass(), "set") {

        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws InvalidArrayIndexException, UnsupportedMessageException {
            long index = (long) arguments[ARGUMENT_OFFSET];
            try {
                boundarySet(proxy, index, arguments[ARGUMENT_OFFSET + 1]);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw InvalidArrayIndexException.create(index);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
            return null;
        }

        @TruffleBoundary
        private void boundarySet(Object proxy, long index, Object value) {
            api.callProxyArraySet(proxy, index, value);
        }
    }.getCallTarget();

    final CallTarget arrayRemove = new GuestToHostRootNode(api.getProxyArrayClass(), "remove") {

        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws InvalidArrayIndexException, UnsupportedMessageException {
            long index = (long) arguments[ARGUMENT_OFFSET];
            try {
                return boundaryRemove(proxy, index);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw InvalidArrayIndexException.create(index);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }

        @TruffleBoundary
        private boolean boundaryRemove(Object proxy, long index) {
            return api.callProxyArrayRemove(proxy, index);
        }
    }.getCallTarget();

    final CallTarget arraySize = new GuestToHostRootNode(api.getProxyArrayClass(), "getSize") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return api.callProxyArraySize(proxy);
        }
    }.getCallTarget();

    final CallTarget memberKeys = new GuestToHostRootNode(api.getProxyObjectClass(), "getMemberKeys") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return api.callProxyObjectMemberKeys(proxy);
        }
    }.getCallTarget();

    final CallTarget getMember = new GuestToHostRootNode(api.getProxyObjectClass(), "getMember") {

        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws UnsupportedMessageException {
            try {
                return boundaryGetMember(proxy, (String) arguments[ARGUMENT_OFFSET]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }

        @TruffleBoundary
        private Object boundaryGetMember(Object proxy, String argument) {
            return api.callProxyObjectGetMember(proxy, argument);
        }
    }.getCallTarget();

    final CallTarget putMember = new GuestToHostRootNode(api.getProxyObjectClass(), "putMember") {

        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws UnsupportedMessageException {
            try {
                boundaryPutMember(proxy, (String) arguments[ARGUMENT_OFFSET], arguments[ARGUMENT_OFFSET + 1]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
            return null;
        }

        @TruffleBoundary
        private void boundaryPutMember(Object proxy, String member, Object value) {
            api.callProxyObjectPutMember(proxy, member, value);
        }
    }.getCallTarget();

    final CallTarget removeMember = new GuestToHostRootNode(api.getProxyObjectClass(), "removeMember") {

        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws UnsupportedMessageException {
            try {
                return removeBoundary(proxy, (String) arguments[ARGUMENT_OFFSET]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }

        @TruffleBoundary
        private boolean removeBoundary(Object proxy, String member) {
            return api.callProxyObjectRemoveMember(proxy, member);
        }
    }.getCallTarget();

    final CallTarget hasMember = new GuestToHostRootNode(api.getProxyObjectClass(), "hasMember") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return api.callProxyObjectHasMember(proxy, (String) arguments[ARGUMENT_OFFSET]);
        }
    }.getCallTarget();

    final CallTarget asTimezone = new GuestToHostRootNode(api.getProxyTimeZoneClass(), "asTimeZone") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            ZoneId zone = api.callProxyTimeZoneAsTimeZone(proxy);
            if (zone == null) {
                throw CompilerDirectives.shouldNotReachHere("The returned zone must not be null.");
            }
            return zone;
        }
    }.getCallTarget();

    final CallTarget asDate = new GuestToHostRootNode(api.getProxyDateClass(), "asDate") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            LocalDate date = api.callProxyDateAsDate(proxy);
            if (date == null) {
                throw new AssertionError("The returned date must not be null.");
            }
            return date;
        }
    }.getCallTarget();

    final CallTarget asTime = new GuestToHostRootNode(api.getProxyTimeClass(), "asTime") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            LocalTime time = api.callProxyTimeAsTime(proxy);
            if (time == null) {
                throw new AssertionError("The returned time must not be null.");
            }
            return time;
        }
    }.getCallTarget();

    final CallTarget asInstant = new GuestToHostRootNode(api.getProxyInstantClass(), "asInstant") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            Instant instant = api.callProxyInstantAsInstant(proxy);
            if (instant == null) {
                throw new AssertionError("The returned instant must not be null.");
            }
            return instant;
        }
    }.getCallTarget();

    final CallTarget asDuration = new GuestToHostRootNode(api.getProxyDurationClass(), "asDuration") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            Duration duration = api.callProxyDurationAsDuration(proxy);
            if (duration == null) {
                throw new AssertionError("The returned duration must not be null.");
            }
            return duration;
        }
    }.getCallTarget();

    final CallTarget getIterator = new GuestToHostRootNode(api.getProxyIterableClass(), "getIterator") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return api.callProxyIterableGetIterator(proxy);
        }
    }.getCallTarget();

    final CallTarget hasIteratorNextElement = new GuestToHostRootNode(api.getProxyIteratorClass(), "hasIteratorNextElement") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return api.callProxyIteratorHasNext(proxy);
        }
    }.getCallTarget();

    final CallTarget getIteratorNextElement = new GuestToHostRootNode(api.getProxyIteratorClass(), "getIteratorNextElement") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) throws StopIterationException, UnsupportedMessageException {
            try {
                return api.callProxyIteratorGetNext(proxy);
            } catch (NoSuchElementException e) {
                throw StopIterationException.create();
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }
    }.getCallTarget();

    final CallTarget hasHashEntry = new GuestToHostRootNode(api.getProxyHashMapClass(), "hasEntry") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) throws InteropException {
            return api.callProxyHashMapHasHashEntry(proxy, arguments[ARGUMENT_OFFSET]);
        }
    }.getCallTarget();

    final CallTarget getHashSize = new GuestToHostRootNode(api.getProxyHashMapClass(), "getSize") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) throws InteropException {
            return api.callProxyHashMapGetHashSize(proxy);
        }
    }.getCallTarget();

    final CallTarget getHashValue = new GuestToHostRootNode(api.getProxyHashMapClass(), "getValue") {
        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) throws InteropException {
            try {
                return api.callProxyHashMapGetHashValue(proxy, arguments[ARGUMENT_OFFSET]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }
    }.getCallTarget();

    final CallTarget putHashEntry = new GuestToHostRootNode(api.getProxyHashMapClass(), "putEntry") {
        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) throws InteropException {
            try {
                api.callProxyHashMapPutHashEntry(proxy, arguments[ARGUMENT_OFFSET], arguments[ARGUMENT_OFFSET + 1]);
                return null;
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }
    }.getCallTarget();

    final CallTarget removeHashEntry = new GuestToHostRootNode(api.getProxyHashMapClass(), "removeEntry") {
        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) throws InteropException {
            try {
                return api.callProxyHashMapRemoveHashEntry(proxy, arguments[ARGUMENT_OFFSET]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }
    }.getCallTarget();

    final CallTarget getHashEntriesIterator = new GuestToHostRootNode(api.getProxyHashMapClass(), "getEntriesIterator") {
        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) throws InteropException {
            return api.callProxyHashMapGetEntriesIterator(proxy);
        }
    }.getCallTarget();
}

class GuestToHostCodeCacheBase {
    final APIAccess api;

    GuestToHostCodeCacheBase(APIAccess api) {
        this.api = api;
    }
}
