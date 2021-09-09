/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.exception;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitchBoundary;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public final class WasmException extends AbstractTruffleException {
    private static final long serialVersionUID = -84137683950579647L;

    private final Failure failure;

    private WasmException(String message, Node location, Failure failure) {
        super(message, location);
        this.failure = failure;
    }

    public static WasmException create(Failure failure, Node location, String message) {
        return new WasmException(message, location, failure);
    }

    public static WasmException create(Failure failure, Node location) {
        return create(failure, location, failure.name);
    }

    public static WasmException create(Failure failure, String message) {
        return create(failure, null, message);
    }

    public static WasmException create(Failure failure) {
        return create(failure, null, failure.name);
    }

    @TruffleBoundary
    public static WasmException format(Failure failure, String format, Object... args) {
        return create(failure, String.format(format, args));
    }

    @TruffleBoundary
    public static WasmException format(Failure failure, Node location, String format, Object... args) {
        return create(failure, location, String.format(format, args));
    }

    @BytecodeInterpreterSwitchBoundary
    @TruffleBoundary
    public static WasmException format(Failure failure, Node location, String format, Object arg) {
        return create(failure, location, String.format(format, arg));
    }

    @BytecodeInterpreterSwitchBoundary
    @TruffleBoundary
    public static WasmException format(Failure failure, Node location, String format, int arg) {
        return create(failure, location, String.format(format, arg));
    }

    @ExportMessage
    public boolean hasMembers() {
        return true;
    }

    @ExportMessage
    public ExceptionType getExceptionType() {
        switch (failure.type) {
            case MALFORMED:
            case INVALID:
                return ExceptionType.PARSE_ERROR;
            case UNLINKABLE:
            case INTERNAL:
            case EXHAUSTION:
            case TRAP:
            default:
                return ExceptionType.RUNTIME_ERROR;
        }
    }

    @ExportMessage
    @SuppressWarnings({"unused", "static-method"})
    Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    public Object readMember(String member) throws UnknownIdentifierException {
        switch (member) {
            case "message":
                return getMessage();
            case "failureType":
                return failure.type.name;
            case "failure":
                return failure.name;
            default:
                throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    @TruffleBoundary
    public boolean isMemberReadable(String member) {
        switch (member) {
            case "message":
            case "failureType":
            case "failure":
                return true;
            default:
                return false;
        }
    }
}
