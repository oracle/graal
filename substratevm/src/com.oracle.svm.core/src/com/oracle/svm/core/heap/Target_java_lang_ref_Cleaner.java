/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import java.lang.ref.Cleaner;
import java.lang.ref.ReferenceQueue;

import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.misc.InnocuousThread;

@TargetClass(className = "java.lang.ref.Cleaner")
public final class Target_java_lang_ref_Cleaner {
    @Alias//
    Target_jdk_internal_ref_CleanerImpl impl;
}

@TargetClass(className = "java.lang.ref.Cleaner$Cleanable")
final class Target_java_lang_ref_Cleaner_Cleanable {
    @AnnotateOriginal
    @NeverInline("Ensure that every exception can be caught, including implicit exceptions.")
    native void clean();
}

@TargetClass(className = "jdk.internal.ref.CleanerImpl")
final class Target_jdk_internal_ref_CleanerImpl {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "jdk.internal.ref.CleanerImpl$CleanableList")//
    Target_jdk_internal_ref_CleanerImpl_CleanableList activeList;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "java.lang.ref.ReferenceQueue")//
    public ReferenceQueue<Object> queue;

    @TargetElement(name = "run")
    @Substitute
    public void run() {
        Thread t = Thread.currentThread();
        InnocuousThread mlThread = (t instanceof InnocuousThread) ? (InnocuousThread) t : null;
        while (!activeList.isEmpty()) {
            if (mlThread != null) {
                mlThread.eraseThreadLocals();
            }
            try {
                Cleaner.Cleanable ref = (Cleaner.Cleanable) queue.remove(60 * 1000L);
                if (ref != null) {
                    ref.clean();
                }
            } catch (Throwable e) {
                if (VMThreads.isTearingDown()) {
                    return;
                }
            }
        }
    }
}

@TargetClass(className = "jdk.internal.ref.PhantomCleanable")
final class Target_jdk_internal_ref_PhantomCleanable {
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = GetCleanableListSingletonTransformer.class) //
    Target_jdk_internal_ref_CleanerImpl_CleanableList list;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    Target_jdk_internal_ref_CleanerImpl_CleanableList_Node node;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ResetToMinusOneTransformer.class) //
    int index;

    @AnnotateOriginal
    @NeverInline("Ensure that every exception can be caught, including implicit exceptions.")
    /* final */ native void clean();
}

@TargetClass(className = "jdk.internal.ref.CleanerImpl$CleanableList")
final class Target_jdk_internal_ref_CleanerImpl_CleanableList {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "jdk.internal.ref.CleanerImpl$CleanableList$Node") //
    Target_jdk_internal_ref_CleanerImpl_CleanableList_Node head;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    Target_jdk_internal_ref_CleanerImpl_CleanableList_Node cache;

    @Alias
    native boolean isEmpty();

}

@TargetClass(className = "jdk.internal.ref.CleanerImpl$CleanableList$Node")
final class Target_jdk_internal_ref_CleanerImpl_CleanableList_Node {
}

final class HolderObjectFieldTransformer implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        return receiver;
    }
}

final class Target_jdk_internal_ref_CleanerImpl_CleanableList_Singleton {
    static final Object list = ReflectionUtil.newInstance(ReflectionUtil.lookupClass("jdk.internal.ref.CleanerImpl$CleanableList"));
}

final class GetCleanableListSingletonTransformer implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        return Target_jdk_internal_ref_CleanerImpl_CleanableList_Singleton.list;
    }
}

final class ResetToMinusOneTransformer implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        return -1;
    }
}
