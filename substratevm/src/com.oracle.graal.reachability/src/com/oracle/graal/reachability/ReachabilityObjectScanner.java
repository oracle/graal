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

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.ObjectScanningObserver;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

// todo resolve return values
public class ReachabilityObjectScanner implements ObjectScanningObserver {

    private final ReachabilityAnalysis bb;
    private final AnalysisMetaAccess access;

    public ReachabilityObjectScanner(BigBang bb, AnalysisMetaAccess access) {
        this.bb = ((ReachabilityAnalysis) bb);
        this.access = access;
    }

    @Override
    public boolean forRelocatedPointerFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue, ObjectScanner.ScanReason reason) {
        field.registerAsAccessed();
        if (fieldValue.isNonNull() && fieldValue.getJavaKind() == JavaKind.Object) {
            // todo mark as instantiated
// bb.markTypeInstantiated(constantType(bb, fieldValue));
        }
        return true;
    }

    @Override
    public boolean forNullFieldValue(JavaConstant receiver, AnalysisField field, ObjectScanner.ScanReason reason) {
        if (receiver != null) {
            bb.markTypeReachable(constantType(receiver));
        }
        bb.markTypeReachable(field.getType());
// System.out.println("Scanning field " + field);
        return true;
    }

    @Override
    public boolean forNonNullFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue, ObjectScanner.ScanReason reason) {
        if (receiver != null) {
            bb.markTypeReachable(constantType(receiver));
        }
        bb.markTypeReachable(field.getType());
// System.out.println("Scanning field " + field);
        return true;
    }

    @Override
    public boolean forNullArrayElement(JavaConstant array, AnalysisType arrayType, int elementIndex, ObjectScanner.ScanReason reason) {
        bb.markTypeReachable(arrayType);
        return true;
    }

    @Override
    public boolean forNonNullArrayElement(JavaConstant array, AnalysisType arrayType, JavaConstant elementConstant, AnalysisType elementType, int elementIndex, ObjectScanner.ScanReason reason) {
        bb.markTypeReachable(arrayType);
        bb.markTypeInstantiated(elementType);
        return true;
    }

    @Override
    public void forEmbeddedRoot(JavaConstant root, ObjectScanner.ScanReason reason) {
        bb.markTypeReachable(constantType(root));
        bb.markTypeInstantiated(constantType(root));
    }

    @Override
    public void forScannedConstant(JavaConstant scannedValue, ObjectScanner.ScanReason reason) {
        AnalysisType type = constantType(scannedValue);
// System.out.println("Scanning constant of type " + type);
        bb.markTypeInstantiated(type);
        type.registerAsInHeap();
    }

    private AnalysisType constantType(JavaConstant constant) {
        return access.lookupJavaType(constantAsObject(constant).getClass());
    }

    private Object constantAsObject(JavaConstant constant) {
        return bb.getSnippetReflectionProvider().asObject(Object.class, constant);
    }

}
