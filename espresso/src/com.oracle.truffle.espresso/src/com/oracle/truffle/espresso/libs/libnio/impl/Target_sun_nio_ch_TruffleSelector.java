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

import static com.oracle.truffle.espresso.io.TruffleIO.INVALID_FD;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.Set;
import java.util.logging.Level;

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
import com.oracle.truffle.espresso.substitutions.Throws;

@EspressoSubstitutions(type = "Lsun/nio/ch/TruffleSelector;", group = LibNio.class)
public final class Target_sun_nio_ch_TruffleSelector {
    @Substitution
    public static int init(@Inject LibsState libsState) {
        return Math.toIntExact(libsState.handlifyTruffleSelector());
    }

    @Substitution
    public static void deregister(int id, int fd, @Inject LibsState libsState) {
        // this is synchronized from outside per guest SelectionKey
        libsState.selectorDeregister(id, fd);
    }

    @Substitution
    public static void register(int id, int fd, int newEvents,
                    @Inject LibsState libsState,
                    @Inject TruffleIO io) {
        // this is synchronized from outside per guest SelectionKey
        try {
            Selector selector = libsState.selectorGetHostSelector(id);
            libsState.selectorRegisterEvents(selector, id, fd, newEvents, io);
        } catch (EspressoException e) {
            /*
             * We should not throw exceptions here, according to the Selector API. In
             * sun.nio.ch.EPollSelectorImpl.processUpdateQueue error numbers from the native
             * sun.nio.ch.EPoll.ctl are just ignored. So we do the same here.
             */
            LibsState.getLogger().log(Level.WARNING, "In io.register the following exception was ingored: ", e);
        }
    }

    @Substitution
    public static void changeEvents(int id, int fd, int newEvents, @Inject LibsState libsState) {
        // this is synchronized from outside per guest SelectionKey
        SelectionKey selectionKey = libsState.selectorGetSelectionKey(id, fd);
        if (selectionKey == null) {
            throw JavaSubstitution.shouldNotReachHere();
        }
        libsState.checkValidOps(selectionKey.channel(), newEvents);
        try {
            selectionKey.interestOps(newEvents);
        } catch (CancelledKeyException e) {
            throw JavaSubstitution.shouldNotReachHere("CancelledKeyException! This should not happen as canceling and changeEvents is synchronized!");
        }
    }

    @Substitution
    @Throws(IOException.class)
    public static int doSelect(int id, long timeout, @Inject LibsState libsState) {
        // synchronized on the "this" of the guest Selector
        Selector selector = libsState.selectorGetHostSelector(id);
        return libsState.doSelect(selector, timeout);
    }

    @Substitution
    public static void wakeup(int id, @Inject LibsState libsState) {
        Selector selector = libsState.selectorGetHostSelector(id);
        selector.wakeup();
    }

    @Substitution
    @TruffleBoundary
    @Throws(IOException.class)
    public static void close(int id, @Inject LibsState libsState, @Inject EspressoContext ctx) {
        // synchronized on the "this" of the guest Selector
        Selector selector = libsState.selectorGetHostSelector(id);
        libsState.freeSelector(id);
        try {
            selector.close();
        } catch (IOException e) {
            throw Throw.throwIOException(e, ctx);
        }
    }

    @Substitution
    @TruffleBoundary
    public static @JavaType(long[].class) StaticObject processEvents(int id, @Inject LibsState libsState, @Inject EspressoContext ctx, @Inject Meta meta) {
        /*
         * Concurrency: This method is synchronized on the "this" of the guest Selector meaning it
         * can happened concurrently with register, deregister and ChangeEvents. For changing events
         * this is not a problem as caning a SelectionKey's interest set or registering a new set
         * will not affect ongoing select operation specified by
         * java.nio.channels.SelectionKey.interestOps(int) and
         * java.nio.channels.SelectableChannel.register(java.nio.channels.Selector, int,
         * java.lang.Object).
         *
         * Canceling a SelectionKey while a select operation is in progress is not well specified.
         * In sun.nio.ch.EPollSelectorImpl.doSelect a guest SelectionKey can be appended to the
         * cancelledKey set while a select operation is in progress. The second call to
         * sun.nio.ch.SelectorImpl.processDeregisterQueue can even deregister the newly cancelled
         * key's natively. This implies the consumer action will not be executed for this key and
         * the key will not be selected. However, if the key is added to the cancelledKey set after
         * the second call to sun.nio.ch.SelectorImpl.processDeregisterQueue, the cancelled key will
         * be selected and its consumer action executed. So the timing between cancelling and
         * processDeregisterQueue is essential in EPollSelectorImpl. In our implementation the
         * timing between canceling a native Key and "int readyOps = key.readyOps();" in this
         * methods body is essential: If the key is cancelled before readOps is called then the
         * latter will throw a CancelledKeyException and we will not return the fd and readyOps for
         * this SelectionKey. This will lead to the guest SelectionKey not being selected and its
         * consumer action not being executed. In the other case when the key is cancelled after
         * readyOps() ops is called the guest selection key will be selected and its consumer action
         * executed. Therefore, our approach will expose behaviour which is consistent with the
         * behaviour of EPollSelectorImpl.
         * 
         */
        Selector selector = libsState.selectorGetHostSelector(id);
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        long[] fdAndOps = new long[selectedKeys.size()];
        int i = 0;
        for (SelectionKey key : selectedKeys) {
            if (key.isValid()) {
                try {
                    int readyOps = key.readyOps();
                    int fd = libsState.selectorSelectionKeyGetFd(key);
                    if (fd == INVALID_FD) {
                        continue;
                    }
                    fdAndOps[i] = ((long) readyOps << 32) | (fd & 0xFFFFFFFFL);
                    i++;
                } catch (CancelledKeyException e) {
                    /*
                     * host selection key's can be canceled at any time even during a select call
                     * leading a CancelledKeyException in key.readyOps(). This is not a problem as
                     * then processEvents should just ignore this SelectionKey. So we just catch the
                     * exception and proceed.
                     *
                     * This implies we might not fill the full array as some keys might be invalid.
                     * This is not a problem as the validity of guest keys is checked in
                     * sun.nio.ch.TruffleSelector.processEvents(java.util.function.Consumer<java
                     * .nio.channels.SelectionKey>) by only handling guest keys in
                     * sun.nio.ch.TruffleSelector.fdToKey. Also note we only cancel host keys if the
                     * guest key was canceled and removed from fdToKey. (see
                     * sun.nio.ch.TruffleSelector.cancel)
                     */
                }
            }
        }
        selectedKeys.clear();
        // set all remaining entries to invalid fd!
        Arrays.fill(fdAndOps, i, fdAndOps.length, INVALID_FD);
        return ctx.getAllocator().wrapArrayAs(meta._long_array, fdAndOps);
    }
}
