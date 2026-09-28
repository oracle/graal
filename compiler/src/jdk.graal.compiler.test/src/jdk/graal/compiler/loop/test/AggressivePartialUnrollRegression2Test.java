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

package jdk.graal.compiler.loop.test;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;

public class AggressivePartialUnrollRegression2Test extends GraalCompilerTest {

    public static void snippet() {
        short var7;
        String[] var21;
        int var11;
        int var20;
        int var29;
        int var4;
        boolean var3 = GraalDirectives.opaque(false);
        Float var5 = 1.0f;
        Boolean var10 = GraalDirectives.opaque(false);
        String var17 = "a^]9ihdw8";
        int var22 = 52163;
        Object[] var23 = {};
        double var24 = GraalDirectives.opaque(0.0);
        var4 = 0;
        while (var3) {
            var7 = (short) (var5 / 1000);
            GraalDirectives.opaque(var7);
            var21 = new String[]{};
            var29 = 0;
            while (Boolean.parseBoolean(var17)) {
                var10 = var3;
                var23 = new Object[]{var10};
                var21 = new String[]{""};
                if (GraalDirectives.injectBranchProbability(0.01, var29 > 100)) {
                    break;
                }
                var29 = var29 + 1;
                var17 = "foobar";
            }
            if (GraalDirectives.injectBranchProbability(0.01, var4 > 100)) {
                break;
            }
            var4 = var4 + 1;
            var11 = 0;
            while (GraalDirectives.opaque(false)) {
                var20 = 0;
                while (var10 ? var10 : true) {
                    var5 = (float) ((var21.length % var22 ^ var23.length) - var24);
                    var24 = (short) var20;
                    var22 = Character.OTHER_NUMBER;
                    if (GraalDirectives.injectBranchProbability(0.01, var20 > 100)) {
                        break;
                    }
                    var20 = var20 + 1;
                }
                if (GraalDirectives.injectBranchProbability(0.01, var11 > 100)) {
                    break;
                }
                var11 = var11 + 1;
            }
        }
    }

    @Test
    public void runTest() {
        test("snippet");
    }
}
