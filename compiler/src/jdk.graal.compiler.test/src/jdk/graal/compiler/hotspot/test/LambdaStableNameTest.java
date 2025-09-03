/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.function.Function;

import org.junit.Test;

import jdk.graal.compiler.java.LambdaUtils;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.runtime.JVMCI;

public class LambdaStableNameTest {

    @Test
    public void checkStableLamdaNameForRunnableAndAutoCloseable() {
        String s = "a string";
        Runnable r0 = s::hashCode;
        String r0Name = getLambdaName(r0.getClass());

        Runnable r1 = s::hashCode;
        String r1Name = getLambdaName(r1.getClass());

        assertEquals("The two stable lambda names should the same as they reference the same method and implement the same interface", r0Name, r1Name);

        AutoCloseable ac = s::hashCode;
        String acName = getLambdaName(ac.getClass());

        assertNotEquals("The two stable lambda names should not be the same as they reference the same method but implement different interfaces", r0Name, acName);

        String myName = getClass().getName().replace('.', '/');
        assertEquals("The name known in 24.0 version is computed", "L" + myName + "$$Lambda.0x59cf38d78b5471f8ea57f1c28b37039c;", r0Name);

        Function<String, Integer> f0 = (str) -> str.hashCode();
        String f0Name = getLambdaName(f0.getClass());

        interface ValueTransformer<L, R> extends Function<L, R> {
        }

        ValueTransformer<String, Integer> f1 = (str) -> str.hashCode();
        String f1Name = getLambdaName(f1.getClass());

        assertNotEquals("The two stable lambda names should not be the same as they reference the same method but implement different interfaces", f0Name, f1Name);
    }

    private static String getLambdaName(Class<?> clazz) {
        ResolvedJavaType type = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess().lookupJavaType(clazz);
        String name = LambdaUtils.findStableLambdaName(type);
        assertLambdaName(name);
        return name;
    }

    private static void assertLambdaName(String name) {
        String expectedPrefix = "L" + LambdaStableNameTest.class.getCanonicalName().replace('.', '/') +
                        LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING;
        if (!name.startsWith(expectedPrefix)) {
            fail("Expecting " + expectedPrefix + " as prefix in lambda class name: " + name);
        }
        assertTrue("semicolon at the end", name.endsWith(";"));

        int index = name.indexOf(LambdaUtils.ADDRESS_PREFIX);

        String hash = name.substring(index + LambdaUtils.ADDRESS_PREFIX.length(), name.length() - 1);

        BigInteger aValue = new BigInteger(hash, 16);
        assertNotNull("Hash can be parsed as a hex number: " + hash, aValue);
    }
}
