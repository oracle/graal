/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;

import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.word.*;

/**
 * Substitutions for {@link java.lang.Thread} methods.
 */
@ClassSubstitution(java.lang.Thread.class)
public class ThreadSubstitutions {

    @MethodSubstitution
    public static Thread currentThread() {
        return PiNode.piCastNonNull(CurrentJavaThreadNode.get(getWordKind()).readObject(threadObjectOffset(), LocationIdentity.FINAL_LOCATION), Thread.class);
    }

    @MethodSubstitution(isStatic = false)
    public static boolean isInterrupted(final Thread thisObject, boolean clearInterrupted) {
        Word javaThread = CurrentJavaThreadNode.get(getWordKind());
        Object thread = javaThread.readObject(threadObjectOffset(), LocationIdentity.FINAL_LOCATION);
        if (thisObject == thread) {
            Word osThread = javaThread.readWord(osThreadOffset(), LocationIdentity.FINAL_LOCATION);
            boolean interrupted = osThread.readInt(osThreadInterruptedOffset(), ANY_LOCATION) != 0;
            if (!interrupted || !clearInterrupted) {
                return interrupted;
            }
        }

        return threadIsInterruptedStub(THREAD_IS_INTERRUPTED, thisObject, clearInterrupted);
    }

    public static final ForeignCallDescriptor THREAD_IS_INTERRUPTED = new ForeignCallDescriptor("thread_is_interrupted", boolean.class, Object.class, boolean.class);

    /**
     * @param descriptor
     */
    @NodeIntrinsic(ForeignCallNode.class)
    private static boolean threadIsInterruptedStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Thread thread, boolean clearIsInterrupted) {
        try {
            Method isInterrupted = Thread.class.getDeclaredMethod("isInterrupted", boolean.class);
            isInterrupted.setAccessible(true);
            return (Boolean) isInterrupted.invoke(thread, clearIsInterrupted);
        } catch (Exception e) {
            throw new GraalInternalError(e);
        }
    }
}
