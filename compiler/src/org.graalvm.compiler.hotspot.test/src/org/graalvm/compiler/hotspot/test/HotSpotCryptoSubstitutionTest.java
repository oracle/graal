/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA2_IMPL_COMPRESS_MB;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA5_IMPL_COMPRESS_MB;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA_IMPL_COMPRESS_MB;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.AlgorithmParameters;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import org.graalvm.compiler.hotspot.meta.HotSpotGraphBuilderPlugins;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.replacements.SnippetSubstitutionNode;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests the intrinsification of certain crypto methods.
 */
public class HotSpotCryptoSubstitutionTest extends HotSpotGraalCompilerTest {

    private final byte[] input;

    public HotSpotCryptoSubstitutionTest() throws Exception {
        input = readClassfile16(getClass());
    }

    private void testEncryptDecrypt(String className, String methodName, String generatorAlgorithm, int keySize, String algorithm) throws Exception {
        Class<?> klass = null;
        try {
            klass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            // It's ok to not find the class - a different security provider
            // may have been installed
            return;
        }
        KeyGenerator gen = KeyGenerator.getInstance(generatorAlgorithm);
        gen.init(keySize);
        SecretKey key = gen.generateKey();
        Result expected = runEncryptDecrypt(key, algorithm);
        InstalledCode intrinsic = compileAndInstallSubstitution(klass, methodName);
        Assert.assertTrue("missing intrinsic", intrinsic != null);
        Result actual = runEncryptDecrypt(key, algorithm);
        assertEquals(expected, actual);
        intrinsic.invalidate();
    }

    @Test
    public void testAESencryptBlock() throws Exception {
        Assume.assumeTrue(runtime().getVMConfig().useAESIntrinsics);
        String aesEncryptName = HotSpotGraphBuilderPlugins.lookupIntrinsicName(runtime().getVMConfig(), "com/sun/crypto/provider/AESCrypt", "implEncryptBlock", "encryptBlock");
        testEncryptDecrypt("com.sun.crypto.provider.AESCrypt", aesEncryptName, "AES", 128, "AES/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.AESCrypt", aesEncryptName, "AES", 128, "AES/CBC/PKCS5Padding");
    }

    @Test
    public void testAESDecryptBlock() throws Exception {
        Assume.assumeTrue(runtime().getVMConfig().useAESIntrinsics);
        String aesDecryptName = HotSpotGraphBuilderPlugins.lookupIntrinsicName(runtime().getVMConfig(), "com/sun/crypto/provider/AESCrypt", "implDecryptBlock", "decryptBlock");
        testEncryptDecrypt("com.sun.crypto.provider.AESCrypt", aesDecryptName, "AES", 128, "AES/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.AESCrypt", aesDecryptName, "AES", 128, "AES/CBC/PKCS5Padding");
    }

    @Test
    public void testCipherBlockChainingEncrypt() throws Exception {
        Assume.assumeTrue(runtime().getVMConfig().useAESIntrinsics);
        String cbcEncryptName = HotSpotGraphBuilderPlugins.lookupIntrinsicName(runtime().getVMConfig(), "com/sun/crypto/provider/CipherBlockChaining", "implEncrypt", "encrypt");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", cbcEncryptName, "AES", 128, "AES/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", cbcEncryptName, "AES", 128, "AES/CBC/PKCS5Padding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", cbcEncryptName, "DESede", 168, "DESede/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", cbcEncryptName, "DESede", 168, "DESede/CBC/PKCS5Padding");
    }

    @Test
    public void testCipherBlockChainingDecrypt() throws Exception {
        Assume.assumeTrue(runtime().getVMConfig().useAESIntrinsics);
        String cbcDecryptName = HotSpotGraphBuilderPlugins.lookupIntrinsicName(runtime().getVMConfig(), "com/sun/crypto/provider/CipherBlockChaining", "implDecrypt", "decrypt");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", cbcDecryptName, "AES", 128, "AES/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", cbcDecryptName, "AES", 128, "AES/CBC/PKCS5Padding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", cbcDecryptName, "DESede", 168, "DESede/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", cbcDecryptName, "DESede", 168, "DESede/CBC/PKCS5Padding");
    }

    @Test
    public void testCounterModeEncrypt() throws Exception {
        Assume.assumeTrue(runtime().getVMConfig().useAESCTRIntrinsics);
        testEncryptDecrypt("com.sun.crypto.provider.CounterMode", "implCrypt", "AES", 128, "AES/CTR/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CounterMode", "implCrypt", "AES", 128, "AES/CTR/PKCS5Padding");
        testEncryptDecrypt("com.sun.crypto.provider.CounterMode", "implCrypt", "DESede", 168, "DESede/CTR/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CounterMode", "implCrypt", "DESede", 168, "DESede/CTR/PKCS5Padding");
    }

