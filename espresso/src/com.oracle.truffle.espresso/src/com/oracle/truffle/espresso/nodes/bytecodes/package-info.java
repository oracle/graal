/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * Standalone nodes for Java bytecodes.
 *
 * <p>
 * Nodes in this package support additional extensions e.g. interop and class redefinition support.
 * These nodes are building blocks for the runtime, substitutions...
 * </p>
 *
 * <p>
 * Some nodes may not preserve the full semantics of Java bytecodes e.g. invoke nodes do NOT perform
 * access checks, the caller is responsible for access checks and sanity checks required for
 * correct/legal execution.
 * </p>
 * 
 * <p>
 * Nodes in this package always offer un-cached versions e.g.
 * {@link com.oracle.truffle.espresso.nodes.bytecodes.InstanceOf.Dynamic} that can be used anywhere,
 * at the expense of performance.
 * </p>
 */
package com.oracle.truffle.espresso.nodes.bytecodes;
