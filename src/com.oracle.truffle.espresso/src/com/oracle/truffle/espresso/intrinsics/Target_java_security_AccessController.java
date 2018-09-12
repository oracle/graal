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

package com.oracle.truffle.espresso.intrinsics;

import java.security.AccessControlContext;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;

import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.Utils;

@EspressoIntrinsics
public class Target_java_security_AccessController {
    @Intrinsic
    public static Object doPrivileged(@Type(PrivilegedAction.class) StaticObject action) {
        MethodInfo runMethod = action.getKlass().findDeclaredConcreteMethod("run", Utils.getContext().getSignatureDescriptors().make("()Ljava/lang/Object;"));
        Object result = runMethod.getCallTarget().call(action);
        return result;
    }

    @Intrinsic(methodName = "doPrivileged")
    public static Object doPrivileged2(@Type(PrivilegedExceptionAction.class) StaticObject action) {
        return doPrivileged(action);
    }

    @Intrinsic
    public static @Type(AccessControlContext.class) StaticObject getStackAccessControlContext() {
        return StaticObject.NULL;
    }

    @Intrinsic(methodName = "doPrivileged")
    public static Object doPrivileged3(@Type(PrivilegedExceptionAction.class) StaticObject action, @Type(AccessControlContext.class) StaticObject context) {
        return doPrivileged(action);
    }

    @Intrinsic(methodName = "doPrivileged")
    public static Object doPrivileged4(@Type(PrivilegedAction.class) StaticObject action, @Type(AccessControlContext.class) StaticObject context) {
        return doPrivileged(action);
    }
}
