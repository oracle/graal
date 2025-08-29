/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.codegen;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.graal.code.CGlobalDataReference;
import com.oracle.svm.core.image.ImageHeapLayoutInfo;
import com.oracle.svm.core.image.ImageHeapLayouter.ImageHeapLayouterCallback;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.image.NativeImageHeapWriter;
import com.oracle.svm.hosted.image.RelocatableBuffer;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.webimage.WebImageCodeCache;
import com.oracle.svm.hosted.webimage.WebImageHostedConfiguration;
import com.oracle.svm.hosted.webimage.codegen.WebImageProviders;
import com.oracle.svm.hosted.webimage.wasm.WebImageWasmOptions;
import com.oracle.svm.hosted.webimage.wasm.ast.Data;
import com.oracle.svm.hosted.webimage.wasm.ast.Export;
import com.oracle.svm.hosted.webimage.wasm.ast.Global;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Relocation;
import com.oracle.svm.hosted.webimage.wasm.ast.Limit;
import com.oracle.svm.hosted.webimage.wasm.ast.Memory;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.ast.visitors.WasmRelocationVisitor;
import com.oracle.svm.hosted.webimage.wasm.gc.MemoryLayout;
import com.oracle.svm.hosted.webimage.wasm.stack.InitialStackPointerConstant;
import com.oracle.svm.webimage.wasm.types.WasmUtil;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.Reference;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.VMConstant;

public class WebImageWasmLMCodeGen extends WebImageWasmCodeGen {

    public WebImageWasmLMCodeGen(WebImageCodeCache codeCache, List<HostedMethod> hostedEntryPoints, HostedMethod mainEntryPoint, WebImageProviders providers, DebugContext debug,
                    WebImageHostedConfiguration config) {
        super(codeCache, hostedEntryPoints, mainEntryPoint, providers, debug, config);
    }

    /**
     * For WasmLM, the image heap size calculated by the heap layouter is not accurate. Due to
     * optimizations in {@link com.oracle.svm.hosted.webimage.wasm.ast.ActiveData}, larger chunks of
     * empty space (null-bytes) in the image heap can be omitted from the final image. Thus, the
     * heap size is simply the combined size of the active data segments, which contain all the
     * image heap data, in the image.
     */
    @Override
    public long getImageHeapSize() {
        return module.getDataSegments().stream().filter(d -> d.active).mapToLong(Data::getSize).sum();
    }

    /**
     * Adds the data collected in the image heap to the module.
     */
    @Override
    protected void writeImageHeap() {
        ImageHeapLayoutInfo layout = codeCache.nativeImageHeap.getLayouter().layout(codeCache.nativeImageHeap, WasmUtil.PAGE_SIZE, ImageHeapLayouterCallback.NONE);
        setLayout(layout);

        afterHeapLayout();

        NativeImageHeapWriter writer = new NativeImageHeapWriter(codeCache.nativeImageHeap, layout);

        RelocatableBuffer heapSectionBuffer = new RelocatableBuffer(layout.getSize(), WasmUtil.BYTE_ORDER);
        codeCache.writeConstants(writer, heapSectionBuffer);
        writer.writeHeap(debug, heapSectionBuffer);
        long heapStart = MemoryLayout.HEAP_BASE.rawValue();
        assert heapStart % 8 == 0 : heapStart;
        int imageHeapSize = (int) layout.getSize();

        EconomicMap<CGlobalData<?>, UnsignedWord> globalData = EconomicMap.create();
        int memorySize = MemoryLayout.constructLayout(globalData, imageHeapSize, WebImageWasmOptions.StackSize.getValue());

        processRelocations(heapSectionBuffer, heapStart, globalData);
        module.addActiveData(heapStart, heapSectionBuffer.getBackingArray());

        WasmId.Memory memId = getProviders().knownIds().heapMemory;
        module.addExport(new Export(Export.Type.MEM, memId, "memory", "Main Memory"));
        // The memory size has to be given as a number of pages.
        module.setMemory(new Memory(memId, Limit.withoutMax(NumUtil.divideAndRoundUp(memorySize, WasmUtil.PAGE_SIZE)), null));
    }

    @Override
    protected void genWasmModule() {
        super.genWasmModule();

        module.addGlobal(new Global(getProviders().knownIds.stackPointer, true, Instruction.Relocation.forConstant(InitialStackPointerConstant.INSTANCE), "Shadow stack pointer"));
    }

