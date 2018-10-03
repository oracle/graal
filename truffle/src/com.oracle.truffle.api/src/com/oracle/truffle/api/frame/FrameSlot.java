/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * A slot in a {@link Frame} and {@link FrameDescriptor} that can store a value of a given type.
 *
 * @since 0.8 or earlier
 */
public final class FrameSlot implements Cloneable {

    final FrameDescriptor descriptor;
    private final Object identifier;
    private final Object info;
    final int index;
    /*
     * The FrameSlot cannot be made immutable by moving the kind field to FrameDescriptor, because
     * it would force getFrameSlotKind and setFrameSlotKind to check frameSlot removal which would
     * require locking the FrameDescriptor, instead of current simple read from the volatile kind
     * field.
     */
    @CompilationFinal volatile FrameSlotKind kind;

    FrameSlot(FrameDescriptor descriptor, Object identifier, Object info, FrameSlotKind kind, int index) {
        this.descriptor = descriptor;
        this.identifier = identifier;
        this.info = info;
        this.index = index;
        this.kind = kind;
    }

    /**
     * Identifier of the slot.
     *
     * @return value as specified in {@link FrameDescriptor#addFrameSlot(java.lang.Object)}
     *         parameter
     * @since 0.8 or earlier
     */
    public Object getIdentifier() {
        return identifier;
    }

    /**
     * Information about the slot.
     *
     * @return value as specified as second parameter of
     *         {@link FrameDescriptor#addFrameSlot(java.lang.Object, java.lang.Object, com.oracle.truffle.api.frame.FrameSlotKind)}
     * @since 0.8 or earlier
     */
    public Object getInfo() {
        return info;
    }

    /**
     * Index of the slot in the {@link FrameDescriptor}.
     *
     * @return position of the slot computed after
     *         {@link FrameDescriptor#addFrameSlot(java.lang.Object, java.lang.Object, com.oracle.truffle.api.frame.FrameSlotKind)
     *         adding} it.
     * @since 0.8 or earlier
     * @deprecated in 1.0 without replacement
     */
    @Deprecated
    public int getIndex() {
        return index;
    }

    /**
     * Kind of the slot. Specified either at
     * {@link FrameDescriptor#addFrameSlot(java.lang.Object, com.oracle.truffle.api.frame.FrameSlotKind)
     * creation time} or updated via
     * {@link FrameDescriptor#setFrameSlotKind(FrameSlot, FrameSlotKind)} method.
     *
     * @return current kind of this slot
     * @since 0.8 or earlier
     * @deprecated in 1.0 use {@link FrameDescriptor#getFrameSlotKind(FrameSlot)} instead.
     */
    @Deprecated
    public FrameSlotKind getKind() {
        return descriptor.getFrameSlotKind(this);
    }

    /**
     * Changes the kind of this slot. Change of the slot kind is done on <em>slow path</em> and
     * invalidates assumptions about version of the {@link FrameDescriptor descriptor} it belongs
     * to.
     *
     * @param kind new kind of the slot
     * @since 0.8 or earlier
     * @deprecated in 1.0 use {@link FrameDescriptor#setFrameSlotKind(FrameSlot, FrameSlotKind)}
     *             instead.
     */
    @Deprecated
    public void setKind(final FrameSlotKind kind) {
        descriptor.setFrameSlotKind(this, kind);
    }

    /** @since 0.8 or earlier */
    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation("do not call FrameSlot.toString from compiled code");
        return "[" + index + "," + identifier + "," + kind + "]";
    }
}
