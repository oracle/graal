/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot.bridge;

import com.oracle.graal.api.code.InvalidInstalledCodeException;
import com.oracle.graal.hotspot.meta.HotSpotInstalledCode;

/**
 * Calls from Java into the GPU.
 */
public interface CompilerToGPU {

    /**
     * Attempts to initialize and create a valid context with the GPU.
     * 
     * @return whether the GPU context has been initialized and is valid.
     */
    boolean deviceInit();

    /**
     * Attempts to detach from a valid GPU context.
     * 
     * @return whether the GPU context has been properly disposed.
     */
    boolean deviceDetach();

    int availableProcessors();

    /**
     * Attempts to generate and return a bound function to the loaded method kernel on the GPU.
     * 
     * @param code the text or binary values for a method kernel
     * @return the value of the bound kernel in GPU space.
     */
    long generateKernel(byte[] code, String name) throws InvalidInstalledCodeException;

    Object executeExternalMethodVarargs(Object[] args, HotSpotInstalledCode hotspotInstalledCode) throws InvalidInstalledCodeException;

    Object executeParallelMethodVarargs(int dimX, int dimY, int dimZ, Object[] args, HotSpotInstalledCode hotspotInstalledCode) throws InvalidInstalledCodeException;

    /**
     * Gets the address of the runtime function for launching a kernel function.
     */
    long getLaunchKernelAddress();
}
