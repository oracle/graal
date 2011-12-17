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
 * A virtual machine compiler-runtime interface (CRI).
 * <p>
 * Specifically, this package defines an interface between the compiler and the runtime system of a virtual machine for
 * the instruction set defined in {@link com.sun.cri.bytecode.Bytecodes}. The interface has three components:
 * <ol>
 * <li>the {@link com.sun.cri.ci compiler-provided interface} that must be used by the runtime.
 * <li>the {@link com.sun.cri.ri runtime-provided interface} that must be used by the compiler.
 * <li>the {@link com.sun.cri.xir XIR interface} for translating object operations.
 * </ol>
 *
 * The interface is independent of any particular compiler or runtime implementation.
 * <p>
 * For more details see <a href="http://wikis.sun.com/download/attachments/173802383/vee2010.pdf">Improving Compiler-Runtime Separation with XIR</a>.
 */
package com.sun.cri;
