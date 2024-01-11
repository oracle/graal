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
package com.oracle.svm.core.code;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import jdk.graal.compiler.api.replacements.Fold;

public abstract class DynamicMethodAddressResolutionHeapSupport {

    @Fold
    public static boolean isEnabled() {
        return ImageSingletons.contains(DynamicMethodAddressResolutionHeapSupport.class);
    }

    @Fold
    public static DynamicMethodAddressResolutionHeapSupport get() {
        return ImageSingletons.lookup(DynamicMethodAddressResolutionHeapSupport.class);
    }

    public abstract int initialize();

    public abstract UnsignedWord getRequiredPreHeapMemoryInBytes();

    public abstract int install(Pointer heapBase);
}
