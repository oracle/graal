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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.LinkedNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;

@EspressoIntrinsics
public class Target_sun_reflect_Reflection {
    @Intrinsic
    public static @Type(Class.class) StaticObject getCallerClass() {
        final int[] depth = new int[]{0};
        CallTarget caller = Truffle.getRuntime().iterateFrames(
                        frameInstance -> {
                            if (frameInstance.getCallTarget() instanceof RootCallTarget) {
                                RootCallTarget callTarget = (RootCallTarget) frameInstance.getCallTarget();
                                RootNode rootNode = callTarget.getRootNode();
                                if (rootNode instanceof LinkedNode) {
                                    if (depth[0]++ > 1)  {
                                        return frameInstance.getCallTarget();
                                    }
                                }
                            }
                            return null;
                        });

        RootCallTarget callTarget = (RootCallTarget) caller;
        RootNode rootNode = callTarget.getRootNode();
        if (rootNode instanceof LinkedNode) {
            return ((LinkedNode) rootNode).getOriginalMethod().getDeclaringClass().rawKlass().mirror();
        }

        throw EspressoError.shouldNotReachHere();
    }

    @Intrinsic
    public static int getClassAccessFlags(@Type(Class.class) StaticObjectClass clazz) {
        // TODO(peterssen): Investigate access vs. modifiers.
        return clazz.getMirror().getModifiers();
    }
}
