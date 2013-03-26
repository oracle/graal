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

import static com.oracle.graal.hotspot.replacements.HotSpotSnippetUtils.*;
import static com.oracle.graal.nodes.extended.UnsafeCastNode.*;

import com.oracle.graal.api.replacements.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.word.*;

/**
 * Substitutions for {@link java.lang.Thread} methods.
 */
@ClassSubstitution(java.lang.Thread.class)
public class ThreadSubstitutions {

    @MethodSubstitution
    public static Thread currentThread() {
        return CurrentThread.get();
    }

    @Alias(declaringClass = Thread.class) private long eetop;

    @MethodSubstitution(isStatic = false)
    public static boolean isInterrupted(final Thread thisObject, boolean clearInterrupted) {
        Thread thread = CurrentThread.get();
        if (thisObject == thread) {
            ThreadSubstitutions threadAlias = unsafeCast(thread, ThreadSubstitutions.class, false, true);
            Word rawThread = Word.unsigned(threadAlias.eetop);
            Word osThread = rawThread.readWord(osThreadOffset(), FINAL_LOCATION);
            int int0 = osThread.readInt(osThreadInterruptedOffset(), UNKNOWN_LOCATION);
            boolean interrupted = int0 != 0;
            if (!interrupted || !clearInterrupted) {
                return interrupted;
            }
        }

        return ThreadIsInterruptedStubCall.call(thisObject, clearInterrupted) != 0;
    }
}
