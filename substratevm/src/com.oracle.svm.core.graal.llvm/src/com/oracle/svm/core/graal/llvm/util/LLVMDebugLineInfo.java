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
package com.oracle.svm.core.graal.llvm.util;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.annotation.CustomSubstitutionType;
import com.oracle.svm.hosted.image.NativeImage;
import com.oracle.svm.hosted.image.sources.SourceManager;
import com.oracle.svm.hosted.lambda.LambdaSubstitutionType;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.substitute.InjectedFieldsType;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.hosted.substitute.SubstitutionType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.nativeimage.ImageSingletons;

import java.nio.file.Path;

import static org.graalvm.compiler.debug.GraalError.unimplemented;

public class LLVMDebugLineInfo {
    private final int bci;
    private final ResolvedJavaMethod method;
    private final DebugContext debugContext;
    private Path cachePath;
    private Path fullFilePath;

    LLVMDebugLineInfo(DebugContext debugContext, NodeSourcePosition position) {
        int posbci = position.getBCI();
        this.bci = (posbci >= 0 ? posbci : 0);
        this.method = position.getMethod();
        this.cachePath = SubstrateOptions.getDebugInfoSourceCacheRoot();
        this.debugContext = debugContext;
        computeFullFilePath();
    }

    private static ResolvedJavaType getOriginal(ResolvedJavaType javaType) {
        if (javaType instanceof SubstitutionType) {
            return ((SubstitutionType) javaType).getOriginal();
        } else if (javaType instanceof CustomSubstitutionType<?, ?>) {
            return ((CustomSubstitutionType<?, ?>) javaType).getOriginal();
        } else if (javaType instanceof LambdaSubstitutionType) {
            return ((LambdaSubstitutionType) javaType).getOriginal();
        } else if (javaType instanceof InjectedFieldsType) {
            return ((InjectedFieldsType) javaType).getOriginal();
        }
        return null;
    }

    protected static ResolvedJavaType getJavaType(HostedMethod hostedMethod, boolean wantOriginal) {
        if (wantOriginal) {
            /* Check for wholesale replacement of the original class. */
            HostedType hostedType = hostedMethod.getDeclaringClass();
            ResolvedJavaType javaType = hostedType.getWrapped().getWrappedWithoutResolve();
            ResolvedJavaType originalType = getOriginal(javaType);
            if (originalType != null) {
                return originalType;
            }
            /* Check for replacement of the original method only. */
            ResolvedJavaMethod javaMethod = hostedMethod.getWrapped().getWrapped();
            if (javaMethod instanceof SubstitutionMethod) {
                return ((SubstitutionMethod) javaMethod).getOriginal().getDeclaringClass();
            } else if (javaMethod instanceof CustomSubstitutionMethod) {
                return ((CustomSubstitutionMethod) javaMethod).getOriginal().getDeclaringClass();
            }
            return hostedType.getWrapped().getWrapped();
        }
        ResolvedJavaMethod javaMethod = hostedMethod.getWrapped().getWrapped();
        return javaMethod.getDeclaringClass();
    }

    public String fileName() {
        if (fullFilePath != null) {
            Path fileName = fullFilePath.getFileName();
            if (fileName != null) {
                return fileName.toString();
            }
        }
        return null;
    }

    public Path filePath() {
        if (fullFilePath != null) {
            return fullFilePath.getParent();
        }
        return null;
    }

    public Path cachePath() {
        return cachePath;
    }

    public String className() {
        return method.format("%H");
    }

    public String methodName() {
        ResolvedJavaMethod targetMethod = method;
        while (targetMethod instanceof WrappedJavaMethod) {
            targetMethod = ((WrappedJavaMethod) targetMethod).getWrapped();
        }
        if (targetMethod instanceof SubstitutionMethod) {
            targetMethod = ((SubstitutionMethod) targetMethod).getOriginal();
        } else if (targetMethod instanceof CustomSubstitutionMethod) {
            targetMethod = ((CustomSubstitutionMethod) targetMethod).getOriginal();
        }
        String name = targetMethod.getName();
        if (name.equals("<init>")) {
            if (method instanceof HostedMethod) {
                name = getJavaType((HostedMethod) method, true).toJavaName();
                if (name.indexOf('.') >= 0) {
                    name = name.substring(name.lastIndexOf('.') + 1);
                }
                if (name.indexOf('$') >= 0) {
                    name = name.substring(name.lastIndexOf('$') + 1);
                }
            } else {
                name = targetMethod.format("%h");
                if (name.indexOf('$') >= 0) {
                    name = name.substring(name.lastIndexOf('$') + 1);
                }
            }
        }
        return name;
    }

    public String symbolNameForMethod() {
        return NativeImage.localSymbolNameForMethod(method);
    }

    public int addressLo() {
        throw unimplemented();
    }

    public int addressHi() {
        throw unimplemented();
    }

    public int line() {
        LineNumberTable lineNumberTable = method.getLineNumberTable();
        if (lineNumberTable != null) {
            return lineNumberTable.getLineNumber(bci);
        }
        return 0;
    }

    @SuppressWarnings("try")
    public void computeFullFilePath() {
        ResolvedJavaType declaringClass = method.getDeclaringClass();
        Class<?> clazz = null;
        if (declaringClass instanceof OriginalClassProvider) {
            clazz = ((OriginalClassProvider) declaringClass).getJavaClass();
        }
        /*
         * HostedType wraps an AnalysisType and both HostedType and AnalysisType punt calls to
         * getSourceFilename to the wrapped class so for consistency we need to do the path
         * lookup relative to the doubly unwrapped HostedType or singly unwrapped AnalysisType.
         */
        if (declaringClass instanceof HostedType) {
            declaringClass = ((HostedType) declaringClass).getWrapped();
        }
        if (declaringClass instanceof AnalysisType) {
            declaringClass = ((AnalysisType) declaringClass).getWrapped();
        }
        SourceManager sourceManager = ImageSingletons.lookup(SourceManager.class);
        try (DebugContext.Scope s = debugContext.scope("DebugCodeInfo", declaringClass)) {
            fullFilePath = sourceManager.findAndCacheSource(declaringClass, clazz, debugContext);
        } catch (Throwable e) {
            throw debugContext.handle(e);
        }
    }
}

