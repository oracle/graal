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
package com.oracle.truffle.espresso.constantpool;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ImmutableConstantPool;
import com.oracle.truffle.espresso.classfile.ParserException;
import com.oracle.truffle.espresso.classfile.attributes.BootstrapMethodsAttribute;
import com.oracle.truffle.espresso.classfile.constantpool.ClassConstant;
import com.oracle.truffle.espresso.classfile.constantpool.ClassMethodRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.DynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.FieldRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.ImmutablePoolConstant;
import com.oracle.truffle.espresso.classfile.constantpool.InterfaceMethodRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.InvokeDynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodHandleConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodTypeConstant;
import com.oracle.truffle.espresso.classfile.constantpool.PoolConstant;
import com.oracle.truffle.espresso.classfile.constantpool.Resolvable;
import com.oracle.truffle.espresso.classfile.constantpool.StringConstant;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;

public final class RuntimeConstantPool extends ConstantPool {
    private final ImmutableConstantPool immutableConstantPool;
    private final ObjectKlass holder;

    @CompilationFinal(dimensions = 1) //
    private final Resolvable.ResolvedConstant[] resolvedConstants;

    public RuntimeConstantPool(ImmutableConstantPool immutableConstantPool, ObjectKlass holder) {
        this.immutableConstantPool = immutableConstantPool;
        this.holder = holder;
        this.resolvedConstants = new Resolvable.ResolvedConstant[immutableConstantPool.length()];
    }

    @Override
    public int length() {
        return immutableConstantPool.length();
    }

    @Override
    public byte[] getRawBytes() {
        return immutableConstantPool.getRawBytes();
    }

    @Override
    public ImmutablePoolConstant at(int index, String description) {
        try {
            return immutableConstantPool.at(index, description);
        } catch (ParserException.ClassFormatError e) {
            throw classFormatError(e.getMessage());
        }
    }

    @TruffleBoundary
    public PoolConstant maybeResolvedAt(int index, Meta meta) {
        if (index < 0 || index >= resolvedConstants.length) {
            meta.throwIndexOutOfBoundsExceptionBoundary("Invalid constant pool index", index, resolvedConstants.length);
        }
        Resolvable.ResolvedConstant c = resolvedConstants[index];
        if (c != null) {
            return c;
        }
        return at(index);
    }

    private Resolvable.ResolvedConstant outOfLockResolvedAt(ObjectKlass accessingKlass, int index, String description) {
        Resolvable.ResolvedConstant c = resolvedConstants[index];
        if (c == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // double check: deopt is a heavy operation.
            c = resolvedConstants[index];
            if (c == null) {
                Resolvable resolvable = (Resolvable) at(index, description);
                Resolvable.ResolvedConstant locallyResolved = resolve(resolvable, index, accessingKlass);
                synchronized (this) {
                    // Triple check: non-trivial resolution
                    c = resolvedConstants[index];
                    if (c == null) {
                        resolvedConstants[index] = c = locallyResolved;
                    }
                }
            }
        }
        return c;
    }

