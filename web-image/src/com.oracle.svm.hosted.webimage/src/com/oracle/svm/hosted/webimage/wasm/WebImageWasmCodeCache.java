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

package com.oracle.svm.hosted.webimage.wasm;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoEncoder;
import com.oracle.svm.core.code.FrameInfoDecoder.ConstantAccess;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.webimage.WebImageCodeCache;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmCompilationResult;
import com.oracle.svm.webimage.wasm.code.FrameData;
import com.oracle.svm.webimage.wasm.code.WasmCodeInfoHolder;
import com.oracle.svm.webimage.wasm.code.WasmCodeInfoQueryResult;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.Indent;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.Infopoint;

public class WebImageWasmCodeCache extends WebImageCodeCache {

    private final HashMap<Integer, WasmCodeInfoQueryResult> codeInfoMap = new HashMap<>();

    public WebImageWasmCodeCache(Map<HostedMethod, CompilationResult> compilationResultMap, NativeImageHeap imageHeap) {
        super(compilationResultMap, imageHeap);
    }

    @Override
    public int codeSizeFor(HostedMethod method) {
        int methodStart = method.getCodeAddressOffset();
        int methodEnd = computeNextMethodStart(methodStart, compilationResultFor(method).getTargetCodeSize());
        return methodEnd - methodStart;
    }

    @Override
    @SuppressWarnings("try")
    public void layoutMethods(DebugContext debug, BigBang bb) {
        try (Indent indent = debug.logAndIndent("layout methods")) {
            /*
             * Assigns the pseudo-instruction pointers (IP) to methods. A method's
             * 'codeAddressOffset' is its starting pseudo-IP and its codesize is the number of IPs
             * the method requires.
             */
            int curPos = 0;
            for (Pair<HostedMethod, CompilationResult> entry : getOrderedCompilations()) {
                HostedMethod method = entry.getLeft();
                CompilationResult compilation = entry.getRight();

                method.setCodeAddressOffset(curPos);
                curPos = computeNextMethodStart(curPos, compilation.getTargetCodeSize());
            }

            // the total code size is the hypothetical start of the next method
            setCodeAreaSize(curPos);
        }
    }

    @Override
    protected void buildRuntimeMetadata(DebugContext debug, SnippetReflectionProvider snippetReflection, CFunctionPointer firstMethod, UnsignedWord codeSize) {
        super.buildRuntimeMetadata(debug, snippetReflection, firstMethod, codeSize);

        int maxIP = codeInfoMap.keySet().stream().max(Integer::compareTo).orElse(-1);
        WasmCodeInfoQueryResult[] codeInfos = new WasmCodeInfoQueryResult[maxIP + 1];
        codeInfoMap.forEach((ip, info) -> codeInfos[ip] = info);
        // Inject the code infos into the image
        WasmCodeInfoHolder.setCodeInfos(codeInfos);
    }

    @Override
    protected boolean verifyMethods(DebugContext debug, HostedUniverse hUniverse, CodeInfoEncoder codeInfoEncoder, CodeInfo codeInfo, ConstantAccess constantAccess) {
        // TODO GR-43486 enable this if we ever use CodeInfoEncoder
        return true;
    }

    @Override
    protected void encodeMethod(CodeInfoEncoder codeInfoEncoder, Pair<HostedMethod, CompilationResult> pair) {
        final HostedMethod method = pair.getLeft();
        final WebImageWasmCompilationResult compilation = (WebImageWasmCompilationResult) pair.getRight();
        final int compilationOffset = method.getCodeAddressOffset();

        EconomicSet<Integer> infopointOffsets = EconomicSet.create();

        FrameData data = new FrameData(compilation.getTotalFrameSize(), method.format("%H.%n(%P)%R"));

        WasmCodeInfoQueryResult queryResult = new WasmCodeInfoQueryResult(data, compilation.getLiveSlots().stream().mapToInt(s -> s.getOffset(compilation.getTotalFrameSize())).toArray());

        // Add code info for start of method
        codeInfoMap.put(compilationOffset, queryResult);

        for (Infopoint infopoint : compilation.getInfopoints()) {
            if (infopoint instanceof Call call) {
                final int offset = call.pcOffset + call.size;
                boolean added = infopointOffsets.add(offset);
                if (!added) {
                    throw VMError.shouldNotReachHere("Encoding two infopoints at same offset. Conflicting infopoint: " + infopoint);
                }

                int ip = compilationOffset + offset;

                codeInfoMap.put(ip, queryResult);
            }
        }
    }

    private static int computeNextMethodStart(int current, int addend) {
        try {
            return Math.addExact(current, addend);
        } catch (ArithmeticException e) {
            throw VMError.shouldNotReachHere("Code size is larger than 2GB");
        }
    }
}
