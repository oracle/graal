/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.heap;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.ObjectScanningObserver;
import com.oracle.graal.pointsto.heap.ImageHeap;
import com.oracle.graal.pointsto.heap.ImageHeapObject;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.ReadableJavaField;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;

public class SVMImageHeapScanner extends ImageHeapScanner {

    private final ImageClassLoader loader;
    protected HostedMetaAccess hostedMetaAccess;
    private final Class<?> economicMapImpl;
    private final Field economicMapImplEntriesField;
    private final Field economicMapImplHashArrayField;
    private final RuntimeReflectionSupport reflectionSupport;

    public SVMImageHeapScanner(ImageHeap imageHeap, ImageClassLoader loader, AnalysisMetaAccess metaAccess,
                    SnippetReflectionProvider snippetReflection, ConstantReflectionProvider aConstantReflection, ObjectScanningObserver aScanningObserver) {
        super(imageHeap, metaAccess, snippetReflection, aConstantReflection, aScanningObserver);
        this.loader = loader;
        economicMapImpl = getClass("org.graalvm.collections.EconomicMapImpl");
        economicMapImplEntriesField = ReflectionUtil.lookupField(economicMapImpl, "entries");
        economicMapImplHashArrayField = ReflectionUtil.lookupField(economicMapImpl, "hashArray");
        ImageSingletons.add(ImageHeapScanner.class, this);
        reflectionSupport = ImageSingletons.lookup(RuntimeReflectionSupport.class);
    }

    public static ImageHeapScanner instance() {
        return ImageSingletons.lookup(ImageHeapScanner.class);
    }

    public void setHostedMetaAccess(HostedMetaAccess hostedMetaAccess) {
        this.hostedMetaAccess = hostedMetaAccess;
    }

    @Override
    protected Class<?> getClass(String className) {
        return loader.findClassOrFail(className);
    }

    @Override
    protected ImageHeapObject getOrCreateConstantReachableTask(JavaConstant javaConstant, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        VMError.guarantee(javaConstant instanceof SubstrateObjectConstant, "Not a substrate constant " + javaConstant);
        return super.getOrCreateConstantReachableTask(javaConstant, reason, onAnalysisModified);
    }

    @Override
    protected Object unwrapObject(JavaConstant constant) {
        /*
         * Unwrap the original object from the constant. Unlike HostedSnippetReflectionProvider this
         * will just return the wrapped object, without any transformation. This is important during
         * scanning: when scanning a java.lang.Class it will be replaced by a DynamicHub which is
         * then actually scanned. The HostedSnippetReflectionProvider returns the original Class for
         * a DynamicHub, which would lead to a deadlock during scanning.
         */
        return SubstrateObjectConstant.asObject(Object.class, constant);
    }

    @Override
    public boolean isValueAvailable(AnalysisField field) {
        if (field.wrapped instanceof ReadableJavaField) {
            ReadableJavaField readableField = (ReadableJavaField) field.wrapped;
            return readableField.isValueAvailable();
        }
        return super.isValueAvailable(field);
    }

    @Override
    protected ValueSupplier<JavaConstant> readHostedFieldValue(AnalysisField field, JavaConstant receiver) {
        AnalysisConstantReflectionProvider aConstantReflection = (AnalysisConstantReflectionProvider) this.constantReflection;
        return aConstantReflection.readHostedFieldValue(field, hostedMetaAccess, receiver);
    }

    @Override
    protected JavaConstant transformFieldValue(AnalysisField field, JavaConstant receiverConstant, JavaConstant originalValueConstant) {
        return ((AnalysisConstantReflectionProvider) constantReflection).interceptValue(field, originalValueConstant);
    }

    @Override
    protected boolean skipScanning() {
        return BuildPhaseProvider.isAnalysisFinished();
    }

    @Override
    protected void rescanEconomicMap(EconomicMap<?, ?> map) {
        super.rescanEconomicMap(map);
        /* Make sure any EconomicMapImpl$CollisionLink objects are scanned. */
        if (map.getClass() == economicMapImpl) {
            rescanField(map, economicMapImplEntriesField);
            rescanField(map, economicMapImplHashArrayField);
        }

    }

    @Override
    protected void onObjectReachable(ImageHeapObject imageHeapObject) {
        super.onObjectReachable(imageHeapObject);

        Object object = SubstrateObjectConstant.asObject(imageHeapObject.getObject());
        if (object instanceof Field || object instanceof Executable) {
            reflectionSupport.registerHeapReflectionObject((AccessibleObject) object);
        } else if (object instanceof DynamicHub) {
            reflectionSupport.registerHeapDynamicHub(object);
        }
    }
}
