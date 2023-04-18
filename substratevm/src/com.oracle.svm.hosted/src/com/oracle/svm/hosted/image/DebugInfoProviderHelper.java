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

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import com.oracle.objectfile.elf.dwarf.DwarfDebugInfo;
import com.oracle.svm.hosted.image.sources.SourceManager;
import com.oracle.svm.hosted.meta.HostedArrayClass;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedInterface;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedPrimitiveType;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.nativeimage.ImageSingletons;

import java.nio.file.Path;
import java.util.Arrays;

import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugPrimitiveTypeInfo.FLAG_INTEGRAL;
import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugPrimitiveTypeInfo.FLAG_NUMERIC;
import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugPrimitiveTypeInfo.FLAG_SIGNED;

// Common helper methods used in both LLVMDebugInfoProvider and NativeImageDebugInfoProvider
public class DebugInfoProviderHelper {

    public static String getFileName(Path fullFilePath) {
        if (fullFilePath != null) {
            Path filename = fullFilePath.getFileName();
            if (filename != null) {
                return filename.toString();
            }
        }
        return "";
    }

    public static Path getFilePath(Path fullFilePath) {
        if (fullFilePath != null) {
            return fullFilePath.getParent();
        }
        return null;
    }

    public static Path getFullFilePathFromMethod(ResolvedJavaMethod method, DebugContext debugContext) {
        ResolvedJavaType javaType;
        if (method instanceof HostedMethod) {
            javaType = NativeImageDebugInfoProvider.getDeclaringClass((HostedMethod) method, false);
        } else {
            javaType = method.getDeclaringClass();
        }
        Class<?> clazz = null;
        if (javaType instanceof OriginalClassProvider) {
            clazz = ((OriginalClassProvider) javaType).getJavaClass();
        }
        SourceManager sourceManager = ImageSingletons.lookup(SourceManager.class);
        Path fullFilePath;
        try (DebugContext.Scope s = debugContext.scope("DebugFileInfo", javaType)) {
            fullFilePath =  sourceManager.findAndCacheSource(javaType, clazz, debugContext);
        } catch (Throwable e) {
            throw debugContext.handle(e);
        }
        return fullFilePath;
    }

    public static Path getFullFilePathFromType(HostedType hostedType, DebugContext debugContext) {
        ResolvedJavaType javaType = NativeImageDebugInfoProvider.getDeclaringClass(hostedType, false);
        Class<?> clazz = hostedType.getJavaClass();
        SourceManager sourceManager = ImageSingletons.lookup(SourceManager.class);
        Path fullFilePath;
        try (DebugContext.Scope s = debugContext.scope("DebugFileInfo", hostedType)) {
            Path filePath = sourceManager.findAndCacheSource(javaType, clazz, debugContext);
            if (filePath == null && hostedType instanceof HostedInstanceClass) {
                // conjure up an appropriate, unique file name to keep tools happy
                // even though we cannot find a corresponding source
                filePath = NativeImageDebugInfoProvider.fullFilePathFromClassName((HostedInstanceClass) hostedType);
            }
            fullFilePath = filePath;
        } catch (Throwable e) {
            throw debugContext.handle(e);
        }
        return fullFilePath;
    }

    public static int getLineNumber(ResolvedJavaMethod method, int bci) {
        LineNumberTable lineNumberTable = method.getLineNumberTable();
        if (lineNumberTable != null && bci >= 0) {
            return lineNumberTable.getLineNumber(bci);
        }
        return -1;
    }

    public static ResolvedJavaMethod getOriginalMethod(ResolvedJavaMethod method) {
        // unwrap to an original method as far as we can
        ResolvedJavaMethod targetMethod = method;
        while (targetMethod instanceof WrappedJavaMethod) {
            targetMethod = ((WrappedJavaMethod) targetMethod).getWrapped();
        }
        // if we hit a substitution then we can translate to the original
        // for identity otherwise we use whatever we unwrapped to.
        if (targetMethod instanceof SubstitutionMethod) {
            targetMethod = ((SubstitutionMethod) targetMethod).getOriginal();
        }
        return targetMethod;
    }

