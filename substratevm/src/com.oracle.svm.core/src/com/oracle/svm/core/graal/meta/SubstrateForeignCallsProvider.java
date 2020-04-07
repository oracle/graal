/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.meta;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyForeignCalls;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaKind;

public class SubstrateForeignCallsProvider implements ArrayCopyForeignCalls {

    private final Map<SubstrateForeignCallDescriptor, SubstrateForeignCallLinkage> foreignCalls;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateForeignCallsProvider() {
        this.foreignCalls = new HashMap<>();
    }

    public Map<SubstrateForeignCallDescriptor, SubstrateForeignCallLinkage> getForeignCalls() {
        return foreignCalls;
    }

    @Override
    public SubstrateForeignCallLinkage lookupForeignCall(ForeignCallDescriptor descriptor) {
        SubstrateForeignCallLinkage callTarget = foreignCalls.get(descriptor);
        if (callTarget == null) {
            throw shouldNotReachHere("missing implementation for runtime call: " + descriptor);
        }
        return callTarget;
    }

    @Override
    public boolean isAvailable(ForeignCallDescriptor descriptor) {
        return foreignCalls.containsKey(descriptor);
    }

    @Override
    public boolean isReexecutable(ForeignCallDescriptor descriptor) {
        return lookupForeignCall(descriptor).getDescriptor().isReexecutable();
    }

    @Override
    public LocationIdentity[] getKilledLocations(ForeignCallDescriptor descriptor) {
        return lookupForeignCall(descriptor).getDescriptor().getKilledLocations();
    }

    @Override
    public boolean canDeoptimize(ForeignCallDescriptor descriptor) {
        return lookupForeignCall(descriptor).getDescriptor().needsDebugInfo();
    }

    @Override
    public boolean isGuaranteedSafepoint(ForeignCallDescriptor descriptor) {
        return lookupForeignCall(descriptor).getDescriptor().isGuaranteedSafepoint();
    }

    @Override
    public LIRKind getValueKind(JavaKind javaKind) {
        return LIRKind.fromJavaKind(ImageSingletons.lookup(SubstrateTargetDescription.class).arch, javaKind);
    }

    @Override
    public ForeignCallDescriptor lookupCheckcastArraycopyDescriptor(boolean uninit) {
        throw VMError.unsupportedFeature("Fast ArrayCopy not supported yet.");
    }

    @Override
    public ForeignCallDescriptor lookupArraycopyDescriptor(JavaKind kind, boolean aligned, boolean disjoint, boolean uninit, boolean killAny) {
        throw VMError.unsupportedFeature("Fast ArrayCopy not supported yet.");
    }
}
