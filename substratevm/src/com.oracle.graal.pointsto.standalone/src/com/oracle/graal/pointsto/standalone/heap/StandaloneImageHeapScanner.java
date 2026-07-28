/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.heap;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanningObserver;
import com.oracle.graal.pointsto.heap.HostedValuesProvider;
import com.oracle.graal.pointsto.heap.ImageHeap;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Standalone heap scanner that consults the standalone hosted-values availability layer before
 * snapshotting hosted values.
 */
public class StandaloneImageHeapScanner extends ImageHeapScanner {
    public StandaloneImageHeapScanner(BigBang bb, ImageHeap heap, AnalysisMetaAccess aMetaAccess, SnippetReflectionProvider aSnippetReflection,
                    ConstantReflectionProvider aConstantReflection, ObjectScanningObserver aScanningObserver,
                    HostedValuesProvider hostedValuesProvider) {
        super(bb, heap, aMetaAccess, aSnippetReflection, aConstantReflection, aScanningObserver, hostedValuesProvider);
    }

    /**
     * Standalone guest reads may temporarily report "value not available yet" by returning a Java
     * {@code null} reference from guest constant reflection. The generic scanner only asks the heap
     * scanner for availability, so re-probe through the standalone hosted-values path while a field
     * is still backed by the hosted guest object. Pure shadow-heap receivers keep the default eager
     * behavior because their values are already represented by image-heap tasks.
     */
    @Override
    public boolean isValueAvailable(AnalysisField field, JavaConstant receiver) {
        JavaConstant hostedReceiver = getHostedReceiver(receiver);
        if (receiver == null || hostedReceiver != null) {
            return readHostedFieldValue(field, hostedReceiver).isAvailable();
        }
        return super.isValueAvailable(field, receiver);
    }

    private static JavaConstant getHostedReceiver(JavaConstant receiver) {
        if (receiver instanceof ImageHeapConstant imageHeapConstant && imageHeapConstant.isBackedByHostedObject()) {
            return imageHeapConstant.getHostedObject();
        }
        if (receiver instanceof ImageHeapConstant) {
            return null;
        }
        return receiver;
    }
}
