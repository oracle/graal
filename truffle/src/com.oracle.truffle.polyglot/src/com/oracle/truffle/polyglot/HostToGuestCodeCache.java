/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.graalvm.polyglot.Value;
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
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.polyglot.HostMethodDesc.SingleMethod;
import com.oracle.truffle.polyglot.HostMethodDesc.SingleMethod.MHBase;

final class HostToGuestCodeCache {

    final CallTarget methodHandleHostInvoke = GuestToHostRootNode.createGuestToHost(new GuestToHostRootNode(HostObject.class, "doInvoke") {
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
    });

    final CallTarget reflectionHostInvoke = GuestToHostRootNode.createGuestToHost(new GuestToHostRootNode(HostObject.class, "doInvoke") {
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
    });

    final CallTarget execute = createGuestToHost(new GuestToHostRootNode(ProxyExecutable.class, "execute") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) throws UnsupportedMessageException {
            try {
                return ((ProxyExecutable) proxy).execute((Value[]) arguments[ARGUMENT_OFFSET]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }
    });

    final CallTarget asPointer = createGuestToHost(new GuestToHostRootNode(ProxyNativeObject.class, "asPointer") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return ((ProxyNativeObject) proxy).asPointer();
        }
    });

    final CallTarget instantiate = createGuestToHost(new GuestToHostRootNode(ProxyInstantiable.class, "newInstance") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) throws UnsupportedMessageException {
            try {
                return ((ProxyInstantiable) proxy).newInstance((Value[]) arguments[ARGUMENT_OFFSET]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }
    });

    final CallTarget arrayGet = createGuestToHost(new GuestToHostRootNode(ProxyArray.class, "get") {

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
        private Object boundaryGet(ProxyArray proxy, long index) {
            return proxy.get(index);
        }
    });

    final CallTarget arraySet = createGuestToHost(new GuestToHostRootNode(ProxyArray.class, "set") {

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
        private void boundarySet(ProxyArray proxy, long index, Value value) {
            proxy.set(index, value);
        }
    });

    final CallTarget arrayRemove = createGuestToHost(new GuestToHostRootNode(ProxyArray.class, "remove") {

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
        private boolean boundaryRemove(ProxyArray proxy, long index) {
            return proxy.remove(index);
        }
    });

    final CallTarget arraySize = createGuestToHost(new GuestToHostRootNode(ProxyArray.class, "getSize") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return ((ProxyArray) proxy).getSize();
        }
    });

    final CallTarget memberKeys = createGuestToHost(new GuestToHostRootNode(ProxyObject.class, "getMemberKeys") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return ((ProxyObject) proxy).getMemberKeys();
        }
    });

    final CallTarget getMember = createGuestToHost(new GuestToHostRootNode(ProxyObject.class, "getMember") {

        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws UnsupportedMessageException {
            try {
                return boundaryGetMember((ProxyObject) proxy, (String) arguments[ARGUMENT_OFFSET]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }

        @TruffleBoundary
        private Object boundaryGetMember(ProxyObject proxy, String argument) {
            return proxy.getMember(argument);
        }
    });

    final CallTarget putMember = createGuestToHost(new GuestToHostRootNode(ProxyObject.class, "putMember") {

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
        private void boundaryPutMember(ProxyObject proxy, String member, Value value) {
            proxy.putMember(member, value);
        }
    });

    final CallTarget removeMember = createGuestToHost(new GuestToHostRootNode(ProxyObject.class, "removeMember") {

        @Override
        protected Object executeImpl(Object proxy, Object[] arguments) throws UnsupportedMessageException {
            try {
                return removeBoundary((ProxyObject) proxy, (String) arguments[ARGUMENT_OFFSET]);
            } catch (UnsupportedOperationException e) {
                throw UnsupportedMessageException.create();
            }
        }

        @TruffleBoundary
        private boolean removeBoundary(ProxyObject proxy, String member) {
            return proxy.removeMember(member);
        }
    });

    final CallTarget hasMember = createGuestToHost(new GuestToHostRootNode(ProxyObject.class, "hasMember") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            return ((ProxyObject) proxy).hasMember((String) arguments[ARGUMENT_OFFSET]);
        }
    });

    final CallTarget asTimezone = createGuestToHost(new GuestToHostRootNode(ProxyTimeZone.class, "asTimeZone") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            ZoneId zone = ((ProxyTimeZone) proxy).asTimeZone();
            if (zone == null) {
                throw CompilerDirectives.shouldNotReachHere("The returned zone must not be null.");
            }
            return zone;
        }
    });

    final CallTarget asDate = createGuestToHost(new GuestToHostRootNode(ProxyDate.class, "asDate") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            LocalDate date = ((ProxyDate) proxy).asDate();
            if (date == null) {
                throw new AssertionError("The returned date must not be null.");
            }
            return date;
        }
    });
    final CallTarget asTime = createGuestToHost(new GuestToHostRootNode(ProxyTime.class, "asTime") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            LocalTime time = ((ProxyTime) proxy).asTime();
            if (time == null) {
                throw new AssertionError("The returned time must not be null.");
            }
            return time;
        }
    });

    final CallTarget asInstant = createGuestToHost(new GuestToHostRootNode(ProxyInstant.class, "asInstant") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            Instant instant = ((ProxyInstant) proxy).asInstant();
            if (instant == null) {
                throw new AssertionError("The returned instant must not be null.");
            }
            return instant;
        }
    });

    final CallTarget asDuration = createGuestToHost(new GuestToHostRootNode(ProxyDuration.class, "asDuration") {

        @Override
        @TruffleBoundary
        protected Object executeImpl(Object proxy, Object[] arguments) {
            Duration duration = ((ProxyDuration) proxy).asDuration();
            if (duration == null) {
                throw new AssertionError("The returned duration must not be null.");
            }
            return duration;
        }
    });

}
