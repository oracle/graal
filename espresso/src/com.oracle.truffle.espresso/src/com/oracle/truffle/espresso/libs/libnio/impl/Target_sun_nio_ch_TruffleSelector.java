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
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;

@EspressoSubstitutions(type = "Lsun/nio/ch/TruffleSelector;", group = LibNio.class)
public final class Target_sun_nio_ch_TruffleSelector {
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
        Selector selector = libsState.net.getSelector(id);
        try {
            SelectionKey key = io.register(fd, selector, newEvents);
            libsState.net.putSelectionKey(id, fd, key);
        } catch (EspressoException e) {
            // We should not throw exceptions here according to the Selector API. In
            // sun.nio.ch.EPollSelectorImpl.processUpdateQueue error numbers from the native
            // sun.nio.ch.EPoll.ctl are just ignored. So we do the same here.
            libsState.getLogger().warning(() -> "In io.register the following exception was ingored: " + e.toString());
        }
    }

    @Substitution
    public static void changeEvents(int id, int fd, int newEvents, @Inject LibsState libsState) {
        SelectionKey selectionKey = libsState.net.getSelectionKey(id, fd);
        if (selectionKey == null) {
            throw JavaSubstitution.shouldNotReachHere();
        }
        libsState.net.checkValidOps(selectionKey.channel(), newEvents);
        selectionKey.interestOps(newEvents);
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
    public static void wakeup(int id, @Inject LibsState libsState) {
        Selector selector = libsState.net.getSelector(id);
        selector.wakeup();
    }

    @Substitution
    @TruffleBoundary
    public static void close(int id, @Inject LibsState libsState, @Inject EspressoContext ctx) {
        Selector selector = libsState.net.getSelector(id);
        libsState.net.freeSelector(id);
        try {
            selector.close();
        } catch (IOException e) {
            throw Throw.throwIOException(e, ctx);
        }
    }

    @Substitution
    @TruffleBoundary
    public static @JavaType(long[].class) StaticObject processEvents(int id, @Inject LibsState libsState, @Inject EspressoContext ctx, @Inject Meta meta) {
        Selector selector = libsState.net.getSelector(id);
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        long[] fdAndOps = new long[selectedKeys.size()];
        int i = 0;
        for (SelectionKey key : selectedKeys) {
            // Check if the key is valid
            if (key.isValid()) {
                // Get the ready ops for the key
                int readyOps = key.readyOps();
                int fd = libsState.net.getFdOfSelectionKey(key);
                if (fd == 0) {
                    throw JavaSubstitution.shouldNotReachHere();
                }
                fdAndOps[i] = ((long) readyOps << 32) | (fd & 0xFFFFFFFFL);
                i++;
            }
        }
        selectedKeys.clear();
        return ctx.getAllocator().wrapArrayAs(meta._long_array, fdAndOps);
    }

}