    public static String getMethodName(ResolvedJavaMethod method) {
        ResolvedJavaMethod targetMethod = getOriginalMethod(method);
        String name = targetMethod.getName();
        if (name.equals("<init>")) {
            if (method instanceof HostedMethod) {
                name = NativeImageDebugInfoProvider.getDeclaringClass((HostedMethod) method, true).toJavaName();
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

    // Get the local variables present at a bci for the current method
    public static Local[] getLocalsBySlot(ResolvedJavaMethod method, int bci) {
        LocalVariableTable lvt = method.getLocalVariableTable();
        Local[] nonEmptySortedLocals = null;
        if (lvt != null) {
            Local[] locals = lvt.getLocalsAt(bci);
            if (locals != null && locals.length > 0) {
                nonEmptySortedLocals = Arrays.copyOf(locals, locals.length);
                Arrays.sort(nonEmptySortedLocals, (Local l1, Local l2) -> l1.getSlot() - l2.getSlot());
            }
        }
        return nonEmptySortedLocals;
    }

    public static String toJavaName(@SuppressWarnings("hiding") HostedType hostedType) {
        return NativeImageDebugInfoProvider.getDeclaringClass(hostedType, true).toJavaName();
    }

    public static int typeSize(HostedType hostedType) {
        if (hostedType instanceof HostedInstanceClass) {
            /* We know the actual instance size in bytes. */
            return ((HostedInstanceClass) hostedType).getInstanceSize();
        } else if (hostedType instanceof HostedArrayClass) {
            /* Use the size of header common to all arrays of this type. */
            return NativeImageDebugInfoProvider.getObjectLayout().getArrayBaseOffset(hostedType.getComponentType().getStorageKind());
        } else if (hostedType instanceof HostedInterface) {
            /* Use the size of the header common to all implementors. */
            return NativeImageDebugInfoProvider.getObjectLayout().getFirstFieldOffset();
        } else {
            /* Use the number of bytes needed needed to store the value. */
            assert hostedType instanceof HostedPrimitiveType;
            JavaKind javaKind = hostedType.getStorageKind();
            return (javaKind == JavaKind.Void ? 0 : javaKind.getByteCount());
        }
    }

    public static int getPrimitiveFlags(HostedPrimitiveType primitiveType) {
        char typeChar = primitiveType.getStorageKind().getTypeChar();
        switch (typeChar) {
            case 'B':
            case 'S':
            case 'I':
            case 'J': {
                return FLAG_NUMERIC | FLAG_INTEGRAL | FLAG_SIGNED;
            }
            case 'C': {
                return FLAG_NUMERIC | FLAG_INTEGRAL;
            }
            case 'F':
            case 'D': {
                return FLAG_NUMERIC;
            }
            default: {
                assert typeChar == 'V' || typeChar == 'Z';
                return 0;
            }
        }
    }

    public static int getPrimitiveBitCount(HostedPrimitiveType primitiveType) {
        JavaKind javaKind = primitiveType.getStorageKind();
        return (javaKind == JavaKind.Void ? 0 : javaKind.getBitCount());
    }

    // Compute the DWARF encoding for a primitive type
    public static byte computeEncoding(int flags, int bitCount) {
        assert bitCount > 0;
        if ((flags & DebugInfoProvider.DebugPrimitiveTypeInfo.FLAG_NUMERIC) != 0) {
            if (((flags & DebugInfoProvider.DebugPrimitiveTypeInfo.FLAG_INTEGRAL) != 0)) {
                if ((flags & DebugInfoProvider.DebugPrimitiveTypeInfo.FLAG_SIGNED) != 0) {
                    switch (bitCount) {
                        case 8:
                            return DwarfDebugInfo.DW_ATE_signed_char;
                        default:
                            assert bitCount == 16 || bitCount == 32 || bitCount == 64;
                            return DwarfDebugInfo.DW_ATE_signed;
                    }
                } else {
                    assert bitCount == 16;
                    return DwarfDebugInfo.DW_ATE_unsigned;
                }
            } else {
                assert bitCount == 32 || bitCount == 64;
                return DwarfDebugInfo.DW_ATE_float;
            }
        } else {
            assert bitCount == 1;
            return DwarfDebugInfo.DW_ATE_boolean;
        }
    }
}
