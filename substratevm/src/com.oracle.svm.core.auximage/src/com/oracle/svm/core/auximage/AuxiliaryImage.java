/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.auximage;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolates.CreateIsolateParameters;

/**
 * An auxiliary image persists a part of the runtime state of a native image during its execution.
 * An object of this class represents an auxiliary image after its persisted state has been
 * restored. Similar to {@link ImageSingletons}, this is a key-value registry that associates a
 * {@link Class} object with an instance of the class (or interface) that it represents.
 * <p>
 * {@link AuxiliaryImageBuilder} is used to create, populate and subsequently persist an image.
 * {@link AuxiliaryImageLoader} is used for loading and unloading a persisted auxiliary image.
 * Auxiliary images can also be loaded {@linkplain CreateIsolateParameters during isolate creation}.
 * <p>
 * Auxiliary images are tightly coupled with the native image from which they were captured. Loading
 * a persisted auxiliary image during execution of a different native image could result in
 * undefined behavior and is not supported.
 */
public interface AuxiliaryImage {

    /**
     * Look up a singleton in the registry. The key must {@linkplain #contains exist} in the image.
     */
    <T> T lookup(Class<T> key);

    /** Determines whether a singleton is present in the registry. */
    boolean contains(Class<?> key);
}
