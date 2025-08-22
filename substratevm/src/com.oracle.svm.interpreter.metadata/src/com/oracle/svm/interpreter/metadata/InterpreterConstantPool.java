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

import java.util.List;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.BuildPhaseProvider.AfterAnalysis;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.ConstantPool;
import com.oracle.svm.espresso.classfile.ParserConstantPool;
import com.oracle.svm.interpreter.metadata.serialization.VisibleForSerialization;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaMethod;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * JVMCI's {@link jdk.vm.ci.meta.ConstantPool} is not designed to be used in a performance-sensitive
 * bytecode interpreter, so a Espresso-like CP implementation is used instead for performance.
 * <p>
 * This class doesn't support runtime resolution on purpose, but supports pre-resolved entries
 * instead for AOT types.
 */
public class InterpreterConstantPool extends ConstantPool implements jdk.vm.ci.meta.ConstantPool {

    final InterpreterResolvedObjectType holder;
    final ParserConstantPool parserConstantPool;

    // Assigned after analysis.
    @UnknownObjectField(availability = AfterAnalysis.class, types = Object[].class) protected Object[] cachedEntries;

    Object objAt(int cpi) {
        if (cpi == 0) {
            // 0 implies unknown (!= unresolved) e.g. unknown class, field, method ...
            // In this case it's not possible to even provide a name or symbolic representation for
            // what's missing.
            // Index 0 must be handled by the resolution methods e.g. resolveType, resolveMethod ...
            // where an appropriate error should be thrown.
            throw VMError.shouldNotReachHere("Cannot resolve CP entry 0");
        }
        return cachedEntries[cpi];
    }

    protected InterpreterConstantPool(InterpreterResolvedObjectType holder, ParserConstantPool parserConstantPool, Object[] cachedEntries) {
        super(parserConstantPool);
        this.holder = MetadataUtil.requireNonNull(holder);
        this.parserConstantPool = parserConstantPool;
        this.cachedEntries = MetadataUtil.requireNonNull(cachedEntries);
    }

    protected InterpreterConstantPool(InterpreterResolvedObjectType holder, ParserConstantPool parserConstantPool) {
        this(holder, parserConstantPool, new Object[parserConstantPool.length()]);
    }

    @VisibleForSerialization
    public static InterpreterConstantPool create(InterpreterResolvedObjectType holder, ParserConstantPool parserConstantPool, Object[] cachedEntries) {
        return new InterpreterConstantPool(holder, parserConstantPool, cachedEntries);
    }

    @Override
    public int length() {
        return cachedEntries.length;
    }

