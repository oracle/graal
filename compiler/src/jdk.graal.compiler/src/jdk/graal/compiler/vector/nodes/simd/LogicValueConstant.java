/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.simd;

import jdk.vm.ci.meta.Constant;

/**
 * A constant element (bit) in an opmask (e.g a k register in x86). A constant opmask would be an
 * {@link SimdConstant} of {@code LogicValueConstant}s.
 */
public final class LogicValueConstant implements Constant {
    public static final LogicValueConstant TRUE = new LogicValueConstant(true);
    public static final LogicValueConstant FALSE = new LogicValueConstant(false);

    private final boolean value;

    private LogicValueConstant(boolean value) {
        this.value = value;
    }

    public static LogicValueConstant ofBoolean(boolean value) {
        return value ? TRUE : FALSE;
    }

    public boolean value() {
        return value;
    }

    @Override
    public boolean isDefaultForKind() {
        return !value;
    }

    @Override
    public String toValueString() {
        return value ? "1" : "0";
    }
}
