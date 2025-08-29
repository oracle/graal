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
package com.oracle.svm.core.debug;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.svm.core.BuildPhaseProvider.AfterAnalysis;
import com.oracle.svm.core.heap.UnknownObjectField;

import jdk.graal.compiler.api.replacements.Fold;

public class SubstrateDebugTypeEntrySupport {
    /**
     * Stores type entries produced from {@code ElementInfo} after analysis for later use during
     * debug info generation. We can't get ElementInfo at run-time, but we can reuse the type
     * entries produced during the native image build for run-time debug info generation.
     */
    @UnknownObjectField(availability = AfterAnalysis.class, fullyQualifiedTypes = {"java.util.HashMap", "java.util.ImmutableCollections$MapN", "java.util.ImmutableCollections$Map1"}) //
    private Map<Long, TypeEntry> typeEntryMap = new HashMap<>();

    @Fold
    public static SubstrateDebugTypeEntrySupport singleton() {
        return ImageSingletons.lookup(SubstrateDebugTypeEntrySupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    public void addTypeEntry(TypeEntry type) {
        assert typeEntryMap instanceof HashMap<Long, TypeEntry>;
        this.typeEntryMap.put(type.getTypeSignature(), type);
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    public void trim() {
        typeEntryMap = Map.copyOf(typeEntryMap);
    }

    public TypeEntry getTypeEntry(Long typeSignature) {
        return typeEntryMap.get(typeSignature);
    }

}