    /**
     * Replaces all relocations in the image heap ({@link RelocatableBuffer#getSortedRelocations()})
     * and the AST ({@link Relocation}) with a concrete value.
     * <p>
     * <ul>
     * <li>Object references are replaced with their address in the image heap.</li>
     * <li>Method pointers populate a function table and are replaced with their index in that
     * table.</li>
     * <li>{@link CGlobalDataReference}s are looked up in the passed {@code globalData} map</li>
     * </ul>
     *
     * @param heapStart The address of the first byte in the image heap.
     */
    private void processRelocations(RelocatableBuffer buffer, long heapStart, UnmodifiableEconomicMap<CGlobalData<?>, UnsignedWord> globalData) {
        for (var entry : buffer.getSortedRelocations()) {
            int offset = entry.getKey();
            RelocatableBuffer.Info info = entry.getValue();
            ObjectFile.RelocationKind relocationKind = info.getRelocationKind();
            Object targetObject = info.getTargetObject();

            // TODO GR-42105, this will likely become DIRECT_4 with 32-bit pointers.
            assert relocationKind == ObjectFile.RelocationKind.DIRECT_8 : "Unsupported relocation kind: " + relocationKind;

            long relocatedValue;

            if (targetObject instanceof WordBase) {
                GraalError.guarantee(targetObject instanceof MethodPointer, "Relocated word is not a method pointer: %s", targetObject);
                relocatedValue = getFunctionTableIndex((MethodPointer) targetObject);
                assert info.getAddend() == 0 : "Non-zero addend in method poiner relocation" + info;
            } else {
                NativeImageHeap.ObjectInfo targetObjectInfo = codeCache.nativeImageHeap.getConstantInfo((JavaConstant) targetObject);
                assert targetObjectInfo != null : "Relocated object not found: " + targetObject;
                relocatedValue = heapStart + targetObjectInfo.getOffset();
            }

            // TODO GR-42105, this will likely have to deal with ints with 32-bit pointers.
            long relocationAddend = relocatedValue + info.getAddend();

            buffer.getByteBuffer().putLong(offset, relocationAddend);
        }

        // Resolve all relocations in the AST
        new WasmRelocationVisitor() {
            @Override
            public void visitUnprocessedRelocation(Relocation relocation) {
                Reference targetRef = relocation.target;
                if (targetRef instanceof ConstantReference) {
                    VMConstant constant = ((ConstantReference) targetRef).getConstant();
                    if (constant instanceof JavaConstant) {
                        NativeImageHeap.ObjectInfo objectInfo = codeCache.nativeImageHeap.getConstantInfo((JavaConstant) constant);
                        int address = (int) (objectInfo.getOffset() + heapStart);
                        relocation.setValue(Instruction.Const.forInt(address));
                    } else if (constant instanceof AbsoluteIPConstant ipConstant) {
                        /*
                         * Replaces IPs relative to the method starts to absolute, globally unique,
                         * IPs by adding the method's base IP.
                         */
                        HostedMethod method = (HostedMethod) ipConstant.method();
                        relocation.setValue(Instruction.Const.forInt(method.getCodeAddressOffset() + ipConstant.relativeIP()));
                    } else if (constant instanceof InitialStackPointerConstant) {
                        relocation.setValue(Instruction.Const.forInt((int) globalData.get(MemoryLayout.STACK_BASE).rawValue()));
                    } else {
                        throw GraalError.unimplemented("Unsupported constant relocation: " + constant); // ExcludeFromJacocoGeneratedReport
                    }
                } else if (targetRef instanceof CGlobalDataReference) {
                    // CGlobalDataReferences are replaced with the value in the globalData map.
                    CGlobalData<?> globalDataReference = ((CGlobalDataReference) targetRef).getDataInfo().getData();
                    GraalError.guarantee(globalData.containsKey(globalDataReference), "CGlobalData was referenced but not defined: %s", globalDataReference);
                    relocation.setValue(Instruction.Const.forWord(globalData.get(globalDataReference)));
                }

                GraalError.guarantee(relocation.wasProcessed(), "Relocation %s was not processed, target: %s", relocation, targetRef);
            }
        }.visitModule(module);
    }
}
