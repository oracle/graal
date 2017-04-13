/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.frame;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * A slot in a {@link Frame} and {@link FrameDescriptor} that can store a value of a given type.
 * 
 * @since 0.8 or earlier
 */
public final class FrameSlot implements Cloneable {

    private final FrameDescriptor descriptor;
    private final Object identifier;
    private final Object info;
    private final int index;
    @CompilationFinal private FrameSlotKind kind;

    /**
     * @deprecated use
     *             {@link FrameDescriptor#addFrameSlot(java.lang.Object, java.lang.Object, com.oracle.truffle.api.frame.FrameSlotKind)}
     *             to create new instance of the slot. This method will be made package private in
     *             the future.
     * @since 0.8 or earlier
     */
    @Deprecated
    public FrameSlot(FrameDescriptor descriptor, Object identifier, Object info, int index, FrameSlotKind kind) {
        this(descriptor, identifier, info, kind, index);
    }

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
     */
    public int getIndex() {
        return index;
    }

    /**
     * Kind of the slot. Specified either at
     * {@link FrameDescriptor#addFrameSlot(java.lang.Object, com.oracle.truffle.api.frame.FrameSlotKind)
     * creation time} or updated via {@link #setKind(com.oracle.truffle.api.frame.FrameSlotKind)}
     * method.
     *
     * @return current kind of this slot
     * @since 0.8 or earlier
     */
    public FrameSlotKind getKind() {
        return kind;
    }

    /**
     * Changes the kind of this slot. Change of the slot kind is done on <em>slow path</em> and
     * invalidates assumptions about version of the {@link #getFrameDescriptor() associated
     * descriptor}.
     *
     * @param kind new kind of the slot
     * @since 0.8 or earlier
     */
    public void setKind(final FrameSlotKind kind) {
        if (this.kind != kind) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.kind = kind;
            this.descriptor.updateVersion();
        }
    }

    /** @since 0.8 or earlier */
    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation("do not call FrameSlot.toString from compiled code");
        return "[" + index + "," + identifier + "," + kind + "]";
    }

    /**
     * Frame descriptor this slot is associated with.
     *
     * @return instance of descriptor that {@link FrameDescriptor#addFrameSlot(java.lang.Object)
     *         created} the slot
     * @since 0.8 or earlier
     */
    public FrameDescriptor getFrameDescriptor() {
        return this.descriptor;
    }
}
