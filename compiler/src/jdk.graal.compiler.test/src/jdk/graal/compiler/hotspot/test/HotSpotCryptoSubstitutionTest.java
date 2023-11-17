/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.hotspot.HotSpotBackend.SHA2_IMPL_COMPRESS_MB;
import static jdk.graal.compiler.hotspot.HotSpotBackend.SHA3_IMPL_COMPRESS_MB;
import static jdk.graal.compiler.hotspot.HotSpotBackend.SHA5_IMPL_COMPRESS_MB;
import static jdk.graal.compiler.hotspot.HotSpotBackend.SHA_IMPL_COMPRESS_MB;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.replacements.SnippetSubstitutionNode;
import jdk.graal.compiler.replacements.nodes.AESNode;
import jdk.graal.compiler.replacements.nodes.CipherBlockChainingAESNode;
import jdk.graal.compiler.replacements.nodes.CounterModeAESNode;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA1Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA256Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA3Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA512Node;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.internal.AssumptionViolatedException;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests the intrinsification of certain crypto methods.
 */
public class HotSpotCryptoSubstitutionTest extends HotSpotGraalCompilerTest {

    private final byte[] input;

    public HotSpotCryptoSubstitutionTest() throws IOException {
        input = readClassfile16(getClass());
    }

    private ResolvedJavaMethod getResolvedJavaMethod(String className, String methodName) throws ClassNotFoundException {
        Class<?> klass = Class.forName(className);
        return getMetaAccess().lookupJavaMethod(getMethod(klass, methodName));
    }

    private ResolvedJavaMethod getResolvedJavaMethod(String className, String methodName, Class<?>... parameterTypes) throws ClassNotFoundException {
        Class<?> klass = Class.forName(className);
        return getMetaAccess().lookupJavaMethod(getMethod(klass, methodName, parameterTypes));
    }

    private void testEncryptDecrypt(String className, String methodName, String generatorAlgorithm, int keySize, String algorithm) throws GeneralSecurityException, ClassNotFoundException {
        testEncryptDecrypt(getResolvedJavaMethod(className, methodName), generatorAlgorithm, keySize, algorithm);
    }

    private void testEncryptDecrypt(ResolvedJavaMethod intrinsicMethod, String generatorAlgorithm, int keySize, String algorithm) throws GeneralSecurityException {
        KeyGenerator gen = KeyGenerator.getInstance(generatorAlgorithm);
        gen.init(keySize);
        SecretKey key = gen.generateKey();
        Result expected = runEncryptDecrypt(key, algorithm);
        InstalledCode intrinsic = compileAndInstallSubstitution(intrinsicMethod);
        Assert.assertTrue("missing intrinsic", intrinsic != null);
        Result actual = runEncryptDecrypt(key, algorithm);
        assertEquals(expected, actual);
        intrinsic.invalidate();
    }

    @Test
    public void testAESEncryptBlock() throws Exception {
        Assume.assumeTrue("AESNode not supported", AESNode.isSupported(getArchitecture()));
        testEncryptDecrypt("com.sun.crypto.provider.AESCrypt", "implEncryptBlock", "AES", 128, "AES/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.AESCrypt", "implEncryptBlock", "AES", 192, "AES/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.AESCrypt", "implEncryptBlock", "AES", 256, "AES/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.AESCrypt", "implEncryptBlock", "AES", 128, "AES/CBC/PKCS5Padding");
        testEncryptDecrypt("com.sun.crypto.provider.AESCrypt", "implEncryptBlock", "AES", 192, "AES/CBC/PKCS5Padding");
        testEncryptDecrypt("com.sun.crypto.provider.AESCrypt", "implEncryptBlock", "AES", 256, "AES/CBC/PKCS5Padding");
    }

    @Test
    public void testAESDecryptBlock() throws Exception {
        Assume.assumeTrue("AESNode not supported", AESNode.isSupported(getArchitecture()));
        testEncryptDecrypt("com.sun.crypto.provider.AESCrypt", "implDecryptBlock", "AES", 128, "AES/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.AESCrypt", "implDecryptBlock", "AES", 192, "AES/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.AESCrypt", "implDecryptBlock", "AES", 256, "AES/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.AESCrypt", "implDecryptBlock", "AES", 128, "AES/CBC/PKCS5Padding");
        testEncryptDecrypt("com.sun.crypto.provider.AESCrypt", "implDecryptBlock", "AES", 192, "AES/CBC/PKCS5Padding");
        testEncryptDecrypt("com.sun.crypto.provider.AESCrypt", "implDecryptBlock", "AES", 256, "AES/CBC/PKCS5Padding");
    }

