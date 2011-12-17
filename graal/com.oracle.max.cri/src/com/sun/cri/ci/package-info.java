/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
/**
 * The compiler-provided part of the bi-directional interface between the compiler and the runtime system of a virtual machine for the instruction set defined in
 * {@link com.sun.cri.bytecode.Bytecodes}.
 *
 * The target hardware architecture is represented by {@link com.sun.cri.ci.CiArchitecture} and the specific target machine
 * environment for a compiler instance is represented by {@link com.sun.cri.ci.CiTarget}.
 * <p>
 * A {@code CiResult} encapsulates
 * {@linkplain com.sun.cri.ci.CiStatistics compilation statistics}, possible {@linkplain com.sun.cri.ci.CiBailout error state}
 * and the {@linkplain com.sun.cri.ci.CiTargetMethod compiled code and metadata}.
 * {@link com.sun.cri.ci.CiCodePos} and {@link com.sun.cri.ci.CiDebugInfo} provide detailed information to the
 * runtime to support debugging and deoptimization of the compiled code.
 * <p>
 * The compiler manipulates {@link com.sun.cri.ci.CiValue} instances that have a {@link com.sun.cri.ci.CiKind}, and are
 * immutable. A concrete {@link com.sun.cri.ci.CiValue value} is one of the following subclasses:
 * <ul>
 * <li>{@link com.sun.cri.ci.CiConstant}: a constant value.
 * <li>{@link com.sun.cri.ci.CiRegisterValue}: a value stored in a {@linkplain com.sun.cri.ci.CiRegister target machine register}.
 * <li>{@link com.sun.cri.ci.CiStackSlot}: a spill slot or an outgoing stack-based argument in a method's frame.
 * <li>{@link com.sun.cri.ci.CiAddress}: an address in target machine memory.
 * <li>{@link com.sun.cri.ci.CiVariable}: a value (cf. virtual register) that is yet to be bound to a target machine location (physical register or memory address).
 *</ul>
 */
package com.sun.cri.ci;

