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
package org.graalvm.component.installer.persist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.TestBase;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.ComponentInfo;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertNotNull;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;

public class DirectoryStorageTest extends TestBase {
    @Rule public TestName name = new TestName();
    @Rule public TemporaryFolder workDir = new TemporaryFolder();
    @Rule public ExpectedException exception = ExpectedException.none();

    private DirectoryStorage storage;
    private Path registryPath;
    private Path graalVMPath;

    public DirectoryStorageTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() throws Exception {
        graalVMPath = workDir.newFolder("graal").toPath();
        Files.createDirectory(graalVMPath.resolve("bin"));
        registryPath = workDir.newFolder("registry").toPath();

        storage = new DirectoryStorage(this, registryPath, graalVMPath);
        // the default assumed by most test data.
        storage.setJavaVersion("8");
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of loadGraalVersionInfo method, of class RegistryStorage.
     */
    @Test
    public void testLoadGraalVersionSimple() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("release_simple.properties")) {
            Files.copy(is, graalVMPath.resolve(SystemUtils.fileName("release")));
        }
        Map<String, String> result = storage.loadGraalVersionInfo();
        assertEquals(7, result.size());
        assertEquals(CommonConstants.EDITION_CE, result.get(CommonConstants.CAP_EDITION));
    }

    @Test
    public void testLoadGraalVersionSimpleEE() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("release_ee_simple.properties")) {
            Files.copy(is, graalVMPath.resolve(SystemUtils.fileName("release")));
        }
        Map<String, String> result = storage.loadGraalVersionInfo();
        assertEquals(7, result.size());
        assertEquals("ee", result.get(CommonConstants.CAP_EDITION));
    }

    @Test
    public void testFailToLoadReleaseFile() throws Exception {
        exception.expect(FailedOperationException.class);
        Map<String, String> result = storage.loadGraalVersionInfo();
        assertEquals(7, result.size());
    }

    @Test
    public void testLoadReleaseWithInvalidSourceVersions() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("release_noVersion.properties")) {
            Files.copy(is, graalVMPath.resolve(SystemUtils.fileName("release")));
        }
        exception.expect(FailedOperationException.class);
        exception.expectMessage("STORAGE_InvalidReleaseFile");

        storage.loadGraalVersionInfo();
    }

    @Test
    public void testLoadGraalVersionCorrupted() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("release_corrupted.properties")) {
            Files.copy(is, graalVMPath.resolve(SystemUtils.fileName("release")));
        }
        exception.expect(FailedOperationException.class);
        exception.expectMessage("STORAGE_InvalidReleaseFile");
        storage.loadGraalVersionInfo();
    }

    @Test
    public void testLoadMetadata() throws Exception {
        Path p = dataFile("list1/fastr.component");
        ComponentInfo info;

        try (InputStream is = Files.newInputStream(p)) {
            info = storage.loadMetadataFrom(is);
        }
        assertEquals("org.graalvm.fastr", info.getId());
        assertEquals("1.0", info.getVersionString());
        assertEquals("0.32", info.getRequiredGraalValues().get("graalvm_version"));
    }

    @Test
    public void testLoadProvidedCapabilities() throws Exception {
        Path p = dataFile("data/core1.component");
        ComponentInfo info;

        try (InputStream is = Files.newInputStream(p)) {
            info = storage.loadMetadataFrom(is);
        }
        assertEquals("org.graalvm", info.getId());
        assertEquals(Version.fromString("1.0.1.0"), info.getProvidedValue("version", Version.class));
        assertEquals("ee", info.getProvidedValue("edition", String.class));
    }

    /**
     * Test of listComponentIDs method, of class RegistryStorage.
     */
    @Test
    public void testListComponentsSimple() throws Exception {
        copyDir("list1", registryPath);
        List<String> components = new ArrayList<>(storage.listComponentIDs());
        Collections.sort(components);
        components.remove(BundleConstants.GRAAL_COMPONENT_ID);
        assertEquals(Arrays.asList("fastr", "fastr-2", "ruby", "sulong"), components);
    }

    @Test
    public void testListComponentsEmpty() throws Exception {
        copyDir("emptylist", registryPath);
        List<String> components = new ArrayList<>(storage.listComponentIDs());
        components.remove(BundleConstants.GRAAL_COMPONENT_ID);
        assertEquals(Collections.emptyList(), components);
    }

    private ComponentInfo loadLastComponent(String id) throws IOException {
        Set<ComponentInfo> infos = storage.loadComponentMetadata(id);
        if (infos == null || infos.isEmpty()) {
            return null;
        }
        List<ComponentInfo> sorted = new ArrayList<>(infos);
        Collections.sort(sorted, ComponentInfo.versionComparator());
        return sorted.get(sorted.size() - 1);
    }

    /**
     * Test of loadComponentMetadata method, of class RegistryStorage.
     */
    @Test
    public void testLoadComponentMetadata() throws Exception {
        copyDir("list1", registryPath);
        ComponentInfo info = loadLastComponent("fastr");
        assertEquals("org.graalvm.fastr", info.getId());
        assertEquals("1.0", info.getVersionString());
        assertEquals("0.32", info.getRequiredGraalValues().get("graalvm_version"));
    }

    /**
     * Test of loadComponentMetadata method, of class RegistryStorage.
     */
    @Test
    public void testLoadComponentMetadata2() throws Exception {
        copyDir("list1", registryPath);
        ComponentInfo info = loadLastComponent("fastr-2");
        assertEquals("org.graalvm.fastr", info.getId());

        assertTrue(info.isPolyglotRebuild());
        assertTrue(info.getWorkingDirectories().contains("jre/languages/test/scrap"));
        assertTrue(info.getWorkingDirectories().contains("jre/lib/test/scrapdir"));
    }

    /**
     * Should strip whitespaces around.
     * 
     * @throws Exception
     */
    @Test
    public void loadComponentFiles() throws Exception {
        copyDir("list1", registryPath);
        ComponentInfo info = loadLastComponent("fastr");
        storage.loadComponentFiles(info);
        List<String> files = info.getPaths();
        assertEquals(Arrays.asList(
                        "bin/", "bin/R", "bin/Rscript"), files.subList(0, 3));
    }

    /**
     * Should strip whitespaces around.
     * 
     * @throws Exception
     */
    @Test
    public void loadComponentFilesMissing() throws Exception {
        copyDir("list1", registryPath);
        Files.delete(registryPath.resolve(SystemUtils.fileName("org.graalvm.fastr.filelist")));

        ComponentInfo info = loadLastComponent("fastr");
        storage.loadComponentFiles(info);
        List<String> files = info.getPaths();
        assertTrue(files.isEmpty());
    }

    /**
     * Test of loadComponentMetadata method, of class RegistryStorage.
     */
    @Test
    public void testLoadMissingComponentMetadata() throws Exception {
        copyDir("list1", registryPath);
        assertNull(loadLastComponent("rrr"));
    }

    @Test
    public void testLoadReplacedFiles() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("replaced-files.properties")) {
            Files.copy(is, registryPath.resolve(SystemUtils.fileName("replaced-files.properties")));
        }
        Map<String, Collection<String>> replaced = storage.readReplacedFiles();
        assertEquals(new HashSet<>(Arrays.asList("fastr", "ruby")), new HashSet<>(replaced.get("shared/lib/jline.jar")));
        assertEquals(new HashSet<>(Arrays.asList("ruby", "sulong")), new HashSet<>(replaced.get("share/other/whatever.jar")));
    }

    @Test
    public void testLoadReplacedFilesMissing() throws Exception {
        Map<String, Collection<String>> replaced = storage.readReplacedFiles();
        assertTrue(replaced.isEmpty());
    }

    /**
     * Test of updateReplacedFiles method, of class RegistryStorage.
     */
    @Test
    public void testUpdateReplacedFiles() throws Exception {
        Map<String, Collection<String>> files = new HashMap<>();
        files.put("whatever/lib.jar", Arrays.asList("fastr", "sulong"));
        storage.updateReplacedFiles(files);
        Path regPath = registryPath.resolve(SystemUtils.fileName("replaced-files.properties"));
        Path goldenPath = dataFile("golden-replaced-files.properties");
        List<String> lines1 = Files.readAllLines(goldenPath);
        List<String> lines2 = Files.readAllLines(regPath).stream().filter((s) -> !s.startsWith("#")).collect(Collectors.toList());
        assertEquals(lines1, lines2);
    }

    /**
     * Test of updateReplacedFiles method, of class RegistryStorage.
     */
    @Test
    public void testUpdateReplacedFilesNone() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("replaced-files.properties")) {
            Files.copy(is, registryPath.resolve(SystemUtils.fileName("replaced-files.properties")));
        }
        Map<String, Collection<String>> files = new HashMap<>();
        storage.updateReplacedFiles(files);
        Path regPath = registryPath.resolve(SystemUtils.fileName("replaced-files.properties"));
        assertFalse(Files.exists(regPath));
    }

    /**
     * Test of updateReplacedFiles method, of class RegistryStorage.
     */
    @Test
    public void testUpdateReplacedFilesEmpty() throws Exception {
        Map<String, Collection<String>> files = new HashMap<>();
        // make some existing file
        Path goldenPath = dataFile("golden-replaced-files.properties");
        Path regPath = registryPath.resolve(SystemUtils.fileName("replaced-files.properties"));
        Files.copy(goldenPath, regPath, StandardCopyOption.REPLACE_EXISTING);
        storage.updateReplacedFiles(files);

        // should be deleted
        assertFalse(Files.exists(regPath));

        storage.updateReplacedFiles(files);
        // should not be created
        assertFalse(Files.exists(regPath));
    }

    /**
     * Test of deleteComponent method, of class RegistryStorage.
     */
    @Test
    public void testDeleteComponent() throws Exception {
        copyDir("list2", registryPath);
        storage.deleteComponent("fastr");

        Path fastrComp = registryPath.resolve(SystemUtils.fileName("fastr.component"));
        Path fastrList = registryPath.resolve(SystemUtils.fileName("fastr.filelist"));

        assertFalse(Files.exists(fastrComp));
        assertFalse(Files.exists(fastrList));

        storage.deleteComponent("sulong");
        Path sulongComp = registryPath.resolve(SystemUtils.fileName("sulong.component"));
        assertFalse(Files.exists(sulongComp));

        storage.deleteComponent("leftover");
        Path leftoverList = registryPath.resolve(SystemUtils.fileName("leftover.filelist"));
        assertFalse(Files.exists(leftoverList));
    }

    /**
     * Test of deleteComponent method, of class RegistryStorage.
     */
    @Test
    public void testDeleteComponentFailure() throws Exception {
        if (isWindows()) {
            return;
        }

        copyDir("list2", registryPath);
        Files.setPosixFilePermissions(registryPath, PosixFilePermissions.fromString("r--r--r--"));

        exception.expect(IOException.class);
        try {
            storage.deleteComponent("fastr");
        } finally {
            try {
                // revert permissions, so JUnit can erase temp directory
                Files.setPosixFilePermissions(registryPath, PosixFilePermissions.fromString("rwxrwxrwx"));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Test of metaToProperties method, of class RegistryStorage.
     */
    @Test
    public void testMetaToProperties() {
        ComponentInfo info = new ComponentInfo("x", "y", "2.0");
        info.addRequiredValue("a", "b");

        Properties props = DirectoryStorage.metaToProperties(info);
        assertEquals("x", props.getProperty(BundleConstants.BUNDLE_ID));
        assertEquals("y", props.getProperty(BundleConstants.BUNDLE_NAME));
        assertEquals("2.0", props.getProperty(BundleConstants.BUNDLE_VERSION));
        assertEquals("b", props.getProperty(BundleConstants.BUNDLE_REQUIRED + "-a"));
    }

    @Test
    public void testSaveComponent() throws Exception {
        ComponentInfo info = new ComponentInfo("x", "y", "2.0");
        info.addRequiredValue("a", "b");
        Path p = registryPath.resolve(SystemUtils.fileName("x.component"));
        assertFalse(Files.exists(p));
        storage.saveComponent(info);
        assertTrue(Files.exists(p));
        List<String> lines = Files.readAllLines(p).stream()
                        .filter((l) -> !l.startsWith("#"))
                        .collect(Collectors.toList());
        List<String> golden = Files.readAllLines(dataFile("golden-save-component.properties")).stream()
                        .filter((l) -> !l.startsWith("#"))
                        .collect(Collectors.toList());

        assertEquals(golden, lines);

    }

    @Test
    public void testSaveComponentWithCapabilities() throws Exception {
        ComponentInfo info = new ComponentInfo("x", "y", "2.0");
        info.provideValue("a", "foo");
        info.provideValue("v", Version.fromString("1.1.1"));
        Path p = registryPath.resolve(SystemUtils.fileName("x.component"));
        assertFalse(Files.exists(p));
        storage.saveComponent(info);
        assertTrue(Files.exists(p));
        List<String> lines = Files.readAllLines(p).stream()
                        .filter((l) -> !l.startsWith("#"))
                        .collect(Collectors.toList());
        List<String> golden = Files.readAllLines(dataFile("golden-save-component2.properties")).stream()
                        .filter((l) -> !l.startsWith("#"))
                        .collect(Collectors.toList());

        assertEquals(golden, lines);

    }

    @Test
    public void saveComponentOptionalTags() throws Exception {
        ComponentInfo info = new ComponentInfo("x", "y", "2.0");
        info.setPolyglotRebuild(true);
        info.addWorkingDirectories(Arrays.asList(
                        "jre/languages/test/scrap",
                        "jre/lib/test/scrapdir"));

        Path p = registryPath.resolve(SystemUtils.fileName("x.component"));
        assertFalse(Files.exists(p));
        storage.saveComponent(info);
        assertTrue(Files.exists(p));

        List<String> lines = Files.readAllLines(p).stream()
                        .filter((l) -> !l.startsWith("#"))
                        .collect(Collectors.toList());
        List<String> golden = Files.readAllLines(dataFile("golden-save-optional.properties")).stream()
                        .filter((l) -> !l.startsWith("#"))
                        .collect(Collectors.toList());

        assertEquals(golden, lines);

    }

    @Test
    public void saveComponentFiles() throws Exception {
        ComponentInfo info = new ComponentInfo("x", "y", "2.0");
        info.addPaths(Arrays.asList("SecondPath/file", "FirstPath/directory/"));

        Path p = registryPath.resolve(SystemUtils.fileName("x.filelist"));
        assertFalse(Files.exists(p));
        storage.saveComponentFileList(info);
        assertTrue(Files.exists(p));

        List<String> lines = Files.readAllLines(p).stream()
                        .filter((l) -> !l.startsWith("#"))
                        .collect(Collectors.toList());
        List<String> golden = Files.readAllLines(dataFile("golden-save-filelist.properties")).stream()
                        .filter((l) -> !l.startsWith("#"))
                        .collect(Collectors.toList());

        assertEquals(golden, lines);
    }

    @Test
    public void testAcceptLicense() throws Exception {
        copyDir("list1", registryPath);
        ComponentInfo info = loadLastComponent("fastr");
        enableLicensesForTesting();
        storage.recordLicenseAccepted(info, "cafebabe", "This is a dummy license", null);
        Path p = registryPath.resolve(SystemUtils.fromCommonString("licenses/cafebabe.accepted/org.graalvm.fastr"));
        Path p2 = registryPath.resolve(SystemUtils.fromCommonString("licenses/cafebabe"));
        assertTrue(Files.isReadable(p));
        assertEquals(Arrays.asList("This is a dummy license"), Files.readAllLines(p2));
    }

    @Test
    public void testLicenseAccepted1() throws Exception {
        copyDir("list1", registryPath);
        ComponentInfo info = loadLastComponent("fastr");
        ComponentInfo info2 = loadLastComponent("ruby");

        Path p = registryPath.resolve(SystemUtils.fromCommonString("licenses/cafebabe.accepted/org.graalvm.fastr"));
        Files.createDirectories(p.getParent());
        Files.write(p, Arrays.asList("ahoj"));

        enableLicensesForTesting();
        assertNotNull(storage.licenseAccepted(info, "cafebabe"));
        assertNull(storage.licenseAccepted(info2, "cafebabe"));
    }

    /**
     * Checks that license management is disabled, that is no license is reported as accepted even
     * if the data (by some miracle) exist.
     */
    @Test
    public void testLicensesDecativated() throws Exception {
        copyDir("list1", registryPath);
        ComponentInfo info = loadLastComponent("fastr");

        Path p = registryPath.resolve(SystemUtils.fromCommonString("licenses/cafebabe.accepted/org.graalvm.fastr"));
        Files.createDirectories(p.getParent());
        Files.write(p, Arrays.asList("ahoj"));
        assertNull(storage.licenseAccepted(info, "cafebabe"));
    }

    /**
     * Checks that no license is recorded, as the feature must be disabled.
     */
    @Test
    public void testLicensesNotRecorded() throws Exception {
        copyDir("list1", registryPath);
        ComponentInfo info = loadLastComponent("fastr");

        Path p = registryPath.resolve(SystemUtils.fromCommonString("licenses/cafebabe.accepted/org.graalvm.fastr"));
        Files.createDirectories(p.getParent());
        Files.write(p, Arrays.asList("ahoj"));

        assertNull(storage.licenseAccepted(info, "cafebabe"));
    }

    /**
     * Checks that graalvm.core is present in the list.
     */
    @Test
    public void testCoreComponentPresent() throws Exception {
        copyDir("list1", registryPath);
        assertTrue("Must contain graalvm core", storage.listComponentIDs().contains(BundleConstants.GRAAL_COMPONENT_ID));
    }

    @Test
    public void testKnowsNativeComponent() throws Exception {
        copyDir("list3", registryPath);
        Collection<String> ids = storage.listComponentIDs();
        assertTrue(ids.contains("fastr"));
        assertTrue(ids.contains("ruby"));
        Set<ComponentInfo> cis = storage.loadComponentMetadata("ruby");
        assertEquals(1, cis.size());
        ComponentInfo ci = cis.iterator().next();
        assertTrue(ci.isNativeComponent());
    }

    @Test
    public void testRefuseInstallationForROPosix() throws Exception {
        PosixFileAttributeView posix = registryPath.getFileSystem().provider().getFileAttributeView(registryPath, PosixFileAttributeView.class);
        Assume.assumeTrue("Not a POSIX system", posix != null);
        try {
            // simulate an unreadable directory:
            posix.setPermissions(PosixFilePermissions.fromString("r-xr-xr-x"));

            exception.expect(FailedOperationException.class);
            exception.expectMessage("ERROR_MustBecomeUser");
            storage.saveComponent(null);
        } finally {
            posix.setPermissions(PosixFilePermissions.fromString("rwxrwxr-x"));
        }
    }

    @Test
    public void testGraalVMCoreComponentNative() throws Exception {
        // fake a release file
        Files.copy(dataFile("release_simple.properties"), graalVMPath.resolve("release"));
        Path meta = registryPath.resolve(BundleConstants.GRAAL_COMPONENT_ID + ".meta");
        // create the component storage, tag core component with .meta file
        Files.createFile(meta);

        Set<ComponentInfo> infos = storage.loadComponentMetadata(BundleConstants.GRAAL_COMPONENT_ID);
        assertEquals(1, infos.size());
        ComponentInfo ci = infos.iterator().next();
        assertTrue(ci.isNativeComponent());
    }

    @Test
    public void testGraalVMCoreComponentRegular() throws Exception {
        Files.copy(dataFile("release_simple.properties"), graalVMPath.resolve("release"));

        Set<ComponentInfo> infos = storage.loadComponentMetadata(BundleConstants.GRAAL_COMPONENT_ID);
        assertEquals(1, infos.size());
        ComponentInfo ci = infos.iterator().next();
        assertFalse(ci.isNativeComponent());
    }
}
