/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.maven.downloader;

import java.nio.file.Paths;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ProcessProperties;

class OptionProperties {
    static final String RELATIVE_OUTPUT_DIR = System.getProperty("org.graalvm.maven.downloader.relative_output_dir");
    static final String DEFAULT_VERSION = System.getProperty("org.graalvm.maven.downloader.default_version");
    static final String VERSION_PROP = "org.graalvm.maven.downloader.version";
    static final String DEFAULT_MAVEN_REPO = "https://repo1.maven.org/maven2/";
    static final String MAVEN_PROP = "org.graalvm.maven.downloader.repository";
    static final String DEFAULT_GROUP_ID = "org.graalvm.polyglot";

    public static String getDefaultGroup() {
        return DEFAULT_GROUP_ID;
    }

    public static String getDefaultRepo() {
        var repo = System.getenv(MAVEN_PROP);
        if (repo == null) {
            return DEFAULT_MAVEN_REPO;
        } else {
            return repo;
        }
    }

    public static String getDefaultVersion() {
        var ver = System.getenv(VERSION_PROP);
        if (ver == null) {
            if (DEFAULT_VERSION != null) {
                return DEFAULT_VERSION;
            } else {
                return "not specified";
            }
        } else {
            return ver;
        }
    }

    public static String getExeName() {
        if (ImageInfo.inImageRuntimeCode()) {
            if (ProcessProperties.getArgumentVectorBlockSize() > 0) {
                return ProcessProperties.getArgumentVectorProgramName();
            }
        }
        return "maven downloader";
    }

    public static String getDefaultOutputDir() {
        if (RELATIVE_OUTPUT_DIR != null) {
            if (ImageInfo.inImageRuntimeCode()) {
                if (ProcessProperties.getArgumentVectorBlockSize() > 0) {
                    String progName = ProcessProperties.getArgumentVectorProgramName();
                    return Paths.get(progName).resolve("..").resolve(RELATIVE_OUTPUT_DIR).normalize().toString();
                }
            }
        }
        return "maven downloader output";
    }
}
