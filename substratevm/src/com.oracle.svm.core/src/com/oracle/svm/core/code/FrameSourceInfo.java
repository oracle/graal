/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import java.lang.module.ModuleDescriptor;
import java.util.Optional;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.hub.DynamicHub;

import jdk.graal.compiler.nodes.FrameState;
import jdk.internal.loader.BuiltinClassLoader;

public abstract class FrameSourceInfo {
    public static final int LINENUMBER_UNKNOWN = -1;
    public static final int LINENUMBER_NATIVE = -2;

    protected Class<?> sourceClass;
    protected String sourceMethodName;
    protected int sourceLineNumber;
    protected long encodedBci;

    protected FrameSourceInfo(Class<?> sourceClass, String sourceMethodName, int sourceLineNumber, int bci) {
        this.sourceClass = sourceClass;
        this.sourceMethodName = sourceMethodName;
        this.sourceLineNumber = sourceLineNumber;
        this.encodedBci = FrameInfoEncoder.encodeBci(bci, FrameState.StackState.BeforePop);
    }

    @SuppressWarnings("this-escape")
    protected FrameSourceInfo() {
        init();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void init() {
        sourceClass = CodeInfoEncoder.Encoders.INVALID_CLASS;
        sourceMethodName = CodeInfoEncoder.Encoders.INVALID_METHOD_NAME;
        sourceLineNumber = LINENUMBER_UNKNOWN;
        encodedBci = -1;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public Class<?> getSourceClass() {
        fillSourceFieldsIfMissing();
        return sourceClass;
    }

    public String getSourceClassName() {
        Class<?> clazz = getSourceClass();
        return (clazz != null) ? clazz.getName() : "";
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public String getSourceMethodName() {
        fillSourceFieldsIfMissing();
        return sourceMethodName;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getSourceLineNumber() {
        return sourceLineNumber;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public String getSourceFileName() {
        Class<?> clazz = getSourceClass();
        return (clazz != null) ? DynamicHub.fromClass(clazz).getSourceFileName() : null;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract void fillSourceFieldsIfMissing();

    /**
     * Returns the bytecode index.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getBci() {
        return FrameInfoDecoder.decodeBci(encodedBci);
    }

    public boolean isNativeMethod() {
        return sourceLineNumber == LINENUMBER_NATIVE;
    }

    /**
     * Returns the name and source code location of the method.
     */
    public StackTraceElement getSourceReference() {
        fillSourceFieldsIfMissing();

        if (sourceClass == null) {
            return new StackTraceElement("", getSourceMethodName(), null, getSourceLineNumber());
        }

        return getSourceReference(getSourceClass(), getSourceMethodName(), getSourceLineNumber());
    }

    public static StackTraceElement getSourceReference(Class<?> sourceClass, String sourceMethodName, int sourceLineNumber) {
        ClassLoader classLoader = sourceClass.getClassLoader();
        String classLoaderName = null;
        if (classLoader != null && !(classLoader instanceof BuiltinClassLoader)) {
            classLoaderName = classLoader.getName();
        }

        Module module = sourceClass.getModule();
        String moduleName = module.getName();
        String moduleVersion = Optional.ofNullable(module.getDescriptor())
                        .flatMap(ModuleDescriptor::version)
                        .map(ModuleDescriptor.Version::toString)
                        .orElse(null);
        String className = sourceClass.getName();
        String sourceFileName = DynamicHub.fromClass(sourceClass).getSourceFileName();

        return new StackTraceElement(classLoaderName, moduleName, moduleVersion, className, sourceMethodName, sourceFileName, sourceLineNumber);
    }
}
