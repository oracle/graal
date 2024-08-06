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
package com.oracle.svm.core;

import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.code.CodeCacheManagerMXBean;
import com.oracle.svm.core.code.CodeCachePoolMXBean;
import com.oracle.svm.core.jdk.management.ManagementSupport;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Keeps track of all GC-related MX beans. The actual implementation of this class is GC-specific.
 * Note that multiple instances of this class may be in the same image if the image contains more
 * than one garbage collector.
 */
public class GCRelatedMXBeans {
    protected final ManagementSupport.MXBeans beans = new ManagementSupport.MXBeans();

    @Platforms(Platform.HOSTED_ONLY.class)
    public GCRelatedMXBeans() {
        beans.addList(MemoryManagerMXBean.class, List.of(new CodeCacheManagerMXBean()));
        beans.addList(MemoryPoolMXBean.class, List.of(new CodeCachePoolMXBean.CodeAndDataPool(), new CodeCachePoolMXBean.NativeMetadataPool()));
    }

    @Fold
    public static ManagementSupport.MXBeans mxBeans() {
        return ImageSingletons.lookup(GCRelatedMXBeans.class).beans;
    }
}
