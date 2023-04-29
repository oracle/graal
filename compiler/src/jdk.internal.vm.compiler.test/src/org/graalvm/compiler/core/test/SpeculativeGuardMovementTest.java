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
package org.graalvm.compiler.core.test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.SpeculationLog;

public class SpeculativeGuardMovementTest extends GraalCompilerTest {

    @Override
    protected SpeculationLog getSpeculationLog() {
        return getCodeCache().createSpeculationLog();
    }

    public static void snippet01(int init, int limit, int offset) {
        for (int i = init; GraalDirectives.injectIterationCount(1000, i < limit); i++) {
            if (Integer.compareUnsigned(i + offset, Integer.MIN_VALUE + 5) > 0) {
                GraalDirectives.deoptimizeAndInvalidate();
                throw new IndexOutOfBoundsException();
            }
        }
    }

    @Test
    public void testOverflowUnsignedGuard() {
        int init = 0;
        int limit = 10;
        int offset = Integer.MAX_VALUE;
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.PartialUnroll, false);
        InstalledCode code = getCode(getResolvedJavaMethod("snippet01"), opt);
        assertTrue(code.isValid());
        try {
            code.executeVarargs(init, limit, offset);
            throw new RuntimeException("should have thrown");
        } catch (Throwable e) {
            if (!(e instanceof IndexOutOfBoundsException)) {
                throw new RuntimeException("unexpected exception " + e);
            }
        }
    }
}
