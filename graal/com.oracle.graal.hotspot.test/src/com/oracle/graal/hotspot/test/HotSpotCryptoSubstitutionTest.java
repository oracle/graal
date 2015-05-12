/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.test;

import static com.oracle.graal.graphbuilderconf.IntrinsicContext.CompilationContext.*;

import java.io.*;
import java.lang.reflect.*;
import java.security.*;

import javax.crypto.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.bridge.CompilerToVM.CodeInstallResult;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.phases.*;

/**
 * Tests the intrinsification of certain crypto methods.
 */
public class HotSpotCryptoSubstitutionTest extends HotSpotGraalCompilerTest {

    @Override
    protected InstalledCode addMethod(ResolvedJavaMethod method, CompilationResult compResult) {
        HotSpotResolvedJavaMethod hsMethod = (HotSpotResolvedJavaMethod) method;
        HotSpotNmethod installedCode = new HotSpotNmethod(hsMethod, compResult.getName(), true);
        HotSpotCompiledNmethod compiledNmethod = new HotSpotCompiledNmethod(hsMethod, compResult);
        CodeInstallResult result = runtime().getCompilerToVM().installCode(compiledNmethod, installedCode, null);
        Assert.assertEquals("Error installing method " + method + ": " + result, result, CodeInstallResult.OK);

        // HotSpotRuntime hsRuntime = (HotSpotRuntime) getCodeCache();
        // TTY.println(hsMethod.toString());
        // TTY.println(hsRuntime.disassemble(installedCode));
        return installedCode;
    }

    SecretKey aesKey;
    SecretKey desKey;
    byte[] input;
    ByteArrayOutputStream aesExpected = new ByteArrayOutputStream();
    ByteArrayOutputStream desExpected = new ByteArrayOutputStream();

    public HotSpotCryptoSubstitutionTest() throws Exception {
        byte[] seed = {0x4, 0x7, 0x1, 0x1};
        SecureRandom random = new SecureRandom(seed);
        KeyGenerator aesKeyGen = KeyGenerator.getInstance("AES");
        KeyGenerator desKeyGen = KeyGenerator.getInstance("DESede");
        aesKeyGen.init(128, random);
        desKeyGen.init(168, random);
        aesKey = aesKeyGen.generateKey();
        desKey = desKeyGen.generateKey();
        input = readClassfile16(getClass());

        aesExpected.write(runEncryptDecrypt(aesKey, "AES/CBC/NoPadding"));
        aesExpected.write(runEncryptDecrypt(aesKey, "AES/CBC/PKCS5Padding"));

        desExpected.write(runEncryptDecrypt(desKey, "DESede/CBC/NoPadding"));
        desExpected.write(runEncryptDecrypt(desKey, "DESede/CBC/PKCS5Padding"));
    }

    @Test
    public void testAESCryptIntrinsics() throws Exception {
        if (compileAndInstall("com.sun.crypto.provider.AESCrypt", "encryptBlock", "decryptBlock")) {
            ByteArrayOutputStream actual = new ByteArrayOutputStream();
            actual.write(runEncryptDecrypt(aesKey, "AES/CBC/NoPadding"));
            actual.write(runEncryptDecrypt(aesKey, "AES/CBC/PKCS5Padding"));
            Assert.assertArrayEquals(aesExpected.toByteArray(), actual.toByteArray());
        }
    }

    @Test
    public void testCipherBlockChainingIntrinsics() throws Exception {
        if (compileAndInstall("com.sun.crypto.provider.CipherBlockChaining", "encrypt", "decrypt")) {
            ByteArrayOutputStream actual = new ByteArrayOutputStream();
            actual.write(runEncryptDecrypt(aesKey, "AES/CBC/NoPadding"));
            actual.write(runEncryptDecrypt(aesKey, "AES/CBC/PKCS5Padding"));
            Assert.assertArrayEquals(aesExpected.toByteArray(), actual.toByteArray());

            actual.reset();
            actual.write(runEncryptDecrypt(desKey, "DESede/CBC/NoPadding"));
            actual.write(runEncryptDecrypt(desKey, "DESede/CBC/PKCS5Padding"));
            Assert.assertArrayEquals(desExpected.toByteArray(), actual.toByteArray());
        }
    }

    /**
     * Compiles and installs the substitution for some specified methods. Once installed, the next
     * execution of the methods will use the newly installed code.
     *
     * @param className the name of the class for which substitutions are available
     * @param methodNames the names of the substituted methods
     * @return true if at least one substitution was compiled and installed
     */
    private boolean compileAndInstall(String className, String... methodNames) {
        boolean atLeastOneCompiled = false;
        for (String methodName : methodNames) {
            Method method = lookup(className, methodName);
            if (method != null) {
                ResolvedJavaMethod installedCodeOwner = getMetaAccess().lookupJavaMethod(method);
                StructuredGraph subst = getReplacements().getSubstitution(installedCodeOwner, 0);
                ResolvedJavaMethod substMethod = subst == null ? null : subst.method();
                if (substMethod != null) {
                    StructuredGraph graph = new StructuredGraph(substMethod, AllowAssumptions.YES);
                    Plugins plugins = new Plugins(((HotSpotProviders) getProviders()).getGraphBuilderPlugins());
                    GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);
                    IntrinsicContext initialReplacementContext = new IntrinsicContext(installedCodeOwner, substMethod, ROOT_COMPILATION);
                    new GraphBuilderPhase.Instance(getMetaAccess(), getProviders().getStampProvider(), getConstantReflection(), config, OptimisticOptimizations.NONE, initialReplacementContext).apply(graph);
                    Assert.assertNotNull(getCode(installedCodeOwner, graph, true));
                    atLeastOneCompiled = true;
                } else {
                    Assert.assertFalse(runtime().getConfig().useAESIntrinsics);
                }
            }
        }
        return atLeastOneCompiled;
    }

    private static Method lookup(String className, String methodName) {
        Class<?> c;
        try {
            c = Class.forName(className);
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    return m;
                }
            }
            // If the expected security provider exists, the specific method should also exist
            throw new NoSuchMethodError(className + "." + methodName);
        } catch (ClassNotFoundException e) {
            // It's ok to not find the class - a different security provider
            // may have been installed
            return null;
        }
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

    public byte[] runEncryptDecrypt(SecretKey key, String algorithm) throws Exception {
        byte[] indata = input.clone();
        byte[] cipher = encrypt(indata, key, algorithm);
        byte[] plain = decrypt(cipher, key, algorithm);
        Assert.assertArrayEquals(indata, plain);
        return plain;
    }
}
