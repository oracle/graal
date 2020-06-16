/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.image;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class ObjectGroupHistogram {
    private final NativeImageHeap heap;
    private final Map<ObjectInfo, String> groups;
    private final Map<String, HeapHistogram> groupHistograms;

    public static void print(NativeImageHeap heap) {
        new ObjectGroupHistogram(heap).doPrint();
    }

    private ObjectGroupHistogram(NativeImageHeap heap) {
        this.heap = heap;
        this.groups = new HashMap<>();
        this.groupHistograms = new LinkedHashMap<>();
    }

    public interface ObjectFilter {
        boolean test(ObjectInfo info, int recursionLevel);
    }

    public interface FieldFilter {
        boolean test(ObjectInfo info, HostedField field);
    }

    private static boolean filterCodeInfoObjects(@SuppressWarnings("unused") ObjectInfo info, int recursionLevel) {
        return recursionLevel <= 2;
    }

    private static boolean filterDynamicHubField(ObjectInfo info, HostedField field) {
        if (info.getObject() instanceof DynamicHub) {
            return field.getName().equals("name") || field.getName().equals("assignableFromMatches") || field.getName().equals("pointerMapEncoding");
        }
        return true;
    }

    private static boolean filterGraalSupportObjects(@SuppressWarnings("unused") ObjectInfo info, int recursionLevel) {
        return recursionLevel <= 1;
    }

    private static boolean filterObjectConstantField(ObjectInfo info, HostedField field) {
        if (info.getObject() instanceof SubstrateObjectConstant) {
            return !field.getName().equals("object");
        }
        return true;
    }

    private void doPrint() {
        /*
         * To group objects, we process certain known types and traverse objects reachable from
         * them. Custom filtering allows to exclude certain fields, in order to cut off the
         * processing. Once an object is assigned to a group, it never switches groups again. So the
         * order in which types are prcessed matters.
         */
        processType(DynamicHub.class, "DynamicHub", true, null, ObjectGroupHistogram::filterDynamicHubField);
        processObject(NonmovableArrays.getHostedArray(DynamicHubSupport.getReferenceMapEncoding()), "DynamicHub", true, null, null);
        processObject(CodeInfoTable.getImageCodeCache(), "ImageCodeInfo", true, ObjectGroupHistogram::filterCodeInfoObjects, null);

        processObject(readGraalSupportField("graphEncoding"), "CompressedGraph", true, ObjectGroupHistogram::filterGraalSupportObjects, null);
        processObject(readGraalSupportField("graphObjects"), "CompressedGraph", true, ObjectGroupHistogram::filterGraalSupportObjects, null);
        processObject(readGraalSupportField("graphNodeTypes"), "CompressedGraph", true, ObjectGroupHistogram::filterGraalSupportObjects, null);

        processType(ResolvedJavaType.class, "Graal Metadata", false, null, null);
        processType(ResolvedJavaMethod.class, "Graal Metadata", false, null, null);
        processType(ResolvedJavaField.class, "Graal Metadata", false, null, null);

        try {
            Field field = Class.forName("com.oracle.svm.graal.SubstrateRuntimeProvider").getDeclaredField("graphObjects");
            Object object = SubstrateObjectConstant.asObject(heap.getMetaAccess().lookupJavaField(field).readValue(null));
            processObject(heap.getObjectInfo(object), "CompressedGraphObjects", true, null, ObjectGroupHistogram::filterObjectConstantField);
        } catch (Throwable ex) {
            /* Ignore. When we build an image without Graal support, the class is not present. */
        }

        HeapHistogram totalHistogram = new HeapHistogram();
        for (ObjectInfo info : heap.getObjects()) {
            totalHistogram.add(info, info.getSize());
            addToGroup(info, "Other");
        }

        totalHistogram.printHeadings("=== Total ===");
        totalHistogram.print();

        for (Map.Entry<String, HeapHistogram> entry : groupHistograms.entrySet()) {
            entry.getValue().printHeadings("=== " + entry.getKey() + " ===");
            entry.getValue().print();
        }

        System.out.println();
        System.out.println("=== Summary ===");
        for (Map.Entry<String, HeapHistogram> entry : groupHistograms.entrySet()) {
            System.out.format("%s; %d; %d\n", entry.getKey(), entry.getValue().getTotalCount(), entry.getValue().getTotalSize());
        }
        System.out.format("%s; %d; %d\n", "Total", totalHistogram.getTotalCount(), totalHistogram.getTotalSize());
    }

    private static Object readGraalSupportField(String name) {
        try {
            Class<?> graalSupportClass = Class.forName("com.oracle.svm.graal.GraalSupport");
            Object graalSupport = ImageSingletons.lookup(graalSupportClass);
            return ReflectionUtil.readField(graalSupportClass, name, graalSupport);
        } catch (Throwable ex) {
            System.out.println("Warning: cannot read field from GraalSupport: " + name);
            return null;
        }
    }

    public void processType(Class<?> clazz, String group, boolean addObject, ObjectFilter objectFilter, FieldFilter fieldFilter) {
        for (ObjectInfo info : heap.getObjects()) {
            if (clazz.isInstance(info.getObject())) {
                processObject(info, group, addObject, 1, objectFilter, fieldFilter);
            }
        }
    }

    public void processObject(Object object, String group, boolean addObject, ObjectFilter objectFilter, FieldFilter fieldFilter) {
        if (object != null) {
            processObject(heap.getObjectInfo(object), group, addObject, 1, objectFilter, fieldFilter);
        }
    }

    private void processObject(ObjectInfo info, String group, boolean addObject, int recursionLevel, ObjectFilter objectFilter, FieldFilter fieldFilter) {
        if (objectFilter != null && !objectFilter.test(info, recursionLevel)) {
            return;
        }
        assert info != null;
        if (addObject) {
            if (!addToGroup(info, group)) {
                return;
            }
        }
        if (info.getClazz().isInstanceClass()) {
            JavaConstant con = SubstrateObjectConstant.forObject(info.getObject());
            for (HostedField field : info.getClazz().getInstanceFields(true)) {
                if (field.getType().getStorageKind() == JavaKind.Object && !HybridLayout.isHybridField(field) && field.isAccessed()) {
                    if (fieldFilter == null || fieldFilter.test(info, field)) {
                        Object fieldValue = SubstrateObjectConstant.asObject(field.readStorageValue(con));
                        if (fieldValue != null) {
                            processObject(heap.getObjectInfo(fieldValue), group, true, recursionLevel + 1, objectFilter, fieldFilter);
                        }
                    }
                }
            }
        } else if (info.getObject() instanceof Object[]) {
            for (Object element : (Object[]) info.getObject()) {
                if (element != null) {
                    ObjectInfo elementInfo = heap.getObjectInfo(heap.getAnalysisUniverse().replaceObject(element));
                    processObject(elementInfo, group, true, recursionLevel + 1, objectFilter, fieldFilter);
                }
            }
        }
    }

    private boolean addToGroup(ObjectInfo info, String group) {
        if (!groups.containsKey(info)) {
            groups.put(info, group);
            HeapHistogram histogram = groupHistograms.get(group);
            if (histogram == null) {
                histogram = new HeapHistogram();
                groupHistograms.put(group, histogram);
            }
            histogram.add(info, info.getSize());
            return true;
        } else {
            return false;
        }
    }
}
