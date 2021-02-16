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
import org.graalvm.compiler.core.common.spi.ForeignCallSignature;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyForeignCalls;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyLookup;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaKind;

public class SubstrateForeignCallsProvider implements ArrayCopyForeignCalls {

    private final Map<ForeignCallSignature, SubstrateForeignCallLinkage> foreignCalls;
    protected ArrayCopyLookup arrayCopyLookup;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateForeignCallsProvider() {
        this.foreignCalls = new HashMap<>();
    }

    public Map<ForeignCallSignature, SubstrateForeignCallLinkage> getForeignCalls() {
        return foreignCalls;
    }

    public void register(Providers providers, SnippetRuntime.SubstrateForeignCallDescriptor... descriptors) {
        for (SnippetRuntime.SubstrateForeignCallDescriptor descriptor : descriptors) {
            SubstrateForeignCallLinkage linkage = new SubstrateForeignCallLinkage(providers, descriptor);
            foreignCalls.put(descriptor.getSignature(), linkage);
        }
    }

    @Override
    public SubstrateForeignCallLinkage lookupForeignCall(ForeignCallDescriptor descriptor) {
        SubstrateForeignCallLinkage callTarget = foreignCalls.get(descriptor.getSignature());
        if (callTarget == null) {
            throw shouldNotReachHere("missing implementation for runtime call: " + descriptor);
        }
        return callTarget;
    }

    @Override
    public ForeignCallDescriptor getDescriptor(ForeignCallSignature signature) {
        SubstrateForeignCallLinkage linkage = foreignCalls.get(signature);
        return linkage.getDescriptor();
    }

    @Override
    public LIRKind getValueKind(JavaKind javaKind) {
        return LIRKind.fromJavaKind(ImageSingletons.lookup(SubstrateTargetDescription.class).arch, javaKind);
    }

    public void registerArrayCopyForeignCallsDelegate(ArrayCopyLookup arraycopyForeignCalls) {
        this.arrayCopyLookup = arraycopyForeignCalls;
    }

    @Override
    public ForeignCallDescriptor lookupCheckcastArraycopyDescriptor(boolean uninit) {
        if (arrayCopyLookup != null) {
            return arrayCopyLookup.lookupCheckcastArraycopyDescriptor(uninit);
        } else {
            throw VMError.unsupportedFeature("Fast checkcast ArrayCopy not supported yet.");
        }
    }

    @Override
    public ForeignCallDescriptor lookupArraycopyDescriptor(JavaKind kind, boolean aligned, boolean disjoint, boolean uninit, LocationIdentity killedLocation) {
        if (arrayCopyLookup != null) {
            return arrayCopyLookup.lookupArraycopyDescriptor(kind, aligned, disjoint, uninit, killedLocation);
        } else {
            throw VMError.unsupportedFeature("Fast ArrayCopy not supported yet.");
        }
    }
}
