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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.ObjectScanningObserver;
import com.oracle.graal.pointsto.heap.ImageHeap;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.VarHandleFeature;
import com.oracle.svm.core.meta.DirectSubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;
import com.oracle.svm.hosted.classinitialization.SimulateClassInitializerSupport;
import com.oracle.svm.hosted.methodhandles.MethodHandleFeature;
import com.oracle.svm.hosted.reflect.ReflectionHostedSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.core.common.type.TypedConstant;
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
    private final Class<?> memberNameClass;
    private final MethodHandleFeature methodHandleSupport;
    private final Class<?> directMethodHandleClass;
    private final VarHandleFeature varHandleSupport;
    private final FieldValueInterceptionSupport fieldValueInterceptionSupport;

    @SuppressWarnings("this-escape")
    public SVMImageHeapScanner(BigBang bb, ImageHeap imageHeap, ImageClassLoader loader, AnalysisMetaAccess metaAccess,
                    SnippetReflectionProvider snippetReflection, ConstantReflectionProvider aConstantReflection, ObjectScanningObserver aScanningObserver) {
        super(bb, imageHeap, metaAccess, snippetReflection, aConstantReflection, aScanningObserver);
        this.loader = loader;
        economicMapImpl = getClass("org.graalvm.collections.EconomicMapImpl");
        economicMapImplEntriesField = ReflectionUtil.lookupField(economicMapImpl, "entries");
        economicMapImplHashArrayField = ReflectionUtil.lookupField(economicMapImpl, "hashArray");
        economicMapImplTotalEntriesField = ReflectionUtil.lookupField(economicMapImpl, "totalEntries");
        economicMapImplDeletedEntriesField = ReflectionUtil.lookupField(economicMapImpl, "deletedEntries");
        ImageSingletons.add(ImageHeapScanner.class, this);
        reflectionSupport = ImageSingletons.lookup(ReflectionHostedSupport.class);
        memberNameClass = getClass("java.lang.invoke.MemberName");
        methodHandleSupport = ImageSingletons.lookup(MethodHandleFeature.class);
        directMethodHandleClass = getClass("java.lang.invoke.DirectMethodHandle");
        varHandleSupport = ImageSingletons.lookup(VarHandleFeature.class);
        fieldValueInterceptionSupport = FieldValueInterceptionSupport.singleton();
    }

    public static ImageHeapScanner instance() {
        return ImageSingletons.lookup(ImageHeapScanner.class);
    }

    @Override
    protected Class<?> getClass(String className) {
        return loader.findClassOrFail(className);
    }

    @Override
    protected ImageHeapConstant getOrCreateImageHeapConstant(JavaConstant javaConstant, ScanReason reason) {
        VMError.guarantee(javaConstant instanceof TypedConstant, "Not a substrate constant: %s", javaConstant);
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
        JavaConstant constant = aConstantReflection.readValue(metaAccess, field, null, true);
        if (constant instanceof DirectSubstrateObjectConstant) {
            /*
             * The "late initialization" doesn't work with heap snapshots because the wrong value
             * will be snapshot for classes proven late, so we only read via the shadow heap if
             * simulation of class initializers is enabled. This branch will be removed when the old
             * initialization strategy is removed.
             */
            VMError.guarantee(!SimulateClassInitializerSupport.singleton().isEnabled());
            return toImageHeapObject(constant, ObjectScanner.OtherReason.UNKNOWN);
        }
        return constant;
    }

    @Override
    protected ValueSupplier<JavaConstant> readHostedFieldValue(AnalysisField field, JavaConstant receiver) {
        AnalysisConstantReflectionProvider aConstantReflection = (AnalysisConstantReflectionProvider) this.constantReflection;
        return aConstantReflection.readHostedFieldValue(field, receiver);
    }

    @Override
    protected JavaConstant transformFieldValue(AnalysisField field, JavaConstant receiverConstant, JavaConstant originalValueConstant) {
        return ((AnalysisConstantReflectionProvider) constantReflection).interceptValue(metaAccess, field, originalValueConstant);
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
        JavaConstant hostedObject = imageHeapConstant.getHostedObject();
        if (hostedObject != null) {
            Object object = snippetReflection.asObject(Object.class, hostedObject);
            if (object instanceof Field field) {
                reflectionSupport.registerHeapReflectionField(field, reason);
            } else if (object instanceof Executable executable) {
                reflectionSupport.registerHeapReflectionExecutable(executable, reason);
            } else if (object instanceof DynamicHub hub) {
                reflectionSupport.registerHeapDynamicHub(hub, reason);
            } else if (object instanceof VarHandle varHandle) {
                varHandleSupport.registerHeapVarHandle(varHandle);
            } else if (directMethodHandleClass.isInstance(object)) {
                varHandleSupport.registerHeapMethodHandle((MethodHandle) object);
            } else if (object instanceof MethodType methodType) {
                methodHandleSupport.registerHeapMethodType(methodType);
            } else if (memberNameClass.isInstance(object)) {
                methodHandleSupport.registerHeapMemberName((Member) object);
            }
        }
    }
}
