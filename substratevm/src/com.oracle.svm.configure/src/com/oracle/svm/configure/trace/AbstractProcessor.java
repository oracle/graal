/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.trace;

import static com.oracle.svm.configure.ConfigurationParser.DECLARING_CLASS_KEY;
import static com.oracle.svm.configure.ConfigurationParser.INTERFACES_KEY;
import static com.oracle.svm.configure.ConfigurationParser.LAMBDA_KEY;
import static com.oracle.svm.configure.ConfigurationParser.PROXY_KEY;

import java.util.Base64;
import java.util.Collection;
import java.util.List;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.configure.ConfigurationTypeDescriptor;
import com.oracle.svm.configure.LambdaConfigurationTypeDescriptor;
import com.oracle.svm.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.configure.ProxyConfigurationTypeDescriptor;
import com.oracle.svm.configure.config.ConfigurationSet;

public abstract class AbstractProcessor {
    AbstractProcessor() {
    }

    abstract void processEntry(EconomicMap<String, Object> entry, ConfigurationSet configurationSet);

    void setInLivePhase(@SuppressWarnings("unused") boolean live) {
    }

    @SuppressWarnings("unchecked")
    static <T> T singleElement(List<?> list) {
        expectSize(list, 1);
        return (T) list.get(0);
    }

    static void expectSize(Collection<?> collection, int size) {
        if (collection.size() != size) {
            throw new IllegalArgumentException("List must have exactly " + size + " element(s)");
        }
    }

    static byte[] asBinary(Object obj) {
        if (obj instanceof byte[]) {
            return (byte[]) obj;
        }
        return Base64.getDecoder().decode((String) obj);
    }

    /**
     * Returns a fresh copy of the trace entry with an additional key-value pair identifying it.
     * Useful when logging entries to the access advisor.
     */
    protected EconomicMap<String, Object> copyWithUniqueEntry(EconomicMap<String, Object> entry, String key, Object value) {
        if (entry.containsKey(key)) {
            throw new AssertionError(String.format("Tried to set unique identifier %s but field already exists: %s%n", key, entry));
        }
        EconomicMap<String, Object> result = EconomicMap.create(entry);
        result.put(key, value);
        return result;
    }

    @SuppressWarnings("unchecked")
    protected static ConfigurationTypeDescriptor descriptorForClass(Object clazz) {
        if (clazz == null) {
            return null;
        } else if (clazz instanceof ConfigurationTypeDescriptor typeDescriptor) {
            /* Type descriptor passed directly */
            return typeDescriptor;
        } else if (clazz instanceof EconomicMap<?, ?>) {
            EconomicMap<String, Object> map = (EconomicMap<String, Object>) clazz;
            if (map.containsKey(LAMBDA_KEY)) {
                String declaringClass = (String) map.get(DECLARING_CLASS_KEY);
                List<String> interfaces = (List<String>) map.get(INTERFACES_KEY);
                return LambdaConfigurationTypeDescriptor.fromTypeNames(declaringClass, interfaces);
            } else if (map.containsKey(PROXY_KEY)) {
                List<String> interfaces = (List<String>) map.get(PROXY_KEY);
                return ProxyConfigurationTypeDescriptor.fromInterfaceTypeNames(interfaces);
            } else {
                throw new IllegalArgumentException("Unknown descriptor type: " + clazz);
            }
        } else if (clazz instanceof List<?>) {
            return ProxyConfigurationTypeDescriptor.fromInterfaceReflectionNames(((List<String>) clazz));
        } else {
            return NamedConfigurationTypeDescriptor.fromReflectionName((String) clazz);
        }
    }
}