    @Test
    public void testCipherBlockChainingEncrypt() throws Exception {
        Assume.assumeTrue("CipherBlockChainingAESNode not supported", CipherBlockChainingAESNode.isSupported(getArchitecture()));
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", "implEncrypt", "AES", 128, "AES/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", "implEncrypt", "AES", 192, "AES/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", "implEncrypt", "AES", 256, "AES/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", "implEncrypt", "AES", 128, "AES/CBC/PKCS5Padding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", "implEncrypt", "AES", 192, "AES/CBC/PKCS5Padding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", "implEncrypt", "AES", 256, "AES/CBC/PKCS5Padding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", "implEncrypt", "DESede", 168, "DESede/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", "implEncrypt", "DESede", 168, "DESede/CBC/PKCS5Padding");
    }

    @Test
    public void testCipherBlockChainingDecrypt() throws Exception {
        Assume.assumeTrue("CipherBlockChainingAESNode not supported", CipherBlockChainingAESNode.isSupported(getArchitecture()));
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", "implDecrypt", "AES", 128, "AES/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", "implDecrypt", "AES", 192, "AES/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", "implDecrypt", "AES", 256, "AES/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", "implDecrypt", "AES", 128, "AES/CBC/PKCS5Padding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", "implDecrypt", "AES", 192, "AES/CBC/PKCS5Padding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", "implDecrypt", "AES", 256, "AES/CBC/PKCS5Padding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", "implDecrypt", "DESede", 168, "DESede/CBC/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CipherBlockChaining", "implDecrypt", "DESede", 168, "DESede/CBC/PKCS5Padding");
    }

    @Test
    public void testCounterModeEncrypt() throws Exception {
        Assume.assumeTrue("CounterModeAESNode not supported", CounterModeAESNode.isSupported(getArchitecture()));
        testEncryptDecrypt("com.sun.crypto.provider.CounterMode", "implCrypt", "AES", 128, "AES/CTR/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CounterMode", "implCrypt", "AES", 192, "AES/CTR/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CounterMode", "implCrypt", "AES", 256, "AES/CTR/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CounterMode", "implCrypt", "AES", 128, "AES/CTR/PKCS5Padding");
        testEncryptDecrypt("com.sun.crypto.provider.CounterMode", "implCrypt", "AES", 192, "AES/CTR/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CounterMode", "implCrypt", "AES", 256, "AES/CTR/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CounterMode", "implCrypt", "DESede", 168, "DESede/CTR/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.CounterMode", "implCrypt", "DESede", 168, "DESede/CTR/PKCS5Padding");
    }

    @Test
    public void testEletronicCodeBookEncrypt() throws Exception {
        Assume.assumeTrue("ElectronicCodeBook encrypt not supported", runtime().getVMConfig().electronicCodeBookEncrypt != 0L);
        testEncryptDecrypt("com.sun.crypto.provider.ElectronicCodeBook", "implECBEncrypt", "AES", 128, "AES/ECB/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.ElectronicCodeBook", "implECBEncrypt", "AES", 192, "AES/ECB/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.ElectronicCodeBook", "implECBEncrypt", "AES", 256, "AES/ECB/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.ElectronicCodeBook", "implECBEncrypt", "AES", 128, "AES/ECB/PKCS5Padding");
        testEncryptDecrypt("com.sun.crypto.provider.ElectronicCodeBook", "implECBEncrypt", "AES", 192, "AES/ECB/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.ElectronicCodeBook", "implECBEncrypt", "AES", 256, "AES/ECB/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.ElectronicCodeBook", "implECBEncrypt", "DESede", 168, "DESede/ECB/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.ElectronicCodeBook", "implECBEncrypt", "DESede", 168, "DESede/ECB/PKCS5Padding");
    }

