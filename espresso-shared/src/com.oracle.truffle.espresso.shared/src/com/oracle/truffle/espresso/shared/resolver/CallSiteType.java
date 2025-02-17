/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.resolver;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.bytecode.Bytecodes;

/**
 * Describes the type of call-site resolution that should happen for a given call-site. For
 * call-sites in the bytecodes, they correspond 1-to-1 to {@link Bytecodes#INVOKESTATIC},
 * {@link Bytecodes#INVOKESPECIAL}, {@link Bytecodes#INVOKEVIRTUAL},
 * {@link Bytecodes#INVOKEINTERFACE}.
 */
public enum CallSiteType {
    Static,
    Special,
    Virtual,
    Interface;

    public static CallSiteType fromOpCode(int opcode) {
        return switch (opcode) {
            case Bytecodes.INVOKESTATIC -> CallSiteType.Static;
            case Bytecodes.INVOKESPECIAL -> CallSiteType.Special;
            case Bytecodes.INVOKEVIRTUAL -> CallSiteType.Virtual;
            case Bytecodes.INVOKEINTERFACE -> CallSiteType.Interface;
            default -> throw new IllegalStateException(unexpectedBytecodeError(opcode));
        };
    }

    @CompilerDirectives.TruffleBoundary
    private static String unexpectedBytecodeError(int opcode) {
        return "Unexpected bytecode " + Bytecodes.nameOf(opcode);
    }
}
