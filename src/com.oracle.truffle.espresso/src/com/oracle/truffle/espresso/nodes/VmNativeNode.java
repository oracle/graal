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

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.meta.Meta;

public class VmNativeNode extends NativeRootNode {

    private final boolean isJni;

    public VmNativeNode(TruffleLanguage<?> language, TruffleObject boundNative, boolean isJni, Meta.Method originalMethod) {
        super(language, boundNative, originalMethod);
        this.isJni = isJni;
    }

    public Object[] preprocessArgs(Object[] args) {
        JniEnv jniEnv = EspressoLanguage.getCurrentContext().getJNI();
        assert jniEnv.getNativePointer() != 0;

        args = super.preprocessArgs(args);

        Object[] argsWithEnv = getOriginalMethod().isStatic()
                        ? prepend1(getOriginalMethod().getDeclaringClass().rawKlass().mirror(), args)
                        : args;

        if (isJni) {
            argsWithEnv = prepend1(jniEnv.getNativePointer(), argsWithEnv);
        }

        return argsWithEnv;
    }
}
