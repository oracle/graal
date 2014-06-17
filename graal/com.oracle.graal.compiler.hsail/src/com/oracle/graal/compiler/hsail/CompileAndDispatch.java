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

package com.oracle.graal.compiler.hsail;

/**
 * Interface for compiling a Java program to HSAIL and dispatching execution to the GPU or an HSAIL
 * simulator.
 */
public interface CompileAndDispatch {

    Object createKernel(Class<?> consumerClass);

    boolean dispatchKernel(Object kernel, int jobSize, Object[] args);

    Object createKernelFromHsailString(String code, String methodName);

    String getIntegerReduceIntrinsic(String reducerName);

    Integer offloadIntReduceImpl(Object kernel, int identity, int[] streamSource);

    String getIntReduceTargetName(Class<?> opClass);
}
