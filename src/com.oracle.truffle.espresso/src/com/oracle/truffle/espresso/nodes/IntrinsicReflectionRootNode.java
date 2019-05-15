/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes;

import java.lang.reflect.InvocationTargetException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoExitException;

public class IntrinsicReflectionRootNode extends EspressoBaseNode {

    private final java.lang.reflect.Method reflectMethod;

    public IntrinsicReflectionRootNode(java.lang.reflect.Method reflectMethod, Method method) {
        super(method);
        this.reflectMethod = reflectMethod;
    }

    @Override
    public Object invokeNaked(VirtualFrame frame) {
        try {
            return callIntrinsic(frame.getArguments());
        } catch (InvocationTargetException e) {
            CompilerDirectives.transferToInterpreter();
            Throwable inner = e.getTargetException();
            // Exceptions that should propagate as is
            if (inner instanceof EspressoException) {
                throw (EspressoException) inner;
            }
            // Exceptions that should not be caught.
            if (inner instanceof EspressoExitException) {
                throw (EspressoExitException) inner;
            }
            // Box exceptions
            if (inner instanceof Exception) {
                throw getMeta().throwExWithMessage(inner.getClass(), inner.getMessage());
            }
            // Errors that should propagate without boxing
            if (inner instanceof VirtualMachineError) {
                throw (VirtualMachineError) inner;
            }
            if (inner instanceof EspressoError) {
                EspressoError outer = (EspressoError) inner;
                inner = inner.getCause();
                while (inner instanceof EspressoError) {
                    outer = (EspressoError) inner;
                    inner = inner.getCause();
                }

                outer.printStackTrace();
                throw outer;
            }
            inner.printStackTrace();
            throw EspressoError.shouldNotReachHere(inner + "\n\t in reflected method: " + reflectMethod);
        } catch (Throwable e) {
            // Non-espresso exceptions cannot escape to the guest.
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @TruffleBoundary
    private Object callIntrinsic(Object... args) throws InvocationTargetException, IllegalAccessException {
        return reflectMethod.invoke(null, args);
    }
}
