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
package com.oracle.truffle.espresso.libs.libnio.impl;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.io.Throw;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.LibsState;
import com.oracle.truffle.espresso.libs.libnio.LibNio;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;

@EspressoSubstitutions(type = "Lsun/nio/ch/TrufflePoller;", group = LibNio.class)
public final class Target_sun_nio_ch_TrufflePoller {
    @Substitution
    public static int init(@Inject LibsState libsState) {
        return Math.toIntExact(libsState.net.handlifySelector());
    }

    @Substitution
    public static void deregister(int id, int fd, @Inject LibsState libsState) {
        SelectionKey selKey = libsState.net.getSelectionKey(id, fd);
        if (selKey != null) {
            selKey.cancel();
            libsState.net.removeSelectionKey(id, fd);
        }

    }

    @Substitution
    public static void register(int id, int fd, int newEvents,
                    @Inject LibsState libsState,
                    @Inject TruffleIO io) {
        SelectionKey key = libsState.net.setInterestOpsOrRegister(id, fd, newEvents, io);
        /*
         * We need to call wake up since Epoll.wait() sees new registrations while waiting where as
         * the selector does not. Therefore, we force the selector to wake up to see the
         * registration. Note that the next select call will return immediately but this should be
         * okay since it is caller is a while(true) loop see: sun.nio.ch.Poller.pollLoop
         */
        key.selector().wakeup();
    }

    @Substitution
    public static int doSelect(int id, long timeout, @Inject LibsState libsState, @Inject EspressoContext ctx) {
        Selector selector = libsState.net.getSelector(id);
        try {
            if (timeout == 0) {
                return selector.selectNow();
            } else if (timeout == -1) {
                return selector.select();
            } else if (timeout > 0) {
                return selector.select(timeout);
            }
            throw Throw.throwIOException("timeout should be >= -1", ctx);
        } catch (IOException e) {
            throw Throw.throwIOException(e, ctx);
        }
    }

    @Substitution
    @TruffleBoundary
    public static @JavaType(int[].class) StaticObject getReadyFds(int id, @Inject LibsState libsState, @Inject EspressoContext ctx, @Inject Meta meta) {
        // We need to ensure that all events are one-shot, meaning they should only be polled once.
        // This is done via synchronization and clearing the interestOps.
        Selector selector = libsState.net.getSelector(id);
        synchronized (selector) {
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            int[] fds = new int[selectedKeys.size()];
            int i = 0;
            for (SelectionKey key : selectedKeys) {
                // Check if the key is valid
                if (key.isValid()) {
                    int fd = libsState.net.getFdOfSelectionKey(key);
                    if (fd == 0) {
                        // indicates an invalid fd
                        throw JavaSubstitution.shouldNotReachHere();
                    }
                    fds[i] = fd;
                    i++;
                    key.interestOps(0);
                }
            }
            selectedKeys.clear();
            return ctx.getAllocator().wrapArrayAs(meta._int_array, fds);
        }
    }
}
