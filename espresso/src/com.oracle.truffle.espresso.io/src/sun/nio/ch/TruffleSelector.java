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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * <b> Differences to other implementations of SelectorImpl </b>
 * <p>
 * In this implementation, when a selection key is canceled or its event ops are updated, the native
 * counterpart is updated immediately, not in a deferred or retained manner as in some other
 * implementations (see {@link TruffleSelector#cancel(SelectionKeyImpl)}) Usually, canceled or
 * updated selection keys are collected in a list and processed during each doSelect call, but here,
 * changes are immediate.
 * <p>
 * <b>Concurrency</b>
 * <p>
 * In the retained mode of updates, the update and canceled key lists are protected by their
 * respective locks. This allows some concurrency between {@code doSelect} and {@code setEventOps},
 * and between {@code doSelect} and cancellation operations. However, all accesses to the native
 * counterpart for adding or canceling selection keys are synchronized on {@code this} within
 * {@code doSelect()}.
 * <p>
 * In this implementation, the modification of the native selection key was moved from
 * {@code doSelect} to {@code setEventOps} and {@code cancel}. As both methods lack external
 * synchronization, internal synchronization is required. For proper internal state management,
 * registering and deregistering the native selection key must occur transactionally for each key.
 * This is achieved by synchronizing on the selection key in both {@code setEventOps} and
 * {@code cancel}.
 * <p>
 * Generally for SelectorImpl concurrent calls to {@code doSelect} and {@code setEventOps} may
 * result in the updates being included in the current selection cycle or deferred to the next.
 * Similarly, concurrent calls to {@code doSelect} and {@code cancel} may lead to the cancelled key
 * being selected and its consumer action executed, or to it being removed natively before the
 * selection completes. In this implementation on the native side, the host {@link Selector}
 * provides enough thread-safety to implement this behaviour between {@code doSelect} and
 * {@code setEventOps} / {@code cancel} calls without requiring further synchronization here. For
 * more information see
 * com.oracle.truffle.espresso.libs.libnio.impl.Target_sun_nio_ch_TruffleSelector#processEvents(int,
 * com.oracle.truffle.espresso.libs.LibsState, com.oracle.truffle.espresso.runtime.EspressoContext,
 * com.oracle.truffle.espresso.meta.Meta)
 * <p>
 * <b>General remark:</b> Selection keys can always be canceled in the guest and host. Care must be
 * taken to avoid causing {@link java.nio.channels.CancelledKeyException}.
 */
public class TruffleSelector extends SelectorImpl {

    private final int id;
    // ConcurrentHashMap to allow concurrency between cancel and setEvenOps
    private final ConcurrentHashMap<Integer, SelectionKeyImpl> fdToKey = new ConcurrentHashMap<>();

    protected TruffleSelector(SelectorProvider sp) {
        super(sp);
        id = init();
    }

    @Override
    protected int doSelect(Consumer<SelectionKey> action, long timeout) throws IOException {
        assert Thread.holdsLock(this);
        boolean blocking = timeout != 0;

        processDeregisterQueue();
        // todo (GR-73603)
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
        /*
         * fdAndOps might contain invalid fds (=-1) see
         * com.oracle.truffle.espresso.io.TruffleIO.INVALID_FD
         */
        long[] fdAndOps = processEvents(id);
        for (int i = 0; i < fdAndOps.length; i++) {
            long fdAndOp = fdAndOps[i];
            int fd = (int) (fdAndOp & 0xFFFF_FFFFL);
            // ignore invalid fds
            if (fd == -1) {
                continue;
            }
            int rOps = (int) (fdAndOp >>> 32);
            SelectionKeyImpl ski = fdToKey.get(fd);
            if (ski != null) {
                /*
                 * The processReadyEvents function of the super class expects the rOps to be in
                 * native form. This works since the underlying channel of ski is a SelChImpl.
                 */
                int rOpsNative = ((SelChImpl) ski.channel()).translateInterestOps(rOps);
                numKeysUpdated += processReadyEvents(rOpsNative, ski, action);
            }
        }
        return numKeysUpdated;
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
    }

    // no outside synchronization
    @Override
    public void cancel(SelectionKeyImpl ski) {
        /*
         * Normally, the channel remains registered with the Selector until the next selection, at
         * which point ImplDereg is called. However, this can cause issues in our implementation: If
         * configureBlocking(true) is called on a "canceled" channel before the next selection, the
         * channel is still internally linked to the selector. This will cause
         * configureBlocking(true) to throw an IllegalBlockingModeException, since a channel with
         * selection keys cannot be configured to blocking mode. To avoid this, we deregister the
         * channel internally as soon as its key is canceled.
         */
        super.cancel(ski);
        /*
         * We don't need to synchronize here on the selector since it is valid to cancel some key's
         * during a select call and then just ignore its consumer action. One then needs to be
         * careful about not getting a CancelledKeyException in the host. (see
         * com.oracle.truffle.espresso.libs.libnio.impl.Target_sun_nio_ch_TruffleSelector.
         * processEvents
         */
        assert !ski.isValid();
        int fd = ski.getFDVal();
        /*
         * The native state of a SelectionKey and fdToKey should be updated atomically per
         * SelectionKey.
         */
        synchronized (ski) {
            if (fdToKey.remove(fd) != null) {
                deregister(id, fd);
                ski.registeredEvents(0);
            } else {
                assert ski.registeredEvents() == 0;
            }
        }
    }

    // no outside synchronization
    @Override
    protected void setEventOps(SelectionKeyImpl ski) {
        int fd = ski.getFDVal();

        int newEvents = ski.nioInterestOps();
        int registeredEvents = ski.registeredEvents();
        /*
         * The native state of a SelectionKey and fdToKey should be updated atomically per
         * SelectionKey.
         */
        synchronized (ski) {
            SelectionKeyImpl previous = fdToKey.putIfAbsent(fd, ski);
            assert (previous == null) || (previous == ski);
            if (newEvents != registeredEvents && ski.isValid()) {
                /*
                 * Noteworthy the SelectionKey is NOT canceled if newEvents == 0 which is consistent
                 * with the jdk code where a SelectionKey becomes only invalid if the selector is
                 * closed or the key is explicitly canceled. Meaning setEventOps with newEvents == 0
                 * should be understood as pausing the SelectionKey but NOT canceling it.
                 */
                if (registeredEvents == 0) {
                    register(id, fd, newEvents);
                } else {
                    changeEvents(id, fd, newEvents);
                }
                ski.registeredEvents(newEvents);
            }
        }
    }

    private static native int init();

    private static native int doSelect(int id, long timeout) throws IOException;

    private static native void deregister(int id, int fd);

    private static native void register(int id, int fd, int newEvents);

    private static native void wakeup(int id);

    private static native void close(int id) throws IOException;

    private static native void changeEvents(int id, int fd, int newEvents);

    private static native long[] processEvents(int id);

}
