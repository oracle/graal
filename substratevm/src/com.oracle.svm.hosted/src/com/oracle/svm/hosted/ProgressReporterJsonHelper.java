/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.json.JsonWriter;

public class ProgressReporterJsonHelper {
    protected static final long UNAVAILABLE_METRIC = -1;
    private static final String ANALYSIS_RESULTS_KEY = "analysis_results";
    private static final String GENERAL_INFO_KEY = "general_info";
    private static final String IMAGE_DETAILS_KEY = "image_details";
    private static final String RESOURCE_USAGE_KEY = "resource_usage";

    private final Map<String, Object> statsHolder = new HashMap<>();

    @SuppressWarnings("unchecked")
    public void putAnalysisResults(AnalysisResults key, long value) {
        Map<String, Object> analysisMap = (Map<String, Object>) statsHolder.computeIfAbsent(ANALYSIS_RESULTS_KEY, k -> new HashMap<>());
        Map<String, Object> bucketMap = (Map<String, Object>) analysisMap.computeIfAbsent(key.bucket(), bk -> new HashMap<>());
        bucketMap.put(key.jsonKey(), value);
    }

    @SuppressWarnings("unchecked")
    public void putGeneralInfo(GeneralInfo info, Object value) {
        Map<String, Object> generalInfoMap = (Map<String, Object>) statsHolder.computeIfAbsent(GENERAL_INFO_KEY, gi -> new HashMap<>());
        if (info.bucket != null) {
            Map<String, Object> subMap = (Map<String, Object>) generalInfoMap.computeIfAbsent(info.bucket, k -> new HashMap<>());
            subMap.put(info.jsonKey(), value);
        } else {
            generalInfoMap.put(info.jsonKey(), value);
        }
    }

    @SuppressWarnings("unchecked")
    public void putImageDetails(ImageDetailKey key, Object value) {
        Map<String, Object> imageDetailsMap = (Map<String, Object>) statsHolder.computeIfAbsent(IMAGE_DETAILS_KEY, id -> new HashMap<>());
        if (key.bucket == null && key.subBucket == null) {
            imageDetailsMap.put(key.jsonKey, value);
        } else if (key.subBucket == null) {
            assert key.bucket != null;
            Map<String, Object> bucketMap = (Map<String, Object>) imageDetailsMap.computeIfAbsent(key.bucket, sb -> new HashMap<>());
            bucketMap.put(key.jsonKey, value);
        } else {
            assert key.subBucket != null;
            Map<String, Object> bucketMap = (Map<String, Object>) imageDetailsMap.computeIfAbsent(key.bucket, sb -> new HashMap<>());
            Map<String, Object> subbucketMap = (Map<String, Object>) bucketMap.computeIfAbsent(key.subBucket, sb -> new HashMap<>());
            subbucketMap.put(key.jsonKey, value);
        }
    }

    @SuppressWarnings("unchecked")
    public void putResourceUsage(ResourceUsageKey key, Object value) {
        Map<String, Object> resUsageMap = (Map<String, Object>) statsHolder.computeIfAbsent(RESOURCE_USAGE_KEY, ru -> new HashMap<>());
        if (key.bucket != null) {
            Map<String, Object> subMap = (Map<String, Object>) resUsageMap.computeIfAbsent(key.bucket, k -> new HashMap<>());
            subMap.put(key.jsonKey, value);
        } else {
            resUsageMap.put(key.jsonKey, value);
        }
    }

    public void print(JsonWriter writer) throws IOException {
        writer.print(statsHolder);
    }

    interface JsonMetric {
        void record(ProgressReporterJsonHelper helper, Object value);
    }

    enum ImageDetailKey implements JsonMetric {
        TOTAL_SIZE(null, null, "total_bytes"),
        CODE_AREA_SIZE("code_area", null, "bytes"),
        NUM_COMP_UNITS("code_area", null, "compilation_units"),
        IMAGE_HEAP_SIZE("image_heap", null, "bytes"),
        IMAGE_HEAP_OBJECT_COUNT("image_heap", "objects", "count"),
        DEBUG_INFO_SIZE("debug_info", null, "bytes"),
        IMAGE_HEAP_RESOURCE_COUNT("image_heap", "resources", "count"),
        RESOURCE_SIZE_BYTES("image_heap", "resources", "bytes"),
        RUNTIME_COMPILED_METHODS_COUNT("runtime_compiled_methods", null, "count"),
        GRAPH_ENCODING_SIZE("runtime_compiled_methods", null, "graph_encoding_bytes");

