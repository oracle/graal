/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.jfr.Configuration;
import jdk.jfr.internal.jfc.JFC;

@TargetClass(value = jdk.jfr.internal.jfc.JFC.class, onlyWith = HasJfrSupport.class)
@SuppressWarnings("unused")
public final class Target_jdk_jfr_internal_jfc_JFC {

    // Checkstyle: stop
    @Delete //
    private static Path JFC_DIRECTORY;
    // Checkstyle: resume

    @Substitute
    public static List<Configuration> getConfigurations() {
        return new ArrayList<>(SubstrateJVM.getKnownConfigurations());
    }

    @Substitute
    public static Configuration createKnown(String name) throws IOException, ParseException {
        Path localPath = Paths.get(name);
        String jfcName = JFC.nameFromPath(localPath);

        // Check if this is a pre-parsed known configuration.
        for (Configuration config : SubstrateJVM.getKnownConfigurations()) {
            if (config.getName().equals(jfcName)) {
                return config;
            }
        }

        // Try to read the configuration from a file.
        try (Reader r = Files.newBufferedReader(localPath)) {
            return Target_jdk_jfr_internal_jfc_JFCParser.createConfiguration(jfcName, r);
        }
    }

    @Substitute
    public static Configuration getPredefined(String name) throws IOException, ParseException {
        for (Configuration config : SubstrateJVM.getKnownConfigurations()) {
            if (config.getName().equals(name)) {
                return config;
            }
        }
        throw new NoSuchFileException("Could not locate configuration with name " + name);
    }
}
