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
package sun.nio.ch;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class TruffleSelector extends SelectorImpl {
    private final int id;
    private final Object updateLock = new Object();
    private final ConcurrentHashMap<Integer, SelectionKeyImpl> fdToKey = new ConcurrentHashMap<>();

    protected TruffleSelector(SelectorProvider sp) {
        super(sp);
        id = init();
    }

    private void ensureOpen() {
        if (!isOpen()) {
            throw new ClosedSelectorException();
        }
    }

    @Override
    protected int doSelect(Consumer<SelectionKey> action, long timeout) throws IOException {
        assert Thread.holdsLock(this);
        boolean blocking = timeout != 0;

        processDeregisterQueue();

        begin(blocking);
        try {
            doSelect(id, timeout);
        } finally {
            end(blocking);
        }

        processDeregisterQueue();
        return processEvents(action);
    }

    private int processEvents(Consumer<SelectionKey> action) {
        assert Thread.holdsLock(this);
        int numKeysUpdated = 0;
        long[] fdAndOps = processEvents(id);
        for (int i = 0; i < fdAndOps.length; i++) {
            long fdAndOp = fdAndOps[i];
            int fd = (int) (fdAndOp & 0xFFFFFFFFL);
            int rOps = (int) (fdAndOp >>> 32);
            SelectionKeyImpl ski = fdToKey.get(fd);
            if (ski != null) {
                // the processReadyEvents expects the rOps to be in native form;
                // There is no unified way of doing the Translation
                // eg. OP_CONNECT gets translated to either PollIn and PollConn depending on the
                // channel
                // This should work since the underlying channel of ski is a SelChImpl
                int rOpsNative = ((SelChImpl) ski.channel()).translateInterestOps(rOps);
                numKeysUpdated += processReadyEvents(rOpsNative, ski, action);
            }
        }
        return numKeysUpdated;
    }

    private int translateOps(int rOps) {
        short nativeOps = 0;

        if ((rOps & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0) {
            nativeOps |= Net.pollinValue(); // You need to pass the correct InformationLeak instance
        }
        if ((rOps & SelectionKey.OP_WRITE) != 0) {
            nativeOps |= Net.polloutValue(); // You need to pass the correct InformationLeak
                                             // instance
        }
        if ((rOps & SelectionKey.OP_CONNECT) != 0) {
            nativeOps |= Net.pollconnValue(); // You need to pass the correct InformationLeak
                                              // instance
        }

        // You might want to handle other conditions like error or hangup
        // nativeOps |= pollerrValue(null); // You need to pass the correct InformationLeak instance
        // nativeOps |= pollhupValue(null); // You need to pass the correct InformationLeak instance

        return nativeOps;
    }

    @Override
    public Selector wakeup() {
        wakeup(id);
        return this;
    }

    @Override
    protected void implClose() throws IOException {
        assert Thread.holdsLock(this);
        close(id);
    }

    @Override
    protected void implDereg(SelectionKeyImpl ski) throws IOException {
        // nop
        // Normally, the channel remains registered with the Selector until the next selection,
        // at which point ImplDereg is called. However, this can cause issues in our implementation:
        // if configureBlocking(true) is called on a "canceled" channel before the next selection,
        // the channel is still internally linked to the selector. This will cause
        // configureBlocking(true) to throw an IllegalBlockingModeException, since a channel with
        // selection keys cannot be configured to blocking mode. To avoid this, we deregister the
        // channel internally as soon as its key is canceled.

    }

    @Override
    public void cancel(SelectionKeyImpl ski) {
        super.cancel(ski);
        // Immediately deregister the channel from the selector internally
        assert !ski.isValid();
        int fd = ski.getFDVal();
        // In EPollSelectorImpl all accesses to sun.nio.ch.EPoll.ctl are synchronized thus we should
        // do the same.
        synchronized (updateLock) {
            if (fdToKey.remove(fd) != null) {
                deregister(id, fd);
            } else {
                assert ski.registeredEvents() == 0;
            }
        }

    }

    @Override
    protected void setEventOps(SelectionKeyImpl ski) {
        int fd = ski.getFDVal();
        SelectionKeyImpl previous = fdToKey.putIfAbsent(fd, ski);
        assert (previous == null) || (previous == ski);
        int newEvents = ski.interestOps();
        int registeredEvents = ski.registeredEvents();
        // Synchronization to native selector operations as in EPollSelectorImpl
        synchronized (updateLock) {
            if (newEvents != registeredEvents && ski.isValid()) {
                // if (newEvents == 0) {
                // deregister(id, fd);
                // }
                // We previously encountered unhandled CancelledKeyExceptions from io.register. The
                // issue arose because our code canceled the HostSelectionKey internally, while the
                // GuestSelectionKey remained valid. This allowed the Channel, Selector pair to be
                // registered again, resulting in CancelledKeyExceptions. To fix this, we now update
                // the interestOps of the HostSelectionKey when newEvents == 0, preventing it from
                // being canceled. If the Channel, Selector pair is registered again,
                // TruffleIO.register returns the same valid HostSelectionKey since it wasn't
                // deregistered. The HostSelectionKey is now only invalidated when the
                // GuestSelectionKey is canceled, which also invalidates the GuestSelectionKey
                // itself.
                if (registeredEvents == 0) {
                    // add to epoll
                    register(id, fd, newEvents);
                } else {
                    // modify events
                    changeEvents(id, fd, newEvents);
                }
                ski.registeredEvents(newEvents);
            }
        }
    }

    static native int init();

    static native int doSelect(int id, long timeout);

    static native void deregister(int id, int fd);

    static native void register(int id, int fd, int newEvents);

    static native void wakeup(int id);

    static native void close(int id);

    static native void changeEvents(int id, int fd, int newEvents);

    static native long[] processEvents(int id);

}
