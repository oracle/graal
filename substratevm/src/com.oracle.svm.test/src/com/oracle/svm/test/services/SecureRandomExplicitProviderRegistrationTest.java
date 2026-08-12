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

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.test.NativeImageBuildArgs;

/*
 * The otherwise-unused property is an image-group discriminator. This test must not share an image
 * with a test containing reachable new SecureRandom(), because that is a different platform signal.
 */
@NativeImageBuildArgs({
                "--future-defaults=explicit-security-provider-registration",
                "--exact-reachability-metadata=com.oracle.svm.test.services",
                "-Dcom.oracle.svm.test.services.SecureRandomExplicitProviderRegistrationTest=true"
})
public class SecureRandomExplicitProviderRegistrationTest {
    /** Tests §FS-002-security-providers.2.4. */
    @Test
    public void testNamedSecureRandomUsesPlatformRegistrationSignal() throws NoSuchAlgorithmException {
        SecureRandom random = SecureRandom.getInstance("DRBG");

        Assert.assertEquals("SUN", random.getProvider().getName());
        Assert.assertEquals("The platform registration signal must retain the implementation.", 1,
                        random.generateSeed(1).length);
    }
}