    @Test
    public void testEletronicCodeBookDecrypt() throws Exception {
        Assume.assumeTrue("ElectronicCodeBook decrypt not supported", runtime().getVMConfig().electronicCodeBookDecrypt != 0L);
        testEncryptDecrypt("com.sun.crypto.provider.ElectronicCodeBook", "implECBDecrypt", "AES", 128, "AES/ECB/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.ElectronicCodeBook", "implECBDecrypt", "AES", 192, "AES/ECB/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.ElectronicCodeBook", "implECBDecrypt", "AES", 256, "AES/ECB/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.ElectronicCodeBook", "implECBDecrypt", "AES", 128, "AES/ECB/PKCS5Padding");
        testEncryptDecrypt("com.sun.crypto.provider.ElectronicCodeBook", "implECBDecrypt", "AES", 192, "AES/ECB/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.ElectronicCodeBook", "implECBDecrypt", "AES", 256, "AES/ECB/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.ElectronicCodeBook", "implECBDecrypt", "DESede", 168, "DESede/ECB/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.ElectronicCodeBook", "implECBDecrypt", "DESede", 168, "DESede/ECB/PKCS5Padding");
    }

    @Test
    public void testGaloisCounterModeCrypt() throws Exception {
        Assume.assumeTrue("GaloisCounterMode not supported", runtime().getVMConfig().galoisCounterModeCrypt != 0L);
        testEncryptDecrypt("com.sun.crypto.provider.GaloisCounterMode", "implGCMCrypt0", "AES", 128, "AES/GCM/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.GaloisCounterMode", "implGCMCrypt0", "AES", 128, "AES/GCM/PKCS5Padding");
        testEncryptDecrypt("com.sun.crypto.provider.GaloisCounterMode", "implGCMCrypt0", "DESede", 168, "DESede/GCM/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.GaloisCounterMode", "implGCMCrypt0", "DESede", 168, "DESede/GCM/PKCS5Padding");
    }

    @Test
    public void testPoly1305() throws Exception {
        Assume.assumeTrue("Poly1305 not supported", runtime().getVMConfig().poly1305ProcessBlocks != 0L);
        testEncryptDecrypt(getResolvedJavaMethod("com.sun.crypto.provider.Poly1305", "processMultipleBlocks", byte[].class, int.class, int.class, long[].class, long[].class),
                        "ChaCha20", 256, "ChaCha20-Poly1305/None/NoPadding");
        testEncryptDecrypt(getResolvedJavaMethod("com.sun.crypto.provider.Poly1305", "processMultipleBlocks", byte[].class, int.class, int.class, long[].class, long[].class),
                        "ChaCha20", 256, "ChaCha20-Poly1305/ECB/NoPadding");
        testEncryptDecrypt(getResolvedJavaMethod("com.sun.crypto.provider.Poly1305", "processMultipleBlocks", byte[].class, int.class, int.class, long[].class, long[].class),
                        "ChaCha20", 256, "ChaCha20-Poly1305/None/PKCS5Padding");
        testEncryptDecrypt(getResolvedJavaMethod("com.sun.crypto.provider.Poly1305", "processMultipleBlocks", byte[].class, int.class, int.class, long[].class, long[].class),
                        "ChaCha20", 256, "ChaCha20-Poly1305/ECB/PKCS5Padding");
    }

    @Test
    public void testChaCha20() throws Exception {
        Assume.assumeTrue("ChaCha20 not support", runtime().getVMConfig().chacha20Block != 0L);
        testEncryptDecrypt("com.sun.crypto.provider.ChaCha20Cipher", "implChaCha20Block", "ChaCha20", 256, "ChaCha20-Poly1305/None/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.ChaCha20Cipher", "implChaCha20Block", "ChaCha20", 256, "ChaCha20-Poly1305/ECB/NoPadding");
        testEncryptDecrypt("com.sun.crypto.provider.ChaCha20Cipher", "implChaCha20Block", "ChaCha20", 256, "ChaCha20-Poly1305/None/PKCS5Padding");
        testEncryptDecrypt("com.sun.crypto.provider.ChaCha20Cipher", "implChaCha20Block", "ChaCha20", 256, "ChaCha20-Poly1305/ECB/PKCS5Padding");
    }

    AlgorithmParameters algorithmParameters;

