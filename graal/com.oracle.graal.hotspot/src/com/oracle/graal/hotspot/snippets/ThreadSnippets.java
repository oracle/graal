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
package com.oracle.graal.hotspot.snippets;

import static com.oracle.graal.hotspot.snippets.HotSpotSnippetUtils.*;

import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.ClassSubstitution.MethodSubstitution;

/**
 * Snippets for {@link java.lang.Thread} methods.
 */
@ClassSubstitution(java.lang.Thread.class)
public class ThreadSnippets implements SnippetsInterface {
    public static Thread currentThread() {
        return CurrentThread.get();
    }

    @MethodSubstitution(isStatic = false)
    @SuppressWarnings("unused")
    private static boolean isInterrupted(final Thread thisObject, boolean clearInterrupted) {
        Word rawThread = HotSpotCurrentRawThreadNode.get();
        Thread thread = (Thread) loadObjectFromWord(rawThread, threadObjectOffset());
        if (thisObject == thread) {
            Word osThread = loadWordFromWord(rawThread, osThreadOffset());
            boolean interrupted = loadIntFromWord(osThread, osThreadInterruptedOffset()) != 0;
            if (!interrupted || !clearInterrupted) {
                return interrupted;
            }
        }

        return ThreadIsInterruptedStubCall.call(thisObject, clearInterrupted) != 0;
    }
}
