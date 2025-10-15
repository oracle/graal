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
package com.oracle.svm.hosted.gc.shenandoah;

import static com.oracle.svm.hosted.gc.shared.NativeGCAccessedFields.CLOSED_TYPE_WORLD_HUB_LAYOUT;
import static com.oracle.svm.hosted.gc.shared.NativeGCAccessedFields.ContinuationsSupported;
import static com.oracle.svm.hosted.gc.shared.NativeGCAccessedFields.OPEN_TYPE_WORLD_HUB_LAYOUT;
import static com.oracle.svm.hosted.gc.shared.NativeGCAccessedFields.USE_PERF_DATA;

import java.lang.ref.SoftReference;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.svm.core.code.RuntimeCodeInfoMemory;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.Target_java_lang_ref_Reference;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jvmstat.PerfLong;
import com.oracle.svm.core.jvmstat.PerfStringVariable;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.thread.VMOperationControl.VMOperationThread;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.hosted.gc.shared.NativeGCAccessedFields.AccessedClass;
import com.oracle.svm.hosted.gc.shared.NativeGCAccessedFields.FieldAccessKind;
import com.oracle.svm.hosted.gc.shared.NativeGCAccessedFields.InstanceField;
import com.oracle.svm.hosted.gc.shared.NativeGCAccessedFields.StaticField;

public class ShenandoahAccessedFields {
    static final AccessedClass[] ACCESSED_CLASSES = {
                    new AccessedClass(VMThreads.class,
                                    new StaticField("head", FieldAccessKind.READ),
                                    new StaticField("numAttachedThreads", FieldAccessKind.READ)),
                    new AccessedClass(PlatformThreads.class,
                                    new StaticField("nonDaemonThreads", FieldAccessKind.READ)),
                    new AccessedClass(VMOperationThread.class,
                                    new InstanceField("isolateThread", FieldAccessKind.READ)),
                    new AccessedClass(Safepoint.class,
                                    new InstanceField("safepointState", FieldAccessKind.READ),
                                    new InstanceField("safepointId", FieldAccessKind.READ)),
                    new AccessedClass(DynamicHub.class,
                                    new InstanceField("name", FieldAccessKind.READ),
                                    new InstanceField("hubType", FieldAccessKind.READ),
                                    new InstanceField("referenceType", FieldAccessKind.READ),
                                    new InstanceField("layoutEncoding", FieldAccessKind.READ),
                                    new InstanceField("componentType", FieldAccessKind.READ),
                                    new InstanceField("referenceMapCompressedOffset", FieldAccessKind.READ),
                                    new InstanceField("typeCheckStart", FieldAccessKind.READ, CLOSED_TYPE_WORLD_HUB_LAYOUT),
                                    new InstanceField("typeCheckRange", FieldAccessKind.READ, CLOSED_TYPE_WORLD_HUB_LAYOUT),
                                    new InstanceField("typeCheckSlot", FieldAccessKind.READ, CLOSED_TYPE_WORLD_HUB_LAYOUT),
                                    new InstanceField("typeID", FieldAccessKind.READ, OPEN_TYPE_WORLD_HUB_LAYOUT),
                                    new InstanceField("typeIDDepth", FieldAccessKind.READ, OPEN_TYPE_WORLD_HUB_LAYOUT),
                                    new InstanceField("numClassTypes", FieldAccessKind.READ, OPEN_TYPE_WORLD_HUB_LAYOUT),
                                    new InstanceField("numIterableInterfaceTypes", FieldAccessKind.READ, OPEN_TYPE_WORLD_HUB_LAYOUT),
                                    new InstanceField("openTypeWorldTypeCheckSlots", FieldAccessKind.READ, OPEN_TYPE_WORLD_HUB_LAYOUT),
                                    new InstanceField("interfaceID", FieldAccessKind.READ, OPEN_TYPE_WORLD_HUB_LAYOUT),
                                    new InstanceField("openTypeWorldInterfaceHashTable", FieldAccessKind.READ, OPEN_TYPE_WORLD_HUB_LAYOUT),
                                    new InstanceField("openTypeWorldInterfaceHashParam", FieldAccessKind.READ, OPEN_TYPE_WORLD_HUB_LAYOUT)),
                    new AccessedClass(String.class,
                                    new InstanceField("value", FieldAccessKind.READ),
                                    new InstanceField("coder", FieldAccessKind.READ)),
                    new AccessedClass(AtomicInteger.class,
                                    new InstanceField("value", FieldAccessKind.READ)),
                    new AccessedClass(Target_java_lang_ref_Reference.class,
                                    new InstanceField("referent", FieldAccessKind.READ_WRITE),
                                    new InstanceField("next", FieldAccessKind.READ_WRITE),
                                    new InstanceField("discovered", FieldAccessKind.READ_WRITE)),
                    new AccessedClass(SoftReference.class,
                                    new InstanceField("timestamp", FieldAccessKind.READ),
                                    new StaticField("clock", FieldAccessKind.READ_WRITE)),
                    new AccessedClass(StoredContinuation.class,
                                    new InstanceField("ip", FieldAccessKind.READ_WRITE, new ContinuationsSupported())),
                    new AccessedClass(RuntimeCodeInfoMemory.class,
                                    new InstanceField("table", FieldAccessKind.READ)),
                    new AccessedClass(PerfLong.class,
                                    new InstanceField("value", FieldAccessKind.READ_WRITE, USE_PERF_DATA)),
                    new AccessedClass(PerfStringVariable.class,
                                    new InstanceField("nullTerminatedValue", FieldAccessKind.READ, USE_PERF_DATA))
    };
}
