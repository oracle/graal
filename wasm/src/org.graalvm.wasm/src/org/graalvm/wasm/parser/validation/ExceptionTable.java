/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.parser.validation;

import org.graalvm.wasm.parser.bytecode.RuntimeBytecodeGen;

/**
 * Represents exception handlers in the same range during parsing.
 */
public final class ExceptionTable {
    private final int from;
    private int to;
    private final ExceptionHandler[] handlers;

    ExceptionTable(int from, ExceptionHandler[] handlers) {
        this.from = from;
        this.to = 0;
        this.handlers = handlers;
    }

    void setTo(int to) {
        this.to = to;
    }

    void generateExceptionTable(RuntimeBytecodeGen bytecode) {
        assert to != 0 && to >= from : "Invalid exception table range";
        for (ExceptionHandler handler : handlers) {
            if (handler.tag() == -1) {
                // from (4 byte) | to (4 byte) | type (1 byte) | 0x0000_0000 | target (4 byte)
                bytecode.addExceptionHandler(from, to, handler.type(), 0, handler.target());
            } else {
                // from (4 byte) | to (4 byte) | type (1 byte) | tag (4 byte) | target (4 byte)
                bytecode.addExceptionHandler(from, to, handler.type(), handler.tag(), handler.target());
            }
        }
    }
}
