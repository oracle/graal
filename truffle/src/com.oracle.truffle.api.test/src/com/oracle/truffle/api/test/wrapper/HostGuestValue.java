/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.wrapper;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.test.wrapper.HostEntryPoint.GuestExceptionPointer;
import com.oracle.truffle.api.test.wrapper.HostEntryPoint.HostValuePointer;
import com.oracle.truffle.api.utilities.TriState;

@ExportLibrary(ReflectionLibrary.class)
class HostGuestValue implements TruffleObject {

    static final Object DEFAULT = new Object();
    static final ReflectionLibrary REFLECTION = ReflectionLibrary.getFactory().getUncached(DEFAULT);
    final long contextId;
    final long id;
    final HostEntryPoint hostToGuest;

    HostGuestValue(HostEntryPoint hostToGuest, long contextId, long id) {
        this.hostToGuest = hostToGuest;
        this.contextId = contextId;
        this.id = id;
    }

    @ExportMessage
    @TruffleBoundary
    final Object send(Message message, Object... args) throws Exception {
        if (message.getLibraryClass() == InteropLibrary.class) {
            return sendImpl(hostToGuest, contextId, id, message, args);
        } else {
            // we only support remoting interop calls
            return REFLECTION.send(DEFAULT, message, args);
        }
    }

    static Object sendImpl(HostEntryPoint hostToGuest, long contextId, long guestId, Message message, Object... args) throws AbstractTruffleException {
        Object[] marshalledArgs = marshalToRemote(hostToGuest, args);
        Object result = hostToGuest.remoteMessage(contextId, guestId, message, marshalledArgs);
        return unmarshallAtHost(hostToGuest, contextId, result);
    }

    static Object unmarshallAtHost(HostEntryPoint hostToGuest, long contextId, Object result) throws AbstractTruffleException {
        if (result instanceof HostValuePointer) {
            return hostToGuest.unmarshallHost(Object.class, ((HostValuePointer) result).id);
        } else if (result instanceof GuestExceptionPointer) {
            throw new HostGuestException(hostToGuest, contextId, ((GuestExceptionPointer) result).id, ((GuestExceptionPointer) result).message);
        } else if (result instanceof Long) {
            return new HostGuestValue(hostToGuest, contextId, (long) result);
        } else if (isGuestPrimitive(result)) {
            return result;
        } else {
            throw new UnsupportedOperationException(result.getClass().getName());
        }
    }

    static boolean isGuestPrimitive(Object result) {
        return result instanceof String || result instanceof TruffleString || result instanceof Boolean || result instanceof Integer || result instanceof TriState || result instanceof ExceptionType ||
                        result instanceof SourceSection;
    }

    static Object[] marshalToRemote(HostEntryPoint hostToGuest, Object[] args) {
        Object[] newArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof Long) {
                // TODO GR-38632 handle long
                throw new UnsupportedOperationException();
            } else if (arg instanceof HostGuestValue) {
                newArgs[i] = ((HostGuestValue) arg).id;
            } else if (arg instanceof TruffleObject) {
                newArgs[i] = new HostValuePointer(hostToGuest.registerHost(arg));
            } else if (arg instanceof Object[]) {
                newArgs[i] = marshalToRemote(hostToGuest, (Object[]) arg);
            } else if (arg instanceof InteropLibrary) {
                // TODO GR-38632 many more types to serialize
                newArgs[i] = null;
            } else if (arg instanceof String) {
                newArgs[i] = arg;
            } else {
                // TODO GR-38632 many more types to serialize
                throw new UnsupportedOperationException(arg.getClass().getName());
            }
        }
        return newArgs;
    }

}
