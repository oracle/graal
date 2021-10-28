/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.polyglot;

/**
 * An exception thrown if a buffer access if out of bounds.
 *
 * @see #getByteOffset()
 * @see #getLength()
 * @since 21.1
 */
public final class InvalidBufferOffsetException extends InteropException {

    private static final long serialVersionUID = 2710415479029780200L;

    private final long byteOffset;
    private final long length;

    private InvalidBufferOffsetException(long byteOffset, long length) {
        super(null);
        this.byteOffset = byteOffset;
        this.length = length;
    }

    private InvalidBufferOffsetException(long byteOffset, long length, Throwable cause) {
        super(null, cause);
        this.byteOffset = byteOffset;
        this.length = length;
    }

    /**
     * Returns the start byte offset of the invalid access from the start of the buffer.
     *
     * @since 21.1
     */
    public long getByteOffset() {
        return byteOffset;
    }

    /**
     * Returns the length of the accessed memory region in bytes starting from {@link #getByteOffset
     * the start byte offset}.
     *
     * @since 21.1
     */
    public long getLength() {
        return length;
    }

    /**
     * {@inheritDoc}
     *
     * @since 21.1
     */
    @Override
    public String getMessage() {
        return "Invalid buffer access of length " + length + " at byteOffset " + byteOffset + ".";
    }

    /**
     * Creates an {@link InvalidBufferOffsetException} to indicate that a buffer access is invalid.
     *
     * @param byteOffset the start byteOffset of the invalid access
     * @param length the length of the accessed memory region in bytes starting from
     *            {@code byteOffset}
     * @since 21.1
     */
    public static InvalidBufferOffsetException create(long byteOffset, long length) {
        return new InvalidBufferOffsetException(byteOffset, length);
    }

    /**
     * Creates an {@link InvalidBufferOffsetException} to indicate that a buffer access is invalid.
     * <p>
     * In addition a cause may be provided. The cause should only be set if the guest language code
     * caused this problem. An example for this is a language specific proxy mechanism that invokes
     * guest language code to describe an object. If the guest language code fails to execute and
     * this interop exception is a valid interpretation of the error, then the error should be
     * provided as cause.
     *
     * @param byteOffset the start byteOffset of the invalid access
     * @param length the length of the accessed memory region in bytes starting from
     *            {@code byteOffset}.
     * @param cause the guest language exception that caused the error.
     * @since 21.1
     */
    public static InvalidBufferOffsetException create(long byteOffset, long length, Throwable cause) {
        return new InvalidBufferOffsetException(byteOffset, length, cause);
    }

}
