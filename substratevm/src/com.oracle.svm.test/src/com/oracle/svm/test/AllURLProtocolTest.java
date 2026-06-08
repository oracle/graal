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

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.nativeimage.ImageInfo;
import org.junit.Assert;
import org.junit.Test;

@NativeImageBuildArgs({
                "-H:+UnlockExperimentalVMOptions",
                "-H:+AllowJRTFileSystem",
                "--add-modules=java.xml",
                "--enable-url-protocols=all"
})
public class AllURLProtocolTest {
    private static final String JDK_CATALOG_RESOURCE = "jdk/xml/internal/jdkcatalog/JDKCatalog.xml";

    @Test
    public void allModeEnablesKnownJDKProtocols() throws Exception {
        URI.create("file:/tmp/native-image-url-protocol-test").toURL();
        URI.create("ftp://example.com/resource.txt").toURL();
        URI.create("http://example.com").toURL();
        URI.create("https://example.com").toURL();
        URI.create("jar:file:/tmp/missing.jar!/resource.txt").toURL();
        URI.create("jmod:file:/tmp/missing.jmod!/classes/module-info.class").toURL();
        URI.create("jrt:/java.base").toURL();
        URI.create("mailto:hello@example.com").toURL();
    }

    @Test
    public void jrtUsesEmbeddedModuleResourcesWithoutRuntimeModulesImage() throws Exception {
        if (!ImageInfo.inImageRuntimeCode()) {
            return;
        }

        String previousJavaHome = System.getProperty("java.home");
        Path fakeJavaHome = Files.createTempDirectory("native-image-no-runtime-modules");
        try {
            System.setProperty("java.home", fakeJavaHome.toString());
            try (InputStream stream = URI.create("jrt:/java.xml/" + JDK_CATALOG_RESOURCE).toURL().openStream()) {
                Assert.assertNotEquals(-1, stream.read());
            }
        } finally {
            if (previousJavaHome == null) {
                System.clearProperty("java.home");
            } else {
                System.setProperty("java.home", previousJavaHome);
            }
            Files.deleteIfExists(fakeJavaHome);
        }
    }
}
