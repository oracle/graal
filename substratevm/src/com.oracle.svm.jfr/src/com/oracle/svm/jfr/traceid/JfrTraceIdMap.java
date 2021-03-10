/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.jfr.traceid;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.Target_java_lang_ClassLoader;
import com.oracle.svm.core.jdk.Target_java_lang_Module;
import com.oracle.svm.core.jdk.Target_java_lang_Package;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

public class JfrTraceIdMap {
    @UnknownObjectField(types = {long[].class})
    private final long[] traceIDs;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrTraceIdMap(int size) {
        traceIDs = new long[size];
        for (int i = 0; i < size; i++) {
            traceIDs[i] = -1;
        }
    }

    private int getIndex(Object key) {
        int index;
        if (key instanceof Class<?>) {
            DynamicHub hub = DynamicHub.fromClass((Class<?>) key);
            index = hub.getTypeID() + 1; // Off-set by 1 for error-catcher
        } else if (key instanceof ClassLoader) {
            Target_java_lang_ClassLoader classLoader = SubstrateUtil.cast(key, Target_java_lang_ClassLoader.class);
            index = classLoader.jfrID;
        } else if (key instanceof Package) {
            Target_java_lang_Package pkg = SubstrateUtil.cast(key, Target_java_lang_Package.class);
            index = pkg.jfrID;
        } else if (key instanceof Module) {
            Target_java_lang_Module module = SubstrateUtil.cast(key, Target_java_lang_Module.class);
            index = module.jfrID;
        } else {
            throw new IllegalArgumentException("Unexpected type: " + key.getClass());
        }
        assert index > 0;
        return index;
    }

    long getId(Object key) {
        return traceIDs[getIndex(key)];
    }

    long getId(int index) {
        return traceIDs[index];
    }

    void setId(Object key, long id) {
        traceIDs[getIndex(key)] = id;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    void setId(int index, long id) {
        traceIDs[index] = id;
    }
}
