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
package jdk.graal.compiler.jtt.jdk;

import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.jtt.JTTTest;
import jdk.graal.compiler.runtime.RuntimeProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;

@RunWith(Parameterized.class)
public class IntegerShuffleBitsTest extends JTTTest {

    @Parameterized.Parameters(name = "{0}, {1}")
    public static Collection<Object[]> testData() {
        int[] inputs = {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 0x55555555, 0xAAAAAAAA, 0xCAFEBABE, 0xFF00FFF0, 0x0000CABAB};

        List<Object[]> testParameters = new ArrayList<>();
        for (int a : inputs) {
            for (int b : inputs) {
                testParameters.add(new Object[]{a, b});
            }
        }
        return testParameters;
    }

    @Parameterized.Parameter(value = 0) public int input0;
    @Parameterized.Parameter(value = 1) public int input1;

    @Before
    public void checkPreview() {
        Architecture arch = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getTarget().arch;
        assumeTrue("skipping AMD64 specific test", arch instanceof AMD64);
        assumeTrue("bmi2 not supported", ((AMD64) arch).getFeatures().contains(AMD64.CPUFeature.BMI2));
    }

    public static int iCompress(int i, int mask) {
        return Integer.compress(i, mask);
    }

    public static int iExpand(int i, int mask) {
        return Integer.expand(i, mask);
    }

    public static int iCompressExpand(int i, int mask) {
        return Integer.compress(Integer.expand(i, mask), mask);
    }

    public static int iExpandCompress(int i, int mask) {
        return Integer.expand(Integer.compress(i, mask), mask);
    }

    @Test
    public void testICompress() {
        runTest("iCompress", input0, input1);
    }

    @Test
    public void testIExpand() {
        runTest("iExpand", input0, input1);
    }

    @Test
    public void testICompressExpand() {
        runTest("iCompressExpand", input0, input1);
    }

    @Test
    public void testIExpandCompress() {
        runTest("iExpandCompress", input0, input1);
    }
}
