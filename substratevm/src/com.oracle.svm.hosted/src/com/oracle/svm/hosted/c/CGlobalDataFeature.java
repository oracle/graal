/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c;

import java.util.IdentityHashMap;
import java.util.Map.Entry;
import java.util.stream.IntStream;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataImpl;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.nodes.CGlobalDataLoadAddressNode;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.RelocatableBuffer;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticFeature
public class CGlobalDataFeature implements GraalFeature {
    private static class CGlobalDataInfo {
        final int offset;
        final byte[] data;

        CGlobalDataInfo(int offset, byte[] data) {
            this.offset = offset;
            this.data = data;
        }
    }

    public static CGlobalDataFeature singleton() {
        return ImageSingletons.lookup(CGlobalDataFeature.class);
    }

    private IdentityHashMap<CGlobalDataImpl<?>, CGlobalDataInfo> map = new IdentityHashMap<>();
    private boolean layouted = false;
    private int totalSize;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(this::replaceObject);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        layout();
    }

    @Override
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, InvocationPlugins invocationPlugins, boolean hosted) {
        Registration r = new Registration(invocationPlugins, CGlobalData.class);
        r.register1("get", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                VMError.guarantee(receiver.get().isConstant(), "Accessed CGlobalData is not a compile-time constant: " + b.getMethod().asStackTraceElement(b.bci()));
                CGlobalDataImpl<?> data = (CGlobalDataImpl<?>) SubstrateObjectConstant.asObject(receiver.get().asConstant());
                b.addPush(targetMethod.getSignature().getReturnKind(), new CGlobalDataLoadAddressNode(data));
                return true;
            }
        });
    }

    private Object replaceObject(Object obj) {
        if (obj instanceof CGlobalDataImpl<?>) {
            assert !layouted || map.containsKey(obj) : "CGlobalData instance must have been discovered during analysis";
            map.putIfAbsent((CGlobalDataImpl<?>) obj, null);
        }
        return obj;
    }

    public void layout() {
        assert !layouted : "Already layouted";
        final int wordSize = ConfigurationValues.getTarget().wordSize;
        int offset = 0;
        for (Entry<CGlobalDataImpl<?>, CGlobalDataInfo> entry : map.entrySet()) {
            assert entry.getValue() == null;
            CGlobalDataImpl<?> data = entry.getKey();
            int size;
            if (data.bytesSupplier != null) {
                byte[] bytes = data.bytesSupplier.get();
                entry.setValue(new CGlobalDataInfo(offset, bytes));
                size = bytes.length;
            } else {
                entry.setValue(new CGlobalDataInfo(offset, null));
                if (data.sizeSupplier != null) {
                    size = data.sizeSupplier.getAsInt();
                } else {
                    assert (data.symbolName != null) : "CGlobalData without bytes, size, or referenced symbol";
                    /*
                     * A symbol reference: we support only instruction-pointer-relative addressing
                     * with 32-bit immediates, which might not be sufficient for the target symbol's
                     * address. Therefore, reserve space for a word with the symbol's true address.
                     */
                    size = wordSize;
                }
            }
            offset += size;
            offset = (offset + (wordSize - 1)) & ~(wordSize - 1); // align
        }
        totalSize = offset;
        layouted = true;
    }

    public int getSize() {
        assert layouted : "Not layouted yet";
        return totalSize;
    }

    public int getOffsetOf(CGlobalData<?> data) {
        assert layouted : "Not layouted yet";
        return map.get(data).offset;
    }

    public void writeData(RelocatableBuffer buffer) {
        int start = buffer.getPosition();
        assert IntStream.range(0, totalSize).allMatch(i -> buffer.getByte(i) == 0) : "Buffer must be zero-initialized";
        for (CGlobalDataInfo info : map.values()) {
            if (info.data != null) {
                buffer.setPosition(start + info.offset);
                buffer.putBytes(info.data, 0, info.data.length);
            }
        }
    }
}
