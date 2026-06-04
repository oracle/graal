/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.services;

import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.svm.core.FutureDefaultsOptions;
import com.oracle.svm.test.NativeImageBuildArgs;

/**
 * Tests that JCE provider verification results are bound to provider implementation classes.
 */
@NativeImageBuildArgs({
                "--future-defaults=run-time-initialize-security-providers",
                "-H:AdditionalSecurityProviders=com.oracle.svm.test.services.SecurityProviderVerificationTest$BuildTimeProvider"
})
public class SecurityProviderVerificationTest {
    private static final String PROVIDER_NAME = "same-name-test-provider";
    private static final String CIPHER_ALGORITHM = "SameNameCipher";

    @Test
    public void testSameNameProviderDoesNotReuseVerificationResult() throws Exception {
        Assume.assumeTrue("needs runtime security-provider initialization", FutureDefaultsOptions.securityProvidersInitializedAtRunTime());

        Security.removeProvider(PROVIDER_NAME);
        Provider runtimeProvider = new RuntimeProvider();
        try {
            Security.addProvider(runtimeProvider);
            Cipher.getInstance(CIPHER_ALGORITHM, runtimeProvider);
            Assert.fail("A provider must not reuse another provider class' verification result only because the provider names match.");
        } catch (SecurityException e) {
            Assert.assertTrue("Error should identify the unverified provider class.",
                            e.getMessage().contains(RuntimeProvider.class.getName()));
        } finally {
            Security.removeProvider(PROVIDER_NAME);
        }
    }

    /**
     * Provider that is verified during image generation.
     */
    public static final class BuildTimeProvider extends Provider {
        static final long serialVersionUID = 1L;

        @SuppressWarnings("deprecation")
        public BuildTimeProvider() {
            super(PROVIDER_NAME, 1.0, "Provider verified during image generation.");
            putService(new Service(this, "Cipher", CIPHER_ALGORITHM, NoOpCipher.class.getName(), null, null));
        }
    }

    /**
     * Provider with the same provider name but a different implementation class.
     */
    public static final class RuntimeProvider extends Provider {
        static final long serialVersionUID = 1L;

        @SuppressWarnings("deprecation")
        public RuntimeProvider() {
            super(PROVIDER_NAME, 1.0, "Provider registered only at run time.");
            putService(new Service(this, "Cipher", CIPHER_ALGORITHM, NoOpCipher.class.getName(), null, null));
        }
    }

    /**
     * Minimal cipher implementation used only to reach JCE provider verification.
     */
    public static final class NoOpCipher extends CipherSpi {
        @Override
        protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
        }

        @Override
        protected void engineSetPadding(String padding) throws NoSuchPaddingException {
        }

        @Override
        protected int engineGetBlockSize() {
            return 1;
        }

        @Override
        protected int engineGetOutputSize(int inputLen) {
            return inputLen;
        }

        @Override
        protected byte[] engineGetIV() {
            return new byte[0];
        }

        @Override
        protected AlgorithmParameters engineGetParameters() {
            return null;
        }

        @Override
        protected void engineInit(int opmode, Key key, java.security.SecureRandom random) throws InvalidKeyException {
        }

        @Override
        protected void engineInit(int opmode, Key key, AlgorithmParameterSpec params, java.security.SecureRandom random) throws InvalidKeyException, InvalidAlgorithmParameterException {
        }

        @Override
        protected void engineInit(int opmode, Key key, AlgorithmParameters params, java.security.SecureRandom random) throws InvalidKeyException, InvalidAlgorithmParameterException {
        }

        @Override
        protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
            return input == null ? new byte[0] : input.clone();
        }

        @Override
        protected int engineUpdate(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset) throws ShortBufferException {
            return 0;
        }

        @Override
        protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen) throws IllegalBlockSizeException, BadPaddingException {
            return input == null ? new byte[0] : input.clone();
        }

        @Override
        protected int engineDoFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
            return 0;
        }
    }
}
