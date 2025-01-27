/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.SubstrateOptions.InitializeJDKAtBuildTimeMigration;
import static com.oracle.svm.hosted.jdk.JDKInitializationFeature.JDK_CLASS_REASON;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;

@AutomaticallyRegisteredFeature
public class JDKInitializationMigrationFeature implements InternalFeature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        RuntimeClassInitializationSupport rci = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
        if (InitializeJDKAtBuildTimeMigration.getValue()) {
            /* Place all excluded entries from JDKInitializationFeature here. */
            rci.initializeAtBuildTime("jdk.xml", JDK_CLASS_REASON);
            /*
             * The XML classes have cyclic class initializer dependencies, and class initialization
             * can deadlock/fail when initialization is started at the "wrong part" of the cycle.
             * Force-initializing the correct class of the cycle here, in addition to the
             * "whole package" initialization above, breaks the cycle because it triggers immediate
             * initialization here before the static analysis is started.
             */
            rci.initializeAtBuildTime("jdk.xml.internal.JdkXmlUtils", JDK_CLASS_REASON);
            /* XML-related */
            rci.initializeAtBuildTime("com.sun.xml", JDK_CLASS_REASON);
            rci.initializeAtBuildTime("com.sun.org.apache", JDK_CLASS_REASON);
            rci.initializeAtRunTime("com.sun.org.apache.xml.internal.serialize.HTMLdtd", "Fails build-time initialization");
            rci.initializeAtBuildTime("com.sun.org.slf4j.internal", JDK_CLASS_REASON);
        }
    }
}
