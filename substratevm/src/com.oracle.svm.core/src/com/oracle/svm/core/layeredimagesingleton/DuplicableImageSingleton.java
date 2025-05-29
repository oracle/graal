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
package com.oracle.svm.core.layeredimagesingleton;

/**
 * A Duplicable ImageSingleton can have multiple instances of the object installed in the Image Heap
 * (at most one per a layer). The specific instance referred to from a given piece of code is
 * dependent on the layer in which the code was installed in.
 *
 * It is expected that either the installed objects (1) have no instance fields or (2) have instance
 * fields which have been made layer-aware through other means (e.g. using a layered ImageHeapMap).
 *
 * Note this is a temporary marker and eventually all instances of {@link DuplicableImageSingleton}s
 * should be removed. This marker should only be used when there is not a correctness issue with
 * installing multiple instances of the singleton. Instead, the marker indicates there is merely a
 * performance/memory overhead due to having multiple copies of this singleton installed (via
 * different layers) within the image heap.
 */
public interface DuplicableImageSingleton extends LayeredImageSingleton {
}
