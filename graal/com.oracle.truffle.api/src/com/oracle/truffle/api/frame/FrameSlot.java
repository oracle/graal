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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * A slot in a frame that can store a value of a given type.
 */
public final class FrameSlot implements Cloneable {

    private final FrameDescriptor descriptor;
    private final Object identifier;
    private final Object info;
    private final int index;
    @CompilationFinal private FrameSlotKind kind;

    public FrameSlot(FrameDescriptor descriptor, Object identifier, Object info, int index, FrameSlotKind kind) {
        this.descriptor = descriptor;
        this.identifier = identifier;
        this.info = info;
        this.index = index;
        this.kind = kind;
    }

    public Object getIdentifier() {
        return identifier;
    }

    public Object getInfo() {
        return info;
    }

    public int getIndex() {
        return index;
    }

    public FrameSlotKind getKind() {
        return kind;
    }

    public void setKind(final FrameSlotKind kind) {
        if (this.kind != kind) {
            CompilerDirectives.transferToInterpreter();
            this.kind = kind;
            this.descriptor.updateVersion();
        }
    }

    @Override
    public String toString() {
        return "[" + index + "," + identifier + "," + kind + "]";
    }

    public FrameDescriptor getFrameDescriptor() {
        return this.descriptor;
    }
}
