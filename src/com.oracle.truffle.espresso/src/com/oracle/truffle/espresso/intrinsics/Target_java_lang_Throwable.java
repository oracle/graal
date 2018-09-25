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

import static com.oracle.truffle.espresso.meta.Meta.meta;

import java.util.ArrayList;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectWrapper;

@EspressoIntrinsics
public class Target_java_lang_Throwable {
    @Intrinsic(hasReceiver = true)
    public static @Type(Throwable.class) StaticObject fillInStackTrace(@Type(Throwable.class) StaticObject self, int dummy) {
        final ArrayList<FrameInstance> frames = new ArrayList<>(16);
        Truffle.getRuntime().iterateFrames(frameInstance -> {
            frames.add(frameInstance);
            return null;
        });
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        meta.THROWABLE.field("backtrace").set(self, new StaticObjectWrapper<>(meta.OBJECT.rawKlass(), frames.toArray(new FrameInstance[0])));
        return self;
    }

    @Intrinsic(hasReceiver = true)
    public static int getStackTraceDepth(@Type(Throwable.class) StaticObject self) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        Object backtrace = meta.THROWABLE.field("backtrace").get(self);
        if (backtrace == StaticObject.NULL) {
            return 0;
        }
        return ((StaticObjectWrapper<FrameInstance[]>) backtrace).getWrapped().length;
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(StackTraceElement.class) StaticObject getStackTraceElement(StaticObject self, int index) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        StaticObject ste = meta.knownKlass(StackTraceElement.class).allocateInstance();
        Object backtrace = meta.THROWABLE.field("backtrace").get(self);
        FrameInstance[] frames = ((StaticObjectWrapper<FrameInstance[]>) backtrace).getWrapped();

        FrameInstance frame = frames[index];

        RootNode rootNode = ((RootCallTarget) frame.getCallTarget()).getRootNode();
        Meta.Method.WithInstance init = meta(ste).method("<init>", void.class, String.class, String.class, String.class, int.class);
        if (rootNode instanceof EspressoRootNode) {
            EspressoRootNode espressoRootNode = (EspressoRootNode) rootNode;
            String className = meta(espressoRootNode.getMethod().getDeclaringClass()).getName();
            init.invoke(className, espressoRootNode.getMethod().getName(), null, -1);
        } else {
            // TODO(peterssen): Get access to the original (intrinsified) method and report
            // properly.
            init.invoke("UnknownIntrinsic", "unknownIntrinsic", null, -1);
        }
        return ste;
    }

}
