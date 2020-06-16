/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.remote.RemotePropertiesStorage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests CatalogContents which is essentially a combination of several catalogs + local registry
 * contents.
 * 
 * @author sdedic
 */
public class MergedCatalogContentsTest extends CommandTestBase {

    public MergedCatalogContentsTest() {
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, "1.0.0.0");
    }

    RemotePropertiesStorage remstorage;
    CatalogContents coll;

    private Version initVersion(String s) throws IOException {
        Version v = Version.fromString(s);
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, s);
        Path catalogPath = dataFile("catalog-deps.properties");
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(catalogPath)) {
            props.load(is);
        }
        remstorage = new RemotePropertiesStorage(this,
                        getLocalRegistry(),
                        props, "linux_amd64", v, catalogPath.toUri().toURL());
        coll = new CatalogContents(this, remstorage, getLocalRegistry());
        return v;
    }

    /**
     * Finds 1st level dependencies of components.
     */
    @Test
    public void testFindDependenciesOneLevel() throws Exception {
        initVersion("19.3");
        ComponentInfo ci = coll.findComponent("org.graalvm.r", null);
        assertNotNull(ci);
        Set<ComponentInfo> deps = new HashSet<>();
        Set<String> errors = coll.findDependencies(ci, false, null, deps);
        assertNull(errors);
        assertEquals(1, deps.size());
        assertEquals("org.graalvm.llvm-toolchain", deps.iterator().next().getId());

        ci = coll.findComponent("org.graalvm.ruby", null);
        assertNotNull(ci);
        deps = new HashSet<>();
        errors = coll.findDependencies(ci, false, null, deps);
        assertNull(errors);
        assertEquals(1, deps.size());
        assertEquals("org.graalvm.native-image", deps.iterator().next().getId());
    }

    /**
     * Finds 1st level dependencies of components, but just those installed.
     */
    @Test
    public void testFindDependenciesOneLevelInstalled() throws Exception {
        initVersion("19.3");
        ComponentInfo ci = coll.findComponent("org.graalvm.r", null);
        ComponentInfo ll = coll.findComponent("org.graalvm.llvm-toolchain", null);
        assertNotNull(ci);
        assertNotNull(ll);

        // fake the component is local
        ll.setInfoPath("");
        storage.installed.add(ll);

        Set<ComponentInfo> deps = new HashSet<>();
        Set<String> errors = coll.findDependencies(ci, false, Boolean.TRUE, deps);
        assertNull(errors);
        assertEquals(1, deps.size());
        assertEquals("org.graalvm.llvm-toolchain", deps.iterator().next().getId());

        // native-image is not local, will be reported as broken:
        ci = coll.findComponent("org.graalvm.ruby", null);
        assertNotNull(ci);
        deps = new HashSet<>();
        errors = coll.findDependencies(ci, false, Boolean.TRUE, deps);
        assertNotNull(errors);
        assertTrue(deps.isEmpty());
        assertEquals(1, errors.size());
        assertEquals("org.graalvm.native-image", errors.iterator().next());
    }

    /**
     * Finds components recursively.
     */
    @Test
    public void testFindDependenciesRecursive() throws Exception {
        initVersion("19.3");
        ComponentInfo ci = coll.findComponent("org.graalvm.r", null);
        assertNotNull(ci);
        Set<ComponentInfo> deps = new HashSet<>();
        Set<String> errors = coll.findDependencies(ci, true, null, deps);
        assertNull(errors);
        assertEquals(1, deps.size());
        assertEquals("org.graalvm.llvm-toolchain", deps.iterator().next().getId());

        ci = coll.findComponent("org.graalvm.ruby", null);
        assertNotNull(ci);
        deps = new HashSet<>();
        errors = coll.findDependencies(ci, true, null, deps);
        assertNull(errors);
        assertEquals(2, deps.size());

        ComponentInfo ni = coll.findComponent("org.graalvm.native-image", null);
        ComponentInfo ll = coll.findComponent("org.graalvm.llvm-toolchain", null);
        assertTrue(deps.contains(ni));
        assertTrue(deps.contains(ll));
    }

    /**
     * Checks that installed components from the closure are reported and those not installed are
     * reported as missing in the return val.
     */
    @Test
    public void testFindDependenciesRecursiveInstalled() throws Exception {
        initVersion("19.3");
        ComponentInfo ci = coll.findComponent("org.graalvm.ruby", null);
        ComponentInfo ni = coll.findComponent("org.graalvm.native-image", null);
        assertNotNull(ci);
        assertNotNull(ni);
        ni.setInfoPath("");
        storage.installed.add(ni);

        Set<ComponentInfo> deps = new HashSet<>();
        Set<String> errors = coll.findDependencies(ci, true, Boolean.TRUE, deps);
        assertNotNull(errors);
        assertEquals(1, deps.size());
        assertEquals(1, errors.size());
        assertEquals("org.graalvm.native-image", deps.iterator().next().getId());
        assertEquals("org.graalvm.llvm-toolchain", errors.iterator().next());
    }

    /**
     * Checks that only not installed dependencies are reported. One is installed and will not be
     * returned (but is present). The other is not yet installed, will be reported.
     */
    @Test
    public void testFindDependenciesOnlyMissing() throws Exception {
        initVersion("19.3");
        ComponentInfo ci = coll.findComponent("org.graalvm.ruby", null);
        ComponentInfo ni = coll.findComponent("org.graalvm.native-image", null);
        assertNotNull(ci);
        assertNotNull(ni);
        ni.setInfoPath("");
        storage.installed.add(ni);

        Set<ComponentInfo> deps = new HashSet<>();
        Set<String> errors = coll.findDependencies(ci, true, Boolean.FALSE, deps);
        assertNull(errors);
        assertEquals(1, deps.size());
        assertEquals("org.graalvm.llvm-toolchain", deps.iterator().next().getId());
    }
}
