/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.TestBase;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.commands.MockStorage;
import org.graalvm.component.installer.jar.JarMetaLoader;
import org.graalvm.component.installer.persist.ComponentPackageLoader;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;

public class ComponentRegistryTest extends TestBase {
    private BufferedReader releaseFile;
    private MockStorage mockStorage = new MockStorage();

    private ComponentInfo rubyInfo;
    private ComponentInfo fakeInfo;
    private ComponentInfo tmp1;
    private ComponentInfo tmp2;

    @Rule public ExpectedException exception = ExpectedException.none();
    @Rule public TestName name = new TestName();

    private ComponentRegistry registry;

    public ComponentRegistryTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() throws Exception {
        registry = new ComponentRegistry(this, mockStorage);

        try (JarFile jf = new JarFile(dataFile("truffleruby2.jar").toFile())) {
            ComponentPackageLoader ldr = new JarMetaLoader(jf, this);

            rubyInfo = ldr.createComponentInfo();
            ldr.loadPaths();
            ldr.loadSymlinks();
        }

        fakeInfo = new ComponentInfo("org.graalvm.fake", "Fake component", "0.32");
        fakeInfo.addPaths(Arrays.asList(
                        "jre/bin/ruby",
                        "jre/languages/fake/nothing"));
        mockStorage.installed.add(fakeInfo);
    }

    private void registerAdditionalComponents() {
        ComponentInfo tmp = new ComponentInfo("org.graalvm.foobar", "Test component 1", "0.32");
        mockStorage.installed.add(tmp);

        tmp = new ComponentInfo("org.graalvm.clash", "Test component 2", "0.32");
        mockStorage.installed.add(tmp);
        tmp1 = tmp;
        tmp = new ComponentInfo("org.github.clash", "Test component 3", "0.32");
        mockStorage.installed.add(tmp);
        tmp2 = tmp;

        tmp = new ComponentInfo("org.graalvm.Uppercase", "Test component 4", "0.32");
        mockStorage.installed.add(tmp);
    }

    @After
    public void tearDown() throws Exception {
        if (releaseFile != null) {
            releaseFile.close();
        }
    }

    /**
     * Test of findComponent method, of class ComponentRegistry.
     */
    @Test
    public void testFindComponent() throws Exception {
        assertSame(fakeInfo, registry.findComponent("org.graalvm.fake"));
        assertNull(registry.findComponent("org.graalvm.ruby"));

        registry.addComponent(rubyInfo);
        assertNotNull(registry.findComponent("org.graalvm.ruby"));

        registry.removeComponent(rubyInfo);
        assertNull(registry.findComponent("org.graalvm.ruby"));
    }

    @Test
    public void testFindAbbreviatedComponent() throws Exception {
        registerAdditionalComponents();

        assertSame(fakeInfo, registry.findComponent("fake"));
        assertNull(registry.findComponent("org.graalvm.ruby"));
        assertNull(registry.findComponent("ruby"));

        assertNotNull(registry.findComponent("foobar"));

        registry.addComponent(rubyInfo);
        assertNotNull(registry.findComponent("ruby"));

        registry.removeComponent(rubyInfo);
        assertNull(registry.findComponent("ruby"));
    }

    @Test
    public void testFindAmbiguousComponent() throws Exception {
        registerAdditionalComponents();
        exception.expect(FailedOperationException.class);
        exception.expectMessage("COMPONENT_AmbiguousIdFound");
        registry.findComponent("clash");
    }

    @Test
    public void testFindAmbiguousComponentAfterRemove() throws Exception {
        registerAdditionalComponents();
        try {
            registry.findComponent("clash");
            Assert.fail("Expected failure clash");
        } catch (FailedOperationException ex) {
            // expected
        }
        registry.removeComponent(tmp2);
        assertSame(tmp1, registry.findComponent("clash"));

    }

    @Test
    public void testFindUnknownComponent() throws Exception {
        assertSame(fakeInfo, registry.findComponent("org.graalvm.fake"));
        assertNull(registry.findComponent("org.graalvm.ruby"));
    }

    public void testLoadUnknownComponent() throws Exception {

    }

    /**
     * Test of getComponentIDs method, of class ComponentRegistry.
     */
    @Test
    public void testGetComponentIDs() throws IOException {
        Collection<String> ids = registry.getComponentIDs();
        assertEquals(1, ids.size());
        assertEquals("org.graalvm.fake", ids.iterator().next());

        registry.addComponent(rubyInfo);

        assertEquals(
                        new HashSet<>(Arrays.asList(
                                        "org.graalvm.fake",
                                        "org.graalvm.ruby")),
                        new HashSet<>(registry.getComponentIDs()));

        registry.removeComponent(rubyInfo);
        assertEquals(
                        new HashSet<>(Arrays.asList(
                                        "org.graalvm.fake")),
                        new HashSet<>(registry.getComponentIDs()));
    }

