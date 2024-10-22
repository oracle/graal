/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.visualizer.upgrader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import org.netbeans.junit.NbTestCase;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.Utilities;

/**
 * @author sdedic
 */
public class UpgraderTestDist extends NbTestCase {
    private static final String APPLICATION_NAME = "idealgraphvisualizer"; // NOI18N
    private static final boolean DEBUG_IMPORT = false;

    public UpgraderTestDist(String name) {
        super(name);
    }

    File distDir;
    File installDir;
    boolean noMainClass;

    private File findInstallDir() throws IOException, URISyntaxException {
        URL u = Lookup.class.getProtectionDomain().getCodeSource().getLocation();
        File utilFile = new File(u.toURI());
        // nbplatform dir
        assertTrue(utilFile.exists());
        File root = utilFile.getParentFile().getParentFile().getParentFile();
        distDir = new File(root.getParentFile(), "dist");
        assertTrue(distDir.exists() && distDir.isDirectory());
        installDir = new File(distDir, "idealgraphvisualizer");
        assertTrue(installDir.isDirectory());
        return installDir;
    }

    public void testLauncherProperties() throws Exception {
        File root = findInstallDir();

        // attempt to exec the launcher, capture the parameters:

        File wd = new File(getWorkDir(), "current");
        String extraArgs = "1 * * *";
        run(root, wd.getAbsolutePath(), extraArgs);
        String[] args = MainCallback.getArgs(wd);
        Properties sysProps = MainCallback.getProperties(wd);

        if (!Utilities.isWindows()) {
            String s = sysProps.getProperty("netbeans.default_userdir_root");
            assertNotNull(s);
            assertFalse(s.isEmpty());

            s = sysProps.getProperty("netbeans.user");
            assertEquals(wd.getAbsolutePath(), s);

            s = sysProps.getProperty("netbeans.importclass"); // NOI18N
            assertEquals(Upgrader.class.getName(), s);
        }
    }

    public void testCheckMigrationFromOldDev() throws Exception {
        File root = findInstallDir();
        File userRoot = new File(getWorkDir(), "userRoot");
        userRoot.mkdirs();
        File cur = new File(getWorkDir(), "current");
        cur.mkdirs();

        unpackZipFile(new File(getDataDir(), "dev.zip"), userRoot);
        String[] opts;

        if (DEBUG_IMPORT) {
            opts = new String[]{
                    "-J-Xdebug",
                    "-J-Xrunjdwp:transport=dt_socket,address=5000,server=y,suspend=y",
                    "-J-Dnetbeans.startup.autoupgrade=true",
                    "-J-Dnetbeans.importclass=" + UpgradeAndExit.class.getName(),
                    "-J-Dnetbeans.default_userdir_root=" + userRoot.getAbsolutePath()
            };
        } else {
            opts = new String[]{
                    "-J-Dnetbeans.startup.autoupgrade=true",
                    "-J-Dnetbeans.importclass=" + UpgradeAndExit.class.getName(),
                    "-J-Dnetbeans.default_userdir_root=" + userRoot.getAbsolutePath()
            };
        }
        noMainClass = true;
        run(root, cur.getAbsolutePath(), opts);

        FileObject confDir = FileUtil.toFileObject(cur).getFileObject("config");
        FileObject oldDevConfDir = FileUtil.toFileObject(userRoot).getFileObject("dev/config");
        assertNotNull(confDir);
        assertNotNull("Old Windows2Local must exist", oldDevConfDir.getFileObject("Windows2Local"));
        assertNull("Windows2Local is not copied", confDir.getFileObject("Windows2Local"));
        // check .nbattrs was copied
        assertTrue("File attributes are migrated", new File(new File(cur, "config"), ".nbattrs").exists());

        FileObject colorFilter = confDir.getFileObject("Filters/Coloring");
        assertNotNull("Customized filter is copied", colorFilter);
        String text = colorFilter.asText();
        assertFalse("Refs to old classes are replaced", text.contains("com.sun.hotspot"));
        assertTrue("Class simple names are translated", text.contains("classSimpleName"));
    }

    private void run(File workDir, String userdir, String... args) throws Exception {
        File bin = new File(workDir, "bin");
        File nbexec = Utilities.isWindows() ? new File(bin, APPLICATION_NAME + ".exe") : new File(bin, APPLICATION_NAME);
        assertTrue("nbexec not found: " + nbexec, nbexec.exists());

        URL tu = MainCallback.class.getProtectionDomain().getCodeSource().getLocation();
        File testf = new File(tu.toURI());
        assertTrue("file found: " + testf, testf.exists());

        LinkedList<String> allArgs = new LinkedList<String>(Arrays.asList(args));
        if (!noMainClass) {
            allArgs.addFirst("-J-Dnetbeans.mainclass=" + MainCallback.class.getName());
        }
        allArgs.addFirst(System.getProperty("java.home"));
        allArgs.addFirst("--jdkhome");
        if (userdir == null) {
            allArgs.addFirst(getWorkDirPath());
        } else {
            allArgs.addFirst(userdir);
        }
        allArgs.addFirst("--userdir");
        allArgs.addFirst(testf.getPath());
        allArgs.addFirst("-cp:p");

        if (!Utilities.isWindows()) {
            allArgs.addFirst(nbexec.getPath());
            allArgs.addFirst("-x");
            allArgs.addFirst("/bin/sh");
        } else {
            allArgs.addFirst(nbexec.getPath());
        }

        StringBuffer sb = new StringBuffer();
        Process p = Runtime.getRuntime().exec(allArgs.toArray(new String[0]), null, workDir);
        int res = readOutput(sb, p);

        String output = sb.toString();

        assertEquals("Execution is ok: " + output, 0, res);
    }

    private static int readOutput(final StringBuffer sb, Process p) throws Exception {
        class Read extends Thread {
            private InputStream is;

            public Read(String name, InputStream is) {
                super(name);
                this.is = is;
                setDaemon(true);
            }

            @Override
            public void run() {
                byte[] arr = new byte[4096];
                try {
                    for (; ; ) {
                        int len = is.read(arr);
                        if (len == -1) {
                            return;
                        }
                        sb.append(new String(arr, 0, len));
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }

        Read out = new Read("out", p.getInputStream());
        Read err = new Read("err", p.getErrorStream());
        out.start();
        err.start();

        int res = p.waitFor();

        out.interrupt();
        err.interrupt();
        out.join();
        err.join();

        return res;
    }


    public static void unpackZipFile(File zip, File dir) throws IOException {
        byte[] buf = new byte[8192];
        InputStream is = new FileInputStream(zip);
        try {
            ZipInputStream zis = new ZipInputStream(is);
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                int slash = name.lastIndexOf('/');
                File d = new File(dir, name.substring(0, slash).replace('/', File.separatorChar));
                if (!d.isDirectory() && !d.mkdirs()) {
                    throw new IOException("could not make " + d);
                }
                if (slash != name.length() - 1) {
                    File f = new File(dir, name.replace('/', File.separatorChar));
                    OutputStream os = new FileOutputStream(f);
                    try {
                        int read;
                        while ((read = zis.read(buf)) != -1) {
                            os.write(buf, 0, read);
                        }
                    } finally {
                        os.close();
                    }
                }
            }
        } finally {
            is.close();
        }
    }
}
