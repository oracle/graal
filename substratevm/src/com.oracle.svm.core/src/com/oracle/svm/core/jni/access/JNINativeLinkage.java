/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jni.access;

import static com.oracle.svm.core.jni.access.JNIReflectionDictionary.WRAPPED_CSTRING_EQUIVALENCE;
import static com.oracle.svm.core.libjvm.WhiteBoxEntryPoints.WHITEBOX_REGISTER_NATIVES;
import static com.oracle.svm.core.libjvm.WhiteBoxEntryPoints.WHITEBOX_REGISTER_NATIVES_SYMBOL;

import java.util.Map;
import java.util.function.Function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.jdk.Target_java_lang_ClassLoader;
import com.oracle.svm.shared.BuildPhaseProvider;

import jdk.internal.vm.annotation.Stable;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Encapsulates the code address of a {@code native} method's implementation at runtime. This object
 * is accessed in the method's compiled {@code JNINativeCallWrapperMethod}.
 */
public final class JNINativeLinkage {

    private PointerBase entryPoint = Word.nullPointer();

    @UnknownObjectField(availability = BuildPhaseProvider.AfterAnalysis.class) //
    @Stable //
    private DynamicHub declaringClass;
    private final CharSequence declaringClassName;
    private final CharSequence name;
    private final CharSequence descriptor;

    private CGlobalDataInfo builtInAddress = null;

    /**
     * Creates an object for linking the address of a native method.
     *
     * @param declaringClass the class declaring the native method
     * @param name the name of the native method
     * @param descriptor the {@linkplain Signature#toMethodDescriptor() descriptor} of the native
     *            method
     */
    public JNINativeLinkage(DynamicHub declaringClass, CharSequence name, CharSequence descriptor) {
        assert declaringClass != null;
        this.declaringClass = declaringClass;
        this.declaringClassName = MetaUtil.toInternalName(declaringClass.getName());
        this.name = name;
        this.descriptor = descriptor;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public JNINativeLinkage(ResolvedJavaType declaringClass, CharSequence name, CharSequence descriptor) {
        this.declaringClassName = MetaUtil.toInternalName(declaringClass.toClassName());
        this.name = name;
        this.descriptor = descriptor;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setDeclaringClass(DynamicHub declaringClass) {
        assert this.declaringClass == null;
        assert MetaUtil.toInternalName(declaringClass.getName()).equals(getDeclaringClassName());
        this.declaringClass = declaringClass;
    }

    public String getDeclaringClassName() {
        return (String) declaringClassName;
    }

    public String getMethodName() {
        return (String) name;
    }

    public String getDescriptor() {
        return (String) descriptor;
    }

    public boolean isBuiltInFunction() {
        return PlatformNativeLibrarySupport.singleton().isBuiltinNative(getShortSymbol());
    }

    public CGlobalDataInfo getOrCreateBuiltInAddress(Function<String, CGlobalDataInfo> createSymbol) {
        assert isBuiltInFunction();
        if (builtInAddress == null) {
            builtInAddress = createSymbol.apply(getShortSymbol());
        }
        return builtInAddress;
    }

    /**
     * Sets the native address for the {@code native} method represented by this object.
     */
    public void setEntryPoint(CFunctionPointer fnptr) {
        entryPoint = fnptr;
    }

    /**
     * Resets the entry point stored for the native method represented by this object, triggering a
     * symbol lookup when the method is called the next time.
     */
    public void unsetEntryPoint() {
        entryPoint = Word.nullPointer();
    }

    @Override
    public int hashCode() {
        return (((name.hashCode() * 31) + descriptor.hashCode()) * 31) + declaringClassName.hashCode();
    }

    /**
     * Returns {@code true} iff {@code obj} is a {@link JNINativeLinkage} and has the same declaring
     * class, name and descriptor as this object.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof JNINativeLinkage that) {
            return (that == this) ||
                            (WRAPPED_CSTRING_EQUIVALENCE.equals(declaringClassName, that.declaringClassName) &&
                                            WRAPPED_CSTRING_EQUIVALENCE.equals(name, that.name) &&
                                            WRAPPED_CSTRING_EQUIVALENCE.equals(descriptor, that.descriptor));
        }
        return false;
    }

    @Override
    public String toString() {
        return MetaUtil.internalNameToJava(getDeclaringClassName(), true, false) + "." + name + descriptor +
                        " [symbol: " + getShortSymbol() + " or " + getLongSymbol() + "]";

    }

    /// Native methods whose implementation is a method in the VM annotated by `CEntryPoint`.
    /// These methods have no symbols in an external library that would be processed by
    /// `JNILibraryLoadFeature`. Furthermore, this table only contains entries for
    /// classes not known at build-time as build-time classes will have
    /// `JNINativeCallWrapperMethod`s generated for their native methods.
    ///
    /// This is similar to the `lookup_special_native_methods` table in
    /// the HotSpot source file.
    private static final Map<String, CEntryPointLiteral<?>> VM_IMPLEMENTED_NATIVE_METHODS = Map.of(
                    WHITEBOX_REGISTER_NATIVES_SYMBOL, WHITEBOX_REGISTER_NATIVES);

    /// Gets the native address for the `native` method represented by this object.
    ///
    /// If the entry point has not been resolved yet, this method asks the declaring class loader.
    public PointerBase getOrFindEntryPoint() {
        if (entryPoint.isNull()) {
            Class<?> classObject = null;
            ClassLoader classLoader = null;
            /*
             * When class loaders are ignored, NativeLibraries.find is substituted with a global
             * NativeLibrarySupport lookup, so neither the declaring class nor its loader is needed.
             */
            if (ClassRegistries.respectClassLoader()) {
                classObject = DynamicHub.toClass(declaringClass);
                classLoader = declaringClass.getClassLoader();
            }
            String shortSymbol = getShortSymbol();

            // If the loader is null, this is a lookup for a system class, so look
            // for an internally implemented method.
            if (classLoader == null) {
                CEntryPointLiteral<?> val = VM_IMPLEMENTED_NATIVE_METHODS.get(shortSymbol);
                if (val != null) {
                    entryPoint = val.getFunctionPointer();
                }
            }

            if (entryPoint.isNull()) {
                entryPoint = Word.pointer(Target_java_lang_ClassLoader.findNative(classLoader, classObject, shortSymbol, getMethodName()));
                if (entryPoint.isNull()) {
                    String longSymbol = getLongSymbol();
                    entryPoint = Word.pointer(Target_java_lang_ClassLoader.findNative(classLoader, classObject, longSymbol, getMethodName()));
                    if (entryPoint.isNull()) {
                        throw new UnsatisfiedLinkError(toString());
                    }
                }
            }
        }
        return entryPoint;
    }

