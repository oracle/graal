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
package com.oracle.svm.hosted;

import java.util.logging.LogManager;

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.RuntimeClassInitialization;
import org.graalvm.nativeimage.RuntimeReflection;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.util.VMError;

@AutomaticFeature
final class LoggingFeature implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        /* Ensure that the log manager is initialized and the initial configuration is read. */
        LogManager.getLogManager();

        /*
         * Rerunning the initialization of SimpleFormatter at run time is required so that
         * SimpleFormatter.format is correctly set to a custom provided value instead of the
         * LoggingSupport.DEFAULT_FORMAT default value.
         */

        RuntimeClassInitialization.rerunClassInitialization(java.util.logging.SimpleFormatter.class);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
            RuntimeReflection.register(java.util.logging.ConsoleHandler.class);
            RuntimeReflection.register(java.util.logging.ConsoleHandler.class.getConstructor());
            RuntimeReflection.register(java.util.logging.FileHandler.class);
            RuntimeReflection.register(java.util.logging.FileHandler.class.getConstructor());
            RuntimeReflection.register(java.util.logging.SimpleFormatter.class);
            RuntimeReflection.register(java.util.logging.SimpleFormatter.class.getConstructor());
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }
}

public class LoggingSupport {
}
