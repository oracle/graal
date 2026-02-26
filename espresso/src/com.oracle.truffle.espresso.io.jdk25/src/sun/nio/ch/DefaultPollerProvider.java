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

public class DefaultPollerProvider extends PollerProvider {
    private static boolean hasWarned = false;
    private static final System.Logger LOGGER = System.getLogger(DefaultPollerProvider.class.getName());

    @Override
    Poller readPoller(boolean subPoller) throws IOException {
        if (subPoller) {
            /*
             * In other OS implementations, the allocation size of the underlying epoll struct
             * shrinks if the subPoller parameter is set. There is no way to pass this to the Java
             * API. We already guide the user in the right directiony by having the default poller
             * mode as system threads. If they want to use virtual threads anyway, subPoller will be
             * set and we will allocate too much space, so we issue a warning here.
             */
            if (!hasWarned) {
                LOGGER.log(System.Logger.Level.WARNING, "the VTHREAD_POLLERS poller mode is not well supported with UseEspressoLibs and might cause high resource usage!");
                hasWarned = true;
            }
        }
        return new TrufflePoller(true);
    }

    @Override
    Poller writePoller(boolean subPoller) throws IOException {
        if (subPoller) {
            // same reasoning as above
            if (!hasWarned) {
                LOGGER.log(System.Logger.Level.WARNING, "the VTHREAD_POLLERS poller mode is not well supported with UseEspressoLibs and might cause high resource usage!");
                hasWarned = true;
            }
        }
        return new TrufflePoller(false);
    }
}
