/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.Pair;
import jdk.graal.compiler.test.AddExports;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import sun.instrument.InstrumentationImpl;

@AddExports({"java.instrument/sun.instrument", "java.instrument/java.lang.instrument"})
public class HotSpotObjectSizeTest extends HotSpotGraalCompilerTest {

    public static long objectSize(InstrumentationImpl impl, Object o) {
        return impl.getObjectSize(o);
    }

    static final int LARGE_INT_ARRAY_SIZE = 1024 * 1024 * 1024 + 1024;

    @Test
    public void testNewInstance() throws InstantiationException, InvalidInstalledCodeException {
        List<Pair<Object, Long>> objects = new ArrayList<>();
        objects.add(Pair.create(new Object(), 16L));
        objects.add(Pair.create(new String(), runtime().getVMConfig().useCompressedOops ? 24L : 32L));
        objects.add(Pair.create(new byte[1], 24L));
        objects.add(Pair.create(new boolean[1][1], 24L));
        objects.add(Pair.create(new long[64], 528L));

        try {
            objects.add(Pair.create(new int[LARGE_INT_ARRAY_SIZE], 0x1_00001010L));
        } catch (OutOfMemoryError e) {
            // We don't have enough memory for such array. Skip this test.
        }

        // We cannot pass the following uninitialized instance to interpreter. However, passing it
        // to a compiled method is fine as long as its content is never read.
        InstrumentationImpl instance = (InstrumentationImpl) UNSAFE.allocateInstance(InstrumentationImpl.class);
        InstalledCode code = getCode(getResolvedJavaMethod("objectSize"));

        for (Pair<Object, Long> pair : objects) {
            long actual = (long) code.executeVarargs(instance, pair.getLeft());
            assertTrue(pair.getRight() == actual, "%s expected size of %s but got %s", pair.getLeft().getClass(), pair.getRight(), actual);
        }
    }

    /**
     * To generate the golden output, package this class into a jar with manifest specifying
     * {@code Premain-class}, and then launch any program with {@code -javaagent} pointing to the
     * jar.
     */
    @SuppressWarnings("unused")
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println(inst.getObjectSize(new Object()));
        System.out.println(inst.getObjectSize(new String()));
        System.out.println(inst.getObjectSize(new byte[1]));
        System.out.println(inst.getObjectSize(new boolean[1][1]));
        System.out.println(inst.getObjectSize(new long[64]));
    }
}
