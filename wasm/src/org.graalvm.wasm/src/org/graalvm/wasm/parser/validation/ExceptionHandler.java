/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.wasm.constants.ExceptionHandlerType;
import org.graalvm.wasm.parser.bytecode.BytecodeFixup;

/**
 * Representation of an exception handler during parsing.
 *
 * <pre>
 * Encoded exception-table layout:
 *
 *   from (4 bytes) | to (4 bytes) | type (1 byte) | tag (4 bytes) | target (4 bytes)
 *
 * Field meanings by handler kind:
 *   CATCH, CATCH_REF, CATCH_ALL, CATCH_ALL_REF, LEGACY_CATCH, LEGACY_CATCH_ALL:
 *     target = transfer destination bytecode offset
 *
 *   LEGACY_DELEGATE:
 *     target = exception-table search continuation offset
 * </pre>
 */
public final class ExceptionHandler implements BytecodeFixup {
    public static final int FROM_OFFSET = 0;
    public static final int TO_OFFSET = 4;
    public static final int TYPE_OFFSET = 8;
    public static final int TAG_OFFSET = 9;
    public static final int TARGET_OFFSET = 13;
    public static final int SIZE = 17;

    /** {@link ExceptionHandlerType}. */
    private final int type;
    /** Tag index expected by typed catches, or {@code -1} when no tag match is required. */
    private final int tag;
    /** Encoded handler target. Its meaning depends on the handler kind. */
    private int target = -1;

    public ExceptionHandler(int type, int tag) {
        this.type = type;
        this.tag = tag;
    }

    public ExceptionHandler(int type, int tag, int target) {
        this(type, tag);
        this.target = target;
    }

    /**
     * Returns the encoded handler kind.
     */
    public int type() {
        return type;
    }

    /**
     * Returns the tag index matched by typed catches, or {@code -1} for untyped handlers.
     */
    public int tag() {
        return tag;
    }

    /**
     * Returns the encoded handler target. For catch handlers, this is a bytecode transfer target.
     * For legacy delegate handlers, this is an exception-table search continuation.
     */
    public int target() {
        return target;
    }

    /**
     * Patches the encoded handler target.
     */
    @Override
    public void patch(int targetOffset) {
        this.target = targetOffset;
    }

    @Override
    public String toString() {
        return ExceptionHandlerType.toString(type) + (tag != -1 ? "(tag=" + tag + ")" : "") + "->" + target;
    }
}
