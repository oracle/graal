/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.reflect;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;

import jdk.vm.ci.meta.ResolvedJavaField;

@AutomaticallyRegisteredImageSingleton(value = StaticFieldsSupport.StaticFieldValidator.class)
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class)
public class StaticFieldValidatorImpl implements StaticFieldsSupport.StaticFieldValidator {

    @Override
    public void hostedCheckFieldOffsetAllowed(ResolvedJavaField field) {
        boolean wordType;
        if (field instanceof AnalysisField aField) {
            wordType = aField.getType().isWordType();
        } else {
            SharedField sField = (SharedField) field;
            wordType = ((SharedType) sField.getType()).isWordType();
        }

        AnalysisError.guarantee(!wordType, "static Word field offsets cannot be queried %s", field);
    }
}
