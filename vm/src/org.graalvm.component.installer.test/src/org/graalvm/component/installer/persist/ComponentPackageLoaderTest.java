/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.persist;

import java.io.BufferedReader;
import org.graalvm.component.installer.MetadataException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.jar.JarFile;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.TestBase;
import org.graalvm.component.installer.jar.JarMetaLoader;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.DistributionType;
import org.junit.After;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestName;

public class ComponentPackageLoaderTest extends TestBase {
    @Rule public TestName name = new TestName();
    @Rule public ExpectedException exception = ExpectedException.none();
    private Properties data = new Properties();
    private JarFile jarData;

    public ComponentPackageLoaderTest() {
    }

    private JarFile jf;

    @Rule public ExternalResource jarFileResource = new ExternalResource() {
        @Override
        protected void after() {
            if (jf != null) {
                try {
                    jf.close();
                } catch (IOException ex) {
                    // ignore
                }
            }
            super.after();
        }
    };

    @Before
    public void setUp() throws Exception {
        defaultBundle = ResourceBundle.getBundle("org.graalvm.component.installer.persist.Bundle"); // NOI18N
        String s = name.getMethodName();
        if (s.startsWith("test") && s.length() > 4) {
            s = Character.toLowerCase(s.charAt(4)) + s.substring(5);
        }

        Path dp = dataFile("data/" + s + ".jar");
        if (Files.exists(dp)) {
            this.jarData = new JarFile(dp.toFile());
        }
        try (InputStream istm = getClass().getResourceAsStream("data/" + s + ".properties")) {
            if (istm != null) {
                data.load(istm);
            }
        }
    }

    @After
    public void tearDown() throws Exception {
        if (jarData != null) {
            jarData.close();
        }
    }

    ComponentInfo info() throws IOException {
        if (jarData == null) {
            return new ComponentPackageLoader(data::getProperty, this).createComponentInfo();
        } else {
            return new JarMetaLoader(jarData, this).createComponentInfo();
        }
    }

    /**
     * Checks that the parser rejects components, if they have some keys required.
     * 
     * @throws Exception
     */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testRequiredKeys() throws Exception {
        // first try to parse OK to capture possibel bugs
        info();

        Properties save = new Properties();
        save.putAll(this.data);
        List<String> sorted = new ArrayList<>((Set) save.keySet());
        Collections.sort(sorted);
        for (String s : save.stringPropertyNames()) {
            data = new Properties();
            data.putAll(save);
            data.remove(s);

            try {
                info();
                Assert.fail("Pasrer must reject component without key " + s);
            } catch (MetadataException ex) {
                // OK
            }
        }
    }

    @Test
    public void testWorkingDirectories() throws Exception {
        // first try to parse OK to capture possibel bugs
        info = info();

        assertTrue(info.getWorkingDirectories().contains("jre/languages/test/scrap"));
        assertTrue(info.getWorkingDirectories().contains("jre/lib/test/scrapdir"));
    }

    @Test
    public void testCollectErrors() throws Exception {
        File f = dataFile("broken1.zip").toFile();
        jf = new JarFile(f);
        loader = new JarMetaLoader(jf, this).infoOnly(true);
        info = loader.createComponentInfo();

        List<String> errs = new ArrayList<>();
        loader.getErrors().forEach((e) -> errs.add(((MetadataException) e).getOffendingHeader()));
        Collections.sort(errs);
        assertEquals("org.graalvm.ruby", info.getId());
        assertEquals(Arrays.asList(
                        BundleConstants.BUNDLE_NAME,
                        BundleConstants.BUNDLE_REQUIRED,
                        BundleConstants.BUNDLE_VERSION), errs);
    }

    private ComponentPackageLoader loader;
    private ComponentInfo info;

    private void setupLoader() throws IOException {
        File f = dataFile("data/truffleruby2.jar").toFile();
        jf = new JarFile(f);
        loader = new JarMetaLoader(jf, this);
        info = loader.createComponentInfo();
    }

    @Test
    public void testLoadPaths() throws Exception {
        setupLoader();
        assertTrue(info.getPaths().isEmpty());
        loader.loadPaths();
        assertFalse(info.getPaths().isEmpty());
        assertTrue(info.getPaths().contains("jre/bin/ruby"));
    }

