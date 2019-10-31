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

import java.io.File;
import org.graalvm.component.installer.CommandTestBase;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.component.installer.remote.CatalogIterable;
import org.graalvm.component.installer.Commands;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.ComponentIterable;
import org.graalvm.component.installer.DependencyException;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.persist.ProxyResource;
import org.graalvm.component.installer.remote.RemoteCatalogDownloader;
import org.graalvm.component.installer.persist.test.Handler;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class InstallTest extends CommandTestBase {
    @Rule public final ProxyResource proxyResource = new ProxyResource();

    InstallCommand inst;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        files.add(dataFile("truffleruby2.jar").toFile());
    }

    @Test
    public void testHelp() throws Exception {
        class F extends FeedbackAdapter {
            List<String> outputKeys = new ArrayList<>();

            @Override
            public void output(String bundleKey, Object... params) {
                outputKeys.add(bundleKey);
            }
        }

        F fe = new F();
        delegateFeedback(fe);
        options.put(Commands.OPTION_HELP, "");

        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));

        inst.execute();
        assertFalse(Files.list(targetPath).findFirst().isPresent());
        assertTrue(fe.outputKeys.get(0).toLowerCase().contains("help"));
    }

    @Test
    public void testDryRunInstall() throws IOException {
        options.put(Commands.OPTION_DRY_RUN, "");

        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));

        inst.execute();

        assertFalse(Files.list(targetPath).findFirst().isPresent());
    }

    @Test
    public void testValidateInstallFail() throws IOException {
        options.put(Commands.OPTION_VALIDATE, "");

        files.add(dataFile("truffleruby-i386.zip").toFile());
        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));

        try {
            inst.execute();
            fail("The second component should fail dependencies");
        } catch (DependencyException ex) {
            // failed install
        }
        assertFalse(Files.list(targetPath).findFirst().isPresent());
    }

    private String errorKey;
    private String msg;

    @Test
    public void testIgnoreFailedInstall() throws IOException {
        options.put(Commands.OPTION_VALIDATE, "");

        files.add(dataFile("truffleruby-i386.zip").toFile());
        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));

        inst.setIgnoreFailures(true);

        delegateFeedback(new FeedbackAdapter() {
            @Override
            public void error(String key, Throwable t, Object... params) {
                errorKey = key;
                msg = t.getMessage();
            }

        });

        inst.execute();
        assertFalse(Files.list(targetPath).findFirst().isPresent());
        assertEquals("VERIFY_Dependency_Failed", msg);
        assertEquals("INSTALL_IgnoreFailedInstallation2", errorKey);

    }

    @Test
    public void testDryRunForce() throws IOException {
        options.put(Commands.OPTION_DRY_RUN, "");
        options.put(Commands.OPTION_FORCE, "");

        files.add(dataFile("truffleruby-i386.zip").toFile());
        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));

        inst.execute();
        assertFalse(Files.list(targetPath).findFirst().isPresent());
    }

    @Test
    public void testValidateInstall() throws IOException {
        options.put(Commands.OPTION_VALIDATE, "");

        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));
        assertFalse(Files.list(targetPath).findFirst().isPresent());
    }

    @Test
    public void testFailOnExistingComponent() throws IOException {
        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));

        inst.execute();

        options.put(Commands.OPTION_FAIL_EXISTING, "");
        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));
        files.add(dataFile("truffleruby3.jar").toFile());

        exception.expect(DependencyException.class);
        exception.expectMessage("VERIFY_ComponentExists");
        inst.execute();
    }

    @Test
    public void testSkipExistingComponent() throws IOException {
        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));

        inst.execute();

        File f = new File(folder.getRoot(), "inst");
        File binRuby = new File(f, "bin/ruby");
        assertTrue("Ruby must be installed", binRuby.exists());

        Files.walk(f.toPath()).forEach((p) -> {
            try {
                if (!p.equals(f.toPath()) && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                    Files.delete(p);
                }
            } catch (IOException ex) {
                Logger.getLogger(InstallTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        });

        assertFalse("Ruby must be deleted", binRuby.exists());

        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));
        files.add(dataFile("truffleruby3.jar").toFile());
        inst.execute();
        assertFalse("Component must not be processed", binRuby.exists());
    }

    ComponentIterable componentIterable;

    @Override
    public ComponentIterable existingFiles() throws FailedOperationException {
        if (componentIterable != null) {
            return componentIterable;
        }
        return super.existingFiles();
    }

    @Test
    public void testFailOnExistingFromCatalog() throws Exception {
        ComponentInfo fakeInfo = new ComponentInfo("ruby", "Fake ruby", "1.0");
        storage.installed.add(fakeInfo);

        URL u = new URL("test://graalvm.io/download/catalog");
        URL u2 = new URL(u, "graalvm-ruby.zip");

        Handler.bind(u.toString(), getClass().getResource("catalog"));
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "0.33-dev");
        initCatalogIterable(u);
        textParams.add("ruby");
        options.put(Commands.OPTION_FAIL_EXISTING, "");
        files.clear();
        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));

        try {
            inst.execute();
        } catch (DependencyException.Conflict ex) {
            assertEquals("VERIFY_ComponentExists", ex.getMessage());
        }

        assertFalse(Handler.isVisited(u2));
    }

    private void initCatalogIterable(URL u) {
        RemoteCatalogDownloader rcd = new RemoteCatalogDownloader(
                        this,
                        this,
                        u);

        registry = new CatalogContents(this, rcd.getStorage(), localRegistry);
        componentIterable = new CatalogIterable(this, this);
    }

    @Test
    public void testSkipExistingFromCatalog() throws Exception {
        ComponentInfo fakeInfo = new ComponentInfo("ruby", "Fake ruby", "1.0");
        storage.installed.add(fakeInfo);

        URL u = new URL("test://graalvm.io/download/catalog");
        URL u2 = new URL(u, "graalvm-ruby.zip");

        Handler.bind(u.toString(), getClass().getResource("catalog"));
        Handler.bind(u2.toString(), getClass().getResource("graalvm-ruby.zip"));
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "0.33-dev");
        initCatalogIterable(u);
        textParams.add("ruby");
        files.clear();
        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));

        try {
            inst.execute();
        } catch (DependencyException.Conflict ex) {
            fail("Should not raise an error");
        }

        assertFalse("Should not touch the remote file", Handler.isVisited(u2));
    }

    @Test
    public void testReplaceExistingComponent() throws IOException {
        options.put(Commands.OPTION_REPLACE_COMPONENTS, "");
        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));

        inst.execute();

        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));
        files.add(dataFile("truffleruby3.jar").toFile());

        inst.execute();
    }

    @Test
    public void testFailInstallCleanup() throws IOException {
        Path offending = targetPath.resolve(SystemUtils.fromCommonString("jre/bin/ruby"));
        Files.createDirectories(offending.getParent());
        Files.createFile(offending);

        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));

        try {
            inst.execute();
            fail("Exception expected");
        } catch (IOException | FailedOperationException ex) {
            // OK
        }
        Files.delete(offending);
        Files.delete(offending.getParent()); // jre/bin
        Files.delete(offending.getParent().getParent()); // jre
        assertFalse(Files.list(targetPath).findFirst().isPresent());
    }

    @Test
    public void testPostinstMessagePrinted() throws Exception {
        AtomicBoolean printed = new AtomicBoolean();
        delegateFeedback(new FeedbackAdapter() {
            @Override
            public boolean verbatimOut(String aMsg, boolean beVerbose) {
                if ("Postinst".equals(aMsg)) {
                    printed.set(true);
                }
                return super.verbatimOut(aMsg, beVerbose);
            }
        });
        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));

        inst.execute();
        assertTrue("Postinst message must be printed", printed.get());
    }

    /**
     * The exact message contents. Whitespaces are important, incl. newlines.
     */
    private static final String GOLDEN_MESSAGE = "\n" +
                    "IMPORTANT NOTE:\n" +
                    "---------------\n" +
                    "The Ruby openssl C extension needs to be recompiled on your system to work with the installed libssl.\n" +
                    "Make sure headers for libssl are installed, see https://github.com/oracle/truffleruby/blob/master/doc/user/installing-libssl.md for details.\n" +
                    "Then run the following command:\n" +
                    "      ${graalvm_home}/jre/languages/ruby/lib/truffle/post_install_hook.sh\n"; // exactly
                                                                                                   // 6
                                                                                                   // spaces
                                                                                                   // at
                                                                                                   // the
                                                                                                   // beginning

    @Test
    public void testPostinstMessageFormat() throws Exception {
        String[] formatted = new String[1];
        files.set(0, dataFile("postinst.jar").toFile());
        delegateFeedback(new FeedbackAdapter() {
            @Override
            public boolean verbatimOut(String aMsg, boolean beVerbose) {
                if (aMsg.contains("Ruby openssl")) { // NOI18N
                    formatted[0] = aMsg;
                }
                return super.verbatimOut(aMsg, beVerbose);
            }
        });
        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));

        inst.execute();
        assertNotNull("Postinst message must be printed", formatted[0]);

        String check = GOLDEN_MESSAGE.replace("${graalvm_home}", getGraalHomePath().toString());
        assertEquals(check, formatted[0]);
    }

    /**
     * Installs an a missing component from the same distribution.
     * 
     * @throws Exception
     */
    @Test
    public void testInstallMissingComponent() throws Exception {
        ComponentInfo fakeInfo = new ComponentInfo("ruby", "Fake ruby", "1.0");
        storage.installed.add(fakeInfo);

    }

    @Test
    public void testRefuseNonAdminInstall() throws Exception {
        options.put(Commands.OPTION_DRY_RUN, "");

        storage.writableUser = "hero"; // NOI18N

        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));

        exception.expect(FailedOperationException.class);
        exception.expectMessage("ADMIN");

        inst.execute();
    }

    private static final String BLOCKED_CONTENT = "This is a blocked file"; // NOI18N
    private static final String INSTALL_CONTENT = "#!/usr/bin/env bash"; // NOI18N

    /**
     * Checks that in the 'replace' scenario, the locked file is first scheduled for delete and then
     * the new version for copy/moe to the original place.
     */
    @Test
    public void testReplaceExistingComponentWithLockedFiles() throws IOException {
        options.put(Commands.OPTION_REPLACE_COMPONENTS, "");
        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));

        inst.execute();

        Path blockedFile = targetPath.resolve(SystemUtils.fromCommonString("jre/languages/ruby/bin/rake"));
        Path copyDir = targetPath.resolve(SystemUtils.fromCommonString("jre/languages/ruby/bin.new"));
        Path copyFile = copyDir.resolve("rake");

        Files.write(blockedFile, Collections.singletonList(BLOCKED_CONTENT));

        BlockedFileOps blockedOps = new BlockedFileOps();
        fileOps = blockedOps;
        fileOps.init(this);
        fileOps.setRootPath(targetPath);

        blockedOps.blockedPaths.add(blockedFile);
        Path delayDeletes = folder.newFile("delayDeletes").toPath();
        Path copiedFiles = folder.newFile("copiedDirs").toPath();
        blockedOps.setDelayDeletedList(delayDeletes);
        blockedOps.setCopyContents(copiedFiles);

        inst = new InstallCommand();
        inst.init(this, withBundle(InstallCommand.class));
        files.add(dataFile("truffleruby3.jar").toFile());

        inst.execute();

        // check that the original blocked file was not replaced:
        assertEquals(BLOCKED_CONTENT, Files.readAllLines(blockedFile).get(0));
        assertEquals(INSTALL_CONTENT, Files.readAllLines(copyFile).get(0));
    }

}
