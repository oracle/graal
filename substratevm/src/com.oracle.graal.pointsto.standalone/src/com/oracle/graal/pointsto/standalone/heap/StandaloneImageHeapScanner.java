/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.pointsto.heap.ImageHeap;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.UninitializedStaticFieldValueReader;
import com.oracle.graal.pointsto.util.AnalysisError;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;

public class StandaloneImageHeapScanner extends ImageHeapScanner {
    private ClassLoader classLoader;

    public StandaloneImageHeapScanner(BigBang bb, ImageHeap heap, AnalysisMetaAccess aMetaAccess, SnippetReflectionProvider aSnippetReflection, ConstantReflectionProvider aConstantReflection,
                    ObjectScanningObserver aScanningObserver,
                    ClassLoader classLoader) {
        super(bb, heap, aMetaAccess, aSnippetReflection, aConstantReflection, aScanningObserver);
        this.classLoader = classLoader;
    }

    @Override
    protected Class<?> getClass(String className) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            AnalysisError.shouldNotReachHere(e);
            return null;
        }
    }

    @Override
    protected ValueSupplier<JavaConstant> readHostedFieldValue(AnalysisField field, JavaConstant receiver) {
        ValueSupplier<JavaConstant> ret = super.readHostedFieldValue(field, receiver);
        if (ret.get() == null && field.isStatic()) {
            JavaConstant constant = UninitializedStaticFieldValueReader.readUninitializedStaticValue(field, value -> universe.getSnippetReflection().forObject(value));
            return ValueSupplier.eagerValue(constant);
        } else {
            return ret;
        }
    }
}