    private byte[] encrypt(byte[] indata, SecretKey key, String algorithm) throws GeneralSecurityException {
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

    private byte[] decrypt(byte[] indata, SecretKey key, String algorithm) throws GeneralSecurityException {
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

    public Result runEncryptDecrypt(SecretKey key, String algorithm) throws GeneralSecurityException {
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
        Assume.assumeTrue("SHA1 not supported", runtime().getVMConfig().sha1ImplCompressMultiBlock != 0L);
        testDigestBase("sun.security.provider.DigestBase", "implCompressMultiBlock", "SHA-1", SHA_IMPL_COMPRESS_MB);
    }

    @Test
    public void testDigestBaseSHA2() throws Exception {
        Assume.assumeTrue("SHA256 not supported", runtime().getVMConfig().sha256ImplCompressMultiBlock != 0L);
        testDigestBase("sun.security.provider.DigestBase", "implCompressMultiBlock", "SHA-256", SHA2_IMPL_COMPRESS_MB);
    }

    @Test
    public void testDigestBaseSHA5() throws Exception {
        Assume.assumeTrue("SHA512 not supported", runtime().getVMConfig().sha512ImplCompressMultiBlock != 0L);
        testDigestBase("sun.security.provider.DigestBase", "implCompressMultiBlock", "SHA-512", SHA5_IMPL_COMPRESS_MB);
    }

    @Test
    public void testDigestBaseSHA3() throws Exception {
        Assume.assumeTrue("SHA3 not supported", runtime().getVMConfig().sha3ImplCompressMultiBlock != 0L);
        testDigestBase("sun.security.provider.DigestBase", "implCompressMultiBlock", "SHA3-512", SHA3_IMPL_COMPRESS_MB);
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
        InstalledCode intrinsic = getCode(method, graph, false, true, GraalCompilerTest.getInitialOptions());
        try {
            Assert.assertNotNull("missing intrinsic", intrinsic);
            byte[] actual = digest.digest(input.clone());
            assertDeepEquals(expected, actual);
        } finally {
            intrinsic.invalidate();
        }
    }

    public byte[] testDigest(String name, byte[] data) throws NoSuchAlgorithmException, NoSuchProviderException {
        MessageDigest digest;
        digest = MessageDigest.getInstance(name, "SUN");
        digest.update(data);
        return digest.digest();
    }

    byte[] getData() {
        byte[] data = new byte[1024 * 16];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        return data;
    }

    @Test
    public void testSha1() {
        Assume.assumeTrue("SHA1 not supported", SHA1Node.isSupported(getArchitecture()));
        testWithInstalledIntrinsic("sun.security.provider.SHA", "implCompress0", "testDigest", "SHA-1", getData());
    }

    void testWithInstalledIntrinsic(String className, String methodName, String testSnippetName, String algorithm, byte[] data) {
        // Ensure the algorithm exists
        try {
            MessageDigest.getInstance(algorithm, "SUN");
        } catch (NoSuchAlgorithmException e) {
            assertFalse(true, "unknown algorithm " + algorithm);
        } catch (NoSuchProviderException e) {
            throw new RuntimeException(e);
        }

        Class<?> c;
        try {
            c = Class.forName(className);
        } catch (ClassNotFoundException e) {
            // It's ok to not find the class - a different security provider
            // may have been installed
            Assume.assumeTrue(className + " is not available", false);
            return;
        }
        InstalledCode code = null;
        try {
            ResolvedJavaMethod method = getResolvedJavaMethod(testSnippetName);
            Object receiver = method.isStatic() ? null : this;
            GraalCompilerTest.Result expect = executeExpected(method, receiver, algorithm, data);
            code = compileAndInstallSubstitution(c, methodName);
            assertTrue("Failed to install " + methodName, code != null);
            testAgainstExpected(method, expect, receiver, algorithm, data);
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
        Assume.assumeTrue("SHA256 not supported", SHA256Node.isSupported(getArchitecture()));
        testWithInstalledIntrinsic("sun.security.provider.SHA2", "implCompress0", "testDigest", "SHA-256", getData());
    }

    @Test
    public void testSha512() {
        Assume.assumeTrue("SHA512 not supported", SHA512Node.isSupported(getArchitecture()));
        testWithInstalledIntrinsic("sun.security.provider.SHA5", "implCompress0", "testDigest", "SHA-512", getData());
    }

    @Test
    public void testSha3() {
        Assume.assumeTrue("SHA3 not supported", SHA3Node.isSupported(getArchitecture()));
        testWithInstalledIntrinsic("sun.security.provider.SHA3", "implCompress0", "testDigest", "SHA3-512", getData());
    }

    @Test
    public void testMD5() {
        testWithInstalledIntrinsic("sun.security.provider.MD5", "implCompress0", "testDigest", "MD5", getData());
    }
}
