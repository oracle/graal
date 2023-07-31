/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.jdk.resources.ResourceStorageEntry;
import com.oracle.svm.core.reflect.ReflectionMetadataDecoder;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeImageWriteAccessImpl;
import com.oracle.svm.hosted.ProgressReporter.LinkStrategy;
import com.oracle.svm.hosted.ProgressReporterJsonHelper.ImageDetailKey;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.hosted.meta.HostedClass;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;

public class HeapBreakdownProvider {
    private static final String BYTE_ARRAY_PREFIX = "byte[] for ";
    private static final Field STRING_VALUE = ReflectionUtil.lookupField(String.class, "value");

    private boolean reportStringBytes = true;
    private long graphEncodingByteLength = -1;

    private List<HeapBreakdownEntry> sortedBreakdownEntries;
    private long totalHeapSize = -1;

    public static HeapBreakdownProvider singleton() {
        return ImageSingletons.lookup(HeapBreakdownProvider.class);
    }

    public void disableStringBytesReporting() {
        reportStringBytes = false;
    }

    public void setGraphEncodingByteLength(int value) {
        graphEncodingByteLength = value;
    }

    public List<HeapBreakdownEntry> getSortedBreakdownEntries() {
        return sortedBreakdownEntries;
    }

    public long getTotalHeapSize() {
        assert totalHeapSize >= 0;
        return totalHeapSize;
    }

    void calculate(BeforeImageWriteAccessImpl access) {
        HostedMetaAccess metaAccess = access.getHostedMetaAccess();

        Map<HostedClass, HeapBreakdownEntry> classToDataMap = new HashMap<>();

        long totalObjectSize = 0;
        long stringByteArrayLength = 0;
        int stringByteArrayCount = 0;
        for (ObjectInfo o : access.getImage().getHeap().getObjects()) {
            long objectSize = o.getSize();
            totalObjectSize += objectSize;
            classToDataMap.computeIfAbsent(o.getClazz(), c -> new HeapBreakdownEntry(c)).add(objectSize);
            if (reportStringBytes) {
                JavaConstant javaObject = o.getConstant();
                if (metaAccess.isInstanceOf(javaObject, String.class)) {
                    stringByteArrayLength += getInternalByteArrayLength(metaAccess.getUniverse().getSnippetReflection().asObject(String.class, javaObject));
                    stringByteArrayCount++;
                }
            }
        }

        /* Prepare to break down byte[] data in more detail. */
        HostedType byteArrayType = metaAccess.lookupJavaType(byte[].class);
        HeapBreakdownEntry byteArrayEntry = classToDataMap.remove(byteArrayType);
        assert byteArrayEntry != null : "Unable to find heap breakdown data for byte[] type";

        /* Convert from map to list. */
        List<HeapBreakdownEntry> entries = new ArrayList<>(classToDataMap.values());
        classToDataMap.clear();

        /* Add heap alignment. */
        totalHeapSize = access.getImage().getImageHeapSize();
        long heapAlignmentSize = totalHeapSize - totalObjectSize;
        assert heapAlignmentSize >= 0 : "Incorrect heap alignment detected: " + heapAlignmentSize;
        if (heapAlignmentSize > 0) {
            HeapBreakdownEntry heapAlignmentEntry = new HeapBreakdownEntry("", "heap alignment", "#glossary-heap-alignment");
            heapAlignmentEntry.add(heapAlignmentSize);
            entries.add(heapAlignmentEntry);
        }

        /* Extract byte[] for Strings. */
        if (stringByteArrayLength > 0) {
            addEntry(entries, byteArrayEntry, new HeapBreakdownEntry(BYTE_ARRAY_PREFIX + "java.lang.String"), stringByteArrayLength, stringByteArrayCount);
        }
        /* Extract byte[] for code info. */
        long codeInfoSize = CodeInfoTable.getImageCodeCache().getTotalByteArraySize();
        int codeInfoCount = CodeInfoTable.getImageCodeCache().getTotalByteArrayCount();
        if (codeInfoSize > 0) {
            addEntry(entries, byteArrayEntry, new HeapBreakdownEntry(BYTE_ARRAY_PREFIX, "code metadata", "#glossary-code-metadata"), codeInfoSize, codeInfoCount);
        }
        /* Extract byte[] for metadata. */
        long metadataByteLength = ImageSingletons.lookup(ReflectionMetadataDecoder.class).getMetadataByteLength();
        if (metadataByteLength > 0) {
            addEntry(entries, byteArrayEntry, new HeapBreakdownEntry(BYTE_ARRAY_PREFIX, "reflection metadata", "#glossary-reflection-metadata"), metadataByteLength, 1);
        }
        /* Extract byte[] for resources. */
        long resourcesByteLength = 0;
        int resourcesByteArrayCount = 0;
        for (ResourceStorageEntry resourceList : Resources.singleton().resources()) {
            for (byte[] resource : resourceList.getData()) {
                resourcesByteLength += resource.length;
                resourcesByteArrayCount++;
            }
        }
        ProgressReporter reporter = ProgressReporter.singleton();
        reporter.recordJsonMetric(ImageDetailKey.RESOURCE_SIZE_BYTES, resourcesByteLength);
        if (resourcesByteLength > 0) {
            addEntry(entries, byteArrayEntry, new HeapBreakdownEntry(BYTE_ARRAY_PREFIX, "embedded resources", "#glossary-embedded-resources"), resourcesByteLength, resourcesByteArrayCount);
        }
        /* Extract byte[] for graph encodings. */
        if (graphEncodingByteLength >= 0) {
            reporter.recordJsonMetric(ImageDetailKey.GRAPH_ENCODING_SIZE, graphEncodingByteLength);
            addEntry(entries, byteArrayEntry, new HeapBreakdownEntry(BYTE_ARRAY_PREFIX, "graph encodings", "#glossary-graph-encodings"), graphEncodingByteLength, 1);
        }
        /* Add remaining byte[]. */
        assert byteArrayEntry.byteSize >= 0 && byteArrayEntry.count >= 0;
        addEntry(entries, byteArrayEntry, new HeapBreakdownEntry(BYTE_ARRAY_PREFIX, "general heap data", "#glossary-general-heap-data"), byteArrayEntry.byteSize, byteArrayEntry.count);
        assert byteArrayEntry.byteSize == 0 && byteArrayEntry.count == 0;
        sortedBreakdownEntries = entries.stream().sorted(Comparator.comparingLong(HeapBreakdownEntry::getByteSize).reversed()).toList();
    }

