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

import java.lang.reflect.Modifier;
import java.nio.file.spi.FileTypeDetector;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.FileTypeDetectorSupport;

@AutomaticFeature
public class ImageClasspathFileTypeDetectorFeature implements Feature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return FileTypeDetectorSupport.Options.AddAllFileTypeDetectors.getValue();
    }

    @SuppressWarnings("try")
    @Override
    public void beforeAnalysis(Feature.BeforeAnalysisAccess access) {
        FeatureImpl.BeforeAnalysisAccessImpl config = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        DebugContext debugContext = config.getDebugContext();
        try (DebugContext.Scope s = debugContext.scope("registerFileTypeDetectors")) {
            for (Class<? extends FileTypeDetector> detector : config.findSubclasses(FileTypeDetector.class)) {
                if (detector == FileTypeDetectorSupport.AlwaysNullFileTypeDetector.class) {
                    continue;
                }
                if (Modifier.isAbstract(detector.getModifiers())) {
                    continue;
                }
                try {
                    FileTypeDetectorSupport.addFirst(detector.getDeclaredConstructor().newInstance());
                    debugContext.log("registerFileTypeDetectors: Registered FileTypeDetector " + detector.getName());
                } catch (Exception ex) {
                    debugContext.log("registerFileTypeDetectors: FileTypeDetector " + detector.getName() + " is not default constructible");
                }
            }
        }
    }
}
