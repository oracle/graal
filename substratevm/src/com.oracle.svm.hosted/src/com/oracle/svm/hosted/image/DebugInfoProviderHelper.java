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
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.hosted.annotation.CustomSubstitutionType;
import com.oracle.svm.hosted.image.sources.SourceManager;
import com.oracle.svm.hosted.lambda.LambdaSubstitutionType;
import com.oracle.svm.hosted.meta.HostedArrayClass;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedInterface;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedPrimitiveType;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.substitute.InjectedFieldsType;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.hosted.substitute.SubstitutionType;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.nativeimage.ImageSingletons;

import java.nio.file.FileSystems;
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
            javaType = getDeclaringClass((HostedMethod) method, false);
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
        ResolvedJavaType javaType = getDeclaringClass(hostedType, false);
        Class<?> clazz = hostedType.getJavaClass();
        SourceManager sourceManager = ImageSingletons.lookup(SourceManager.class);
        Path fullFilePath;
        try (DebugContext.Scope s = debugContext.scope("DebugFileInfo", hostedType)) {
            Path filePath = sourceManager.findAndCacheSource(javaType, clazz, debugContext);
            if (filePath == null && hostedType instanceof HostedInstanceClass) {
                // conjure up an appropriate, unique file name to keep tools happy
                // even though we cannot find a corresponding source
                filePath = fullFilePathFromClassName((HostedInstanceClass) hostedType);
            }
            fullFilePath = filePath;
        } catch (Throwable e) {
            throw debugContext.handle(e);
        }
        return fullFilePath;
    }

    public static Path getFullFilePathFromField(HostedField hostedField, DebugContext debugContext) {
        ResolvedJavaType javaType = getDeclaringClass(hostedField, false);
        HostedType hostedType = hostedField.getDeclaringClass();
        Class<?> clazz = hostedType.getJavaClass();
        SourceManager sourceManager = ImageSingletons.lookup(SourceManager.class);
        Path fullFilePath;
        try (DebugContext.Scope s = debugContext.scope("DebugFileInfo", hostedType)) {
            fullFilePath = sourceManager.findAndCacheSource(javaType, clazz, debugContext);
        } catch (Throwable e) {
            throw debugContext.handle(e);
        }
        return fullFilePath;
    }

    private static Path fullFilePathFromClassName(HostedInstanceClass hostedInstanceClass) {
        String[] elements = hostedInstanceClass.toJavaName().split("\\.");
        int count = elements.length;
        String name = elements[count - 1];
        while (name.startsWith("$")) {
            name = name.substring(1);
        }
        if (name.contains("$")) {
            name = name.substring(0, name.indexOf('$'));
        }
        if (name.equals("")) {
            name = "_nofile_";
        }
        elements[count - 1] = name + ".java";
        return FileSystems.getDefault().getPath("", elements);
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
                name = getDeclaringClass((HostedMethod) method, true).toJavaName();
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
        return getDeclaringClass(hostedType, true).toJavaName();
    }

    public static int typeSize(HostedType hostedType) {
        if (hostedType instanceof HostedInstanceClass) {
            /* We know the actual instance size in bytes. */
            return ((HostedInstanceClass) hostedType).getInstanceSize();
        } else if (hostedType instanceof HostedArrayClass) {
            /* Use the size of header common to all arrays of this type. */
            return getObjectLayout().getArrayBaseOffset(hostedType.getComponentType().getStorageKind());
        } else if (hostedType instanceof HostedInterface) {
            /* Use the size of the header common to all implementors. */
            return getObjectLayout().getFirstFieldOffset();
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

    // Compute the DWARF encoding for a primitive type. Copied from DwarfInfoSectionImpl
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

    /*
     * HostedType wraps an AnalysisType and both HostedType and AnalysisType punt calls to
     * getSourceFilename to the wrapped class so for consistency we need to do type names and path
     * lookup relative to the doubly unwrapped HostedType.
     *
     * However, note that the result of the unwrap on the AnalysisType may be a SubstitutionType
     * which wraps both an original type and the annotated type that substitutes it. Unwrapping
     * normally returns the AnnotatedType which we need to use to resolve the file name. However, we
     * need to use the original to name the owning type to ensure that names found in method param
     * and return types resolve correctly.
     */
    public static ResolvedJavaType getDeclaringClass(HostedType hostedType, boolean wantOriginal) {
        // unwrap to the underlying class eihter the original or target class
        if (wantOriginal) {
            return getOriginal(hostedType);
        }
        // we want any substituted target if there is one. directly unwrapping will
        // do what we want.
        return hostedType.getWrapped().getWrapped();
    }

    public static ResolvedJavaType getDeclaringClass(HostedMethod hostedMethod, boolean wantOriginal) {
        if (wantOriginal) {
            return getOriginal(hostedMethod.getDeclaringClass());
        }
        // we want a substituted target if there is one. if there is a substitution at the end of
        // the method chain fetch the annotated target class
        ResolvedJavaMethod javaMethod = getAnnotatedOrOriginal(hostedMethod);
        return javaMethod.getDeclaringClass();
    }

    public static ResolvedJavaType getDeclaringClass(HostedField hostedField, boolean wantOriginal) {
        /* for now fields are always reported as belonging to the original class */
        return getOriginal(hostedField.getDeclaringClass());
    }

    public static ResolvedJavaType getOriginal(HostedType hostedType) {
        /* partially unwrap then traverse through substitutions to the original */
        ResolvedJavaType javaType = hostedType.getWrapped().getWrappedWithoutResolve();
        if (javaType instanceof SubstitutionType) {
            return ((SubstitutionType) javaType).getOriginal();
        } else if (javaType instanceof CustomSubstitutionType<?, ?>) {
            return ((CustomSubstitutionType<?, ?>) javaType).getOriginal();
        } else if (javaType instanceof LambdaSubstitutionType) {
            return ((LambdaSubstitutionType) javaType).getOriginal();
        } else if (javaType instanceof InjectedFieldsType) {
            return ((InjectedFieldsType) javaType).getOriginal();
        }
        return javaType;
    }

    public static ResolvedJavaMethod getAnnotatedOrOriginal(HostedMethod hostedMethod) {
        ResolvedJavaMethod javaMethod = hostedMethod.getWrapped().getWrapped();
        // This method is only used when identifying the modifiers or the declaring class
        // of a HostedMethod. Normally the method unwraps to the underlying JVMCI method
        // which is the one that provides bytecode to the compiler as well as, line numbers
        // and local info. If we unwrap to a SubstitutionMethod then we use the annotated
        // method, not the JVMCI method that the annotation refers to since that will be the
        // one providing the bytecode etc used by the compiler. If we unwrap to any other,
        // custom substitution method we simply use it rather than dereferencing to the
        // original. The difference is that the annotated method's bytecode will be used to
        // replace the original and the debugger needs to use it to identify the file and access
        // permissions. A custom substitution may exist alongside the original, as is the case
        // with some uses for reflection. So, we don't want to conflate the custom substituted
        // method and the original. In this latter case the method code will be synthesized without
        // reference to the bytecode of the original. Hence there is no associated file and the
        // permissions need to be determined from the custom substitution method itself.

        if (javaMethod instanceof SubstitutionMethod) {
            SubstitutionMethod substitutionMethod = (SubstitutionMethod) javaMethod;
            javaMethod = substitutionMethod.getAnnotated();
        }
        return javaMethod;
    }

    public static ObjectLayout getObjectLayout() {
        return ConfigurationValues.getObjectLayout();
    }

}
