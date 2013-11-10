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

package com.oracle.graal.hotspot.hsail;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.hsail.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.hotspot.meta.*;

/**
 * Implements compile and dispatch of Java code containing lambda constructs. Currently only used by
 * JDK interception code that offloads to the GPU.
 */
public class ForEachToGraal implements CompileAndDispatch {

    private static HSAILCompilationResult getCompiledLambda(Class consumerClass) {
        return HSAILCompilationResult.getCompiledLambda(consumerClass);
    }

    // Implementations of the CompileAndDispatch interface.
    @Override
    public Object createKernel(Class<?> consumerClass) {
        try {
            return getCompiledLambda(consumerClass);
        } catch (Throwable e) {
            // Note: Graal throws Errors. We want to revert to regular Java in these cases.
            Debug.log("WARNING:Graal compilation failed.");
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean dispatchKernel(Object kernel, int jobSize, Object[] args) {
        // kernel is an HSAILCompilationResult
        HotSpotNmethod code = (HotSpotNmethod) ((HSAILCompilationResult) kernel).getInstalledCode();

        if (code != null) {
            try {
                // No return value from HSAIL kernels
                code.executeParallel(jobSize, 0, 0, args);
                return true;
            } catch (InvalidInstalledCodeException iice) {
                Debug.log("WARNING:Invalid installed code at exec time." + iice);
                iice.printStackTrace();
                return false;
            }
        } else {
            // Should throw something sensible here
            return false;
        }
    }
}
