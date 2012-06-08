/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * {@link com.oracle.graal.compiler.graphbuilder.Bytecodes}.
 *
 * The target hardware architecture is represented by {@link com.oracle.graal.api.code.CiArchitecture} and the specific target machine
 * environment for a compiler instance is represented by {@link com.oracle.graal.api.code.CiTarget}.
 * <p>
 * A {@code CiResult} encapsulates
 * {@linkplain com.oracle.max.cri.ci.CiStatistics compilation statistics}, possible {@linkplain com.oracle.graal.api.code.CiBailout error state}
 * and the {@linkplain com.oracle.graal.api.code.CiTargetMethod compiled code and metadata}.
 * {@link com.oracle.graal.api.code.CiCodePos} and {@link com.oracle.graal.api.code.CiDebugInfo} provide detailed information to the
 * runtime to support debugging and deoptimization of the compiled code.
 * <p>
 * The compiler manipulates {@link com.oracle.graal.api.meta.RiValue} instances that have a {@link com.oracle.graal.api.meta.RiKind}, and are
 * immutable. A concrete {@link com.oracle.graal.api.meta.RiValue value} is one of the following subclasses:
 * <ul>
 * <li>{@link com.oracle.graal.api.meta.Constant}: a constant value.
 * <li>{@link com.oracle.graal.api.code.CiRegisterValue}: a value stored in a {@linkplain com.oracle.graal.api.code.CiRegister target machine register}.
 * <li>{@link com.oracle.graal.api.code.CiStackSlot}: a spill slot or an outgoing stack-based argument in a method's frame.
 * <li>{@link com.oracle.graal.api.code.CiAddress}: an address in target machine memory.
 * <li>{@link com.oracle.graal.compiler.lir.CiVariable}: a value (cf. virtual register) that is yet to be bound to a target machine location (physical register or memory address).
 *</ul>
 */
package com.oracle.graal.api.code;

