/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect;

import java.lang.reflect.Executable;

import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.word.BarrieredAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.reflect.ReflectionAccessorHolder.MethodInvokeFunctionPointer;
import com.oracle.svm.core.reflect.ReflectionAccessorHolder.MethodInvokeFunctionPointerForCallerSensitiveAdapter;
import com.oracle.svm.core.util.VMError;

import jdk.internal.reflect.MethodAccessor;

interface MethodAccessorJDK19 {
    Object invoke(Object obj, Object[] args, Class<?> caller);
}

@InternalVMMethod
public final class SubstrateMethodAccessor extends SubstrateAccessor implements MethodAccessor, MethodAccessorJDK19 {

    public static final int STATICALLY_BOUND = -1;
    public static final int OFFSET_NOT_YET_COMPUTED = 0xdead0001;

    /**
     * The expected receiver type, which is checked before invoking the {@link #expandSignature}
     * method, or null for static methods.
     */
    private final Class<?> receiverType;
    /** The actual value is computed after static analysis using a field value transformer. */
    private int vtableOffset;
    private final boolean callerSensitiveAdapter;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateMethodAccessor(Executable member, Class<?> receiverType, CFunctionPointer expandSignature, CFunctionPointer directTarget, int vtableOffset, DynamicHub initializeBeforeInvoke,
                    boolean callerSensitiveAdapter) {
        super(member, expandSignature, directTarget, initializeBeforeInvoke);
        this.receiverType = receiverType;
        this.vtableOffset = vtableOffset;
        this.callerSensitiveAdapter = callerSensitiveAdapter;
    }

    public int getVTableOffset() {
        return vtableOffset;
    }

    private void preInvoke(Object obj) {
        if (initializeBeforeInvoke != null) {
            EnsureClassInitializedNode.ensureClassInitialized(DynamicHub.toClass(initializeBeforeInvoke));
        }
        if (receiverType != null) {
            if (obj == null) {
                /*
                 * The specification explicitly demands a NullPointerException and not a
                 * IllegalArgumentException when the receiver of a non-static method is null
                 */
                throw new NullPointerException();
            } else if (!receiverType.isInstance(obj)) {
                throw new IllegalArgumentException("Receiver type " + obj.getClass().getTypeName() + " is not an instance of the declaring class " + receiverType.getTypeName());
            }
        }
    }

    private CFunctionPointer invokeTarget(Object obj) {
        /*
         * In case we have both a vtableOffset and a directTarget, the vtable lookup wins. For such
         * methods, the directTarget is only used when doing an invokeSpecial.
         */
        CFunctionPointer target;
        if (vtableOffset == OFFSET_NOT_YET_COMPUTED) {
            throw VMError.shouldNotReachHere("Missed vtableOffset recomputation at image build time");
        } else if (vtableOffset != STATICALLY_BOUND) {
            target = BarrieredAccess.readWord(obj.getClass(), vtableOffset, NamedLocationIdentity.FINAL_LOCATION);
        } else {
            target = directTarget;
        }
        return target;
    }

    @Override
    public Object invoke(Object obj, Object[] args) {
        if (callerSensitiveAdapter) {
            throw VMError.shouldNotReachHere("Cannot invoke method that has a @CallerSensitiveAdapter without an explicit caller");
        }
        preInvoke(obj);
        return ((MethodInvokeFunctionPointer) expandSignature).invoke(obj, args, invokeTarget(obj));
    }

    @Override
    public Object invoke(Object obj, Object[] args, Class<?> caller) {
        if (callerSensitiveAdapter) {
            preInvoke(obj);
            return ((MethodInvokeFunctionPointerForCallerSensitiveAdapter) expandSignature).invoke(obj, args, invokeTarget(obj), caller);
        } else {
            /* Not a @CallerSensitiveAdapter method, so we can ignore the caller argument. */
            return invoke(obj, args);
        }
    }

    public Object invokeSpecial(Object obj, Object[] args) {
        if (callerSensitiveAdapter) {
            throw VMError.shouldNotReachHere("Cannot invoke method that has a @CallerSensitiveAdapter without an explicit caller");
        }
        preInvoke(obj);

        CFunctionPointer target = directTarget;
        if (target.isNull()) {
            throw new IllegalArgumentException("Cannot do invokespecial for an abstract method");
        }
        return ((MethodInvokeFunctionPointer) expandSignature).invoke(obj, args, target);
    }
}
