/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.meta;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.annotate.AutomaticFeature;

/**
 * Tracks fields with constant values which could be inlined, but which must exist in memory -- for
 * example, when they might be accessed via JNI.
 */
public class MaterializedConstantFields {
    static void initialize() {
        ImageSingletons.add(MaterializedConstantFields.class, new MaterializedConstantFields());
    }

    public static MaterializedConstantFields singleton() {
        return ImageSingletons.lookup(MaterializedConstantFields.class);
    }

    private final Set<AnalysisField> fields = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private boolean sealed = false;

    public void register(AnalysisField field) {
        assert field.isStatic() && field.isFinal() : "Only required for static final fields";
        assert field.isAccessed() : "Field must be accessed as read";
        assert !sealed : "Already sealed";
        fields.add(field);
    }

    public boolean contains(AnalysisField field) {
        if (field.isStatic() && field.isFinal()) {
            return fields.contains(field);
        }
        return false;
    }

    void seal() {
        sealed = true;
    }
}

@AutomaticFeature
class MaterializedConstantFieldsFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        MaterializedConstantFields.initialize();
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        MaterializedConstantFields.singleton().seal();
    }
}
