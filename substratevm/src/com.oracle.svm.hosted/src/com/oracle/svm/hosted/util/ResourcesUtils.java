/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.util;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import com.oracle.svm.core.util.VMError;

import sun.net.www.ParseUtil;

public class ResourcesUtils {

    /**
     * Returns jar path from the given url.
     */
    private static String urlToJarPath(URL url) {
        return urlToJarUri(url).getPath();
    }

    /**
     * Returns directory that contains resource on the given url.
     */
    public static String getResourceSource(URL url, String resource, boolean fromJar) {
        try {
            String source = fromJar ? urlToJarUri(url).toString() : url.toURI().toString();

            if (!fromJar) {
                // -1 removes trailing slash from path of directory that contains resource
                source = source.substring(0, source.length() - resource.length() - 1);
                if (source.endsWith("/")) {
                    // if resource was directory we still have one slash at the end
                    source = source.substring(0, source.length() - 1);
                }
            }

            return source;
        } catch (URISyntaxException e) {
            throw VMError.shouldNotReachHere("Cannot get uri from: " + url, e);
        }
    }

    /**
     * Returns whether the given resource is directory or not.
     */
    public static boolean resourceIsDirectory(URL url, boolean fromJar) throws IOException, URISyntaxException {
        if (fromJar) {
            try (JarFile jf = new JarFile(urlToJarPath(url))) {
                String path = url.getPath();
                String fullResourcePath = path.substring(path.indexOf("!") + 1);
                if (fullResourcePath.startsWith("/")) {
                    fullResourcePath = fullResourcePath.substring(1);
                }

                /* we are using decoded path to be resilient to spaces */
                String decodedString = ParseUtil.decode(fullResourcePath);
                return jf.getEntry(decodedString).isDirectory();
            }
        } else {
            return Files.isDirectory(Path.of(url.toURI()));
        }
    }

    /**
     * Returns directory content of the resource from the given path.
     */
    public static String getDirectoryContent(String path, boolean fromJar) throws IOException {
        Set<String> content = new TreeSet<>();
        if (fromJar) {
            try (JarFile jf = new JarFile(urlToJarPath(URI.create(path).toURL()))) {
                String pathSeparator = FileSystems.getDefault().getSeparator();
                String directoryPath = path.split("!")[1];

                // we are removing leading slash because jar entry names don't start with slash
                if (directoryPath.startsWith(pathSeparator)) {
                    directoryPath = directoryPath.substring(1);
                }

                Enumeration<JarEntry> entries = jf.entries();
                while (entries.hasMoreElements()) {
                    String entry = entries.nextElement().getName();
                    if (entry.startsWith(directoryPath)) {
                        String contentEntry = entry.substring(directoryPath.length());

                        // remove the leading slash
                        if (contentEntry.startsWith(pathSeparator)) {
                            contentEntry = contentEntry.substring(1);
                        }

                        // prevent adding empty strings as a content
                        if (!contentEntry.isEmpty()) {
                            // get top level content only
                            int firstSlash = contentEntry.indexOf(pathSeparator);
                            if (firstSlash != -1) {
                                content.add(contentEntry.substring(0, firstSlash));
                            } else {
                                content.add(contentEntry);
                            }
                        }
                    }
                }

            }
        } else {
            try (Stream<Path> contentStream = Files.list(Path.of(path))) {
                content = new TreeSet<>(contentStream
                                .map(Path::getFileName)
                                .map(Path::toString)
                                .toList());
            }
        }

        return String.join(System.lineSeparator(), content);
    }

    private static URI urlToJarUri(URL url) {
        try {
            return ((JarURLConnection) url.openConnection()).getJarFileURL().toURI();
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

}
