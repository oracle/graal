/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.options;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import com.oracle.graal.options.OptionsParser.OptionDescriptorsProvider;

/**
 * Access to the {@link OptionDescriptors} declared by
 * {@code META-INF/services/com.oracle.graal.options.OptionDescriptors} files in {@code 
 * <jre>/lib/jvmci/*.jar}.
 */
public final class GraalJarsOptionDescriptorsProvider implements OptionDescriptorsProvider {

    static final String OptionDescriptorsServiceFile = "META-INF/services/" + OptionDescriptors.class.getName();

    private final Iterator<File> jars;
    private final List<OptionDescriptors> optionsDescriptorsList;

    /**
     * Creates a {@link GraalJarsOptionDescriptorsProvider} if at least one JVMCI jar is available
     * otherwise returns null.
     */
    public static GraalJarsOptionDescriptorsProvider create() {
        List<File> jarsList = findJVMCIJars();
        if (jarsList.isEmpty()) {
            return null;
        }
        return new GraalJarsOptionDescriptorsProvider(jarsList);
    }

    private GraalJarsOptionDescriptorsProvider(List<File> jarsList) {
        this.jars = jarsList.iterator();
        this.optionsDescriptorsList = new ArrayList<>(jarsList.size() * 3);
    }

    /**
     * Finds the list of JVMCI jars.
     */
    private static List<File> findJVMCIJars() {
        File javaHome = new File(System.getProperty("java.home"));
        File lib = new File(javaHome, "lib");
        File jvmci = new File(lib, "jvmci");
        if (!jvmci.exists()) {
            return Collections.emptyList();
        }

        List<File> jarFiles = new ArrayList<>();
        for (String fileName : jvmci.list()) {
            if (fileName.endsWith(".jar")) {
                File file = new File(jvmci, fileName);
                if (file.isDirectory()) {
                    continue;
                }
                jarFiles.add(file);
            }
        }
        return jarFiles;
    }

    @Override
    public OptionDescriptor get(String name) {
        // Look up loaded option descriptors first
        for (OptionDescriptors optionDescriptors : optionsDescriptorsList) {
            OptionDescriptor desc = optionDescriptors.get(name);
            if (desc != null) {
                return desc;
            }
        }
        while (jars.hasNext()) {
            File path = jars.next();
            try (JarFile jar = new JarFile(path)) {
                ZipEntry entry = jar.getEntry(OptionDescriptorsServiceFile);
                if (entry != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(jar.getInputStream(entry)));
                    String line = null;
                    OptionDescriptor desc = null;
                    while ((line = br.readLine()) != null) {
                        OptionDescriptors options;
                        try {
                            options = (OptionDescriptors) Class.forName(line).newInstance();
                            optionsDescriptorsList.add(options);
                            if (desc == null) {
                                desc = options.get(name);
                            }
                        } catch (Exception e) {
                            throw new InternalError("Error instantiating class " + line + " read from " + path, e);
                        }
                    }
                    if (desc != null) {
                        return desc;
                    }
                }
            } catch (IOException e) {
                throw new InternalError("Error reading " + path, e);
            }
        }
        return null;
    }
}
