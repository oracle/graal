/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.core.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ConvertDeoptimizeToGuardPrioritiesTest extends GraalCompilerTest {

    private OptionValues getOptions() {
        return new OptionValues(getInitialOptions(),
                        GraalOptions.GuardPriorities, true,
                        GraalOptions.LoopPeeling, false,
                        GraalOptions.LoopUnswitch, false);
    }

    public static boolean differentInvalidationOriginalSnippet(boolean deoptWithoutInvalidation) {
        if (deoptWithoutInvalidation) {
            GraalDirectives.deoptimize();
        } else {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return GraalDirectives.inCompiledCode();
    }

    @Test
    public void testDifferentInvalidationOriginal() throws InvalidInstalledCodeException {
        ResolvedJavaMethod method = getResolvedJavaMethod("differentInvalidationOriginalSnippet");
        InstalledCode compiledMethod = getCode(method, getOptions());
        compiledMethod.executeVarargs(true);
        Assert.assertTrue("must take the deopt without invalidation", compiledMethod.isValid());
    }

    public static boolean differentInvalidationSwappedSnippet(boolean deoptWithInvalidation) {
        /*
         * Same as differentInvalidationOriginalSnippet, but with the branches reversed. If the order of propagation in
         * ConvertDeoptimizeToGuard depends only on the numbering of nodes in the graph, this will behave differently
         * from the other snippet.
         */
        if (deoptWithInvalidation) {
            GraalDirectives.deoptimizeAndInvalidate();
        } else {
            GraalDirectives.deoptimize();
        }
        return GraalDirectives.inCompiledCode();
    }

    @Test
    public void testDifferentInvalidationSwapped() throws InvalidInstalledCodeException {
        ResolvedJavaMethod method = getResolvedJavaMethod("differentInvalidationSwappedSnippet");
        InstalledCode compiledMethod = getCode(method, getOptions());
        compiledMethod.executeVarargs(false);
        Assert.assertTrue("must take the deopt without invalidation", compiledMethod.isValid());
    }

    public static boolean zeroTripLoopSnippet(boolean enterDeopt, boolean selectNone, int limit) {
        for (int i = 0; i < limit; i++) {
            if (enterDeopt) {
                if (selectNone) {
                    GraalDirectives.deoptimize();
                } else {
                    GraalDirectives.deoptimizeAndInvalidate();
                }
            }
        }
        return GraalDirectives.inCompiledCode();
    }

    @Test
    public void testZeroTripLoop() throws InvalidInstalledCodeException {
        boolean interpretedEndedInCompiledCode = zeroTripLoopSnippet(true, true, 0);
        Assert.assertFalse("interpreted code must return false from inCompiledCode", interpretedEndedInCompiledCode);

        ResolvedJavaMethod method = getResolvedJavaMethod("zeroTripLoopSnippet");
        InstalledCode compiledMethod = getCode(method, getOptions());
        boolean compiledEndedInCompiledCode = (boolean) compiledMethod.executeVarargs(true, true, 0);
        Assert.assertTrue("compiled code should bypass the loop and return true from inCompiledCode", compiledEndedInCompiledCode);
        Assert.assertEquals("compiled code should not deopt", 0, method.getProfilingInfo().getDeoptimizationCount(DeoptimizationReason.TransferToInterpreter));
    }
}
