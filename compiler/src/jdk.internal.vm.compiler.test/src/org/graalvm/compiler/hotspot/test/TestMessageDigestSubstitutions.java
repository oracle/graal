/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import static org.junit.Assume.assumeTrue;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.junit.Test;
import org.junit.internal.AssumptionViolatedException;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Exercise the execution of message digest substitutions.
 */
public class TestMessageDigestSubstitutions extends HotSpotGraalCompilerTest {

    public byte[] testDigest(String name, byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(name, "SUN");
            digest.update(data);
            return digest.digest();
        } catch (NoSuchProviderException e) {
            return null;
        }
    }

    byte[] getData() {
        byte[] data = new byte[1024 * 16];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        return data;
    }

    GraalHotSpotVMConfig getConfig() {
        HotSpotGraalRuntimeProvider rt = (HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class);
        return rt.getVMConfig();
    }

    @Test
    public void testSha1() {
        assumeTrue("SHA1 not supported", getConfig().useSHA1Intrinsics());
        testWithInstalledIntrinsic("sun.security.provider.SHA", "implCompress0", "testDigest", "SHA-1", getData());
    }

    void testWithInstalledIntrinsic(String className, String methodName, String testSnippetName, Object... args) {
        Class<?> c;
        try {
            c = Class.forName(className);
        } catch (ClassNotFoundException e) {
            // It's ok to not find the class - a different security provider
            // may have been installed
            return;
        }
        InstalledCode code = null;
        try {
            ResolvedJavaMethod method = getResolvedJavaMethod(testSnippetName);
            Object receiver = method.isStatic() ? null : this;
            Result expect = executeExpected(method, receiver, args);
            code = compileAndInstallSubstitution(c, methodName);
            assertTrue("Failed to install " + methodName, code != null);
            testAgainstExpected(method, expect, receiver, args);
        } catch (AssumptionViolatedException e) {
            // Suppress so that subsequent calls to this method within the
            // same Junit @Test annotated method can proceed.
        }
        if (code != null) {
            code.invalidate();
        }
    }

    @Test
    public void testSha256() {
        assumeTrue("SHA256 not supported", getConfig().useSHA256Intrinsics());
        testWithInstalledIntrinsic("sun.security.provider.SHA2", "implCompress0", "testDigest", "SHA-256", getData());
    }

    @Test
    public void testSha512() {
        assumeTrue("SHA512 not supported", getConfig().useSHA512Intrinsics());
        testWithInstalledIntrinsic("sun.security.provider.SHA5", "implCompress0", "testDigest", "SHA-512", getData());
    }

    @Test
    public void testSha3() {
        assumeTrue("SHA3 not supported", getConfig().sha3ImplCompress != 0L);
        testWithInstalledIntrinsic("sun.security.provider.SHA3", "implCompress0", "testDigest", "SHA-3", getData());
    }

    @Test
    public void testMD5() {
        assumeTrue("MD5 not supported", getConfig().md5ImplCompress != 0L);
        testWithInstalledIntrinsic("sun.security.provider.MD5", "implCompress0", "testDigest", "MD5", getData());
    }
}
