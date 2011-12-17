/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.util;

import java.util.*;

/**
 * Deferred Runnables.
 *
 * Creating a Deferrable either causes immediate execution of its 'run()' method
 * or queues it for deferred execution later on when 'runAll()' is called.
 */
public abstract class Deferrable implements Runnable {

    public Deferrable(Queue queue) {
        queue.handle(this);
    }

    @Override
    public String toString() {
        return super.toString();
    }

    public static final class Queue {

        private List<Deferrable> deferrables;

        private Queue() {
        }

        synchronized void handle(Deferrable deferrable) {
            if (deferrables != null) {
                deferrables.add(deferrable);
            } else {
                deferrable.run();
            }
        }

        public synchronized void deferAll() {
            deferrables = new LinkedList<Deferrable>();
        }

        public synchronized void runAll() {
            while (deferrables != null) {
                final List<Deferrable> oldDeferrables = this.deferrables;
                this.deferrables = new LinkedList<Deferrable>();
                for (Deferrable deferrable : oldDeferrables) {
                    deferrable.run();
                }
                if (oldDeferrables.isEmpty()) {
                    this.deferrables = null;
                }
            }
        }
    }

    public static Queue createRunning() {
        return new Queue();
    }

    public static Queue createDeferred() {
        final Queue queue = new Queue();
        queue.deferAll();
        return queue;
    }

    public abstract static class Block implements Runnable {
        public Block(Queue queue) {
            queue.deferAll();
            run();
            queue.runAll();
        }
    }
}
