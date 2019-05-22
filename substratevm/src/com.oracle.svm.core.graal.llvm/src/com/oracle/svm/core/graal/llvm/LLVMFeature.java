/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm;

import static com.oracle.svm.core.SubstrateOptions.CompilerBackend;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateBackendFactory;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.c.util.FileUtils;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.image.NativeImageCodeCacheFactory;
import com.oracle.svm.hosted.image.NativeImageHeap;

@AutomaticFeature
@CLibrary("m")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
public class LLVMFeature implements Feature, GraalFeature, Snippets {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return CompilerBackend.getValue().equals("llvm");
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        checkLLVMVersion();

        ImageSingletons.add(SubstrateBackendFactory.class, new SubstrateBackendFactory() {
            @Override
            public SubstrateBackend newBackend(Providers newProviders) {
                return new SubstrateLLVMBackend(newProviders);
            }
        });
        ImageSingletons.add(NativeImageCodeCacheFactory.class, new NativeImageCodeCacheFactory() {
            @Override
            public NativeImageCodeCache newCodeCache(CompileQueue compileQueue, NativeImageHeap heap, Platform platform) {
                return new LLVMNativeImageCodeCache(compileQueue.getCompilations(), heap, platform);
            }
        });
        ImageSingletons.add(SnippetRuntime.ExceptionStackFrameVisitor.class, new SnippetRuntime.ExceptionStackFrameVisitor() {
            @Override
            public CodePointer getExceptionHandlerPointer(CodePointer ip, Pointer sp, long handlerOffset) {
                /*
                 * LLVM uses a setjmp/longjmp mechanism for exception handling, with the unwind
                 * information held in a buffer on the stack of the caller. As such, the value
                 * returned by lookupExceptionOffset is not the offset of the exception handling
                 * code relative to the call IP, but the offset of the setjmp buffer relative to the
                 * caller's stack pointer. Furthermore, this offset is stored as itself + 1, because
                 * it is legal (and frequent) to have an offset of 0 for the buffer, which would be
                 * considered as the absence of the exception handler.
                 */
                return (CodePointer) sp.add(WordFactory.unsigned(handlerOffset - 1));
            }
        });
    }

    private static void checkLLVMVersion() {
        List<String> supportedVersions = Arrays.asList("6.0.0", "6.0.1");

        int status;
        String output = null;
        try (OutputStream os = new ByteArrayOutputStream()) {
            List<String> cmd = new ArrayList<>();
            cmd.add("llvm-config");
            cmd.add("--version");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            FileUtils.drainInputStream(p.getInputStream(), os);

            status = p.waitFor();
            output = os.toString().trim();
        } catch (IOException | InterruptedException e) {
            status = -1;
        }

        if (status != 0) {
            throw UserError.abort("Using the LLVM backend requires LLVM to be installed on your machine.");
        }
        if (!supportedVersions.contains(output)) {
            throw UserError.abort("Unsupported LLVM version: " + output + ". Supported versions are: [" + String.join(", ", supportedVersions) + "]");
        }
    }
}
