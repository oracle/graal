/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
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
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.graal.nodes.CGlobalDataLoadAddressNode;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.RelocatableBuffer;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticFeature
public class CGlobalDataFeature implements GraalFeature {
    public static CGlobalDataFeature singleton() {
        return ImageSingletons.lookup(CGlobalDataFeature.class);
    }

    private Map<CGlobalDataImpl<?>, CGlobalDataInfo> map = new ConcurrentHashMap<>();
    private int totalSize = -1;

    private boolean isLayouted() {
        return totalSize != -1;
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(this::replaceObject);
    }

    @Override
    public void afterHeapLayout(AfterHeapLayoutAccess access) {
        layout();
    }

    @Override
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, InvocationPlugins invocationPlugins, boolean analysis, boolean hosted) {
        Registration r = new Registration(invocationPlugins, CGlobalData.class);
        r.register1("get", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                VMError.guarantee(receiver.get().isConstant(), "Accessed CGlobalData is not a compile-time constant: " + b.getMethod().asStackTraceElement(b.bci()));
                CGlobalDataImpl<?> data = (CGlobalDataImpl<?>) SubstrateObjectConstant.asObject(receiver.get().asConstant());
                CGlobalDataInfo info = CGlobalDataFeature.this.map.get(data);
                b.addPush(targetMethod.getSignature().getReturnKind(), new CGlobalDataLoadAddressNode(info));
                return true;
            }
        });
    }

    public CGlobalDataInfo registerAsAccessed(CGlobalData<?> obj) {
        CGlobalDataImpl<?> data = (CGlobalDataImpl<?>) obj;
        assert !isLayouted() || map.containsKey(data) : "CGlobalData instance must have been discovered/registered before or during analysis";
        return map.computeIfAbsent((CGlobalDataImpl<?>) obj, o -> new CGlobalDataInfo(data));
    }

    private Object replaceObject(Object obj) {
        if (obj instanceof CGlobalDataImpl<?>) {
            registerAsAccessed((CGlobalData<?>) obj);
        }
        return obj;
    }

    private void layout() {
        assert !isLayouted() : "Already layouted";
        final int wordSize = ConfigurationValues.getTarget().wordSize;
        int offset = 0;
        for (Entry<CGlobalDataImpl<?>, CGlobalDataInfo> entry : map.entrySet()) {
            CGlobalDataImpl<?> data = entry.getKey();
            CGlobalDataInfo info = entry.getValue();
            int size;
            byte[] bytes = null;
            if (data.bytesSupplier != null) {
                bytes = data.bytesSupplier.get();
                size = bytes.length;
            } else {
                if (data.sizeSupplier != null) {
                    size = data.sizeSupplier.getAsInt();
                } else {
                    assert data.symbolName != null : "CGlobalData without bytes, size, or referenced symbol";
                    /*
                     * A symbol reference: we support only instruction-pointer-relative addressing
                     * with 32-bit immediates, which might not be sufficient for the target symbol's
                     * address. Therefore, reserve space for a word with the symbol's true address.
                     */
                    size = wordSize;
                }
            }
            info.assign(offset, bytes);

            offset += size;
            offset = (offset + (wordSize - 1)) & ~(wordSize - 1); // align
        }
        totalSize = offset;
        assert isLayouted();
    }

    public int getSize() {
        assert isLayouted() : "Not layouted yet";
        return totalSize;
    }

    public void writeData(RelocatableBuffer buffer, BiFunction<Integer, String, ?> createSymbol) {
        assert isLayouted() : "Not layouted yet";
        int start = buffer.getPosition();
        assert IntStream.range(0, totalSize).allMatch(i -> buffer.getByte(i) == 0) : "Buffer must be zero-initialized";
        for (CGlobalDataInfo info : map.values()) {
            byte[] bytes = info.getBytes();
            if (bytes != null) {
                buffer.setPosition(start + info.getOffset());
                buffer.putBytes(bytes, 0, bytes.length);
            }
            CGlobalDataImpl<?> data = info.getData();
            if (data.symbolName != null && !info.isSymbolReference()) {
                createSymbol.apply(info.getOffset(), data.symbolName);
            }
        }
    }
}