        private String bucket;
        private String jsonKey;
        private String subBucket;

        ImageDetailKey(String bucket, String subBucket, String key) {
            this.bucket = bucket;
            this.jsonKey = key;
            this.subBucket = subBucket;
        }

        @Override
        public void record(ProgressReporterJsonHelper helper, Object value) {
            helper.putImageDetails(this, value);
        }
    }

    enum ResourceUsageKey implements JsonMetric {
        CPU_LOAD("cpu", "load"),
        CPU_CORES_TOTAL("cpu", "total_cores"),
        GC_COUNT("garbage_collection", "count"),
        GC_MAX_HEAP("garbage_collection", "max_heap"),
        GC_SECS("garbage_collection", "total_secs"),
        PARALLELISM("cpu", "parallelism"),
        PEAK_RSS("memory", "peak_rss_bytes"),
        MEMORY_TOTAL("memory", "system_total"),
        TOTAL_SECS(null, "total_secs");

        private String bucket;
        private String jsonKey;

        ResourceUsageKey(String bucket, String key) {
            this.bucket = bucket;
            this.jsonKey = key;
        }

        @Override
        public void record(ProgressReporterJsonHelper helper, Object value) {
            helper.putResourceUsage(this, value);
        }
    }

    public enum AnalysisResults implements JsonMetric {
        TYPES_TOTAL("types", "total"),
        TYPES_REACHABLE("types", "reachable"),
        TYPES_JNI("types", "jni"),
        TYPES_REFLECT("types", "reflection"),
        METHOD_TOTAL("methods", "total"),
        METHOD_REACHABLE("methods", "reachable"),
        METHOD_JNI("methods", "jni"),
        METHOD_REFLECT("methods", "reflection"),
        FIELD_TOTAL("fields", "total"),
        FIELD_REACHABLE("fields", "reachable"),
        FIELD_JNI("fields", "jni"),
        FIELD_REFLECT("fields", "reflection"),
        FOREIGN_DOWNCALLS("methods", "foreign_downcalls"),

        // TODO GR-42148: remove deprecated entries in a future release
        DEPRECATED_CLASS_TOTAL("classes", "total"),
        DEPRECATED_CLASS_REACHABLE("classes", "reachable"),
        DEPRECATED_CLASS_JNI("classes", "jni"),
        DEPRECATED_CLASS_REFLECT("classes", "reflection");

        private String key;
        private String bucket;

        AnalysisResults(String bucket, String key) {
            this.key = key;
            this.bucket = bucket;
        }

        public String jsonKey() {
            return key;
        }

        public String bucket() {
            return bucket;
        }

        @Override
        public void record(ProgressReporterJsonHelper helper, Object value) {
            if (value instanceof Integer v) {
                helper.putAnalysisResults(this, v);
            } else if (value instanceof Long v) {
                helper.putAnalysisResults(this, v);
            } else {
                VMError.shouldNotReachHere("Imcompatible type of 'value': " + value.getClass());
            }
        }
    }

    public enum GeneralInfo implements JsonMetric {
        IMAGE_NAME("name", null),
        JAVA_VERSION("java_version", null),
        VENDOR_VERSION("vendor_version", null),
        GRAALVM_VERSION("graalvm_version", null),
        GRAAL_COMPILER_OPTIMIZATION_LEVEL("optimization_level", "graal_compiler"),
        GRAAL_COMPILER_MARCH("march", "graal_compiler"),
        GRAAL_COMPILER_PGO("pgo", "graal_compiler"),
        GC("garbage_collector", null),
        CC("c_compiler", null);

        private String key;
        private String bucket;

        GeneralInfo(String key, String bucket) {
            this.key = key;
            this.bucket = bucket;
        }

        public String jsonKey() {
            return key;
        }

        @Override
        public void record(ProgressReporterJsonHelper helper, Object value) {
            if (value instanceof String || value instanceof Boolean || value instanceof List || value == null) {
                helper.putGeneralInfo(this, value);
            } else {
                VMError.shouldNotReachHere("Imcompatible type of 'value': " + value.getClass());
            }
        }
    }
}
