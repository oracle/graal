/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.dashboard;

import com.oracle.svm.hosted.dashboard.ToJson.JsonObject;
import com.oracle.svm.hosted.dashboard.ToJson.JsonString;
import com.oracle.svm.hosted.dashboard.ToJson.JsonNumber;
import com.oracle.svm.hosted.dashboard.ToJson.JsonValue;
import com.oracle.svm.hosted.dashboard.ToJson.JsonArray;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.image.NativeImageHeap;
import org.graalvm.nativeimage.hosted.Feature;

import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

class HeapBreakdownJsonObject extends JsonObject {

    private final Feature.AfterHeapLayoutAccess access;
    private boolean built = false;
    private static final String INFO_NAME = "name";
    private static final String INFO_SIZE = "size";
    private static final String INFO_COUNT = "count";
    private static final List<String> NAMES = Arrays.asList(INFO_NAME, INFO_SIZE, INFO_COUNT);

    private final HashMap<String, Statistics> sizes = new HashMap<>();

    HeapBreakdownJsonObject(Feature.AfterHeapLayoutAccess access) {
        this.access = access;
    }

    @Override
    Stream<String> getNames() {
        return Arrays.asList("heap-size").stream();
    }

    @Override
    JsonValue getValue(String name) {
        return JsonArray.get(sizes.entrySet().stream().map(ClassJsonObject::new));
    }

    private static class ClassJsonObject extends JsonObject {

        private final Map.Entry<String, Statistics> entry;

        ClassJsonObject(Map.Entry<String, Statistics> entry) {
            this.entry = entry;
        }

        @Override
        Stream<String> getNames() {
            return NAMES.stream();
        }

        @Override
        JsonValue getValue(String name) {
            switch (name) {
                case INFO_NAME:
                    return JsonString.get(entry.getKey());
                case INFO_SIZE:
                    return JsonNumber.get(entry.getValue().size);
                case INFO_COUNT:
                    return JsonNumber.get(entry.getValue().count);
                default:
                    return null;
            }
        }
    }

    private class Statistics {

        long size = 0;
        long count = 0;
    }

    @Override
    protected void build() {
        if (built) {
            return;
        }
        FeatureImpl.AfterHeapLayoutAccessImpl config = (FeatureImpl.AfterHeapLayoutAccessImpl) access;
        NativeImageHeap heap = config.getHeap();
        for (NativeImageHeap.ObjectInfo info : heap.getObjects()) {
            final String className = info.getClazz().getName();
            Statistics stats = sizes.get(className);
            if (stats == null) {
                stats = new Statistics();
                sizes.put(className, stats);
            }
            stats.size += info.getSize();
            stats.count += 1;
        }
        built = true;
    }
}
