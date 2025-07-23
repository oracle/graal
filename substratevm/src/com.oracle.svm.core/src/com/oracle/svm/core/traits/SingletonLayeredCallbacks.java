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
package com.oracle.svm.core.traits;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton.PersistFlags;
import com.oracle.svm.core.util.VMError;

/**
 * This class contains actions which can be called on singletons during a layered image build.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public abstract class SingletonLayeredCallbacks {

    /**
     * Used to recreate a singleton across layers.
     *
     * <p>
     * To explain in more detail, consider a two-layered configuration, where "I" is the initial
     * layer and "A" is the application layer. If, while building I, singleton "S" specifies that it
     * wants to be created during startup of the next layer (via returning
     * {@link PersistFlags#CREATE} from its {@link #doPersist} callback), then it will also provide
     * a {@link LayeredSingletonInstantiator} "LI" to use via {@link #getSingletonInstantiator}.
     *
     * <p>
     * Now while building layer A, in order to create S in this layer, the native image generator
     * will initialize LI and will then call {@link #createFromLoader} to attain the S to be used in
     * layer A.
     */
    public interface LayeredSingletonInstantiator {
        Object createFromLoader(ImageSingletonLoader loader);
    }

    /**
     * When {@link ImageLayerBuildingSupport#buildingSharedLayer()} is true, this method is called
     * at the end of native image generation to perform any needed final actions. The method's
     * return value also specifies what actions should be taken at the startup of the next layer.
     */
    public abstract PersistFlags doPersist(ImageSingletonWriter writer, Object singleton);

    /**
     * If {@link #doPersist} returns {@link PersistFlags#CREATE}, then this method is called to
     * determine how to instantiate the singleton in the next layer.
     */
    public Class<? extends LayeredSingletonInstantiator> getSingletonInstantiator() {
        throw VMError.shouldNotReachHere("getSingletonInstantiator is not implemented. This method must only be implemented if doPersist returns PersistFlag.CREATE");
    }

}
