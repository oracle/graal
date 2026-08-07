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
package com.oracle.svm.configure.test.config;

import static org.junit.Assume.assumeTrue;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.KEM;
import javax.crypto.KEMSpi;

import org.junit.Assert;
import org.junit.Test;

/**
 * Exercises provider enumeration under the native-image agent. The JCK harness and other
 * applications can initialize some providers before their test code enumerates the provider list,
 * so the agent must record both newly loaded and already cached providers.
 */
public class SecurityProviderAgentTest {
    private static final String GENERATOR_ENABLED_PROPERTY = SecurityProviderAgentTest.class.getName() + ".generator.enabled";
    private static final String KEM_ALGORITHM = "AgentKEM";
    private static final String DELAYED_KEM_ALGORITHM = "AgentDelayedKEM";

    /** Tests §FS-security-providers.6.1. */
    @Test
    public void enumerateSecurityProviders() throws Exception {
        assumeTrue("Test must be explicitly enabled because it is designed to run under the agent",
                        Boolean.getBoolean(GENERATOR_ENABLED_PROPERTY));

        Provider[] providers = Security.getProviders();
        Assert.assertTrue("The JDK must have at least one configured security provider", providers.length > 0);
        Assert.assertSame(ReflectiveProbe.class, Class.forName(ReflectiveProbe.class.getName()));
    }

    /** Tests §FS-security-providers.6.1. */
    @Test
    public void programmaticProviderMutationDoesNotTraceConfiguredProviders() throws Exception {
        assumeTrue("Test must be explicitly enabled because it is designed to run under the agent",
                        Boolean.getBoolean(GENERATOR_ENABLED_PROPERTY));

        Provider provider = new ProgrammaticallyAddedProvider();
        int position = Security.addProvider(provider);
        try {
            Assert.assertTrue("The test provider must be added", position > 0);
            Assert.assertSame(ReflectiveProbe.class, Class.forName(ReflectiveProbe.class.getName()));
        } finally {
            Security.removeProvider(provider.getName());
        }
    }

    /** Tests §FS-security-providers.6.1. */
    @Test
    public void providerServiceHelpersRetainConstructorMetadata() throws Exception {
        assumeTrue("Test must be explicitly enabled because it is designed to run under the agent",
                        Boolean.getBoolean(GENERATOR_ENABLED_PROPERTY));

        Provider provider = new ProgrammaticallyAddedKEMProvider();
        int position = Security.addProvider(provider);
        try {
            Assert.assertTrue("The test provider must be added", position > 0);
            Assert.assertNotNull(KEM.getInstance(KEM_ALGORITHM, provider));
        } finally {
            Security.removeProvider(provider.getName());
        }
    }

    /** Tests §FS-security-providers.6.1 for selection before service instantiation. */
    @Test
    public void jceServiceSelectionRetainsConstructorMetadata() throws Exception {
        assumeTrue("Test must be explicitly enabled because it is designed to run under the agent",
                        Boolean.getBoolean(GENERATOR_ENABLED_PROPERTY));

        Provider provider = new ProgrammaticallyAddedDelayedKEMProvider();
        int position = Security.addProvider(provider);
        try {
            Assert.assertTrue("The test provider must be added", position > 0);
            Assert.assertNotNull(KEM.getInstance(DELAYED_KEM_ALGORITHM));
        } finally {
            Security.removeProvider(provider.getName());
        }
    }

    static final class ReflectiveProbe {
    }

    static final class ProgrammaticallyAddedProvider extends Provider {
        private static final long serialVersionUID = 1L;

        @SuppressWarnings("deprecation")
        ProgrammaticallyAddedProvider() {
            super("AgentMutationProvider", 1.0, "Provider used to verify mutation tracing");
        }
    }

    static final class ProgrammaticallyAddedKEMProvider extends Provider {
        private static final long serialVersionUID = 1L;

        @SuppressWarnings("deprecation")
        ProgrammaticallyAddedKEMProvider() {
            super("AgentKEMProvider", 1.0, "Provider used to verify service constructor tracing");
            put("KEM." + KEM_ALGORITHM, TestKEM.class.getName());
        }
    }

    static final class ProgrammaticallyAddedDelayedKEMProvider extends Provider {
        private static final long serialVersionUID = 1L;

        @SuppressWarnings("deprecation")
        ProgrammaticallyAddedDelayedKEMProvider() {
            super("AgentDelayedKEMProvider", 1.0, "Provider used to verify pre-instantiation service tracing");
            put("KEM." + DELAYED_KEM_ALGORITHM, DelayedTestKEM.class.getName());
        }
    }

    public static final class TestKEM implements KEMSpi {
        @Override
        public EncapsulatorSpi engineNewEncapsulator(PublicKey publicKey, AlgorithmParameterSpec spec, SecureRandom secureRandom)
                        throws InvalidAlgorithmParameterException, InvalidKeyException {
            throw new UnsupportedOperationException();
        }

        @Override
        public DecapsulatorSpi engineNewDecapsulator(PrivateKey privateKey, AlgorithmParameterSpec spec)
                        throws InvalidAlgorithmParameterException, InvalidKeyException {
            throw new UnsupportedOperationException();
        }
    }

    public static final class DelayedTestKEM implements KEMSpi {
        @Override
        public EncapsulatorSpi engineNewEncapsulator(PublicKey publicKey, AlgorithmParameterSpec spec, SecureRandom secureRandom)
                        throws InvalidAlgorithmParameterException, InvalidKeyException {
            throw new UnsupportedOperationException();
        }

        @Override
        public DecapsulatorSpi engineNewDecapsulator(PrivateKey privateKey, AlgorithmParameterSpec spec)
                        throws InvalidAlgorithmParameterException, InvalidKeyException {
            throw new UnsupportedOperationException();
        }
    }
}
