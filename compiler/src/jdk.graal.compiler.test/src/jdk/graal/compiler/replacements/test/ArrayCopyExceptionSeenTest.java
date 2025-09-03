/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import static jdk.graal.compiler.core.common.GraalOptions.StressInvokeWithExceptionNode;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.spi.ProfileProvider;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ArrayCopyExceptionSeenTest extends GraalCompilerTest {

    @Override
    protected ProfileProvider getProfileProvider(ResolvedJavaMethod method) {
        if (method == getResolvedJavaMethod("copyWithHandler")) {
            return super.getProfileProvider(method);
        } else {
            return NO_PROFILE_PROVIDER;
        }
    }

    static class A {
        int field = 0;
    }

    static A a = null;

    public void copy(Object src, Object dst) {
        try {
            System.arraycopy(src, 0, dst, 0, 1);
        } catch (Throwable e) {
            try {
                a.field++;
            } catch (Throwable npe) {
                GraalDirectives.deoptimize();
            }
        }
    }

    public static boolean copyWithHandler(Object src, int srcPos, Object dst, int destPos, int length) {
        try {
            System.arraycopy(src, srcPos, dst, destPos, length);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Test
    public void testCopy() {
        getFinalGraph(getResolvedJavaMethod("copy"), new OptionValues(getInitialOptions(), StressInvokeWithExceptionNode, true));
    }

    /**
     * Tests that calls to {@link System#arraycopy} which result in an exception will be compiled
     * with explicit exception handlers when recompiled.
     */
    @Test
    public void testFailingCopy() throws InvalidInstalledCodeException {
        var method = getResolvedJavaMethod("copyWithHandler");
        Assert.assertEquals("No recorded deopt expected before first invocation.", 0, method.getProfilingInfo().getDeoptimizationCount(DeoptimizationReason.BoundsCheckException));
        test(method, null, new Object[3], -1, new Object[3], 0, 1);
        Assert.assertEquals("Single deopt expected after first invocation.", 1, method.getProfilingInfo().getDeoptimizationCount(DeoptimizationReason.BoundsCheckException));
        /*
         * Force a recompile which should create an explicit exception edge for the System.arraycopy
         * call because an exception has been recorded at that position.
         */
        var code = getCode(method, null, true, true, getInitialOptions());
        code.executeVarargs(new Object[3], -1, new Object[3], 0, 1);
        Assert.assertEquals("No more deopts expected after recompile with explicit exception edge.", 1, method.getProfilingInfo().getDeoptimizationCount(DeoptimizationReason.BoundsCheckException));
    }
}