    /// Gets the native method's JNI-mangled qualified name without a signature suffix.
    ///
    /// For example, `java.lang.Object.registerNatives()` maps to
    /// `Java_java_lang_Object_registerNatives`.
    public String getShortSymbol() {
        StringBuilder sb = new StringBuilder("Java_");
        mangleName(getDeclaringClassName(), 1, getDeclaringClassName().length() - 1, sb);
        sb.append('_');
        mangleName(getMethodName(), 0, name.length(), sb);
        return sb.toString();
    }

    /// Gets the native method's JNI-mangled qualified name with a signature suffix.
    ///
    /// For example, `pkg.Native.method(int)` maps to `Java_pkg_Native_method__I`.
    public String getLongSymbol() {
        return getShortSymbol() + "__" + getSignature();
    }

    private String getSignature() {
        int closing = getDescriptor().indexOf(')');
        assert getDescriptor().startsWith("(") && getDescriptor().indexOf(')') == closing && closing != -1;
        return mangleName(getDescriptor(), 1, closing, new StringBuilder()).toString();
    }

    private static StringBuilder mangleName(String name, int beginIndex, int endIndex, StringBuilder sb) {
        // from OpenJDK: nativeLookup.cpp, mangle_name_on()
        for (int i = beginIndex; i < endIndex; i++) {
            char c = name.charAt(i);
            if (c <= 0x7f && Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else {
                switch (c) {
                    case '/':
                        sb.append("_");
                        break;
                    case '_':
                        sb.append("_1");
                        break;
                    case ';':
                        sb.append("_2");
                        break;
                    case '[':
                        sb.append("_3");
                        break;
                    default: // _0xxxx, where xxxx is lower-case hexadecimal Unicode value
                        sb.append('_');
                        String hex = Integer.toHexString(c);
                        // padding
                        sb.repeat("0", 5 - hex.length());
                        sb.append(hex);
                        break;
                }
            }
        }
        return sb;
    }
}
