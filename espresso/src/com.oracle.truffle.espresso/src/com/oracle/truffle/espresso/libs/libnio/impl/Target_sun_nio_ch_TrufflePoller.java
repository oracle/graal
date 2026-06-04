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
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.io.FDAccess;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.LibsState;
import com.oracle.truffle.espresso.libs.libnio.LibNio;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.Throws;

/**
 * Class for substitutions of native methods defined in TrufflePoller, which is a
 * platform-independent implementation of a Poller.
 * <p>
 * <b>Context of the Poller:</b> When the Poller class is initialized, a new thread is created that
 * executes a poller loop: It constantly calls the poll methods of the Poller implementation, in our
 * case the TrufflePoller. Other threads register their FileDescriptors on a Poller and park.
 * Whenever a FileDescriptor is successfully polled by the poller loop, the thread waiting on it
 * gets unparked.
 * </p>
 * <p>
 * <b>Implementation Details:</b> As always, we are substituting low-level methods with high-level
 * Java APIs, in this case abstract methods of sun.nio.ch.Poller using the publicly available
 * Selector. This leads to problems as their methods don't have the same semantics. Below is a list
 * of the differences and associated challenges:
 * </p>
 * <p>
 * - The sun.nio.ch.Poller#poll(int) is expected to see new registrations from
 * sun.nio.ch.Poller#implRegister(int) while polling, whereas the Selector doesn't see new
 * registrations while in a select operation. To this end, when registering or changing the interest
 * set of a {@link SelectionKey}, we always need to wake up the underlying Selector. This implies
 * that some select operations might return too early without having selected anything. This should
 * not be a problem, as then no parked thread gets woken up and the poller loop just polls again.
 * </p>
 * <p>
 * - One-shot registrations: It is assumed that every registration with a poller is one-shot,
 * meaning after the event is polled once, it needs re-registration to be polled again. We achieve
 * this by setting the {@link SelectionKey#interestOps(int)} to 0 after it was selected. Note we do
 * not cancel the SelectionKey as it might get re-registered immediately which can lead to problems
 * as canceled SelectionKeys cannot be re-registered with the same selector until a new select
 * operation is performed. Moreover, creating and deleting a selectionKey for every registration
 * with the same fd is expensive. Thus, the first time a fd is registered we get a SelectionKey and
 * keep it until the channel is closed. Once it is closed in
 * {@link TruffleIO#close(StaticObject, FDAccess)} we will start cleaning up all resources
 * associated with the fd in {@link LibsState#pollerCleanSelectionKey(int)}.
 * </p>
 *
 */
@EspressoSubstitutions(type = "Lsun/nio/ch/TrufflePoller;", group = LibNio.class)
public final class Target_sun_nio_ch_TrufflePoller {

    @Substitution
    public static int init(@Inject LibsState libsState) {
        return Math.toIntExact(libsState.pollerHandlify());
    }

    @Substitution

    public static void deregister(int pollerId, int fd, @Inject LibsState libsState) {
        SelectionKey selKey = libsState.pollerGetSelectionKey(pollerId, fd);
        if (selKey != null) {
            try {
                selKey.interestOps(0);
            } catch (CancelledKeyException e) {
                /*
                 * The fd must have been closed, which canceled the SelectionKey and initiated
                 * cleanUp. We only reach here if this method is called when the cleanUp has not yet
                 * fully finished. This should be rare. Since the channel was closed and the key is
                 * just waiting to be cleaned, it is safe to ignore this exception.
                 */
            }
            libsState.pollerGetHostSelector(pollerId).wakeup();
        }
    }

    @Substitution
    @Throws(IOException.class)
    @TruffleBoundary
    public static void register(int pollerId, int fd, int newEvents,
                    @Inject LibsState libsState,
                    @Inject TruffleIO io) {
        Selector selector = libsState.pollerGetHostSelector(pollerId);
        boolean keyChanged = libsState.pollerRegisterEvents(selector, pollerId, fd, newEvents, io);
        if (keyChanged) {
            /*
             * We need to call wake up since Epoll.wait() sees new registrations while waiting where
             * as a selector does not. Therefore, we force the selector to wake up to see the
             * registration. Note that the next select call will return immediately but this should
             * be okay since its caller is a while(true) loop see: sun.nio.ch.Poller.pollLoop
             */
            selector.wakeup();
        }
    }

    @Substitution
    @TruffleBoundary
    @Throws(IOException.class)
    public static int doSelect(int pollerId, long timeout, @Inject LibsState libsState) {
        Selector selector = libsState.pollerGetHostSelector(pollerId);
        return libsState.doSelect(selector, timeout);
    }

    /**
     * @return a guest int array containing the selected and possibly invalid fds. The invalid fds
     *         will have a value of -1.
     */
    @Substitution
    @TruffleBoundary
    public static @JavaType(int[].class) StaticObject getReadyFds(int pollerId, @Inject LibsState libsState, @Inject EspressoContext ctx, @Inject Meta meta) {
        // We need to ensure that all events are one-shot, meaning they should only be polled once.
        // This is done via synchronization and clearing the interestOps.
        Selector selector = libsState.pollerGetHostSelector(pollerId);
        synchronized (selector) {
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            int[] fds = new int[selectedKeys.size()];
            int i = 0;
            for (SelectionKey key : selectedKeys) {
                // Check if the key is valid
                int fd = libsState.pollerSelectionKeyGetFd(key);
                try {
                    key.interestOps(0);
                } catch (CancelledKeyException e) {
                    /*
                     * The fd must have been closed, which canceled the SelectionKey and initiated
                     * cleanUp. We only reach here if this method is called when the cleanUp has not
                     * yet fully finished. This should be rare. Since the channel was closed and the
                     * key is just waiting to be cleaned, it is safe to ignore this exception.
                     */
                    fd = TruffleIO.INVALID_FD;
                }
                fds[i] = fd;
                i++;
            }
            selectedKeys.clear();
            return ctx.getAllocator().wrapArrayAs(meta._int_array, fds);
        }
    }
}
