/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.image;

import com.oracle.svm.core.UniqueShortNameProvider;
import com.oracle.svm.core.UniqueShortNameProviderDefaultImpl;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.hosted.FeatureImpl;
import org.graalvm.nativeimage.ImageSingletons;

import java.util.List;

/**
 * An automatic feature class which ensures that the Linux debug unique short name provider is
 * registered when generating debug info for Linux.
 */
@AutomaticallyRegisteredFeature
@SuppressWarnings("unused")
class NativeImageDebugInfoFeature implements InternalFeature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (!UniqueShortNameProviderDefaultImpl.UseDefault.useDefaultProvider()) {
            if (!ImageSingletons.contains(UniqueShortNameProvider.class)) {
                // configure a BFD mangler to provide unique short names for method and field
                // symbols
                FeatureImpl.AfterRegistrationAccessImpl accessImpl = (FeatureImpl.AfterRegistrationAccessImpl) access;
                // the Graal system loader will not duplicate JDK builtin loader classes
                ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
                // the Graal app loader and image loader and their parent loader will not duplicate
                // classes
                ClassLoader appLoader = accessImpl.getApplicationClassLoader();
                ClassLoader imageLoader = accessImpl.getImageClassLoader().getClassLoader();
                ClassLoader imageLoaderParent = imageLoader.getParent();
                // the app and image loader should both have the same parent
                assert imageLoaderParent == appLoader.getParent();
                // ensure the mangle ignores prefix generation for Graal loaders
                List<ClassLoader> ignored = List.of(systemLoader, imageLoaderParent, appLoader, imageLoader);
                ImageSingletons.add(UniqueShortNameProvider.class, new NativeImageBFDNameProvider(ignored));
            }
        }
    }
}
