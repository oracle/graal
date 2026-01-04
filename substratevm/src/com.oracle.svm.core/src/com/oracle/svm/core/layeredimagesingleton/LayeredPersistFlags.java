/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.layeredimagesingleton;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.traits.SingletonLayeredCallbacks;

public enum LayeredPersistFlags {
    /**
     * Indicates nothing should be persisted for this singleton. A different singleton can be linked
     * to this key in a subsequent image layer.
     */
    NOTHING,
    /**
     * Indicates nothing should be persisted for this singleton and that a singleton cannot be
     * linked to this key in a subsequent image layer.
     */
    FORBIDDEN,
    /**
     * Indicates in a subsequent image a new singleton should be created and linked via calling
     * {@code Object createFromLoader(ImageSingletonLoader)}.
     */
    CREATE,
    /**
     * Indicates that when and/or if this singleton is registered in the next image via
     * {@link ImageSingletons#add}, then {@link SingletonLayeredCallbacks#onSingletonRegistration}
     * should be called on this singleton.
     * <p>
     * If a singleton is registered under multiple keys, then
     * {@link SingletonLayeredCallbacks#onSingletonRegistration} should only be called once and will
     * have finished executing before the singleton is installed into the registry.
     */
    CALLBACK_ON_REGISTRATION
}
