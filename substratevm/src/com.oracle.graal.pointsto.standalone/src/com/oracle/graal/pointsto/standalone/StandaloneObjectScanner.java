/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone;

import java.util.function.Predicate;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.ObjectScanningObserver;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.util.CompletionExecutor;

import jdk.vm.ci.meta.JavaConstant;

public class StandaloneObjectScanner extends ObjectScanner {

    private Predicate<JavaConstant> shouldScanConstant;
    private Predicate<AnalysisField> shouldScanField;

    public StandaloneObjectScanner(BigBang bb, CompletionExecutor executor, ReusableSet scannedObjects, ObjectScanningObserver scanningObserver,
                    Predicate<JavaConstant> shouldScanConstant, Predicate<AnalysisField> shouldScanField) {
        super(bb, executor, scannedObjects, scanningObserver);
        this.shouldScanConstant = shouldScanConstant;
        this.shouldScanField = shouldScanField;
    }

    @Override
    protected void scanEmbeddedRoot(JavaConstant root, Object position) {
        if (shouldScanConstant.test(root)) {
            super.scanEmbeddedRoot(root, position);
        }
    }

    @Override
    protected final void scanField(AnalysisField field, JavaConstant receiver, ScanReason prevReason) {
        if (shouldScanField.test(field)) {
            super.scanField(field, receiver, prevReason);
        }
    }

    @Override
    public final void scanConstant(JavaConstant value, ScanReason reason) {
        if (shouldScanConstant.test(value)) {
            super.scanConstant(value, reason);
        }
    }
}
