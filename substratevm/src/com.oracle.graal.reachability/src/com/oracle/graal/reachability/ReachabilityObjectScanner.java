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

/**
 * Notifies the reachability analysis engine about types discovered in the image heap.
 *
 * @see ReachabilityAnalysisEngine
 */
public class ReachabilityObjectScanner implements ObjectScanningObserver {

    private final ReachabilityAnalysisEngine bb;
    private final AnalysisMetaAccess access;

    public ReachabilityObjectScanner(BigBang bb, AnalysisMetaAccess access) {
        this.bb = ((ReachabilityAnalysisEngine) bb);
        this.access = access;
    }

    @Override
    public boolean forRelocatedPointerFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue, ObjectScanner.ScanReason reason) {
        if (!field.isWritten()) {
            return field.registerAsWritten(reason);
        }
        return false;
    }

    @Override
    public boolean forNullFieldValue(JavaConstant receiver, AnalysisField field, ObjectScanner.ScanReason reason) {
        boolean modified = false;
        if (receiver != null) {
            modified = bb.registerTypeAsReachable(constantType(receiver), reason);
        }
        return modified || bb.registerTypeAsReachable(field.getType(), reason);
    }

    @Override
    public boolean forNonNullFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue, ObjectScanner.ScanReason reason) {
        boolean modified = false;
        if (receiver != null) {
            bb.registerTypeAsInHeap(constantType(receiver), reason);
            modified = bb.registerTypeAsReachable(constantType(receiver), reason);
        }
        return modified || bb.registerTypeAsReachable(field.getType(), reason);
    }

    @Override
    public boolean forNullArrayElement(JavaConstant array, AnalysisType arrayType, int elementIndex, ObjectScanner.ScanReason reason) {
        return bb.registerTypeAsReachable(arrayType, reason);
    }

    @Override
    public boolean forNonNullArrayElement(JavaConstant array, AnalysisType arrayType, JavaConstant elementConstant, AnalysisType elementType, int elementIndex, ObjectScanner.ScanReason reason) {
        return bb.registerTypeAsReachable(arrayType, reason) || bb.registerTypeAsInHeap(elementType, reason);
    }

    @Override
    public void forEmbeddedRoot(JavaConstant root, ObjectScanner.ScanReason reason) {
        bb.registerTypeAsReachable(constantType(root), reason);
        bb.registerTypeAsInHeap(constantType(root), reason);
    }

    @Override
    public void forScannedConstant(JavaConstant scannedValue, ObjectScanner.ScanReason reason) {
        AnalysisType type = constantType(scannedValue);
        bb.registerTypeAsInHeap(type, reason);
    }

    private AnalysisType constantType(JavaConstant constant) {
        return access.lookupJavaType(constant);
    }
}
