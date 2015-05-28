/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.options.processor;

import java.io.*;
import java.util.*;
import java.util.stream.*;
import java.util.zip.*;

public class GraalJars implements Iterable<ZipEntry> {
    private final List<ZipFile> jars = new ArrayList<>(2);

    public GraalJars() {
        String classPath = System.getProperty("java.class.path");
        for (String e : classPath.split(File.pathSeparator)) {
            if (e.endsWith(File.separatorChar + "graal.jar") || e.endsWith(File.separatorChar + "graal-truffle.jar")) {
                try {
                    jars.add(new ZipFile(e));
                } catch (IOException ioe) {
                    throw new InternalError(ioe);
                }
            }
        }
        if (jars.size() != 2) {
            throw new InternalError("Could not find graal.jar or graal-truffle.jar on class path: " + classPath);
        }
    }

    public Iterator<ZipEntry> iterator() {
        Stream<ZipEntry> entries = jars.stream().flatMap(ZipFile::stream);
        return entries.iterator();
    }

    public InputStream getInputStream(String classFilePath) throws IOException {
        for (ZipFile jar : jars) {
            ZipEntry entry = jar.getEntry(classFilePath);
            if (entry != null) {
                return jar.getInputStream(entry);
            }
        }
        return null;
    }
}
