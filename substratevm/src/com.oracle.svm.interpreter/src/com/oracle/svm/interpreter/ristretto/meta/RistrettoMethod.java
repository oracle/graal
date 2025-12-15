/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.meta;

import java.util.function.Function;

import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.profile.MethodProfile;
import com.oracle.svm.interpreter.ristretto.RistrettoConstants;
import com.oracle.svm.interpreter.ristretto.RistrettoUtils;

import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

/**
 * JVMCI representation of a {@link jdk.vm.ci.meta.ResolvedJavaMethod} used by Ristretto for
 * compilation. Exists once per {@link InterpreterResolvedJavaMethod}. Allocated before the start of
 * a runtime compilation by {@link RistrettoUtils}. Acts as the major connection link between
 * substrate's JVMCI world and the interpreter's JVMCI world.
 * <p>
 * Additionally, holds necessary information for profiling and runtime compilation code management.
 * See {@link com.oracle.svm.interpreter.ristretto.profile.RistrettoCompilationManager} and
 * {@link com.oracle.svm.interpreter.ristretto.profile.RistrettoProfileSupport} for details.
 * <p>
 * Life cycle: lives until the referencing {@link InterpreterResolvedJavaMethod} is gc-ed.
 */
public final class RistrettoMethod extends SubstrateMethod {
    private final InterpreterResolvedJavaMethod interpreterMethod;
    private RistrettoConstantPool ristrettoConstantPool;

    // JIT COMPILER SUPPORT START
    /**
     * Field exposed for profiling support for this method. Initialized once upon first profiling
     * under heavy synchronization. Never written again. If a ristretto method is GCed profile is
     * lost.
     */
    private MethodProfile profile;
    /**
     * State-machine for compilation handling of this crema method. Every methods starts in a
     * NEVER_COMPILED state and than can cycle through different states.
     * <p>
     * TODO expand docs once this becomes more sophisticated.
     */
    public volatile int compilationState = RistrettoConstants.COMPILE_STATE_INIT_VAL;

    /**
     * Pointer to the svm installed code, if compilationState==COMPILED should be non-null.
     * <p>
     * TODO - deoptimization and retirement of this pointer not implemented yet.
     */
    public volatile InstalledCode installedCode;
    // JIT COMPILER SUPPORT END

    private RistrettoMethod(InterpreterResolvedJavaMethod interpreterMethod) {
        super(0, null, 0, null, 0, null);
        this.interpreterMethod = interpreterMethod;
        this.declaringClass = RistrettoType.create(interpreterMethod.getDeclaringClass());
        this.signature = new RistrettoUnresolvedSignature(interpreterMethod.getSignature());
    }

    public InterpreterResolvedJavaMethod getInterpreterMethod() {
        return interpreterMethod;
    }

    private static final Function<InterpreterResolvedJavaMethod, ResolvedJavaMethod> RISTRETTO_METHOD_FUNCTION = RistrettoMethod::new;

    public static RistrettoMethod create(InterpreterResolvedJavaMethod interpreterMethod) {
        return (RistrettoMethod) interpreterMethod.getRistrettoMethod(RISTRETTO_METHOD_FUNCTION);
    }

    public MethodProfile getProfile() {
        if (profile == null) {
            initializeProfile();
        }
        return profile;
    }

    /**
     * Allocate the profile once per method. Apart from test scenarios the profile is never set to
     * null again. Thus, the heavy locking code below is normally not run in a fast path.
     */
    private synchronized void initializeProfile() {
        if (profile == null) {
            MethodProfile newProfile = new MethodProfile(this);
            // ensure everything is allocated and initialized before we signal the barrier
            // for the publishing write
            MembarNode.memoryBarrier(MembarNode.FenceKind.STORE_STORE);
            profile = newProfile;
        }
    }

    public synchronized void resetProfile() {
        profile = null;
    }

    @Override
    public boolean canBeInlined() {
        final boolean wasCompiledAOT = RistrettoUtils.wasAOTCompiled(this.getInterpreterMethod());
        if (!wasCompiledAOT) {
            // until GR-71589 is fixed we assume every other method can be inlined
            return RistrettoUtils.runtimeBytecodesAvailable(this.getInterpreterMethod());
        }
        return false;
    }

    @Override
    public String getName() {
        return interpreterMethod.getName();
    }

    @Override
    public byte[] getCode() {
        return interpreterMethod.getCode();
    }

    @Override
    public int getCodeSize() {
        return interpreterMethod.getCodeSize();
    }

    @Override
    public int getMaxStackSize() {
        return interpreterMethod.getMaxStackSize();
    }

    @Override
    public int getMaxLocals() {
        return interpreterMethod.getMaxLocals();
    }

    @Override
    public ConstantPool getConstantPool() {
        if (ristrettoConstantPool == null) {
            ristrettoConstantPool = RistrettoConstantPool.create(interpreterMethod.getConstantPool());
        }
        return ristrettoConstantPool;
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        return interpreterMethod.getLineNumberTable();
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        return interpreterMethod.getLocalVariableTable();
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        return interpreterMethod.getExceptionHandlers();
    }

    @Override
    public Signature getSignature() {
        return new RistrettoUnresolvedSignature(interpreterMethod.getSignature());
    }

    @Override
    public String toString() {
        return "RistrettoMethod{super=" + super.toString() + ", interpreterMethod=" + interpreterMethod + "}";
    }

    @Override
    public SubstrateType getDeclaringClass() {
        return RistrettoType.create(interpreterMethod.getDeclaringClass());
    }

    @Override
    public int getModifiers() {
        return interpreterMethod.getModifiers();
    }

    @Override
    public boolean isVarArgs() {
        return interpreterMethod.isVarArgs();
    }

    @Override
    public boolean isInterface() {
        return interpreterMethod.isInterface();
    }

    @Override
    public boolean isSynchronized() {
        return interpreterMethod.isSynchronized();
    }

    @Override
    public boolean isStatic() {
        return interpreterMethod.isStatic();
    }

    @Override
    public boolean isConstructor() {
        return interpreterMethod.isConstructor();
    }

    @Override
    public boolean isFinalFlagSet() {
        return interpreterMethod.isFinalFlagSet();
    }

    @Override
    public boolean isPublic() {
        return interpreterMethod.isPublic();
    }

    @Override
    public boolean isPackagePrivate() {
        return interpreterMethod.isPackagePrivate();
    }

    @Override
    public boolean isPrivate() {
        return interpreterMethod.isPrivate();
    }

    @Override
    public boolean isProtected() {
        return interpreterMethod.isProtected();
    }

    @Override
    public boolean isTransient() {
        return interpreterMethod.isTransient();
    }

    @Override
    public boolean isStrict() {
        return interpreterMethod.isStrict();
    }

    @Override
    public boolean isVolatile() {
        return interpreterMethod.isVolatile();
    }

    @Override
    public boolean isNative() {
        return interpreterMethod.isNative();
    }

    @Override
    public boolean isAbstract() {
        return interpreterMethod.isAbstract();
    }

    @Override
    public boolean isConcrete() {
        return interpreterMethod.isConcrete();
    }

}