    private static void addEntry(List<HeapBreakdownEntry> entries, HeapBreakdownEntry byteArrayEntry, HeapBreakdownEntry newData, long byteSize, int count) {
        newData.add(byteSize, count);
        entries.add(newData);
        byteArrayEntry.remove(byteSize, count);
    }

    private static int getInternalByteArrayLength(String string) {
        if (string == null) {
            return 0;
        }
        try {
            return ((byte[]) STRING_VALUE.get(string)).length;
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    public static class HeapBreakdownEntry {
        final HeapBreakdownLabel label;
        long byteSize;
        int count;

        HeapBreakdownEntry(HostedClass hostedClass) {
            this(hostedClass.toJavaName(true));
        }

        public HeapBreakdownEntry(String name) {
            label = new SimpleHeapObjectKindName(name);
        }

        HeapBreakdownEntry(String prefix, String name, String htmlAnchor) {
            label = new LinkyHeapObjectKindName(prefix, name, htmlAnchor);
        }

        public HeapBreakdownLabel getLabel() {
            return label;
        }

        public long getByteSize() {
            return byteSize;
        }

        public long getCount() {
            return count;
        }

        public void add(long addByteSize) {
            add(addByteSize, 1);
        }

        void add(long addByteSize, int addCount) {
            this.byteSize += addByteSize;
            this.count += addCount;
        }

        void remove(long subByteSize, int subCount) {
            this.byteSize -= subByteSize;
            this.count -= subCount;
        }
    }

    public interface HeapBreakdownLabel {
        String renderToString(LinkStrategy linkStrategy);
    }

    record SimpleHeapObjectKindName(String name) implements HeapBreakdownLabel {
        @Override
        public String renderToString(LinkStrategy linkStrategy) {
            return name;
        }
    }

    record LinkyHeapObjectKindName(String prefix, String label, String htmlAnchor) implements HeapBreakdownLabel {
        @Override
        public String renderToString(LinkStrategy linkStrategy) {
            return prefix + linkStrategy.asDocLink(label, htmlAnchor);
        }
    }
}
