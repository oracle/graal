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
package com.oracle.svm.hosted.javafx;

import java.util.Objects;
import java.util.stream.Stream;

import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.hosted.FeatureImpl;

final class JavaFXFeature implements Feature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return getJavaFXApplication(access) != null;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        /*
         * Include subclasses of javafx.application.Application for reflection so users don't have
         * to do it every time.
         *
         * See javafx.application.Application#launch(java.lang.String...).
         */
        ((FeatureImpl.BeforeAnalysisAccessImpl) access)
                        .findSubclasses(getJavaFXApplication(access))
                        .forEach(RuntimeReflection::register);

        /*
         * Static initializers that are not supported in JavaFX.
         */
        Stream.of("com.sun.javafx.tk.quantum.QuantumRenderer", "javafx.stage.Screen", "com.sun.javafx.scene.control.behavior.BehaviorBase", "com.sun.javafx.scene.control.skin.BehaviorSkinBase",
                        "javafx.scene.control.SkinBase", "javafx.scene.control.Control", "javafx.scene.control.PopupControl", "javafx.scene.control.SkinBase$StyleableProperties",
                        "javafx.scene.control.Labeled$StyleableProperties", "javafx.scene.control.SkinBase$StyleableProperties", "com.sun.javafx.tk.quantum.PrismImageLoader2$AsyncImageLoader",
                        "com.sun.javafx.tk.quantum.PrismImageLoader2", "com.sun.prism.PresentableState")
                        .map(access::findClassByName)
                        .filter(Objects::nonNull)
                        .forEach(RuntimeClassInitialization::initializeAtRunTime);
    }

    private static Class<?> getJavaFXApplication(FeatureAccess access) {
        return access.findClassByName("javafx.application.Application");
    }

}
