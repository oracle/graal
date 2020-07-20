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
package com.oracle.svm.jni.access;

import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.hosted.c.CGlobalDataFeature;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.Signature;

/**
 * Encapsulates the code address of a {@code native} method's implementation at runtime. This object
 * is accessed in the method's compiled {@code JNINativeCallWrapperMethod}.
 */
public final class JNINativeLinkage {

    private PointerBase entryPoint = WordFactory.nullPointer();

    private final String declaringClass;
    private final String name;
    private final String descriptor;

    private CGlobalDataInfo builtInAddress = null;

    /**
     * Creates an object for linking the address of a native method.
     *
     * @param declaringClass the {@linkplain JavaType#getName() name} of the class declaring the
     *            native method
     * @param name the name of the native method
     * @param descriptor the {@linkplain Signature#toMethodDescriptor() descriptor} of the native
     *            method
     */
    public JNINativeLinkage(String declaringClass, String name, String descriptor) {
        assert declaringClass.startsWith("L") && declaringClass.endsWith(";") : declaringClass;
        this.declaringClass = declaringClass;
        this.name = name;
        this.descriptor = descriptor;
    }

    public String getDeclaringClassName() {
        return declaringClass;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public boolean isBuiltInFunction() {
        return (PlatformNativeLibrarySupport.singleton().isBuiltinPkgNative(this.getShortName()));
    }

    public CGlobalDataInfo getBuiltInAddress() {
        assert this.isBuiltInFunction();
        if (builtInAddress == null) {
            CGlobalData<CFunctionPointer> linkage = CGlobalDataFactory.forSymbol(this.getShortName());
            builtInAddress = CGlobalDataFeature.singleton().registerAsAccessedOrGet(linkage);
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
        entryPoint = WordFactory.nullPointer();
    }

    @Override
    public int hashCode() {
        return (((name.hashCode() * 31) + descriptor.hashCode()) * 31) + declaringClass.hashCode();
    }

    /**
     * Returns {@code true} iff {@code obj} is a {@link JNINativeLinkage} and has the same declaring
     * class, name and descriptor as this object.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof JNINativeLinkage) {
            JNINativeLinkage that = (JNINativeLinkage) obj;
            return (that == this) ||
                            (this.declaringClass.equals(that.declaringClass) &&
                                            this.name.equals(that.name) &&
                                            this.descriptor.equals(that.descriptor));
        }
        return false;
    }

    @Override
    public String toString() {
        String shortName = getShortName();
        return MetaUtil.internalNameToJava(declaringClass, true, false) + "." + name + descriptor +
                        " [symbol: " + shortName + " or " + shortName + "__" + getSignature() + "]";

    }

    /**
     * Gets the native address for the {@code native} method represented by this object, attempting
     * to resolve it if it is currently 0.
     */
    public PointerBase getOrFindEntryPoint() {
        if (entryPoint.isNull()) {
            String shortName = getShortName();
            entryPoint = NativeLibrarySupport.singleton().findSymbol(shortName);
            if (entryPoint.isNull()) {
                String longName = shortName + "__" + getSignature();
                entryPoint = NativeLibrarySupport.singleton().findSymbol(longName);
                if (entryPoint.isNull()) {
                    throw new UnsatisfiedLinkError(toString());
                }
            }
        }
        return entryPoint;
    }

    private String getShortName() {
        StringBuilder sb = new StringBuilder("Java_");
        mangleName(declaringClass, 1, declaringClass.length() - 1, sb);
        sb.append('_');
        mangleName(name, 0, name.length(), sb);
        return sb.toString();
    }

    private String getSignature() {
        int closing = descriptor.indexOf(')');
        assert descriptor.startsWith("(") && descriptor.indexOf(')') == closing && closing != -1;
        return mangleName(descriptor, 1, closing, new StringBuilder()).toString();
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
                        for (int j = hex.length(); j < 5; j++) {
                            sb.append('0'); // padding
                        }
                        sb.append(hex);
                        break;
                }
            }
        }
        return sb;
    }

    public static String mangle(String s) {
        return mangleName(s, 0, s.length(), new StringBuilder()).toString();
    }
}
