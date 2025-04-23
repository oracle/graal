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
package com.oracle.truffle.espresso.runtime.dispatch.messages;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.InteropKlassesDispatch;

/**
 * Provides {@link CallTarget} for interop messages implementations.
 * <p>
 * Factories need to be registered through
 * {@link #register(Class, InteropMessage.Message, InteropMessageFactory, boolean)} before being
 * able to be {@link #createInteropMessageTarget(EspressoLanguage, int, InteropMessage.Message)}
 * fetched.
 */

public final class InteropMessageFactories {
    private InteropMessageFactories() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) //
    @CompilationFinal(dimensions = 1) //
    private static final InteropMessageFactory[] messages = new InteropMessageFactory[InteropKlassesDispatch.DISPATCH_TOTAL * InteropMessage.Message.MESSAGE_COUNT];
    @CompilationFinal(dimensions = 1) //
    private static final boolean[] isShareable = new boolean[InteropKlassesDispatch.DISPATCH_TOTAL * InteropMessage.Message.MESSAGE_COUNT];

    public static void register(Class<?> cls, InteropMessage.Message message, InteropMessageFactory factory, boolean shareable) {
        assert cls != null;
        assert message != null;
        assert factory != null;
        int index = getIndex(cls, message);
        if (messages[index] == null) {
            messages[index] = factory;
        }
        if (shareable) {
            isShareable[index] = true;
        }
    }

    @TruffleBoundary
    public static CallTarget createInteropMessageTarget(EspressoLanguage lang, int id, InteropMessage.Message message) {
        int index = getIndex(id, message);
        InteropMessageFactory factory = messages[index];
        if (factory == null) {
            return null;
        }
        InteropMessage interopMessage = factory.create(message);
        return new InteropMessageRootNode(lang, interopMessage).getCallTarget();
    }

    public static boolean isShareable(int dispatchId, InteropMessage.Message message) {
        int index = getIndex(dispatchId, message);
        return isShareable[index];
    }

    public static int sourceDispatch(int dispatchId, InteropMessage.Message message) {
        assert isShareable(dispatchId, message);
        int index = getIndex(dispatchId, message);
        return messages[index].sourceDispatch();
    }

    public static int getIndex(int dispatchId, InteropMessage.Message message) {
        int messageId = message.ordinal();
        return InteropMessage.Message.MESSAGE_COUNT * dispatchId + messageId;
    }

    public static int dispatchToId(Class<?> cls) {
        return InteropKlassesDispatch.dispatchToId(cls);
    }

    private static int getIndex(Class<?> cls, InteropMessage.Message message) {
        return getIndex(dispatchToId(cls), message);
    }

    static {
        for (InteropNodes nodes : InteropNodesCollector.getInstances(InteropNodes.class)) {
            nodes.register();
        }
    }
}
