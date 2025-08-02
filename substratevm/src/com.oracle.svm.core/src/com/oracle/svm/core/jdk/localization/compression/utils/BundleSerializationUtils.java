/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Supplier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.jdk.localization.BundleContentSubstitutedLocalizationSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.debug.GraalError;

public class BundleSerializationUtils {

    /**
     * Extracts the content of the bundle by looking up the lookup field. All the jdk internal
     * bundles can be resolved this way, except from the {@link java.text.BreakIterator}. In the
     * future, it can be extended with a fallback to user defined bundles by using the handleKeySet
     * and handleGetObject methods.
     * <p>
     * {@link BundleContentSubstitutedLocalizationSupport} depends on the ability the extract the
     * contents of resource bundles, and we currently do so via the lookup field. If we failed to
     * extract the content, we would get a runtime crash when trying to look up the content from our
     * substitutions.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractContent(ResourceBundle bundle) {
        bundle.keySet(); // force lazy initialization
        Class<?> clazz = bundle.getClass().getSuperclass();
        while (clazz != null && ResourceBundle.class.isAssignableFrom(clazz)) {
            try {
                Object lookup = ReflectionUtil.lookupField(clazz, "lookup").get(bundle);
                if (lookup instanceof Supplier) {
                    return ((Supplier<Map<String, Object>>) lookup).get();
                }
                return (Map<String, Object>) lookup;
            } catch (ReflectionUtil.ReflectionUtilError | ReflectiveOperationException e) {
                clazz = clazz.getSuperclass();
            }
        }
        /*
         * The list of tested classes could be collected above, but we only need it in case of an
         * unlikely failure, therefore we do not want to pollute the fast path with it.
         */
        var testedClasses = new ArrayList<Class<?>>();
        for (Class<?> testedClass = bundle.getClass().getSuperclass(); testedClass != null && ResourceBundle.class.isAssignableFrom(testedClass); testedClass = testedClass.getSuperclass()) {
            testedClasses.add(testedClass);
        }
        /* See the method's javadoc for more details. */
        throw VMError.shouldNotReachHere("Failed to extract the content for " + bundle + " of type " + bundle.getClass() +
                        ". Did not find the `lookup` field in any of the super classes of " + bundle.getClass() + " " + testedClasses +
                        ". This most likely means that the internal implementation of resource bundles in JDK has changed and is now incompatible with our resource bundle handling.");
    }

    public record SerializedContent(String text, int[] indices) {
    }

    /**
     * @param content content of the bundle to be serialized
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static SerializedContent serializeContent(Map<String, Object> content) {
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
        return new SerializedContent(builder.toString(), res);
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
