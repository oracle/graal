/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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
package com.oracle.svm.hosted.image;

import java.nio.file.Path;
import java.util.stream.Stream;


import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import static com.oracle.svm.core.util.VMError.unimplemented;

import com.oracle.svm.hosted.meta.HostedType;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.DebugContext;

public class LLVMDebugInfoProvider implements DebugInfoProvider {
    public static NativeImageHeap heap;
    NativeImageDebugInfoProvider dbgInfoHelper;


    public LLVMDebugInfoProvider() {
        dbgInfoHelper = null;
    }

   public static void initializeHeap(NativeImageHeap heapArg) {
       heap = heapArg;
   }

   public NativeImageDebugInfoProvider getHelper() {
        return dbgInfoHelper;
   }

   @Override
    public Stream<DebugTypeInfo> typeInfoProvider() {
        throw unimplemented();
    }

   public Stream<HostedType> typeInfoProvider2() {
       Stream<HostedType> heapTypeInfo = heap.getUniverse().getTypes().stream();
       return heapTypeInfo;
   }

   public static class LLVMLocationInfoProvider implements DebugLocationInfo {
        private Path fullFilePath;
        private int bci;
        private ResolvedJavaMethod method;

        public LLVMLocationInfoProvider(DebugContext debugContext, int bci, ResolvedJavaMethod method) {
            fullFilePath = DebugInfoProviderHelper.getFullFilePathFromMethod(method, debugContext);
            this.bci = bci;
            this.method = method;
        }

       @Override
       public String fileName() {
           return DebugInfoProviderHelper.getFileName(fullFilePath);
       }

       @Override
       public Path filePath() {
           return DebugInfoProviderHelper.getFilePath(fullFilePath);
       }

       @Override
       public Path cachePath() {
           throw unimplemented();
       }

       @Override
       public int line() {
            return DebugInfoProviderHelper.getLineNumber(this.method, this.bci);
       }

       @Override
       public DebugLocalInfo[] getParamInfo() {
           throw unimplemented();
       }

       @Override
       public DebugLocalInfo getThisParamInfo() {
           throw unimplemented();
       }

       @Override
       public String symbolNameForMethod() {
           throw unimplemented();
       }

       @Override
       public boolean isDeoptTarget() {
           throw unimplemented();
       }

       @Override
       public boolean isConstructor() {
           throw unimplemented();
       }

       @Override
       public boolean isVirtual() {
           throw unimplemented();
       }

       @Override
       public int vtableOffset() {
           throw unimplemented();
       }

       @Override
       public boolean isOverride() {
           throw unimplemented();
       }

       @Override
       public ResolvedJavaType ownerType() {
           throw unimplemented();
       }

       @Override
       public ResolvedJavaMethod idMethod() {
           throw unimplemented();
       }

       @Override
       public String name() {
           return DebugInfoProviderHelper.getMethodName(method);
       }

       @Override
       public ResolvedJavaType valueType() {
           throw unimplemented();
       }

       @Override
       public int modifiers() {
           throw unimplemented();
       }

       @Override
       public int addressLo() {
           throw unimplemented();
       }

       @Override
       public int addressHi() {
           throw unimplemented();
       }

       @Override
       public DebugLocationInfo getCaller() {
           throw unimplemented();
       }

       @Override
       public DebugLocalValueInfo[] getLocalValueInfo() {
           throw unimplemented();
       }
   }



    @Override
    public boolean useHeapBase() {
        throw unimplemented();
    }

    @Override
    public int oopCompressShift() {
        throw unimplemented();
    }

    @Override
    public int oopTagsMask() {
        throw unimplemented();
    }

    @Override
    public int oopReferenceSize() {
        throw unimplemented();
    }

    @Override
    public int pointerSize() {
        throw unimplemented();
    }

    @Override
    public int oopAlignment() {
        throw unimplemented();
    }

    @Override
    public Stream<DebugCodeInfo> codeInfoProvider() {
        throw unimplemented();
    }

    @Override
    public Stream<DebugDataInfo> dataInfoProvider() {
        throw unimplemented();
    }
}
