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
package org.graalvm.component.installer.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.DependencyException;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.TestBase;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.persist.ComponentPackageLoader;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class InstallerTest extends TestBase {
    @Rule public ExpectedException exception = ExpectedException.none();
    protected JarFile componentJarFile;
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private Path targetPath;

    private MockStorage storage;
    private ComponentRegistry registry;
    private ComponentPackageLoader loader;
    private Installer installer;
    private ComponentInfo componentInfo;

    private void setupComponentInstall(String relativePath) throws IOException {
        File f = dataFile(relativePath).toFile();
        componentJarFile = new JarFile(f);

        loader = new ComponentPackageLoader(componentJarFile, this);
        componentInfo = loader.createComponentInfo();

        loader.loadPaths();
        installer = new Installer(fb(), componentInfo, registry);
        installer.setJarFile(componentJarFile);
        installer.setInstallPath(targetPath);
        installer.setLicenseRelativePath(SystemUtils.fromCommonString(loader.getLicensePath()));
    }

    private Feedback fb() {
        return withBundle(Installer.class);
    }

    public InstallerTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() throws IOException {
        targetPath = folder.newFolder("inst").toPath();
        storage = new MockStorage();
        registry = new ComponentRegistry(this, storage);
    }

    @After
    public void tearDown() throws Exception {
        if (componentJarFile != null) {
            componentJarFile.close();
        }
    }

    @Test
    public void testFailRemoteComponentExisting() throws IOException {
        setupComponentInstall("truffleruby2.jar");
        ComponentInfo fakeInfo = new ComponentInfo("org.graalvm.ruby", "Fake ruby", "1.0");
        storage.installed.add(fakeInfo);
        exception.expect(DependencyException.Conflict.class);
        exception.expectMessage("VERIFY_ComponentExists");
        installer.validateRequirements();
    }

    /**
     * Checks that the component will be uninstalled before installing a new one.
     */
    @Test
    public void testSetReplaceComponents() throws IOException {
        setupComponentInstall("truffleruby2.jar");
        ComponentInfo fakeInfo = new ComponentInfo("org.graalvm.ruby", "Fake ruby", "1.0");
        storage.installed.add(fakeInfo);

        installer.setReplaceComponents(true);
        installer.validateRequirements();
        installer.install();
    }

    @Test
    public void testFailOnExistingComponent() throws IOException {
        setupComponentInstall("truffleruby2.jar");
        ComponentInfo fakeInfo = new ComponentInfo("org.graalvm.ruby", "Fake ruby", "1.0");
        storage.installed.add(fakeInfo);

        exception.expect(DependencyException.Conflict.class);
        exception.expectMessage("VERIFY_ComponentExists");
        installer.validateRequirements();
    }

    /**
     * Test of uninstall method, of class Installer.
     */
    @Test
    public void testUninstall() throws Exception {
        setupComponentInstall("truffleruby2.jar");
        installer.setPermissions(loader.loadPermissions());
        installer.setSymlinks(loader.loadSymlinks());
        // install
        installer.install();

        ComponentInfo savedInfo = installer.getComponentInfo();

        // now uninstall, fileName a new installer.
        Uninstaller uninstaller = new Uninstaller(fb(), savedInfo, registry);
        uninstaller.setInstallPath(targetPath);
        uninstaller.uninstallContent();

        assertFalse("All files should be removed after uninstall",
                        Files.list(targetPath).findAny().isPresent());
    }

    /**
     * Test of uninstall method, of class Installer.
     */
    @Test
    public void testUninstallFailsOnExtraFile() throws Exception {
        setupComponentInstall("truffleruby2.jar");
        installer.setPermissions(loader.loadPermissions());
        installer.setSymlinks(loader.loadSymlinks());
        // install
        installer.install();

        ComponentInfo savedInfo = installer.getComponentInfo();

        Path langPath = targetPath.resolve(SystemUtils.fromCommonString("jre/languages/ruby"));
        Path roPath = langPath.resolve(SystemUtils.fromCommonString("doc/user"));
        // and add a new file to that dir:
        Path uf = roPath.resolve(SystemUtils.fileName("userFile.txt"));
        Files.write(uf, Arrays.asList("This file", "Should vanish"));

        exception.expect(DirectoryNotEmptyException.class);
        exception.expectMessage("jre/languages/ruby/doc/user".replace(SystemUtils.DELIMITER, File.separator));
        // now uninstall, fileName a new installer.
        Uninstaller uninstaller = new Uninstaller(fb(), savedInfo, registry);
        uninstaller.setInstallPath(targetPath);
        uninstaller.uninstallContent();

        fail("Shouldn't be reached");
    }

    @Test
    public void testRecursiveDelete() throws Exception {
        // install just to get the component structure
        setupComponentInstall("truffleruby2.jar");
        installer.setPermissions(loader.loadPermissions());
        installer.setSymlinks(loader.loadSymlinks());
        // install
        installer.install();
        ComponentInfo savedInfo = installer.getComponentInfo();
        Uninstaller uninstaller = new Uninstaller(fb(), savedInfo, registry);
        Path langPath = targetPath.resolve(SystemUtils.fromCommonString("jre/languages/ruby"));
        uninstaller.deleteContentsRecursively(langPath);

        // the root dir still exists
        assertTrue(Files.exists(langPath));
        // but is empty:

        assertFalse("All files should be removed by recursive delete",
                        Files.list(langPath).findAny().isPresent());
    }

    @Test
    public void testRecursiveDeleteWithReadonlyFiles() throws Exception {
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            return;
        }

        // install just to get the component structure
        setupComponentInstall("truffleruby2.jar");
        installer.setPermissions(loader.loadPermissions());
        installer.setSymlinks(loader.loadSymlinks());
        // install
        installer.install();
        ComponentInfo savedInfo = installer.getComponentInfo();
        Uninstaller uninstaller = new Uninstaller(fb(), savedInfo, registry);
        Path langPath = targetPath.resolve(SystemUtils.fromCommonString("jre/languages/ruby"));

        Path roPath = langPath.resolve(SystemUtils.fromCommonString("doc/legal"));
        Files.setPosixFilePermissions(roPath, PosixFilePermissions.fromString("r-xr-xr-x"));

        uninstaller.deleteContentsRecursively(langPath);
        // the root dir still exists
        assertTrue(Files.exists(langPath));
        // but is empty:

        assertFalse("All files should be removed by recursive delete",
                        Files.list(langPath).findAny().isPresent());
    }

    @Test
    public void testUninstallComponentWithROFiles() throws Exception {
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            return;
        }
        setupComponentInstall("trufflerubyRO.jar");
        installer.setPermissions(loader.loadPermissions());
        installer.setSymlinks(loader.loadSymlinks());
        // install
        installer.install();

        ComponentInfo savedInfo = installer.getComponentInfo();

        // now uninstall, fileName a new installer.
        Uninstaller uninstaller = new Uninstaller(fb(), savedInfo, registry);
        uninstaller.setInstallPath(targetPath);
        uninstaller.uninstallContent();

        assertFalse("All files should be removed after uninstall",
                        Files.list(targetPath).findAny().isPresent());
    }

    @Test
    public void testUninstallComponentWithUserROFiles() throws Exception {
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            return;
        }
        setupComponentInstall("trufflerubyWork.jar");
        installer.setPermissions(loader.loadPermissions());
        installer.setSymlinks(loader.loadSymlinks());
        // install
        installer.install();

        ComponentInfo savedInfo = installer.getComponentInfo();

        Path langPath = targetPath.resolve(SystemUtils.fromCommonString("jre/languages/ruby"));
        Path roPath = langPath.resolve(SystemUtils.fromCommonString("doc/user"));

        // and add a new file to that dir:
        Path uf = roPath.resolve(SystemUtils.fileName("userFile.txt"));
        Files.write(uf, Arrays.asList("This file", "Should vanish"));
        Files.setPosixFilePermissions(uf, PosixFilePermissions.fromString("r--r-----"));
        Files.setPosixFilePermissions(roPath, PosixFilePermissions.fromString("r-xr-xr-x"));

        // now uninstall, fileName a new installer.
        Uninstaller uninstaller = new Uninstaller(fb(), savedInfo, registry);
        uninstaller.setInstallPath(targetPath);
        uninstaller.uninstallContent();

        assertFalse("All files should be removed after uninstall",
                        Files.list(targetPath).findAny().isPresent());
    }

    /**
     * Checks that the uninstall does not fail even though it cannot delete something.
     */
    @Test
    public void testSetIgnoreFailedDeletions() throws IOException {
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            return;
        }
        setupComponentInstall("truffleruby2.jar");
        installer.setPermissions(loader.loadPermissions());
        installer.setSymlinks(loader.loadSymlinks());
        // install
        installer.install();

        ComponentInfo savedInfo = installer.getComponentInfo();

        // make some directory readonly
        Path p = targetPath.resolve(SystemUtils.fromCommonString("jre/languages/ruby/doc/legal"));
        Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("r--r--r--"));

        // now uninstall, fileName a new installer.
        Uninstaller uninstaller = new Uninstaller(fb(), savedInfo, registry);
        uninstaller.setInstallPath(targetPath);
        uninstaller.setIgnoreFailedDeletions(true);
        uninstaller.uninstall();
    }

    /**
     * Checks that the uninstall does not fail even though it cannot delete something.
     */
    @Test
    public void testFailedDeletionAborts() throws IOException {
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            return;
        }
        setupComponentInstall("truffleruby2.jar");
        installer.setPermissions(loader.loadPermissions());
        installer.setSymlinks(loader.loadSymlinks());
        // install
        installer.install();

        ComponentInfo savedInfo = installer.getComponentInfo();

        // make some directory readonly
        Path p = targetPath.resolve(SystemUtils.fromCommonString("jre/languages/ruby/doc/legal"));
        Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("r--r--r--"));

        // now uninstall, fileName a new installer.
        Uninstaller uninstaller = new Uninstaller(fb(), savedInfo, registry);
        uninstaller.setInstallPath(targetPath);

        exception.expect(IOException.class);
        uninstaller.uninstall();
    }

    /**
     * Checks that requirements are properly validated.
     */
    @Test
    public void testValidateRequirementsSuccess() throws Exception {
        setupComponentInstall("truffleruby2.jar");
        installer.validateRequirements();
    }

    @Test
    public void testValidateRequirementsGraalVersion() throws Exception {
        setupComponentInstall("truffleruby2.jar");
        installer.getComponentInfo().addRequiredValue(CommonConstants.CAP_GRAALVM_VERSION, "0.33");

        exception.expect(DependencyException.class);
        exception.expectMessage("VERIFY_Dependency_Failed");
        installer.validateRequirements();
    }

    @Test
    public void testValidateRequirementsGraalVersion2() throws Exception {

        setupComponentInstall("truffleruby2.jar");
        // simulate different version of Graal installation
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "0.30");

        exception.expect(DependencyException.class);
        exception.expectMessage("VERIFY_Dependency_Failed");
        installer.validateRequirements();
    }

    /**
     * Checks that the component is installed despite the requirements.
     */
    @Test
    public void testSetIgnoreRequirements() throws Exception {

        setupComponentInstall("truffleruby2.jar");
        // simulate different version of Graal installation
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "0.30");

        installer.setIgnoreRequirements(true);
        installer.validateRequirements();
    }

    /**
     * Test of install method, of class Installer.
     */
    @Test
    public void testInstall() throws Exception {
        setupComponentInstall("truffleruby2.jar");
        installer.setSymlinks(loader.loadSymlinks());
        installer.setPermissions(loader.loadPermissions());
        installer.install();

        Path jreRuby = targetPath.resolve(SystemUtils.fromCommonString("jre/bin/ruby"));
        Path binRuby = targetPath.resolve(SystemUtils.fromCommonString("bin/ruby"));

        assertTrue(Files.exists(jreRuby));

        // symlink is skipped on Windows OS
        if (!isWindows()) {
            assertTrue(Files.exists(binRuby));
        }

        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            assertTrue(Files.isExecutable(jreRuby));
            assertTrue(Files.isSymbolicLink(binRuby));
        }

        // checks that everything is properly reverted
        installer.revertInstall();

        assertFalse("No files should be created under dry run",
                        Files.list(targetPath).findAny().isPresent());
    }

    /**
     * Test of installOneFile method, of class Installer.
     */
    @Test
    public void testInstallOneRegularFile() throws Exception {
        setupComponentInstall("truffleruby2.jar");
        /*
         * inst.setPermissions(ldr.loadPermissions()); inst.setSymlinks(ldr.loadSymlinks());
         */
        JarEntry entry = componentJarFile.getJarEntry("jre/bin/ruby");
        Path resultPath = installer.installOneFile(installer.translateTargetPath(entry), entry);
        Path relative = targetPath.relativize(resultPath);
        assertEquals(entry.getName(), SystemUtils.toCommonPath(relative));

        Path check = targetPath.resolve(SystemUtils.fromCommonString("jre/bin/ruby"));
        // assume directories are also created
        assertTrue(Files.exists(check));
        assertEquals(entry.getSize(), Files.size(check));

        // check that the installation is reverted
        installer.revertInstall();

        assertFalse(Files.exists(check));
        // two levels of directories should have been created
        assertFalse(Files.exists(check.getParent()));
        assertFalse(Files.exists(check.getParent().getParent()));
    }

    /**
     * Check that if the same file is found and skipped, it will not revert on installation abort.
     */
    @Test
    public void testInstallExistingFileWillNotRevert() throws Exception {
        setupComponentInstall("truffleruby2.jar");
        /*
         * inst.setPermissions(ldr.loadPermissions()); inst.setSymlinks(ldr.loadSymlinks());
         */
        Path existing = targetPath.resolve(SystemUtils.fromCommonString("jre/bin/ruby"));
        Files.createDirectories(existing.getParent());
        Files.copy(dataFile("ruby"), existing);

        JarEntry entry = componentJarFile.getJarEntry("jre/bin/ruby");
        Path resultPath = installer.installOneFile(installer.translateTargetPath(entry), entry);
        Path relative = targetPath.relativize(resultPath);
        assertEquals(entry.getName(), SystemUtils.toCommonPath(relative));

        Path check = targetPath.resolve(SystemUtils.fromCommonString("jre/bin/ruby"));
        // assume directories are also created
        assertTrue(Files.exists(check));
        assertEquals(entry.getSize(), Files.size(check));

        // check that the installation is reverted
        installer.revertInstall();

        // MUST still exist, the installe did not fileName it
        assertTrue(Files.exists(check));
    }

    /**
     * Check that if the same file is found and skipped, it will not revert on installation abort.
     */
    @Test
    public void testInstallOverwrittemFileWillNotRevert() throws Exception {
        setupComponentInstall("truffleruby2.jar");
        installer.setReplaceDiferentFiles(true);

        /*
         * inst.setPermissions(ldr.loadPermissions()); inst.setSymlinks(ldr.loadSymlinks());
         */
        Path existing = targetPath.resolve(SystemUtils.fromCommonString("jre/bin/ruby"));
        Files.createDirectories(existing.getParent());
        Files.copy(dataFile("ruby2"), existing);

        JarEntry entry = componentJarFile.getJarEntry("jre/bin/ruby");
        Path resultPath = installer.installOneFile(installer.translateTargetPath(entry), entry);
        Path relative = targetPath.relativize(resultPath);
        assertEquals(entry.getName(), SystemUtils.toCommonPath(relative));

        Path check = targetPath.resolve(SystemUtils.fromCommonString("jre/bin/ruby"));
        // assume directories are also created
        assertTrue(Files.exists(check));
        assertEquals(entry.getSize(), Files.size(check));

        // check that the installation is reverted
        installer.revertInstall();

        // MUST still exist, the installe did not fileName it
        assertTrue(Files.exists(check));
    }

    @Test
    public void testInstallOneLicenseFile() throws Exception {
        setupComponentInstall("truffleruby2.jar");

        JarEntry entry2 = componentJarFile.getJarEntry("LICENSE");
        Path resultPath = installer.installOneFile(installer.translateTargetPath(entry2), entry2);
        Path relative = targetPath.relativize(resultPath);
        assertNotEquals(entry2.getName(), relative.toString());
        assertTrue(relative.toString().contains(componentInfo.getVersionString()));
        assertEquals(targetPath, resultPath.getParent());

        installer.revertInstall();
        assertFalse(Files.exists(resultPath));
    }

    /**
     * Checks that an empty directory is installed.
     */
    @Test
    public void testInstallOneDirectory() throws Exception {
        setupComponentInstall("truffleruby2.jar");

        JarEntry entry = componentJarFile.getJarEntry("jre/bin/");
        installer.installOneEntry(entry);

        Path check = targetPath.resolve(SystemUtils.fromCommonString("jre/bin"));
        // assume directories are also created
        assertTrue(Files.exists(check));
        assertTrue(Files.isDirectory(check));

        // rollback
        installer.revertInstall();

        assertFalse(Files.exists(check));
    }

    /**
     * Checks that if a directory already exists, it will not be reverted on failed install.
     */
    @Test
    public void testInstallExistingDirectoryWillNotRevert() throws Exception {
        setupComponentInstall("truffleruby2.jar");

        Path existing = targetPath.resolve(SystemUtils.fromCommonString("jre/bin"));
        Files.createDirectories(existing);

        JarEntry entry = componentJarFile.getJarEntry("jre/bin/");
        installer.installOneEntry(entry);

        Path check = targetPath.resolve(SystemUtils.fromCommonString("jre/bin"));
        // assume directories are also created
        assertTrue(Files.exists(check));
        assertTrue(Files.isDirectory(check));

        // rollback
        installer.revertInstall();

        assertTrue(Files.exists(check));
    }

    /**
     * Checks that permissions are correctly changed. Works only on UNIXes.
     */
    @Test
    public void testProcessPermissions() throws Exception {
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            return;
        }

        setupComponentInstall("truffleruby3.jar");
        installer.unpackFiles();
        // check the executable file has no permissions
        Path check = targetPath.resolve(SystemUtils.fromCommonString("jre/bin/ruby"));
        assertFalse(Files.isExecutable(check));
        installer.processPermissions();
        // still nothing, no permissions were set
        assertFalse(Files.isExecutable(check));
        installer.setPermissions(loader.loadPermissions());
        installer.processPermissions();
        assertTrue(Files.isExecutable(check));
        assertTrue(Files.isExecutable(targetPath.resolve(
                        SystemUtils.fromCommonString("jre/languages/ruby/bin/ri"))));
    }

    /**
     * Checks correct creation of symlinks. Works only on UNIXes.
     */
    @Test
    public void testCreateSymlinks() throws Exception {
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            return;
        }
        setupComponentInstall("truffleruby2.jar");
        installer.unpackFiles();

        // check the executable file has no permissions
        Path check = targetPath.resolve(SystemUtils.fromCommonString("bin/ruby"));
        assertFalse(Files.exists(check));

        installer.setSymlinks(loader.loadSymlinks());
        installer.createSymlinks();

        assertTrue(Files.exists(check));
        assertTrue(Files.isSymbolicLink(check));
        Path target = Files.readSymbolicLink(check);

        Path resolved = targetPath.relativize(check.resolveSibling(target).normalize());
        assertEquals("jre/bin/ruby", resolved.toString());

        installer.revertInstall();

        assertFalse(Files.exists(check));
    }

    /**
     * Test of checkFileReplacement method, of class Installer.
     */
    @Test
    public void testCheckFileReplacementSame() throws Exception {
        setupComponentInstall("truffleruby2.jar");

        Path existing = dataFile("ruby");

        JarEntry je = componentJarFile.getJarEntry("jre/bin/ruby");

        // should pass:
        installer.checkFileReplacement(existing, je);
    }

    /**
     * Test of checkFileReplacement method, of class Installer.
     */
    @Test
    public void testCheckFileReplacementDifferent() throws Exception {
        setupComponentInstall("truffleruby2.jar");

        Path existing = dataFile("ruby2");
        JarEntry je = componentJarFile.getJarEntry("jre/bin/ruby");

        // should fail:
        exception.expect(FailedOperationException.class);
        exception.expectMessage("INSTALL_ReplacedFileDiffers");
        installer.checkFileReplacement(existing, je);
    }

    /**
     * Test of checkFileReplacement method, of class Installer.
     */
    @Test
    public void testCheckFileReplacementForced() throws Exception {
        setupComponentInstall("truffleruby2.jar");
        installer.setReplaceDiferentFiles(true);

        Path existing = dataFile("ruby2");
        JarEntry je = componentJarFile.getJarEntry("jre/bin/ruby");

        // should succeed:
        installer.checkFileReplacement(existing, je);
    }

    /**
     * Checks that the install does not do anything if dry run is enabled.
     */
    @Test
    public void testSetDryRun() throws IOException {
        setupComponentInstall("truffleruby2.jar");
        installer.setDryRun(true);
        installer.setPermissions(loader.loadPermissions());
        installer.setSymlinks(loader.loadSymlinks());

        installer.install();

        assertFalse("No files should be created under dry run",
                        Files.list(targetPath).findAny().isPresent());
    }

    @Test
    public void testValidateFiles() throws Exception {
        setupComponentInstall("truffleruby2.jar");
        installer.validateFiles();

        installer.setSymlinks(loader.loadSymlinks());
        installer.setPermissions(loader.loadPermissions());

        installer.validateAll();
    }

    @Test
    public void testValidateOverwriteDirectoryWithFile() throws IOException {
        setupComponentInstall("truffleruby2.jar");
        Path offending = targetPath.resolve(SystemUtils.fromCommonString("jre/bin/ruby"));
        Files.createDirectories(offending);
        ZipEntry entry = componentJarFile.getEntry("jre/bin/ruby");

        exception.expect(IOException.class);
        exception.expectMessage("INSTALL_OverwriteWithFile");
        installer.validateOneEntry(
                        installer.translateTargetPath(entry), entry);
    }

    @Test
    public void testValidateOverwriteFileWithDirectory() throws IOException {
        setupComponentInstall("truffleruby2.jar");
        Path offending = targetPath.resolve(SystemUtils.fromCommonString("jre/languages/ruby"));
        Files.createDirectories(offending.getParent());
        Files.createFile(offending);
        ZipEntry entry = componentJarFile.getEntry("jre/languages/ruby/");

        exception.expect(IOException.class);
        exception.expectMessage("INSTALL_OverwriteWithDirectory");
        installer.validateOneEntry(
                        installer.translateTargetPath(entry), entry);
    }

    @Test
    public void testRevertInstallFailureFile() throws Exception {
        if (isWindows()) {
            return;
        }
        setupComponentInstall("truffleruby2.jar");
        installer.install();

        Path jreRuby = targetPath.resolve(SystemUtils.fromCommonString("jre/bin/ruby"));

        assertTrue(Files.exists(jreRuby));

        Files.setPosixFilePermissions(
                        jreRuby.getParent(), PosixFilePermissions.fromString("r-xr-xr-x"));
        // checks that everything is properly reverted
        try {
            class FD extends FeedbackAdapter {
                List<String> errors = new ArrayList<>();

                @Override
                public void error(String key, Throwable t, Object... params) {
                    errors.add(key);
                }

            }
            FD fd = new FD();
            delegateFeedback(fd);
            installer.revertInstall();
            // must report something
            assertFalse(fd.errors.isEmpty());
            assertTrue(Files.exists(jreRuby));
        } finally {
            Files.setPosixFilePermissions(
                            jreRuby.getParent(), PosixFilePermissions.fromString("rwxrwxrwx"));
        }
    }

    @Test
    public void testRevertInstallFailureDir() throws Exception {
        if (isWindows()) {
            return;
        }
        setupComponentInstall("truffleruby2.jar");
        installer.install();

        Path jreLang = targetPath.resolve(SystemUtils.fromCommonString("jre/languages"));

        assertTrue(Files.exists(jreLang));

        Files.setPosixFilePermissions(
                        jreLang, PosixFilePermissions.fromString("r-xr-xr-x"));
        // checks that everything is properly reverted
        try {
            class FD extends FeedbackAdapter {
                List<String> errors = new ArrayList<>();

                @Override
                public void error(String key, Throwable t, Object... params) {
                    errors.add(key);
                }

            }
            FD fd = new FD();
            delegateFeedback(fd);
            installer.revertInstall();
            // must report something
            assertFalse(fd.errors.isEmpty());
            assertTrue(Files.exists(jreLang));
        } finally {
            Files.setPosixFilePermissions(
                            jreLang, PosixFilePermissions.fromString("rwxrwxrwx"));
        }
    }

    @Test
    public void testUnpackExistingSymlinks() throws Exception {
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            return;
        }
        setupComponentInstall("truffleruby2.jar");
        Path offending = targetPath.resolve(SystemUtils.fromCommonString("bin/ruby"));
        Files.createDirectories(offending.getParent());
        Files.createSymbolicLink(offending, SystemUtils.fromCommonString("../jre/bin/ruby"));

        Path offending2 = targetPath.resolve(SystemUtils.fromCommonString("jre/languages/ruby/bin/ruby"));
        Files.createDirectories(offending2.getParent());
        Files.createSymbolicLink(offending2, SystemUtils.fileName("xxx"));

        installer.setReplaceDiferentFiles(true);
        installer.setSymlinks(loader.loadSymlinks());
        installer.createSymlinks();

        List<String> paths = componentInfo.getPaths();
        assertTrue(paths.contains("bin/ruby"));
        assertTrue(paths.contains("jre/languages/ruby/bin/ruby"));

        assertEquals(SystemUtils.fileName("truffleruby"), Files.readSymbolicLink(offending2));
    }

    @Test
    public void testFailOverwriteFileWithSymlink() throws Exception {
        setupComponentInstall("truffleruby2.jar");
        // prepare offending symlink going elsewhere

        Path offending = targetPath.resolve(SystemUtils.fromCommonString("bin/ruby"));
        Files.createDirectories(offending.getParent());
        Files.createFile(offending);

        exception.expect(IOException.class);
        exception.expectMessage("INSTALL_OverwriteWithLink");
        installer.setSymlinks(loader.loadSymlinks());
        installer.validateSymlinks();
    }

    @Test
    public void testOverwriteFileWithSymlink() throws Exception {
        if (isWindows()) {
            return;
        }

        setupComponentInstall("truffleruby2.jar");
        // prepare offending symlink going elsewhere

        Path offending = targetPath.resolve(SystemUtils.fromCommonString("bin/ruby"));
        Files.createDirectories(offending.getParent());
        Files.createSymbolicLink(offending, targetPath.resolve(SystemUtils.fromCommonString("../jre/bin/ruby")));

        installer.setReplaceDiferentFiles(true);
        installer.setSymlinks(loader.loadSymlinks());
        installer.validateSymlinks();
    }

    @Test
    public void testFailOverwriteOtherSymlink() throws Exception {
        if (isWindows()) {
            return;
        }

        setupComponentInstall("truffleruby2.jar");
        // prepare offending symlink going elsewhere

        Path offending = targetPath.resolve(SystemUtils.fromCommonString("bin/ruby"));
        Files.createDirectories(offending.getParent());
        Files.createSymbolicLink(offending, targetPath.resolve(SystemUtils.fromCommonString("../x")));

        exception.expect(FailedOperationException.class);
        exception.expectMessage("INSTALL_ReplacedFileDiffers");
        installer.setSymlinks(loader.loadSymlinks());
        installer.validateSymlinks();
    }

    @Test
    public void testOverwriteOtherSymlink() throws Exception {
        if (isWindows()) {
            return;
        }
        setupComponentInstall("truffleruby2.jar");
        // prepare offending symlink going elsewhere

        Path offending = targetPath.resolve(SystemUtils.fromCommonString("bin/ruby"));
        Files.createDirectories(offending.getParent());
        Files.createSymbolicLink(offending, targetPath.resolve(SystemUtils.fromCommonString("../x")));

        installer.setSymlinks(loader.loadSymlinks());
        installer.setReplaceDiferentFiles(true);
        installer.validateSymlinks();
    }

    public void testVerifyCatalogMatchingComponent() throws Exception {
        fail("TBD");
    }

    public void testVerifyCatalogInvalidComponent() throws Exception {
        fail("TBD");
    }
}
