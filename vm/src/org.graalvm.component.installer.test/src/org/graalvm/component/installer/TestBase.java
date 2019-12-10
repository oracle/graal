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
package org.graalvm.component.installer;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Enumeration;
import java.util.ResourceBundle;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.After;

import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;

/**
 * Boilerplate for tests.
 */
public class TestBase implements Feedback {
    private static final ResourceBundle NO_BUNDLE = new ResourceBundle() {
        @Override
        protected Object handleGetObject(String key) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Enumeration<String> getKeys() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    };
    protected ResourceBundle defaultBundle = ResourceBundle.getBundle("org.graalvm.component.installer.Bundle"); // NOI18N
    private Feedback feedbackDelegate;
    protected boolean verbose;

    @ClassRule public static TemporaryFolder expandedFolder = new ClassTempFolder();
    @Rule public TemporaryFolder testFolder = new TemporaryFolder();
    @Rule public TestName testName = new TestName();

    public TestBase() {
    }

    static class ClassTempFolder extends TemporaryFolder {
        ThreadLocal<File> root = new ThreadLocal<>();

        @Override
        public void delete() {
            File folder = root.get();
            if (folder != null) {
                recursiveDelete(folder);
            }
            root.set(null);
        }

        private void recursiveDelete(File file) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File each : files) {
                    recursiveDelete(each);
                }
            }
            file.delete();
        }

        @Override
        public File getRoot() {
            return root.get();
        }

        @Override
        public void create() throws IOException {
            File createdFolder = File.createTempFile("junit", "", null);
            createdFolder.delete();
            createdFolder.mkdir();
            root.set(createdFolder);
        }

    }

    protected static Path expandedJarPath = null;

    protected void delegateFeedback(Feedback delegate) {
        this.feedbackDelegate = delegate;
    }

    @AfterClass
    public static void cleanupExpandedPath() {
        expandedJarPath = null;
    }

    public void expandJar() throws IOException {
        if (expandedJarPath != null) {
            return;
        }
        URL location = getClass().getProtectionDomain().getCodeSource().getLocation();
        File f;
        try {
            f = new File(location.toURI());
        } catch (URISyntaxException ex) {
            fail(ex.getMessage());
            return; // keep compiler happy
        }
        if (f.isDirectory()) {
            // ok, we can access files directly
            return;
        }
        expandedJarPath = expandedFolder.newFolder("expanded-data").toPath();
        JarFile jf = new JarFile(f);
        for (JarEntry en : Collections.list(jf.entries())) {
            Path target = expandedJarPath.resolve(SystemUtils.fromCommonString(en.getName()));
            if (en.isDirectory()) {
                Files.createDirectories(target);
            } else {
                Files.createDirectories(target.getParent());
                try (InputStream is = jf.getInputStream(en)) {
                    Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    protected Path dataFile(String relative) throws IOException {
        expandJar();
        URL u = getClass().getClassLoader().getResource(getClass().getName().replace('.', '/') + ".class");
        Path basePath;
        if (expandedJarPath != null) {
            String n = getClass().getName();
            n = n.substring(0, n.lastIndexOf('.')).replace('.', '/');
            basePath = expandedJarPath.resolve(SystemUtils.fromCommonString(n));
        } else {
            try {
                basePath = Paths.get(u.toURI()).getParent();
            } catch (URISyntaxException ex) {
                fail("URI error");
                return null;
            }
        }
        return basePath.resolve(SystemUtils.fromCommonString(relative));
    }

    protected InputStream dataStream(String relative) {
        String n = getClass().getName();
        n = n.substring(0, n.lastIndexOf('.')).replace('.', '/');
        return getClass().getClassLoader().getResourceAsStream(n + "/" + relative);
    }

    protected void copyDir(String subdir, Path to) throws IOException {
        Path subdirPath = dataFile(subdir);
        Files.walkFileTree(subdirPath, new CopyDir(subdirPath, to));
    }

    public class CopyDir extends SimpleFileVisitor<Path> {
        private Path sourceDir;
        private Path targetDir;

        public CopyDir(Path sourceDir, Path targetDir) {
            this.sourceDir = sourceDir;
            this.targetDir = targetDir;
        }

        @Override
        public FileVisitResult visitFile(Path file,
                        BasicFileAttributes attributes) throws IOException {
            Path targetFile = targetDir.resolve(sourceDir.relativize(file));
            Files.copy(file, targetFile);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir,
                        BasicFileAttributes attributes) throws IOException {
            Path newDir = targetDir.resolve(sourceDir.relativize(dir));
            Files.createDirectories(newDir);
            return FileVisitResult.CONTINUE;
        }
    }

    public void message(ResourceBundle bundle, String bundleKey, Object... params) {
        if (bundle != null) {
            MessageFormat.format(bundle.getString(bundleKey), params);
        }
        if (feedbackDelegate instanceof FeedbackAdapter) {
            ((FeedbackAdapter) feedbackDelegate).setBundle(bundle == null ? NO_BUNDLE : bundle);
        }
        if (feedbackDelegate != null) {
            feedbackDelegate.message(bundleKey, params);
        }
    }

    public void output(ResourceBundle bundle, String bundleKey, Object... params) {
        if (bundle != null) {
            MessageFormat.format(bundle.getString(bundleKey), params);
        }
        if (feedbackDelegate instanceof FeedbackAdapter) {
            ((FeedbackAdapter) feedbackDelegate).setBundle(bundle == null ? NO_BUNDLE : bundle);
        }
        if (feedbackDelegate != null) {
            feedbackDelegate.output(bundleKey, params);
        }
    }

    @Override
    public void outputPart(String bundleKey, Object... params) {
        output(defaultBundle, bundleKey, params);
    }

    @Override
    public boolean verbatimPart(String msg, boolean error, boolean beVerbose) {
        return verbatimPart(msg, beVerbose);
    }

    @Override
    public boolean verbatimPart(String msg, boolean beVerbose) {
        if (feedbackDelegate instanceof FeedbackAdapter) {
            ((FeedbackAdapter) feedbackDelegate).setBundle(NO_BUNDLE);
        }
        try {
            if (feedbackDelegate != null) {
                return feedbackDelegate.verbatimPart(msg, beVerbose);
            }
        } finally {
            if (feedbackDelegate instanceof FeedbackAdapter) {
                ((FeedbackAdapter) feedbackDelegate).setBundle(null);
            }
        }
        return verbose;
    }

    public void verbosePart(ResourceBundle bundle, String bundleKey, Object... params) {
        if (bundle != null) {
            MessageFormat.format(bundle.getString(bundleKey), params);
        }
        if (feedbackDelegate instanceof FeedbackAdapter) {
            ((FeedbackAdapter) feedbackDelegate).setBundle(bundle == null ? NO_BUNDLE : bundle);
        }
        if (feedbackDelegate != null) {
            feedbackDelegate.verbosePart(bundleKey, params);
        }
    }

    public void verboseOutput(ResourceBundle bundle, String bundleKey, Object... params) {
        if (bundle != null && bundleKey != null) {
            MessageFormat.format(bundle.getString(bundleKey), params);
        }
        if (feedbackDelegate instanceof FeedbackAdapter) {
            ((FeedbackAdapter) feedbackDelegate).setBundle(bundle == null ? NO_BUNDLE : bundle);
        }
        if (feedbackDelegate != null) {
            feedbackDelegate.verboseOutput(bundleKey, params);
        }
    }

    public void error(ResourceBundle bundle, String key, Throwable t, Object... params) {
        if (bundle != null) {
            MessageFormat.format(bundle.getString(key), params);
        }
        if (feedbackDelegate instanceof FeedbackAdapter) {
            ((FeedbackAdapter) feedbackDelegate).setBundle(bundle == null ? NO_BUNDLE : bundle);
        }
        if (feedbackDelegate != null) {
            feedbackDelegate.error(key, t, params);
        }
    }

    public String l10n(ResourceBundle bundle, String key, Object... params) {
        if (bundle != null) {
            MessageFormat.format(bundle.getString(key), params);
        }
        if (feedbackDelegate != null) {
            String s;
            if (feedbackDelegate instanceof FeedbackAdapter) {
                ((FeedbackAdapter) feedbackDelegate).setBundle(bundle);
            }
            s = feedbackDelegate.l10n(key, params);
            if (s != null) {
                return s;
            }
        }
        if (key.endsWith("@") && bundle != null) {
            return MessageFormat.format(bundle.getString(key), params);
        } else {
            return key;
        }
    }

    public String reallyl10n(ResourceBundle bundle, String key, Object... params) {
        ResourceBundle b = bundle != null ? bundle : defaultBundle;
        return MessageFormat.format(b.getString(key), params);
    }

    @Override
    public boolean verbatimOut(String msg, boolean verboseOutput) {
        if (feedbackDelegate instanceof FeedbackAdapter) {
            ((FeedbackAdapter) feedbackDelegate).setBundle(NO_BUNDLE);
        }
        try {
            if (verboseOutput) {
                verboseOutput((ResourceBundle) null, msg);
            } else {
                if (feedbackDelegate != null) {
                    feedbackDelegate.verbatimOut(msg, verboseOutput);
                }
                output((ResourceBundle) null, msg);
            }
        } finally {
            if (feedbackDelegate instanceof FeedbackAdapter) {
                ((FeedbackAdapter) feedbackDelegate).setBundle(null);
            }
        }
        return verboseOutput;
    }

    public RuntimeException failure(ResourceBundle bundle, String key, Throwable t, Object... params) {
        MessageFormat.format(bundle.getString(key), params);
        throw new FailedOperationException(key, t);
    }

    @Override
    public void message(String bundleKey, Object... params) {
        message(defaultBundle, bundleKey, params);
    }

    @Override
    public void output(String bundleKey, Object... params) {
        output(defaultBundle, bundleKey, params);
    }

    @Override
    public boolean backspace(int chars, boolean beVerbose) {
        if (beVerbose && !verbose) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chars; i++) {
            sb.append('\b');
        }
        if (beVerbose) {
            verbosePart((ResourceBundle) null, sb.toString());
        } else {
            output((ResourceBundle) null, sb.toString());
        }
        return verbose;
    }

    @Override
    public boolean verbosePart(String bundleKey, Object... params) {
        verbosePart(defaultBundle, bundleKey, params);
        return verbose;
    }

    @Override
    public boolean verboseOutput(String bundleKey, Object... params) {
        verboseOutput(defaultBundle, bundleKey, params);
        return verbose;
    }

    @Override
    public void error(String key, Throwable t, Object... params) {
        error(defaultBundle, key, t, params);
    }

    @Override
    public String l10n(String key, Object... params) {
        return l10n(defaultBundle, key, params);
    }

    @Override
    public RuntimeException failure(String key, Throwable t, Object... params) {
        return failure(defaultBundle, key, t, params);
    }

    @Override
    public <T> Feedback withBundle(Class<T> clazz) {
        return new WB(clazz);
    }

    class WB implements Feedback {
        ResourceBundle localBundle;

        @Override
        public void message(String bundleKey, Object... params) {
            TestBase.this.message(localBundle, bundleKey, params);
        }

        @Override
        public void output(String bundleKey, Object... params) {
            TestBase.this.output(localBundle, bundleKey, params);
        }

        @Override
        public boolean verbosePart(String bundleKey, Object... params) {
            TestBase.this.verbosePart(localBundle, bundleKey, params);
            return verbose;
        }

        @Override
        public boolean verboseOutput(String bundleKey, Object... params) {
            TestBase.this.verboseOutput(localBundle, bundleKey, params);
            return verbose;
        }

        @Override
        public void error(String key, Throwable t, Object... params) {
            TestBase.this.error(localBundle, key, t, params);
        }

        @Override
        public String l10n(String key, Object... params) {
            return TestBase.this.l10n(localBundle, key, params);
        }

        @Override
        public RuntimeException failure(String key, Throwable t, Object... params) {
            return TestBase.this.failure(localBundle, key, t, params);
        }

        @Override
        public boolean verbatimOut(String msg, boolean beVerbose) {
            return TestBase.this.verbatimOut(msg, beVerbose);
        }

        <X> WB(Class<X> clazz) {
            String s = clazz.getName();
            s = s.substring(0, s.lastIndexOf('.'));
            this.localBundle = ResourceBundle.getBundle(s + ".Bundle"); // NOI18N
        }

        @Override
        public <X> Feedback withBundle(Class<X> clazz) {
            return new WB(clazz);
        }

        @Override
        public void outputPart(String bundleKey, Object... params) {
            TestBase.this.output(localBundle, bundleKey, params);
        }

        @Override
        public boolean verbatimPart(String msg, boolean error, boolean beVerbose) {
            return TestBase.this.verbatimPart(msg, error, beVerbose);
        }

        @Override
        public boolean verbatimPart(String msg, boolean beVerbose) {
            return TestBase.this.verbatimPart(msg, beVerbose);
        }

        @Override
        public boolean backspace(int chars, boolean beVerbose) {
            return TestBase.this.backspace(chars, beVerbose);
        }

        @Override
        public String acceptLine(boolean autoYes) {
            return TestBase.this.acceptLine(autoYes);
        }

        @Override
        public char[] acceptPassword() {
            return TestBase.this.acceptPassword();
        }

        @Override
        public void addLocalFileCache(URL location, Path local) {
            TestBase.this.addLocalFileCache(location, local);
        }

        @Override
        public Path getLocalCache(URL location) {
            return TestBase.this.getLocalCache(location);
        }
    }

    public class FeedbackAdapter implements Feedback {
        private ResourceBundle currentBundle;

        @Override
        public boolean verbatimOut(String msg, boolean beVerbose) {
            return verbose;
        }

        @Override
        public void message(String bundleKey, Object... params) {
        }

        @Override
        public void output(String bundleKey, Object... params) {
        }

        @Override
        public boolean verbosePart(String bundleKey, Object... params) {
            return verbose;
        }

        @Override
        public boolean verboseOutput(String bundleKey, Object... params) {
            return verbose;
        }

        @Override
        public void error(String key, Throwable t, Object... params) {
        }

        @Override
        public String l10n(String key, Object... params) {
            return null;
        }

        @Override
        public RuntimeException failure(String key, Throwable t, Object... params) {
            return null;
        }

        @Override
        public <X> Feedback withBundle(Class<X> clazz) {
            return this;
        }

        @Override
        public void outputPart(String bundleKey, Object... params) {
        }

        @Override
        public boolean verbatimPart(String msg, boolean error, boolean beVerbose) {
            return verbose;
        }

        @Override
        public boolean verbatimPart(String msg, boolean beVerbose) {
            return verbose;
        }

        @Override
        public boolean backspace(int chars, boolean beVerbose) {
            return verbose;
        }

        protected String reallyl10n(String k, Object... params) {
            return TestBase.this.reallyl10n(getBundle(), k, params);
        }

        protected ResourceBundle getBundle() {
            if (currentBundle == NO_BUNDLE) {
                return null;
            }
            if (currentBundle != null) {
                return currentBundle;
            }
            return defaultBundle;
        }

        void setBundle(ResourceBundle bundle) {
            this.currentBundle = bundle;
        }

        @Override
        public String acceptLine(boolean autoYes) {
            return TestBase.this.doAcceptLine(autoYes);
        }

        @Override
        public char[] acceptPassword() {
            return TestBase.this.doAcceptPassword();
        }

        @Override
        public void addLocalFileCache(URL location, Path local) {
            TestBase.this.addLocalFileCache(location, local);
        }

        @Override
        public Path getLocalCache(URL location) {
            return TestBase.this.getLocalCache(location);
        }

    }

    public static boolean isWindows() {
        return SystemUtils.isWindows();
    }

    protected StringBuilder userInput = new StringBuilder();
    protected String password;
    protected boolean autoYesEnabled;

    @Override
    public String acceptLine(boolean autoYes) {
        if (feedbackDelegate != null) {
            return feedbackDelegate.acceptLine(autoYes);
        }
        return doAcceptLine(autoYes);
    }

    String doAcceptLine(boolean autoYes) {
        if (autoYes && autoYesEnabled) {
            return AUTO_YES;
        }
        int nl = userInput.indexOf("\n");
        if (nl < 0) {
            nl = userInput.length();
        }
        String r = userInput.substring(0, nl);
        userInput.delete(0, nl);
        return r;
    }

    @Override
    public char[] acceptPassword() {
        if (feedbackDelegate != null) {
            return feedbackDelegate.acceptPassword();
        }
        return doAcceptPassword();
    }

    public char[] doAcceptPassword() {
        return password == null ? null : password.toCharArray();
    }

    @Override
    public void addLocalFileCache(URL location, Path local) {

    }

    @Override
    public Path getLocalCache(URL location) {
        return null;
    }

    @After
    public void disableLicenseAfterTest() {
        SystemUtils.licenseTracking = false;
    }

    public static void enableLicensesForTesting() {
        SystemUtils.licenseTracking = true;
    }
}
