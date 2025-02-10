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
package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.classfile.bytecode.VolatileArrayAccess;
import com.oracle.truffle.espresso.impl.ContextAccessImpl;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.InteropKlassesDispatch;
import com.oracle.truffle.espresso.nodes.commands.AddPathToBindingsCache;
import com.oracle.truffle.espresso.nodes.commands.ReferenceProcessCache;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessage;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessageFactories;

public class LazyContextCaches extends ContextAccessImpl {
    // region Command processing

    public LazyContextCaches(EspressoContext context) {
        super(context);
        this.messages = new CallTarget[InteropKlassesDispatch.DISPATCH_TOTAL * InteropMessage.Message.MESSAGE_COUNT];
    }

    @CompilationFinal //
    private volatile ReferenceProcessCache referenceProcessCache = null;
    @CompilationFinal //
    private volatile AddPathToBindingsCache addPathToBindingsCache = null;

    public ReferenceProcessCache getReferenceProcessCache() {
        ReferenceProcessCache cache = referenceProcessCache;
        if (cache == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                cache = referenceProcessCache;
                if (cache == null) {
                    cache = (referenceProcessCache = new ReferenceProcessCache(getContext()));
                }
            }
        }
        return cache;
    }

    public AddPathToBindingsCache getAddPathToBindingsCache() {
        AddPathToBindingsCache cache = addPathToBindingsCache;
        if (cache == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                cache = addPathToBindingsCache;
                if (cache == null) {
                    cache = (addPathToBindingsCache = new AddPathToBindingsCache(getContext()));
                }
            }
        }
        return cache;
    }

    // endregion Command processing

    // region Shared Interop

    // Maps interop messages to their implementation. The index depends on both the interop message
    // and the dispatch class of the receiver.
    private final CallTarget[] messages;
    // Marker object for interop messages with no implementation that should resolve to the default.
    private static final CallTarget NO_IMPL = new RootNode(null) {
        @Override
        public Object execute(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere();
        }
    }.getCallTarget();

    public CallTarget getInteropMessage(InteropMessage.Message message, int dispatch) {
        int index = InteropMessageFactories.getIndex(dispatch, message);
        CallTarget target = messages[index];
        if (target == null) {
            CallTarget toRegister = InteropMessageFactories.createInteropMessageTarget(getContext().getLanguage(), dispatch, message);
            if (toRegister == null) {
                toRegister = NO_IMPL;
            }
            if (VolatileArrayAccess.compareAndSet(messages, index, null, toRegister)) {
                target = toRegister;
            } else {
                target = VolatileArrayAccess.volatileRead(messages, index);
                assert target != null;
            }
        }
        return interpretCacheTarget(target);
    }

    private static CallTarget interpretCacheTarget(CallTarget target) {
        return target == NO_IMPL ? null : target;
    }
    // endregion Shared Interop
}
