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
package com.oracle.graal.reachability;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import jdk.vm.ci.meta.JavaConstant;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallSignature;

import java.util.Arrays;

public class MethodSummary {
    public static final MethodSummary EMPTY = new MethodSummary(new AnalysisMethod[0], new AnalysisMethod[0], new AnalysisType[0], new AnalysisType[0], new AnalysisField[0], new AnalysisField[0],
                    new JavaConstant[0], new ForeignCallDescriptor[0], new ForeignCallSignature[0]);

    public final AnalysisMethod[] invokedMethods;
    public final AnalysisMethod[] implementationInvokedMethods;
    public final AnalysisType[] accessedTypes;
    public final AnalysisType[] instantiatedTypes;
    public final AnalysisField[] readFields;
    public final AnalysisField[] writtenFields;
    public final JavaConstant[] embeddedConstants;
    public final ForeignCallDescriptor[] foreignCallDescriptors;
    public final ForeignCallSignature[] foreignCallSignatures;

    public MethodSummary(AnalysisMethod[] invokedMethods, AnalysisMethod[] implementationInvokedMethods, AnalysisType[] accessedTypes, AnalysisType[] instantiatedTypes, AnalysisField[] readFields,
                    AnalysisField[] writtenFields,
                    JavaConstant[] embeddedConstants,
                    ForeignCallDescriptor[] foreignCallDescriptors,
                    ForeignCallSignature[] foreignCallSignatures) {
        this.invokedMethods = invokedMethods;
        this.implementationInvokedMethods = implementationInvokedMethods;
        this.accessedTypes = accessedTypes;
        this.instantiatedTypes = instantiatedTypes;
        this.readFields = readFields;
        this.writtenFields = writtenFields;
        this.embeddedConstants = embeddedConstants;
        this.foreignCallDescriptors = foreignCallDescriptors;
        this.foreignCallSignatures = foreignCallSignatures;
    }

    @Override
    public String toString() {
        return "MethodSummary{" +
                        "invokedMethods=" + Arrays.toString(invokedMethods) +
                        ", implementationInvokedMethods=" + Arrays.toString(implementationInvokedMethods) +
                        ", accessedTypes=" + Arrays.toString(accessedTypes) +
                        ", instantiatedTypes=" + Arrays.toString(instantiatedTypes) +
                        ", readFields=" + Arrays.toString(readFields) +
                        ", writtenFields=" + Arrays.toString(writtenFields) +
                        ", embeddedConstants=" + Arrays.toString(embeddedConstants) +
                        '}';
    }

    public MethodSummary withoutMethods() {
        return new MethodSummary(new AnalysisMethod[0], new AnalysisMethod[0], accessedTypes, instantiatedTypes, readFields, writtenFields, embeddedConstants, foreignCallDescriptors,
                        foreignCallSignatures);
    }
}
