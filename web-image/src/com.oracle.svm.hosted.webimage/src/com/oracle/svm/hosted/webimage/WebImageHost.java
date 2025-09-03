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

package com.oracle.svm.hosted.webimage;

import java.util.Optional;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.MissingRegistrationSupport;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.hosted.webimage.wasm.snippets.WasmImportForeignCallDescriptor;
import com.oracle.svm.webimage.wasm.WasmForeignCallDescriptor;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.options.OptionValues;

public class WebImageHost extends SVMHost {
    public WebImageHost(OptionValues options, ImageClassLoader loader, ClassInitializationSupport classInitializationSupport, AnnotationSubstitutionProcessor annotationSubstitutions,
                    MissingRegistrationSupport missingRegistrationSupport) {
        super(options, loader, classInitializationSupport, annotationSubstitutions, missingRegistrationSupport);
    }

    @Override
    public Optional<AnalysisMethod> handleForeignCall(ForeignCallDescriptor foreignCallDescriptor, ForeignCallsProvider foreignCallsProvider) {
        if (foreignCallDescriptor instanceof WasmImportForeignCallDescriptor || foreignCallDescriptor instanceof WasmForeignCallDescriptor) {
            // Wasm (import) foreign calls do not have an associated Java method
            return Optional.empty();
        }
        return super.handleForeignCall(foreignCallDescriptor, foreignCallsProvider);
    }
}
