/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.workflowtest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.Collections;
import java.util.Map;

/**
 * Download and expand the latest dev version of Chromium.
 */
public final class DownloadChromiumDev {

    private static final String DIR_NAME = "CHROME";
    private static final String LAST_VERSION_URL = "https://www.googleapis.com/download/storage/v1/b/chromium-browser-snapshots/o/Linux_x64%2FLAST_CHANGE?alt=media";
    private static final String ZIP_URL = "https://www.googleapis.com/download/storage/v1/b/chromium-browser-snapshots/o/Linux_x64%2FVERSION%2Fchrome-linux.zip?alt=media";
    private static final String CHROMIUM_ZIP_FILE = "chrome-linux.zip";

    public static void main(String[] args) throws IOException {
        File localDir;
        if (args.length > 0) {
            localDir = new File(args[0]);
            localDir.mkdirs();
        } else {
            localDir = new File("");
        }
        downloadLinux(localDir.getAbsoluteFile().getCanonicalFile());
    }

    public static String[] download() throws IOException {
        File tmp;
        String runnerTemp = System.getenv("RUNNER_TEMP");
        if (runnerTemp != null) {
            tmp = new File(runnerTemp, DIR_NAME);
            tmp.mkdir();
        } else {
            tmp = Files.createTempDirectory(DIR_NAME).toFile();
        }
        tmp = tmp.getCanonicalFile();
        return downloadLinux(tmp);
    }

    private static InputStream openURL(String url) throws IOException {
        String httpsProxy = System.getenv("HTTPS_PROXY");
        if (httpsProxy == null) {
            return URI.create(url).toURL().openStream();
        }
        int colonIndex = httpsProxy.lastIndexOf(':');
        String host = httpsProxy.substring(0, colonIndex);
        int protocolIndex = host.indexOf("://");
        if (protocolIndex > 0) {
            host = host.substring(protocolIndex + 3);
        }
        int port = Integer.parseInt(httpsProxy.substring(colonIndex + 1));
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        return URI.create(url).toURL().openConnection(proxy).getInputStream();
    }

    private static String[] downloadLinux(File localDir) throws IOException {
        String version;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(openURL(LAST_VERSION_URL)))) {
            version = reader.readLine();
        }
        System.err.println("Downloading Chromium " + version + " to " + localDir);
        String zipUrl = ZIP_URL.replace("VERSION", version);
        File zipFile = new File(localDir, CHROMIUM_ZIP_FILE).getAbsoluteFile();
        Files.copy(openURL(zipUrl), zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        URI zipLocalUri = URI.create("jar:" + zipFile.toURI());
        Map<String, Object> env = Collections.singletonMap("enablePosixFileAttributes", Boolean.TRUE);

        try(FileSystem zipFs = FileSystems.newFileSystem(zipLocalUri, env)) {
            Path localRootPath = localDir.toPath();
            for (Path root : zipFs.getRootDirectories()) {
                Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
                        if (!attrs.isDirectory()) {
                            String name = root.relativize(filePath).toString();
                            Path targetPath = localRootPath.resolve(name);
                            Files.createDirectories(targetPath.getParent());
                            Files.copy(filePath, targetPath, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                            if (attrs instanceof PosixFileAttributes pattrs) {
                                Files.setPosixFilePermissions(targetPath, pattrs.permissions());
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
        zipFile.delete();
        System.err.println("Chromium " + version + " expanded to " + localDir);
        String chromePath = new File(localDir, "chrome-linux/chrome").getCanonicalPath();
        System.out.println("Chromium binary is at " + new File(localDir, "chrome-linux/chrome").getCanonicalPath());
        version = getChromeVersion(chromePath);
        String[] chrome = new String[2];
        chrome[0] = chromePath; // Chrome binary
        chrome[1] = version;    // Chrome version
        return chrome;
    }

    private static String getChromeVersion(String chromePath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(chromePath, "--version");
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process p = pb.start();
        String versionLine;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            versionLine = br.readLine();
        }
        try {
            p.waitFor();
        } catch (InterruptedException ex) {
            // skip
        }
        System.out.println("Version: " + versionLine);
        int i1 = 0;
        while (i1 < versionLine.length() && !Character.isDigit(versionLine.charAt(i1))) {
            i1++;
        }
        int i2 = versionLine.indexOf('.', i1);
        return versionLine.substring(i1, i2);
    }

}
