/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.resolver;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.classfile.bytecode.Bytecodes;
import com.oracle.truffle.espresso.meta.EspressoError;

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
        switch (opcode) {
            case Bytecodes.INVOKESTATIC:
                return Static;
            case Bytecodes.INVOKESPECIAL:
                return Special;
            case Bytecodes.INVOKEVIRTUAL:
                return Virtual;
            case Bytecodes.INVOKEINTERFACE:
                return Interface;
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(Bytecodes.nameOf(opcode));
        }
    }

    public static CallSiteType fromRefKind(int refKind) {
        switch (refKind) {
            case Constants.REF_invokeVirtual:
                return Virtual;
            case Constants.REF_invokeStatic:
                return Static;
            case Constants.REF_invokeSpecial: // fallthrough
            case Constants.REF_newInvokeSpecial:
                return Special;
            case Constants.REF_invokeInterface:
                return Interface;
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("refKind: " + refKind);
        }
    }
}
