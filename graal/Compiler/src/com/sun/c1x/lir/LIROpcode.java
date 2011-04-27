/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.lir;

/**
 * The {@code LirOpcode} enum represents the Operation code of each LIR instruction.
 *
 * @author Marcelo Cintra
 * @author Thomas Wuerthinger
 */
public enum LIROpcode {
    // Checkstyle: stop
    // @formatter:off
    BeginOp0,
        Label,
        OsrEntry,
        Here,
        Info,
        Alloca,
        Breakpoint,
        Pause,
        RuntimeCall,
        Membar,
        Branch,
        CondFloatBranch,
    EndOp0,
    BeginOp1,
        NullCheck,
        Return,
        Lea,
        Neg,
        TableSwitch,
        Move,
        Prefetchr,
        Prefetchw,
        Convert,
        Lsb,
        Msb,
        MonitorAddress,
    EndOp1,
    BeginOp2,
        Cmp,
        Cmpl2i,
        Ucmpfd2i,
        Cmpfd2i,
        Cmove,
        Add,
        Sub,
        Mul,
        Div,
        Rem,
        Sqrt,
        Abs,
        Sin,
        Cos,
        Tan,
        Log,
        Log10,
        LogicAnd,
        LogicOr,
        LogicXor,
        Shl,
        Shr,
        Ushr,
        Throw,
        Unwind,
        CompareTo,
    EndOp2,
    BeginOp3,
        Idiv,
        Irem,
        Ldiv,
        Lrem,
        Wdiv,
        Wdivi,
        Wrem,
        Wremi,
    EndOp3,
    NativeCall,
    DirectCall,
    ConstDirectCall,
    IndirectCall,
    TemplateCall,
    InstanceOf,
    CheckCast,
    StoreCheck,
    CasLong,
    CasWord,
    CasObj,
    CasInt,
    Xir,
    // @formatter:on
    // Checkstyle: resume
}
