/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.frame;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Exception thrown if the frame slot kind does not match the expected kind.
 *
 * @since 0.8 or earlier
 */
public final class FrameSlotTypeException extends IllegalStateException {

    private static final long serialVersionUID = 6972120475215757452L;

    private final int slot;
    private final FrameSlotKind expectedKind;
    private final FrameSlotKind actualKind;

    /**
     * @since 0.8 or earlier
     * @deprecated use {@link #create(int, FrameSlotKind, FrameSlotKind)} instead
     */
    @Deprecated
    public FrameSlotTypeException() {
        this.slot = -1;
        this.expectedKind = null;
        this.actualKind = null;
    }

    FrameSlotTypeException(int slot, FrameSlotKind expectedTag, FrameSlotKind actualTag) {
        this.slot = slot;
        this.expectedKind = expectedTag;
        this.actualKind = actualTag;
    }

    /**
     * {@inheritDoc}
     *
     * @since 24.2
     */
    @Override
    @TruffleBoundary
    public String getMessage() {
        if (slot < 0) {
            // legacy support
            return null;
        }
        return String.format("Frame slot kind %s expected, but got %s at frame slot index %s.", expectedKind, actualKind, slot);
    }

    /**
     * Returns the frame slot index that was read.
     *
     * @since 24.2
     */
    public int getSlot() {
        return slot;
    }

    /**
     * Returns the expected frame slot kind when the exception occurred.
     *
     * @since 24.2
     */
    public FrameSlotKind getExpectedKind() {
        return expectedKind;
    }

    /**
     * Returns the actual frame slot kind when the exception occurred.
     *
     * @since 24.2
     */
    public FrameSlotKind getActualKind() {
        return actualKind;
    }

    /**
     * Creates a new frame slot type exception.
     *
     * @param slot the frame slot index used when reading
     * @param expectedKind the expected frame slot kind when reading
     * @param actualKind the actual frame slot kind when reading
     *
     * @since 24.2
     */
    public static FrameSlotTypeException create(int slot, FrameSlotKind expectedKind, FrameSlotKind actualKind) {
        return new FrameSlotTypeException(slot, expectedKind, actualKind);
    }
}
