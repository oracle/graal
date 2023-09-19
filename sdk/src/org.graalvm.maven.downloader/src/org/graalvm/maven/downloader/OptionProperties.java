/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
                String progName = ProcessProperties.getExecutableName();
                return Paths.get(progName).resolve("..").resolve(RELATIVE_OUTPUT_DIR).normalize().toString();
            }
        }
        return "maven downloader output";
    }
}
