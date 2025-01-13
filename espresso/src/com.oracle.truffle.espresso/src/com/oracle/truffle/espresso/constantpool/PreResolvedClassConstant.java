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

import java.nio.ByteBuffer;
import java.util.Objects;

import com.oracle.truffle.espresso.classfile.constantpool.ClassConstant;
import com.oracle.truffle.espresso.classfile.constantpool.Resolvable;
import com.oracle.truffle.espresso.impl.Klass;

/**
 * Constant Pool patching inserts already resolved constants in the constant pool. However, at the
 * time of patching, we do not have a Runtime CP. Therefore, we help the CP by inserting a
 * Pre-Resolved constant.
 * <p>
 * This is also used to Pre-resolve anonymous classes.
 */
public final class PreResolvedClassConstant implements ClassConstant, Resolvable {
    private final Klass resolved;

    PreResolvedClassConstant(Klass resolved) {
        this.resolved = Objects.requireNonNull(resolved);
    }

    public Klass getResolved() {
        return resolved;
    }

    @Override
    public void dump(ByteBuffer buf) {
        buf.putChar((char) 0);
    }
}
