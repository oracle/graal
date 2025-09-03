/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.graal;

import jdk.graal.compiler.core.common.memory.MemoryExtendKind;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.nodes.memory.ExtendableMemoryAccess;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;

public final class DummyLoweringProvider implements LoweringProvider {
    private final TargetDescription target;

    public DummyLoweringProvider(TargetDescription target) {
        this.target = target;
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public ValueNode reconstructArrayIndex(JavaKind elementKind, AddressNode address) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public Integer smallestCompareWidth() {
        SuspiciousHostAccessCollector.onSuspiciousHostAccess();
        // used at least by AutomaticUnsafeTransformationSupport.getStaticInitializerGraph
        return null;
    }

    @Override
    public boolean supportsBulkZeroingOfEden() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public boolean supportsOptimizedFilling(OptionValues options) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public boolean supportsImplicitNullChecks() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public boolean writesStronglyOrdered() {
        SuspiciousHostAccessCollector.onSuspiciousHostAccess();
        // used at least by AutomaticUnsafeTransformationSupport.getStaticInitializerGraph
        return false;
    }

    @Override
    public TargetDescription getTarget() {
        SuspiciousHostAccessCollector.onSuspiciousHostAccess();
        // used at least by AutomaticUnsafeTransformationSupport.getStaticInitializerGraph
        return target;
    }

    @Override
    public BarrierSet getBarrierSet() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public boolean divisionOverflowIsJVMSCompliant() {
        SuspiciousHostAccessCollector.onSuspiciousHostAccess();
        // used at least by AutomaticUnsafeTransformationSupport.getStaticInitializerGraph
        return false;
    }

    @Override
    public boolean narrowsUseCastValue() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public boolean supportsFoldingExtendIntoAccess(ExtendableMemoryAccess access, MemoryExtendKind extendKind) {
        throw GraalError.unimplementedOverride();
    }
}
