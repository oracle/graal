/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.zipfile;

import java.util.Enumeration;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.stream.Stream;

import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

import jdk.internal.perf.PerfCounter;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.misc.JavaUtilZipFileAccess;

final class ZipFileUtil {

    static void setJavaUtilZipFileAccess() {
        SharedSecrets.setJavaUtilZipFileAccess(
                        // SVM start
                        new JavaUtilZipFileAccess() {
                            @Override
                            public boolean startsWithLocHeader(java.util.zip.ZipFile zip) {
                                return KnownIntrinsics.unsafeCast(zip, ZipFile.class).zsrc.startsWithLoc;
                            }

                            @Override
                            public String[] getMetaInfEntryNames(java.util.zip.ZipFile zip) {
                                return KnownIntrinsics.unsafeCast(zip, ZipFile.class).getMetaInfEntryNames();
                            }

                            @Override
                            public JarEntry getEntry(java.util.zip.ZipFile zip, String name,
                                            Function<String, JarEntry> func) {
                                ZipEntry ze = KnownIntrinsics.unsafeCast(zip, ZipFile.class).getEntry0(name, func);
                                return (JarEntry) KnownIntrinsics.unsafeCast(ze, java.util.zip.ZipEntry.class);
                            }

                            @Override
                            public Enumeration<JarEntry> entries(java.util.zip.ZipFile zip,
                                            Function<String, JarEntry> func) {
                                throw VMError.unimplemented();
                            }

                            @Override
                            public Stream<JarEntry> stream(java.util.zip.ZipFile zip,
                                            Function<String, JarEntry> func) {
                                throw VMError.unimplemented();
                            }

                            @Override
                            public Stream<String> entryNameStream(java.util.zip.ZipFile zip) {
                                throw VMError.unimplemented();
                            }
                        });
    }

    static void updateZipFileCounters(long startZipFileOpen) {
        PerfCounter.getZipFileOpenTime().addElapsedTimeFrom(startZipFileOpen);
        PerfCounter.getZipFileCount().increment();
    }
}
