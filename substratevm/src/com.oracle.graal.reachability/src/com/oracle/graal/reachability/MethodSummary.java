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
import org.graalvm.collections.EconomicSet;

/**
 * This class fully represents a method for the purposes of Reachability Analysis, so that it does
 * not have to be re-analyzed again.
 */
public class MethodSummary {
    public final EconomicSet<AnalysisMethod> invokedMethods;
    public final EconomicSet<AnalysisMethod> implementationInvokedMethods;
    public final EconomicSet<AnalysisType> accessedTypes;
    public final EconomicSet<AnalysisType> instantiatedTypes;
    public final EconomicSet<AnalysisField> readFields;
    public final EconomicSet<AnalysisField> writtenFields;
    public final EconomicSet<JavaConstant> embeddedConstants;
    public final EconomicSet<AnalysisMethod> foreignCallTargets;

    public MethodSummary(EconomicSet<AnalysisMethod> invokedMethods, EconomicSet<AnalysisMethod> implementationInvokedMethods, EconomicSet<AnalysisType> accessedTypes,
                    EconomicSet<AnalysisType> instantiatedTypes,
                    EconomicSet<AnalysisField> readFields,
                    EconomicSet<AnalysisField> writtenFields,
                    EconomicSet<JavaConstant> embeddedConstants,
                    EconomicSet<AnalysisMethod> foreignCallTargets) {
        this.invokedMethods = invokedMethods;
        this.implementationInvokedMethods = implementationInvokedMethods;
        this.accessedTypes = accessedTypes;
        this.instantiatedTypes = instantiatedTypes;
        this.readFields = readFields;
        this.writtenFields = writtenFields;
        this.embeddedConstants = embeddedConstants;
        this.foreignCallTargets = foreignCallTargets;
    }

    @Override
    public String toString() {
        return "invoked: " +
                        invokedMethods.size() +
                        ", impl invoked: " +
                        implementationInvokedMethods.size() +
                        ", accessed: " +
                        accessedTypes.size() +
                        ", instantiated: " +
                        instantiatedTypes.size() +
                        ", read: " +
                        readFields.size() +
                        ", written: " +
                        writtenFields.size() +
                        ", embedded: " +
                        embeddedConstants.size() +
                        ", foreignCalls: " +
                        foreignCallTargets.size();
    }
}
