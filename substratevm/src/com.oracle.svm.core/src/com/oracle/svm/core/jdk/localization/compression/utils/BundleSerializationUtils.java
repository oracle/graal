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
package com.oracle.svm.core.jdk.localization.compression.utils;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;
import org.graalvm.collections.Pair;
import jdk.graal.compiler.debug.GraalError;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class BundleSerializationUtils {

    /**
     * Extracts the content of the bundle by looking up the lookup field. All the jdk internal
     * bundles can be resolved this way, except from the {@link java.text.BreakIterator}. In the
     * future, it can be extended with a fallback to user defined bundles by using the handleKeySet
     * and handleGetObject methods.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractContent(ResourceBundle bundle) {
        bundle.keySet(); // force lazy initialization
        Class<?> clazz = bundle.getClass().getSuperclass();
        while (clazz != null && ResourceBundle.class.isAssignableFrom(clazz)) {
            try {
                return (Map<String, Object>) ReflectionUtil.lookupField(clazz, "lookup").get(bundle);
            } catch (ReflectionUtil.ReflectionUtilError | ReflectiveOperationException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw VMError.shouldNotReachHere("Failed to extract content for " + bundle + " of type " + bundle.getClass());
    }

    /**
     * @param content content of the bundle to be serialized
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static Pair<String, int[]> serializeContent(Map<String, Object> content) {
        List<Integer> indices = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Object> entry : content.entrySet()) {
            String key = entry.getKey();
            builder.append(key);
            Object value = entry.getValue();
            if (value instanceof String) {
                builder.append(value);
                indices.add(-1);
                indices.add(key.length());
                indices.add(((String) value).length());
            } else if (value instanceof Object[]) {
                Object[] arr = (Object[]) value;
                indices.add(arr.length);
                indices.add(key.length());
                for (Object o : arr) {
                    GraalError.guarantee(o instanceof String, "Bundle content can't be serialized.");
                    builder.append(o);
                    indices.add(((String) o).length());
                }
            } else {
                GraalError.shouldNotReachHere("Bundle content can't be serialized."); // ExcludeFromJacocoGeneratedReport
            }
        }
        int[] res = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            res[i] = indices.get(i);
        }
        return Pair.create(builder.toString(), res);
    }

    public static Map<String, Object> deserializeContent(int[] indices, String text) {
        Map<String, Object> content = new HashMap<>();
        int i = 0;
        int offset = 0;
        while (i < indices.length) {
            int valueCnt = indices[i++];
            int keyLen = indices[i++];
            String key = text.substring(offset, offset + keyLen);
            offset += keyLen;
            boolean isArray = valueCnt != -1;
            if (isArray) {
                Object[] values = new String[valueCnt];
                for (int j = 0; j < valueCnt; j++) {
                    int valueLen = indices[i++];
                    values[j] = text.substring(offset, offset + valueLen);
                    offset += valueLen;
                }
                content.put(key, values);
            } else {
                int valueLen = indices[i++];
                String value = text.substring(offset, offset + valueLen);
                offset += valueLen;
                content.put(key, value);
            }
        }
        return content;
    }
}
