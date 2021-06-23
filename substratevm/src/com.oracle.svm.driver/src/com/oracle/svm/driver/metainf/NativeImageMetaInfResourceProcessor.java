/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver.metainf;

import java.nio.file.Path;

public interface NativeImageMetaInfResourceProcessor {
    /**
     * Process a single file located under the native-image META-INF directory.
     *
     * @param classpathEntry Classpath entry from which the file originated.
     * @param resourceRoot Parent of the META-INF/native-image directory from which the file
     *            originated.
     * @param resourcePath Path to the file.
     * @param type Type of the file.
     */
    void processMetaInfResource(Path classpathEntry, Path resourceRoot, Path resourcePath, MetaInfFileType type) throws Exception;

    void showWarning(String message);

    void showVerboseMessage(String message);

    boolean isExcluded(Path resourcePath, Path classpathEntry);
}