    /**
     * Test of removeComponent method, of class ComponentRegistry.
     */
    @Test
    public void testRemoveComponent() throws Exception {
        mockStorage.installed.add(rubyInfo);
        mockStorage.replacedFiles.put("jre/bin/ruby",
                        Arrays.asList(
                                        "org.graalvm.fake",
                                        "org.graalvm.ruby"));

        registry.removeComponent(rubyInfo);
        Assert.assertNotEquals(mockStorage.updatedReplacedFiles, mockStorage.replacedFiles);
        assertNull(mockStorage.updatedReplacedFiles.get("jre/bin/ruby"));
    }

    /**
     * Test of addComponent method, of class ComponentRegistry.
     */
    @Test
    public void testAddComponent() throws Exception {
        registry.addComponent(rubyInfo);

        // check replaced files:
        assertNotNull(mockStorage.updatedReplacedFiles.get("jre/bin/ruby"));
        assertEquals(new HashSet<>(Arrays.asList(
                        "org.graalvm.fake",
                        "org.graalvm.ruby")),
                        new HashSet<>(mockStorage.updatedReplacedFiles.get("jre/bin/ruby")));
    }

    /**
     * Test of getPreservedFiles method, of class ComponentRegistry.
     */
    @Test
    public void testGetPreservedFiles() {
        mockStorage.installed.add(rubyInfo);

        List<String> ll = registry.getPreservedFiles(rubyInfo);
        assertEquals(Arrays.asList("jre/bin/ruby", CommonConstants.PATH_COMPONENT_STORAGE), ll);
    }

    /**
     * Test of getOwners method, of class ComponentRegistry.
     */
    @Test
    public void testGetOwners() {
        rubyInfo.addPaths(Collections.singletonList("bin/ruby"));
        mockStorage.installed.add(rubyInfo);
        List<String> l = registry.getOwners("jre/bin/ruby");
        assertEquals(new HashSet<>(Arrays.asList(
                        "org.graalvm.fake",
                        "org.graalvm.ruby")), new HashSet<>(l));
        l = registry.getOwners("bin/ruby");
        assertEquals(Arrays.asList("org.graalvm.ruby"), l);
    }

    /**
     * Test of isReplacedFilesChanged method, of class ComponentRegistry.
     */
    @Test
    public void testIsReplacedFilesChanged() throws IOException {
        registry.addComponent(rubyInfo);
        assertTrue(registry.isReplacedFilesChanged());

        ComponentInfo testInfo = new ComponentInfo("org.graalvm.test", "Test component", "1.0");
        testInfo.addPaths(Arrays.asList(
                        "jre/bin/ruby2",
                        "jre/languages/fake/nothing2"));

        // add disjunct component
        registry.addComponent(testInfo);
        assertFalse(registry.isReplacedFilesChanged());
    }

    @Test
    public void testFindUppercaseIDComponent() throws Exception {
        registerAdditionalComponents();
        ComponentInfo ci = registry.findComponent("org.graalvm.Uppercase");
        assertNotNull(ci);
    }

    @Test
    public void testFindUppercaseIDComponentWithLowercaseExor() throws Exception {
        registerAdditionalComponents();
        ComponentInfo ci = registry.findComponent("org.graalvm.uppercase");
        assertNotNull(ci);
    }

    ComponentInfo ruby;
    ComponentInfo fastr;
    ComponentInfo llvm;
    ComponentInfo image;

    private void setupComponentsWithDependencies() {
        llvm = new ComponentInfo("org.graalvm.llvm-toolchain", "LLVM", Version.fromString("19.3"));
        image = new ComponentInfo("org.graalvm.native-image", "Native Image", Version.fromString("19.3"));
        image.setDependencies(Collections.singleton(llvm.getId()));

        fastr = new ComponentInfo("org.graalvm.r", "R", Version.fromString("19.3"));
        fastr.setDependencies(Collections.singleton(llvm.getId()));
        ruby = new ComponentInfo("org.graalvm.ruby", "Ruby", Version.fromString("19.3"));
        ruby.setDependencies(new HashSet<>(Arrays.asList(image.getId())));

        mockStorage.installed.add(llvm);
        mockStorage.installed.add(image);
        mockStorage.installed.add(fastr);
        mockStorage.installed.add(ruby);
    }

    @Test
    public void testDependentComponents() throws Exception {
        setupComponentsWithDependencies();

        Set<ComponentInfo> comps = registry.findDependentComponents(image, false);
        assertEquals(1, comps.size());
        assertSame(ruby, comps.iterator().next());

        comps = registry.findDependentComponents(llvm, false);
        assertEquals(2, comps.size());
        assertTrue(comps.contains(fastr));
        assertTrue(comps.contains(image));
    }

    @Test
    public void testDependentComponentsRecursive() throws Exception {
        setupComponentsWithDependencies();

        Set<ComponentInfo> comps = registry.findDependentComponents(image, true);
        assertEquals(1, comps.size());
        assertSame(ruby, comps.iterator().next());

        comps = registry.findDependentComponents(llvm, true);
        assertEquals(3, comps.size());
        assertTrue(comps.contains(fastr));
        assertTrue(comps.contains(ruby));
        assertTrue(comps.contains(image));
    }
}
