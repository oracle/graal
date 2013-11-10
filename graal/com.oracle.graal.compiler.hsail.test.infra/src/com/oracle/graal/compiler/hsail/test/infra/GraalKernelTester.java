/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.hsail.test.infra;

/**
 * This class extends KernelTester and provides a base class
 * for which the HSAIL code comes from the Graal compiler.
 */
import static com.oracle.graal.phases.GraalOptions.*;

import java.io.*;
import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.hsail.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.options.*;

public abstract class GraalKernelTester extends KernelTester {

    HSAILCompilationResult hsailCompResult;
    private boolean showHsailSource = false;
    private boolean saveInFile = false;

    @Override
    public String getCompiledHSAILSource(Method method) {
        if (hsailCompResult == null) {
            hsailCompResult = HSAILCompilationResult.getHSAILCompilationResult(method);
        }
        String hsailSource = hsailCompResult.getHSAILCode();
        if (showHsailSource) {
            logger.severe(hsailSource);
        }
        if (saveInFile) {
            try {
                File fout = File.createTempFile("tmp", ".hsail");
                logger.fine("creating " + fout.getCanonicalPath());
                FileWriter fw = new FileWriter(fout);
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write(hsailSource);
                bw.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return hsailSource;
    }

    public boolean aggressiveInliningEnabled() {
        return (InlineEverything.getValue());
    }

    public boolean canHandleHSAILMethodCalls() {
        // needs 2 things, backend needs to be able to generate such calls, and target needs to be
        // able to run them
        boolean canGenerateCalls = false;   // not implemented yet
        boolean canExecuteCalls = runningOnSimulator();
        return (canGenerateCalls && canExecuteCalls);
    }

    @Override
    protected void dispatchLambdaMethodKernelOkra(int range, MyIntConsumer consumer) {
        HSAILCompilationResult hcr = HSAILCompilationResult.getCompiledLambda(consumer.getClass());
        HotSpotNmethod code = (HotSpotNmethod) hcr.getInstalledCode();

        logger.info("To determine parameters to pass to hsail kernel, we will examine   " + consumer.getClass());
        Field[] fields = consumer.getClass().getDeclaredFields();
        Object[] args = new Object[fields.length];
        int argIndex = 0;
        for (Field f : fields) {
            logger.info("... " + f);
            args[argIndex++] = getFieldFromObject(f, consumer);
        }

        if (code != null) {
            try {
                // No return value from HSAIL kernels
                code.executeParallel(range, 0, 0, args);
            } catch (InvalidInstalledCodeException e) {
                Debug.log("WARNING:Invalid installed code: " + e);
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void dispatchMethodKernelOkra(int range, Object... args) {
        Object[] fixedArgs = fixArgTypes(args);

        HSAILCompilationResult hcr = HSAILCompilationResult.getHSAILCompilationResult(testMethod);
        HotSpotNmethod code = (HotSpotNmethod) hcr.getInstalledCode();

        if (code != null) {
            try {
                if (Modifier.isStatic(testMethod.getModifiers())) {
                    code.executeParallel(range, 0, 0, fixedArgs);
                } else {
                    // If it is a non-static method we have to push "this" as the first argument.
                    Object[] newFixedArgs = new Object[fixedArgs.length + 1];
                    System.arraycopy(fixedArgs, 0, newFixedArgs, 1, fixedArgs.length);
                    newFixedArgs[0] = this;
                    code.executeParallel(range, 0, 0, newFixedArgs);
                }
            } catch (InvalidInstalledCodeException e) {
                Debug.log("WARNING:Invalid installed code: " + e);
                e.printStackTrace();
            }
        }
    }

    public static OptionValue<?> getOptionFromField(Class declaringClass, String fieldName) {
        try {
            Field f = declaringClass.getDeclaredField(fieldName);
            f.setAccessible(true);
            return (OptionValue<?>) f.get(null);
        } catch (Exception e) {
            throw new GraalInternalError(e);
        }
    }
}