    AlgorithmParameters algorithmParameters;

    private byte[] encrypt(byte[] indata, SecretKey key, String algorithm) throws Exception {
        byte[] result = indata;

        Cipher c = Cipher.getInstance(algorithm);
        c.init(Cipher.ENCRYPT_MODE, key);
        algorithmParameters = c.getParameters();

        byte[] r1 = c.update(result);
        byte[] r2 = c.doFinal();

        result = new byte[r1.length + r2.length];
        System.arraycopy(r1, 0, result, 0, r1.length);
        System.arraycopy(r2, 0, result, r1.length, r2.length);

        return result;
    }

    private byte[] decrypt(byte[] indata, SecretKey key, String algorithm) throws Exception {
        byte[] result = indata;

        Cipher c = Cipher.getInstance(algorithm);
        c.init(Cipher.DECRYPT_MODE, key, algorithmParameters);

        byte[] r1 = c.update(result);
        byte[] r2 = c.doFinal();

        result = new byte[r1.length + r2.length];
        System.arraycopy(r1, 0, result, 0, r1.length);
        System.arraycopy(r2, 0, result, r1.length, r2.length);
        return result;
    }

    private static byte[] readClassfile16(Class<? extends HotSpotCryptoSubstitutionTest> c) throws IOException {
        String classFilePath = "/" + c.getName().replace('.', '/') + ".class";
        InputStream stream = c.getResourceAsStream(classFilePath);
        int bytesToRead = stream.available();
        bytesToRead -= bytesToRead % 16;
        byte[] classFile = new byte[bytesToRead];
        new DataInputStream(stream).readFully(classFile);
        return classFile;
    }

    public Result runEncryptDecrypt(SecretKey key, String algorithm) throws Exception {
        try {
            byte[] indata = input.clone();
            byte[] cipher = encrypt(indata, key, algorithm);
            byte[] plain = decrypt(cipher, key, algorithm);
            Assert.assertArrayEquals(indata, plain);
            return new Result(plain, null);
        } catch (NoSuchAlgorithmException e) {
            return new Result(null, e);
        }
    }

    @Test
    public void testDigestBaseSHA() throws Exception {
        Assume.assumeTrue("SHA1 not supported", runtime().getVMConfig().useSHA1Intrinsics());
        testDigestBase("sun.security.provider.DigestBase", "implCompressMultiBlock", "SHA-1", SHA_IMPL_COMPRESS_MB);
    }

    @Test
    public void testDigestBaseSHA2() throws Exception {
        Assume.assumeTrue("SHA256 not supported", runtime().getVMConfig().useSHA256Intrinsics());
        testDigestBase("sun.security.provider.DigestBase", "implCompressMultiBlock", "SHA-256", SHA2_IMPL_COMPRESS_MB);
    }

    @Test
    public void testDigestBaseSHA5() throws Exception {
        Assume.assumeTrue("SHA512 not supported", runtime().getVMConfig().useSHA512Intrinsics());
        testDigestBase("sun.security.provider.DigestBase", "implCompressMultiBlock", "SHA-512", SHA5_IMPL_COMPRESS_MB);
    }

    @Before
    public void clearExceptionCall() {
        expectedCall = null;
    }

    HotSpotForeignCallDescriptor expectedCall;

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        if (expectedCall != null) {
            for (ForeignCallNode node : graph.getNodes().filter(ForeignCallNode.class)) {
                if (node.getDescriptor() == expectedCall) {
                    return;
                }
            }
            assertTrue("expected call to " + expectedCall, false);
        }
    }

    private void testDigestBase(String className, String methodName, String algorithm, HotSpotForeignCallDescriptor call) throws Exception {
        Class<?> klass = Class.forName(className);
        expectedCall = call;
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] expected = digest.digest(input.clone());
        ResolvedJavaMethod method = getResolvedJavaMethod(klass, methodName);

        try {
            testDigestBase(digest, expected, method);
        } catch (BailoutException e) {
            // The plugin may cause loading which invalidates assumptions in the graph so retry it
            // once. This normally only occurs when running individual tests.
            if (e.getMessage().contains("Code installation failed: dependencies failed")) {
                testDigestBase(digest, expected, method);
            } else {
                throw e;
            }
        }
    }

    private void testDigestBase(MessageDigest digest, byte[] expected, ResolvedJavaMethod method) {
        StructuredGraph graph = parseForCompile(method);
        assertTrue(graph.getNodes().filter(SnippetSubstitutionNode.class).isNotEmpty());
        InstalledCode intrinsic = getCode(method, graph, false, true, getInitialOptions());
        try {
            Assert.assertNotNull("missing intrinsic", intrinsic);
            byte[] actual = digest.digest(input.clone());
            assertDeepEquals(expected, actual);
        } finally {
            intrinsic.invalidate();
        }
    }
}
