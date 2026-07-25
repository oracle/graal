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

import java.security.Provider;
import java.security.Security;

import org.junit.Assert;
import org.junit.Test;

/**
 * Exercises provider enumeration under the native-image agent. The JCK harness and other
 * applications can initialize some providers before their test code enumerates the provider list,
 * so the agent must record both newly loaded and already cached providers.
 */
public class SecurityProviderAgentTest {
    private static final String GENERATOR_ENABLED_PROPERTY = SecurityProviderAgentTest.class.getName() + ".generator.enabled";

    /** Tests §FS-security-providers.6.1. */
    @Test
    public void enumerateSecurityProviders() throws Exception {
        assumeTrue("Test must be explicitly enabled because it is designed to run under the agent",
                        Boolean.getBoolean(GENERATOR_ENABLED_PROPERTY));

        Provider[] providers = Security.getProviders();
        Assert.assertTrue("The JDK must have at least one configured security provider", providers.length > 0);
        Assert.assertSame(ReflectiveProbe.class, Class.forName(ReflectiveProbe.class.getName()));
    }

    static final class ReflectiveProbe {
    }
}