    @Override
    public JavaField lookupField(int cpi, ResolvedJavaMethod method, int opcode) {
        return (JavaField) objAt(cpi);
    }

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode) {
        return (JavaMethod) objAt(cpi);
    }

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode, ResolvedJavaMethod caller) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public List<BootstrapMethodInvocation> lookupBootstrapMethodInvocations(boolean invokeDynamic) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public JavaType lookupType(int cpi, int opcode) {
        return (JavaType) objAt(cpi);
    }

    @Override
    public Object lookupConstant(int cpi) {
        Object entry = objAt(cpi);
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
        return (JavaConstant) objAt(cpi);
    }

    @VisibleForSerialization
    @Platforms(Platform.HOSTED_ONLY.class)
    public Object[] getCachedEntries() {
        return cachedEntries;
    }

    public Object peekCachedEntry(int cpi) {
        return cachedEntries[cpi];
    }

    public InterpreterResolvedObjectType getHolder() {
        return holder;
    }

    @Override
    public RuntimeException classFormatError(String message) {
        throw new ClassFormatError(message);
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

    @Override
    public ParserConstantPool getParserConstantPool() {
        return parserConstantPool;
    }

    protected Object resolve(int cpi, @SuppressWarnings("unused") InterpreterResolvedObjectType accessingClass) {
        assert Thread.holdsLock(this);
        assert cpi != 0; // guaranteed by the caller

        @SuppressWarnings("unused")
        Tag tag = tagAt(cpi); // CPI bounds check

        Object entry = cachedEntries[cpi];
        if (isUnresolved(entry)) {
            /*
             * Runtime resolution is deliberately unsupported for AOT types (using base
             * InterpreterConstantPool). This can be relaxed in the future e.g. by attaching a
             * RuntimeInterpreterConstantPool instead.
             */
            throw new UnsupportedResolutionException();
        }

        return entry;
    }

    public Object resolvedAt(int cpi, InterpreterResolvedObjectType accessingClass) {
        Object entry = cachedEntries[cpi];
        if (isUnresolved(entry)) {
            // TODO(peterssen): GR-68611 Avoid deadlocks when hitting breakpoints (JDWP debugger)
            // during class resolution.
            /*
             * Class resolution can run arbitrary code (not in the to-be resolved class <clinit>
             * but) in the user class loaders where it can hit a breakpoint (JDWP debugger), causing
             * a deadlock.
             */
            synchronized (this) {
                entry = cachedEntries[cpi];
                if (isUnresolved(entry)) {
                    cachedEntries[cpi] = entry = resolve(cpi, accessingClass);
                }
            }
        }

        return entry;
    }

    private static boolean isUnresolved(Object entry) {
        return entry == null || entry instanceof UnresolvedJavaType || entry instanceof UnresolvedJavaMethod || entry instanceof UnresolvedJavaField;
    }

    @SuppressWarnings("unchecked")
    protected static <T extends Throwable> RuntimeException uncheckedThrow(Throwable t) throws T {
        throw (T) t;
    }

    public InterpreterResolvedJavaField resolvedFieldAt(InterpreterResolvedObjectType accessingKlass, int cpi) {
        Object resolvedEntry = resolvedAt(cpi, accessingKlass);
        assert resolvedEntry != null;
        return (InterpreterResolvedJavaField) resolvedEntry;
    }

    public InterpreterResolvedJavaMethod resolvedMethodAt(InterpreterResolvedObjectType accessingKlass, int cpi) {
        Object resolvedEntry = resolvedAt(cpi, accessingKlass);
        assert resolvedEntry != null;
        return (InterpreterResolvedJavaMethod) resolvedEntry;
    }

    public InterpreterResolvedObjectType resolvedTypeAt(InterpreterResolvedObjectType accessingKlass, int cpi) {
        Object resolvedEntry = resolvedAt(cpi, accessingKlass);
        assert resolvedEntry != null;
        return (InterpreterResolvedObjectType) resolvedEntry;
    }

    public String resolveStringAt(int cpi) {
        Object resolvedEntry = resolvedAt(cpi, null);
        if (resolvedEntry instanceof ReferenceConstant<?> referenceConstant) {
            resolvedEntry = referenceConstant.getReferent();
        }
        assert resolvedEntry != null;
        return (String) resolvedEntry;
    }

    @Override
    public int intAt(int index) {
        checkTag(index, CONSTANT_Integer);
        Object entry = cachedEntries[index];
        assert entry == null || entry instanceof PrimitiveConstant;
        if (entry instanceof PrimitiveConstant primitiveConstant) {
            assert primitiveConstant.getJavaKind() == JavaKind.Int;
            return primitiveConstant.asInt();
        }
        return super.intAt(index);
    }

    @Override
    public float floatAt(int index) {
        checkTag(index, CONSTANT_Float);
        Object entry = cachedEntries[index];
        assert entry == null || entry instanceof PrimitiveConstant;
        if (entry instanceof PrimitiveConstant primitiveConstant) {
            assert primitiveConstant.getJavaKind() == JavaKind.Float;
            return primitiveConstant.asFloat();
        }
        return super.floatAt(index);
    }

    @Override
    public double doubleAt(int index) {
        checkTag(index, CONSTANT_Double);
        Object entry = cachedEntries[index];
        assert entry == null || entry instanceof PrimitiveConstant;
        if (entry instanceof PrimitiveConstant primitiveConstant) {
            assert primitiveConstant.getJavaKind() == JavaKind.Double;
            return primitiveConstant.asDouble();
        }
        return super.doubleAt(index);
    }

    @Override
    public long longAt(int index) {
        checkTag(index, CONSTANT_Long);
        Object entry = cachedEntries[index];
        assert entry == null || entry instanceof PrimitiveConstant;
        if (entry instanceof PrimitiveConstant primitiveConstant) {
            assert primitiveConstant.getJavaKind() == JavaKind.Long;
            return primitiveConstant.asLong();
        }
        return super.longAt(index);
    }
}
