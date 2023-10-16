/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.vm.npe;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.VM;

public final class ExtendedNPEMessage {
    public static String getNPEMessage(StaticObject throwable) {
        if (throwable == null || StaticObject.isNull(throwable) || throwable.isForeignObject()) {
            return null;
        }
        EspressoContext ctx = throwable.getKlass().getContext();
        assert throwable.getKlass() == ctx.getMeta().java_lang_NullPointerException : "Calling getExtendedNPEMessage with non NPE throwable.";

        // Java tries to ensure NPE message is constructed after the stack trace is filled in.
        VM.StackTrace frames = (VM.StackTrace) ctx.getMeta().HIDDEN_FRAMES.getHiddenObject(throwable);
        if (frames == null) {
            return null;
        }
        // Ensure the top frame is the actual java frame where the NPE happened.
        if (frames.isSkippedFramesHidden()) {
            return null;
        }
        VM.StackElement top = frames.top();
        if (top == null || top.getBci() < 0 /*- native, unknown or foreign frame */) {
            return null;
        }
        // If this NPE was created via reflection, we have no real NPE.
        if (top.getMethod().getDeclaringKlass().getType() == Symbol.Type.jdk_internal_reflect_NativeConstructorAccessorImpl) {
            return null;
        }
        try {
            return Analysis.analyze(top.getMethod(), top.getBci()).buildMessage();
        } catch (Throwable e) {
            // Unexpected host exception
            ctx.getLogger().warning(() -> "Unexpected throw during extended NPE message construction: bailing out...");
            ctx.getLogger().warning(e::toString);
            return null;
        }
    }
}
