/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.target.amd64;


/**
 * A collection of AMD64 specific opcodes that is convenient for a static import.
 * Since every defined opcode is in its own enum, accessing them without this class is
 * more verbose.
 */
public class AMD64StandardOpcode {
    public static final AMD64MoveOpcode.MoveOpcode MOVE = AMD64MoveOpcode.MoveOpcode.MOVE;
    public static final AMD64MoveOpcode.LoadOpcode LOAD = AMD64MoveOpcode.LoadOpcode.LOAD;
    public static final AMD64MoveOpcode.StoreOpcode STORE = AMD64MoveOpcode.StoreOpcode.STORE;
    public static final AMD64MoveOpcode.LeaOpcode LEA = AMD64MoveOpcode.LeaOpcode.LEA;
    public static final AMD64MoveOpcode.LeaStackBlockOpcode LEA_STACK_BLOCK = AMD64MoveOpcode.LeaStackBlockOpcode.LEA_STACK_BLOCK;
    public static final AMD64MoveOpcode.MembarOpcode MEMBAR = AMD64MoveOpcode.MembarOpcode.MEMBAR;
    public static final AMD64MoveOpcode.NullCheckOpcode NULL_CHECK = AMD64MoveOpcode.NullCheckOpcode.NULL_CHECK;
    public static final AMD64MoveOpcode.CompareAndSwapOpcode CAS = AMD64MoveOpcode.CompareAndSwapOpcode.CAS;

    public static final AMD64ControlFlowOpcode.LabelOpcode LABEL = AMD64ControlFlowOpcode.LabelOpcode.LABEL;
    public static final AMD64ControlFlowOpcode.ReturnOpcode RETURN = AMD64ControlFlowOpcode.ReturnOpcode.RETURN;
    public static final AMD64ControlFlowOpcode.JumpOpcode JUMP = AMD64ControlFlowOpcode.JumpOpcode.JUMP;
    public static final AMD64ControlFlowOpcode.BranchOpcode BRANCH = AMD64ControlFlowOpcode.BranchOpcode.BRANCH;
    public static final AMD64ControlFlowOpcode.FloatBranchOpcode FLOAT_BRANCH = AMD64ControlFlowOpcode.FloatBranchOpcode.FLOAT_BRANCH;
    public static final AMD64ControlFlowOpcode.TableSwitchOpcode TABLE_SWITCH = AMD64ControlFlowOpcode.TableSwitchOpcode.TABLE_SWITCH;
    public static final AMD64ControlFlowOpcode.CondMoveOpcode CMOVE = AMD64ControlFlowOpcode.CondMoveOpcode.CMOVE;
    public static final AMD64ControlFlowOpcode.FloatCondMoveOpcode FLOAT_CMOVE = AMD64ControlFlowOpcode.FloatCondMoveOpcode.FLOAT_CMOVE;
}
