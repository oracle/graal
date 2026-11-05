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
package com.oracle.svm.test;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.core.jdk.Resources;

@NativeImageBuildArgs({
                "-H:+UnlockExperimentalVMOptions",
                "-H:+AllowJRTFileSystem",
                "-H:DisableURLProtocols=file,resource,disabled,jrt",
                "--features=com.oracle.svm.test.DisabledURLProtocolTest$TestFeature"
})
public class DisabledURLProtocolTest {
    private static final String REGISTERED_RESOURCE = "resources/resource-test1.txt";

    @Test
    @SuppressWarnings("deprecation")
    public void disabledBuiltInProtocolIsRejectedBeforeLookup() {
        assertUnknownProtocol("file", () -> new URL("file:/tmp/native-image-url-disable-test"));
        assertUnknownProtocol("file", () -> URI.create("file:/tmp/native-image-url-disable-test").toURL());
        assertUnknownProtocol("resource", () -> new URL("resource:/native-image-url-disable-test"));
        assertUnknownProtocol("resource", () -> URI.create("resource:/native-image-url-disable-test").toURL());
    }

    @Test
    public void disabledJRTProtocolIsRejectedByURIToURLFastPath() {
        assertUnknownProtocol("jrt", () -> URI.create(opaque("jrt:/java.base/java/lang/Object.class")).toURL());
    }

    @Test
    public void disabledResourceProtocolIsRejectedByClassResourceURLs() throws IOException {
        try (InputStream stream = Resources.createInputStream(null, REGISTERED_RESOURCE)) {
            Assert.assertNotNull(stream);
        }

        Assert.assertNull(DisabledURLProtocolTest.class.getResource('/' + REGISTERED_RESOURCE));
        Assert.assertNull(DisabledURLProtocolTest.class.getClassLoader().getResource(REGISTERED_RESOURCE));
        Assert.assertFalse(DisabledURLProtocolTest.class.getClassLoader().getResources(REGISTERED_RESOURCE).hasMoreElements());
    }

    @Test
    public void disabledProtocolIsRejectedBeforePropertyHandlerLookup() {
        String oldHandlerPackages = System.getProperty("java.protocol.handler.pkgs");
        System.setProperty("java.protocol.handler.pkgs", "com.oracle.svm.test.protocol");
        try {
            assertUnknownProtocol("disabled", () -> URI.create("disabled:/test").toURL());
        } finally {
            if (oldHandlerPackages == null) {
                System.clearProperty("java.protocol.handler.pkgs");
            } else {
                System.setProperty("java.protocol.handler.pkgs", oldHandlerPackages);
            }
        }
    }

    private static void assertUnknownProtocol(String protocol, URLAction action) {
        MalformedURLException exception = Assert.assertThrows(MalformedURLException.class, action::run);
        Assert.assertTrue(exception.getMessage(), exception.getMessage().contains("unknown protocol: " + protocol));
    }

    private static <T> T opaque(T value) {
        return Math.random() >= 0.0d ? value : null;
    }

    @FunctionalInterface
    private interface URLAction {
        void run() throws MalformedURLException;
    }

    public static final class TestFeature implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            RuntimeResourceAccess.addResource(TestFeature.class.getModule(), REGISTERED_RESOURCE);
            RuntimeReflection.register(com.oracle.svm.test.protocol.disabled.Handler.class);
            RuntimeReflection.register(com.oracle.svm.test.protocol.disabled.Handler.class.getDeclaredConstructors());
        }
    }
}
