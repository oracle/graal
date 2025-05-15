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

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.ObjectScanningObserver;
import com.oracle.graal.pointsto.heap.HostedValuesProvider;
import com.oracle.graal.pointsto.heap.ImageHeap;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;
import com.oracle.svm.hosted.reflect.ReflectionHostedSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;

public class SVMImageHeapScanner extends ImageHeapScanner {

    private final ImageClassLoader loader;
    private final Class<?> economicMapImpl;
    private final Field economicMapImplEntriesField;
    private final Field economicMapImplHashArrayField;
    private final Field economicMapImplTotalEntriesField;
    private final Field economicMapImplDeletedEntriesField;
    private final ReflectionHostedSupport reflectionSupport;
    private final FieldValueInterceptionSupport fieldValueInterceptionSupport;

    @SuppressWarnings("this-escape")
    public SVMImageHeapScanner(BigBang bb, ImageHeap imageHeap, ImageClassLoader loader, AnalysisMetaAccess metaAccess, SnippetReflectionProvider snippetReflection,
                    ConstantReflectionProvider aConstantReflection, ObjectScanningObserver aScanningObserver, HostedValuesProvider hostedValuesProvider) {
        super(bb, imageHeap, metaAccess, snippetReflection, aConstantReflection, aScanningObserver, hostedValuesProvider);
        this.loader = loader;
        economicMapImpl = getClass("org.graalvm.collections.EconomicMapImpl");
        economicMapImplEntriesField = ReflectionUtil.lookupField(economicMapImpl, "entries");
        economicMapImplHashArrayField = ReflectionUtil.lookupField(economicMapImpl, "hashArray");
        economicMapImplTotalEntriesField = ReflectionUtil.lookupField(economicMapImpl, "totalEntries");
        economicMapImplDeletedEntriesField = ReflectionUtil.lookupField(economicMapImpl, "deletedEntries");
        reflectionSupport = ImageSingletons.lookup(ReflectionHostedSupport.class);
        fieldValueInterceptionSupport = FieldValueInterceptionSupport.singleton();
    }

    @Override
    protected Class<?> getClass(String className) {
        return loader.findClassOrFail(className);
    }

    @Override
    protected ImageHeapConstant getOrCreateImageHeapConstant(JavaConstant javaConstant, ScanReason reason) {
        return super.getOrCreateImageHeapConstant(javaConstant, reason);
    }

    @Override
    public boolean isValueAvailable(AnalysisField field) {
        return fieldValueInterceptionSupport.isValueAvailable(field);
    }

    /**
     * Redirect static field reading through the {@link AnalysisConstantReflectionProvider}. This
     * provider first checks if a value for the field is available from the simulated-values
     * registry. If not, it reads from the shadow heap.
     */
    @Override
    public JavaConstant readStaticFieldValue(AnalysisField field) {
        AnalysisConstantReflectionProvider aConstantReflection = (AnalysisConstantReflectionProvider) this.constantReflection;
        return aConstantReflection.readValue(field, null, true, true);
    }

    @Override
    protected void rescanEconomicMap(EconomicMap<?, ?> map) {
        super.rescanEconomicMap(map);
        /* Make sure any EconomicMapImpl$CollisionLink objects are scanned. */
        if (map.getClass() == economicMapImpl) {
            rescanField(map, economicMapImplEntriesField);
            rescanField(map, economicMapImplHashArrayField);
            rescanField(map, economicMapImplTotalEntriesField);
            rescanField(map, economicMapImplDeletedEntriesField);
        }

    }

    @Override
    protected void onObjectReachable(ImageHeapConstant imageHeapConstant, ScanReason reason, Consumer<ScanReason> onAnalysisModified) {
        super.onObjectReachable(imageHeapConstant, reason, onAnalysisModified);
        Object object = snippetReflection.asObject(Object.class, imageHeapConstant);
        if (object instanceof Field field) {
            reflectionSupport.registerHeapReflectionField(field, reason);
        } else if (object instanceof Executable executable) {
            reflectionSupport.registerHeapReflectionExecutable(executable, reason);
        } else if (object instanceof DynamicHub hub) {
            reflectionSupport.registerHeapDynamicHub(hub, reason);
        }
    }

    @Override
    protected void maybeRunInExecutor(CompletionExecutor.DebugContextRunnable task) {
        if (BuildPhaseProvider.isAnalysisStarted()) {
            super.maybeRunInExecutor(task);
        } else {
            /*
             * Before the analysis is started post all scanning tasks to the executor. They will be
             * executed after the analysis starts.
             */
            bb.postTask(task);
        }
    }
}
