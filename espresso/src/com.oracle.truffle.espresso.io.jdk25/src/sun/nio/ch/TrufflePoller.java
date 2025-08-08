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

public class TrufflePoller extends Poller {
    private final int id;
    private final int event;

    TrufflePoller(boolean read) {
        super();
        this.event = (read) ? SelectionKey.OP_READ : SelectionKey.OP_WRITE;
        id = init();
    }

    @Override
    void implRegister(int fdVal) throws IOException {
        // this registeration must be one shot now
        register(id, fdVal, event);
    }

    @Override
    void implDeregister(int fdVal, boolean polled) {
        if (!polled) {
            deregister(id, fdVal);
        }
    }

    @Override
    int poll(int timeout) throws IOException {
        doSelect(id, timeout);
        int[] fds = getReadyFds(id);
        for (int i = 0; i < fds.length; i++) {
            polled(fds[i]);
        }
        return fds.length;
    }

    static native int init();

    static native void deregister(int id, int fd);

    static native void register(int id, int fd, int newEvents);

    static native int doSelect(int id, long timeout);

    static native int[] getReadyFds(int id);

}
