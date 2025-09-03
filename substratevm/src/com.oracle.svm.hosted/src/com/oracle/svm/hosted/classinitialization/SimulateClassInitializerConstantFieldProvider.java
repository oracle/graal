/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.classinitialization;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.ameta.AnalysisConstantFieldProvider;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Constant field provider that also folds fields of classes that are already simulated as
 * initialized. The regular {@link AnalysisConstantFieldProvider} is not allowed to do that because
 * otherwise simulation information would be used during bytecode parsing already.
 * 
 * See {@link SimulateClassInitializerSupport} for an overview of class initializer simulation.
 */
final class SimulateClassInitializerConstantFieldProvider extends AnalysisConstantFieldProvider {
    final SimulateClassInitializerSupport support;

    SimulateClassInitializerConstantFieldProvider(MetaAccessProvider metaAccess, SVMHost hostVM, SimulateClassInitializerSupport support) {
        super(metaAccess, hostVM);
        this.support = support;
    }

    @Override
    protected boolean isClassInitialized(ResolvedJavaField field) {
        return support.isSimulatedOrInitializedAtBuildTime((AnalysisType) field.getDeclaringClass());
    }
}
