/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode;

/**
 * Represents the meta-information of an exception handler in a bytecode interpreter. An exception
 * handler stores information for bytecode index ranges that determine how an exception should be
 * handled at a particular location.
 *
 * @see BytecodeNode#getExceptionHandlers()
 * @see BytecodeLocation#getExceptionHandlers()
 * @since 24.1
 */
public abstract class ExceptionHandler {

    /**
     * Internal constructor for generated code. Do not use.
     *
     * @since 24.1
     */
    protected ExceptionHandler(Object token) {
        BytecodeRootNodes.checkToken(token);
    }

    /**
     * Returns a kind that determine whether handler is a custom or a special exception handler.
     *
     * @see HandlerKind
     * @since 24.1
     */
    public abstract HandlerKind getKind();

    /**
     * Returns the start bytecode index of this exception handler (inclusive).
     *
     * @since 24.1
     */
    public abstract int getStartIndex();

    /**
     * Returns the end bytecode index of this exception handler (exclusive).
     *
     * @since 24.1
     */
    public abstract int getEndIndex();

    /**
     * Returns the target bytecode index of this exception handler if this exception handler is of
     * kind {@link HandlerKind#CUSTOM}.
     *
     * @throws UnsupportedOperationException for handlers not of kind {@link HandlerKind#CUSTOM}
     * @since 24.1
     */
    public int getHandlerIndex() throws UnsupportedOperationException {
        throw new UnsupportedOperationException("getHandlerIndex() is not supported for handler kind: " + getKind());
    }

    /**
     * Returns the tag tree of this exception handler if this exception handler is of kind
     * {@link HandlerKind#TAG}.
     *
     * @throws UnsupportedOperationException for handlers not of kind {@link HandlerKind#TAG}
     * @since 24.1
     */
    public TagTree getTagTree() throws UnsupportedOperationException {
        throw new UnsupportedOperationException("getTagTree() is not supported for handler kind: " + getKind());
    }

    @Override
    public final String toString() {
        String description;
        switch (getKind()) {
            case CUSTOM:
                description = String.format("handler %04x", getHandlerIndex());
                break;
            case EPILOG:
                description = "epilog.exceptional";
                break;
            case TAG:
                description = String.format("tag.exceptional %s", ((TagTreeNode) getTagTree()).getTagsString());
                break;
            default:
                throw new AssertionError("Invalid handler kind");
        }
        return String.format("[%04x .. %04x] %s", getStartIndex(), getEndIndex(), description);
    }

    /**
     * Represents the kind of the exception handler.
     */
    public enum HandlerKind {

        /**
         * Handler directly emitted with the bytecode builder.
         */
        CUSTOM,

        /**
         * A special handler which handles tag instrumentation exceptional behavior. Only emitted if
         * {@link GenerateBytecode#enableTagInstrumentation() tag instrumentation} is enabled.
         */
        TAG,

        /**
         * A special handler which handles epilog exceptional behavior. Only emitted if the language
         * specifies an {@link EpilogExceptional} annotated operation.
         */
        EPILOG,

    }
}