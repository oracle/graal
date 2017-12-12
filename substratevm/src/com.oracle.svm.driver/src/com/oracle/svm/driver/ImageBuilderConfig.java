/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver;

import java.nio.file.Path;
import java.util.function.Consumer;

public interface ImageBuilderConfig {

    boolean isRelease();

    Path getRootDir();

    Path getJavaHome();

    Path[] getTruffleLanguageJars(TruffleOptionHandler handler);

    void addImageBuilderClasspath(Path... classpaths);

    void addImageBuilderBootClasspath(Path... classpaths);

    void addImageBuilderJavaArgs(String... javaArgs);

    void addImageBuilderArgs(String... args);

    void addImageBuilderFeatures(String... names);

    void addImageBuilderSubstitutions(String... substitutions);

    void addImageBuilderResourceBundles(String... resourceBundles);

    void addImageClasspath(Path... classpath);

    void addCustomJavaArgs(String... javaArgs);

    void addCustomImageBuilderArgs(String... args);

    void addCustomImageClasspath(Path... classpath);

    void addTruffleLanguage(TruffleOptionHandler handler);

    void setVerbose(boolean val);

    void showVerboseMessage(String message);

    void showMessage(String message);

    void showWarning(String message);

    Error showError(String message);

    void forEachJarInDirectory(Path dir, Consumer<? super Path> action);
}