    public Resolvable.ResolvedConstant resolvedAt(ObjectKlass accessingKlass, int index, String description) {
        Resolvable.ResolvedConstant c = resolvedConstants[index];
        if (c == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                // fence += 1;
                // FIXME(peterssen): Add memory fence for array read.
                c = resolvedConstants[index];
                if (c == null) {
                    Resolvable resolvable = (Resolvable) at(index, description);
                    resolvedConstants[index] = c = resolve(resolvable, index, accessingKlass);
                }
            }
        }
        return c;
    }

    private Resolvable.ResolvedConstant resolvedAtNoCache(ObjectKlass accessingKlass, int index, String description) {
        CompilerAsserts.neverPartOfCompilation();
        Resolvable resolvable = (Resolvable) immutableConstantPool.at(index, description);
        return resolve(resolvable, index, accessingKlass);
    }

    public StaticObject resolvedStringAt(int index) {
        Resolvable.ResolvedConstant resolved = resolvedAt(null, index, "string");
        return (StaticObject) resolved.value();
    }

    public Klass resolvedKlassAt(ObjectKlass accessingKlass, int index) {
        ResolvedClassConstant resolved = (ResolvedClassConstant) resolvedAt(accessingKlass, index, "klass");
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
                resolvedConstants[index] = null;
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
                resolvedConstants[index] = resolved;
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
                Field delegationField = getContext().getClassRedefinition().createDelegationFrom(realField);
                Resolvable.ResolvedConstant resolved = new ResolvedFieldRefConstant(delegationField);
                resolvedConstants[index] = resolved;
                return delegationField;
            }
        }
    }

    public boolean isResolutionSuccessAt(int index) {
        Resolvable.ResolvedConstant constant = resolvedConstants[index];
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

    public Method resolveMethodAndUpdate(ObjectKlass accessingKlass, int index) {
        CompilerAsserts.neverPartOfCompilation();
        Resolvable.ResolvedConstant resolved = resolvedAtNoCache(accessingKlass, index, "method");
        synchronized (this) {
            resolvedConstants[index] = resolved;
        }
        return ((Method) resolved.value());
    }

    public StaticObject resolvedMethodHandleAt(ObjectKlass accessingKlass, int index) {
        Resolvable.ResolvedConstant resolved = resolvedAt(accessingKlass, index, "method handle");
        return (StaticObject) resolved.value();
    }

    public StaticObject resolvedMethodTypeAt(ObjectKlass accessingKlass, int index) {
        Resolvable.ResolvedConstant resolved = resolvedAt(accessingKlass, index, "method type");
        return (StaticObject) resolved.value();
    }

    public SuccessfulCallSiteLink linkInvokeDynamic(ObjectKlass accessingKlass, int index, Method method, int bci) {
        ResolvedInvokeDynamicConstant indy = (ResolvedInvokeDynamicConstant) resolvedAt(accessingKlass, index, "indy");
        CallSiteLink link = indy.link(this, accessingKlass, index, method, bci);
        if (link instanceof FailedCallSiteLink failed) {
            throw failed.fail();
        }
        return (SuccessfulCallSiteLink) link;
    }

    public ResolvedInvokeDynamicConstant peekResolvedInvokeDynamic(int index) {
        return (ResolvedInvokeDynamicConstant) resolvedConstants[index];
    }

    public ResolvedDynamicConstant resolvedDynamicConstantAt(ObjectKlass accessingKlass, int index) {
        ResolvedDynamicConstant dynamicConstant = (ResolvedDynamicConstant) outOfLockResolvedAt(accessingKlass, index, "dynamic constant");
        return dynamicConstant;
    }

    public StaticObject getClassLoader() {
        return holder.getDefiningClassLoader();
    }

    public EspressoContext getContext() {
        return holder.getContext();
    }

    public ObjectKlass getHolder() {
        return holder;
    }

    public void setKlassAt(int index, ObjectKlass klass) {
        resolvedConstants[index] = new ResolvedFoundClassConstant(klass);
    }

    @Override
    public int getMajorVersion() {
        return immutableConstantPool.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return immutableConstantPool.getMinorVersion();
    }

    public StaticObject getMethodHandle(BootstrapMethodsAttribute.Entry entry, ObjectKlass accessingKlass) {
        return this.resolvedMethodHandleAt(accessingKlass, entry.getBootstrapMethodRef());
    }

    public StaticObject[] getStaticArguments(BootstrapMethodsAttribute.Entry entry, ObjectKlass accessingKlass) {
        Meta meta = accessingKlass.getMeta();
        StaticObject[] args = new StaticObject[entry.numBootstrapArguments()];
        // @formatter:off
        for (int i = 0; i < entry.numBootstrapArguments(); i++) {
            PoolConstant pc = this.at(entry.argAt(i));
            switch (pc.tag()) {
                case METHODHANDLE:
                    args[i] = this.resolvedMethodHandleAt(accessingKlass, entry.argAt(i));
                    break;
                case METHODTYPE:
                    args[i] = this.resolvedMethodTypeAt(accessingKlass, entry.argAt(i));
                    break;
                case DYNAMIC:
                    args[i] = this.resolvedDynamicConstantAt(accessingKlass, entry.argAt(i)).guestBoxedValue(meta);
                    break;
                case CLASS:
                    args[i] = this.resolvedKlassAt(accessingKlass, entry.argAt(i)).mirror();
                    break;
                case STRING:
                    args[i] = this.resolvedStringAt(entry.argAt(i));
                    break;
                case INTEGER:
                    args[i] = meta.boxInteger(this.intAt(entry.argAt(i)));
                    break;
                case LONG:
                    args[i] = meta.boxLong(this.longAt(entry.argAt(i)));
                    break;
                case DOUBLE:
                    args[i] = meta.boxDouble(this.doubleAt(entry.argAt(i)));
                    break;
                case FLOAT:
                    args[i] = meta.boxFloat(this.floatAt(entry.argAt(i)));
                    break;
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
            }
        }
        return args;
        // @formatter:on
    }

    Resolvable.ResolvedConstant resolve(Resolvable resolvable, int thisIndex, ObjectKlass accessingKlass) {
        switch (resolvable.tag()) {
            case STRING:
                if (resolvable instanceof StringConstant.Index fromIndex) {
                    return Resolution.resolveStringConstant(fromIndex, this, thisIndex, accessingKlass);
                }
                break;
            case FIELD_REF:
                if (resolvable instanceof FieldRefConstant.Indexes fromIndex) {
                    return Resolution.resolveFieldRefConstant(fromIndex, this, thisIndex, accessingKlass);
                }
                break;
            case INTERFACE_METHOD_REF:
                if (resolvable instanceof InterfaceMethodRefConstant.Indexes fromIndex) {
                    return Resolution.resolveInterfaceMethodRefConstant(fromIndex, this, thisIndex, accessingKlass);
                }
                break;
            case METHOD_REF:
                if (resolvable instanceof ClassMethodRefConstant.Indexes fromIndex) {
                    return Resolution.resolveClassMethodRefConstant(fromIndex, this, thisIndex, accessingKlass);
                }
                break;
            case METHODTYPE:
                if (resolvable instanceof MethodTypeConstant.Index fromIndex) {
                    return Resolution.resolveMethodTypeConstant(fromIndex, this, thisIndex, accessingKlass);
                }
                break;
            case INVOKEDYNAMIC:
                if (resolvable instanceof InvokeDynamicConstant.Indexes fromIndex) {
                    return Resolution.resolveInvokeDynamicConstant(fromIndex, this, thisIndex, accessingKlass);
                }
                break;
            case DYNAMIC:
                if (resolvable instanceof DynamicConstant.Indexes fromIndex) {
                    return Resolution.resolveDynamicConstant(fromIndex, this, thisIndex, accessingKlass);
                }
                break;
            case METHODHANDLE:
                if (resolvable instanceof MethodHandleConstant.Index fromIndex) {
                    return Resolution.resolveMethodHandleConstant(fromIndex, this, thisIndex, accessingKlass);
                }
                break;
            case CLASS:
                if (resolvable instanceof ClassConstant.Index fromIndex) {
                    return Resolution.resolveClassConstant(fromIndex, this, thisIndex, accessingKlass);
                }
                break;
        }
        throw EspressoError.shouldNotReachHere("Unexpected CP entry: " + resolvable);
    }

    @Override
    @TruffleBoundary
    public @JavaType(ClassFormatError.class) EspressoException classFormatError(String message) {
        CompilerAsserts.neverPartOfCompilation();
        Meta meta = EspressoContext.get(null).getMeta();
        if (meta.java_lang_ClassFormatError == null) {
            throw EspressoError.fatal("ClassFormatError during early startup: ", message);
        }
        throw meta.throwExceptionWithMessage(meta.java_lang_ClassFormatError, message);
    }

    public void patchAt(int index, Resolvable.ResolvedConstant resolvedConstant) {
        assert resolvedConstant != null;
        assert resolvedConstant.value() != null;
        resolvedConstants[index] = resolvedConstant;
    }
}
