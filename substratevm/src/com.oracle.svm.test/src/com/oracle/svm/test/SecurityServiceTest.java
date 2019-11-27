/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test;

import java.security.Provider;
import java.security.Security;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the {@code SecurityServicesFeature}.
 */
public class SecurityServiceTest {
    public static class TestFeature implements Feature {

        @Override
        public void duringSetup(final DuringSetupAccess access) {
            // we use these (application) classes during Native image build
            RuntimeClassInitialization.initializeAtBuildTime(NoOpService.class);
            RuntimeClassInitialization.initializeAtBuildTime(NoOpProvider.class);

            // register the provider
            Security.addProvider(new NoOpProvider());
        }
    }

    /**
     * Tests that native-image generation doesn't run into an issue (like NPE) if the application
     * uses a java.security.Provider.Service which isn't part of the services shipped in the JDK.
     *
     * @throws Exception
     * @see <a href="https://github.com/oracle/graal/issues/1883">issue-1883</a>
     */
    @Test
    public void testUnKnownSecurityServices() throws Exception {
        final Provider registered = Security.getProvider("no-op-provider");
        Assert.assertNotNull("Service is not registered", registered);
        // make Provider.Service#newInstance(...) "reachable" for native-image to trigger
        // certain (auto) reflection registrations through the SecurityServicesFeature
        final Object impl = registered.getService("NoOp", "no-op-algo").newInstance(null);
        Assert.assertNotNull("No service instance was created", impl);
        Assert.assertThat("Unexpected service implementation class", impl, CoreMatchers.instanceOf(NoOpImpl.class));
    }

    private static final class NoOpProvider extends Provider {

        static final long serialVersionUID = 1234L;

        /*
         * The java.security.Provider(String name, double version, String info) constructor was
         * deprecated in Java > 8
         */
        @SuppressWarnings("deprecation")
        protected NoOpProvider() {
            super("no-op-provider", 1.0, "No-op provider used in " + SecurityServiceTest.class.getName());
            putService(new NoOpService(this));
        }
    }

    private static final class NoOpService extends Provider.Service {
        NoOpService(final Provider provider) {
            super(provider, "NoOp", "no-op-algo", NoOpImpl.class.getName(), null, null);
        }
    }

    public static final class NoOpImpl {
        public NoOpImpl() {

        }
    }
}
