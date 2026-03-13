/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.snippets;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.locale.LocaleSupport;
import com.oracle.svm.core.container.Container;
import com.oracle.svm.core.thread.IsolateThreadCache;
import com.oracle.svm.shared.Uninterruptible;

import jdk.graal.compiler.nodes.PauseNode;

/** Used to initialize early process-wide state that must be visible across all isolates. */
final class EarlyProcessWideState {
    private static final CGlobalData<Pointer> STATE = CGlobalDataFactory.createWord();

    private static final UnsignedWord UNINITIALIZED = Word.unsigned(0);
    private static final UnsignedWord INITIALIZING = Word.unsigned(1);
    private static final UnsignedWord SUCCESS = Word.unsigned(2);

    private EarlyProcessWideState() {
        // no instances
    }

    @Uninterruptible(reason = "No isolate yet.")
    static void initialize() {
        Pointer statePtr = STATE.get();
        UnsignedWord state = statePtr.compareAndSwapWord(0, UNINITIALIZED, INITIALIZING, LocationIdentity.ANY_LOCATION);
        if (state == UNINITIALIZED) {
            initialize0();
            statePtr.writeWordVolatile(0, SUCCESS);
        } else {
            /* Wait for first isolate. */
            while (state == INITIALIZING) {
                PauseNode.pause();
                state = statePtr.readWordVolatile(0, LocationIdentity.ANY_LOCATION);
            }
            initialize0();
        }
    }

    /*
     * This method is executed once per isolate. It is guaranteed that the first isolate has already
     * finished execution before subsequent isolates can execute this method.
     */
    @Uninterruptible(reason = "No isolate yet.")
    private static void initialize0() {
        LocaleSupport.initialize();
        Container.initialize();
        IsolateThreadCache.initialize();
    }
}
