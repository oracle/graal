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
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static org.junit.Assume.*;

import java.io.*;
import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.gpu.*;
import com.oracle.graal.hotspot.hsail.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hsail.*;
import com.oracle.graal.options.*;
import com.oracle.graal.options.OptionValue.OverrideScope;

public abstract class GraalKernelTester extends KernelTester {

    private static boolean substitutionsInstalled;

    private static synchronized void installSubstitutions() {
        if (!substitutionsInstalled) {
            getHSAILBackend().getProviders().getReplacements().registerSubstitutions(ForceDeoptSubstitutions.class);
            substitutionsInstalled = true;
        }
    }

    public GraalKernelTester() {
        super(getHSAILBackend().isDeviceInitialized());
        installSubstitutions();
    }

    protected static HSAILHotSpotBackend getHSAILBackend() {
        Backend backend = runtime().getBackend(HSAIL.class);
        Assume.assumeTrue(backend instanceof HSAILHotSpotBackend);
        return (HSAILHotSpotBackend) backend;
    }

    ExternalCompilationResult hsailCode;
    private boolean showHsailSource = false;
    private boolean saveInFile = false;

    @Override
    public String getCompiledHSAILSource(Method method) {
        if (hsailCode == null) {
            HSAILHotSpotBackend backend = getHSAILBackend();
            ResolvedJavaMethod javaMethod = backend.getProviders().getMetaAccess().lookupJavaMethod(method);
            hsailCode = backend.compileKernel(javaMethod, false);
        }
        String hsailSource = hsailCode.getCodeString();
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

    private static boolean supportsObjectAllocation() {
        return true;
    }

    /**
     * Determines if the runtime supports object allocation in HSAIL code.
     */
    public boolean canHandleObjectAllocation() {
        return supportsObjectAllocation() && canDeoptimize();
    }

    /**
     * Determines if the runtime supports deoptimization in HSAIL code.
     */
    public boolean canDeoptimize() {
        return getHSAILBackend().getRuntime().getConfig().useHSAILDeoptimization;
    }

    /**
     * Determines if the JVM supports the required typeProfileWidth.
     */
    public boolean typeProfileWidthAtLeast(int val) {
        return (getHSAILBackend().getRuntime().getConfig().typeProfileWidth >= val);
    }

    /**
     * Determines if the runtime supports {@link VirtualObject}s in {@link DebugInfo} associated
     * with HSAIL code.
     */
    public boolean canHandleDeoptVirtualObjects() {
        return true;
    }

    /**
     * Determines if the runtime has the capabilities required by this test.
     */
    protected boolean supportsRequiredCapabilities() {
        return true;
    }

    @Override
    protected void dispatchKernelOkra(int range, Object... args) {
        HSAILHotSpotBackend backend = getHSAILBackend();
        if (backend.isDeviceInitialized()) {
            try {
                HotSpotNmethod code = backend.compileAndInstallKernel(testMethod);
                backend.executeKernel(code, range, args);
            } catch (InvalidInstalledCodeException e) {
                Debug.log("WARNING:Invalid installed code: " + e);
                e.printStackTrace();
            }
        } else {
            super.dispatchKernelOkra(range, args);
        }
    }

    public static OptionValue<?> getOptionFromField(Class<?> declaringClass, String fieldName) {
        try {
            Field f = declaringClass.getDeclaredField(fieldName);
            f.setAccessible(true);
            return (OptionValue<?>) f.get(null);
        } catch (Exception e) {
            throw new GraalInternalError(e);
        }
    }

    private OptionValue<?> accessibleRemoveNeverExecutedCode = getOptionFromField(GraalOptions.class, "RemoveNeverExecutedCode");

    // Special overrides for the testGeneratedxxx routines which set
    // required graal options that we need to run any junit test

    private OverrideScope getOverrideScope() {
        return OptionValue.override(GraalOptions.InlineEverything, true, accessibleRemoveNeverExecutedCode, false);
    }

    @Override
    public void testGeneratedHsail() {
        try (OverrideScope s = getOverrideScope()) {
            assumeTrue(supportsRequiredCapabilities());
            super.testGeneratedHsail();
        }
    }

    @Override
    public void testGeneratedHsailUsingLambdaMethod() {
        try (OverrideScope s = getOverrideScope()) {
            assumeTrue(supportsRequiredCapabilities());
            super.testGeneratedHsailUsingLambdaMethod();
        }
    }

    // used for forcing a deoptimization
    public static int forceDeopt(int x) {
        return x * x;
    }

    public static double forceDeopt(double x) {
        return x * x;
    }

}
