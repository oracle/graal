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
 * XIR defines a domain specific instruction set for expressing the lowering of bytecode operations. The details of the
 * lowering operations are entirely encapsulated in the runtime and are provided to the compiler on request using
 * {@link com.sun.cri.xir.XirSnippet XIR snippets}. A snippet is a combination of a {@link com.sun.cri.xir.XirTemplate
 * template}, which is a sequence of {@link com.sun.cri.xir.CiXirAssembler.XirInstruction XIR instructions} that has
 * unbound {@link com.sun.cri.xir.CiXirAssembler.XirParameter parameters}, and site-specific
 * {@link com.sun.cri.xir.XirArgument arguments} that are bound to the parameters.
 * <p>
 * The runtime is responsible for creating the {@link com.sun.cri.xir.XirTemplate templates} and provides these to the
 * compiler as part of the initialization process.
 * <p>
 * The XIR instruction set has no textual representation, and therefore no parser. An assembly is represented by an
 * instance of {@link com.sun.cri.xir.CiXirAssembler}, which provides methods to create instructions and operands.
 */
package com.sun.cri.xir;
