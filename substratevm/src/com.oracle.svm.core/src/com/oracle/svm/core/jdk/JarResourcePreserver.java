/*
 * Copyright (c) 2019 Alibaba Group Holding Limited. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Alibaba designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 */
package com.oracle.svm.core.jdk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import com.oracle.svm.core.util.UserError.UserException;

/**
 * Save and search tailored jar file to the path specified by -Dsvm.resources.jarLocation or by
 * default to resources/jars. Do nothing if -Dsvm.resource.preserveJarRes is not set to true.
 *
 * @author cengfeng.lzy@alibaba-inc.com
 *
 */
public class JarResourcePreserver {
    // Using property to specify where to preserve the jar resources
    private static final String JAR_RESOURCES_LOC = System.getProperty("svm.resources.jarLocation", "resources/jars");
    public static final boolean PRESERVE_JARS = Boolean.parseBoolean(System.getProperty("svm.resource.preserveJarRes", "false"));
    private File saveToDir;

    public JarResourcePreserver() {
        if (JAR_RESOURCES_LOC == null) {
            saveToDir = null;
        } else {
            saveToDir = new File(JAR_RESOURCES_LOC);
            if (!saveToDir.exists()) {
                saveToDir.mkdirs();
            }
        }
    }

    /**
     * Tail the specified jar file and save it to resource directory. By default all class files are
     * removed, only directory structure and configuration files are reserved. However, any jar
     * entry specified in {@code reservedJarEntries} is also reserved.
     *
     * @param file the target jar file to process
     * @param reservedJarEntries entries in jar file to reserved
     * @throws IOException
     */
    public void preserveJar(File file, List<JarEntry> reservedJarEntries) throws IOException {
        JarFile originalJar = new JarFile(file);
        Manifest man = originalJar.getManifest();
        Enumeration<JarEntry> en = originalJar.entries();

        File ret = new File(saveToDir + File.separator + file.getName());
        JarOutputStream os = new JarOutputStream(new FileOutputStream(ret), man == null ? new Manifest() : new Manifest(man));
        while (en.hasMoreElements()) {
            JarEntry originalElem = en.nextElement();
            String elemName = originalElem.getName();
            // Skip MANIFEST.MF which has been added to new jar at the very beginning.
            // Skip all class files
            if (elemName.equals("META-INF/MANIFEST.MF") ||
                            (elemName.endsWith(".class") && !reservedJarEntries.stream().anyMatch(entry -> {
                                return entry.getName().equals(elemName);
                            }))) {
                continue;
            }
            JarEntry elem = new JarEntry(elemName);
            os.putNextEntry(elem);
            InputStream is = originalJar.getInputStream(originalElem);
            copyStreams(is, os);
            is.close();

        }
        os.close();
        originalJar.close();
    }

    private static void copyStreams(InputStream is, OutputStream os) throws IOException {
        List<Byte> BUFFER = new ArrayList<>();
        int data;
        do {
            data = is.read();
            if (data != -1) {
                BUFFER.add((byte) data);
            } else {
                break;
            }
        } while (true);

        int len = BUFFER.size();
        byte[] writeBuffer = new byte[len];
        for (int i = 0; i < len; i++) {
            writeBuffer[i] = BUFFER.get(i);
        }
        os.write(writeBuffer, 0, len);
    }

    /**
     * Search the resource given by name in jar files in JAR_RESOURCES_LOC directory.
     *
     * @param name resource name
     * @param urls list of matched jar URLs
     * @param firstOnly stop searching when having the first match when {@code true}
     * @throws UserException
     */
    public void searchResourceDir(String name, List<URL> urls, boolean firstOnly) {
        if (PRESERVE_JARS) {
            for (File f : saveToDir.listFiles((d, f) -> {
                return f.endsWith(".jar");
            })) {
                URL ret = searchMatchedJar(name, f);
                if (ret != null) {
                    urls.add(ret);
                    if (firstOnly) {
                        break;
                    }
                }
            }
        }
    }

    private URL searchMatchedJar(String name, File f) {
        try {
            JarFile jf = new JarFile(f);
            boolean found = false;
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                if (e.getName().endsWith("/")) {
                    continue;
                }
                if (name.equals(e.getName())) {
                    found = true;
                    break;
                }
            }
            jf.close();
            if (found) {
                return new URL("jar:file:" + f.getAbsolutePath() + "!/" + name);
            } else {
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
