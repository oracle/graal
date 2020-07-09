/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jdk;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.annotate.AutomaticFeature;

@AutomaticFeature
public class JDKInitializationFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {

        RuntimeClassInitialization.initializeAtBuildTime("com.sun.crypto.provider", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("com.sun.java.util.jar.pack", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("com.sun.management", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("com.sun.naming.internal", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("com.sun.net.ssl", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("com.sun.nio.file", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("com.sun.nio.sctp", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("com.sun.nio.zipfs", "Native Image classes are always initialized at build time");

        RuntimeClassInitialization.initializeAtBuildTime("java.io", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("java.lang", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("java.math", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("java.net", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("java.nio", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("java.text", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("java.time", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("java.util", "Core JDK classes are initialized at build time");

        RuntimeClassInitialization.initializeAtBuildTime("javax.annotation.processing", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("javax.crypto", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("javax.lang.model", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("javax.management", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("javax.naming", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("javax.net", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("javax.tools", "Core JDK classes are initialized at build time");

        RuntimeClassInitialization.initializeAtBuildTime("jdk.internal", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("jdk.jfr", "Needed for Native Image substitutions");
        RuntimeClassInitialization.initializeAtBuildTime("jdk.net", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("jdk.nio", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("jdk.vm.ci", "Native Image classes are always initialized at build time");

        RuntimeClassInitialization.initializeAtBuildTime("sun.invoke", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("sun.launcher", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("sun.management", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("sun.misc", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("sun.net", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("sun.nio", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("sun.reflect", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("sun.text", "Core JDK classes are initialized at build time");
        RuntimeClassInitialization.initializeAtBuildTime("sun.util", "Core JDK classes are initialized at build time");

        /* Minor fixes to make the list work */
        RuntimeClassInitialization.initializeAtRunTime("com.sun.naming.internal.ResourceManager$AppletParameter", "Initializes AWT");
        RuntimeClassInitialization.initializeAtBuildTime("java.awt.font.TextAttribute", "Required for sun.text.bidi.BidiBase.NumericShapings");
        RuntimeClassInitialization.initializeAtBuildTime("java.awt.font.NumericShaper", "Required for sun.text.bidi.BidiBase.NumericShapings");
    }
}
