/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.meta;

import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.ObjectScanner.OtherReason;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.graal.meta.SubstrateSnippetReflectionProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;

import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public class HostedSnippetReflectionProvider extends SubstrateSnippetReflectionProvider {
    private ImageHeapScanner heapScanner;

    public HostedSnippetReflectionProvider(ImageHeapScanner heapScanner, WordTypes wordTypes) {
        super(wordTypes);
        this.heapScanner = heapScanner;
    }

    public void setHeapScanner(ImageHeapScanner heapScanner) {
        this.heapScanner = heapScanner;
    }

    @Override
    public JavaConstant forObject(Object object) {
        if (object instanceof RelocatedPointer pointer) {
            /* Relocated pointers are subject to relocation, so we don't know their value yet. */
            return new RelocatableConstant(pointer);
        } else if (object instanceof WordBase word) {
            return JavaConstant.forIntegerKind(FrameAccess.getWordKind(), word.rawValue());
        }
        AnalysisConstantReflectionProvider.validateRawObjectConstant(object);
        /* Redirect constant lookup through the shadow heap. */
        return heapScanner.createImageHeapConstant(super.forObject(object), OtherReason.UNKNOWN);
    }

    @Override
    public JavaConstant forBoxed(JavaKind kind, Object value) {
        if (kind == JavaKind.Object) {
            return forObject(value);
        }
        return JavaConstant.forBoxedPrimitive(value);
    }

    @Override
    public <T> T asObject(Class<T> type, JavaConstant c) {
        JavaConstant constant = c;
        if (constant instanceof RelocatableConstant relocatable) {
            return type.cast(relocatable.getPointer());
        }
        if (constant instanceof ImageHeapConstant imageHeapConstant) {
            constant = imageHeapConstant.getHostedObject();
            if (constant == null) {
                /* Simulated image heap object without a hosted backing object. */
                return null;
            }
        }

        if (type == Class.class && constant instanceof SubstrateObjectConstant) {
            /* Only unwrap the DynamicHub if a Class object is required explicitly. */
            if (SubstrateObjectConstant.asObject(constant) instanceof DynamicHub hub) {
                return type.cast(hub.getHostedJavaClass());
            }
        }
        return super.asObject(type, constant);
    }
}
