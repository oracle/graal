/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.api;

import java.util.Arrays;

import org.graalvm.compiler.truffle.compiler.substitutions.KnownTruffleTypes;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.heap.Target_java_lang_ref_Reference;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class SubstrateKnownTruffleTypes extends KnownTruffleTypes {

    public final ResolvedJavaField referentField = findField(lookupType(Target_java_lang_ref_Reference.class), ReferenceInternals.REFERENT_FIELD_NAME);

    public SubstrateKnownTruffleTypes(MetaAccessProvider metaAccess) {
        super(metaAccess);
    }

    @Override
    protected ResolvedJavaType lookupType(String className) {
        AnalysisType type = (AnalysisType) super.lookupType(className);
        type.registerAsReachable();
        return type;
    }

    @Override
    protected ResolvedJavaType lookupType(Class<?> c) {
        AnalysisType type = (AnalysisType) super.lookupType(c);
        type.registerAsReachable();
        return type;
    }

    @Override
    protected ResolvedJavaField[] getInstanceFields(ResolvedJavaType type, boolean includeSuperclasses) {
        AnalysisField[] fields = ((AnalysisType) type).getInstanceFields(includeSuperclasses);
        /*
         * We must not embed an object of dynamic type AnalysisField[] in the image heap. Object
         * replacement does not replace arrays, only their elements. Therefore we replace the array
         * manually by one with a dynamic type allowed in the image heap.
         */
        return Arrays.copyOf(fields, fields.length, ResolvedJavaField[].class);
    }
}
