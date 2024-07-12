/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.imagelayer;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;

/**
 * Registry to manage cross-layer constant references.
 */
public interface CrossLayerConstantRegistry {

    static CrossLayerConstantRegistry singletonOrNull() {
        if (ImageSingletons.contains(CrossLayerConstantRegistry.class)) {
            return ImageSingletons.lookup(CrossLayerConstantRegistry.class);
        }

        return null;
    }

    /**
     * Retrieves the constant associated with {@code keyName}. If a constant does not exist then an
     * error is thrown.
     */
    ImageHeapConstant getConstant(String keyName);

    /**
     * Checks whether a constant for {@code keyName} was stored in a prior layer.
     */
    boolean constantExists(String keyName);

    /**
     * Registers a value which may be stored in this layer's heap. If the value is stored in the
     * heap, later layers can access it via {@link #getConstant} and check whether it exists via
     * {@link #constantExists}.
     */
    void registerConstantCandidate(String keyName, Object obj);
}
