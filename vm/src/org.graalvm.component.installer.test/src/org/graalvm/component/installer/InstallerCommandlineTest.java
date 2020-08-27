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
package org.graalvm.component.installer;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.graalvm.component.installer.persist.ProxyResource;
import org.graalvm.component.installer.persist.test.Handler;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class InstallerCommandlineTest extends CommandTestBase {

    static class MainErrorException extends RuntimeException {
        private static final long serialVersionUID = 0L;
    }

    private Environment environment;

    class MockInstallerMain extends ComponentInstaller {
        MockInstallerMain(String[] args) {
            super(args);
        }

        @Override
        Environment setupEnvironment(SimpleGetopt go) {

            Environment env = new Environment(getCommand(), getParameters(), go.getOptValues()) {
                @Override
                public Map<String, String> parameters(boolean cmdLine) {
                    return InstallerCommandlineTest.this.parameters(cmdLine);
                }

                @Override
                public String getParameter(String key, boolean cmdLine) {
                    return InstallerCommandlineTest.this.getParameter(key, cmdLine);
                }

            };

            setInput(env);
            setFeedback(InstallerCommandlineTest.this);

            env.setGraalHome(getGraalHomePath());
            env.setLocalRegistry(getLocalRegistry());
            env.setFileOperations(getFileOperations());
            environment = env;
            return env;
        }

        @Override
        protected RuntimeException error(String messageKey, Object... aa) {
            InstallerCommandlineTest.this.error(messageKey, null, aa);
            throw new MainErrorException();
        }

        @Override
        protected SimpleGetopt createOptionsObject(Map<String, String> opts) {
            return new SimpleGetopt(globalOptions) {
                @Override
                public RuntimeException err(String messageKey, Object... aa) {
                    return MockInstallerMain.this.error(messageKey, aa);
                }
            };
        }

        @Override
        protected void printUsage(Feedback output) {
            super.printUsage(InstallerCommandlineTest.this);
        }
    }

    MockInstallerMain main = new MockInstallerMain(new String[0]);

    static class Msg {
        String keyOrMessage;
        Object[] args;
        Throwable ex;

        Msg(String keyOrMessage, Object[] args, Throwable ex) {
            this.keyOrMessage = keyOrMessage;
            this.args = args;
            this.ex = ex;
        }
    }

    class CaptureOut extends FeedbackAdapter {
        private List<Msg> err = new ArrayList<>();
        private List<Msg> out = new ArrayList<>();

        @Override
        public void error(String key, Throwable t, Object... params) {
            err.add(new Msg(key, params, t));
        }

        @Override
        public void output(String bundleKey, Object... params) {
            out.add(new Msg(bundleKey, params, null));
        }

        @Override
        public void message(String bundleKey, Object... params) {
            out.add(new Msg(bundleKey, params, null));
        }

        @Override
        public boolean verbatimOut(String msg, boolean beVerbose) {
            System.err.println("");
            return false;
        }

        @Override
        public String l10n(String key, Object... params) {
            if ("Installer_BuiltingCatalogURL".equals(key)) {
                return testCatalogURL;
            }
            return super.l10n(key, params);
        }
    }

    String testCatalogURL = "test://www.graalvm.org/test/catalog";
    CaptureOut capture = new CaptureOut();
    LinkedList<String> args = new LinkedList<>();

    void assertMsg(String msgKey, boolean out) {
        List<Msg> list = out ? capture.out : capture.err;
        for (Msg m : list) {
            if (msgKey.equals(m.keyOrMessage)) {
                return;
            }
        }
        fail("Expected message: " + msgKey);
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        ComponentInstaller.initCommands();
        delegateFeedback(capture);
    }

    /**
     * Checks that help is printed with no params.
     */
    @Test
    public void testNoParamsPrintsHelp() {
        main.processOptions(new LinkedList<>(Collections.emptyList()));
        assertMsg("INFO_Usage", true);
    }

    /**
     * Checks that help is printed with no params.
     */
    @Test
    public void testNoCommandPrintsError() {
        args = new LinkedList<>();
        args.add("--file");
        exception.expect(MainErrorException.class);

        try {
            main.processOptions(args);
        } finally {
            assertMsg("ERROR_MissingCommand", false);
        }
    }

    /**
     * Checks that --version option prints version and terminates with 0 exit code.
     */
    @Test
    public void testVersionSucceeds() {
        args = new LinkedList<>();
        args.add("--version");

        delegateFeedback(capture);
        int excode = main.processOptions(args);
        assertTrue(capture.err.isEmpty());
        assertMsg("MSG_InstallerVersion", true);
        assertEquals("Must complete succesfully", 0, excode);
    }

    /**
     * Checks that --show-version option prints version and performs the command.
     */
    @Test
    public void testShowVersionSucceeds() {
        args = new LinkedList<>();
        args.add("--show-version");
        args.add("list");

        delegateFeedback(capture);
        int excode = main.processOptions(args);
        assertTrue(capture.err.isEmpty());
        assertMsg("MSG_InstallerVersion", true);
        assertEquals("Should continue execution", -1, excode);
    }

    /**
     * Checks that help is printed with no params.
     */
    @Test
    public void testHelpOption() {
        args = new LinkedList<>();
        args.add("--help");
        assertNull(main.interpretOptions(main.createOptions(args)));
        assertMsg("INFO_Usage", true);

        main = new MockInstallerMain(new String[0]);
        capture.out.clear();
        args.add(0, "install");
        assertNotNull(main.createOptions(args));
    }

    /**
     * Checks that -L -U is not valid.
     */
    @Test
    public void testUrlAndFiles() throws Exception {
        exception.expect(MainErrorException.class);

        args.add("--url");
        args.add("--file");
        main.processOptions(args);
    }

    /**
     * Checks that -c -U is not valid.
     */
    @Test
    public void testUrlAndCatalog() throws Exception {
        exception.expect(MainErrorException.class);

        args.add("--url");
        args.add("-c");
        main.processOptions(args);
    }

    /**
     * Checks that -c -U is not valid.
     */
    @Test
    public void testUrlAndCustomCatalog() throws Exception {
        exception.expect(MainErrorException.class);

        args.add("--url");
        args.add("-C");
        args.add("eee");
        main.processOptions(args);
    }

    @Rule public ProxyResource proxyResource = new ProxyResource();

    @Override
    public CatalogFactory getCatalogFactory() {
        return environment.getCatalogFactory();
    }

    /**
     * Checks that the hardcoded catalog is used, if there's nothing in release file.
     */
    @Test
    public void testUseHardcodedCatalog() throws Exception {
        URL u = getClass().getResource("remote/catalog");
        Handler.bind(testCatalogURL, u);
        this.storage.graalInfo.put(BundleConstants.GRAAL_VERSION, "0.33-dev");
        args.add("avail");

        main.processOptions(args);
        main.doProcessCommand();
        assertTrue(Handler.isVisited(testCatalogURL));
    }

    String releaseURL = "test://graalvm.org/relase/catalog";

    void setupReleaseCatalog() {
        this.storage.graalInfo.put(BundleConstants.GRAAL_VERSION, "0.33-dev");
        this.storage.graalInfo.put("component_catalog", releaseURL);
    }

    /**
     * Checks that the hardcoded catalog is used, if there's nothing in release file.
     */
    @Test
    public void testUseReleaseCatalog() throws Exception {
        setupReleaseCatalog();
        URL u = getClass().getResource("remote/catalog");
        Handler.bind(releaseURL, u);
        args.add("avail");

        main.processOptions(args);
        main.doProcessCommand();
        assertTrue(Handler.isVisited(releaseURL));
    }

    String envURL = "test://graalvm.org/environment/catalog";

    void setupEnvCatalog() {
        envParameters.put("GRAALVM_CATALOG", envURL);
    }

    /**
     * Checks that the hardcoded catalog is used, if there's nothing in release file.
     */
    @Test
    public void testEnvironmentOverridesRelease() throws Exception {
        URL u = getClass().getResource("remote/catalog");
        Handler.bind(envURL, u);
        setupReleaseCatalog();
        setupEnvCatalog();
        args.add("avail");

        main.processOptions(args);
        main.doProcessCommand();
        assertTrue(Handler.isVisited(envURL));
    }

    String syspropURL = "test://graalvm.org/sysprop/catalog";

    void setupSyspropCatalog() {
        propParameters.put("org.graalvm.component.catalog", syspropURL);
    }

    /**
     * Checks that the hardcoded catalog is used, if there's nothing in release file.
     */
    @Test
    public void testSysPropertyOverridesEnv() throws Exception {
        URL u = getClass().getResource("remote/catalog");
        Handler.bind(syspropURL, u);
        setupReleaseCatalog();
        setupEnvCatalog();
        setupSyspropCatalog();
        args.add("avail");

        main.processOptions(args);
        assertEquals(0, main.doProcessCommand());
        assertTrue(Handler.isVisited(syspropURL));
    }

    /**
     * Checks that without an explicit option remote catalogs are not processed when using local
     * files.
     */
    @Test
    public void testLocalFileDoesNotReadCatalogs() throws Exception {
        setupReleaseCatalog();
        Path file = dataFile("persist/dir1/llvm-toolchain.jar");
        args.add("--file");
        args.add("install");
        args.add("-0");
        args.add(file.toString());

        main.processOptions(args);
        assertEquals(0, main.doProcessCommand());

        assertFalse(Handler.isVisited(releaseURL));
        assertFalse(Handler.isVisited(envURL));
        assertFalse(Handler.isVisited(testCatalogURL));
    }

    /**
     * Checks that without an explicit option remote catalogs are not processed when using local
     * files.
     */
    @Test
    public void testLocalFilesWithDefaultCatalog() throws Exception {
        setupReleaseCatalog();
        Path file = dataFile("persist/dir1/llvm-toolchain.jar");
        args.add("--file");
        args.add("-c");
        args.add("install");
        args.add("-0");
        args.add(file.toString());

        // allow the remote catalog:
        URL u = getClass().getResource("remote/catalog");
        Handler.bind(releaseURL, u);

        main.processOptions(args);
        assertEquals(0, main.doProcessCommand());

        // no ID is resolved
        assertFalse(Handler.isVisited(releaseURL));
        assertFalse(Handler.isVisited(envURL));
        assertFalse(Handler.isVisited(testCatalogURL));
    }

    /**
     * Local file with dependencies fails to install if -D is not given.
     */
    @Test
    public void testFailLocalWithDependencies() throws Exception {
        setupReleaseCatalog();
        Path file = dataFile("persist/dir1/ruby.jar");
        args.add("--file");
        args.add("install");
        args.add("-0");
        args.add(file.toString());

        // allow the remote catalog:
        URL u = getClass().getResource("remote/catalog");
        Handler.bind(releaseURL, u);

        main.processOptions(args);

        exception.expect(FailedOperationException.class);
        exception.expectMessage("INSTALL_UnresolvedDependencies");

        try {
            assertEquals(0, main.doProcessCommand());
        } finally {
            assertFalse(Handler.isVisited(releaseURL));
            assertFalse(Handler.isVisited(envURL));
            assertFalse(Handler.isVisited(testCatalogURL));
        }
    }

    /**
     * Checks that with -D, local dependencies are resolved in the file's directory.
     */
    @Test
    public void testLocalDepsResolvedInDirectory() throws Exception {
        setupReleaseCatalog();
        Path file = dataFile("persist/dir1/ruby.jar");
        args.add("--file");
        args.add("install");
        args.add("-0");
        args.add("-D");
        args.add(file.toString());

        // allow the remote catalog:
        URL u = getClass().getResource("remote/catalog");
        Handler.bind(releaseURL, u);

        main.processOptions(args);

        assertEquals(0, main.doProcessCommand());
        assertFalse(Handler.isVisited(releaseURL));
        assertFalse(Handler.isVisited(envURL));
        assertFalse(Handler.isVisited(testCatalogURL));
    }

    /**
     * Checks that -c will cause the catalog to be downloaded.
     * 
     * @throws Exception
     */
    @Test
    public void testEnableCatalogFetchesRemote() throws Exception {
        setupReleaseCatalog();
        args.add("-c");
        args.add("list");

        URL u = getClass().getResource("remote/catalog");
        Handler.bind(releaseURL, u);
        storage.graalInfo.put("component_catalog", releaseURL);

        main.processOptions(args);
        main.doProcessCommand();

        assertTrue(Handler.isVisited(releaseURL));
    }

    /**
     * When -C local-file is passed, gu must not touch its builtin (release file) catalogs URLs.
     * 
     * @throws Exception
     */
    @Test
    public void testDirectoryCatalogDisablesRelease() throws Exception {
        setupReleaseCatalog();
        Path dir = dataFile("repo/19.3.0.0");
        args.add("-C");
        args.add(dir.toAbsolutePath().toString());
        args.add("avail");

        URL u = getClass().getResource("remote/catalog");
        Handler.bind(releaseURL, u);
        storage.graalInfo.put("component_catalog", releaseURL);

        Set<String> componentShorts = new HashSet<>();
        class FB extends FeedbackAdapter {

            @Override
            public String l10n(String key, Object... params) {
                if ("LIST_ComponentShortList".equals(key)) {
                    return "%1$s";
                }
                return super.l10n(key, params);
            }

            @Override
            public boolean verbatimOut(String msg, boolean beVerbose) {
                componentShorts.add(msg);
                return super.verbatimOut(msg, beVerbose);
            }
        }

        FB fb = new FB();

        delegateFeedback(fb);

        main.processOptions(args);
        main.doProcessCommand();

        assertTrue(componentShorts.contains("graalvm"));
        assertTrue(componentShorts.contains("R"));
        assertTrue(componentShorts.contains("ruby"));
        assertTrue(componentShorts.contains("llvm-toolchain"));

        assertFalse(Handler.isVisited(releaseURL));
    }

    /**
     * There's a mix of component versions in the dir; should return just the ones that fit into the
     * graalvm.
     * 
     * @throws Exception
     */
    @Test
    public void testVersionSpecificComponentsFromDir() throws Exception {
        setupReleaseCatalog();
        Path dir = dataFile("persist/data");
        args.add("-C");
        args.add(dir.toAbsolutePath().toString());
        args.add("avail");

        URL u = getClass().getResource("remote/catalog");
        Handler.bind(releaseURL, u);
        storage.graalInfo.put("component_catalog", releaseURL);

        Set<String> componentShorts = new HashSet<>();
        class FB extends FeedbackAdapter {

            @Override
            public String l10n(String key, Object... params) {
                if ("LIST_ComponentShortList".equals(key)) {
                    return "%1$s";
                }
                return super.l10n(key, params);
            }

            @Override
            public boolean verbatimOut(String msg, boolean beVerbose) {
                componentShorts.add(msg);
                return super.verbatimOut(msg, beVerbose);
            }
        }

        FB fb = new FB();

        delegateFeedback(fb);

        main.processOptions(args);
        main.doProcessCommand();

        // note: there's a typo in test data.
        assertTrue(componentShorts.contains("org.graavm.ruby"));
        assertTrue(componentShorts.contains("llvm-toolchain"));
        assertTrue(componentShorts.contains("ruby"));

        assertFalse(Handler.isVisited(releaseURL));
    }

    /**
     * Checks that potential missing resources in release catalog are skipped.
     */
    @Test
    public void testSkipMissingResourcesInReleaseCatalog() throws Exception {
        setupReleaseCatalog();
        args.add("avail");

        URL u = getClass().getResource("remote/catalog.properties");
        String urlString = releaseURL + "_2";
        Handler.bind(urlString, u);
        // note: the releaseURL is NOT bound, FileNotFoundException will be thrown
        storage.graalInfo.put("component_catalog", releaseURL + "|" + urlString);

        class FB extends FeedbackAdapter {
            String warningLine;

            @Override
            public void error(String key, Throwable t, Object... params) {
                if ("REMOTE_WarningErrorDownloadCatalogNotFoundSkip".equals(key)) {
                    warningLine = params[0].toString();
                }
            }

        }

        FB fb = new FB();

        delegateFeedback(fb);
        main.processOptions(args);
        main.doProcessCommand();

        assertTrue(Handler.isVisited(releaseURL));
        assertTrue(Handler.isVisited(urlString));
        assertEquals(releaseURL, fb.warningLine);
    }

    /**
     * Checks that catalog entries in environment win over release file ones.
     * 
     * @throws Exception
     */
    @Test
    public void testExplicitCatalogWinsOverItems() throws Exception {
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "1.0.1.0");
        storage.graalInfo.put("component_catalog", releaseURL);

        // override with env variables catalog entries:
        String url1 = "test://graalv.org/test/explicit.properties";
        String url2 = "test://graalv.org/test/envcatalog.properties";
        envParameters.put("GRAALVM_CATALOG", url1);
        envParameters.put("GRAALVM_COMPONENT_CATALOG_1_URL", url2);
        envParameters.put("GRAALVM_COMPONENT_CATALOG_1_LABEL", "First env");

        args.add("avail");

        main.processOptions(args);
        main.doProcessCommand();

        URL u = getClass().getResource("remote/catalog");
        Handler.bind(releaseURL, u);
        Handler.bind(url1, u);
        Handler.bind(url2, u);

        assertFalse(Handler.isVisited(releaseURL));
        assertFalse(Handler.isVisited(url2));

        assertTrue(Handler.isVisited(url1));
    }
}
