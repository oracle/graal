/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.hotspot.test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotNmethod;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

/**
 * Tests the fix for GR-35930. This test relies on implementation details of {@link HotSpotNmethod}
 * and {@link HotSpotSpeculationLog}.
 */
public class HotSpotManagedFailedSpeculationListTest extends HotSpotGraalCompilerTest {
    private static final SpeculationReasonGroup MY_SPECULATIONS = new SpeculationReasonGroup("HotSpotSpeculationLogTest", int.class);
    private static final SpeculationReason MY_SPECULATION = MY_SPECULATIONS.createSpeculationReason(42);

    /**
     * A simple method that unconditionally deoptimizes upon entry and associates
     * {@link #MY_SPECULATION} with the deoptimization.
     */
    public static int deoptimizeSnippet() {
        GraalDirectives.deoptimize(DeoptimizationAction.None, DeoptimizationReason.TransferToInterpreter, MY_SPECULATION);
        return 42;
    }

    InstalledCode compiledMethod;

    /**
     * Determines if {@link HotSpotNmethod} declares
     * {@code setSpeculationLog(HotSpotSpeculationLog)}. Only such versions properly add a tether
     * from an nmethod to the failed speculation list.
     */
    private static boolean hasSpeculationLogTether() {
        try {
            HotSpotNmethod.class.getDeclaredMethod("setSpeculationLog", HotSpotSpeculationLog.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @Test
    public void testDeoptimize() throws Exception {
        Assume.assumeTrue(hasSpeculationLogTether());

        // Compile and run deoptimizeSnippet
        test("deoptimizeSnippet");

        cutGraphTether();

        // The combination of a GC and creation of a new failed speculation list
        // would reclaim the failed speculation list associated with compiledMethod
        // if there is no tether from compiledMethod to the list.
        System.gc();
        ((HotSpotSpeculationLog) getCodeCache().createSpeculationLog()).getFailedSpeculationsAddress();

        // Execute compiledMethod again. If the failed speculation list has been
        // freed, then this crashes the VM with a fatal error message along the lines of
        // "Adding to failed speculations list that appears to have been freed."
        compiledMethod.executeVarargs();
    }

    /**
     * Clears reference to the last compiled graph such that the only remaining tether to the failed
     * speculation list is {@code lastCompiledGraph.speculationLog}.
     */
    private void cutGraphTether() {
        // Assert that MY_SPECULATION was recorded as a failed speculation
        SpeculationLog log = lastCompiledGraph.getSpeculationLog();
        log.collectFailedSpeculations();
        Assert.assertFalse("expected failed " + MY_SPECULATION + " in " + log, log.maySpeculate(MY_SPECULATION));

        lastCompiledGraph = null;
    }

    @Override
    protected InstalledCode getCode(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean forceCompile, boolean installAsDefault, OptionValues options) {
        compiledMethod = super.getCode(installedCodeOwner, graph, true, installAsDefault, options);
        SpeculationLog speculationLog = lastCompiledGraph.getSpeculationLog();
        Assert.assertTrue("unexpected failed " + MY_SPECULATION + " in " + speculationLog, speculationLog.maySpeculate(MY_SPECULATION));
        return compiledMethod;
    }
}
