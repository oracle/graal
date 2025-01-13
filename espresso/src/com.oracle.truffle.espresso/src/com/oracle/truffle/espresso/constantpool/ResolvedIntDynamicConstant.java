/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.constantpool;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.EspressoFrame;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class ResolvedIntDynamicConstant implements ResolvedDynamicConstant {
    final int resolved;
    final JavaKind kind;

    public ResolvedIntDynamicConstant(int resolved, JavaKind kind) {
        this.resolved = resolved;
        this.kind = kind;
    }

    @Override
    public void putResolved(VirtualFrame frame, int top, BytecodeNode node) {
        EspressoFrame.putInt(frame, top, resolved);
    }

    @Override
    public Object value() {
        return resolved;
    }

    @Override
    public JavaKind getKind() {
        return kind;
    }

    @Override
    public StaticObject guestBoxedValue(Meta meta) {
        Object value = switch (kind) {
            case Boolean -> resolved != 0;
            case Byte -> (byte) resolved;
            case Short -> (short) resolved;
            case Char -> (char) resolved;
            case Int -> resolved;
            default ->
                throw EspressoError.shouldNotReachHere(kind.toString());
        };
        return Meta.box(meta, value);
    }
}
