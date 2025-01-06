/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata;

import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEDYNAMIC;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.interpreter.metadata.serialization.VisibleForSerialization;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

public final class InterpreterConstantPool implements ConstantPool {

    private final InterpreterResolvedObjectType holder;
    // Assigned after analysis.
    @UnknownObjectField(types = Object[].class) private Object[] entries;

    Object at(int cpi) {
        if (cpi == 0) {
            // 0 implies unknown (!= unresolved) e.g. unknown class, field, method ...
            // In this case it's not possible to even provide a name or symbolic representation for
            // what's missing.
            // Index 0 must be handled by the resolution methods e.g. resolveType, resolveMethod ...
            // where an appropriate error should be thrown.
            throw VMError.shouldNotReachHere("Cannot resolve CP entry 0");
        }
        return entries[cpi];
    }

    private InterpreterConstantPool(InterpreterResolvedObjectType holder, Object[] entries) {
        this.holder = MetadataUtil.requireNonNull(holder);
        this.entries = MetadataUtil.requireNonNull(entries);
    }

    @VisibleForSerialization
    public static InterpreterConstantPool create(InterpreterResolvedObjectType holder, Object[] entries) {
        return new InterpreterConstantPool(holder, entries);
    }

    @Override
    public int length() {
        return entries.length;
    }

    @Override
    public JavaField lookupField(int cpi, ResolvedJavaMethod method, int opcode) {
        return (JavaField) at(cpi);
    }

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode) {
        return (JavaMethod) at(cpi);
    }

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode, ResolvedJavaMethod caller) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public JavaType lookupType(int cpi, int opcode) {
        return (JavaType) at(cpi);
    }

    @Override
    public Object lookupConstant(int cpi) {
        Object entry = at(cpi);
        if (entry instanceof JavaConstant) {
            return entry;
        } else if (entry instanceof JavaType) {
            return entry;
        }
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public Object lookupConstant(int cpi, boolean resolve) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public JavaConstant lookupAppendix(int cpi, int opcode) {
        assert opcode == INVOKEDYNAMIC;
        return (JavaConstant) at(cpi);
    }

    @VisibleForSerialization
    @Platforms(Platform.HOSTED_ONLY.class)
    public Object[] getEntries() {
        return entries;
    }

    public InterpreterResolvedObjectType getHolder() {
        return holder;
    }

    // region Unimplemented methods

    @Override
    public void loadReferencedType(int cpi, int opcode) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public JavaType lookupReferencedType(int cpi, int opcode) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public String lookupUtf8(int cpi) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public Signature lookupSignature(int cpi) {
        throw VMError.intentionallyUnimplemented();
    }

    // endregion Unimplemented methods
}