    @Test
    public void testLoadSymlinks() throws Exception {
        setupLoader();
        loader.loadPaths();
        Map<String, String> slinks = loader.loadSymlinks();
        assertNotNull(slinks);
        assertNotNull(slinks.get("bin/ruby"));

        // check that empty symlink props parse to nothing:
        loader.parseSymlinks(new Properties());
    }

    @Test
    public void testComponetInfoFromJar() throws Exception {
        setupLoader();
        assertNotNull(info.getId());
        assertEquals("1.0", info.getVersionString());

        Map<String, String> caps = info.getRequiredGraalValues();
        assertNotNull(caps);
        assertNotNull(caps.get(CommonConstants.CAP_GRAALVM_VERSION));
    }

    @Test
    public void testParseChainedSymlinks() throws Exception {
        setupLoader();
        loader.loadPaths();

        Properties links = new Properties();
        try (InputStream istm = new FileInputStream(dataFile("chainedSymlinks.properties").toFile())) {
            links.load(istm);
        }

        loader.parseSymlinks(links);
    }

    @Test
    public void testParseCircularSymlink() throws Exception {
        setupLoader();
        loader.loadPaths();

        Properties links = new Properties();
        try (InputStream istm = new FileInputStream(dataFile("circularSymlinks.properties").toFile())) {
            links.load(istm);
        }
        exception.expect(FailedOperationException.class);
        exception.expectMessage("ERROR_CircularSymlink");
        loader.parseSymlinks(links);
    }

    @Test
    public void testBrokenSymlink() throws Exception {
        setupLoader();
        loader.loadPaths();

        Properties links = new Properties();
        try (InputStream istm = new FileInputStream(dataFile("brokenSymlinks.properties").toFile())) {
            links.load(istm);
        }
        exception.expect(FailedOperationException.class);
        exception.expectMessage("ERROR_BrokenSymlink");
        loader.parseSymlinks(links);
    }

    @Test
    public void testBrokenChainedSymlink() throws Exception {
        setupLoader();
        loader.loadPaths();

        Properties links = new Properties();
        try (InputStream istm = new FileInputStream(dataFile("brokenChainedSymlinks.properties").toFile())) {
            links.load(istm);
        }
        exception.expect(FailedOperationException.class);
        exception.expectMessage("ERROR_BrokenSymlink");
        loader.parseSymlinks(links);
    }

    @Test
    public void testLoadInvalidPermssions() throws Exception {
        setupLoader();
        exception.expect(FailedOperationException.class);
        exception.expectMessage("ERROR_PermissionFormat");
        try (InputStream istm = Files.newInputStream(dataFile("brokenPermissions.properties"));
                        BufferedReader br = new BufferedReader(new InputStreamReader(istm))) {
            loader.parsePermissions(br);
        }
    }

    @Test
    public void testLoadPermssions() throws Exception {
        setupLoader();
        Map<String, String> permissions = loader.loadPermissions();

        String v = permissions.get("./jre/languages/ruby/bin/gem");
        assertEquals("", v);
        v = permissions.get("./jre/bin/ruby");
        assertEquals("r-xr-xr-x", v);
    }

    @Test
    public void testPostinstMessage() throws Exception {
        // first try to parse OK to capture possibel bugs
        info = info();
        String[] slashes = info.getPostinstMessage().split("\\\\");
        assertEquals(3, slashes.length);
        String[] lines = info.getPostinstMessage().split("\n");
        assertEquals(4, lines.length);
    }

    @Test
    public void testFastr() throws Exception {
        info = info();
        assertEquals(1, info.getDependencies().size());
        assertEquals("org.graalvm.llvm-toolchain", info.getDependencies().iterator().next());
    }

    @Test
    public void testDistributionTypeMissing() throws Exception {
        info = info();
        assertEquals(DistributionType.OPTIONAL, info.getDistributionType());
    }

    @Test
    public void testDistributionTypeBundled() throws Exception {
        info = info();
        assertEquals(DistributionType.BUNDLED, info.getDistributionType());
    }

    @Test
    public void testDistributionTypeInvalid() throws Exception {
        exception.expect(MetadataException.class);
        info = info();
    }
}
