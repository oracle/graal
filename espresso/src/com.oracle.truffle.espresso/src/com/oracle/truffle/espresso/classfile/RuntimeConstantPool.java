/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.constantpool.ClassConstant;
import com.oracle.truffle.espresso.classfile.constantpool.DynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.FieldRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.InvokeDynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.NeedsFreshResolutionException;
import com.oracle.truffle.espresso.classfile.constantpool.PoolConstant;
import com.oracle.truffle.espresso.classfile.constantpool.Resolvable;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class RuntimeConstantPool extends ConstantPool {

    private final EspressoContext context;
    private final ImmutableConstantPool pool;
    private final StaticObject classLoader;

    @CompilationFinal(dimensions = 1) //
    private final Resolvable.ResolvedConstant[] constants;

    public RuntimeConstantPool(EspressoContext context, ImmutableConstantPool pool, StaticObject classLoader) {
        this.context = context;
        this.pool = pool;
        this.constants = new Resolvable.ResolvedConstant[pool.length()];
        this.classLoader = classLoader;
    }

    @Override
    public int length() {
        return pool.length();
    }

    @Override
    public byte[] getRawBytes() {
        return pool.getRawBytes();
    }

    @Override
    public PoolConstant at(int index, String description) {
        return pool.at(index, description);
    }

    private Resolvable.ResolvedConstant outOfLockResolvedAt(ObjectKlass accessingKlass, int index, String description) {
        Resolvable.ResolvedConstant c = constants[index];
        if (c == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // double check: deopt is a heavy operation.
            c = constants[index];
            if (c == null) {
                Resolvable.ResolvedConstant locallyResolved = ((Resolvable) pool.at(index, description)).resolve(this, index, accessingKlass);
                synchronized (this) {
                    // Triple check: non-trivial resolution
                    c = constants[index];
                    if (c == null) {
                        constants[index] = c = locallyResolved;
                    }
                }
            }
        }
        return c;
    }

    private Resolvable.ResolvedConstant resolvedAt(ObjectKlass accessingKlass, int index, String description) {
        Resolvable.ResolvedConstant c = constants[index];
        if (c == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                // fence += 1;
                // FIXME(peterssen): Add memory fence for array read.
                c = constants[index];
                if (c == null) {
                    constants[index] = c = ((Resolvable) pool.at(index, description)).resolve(this, index, accessingKlass);
                }
            }
        }
        return c;
    }

    private Resolvable.ResolvedConstant resolvedAtNoCache(ObjectKlass accessingKlass, int index, String description) {
        CompilerAsserts.neverPartOfCompilation();
        return ((Resolvable) pool.at(index, description)).resolve(this, index, accessingKlass);
    }

    public StaticObject resolvedStringAt(int index) {
        Resolvable.ResolvedConstant resolved = resolvedAt(null, index, "string");
        return (StaticObject) resolved.value();
    }

    public Klass resolvedKlassAt(ObjectKlass accessingKlass, int index) {
        Resolvable.ResolvedConstant resolved = resolvedAt(accessingKlass, index, "klass");
        return (Klass) resolved.value();
    }

    public Field resolvedFieldAt(ObjectKlass accessingKlass, int index) {
        Resolvable.ResolvedConstant resolved = resolvedAt(accessingKlass, index, "field");
        try {
            return ((Field) resolved.value());
        } catch (NeedsFreshResolutionException e) {
            // clear the constants cache and re-resolve
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                constants[index] = null;
            }
            return resolvedFieldAt(accessingKlass, index);
        }
    }

    public Field resolveFieldAndUpdate(ObjectKlass accessingKlass, int index, Field field) {
        CompilerAsserts.neverPartOfCompilation();
        try {
            Resolvable.ResolvedConstant resolved = resolvedAtNoCache(accessingKlass, index, "field");
            // a compatible field was found, so update the entry
            synchronized (this) {
                constants[index] = resolved;
            }
            return ((Field) resolved.value());
        } catch (EspressoException e) {
            Field realField = field;
            if (realField.hasCompatibleField()) {
                realField = realField.getCompatibleField();
            }
            // A new compatible field was not found, but we still allow
            // obsolete code to use the latest known resolved field.
            // To avoid a de-opt loop here, we create a compatible delegation
            // field that actually uses the latest known resolved field
            // underneath.
            synchronized (this) {
                Field delegationField = context.getClassRedefinition().createDelegationFrom(realField);
                Resolvable.ResolvedConstant resolved = FieldRefConstant.fromPreResolved(delegationField);
                constants[index] = resolved;
                return delegationField;
            }
        }
    }

    public boolean isResolutionSuccessAt(int index) {
        Resolvable.ResolvedConstant constant = constants[index];
        return constant != null && constant.isSuccess();
    }

    public Method resolvedMethodAt(ObjectKlass accessingKlass, int index) {
        Resolvable.ResolvedConstant resolved = resolvedAt(accessingKlass, index, "method");
        return (Method) resolved.value();
    }

    public MethodRefConstant resolvedMethodRefAt(ObjectKlass accessingKlass, int index) {
        Resolvable.ResolvedConstant resolved = resolvedAt(accessingKlass, index, "method");
        return (MethodRefConstant) resolved;
    }

    public Method resolvedMethodAtNoCache(ObjectKlass accessingKlass, int index) {
        CompilerAsserts.neverPartOfCompilation();
        Resolvable.ResolvedConstant resolved = resolvedAtNoCache(accessingKlass, index, "method");
        return (Method) resolved.value();
    }

    public StaticObject resolvedMethodHandleAt(ObjectKlass accessingKlass, int index) {
        Resolvable.ResolvedConstant resolved = resolvedAt(accessingKlass, index, "method handle");
        return (StaticObject) resolved.value();
    }

    public StaticObject resolvedMethodTypeAt(ObjectKlass accessingKlass, int index) {
        Resolvable.ResolvedConstant resolved = resolvedAt(accessingKlass, index, "method type");
        return (StaticObject) resolved.value();
    }

    public InvokeDynamicConstant.CallSiteLink linkInvokeDynamic(ObjectKlass accessingKlass, int index, int bci, Method method) {
        InvokeDynamicConstant indy = (InvokeDynamicConstant) resolvedAt(accessingKlass, index, "indy");
        try {
            return indy.link(this, accessingKlass, index, method, bci);
        } catch (InvokeDynamicConstant.CallSiteLinkingFailure failure) {
            // On failure, shortcut subsequent linking operations.
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                constants[index] = failure.failConstant();
            }
            throw failure.cause;
        }
    }

    public InvokeDynamicConstant.Resolved peekResolvedInvokeDynamic(int index) {
        return (InvokeDynamicConstant.Resolved) constants[index];
    }

    public DynamicConstant.Resolved resolvedDynamicConstantAt(ObjectKlass accessingKlass, int index) {
        DynamicConstant.Resolved dynamicConstant = (DynamicConstant.Resolved) outOfLockResolvedAt(accessingKlass, index, "dynamic constant");
        dynamicConstant.checkFail();
        return dynamicConstant;
    }

    public StaticObject getClassLoader() {
        return classLoader;
    }

    public EspressoContext getContext() {
        return context;
    }

    public void setKlassAt(int index, ObjectKlass klass) {
        constants[index] = ClassConstant.resolved(klass);
    }

    @Override
    public int getMajorVersion() {
        return pool.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return pool.getMinorVersion();
    }
}
