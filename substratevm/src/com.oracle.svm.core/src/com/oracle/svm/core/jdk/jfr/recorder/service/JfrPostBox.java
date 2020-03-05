/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.oracle.svm.core.jdk.jfr.recorder.service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class JfrPostBox {

    /**
     * Jfr messaging.
     *
     * Synchronous messages (posting thread waits for message completion):
     *
     * MSG_CLONE_IN_MEMORY (0) ; MSGBIT(MSG_CLONE_IN_MEMORY) == (1 << 0) == 0x1
     * MSG_START(1) ; MSGBIT(MSG_START) == (1 << 0x1) == 0x2 MSG_STOP (2) ;
     * MSGBIT(MSG_STOP) == (1 << 0x2) == 0x4 MSG_ROTATE (3) ; MSGBIT(MSG_ROTATE) ==
     * (1 << 0x3) == 0x8 MSG_VM_ERROR (8) ; MSGBIT(MSG_VM_ERROR) == (1 << 0x8) ==
     * 0x100 MSG_FLUSHPOINT (10) ; MSGBIT(MSG_FLUSHPOINT) == (1 << 0xa) == 0x400
     *
     * Asynchronous messages (posting thread returns immediately upon deposit):
     *
     * MSG_FULLBUFFER (4) ; MSGBIT(MSG_FULLBUFFER) == (1 << 0x4) == 0x10
     * MSG_CHECKPOINT (5) ; MSGBIT(CHECKPOINT) == (1 << 0x5) == 0x20 MSG_WAKEUP (6)
     * ; MSGBIT(WAKEUP) == (1 << 0x6) == 0x40 MSG_SHUTDOWN (7) ;
     * MSGBIT(MSG_SHUTDOWN) == (1 << 0x7) == 0x80 MSG_DEADBUFFER (9) ;
     * MSGBIT(MSG_DEADBUFFER) == (1 << 0x9) == 0x200
     */

    public enum JfrMsg {
        // Synchronous: posting thread waits for message completion
        CLONE_IN_MEMORY(0),
        START(1),
        STOP(2),
        ROTATE(3),
        VM_ERROR(8),
        FLUSHPOINT(10),
        // Asynchronous: posting thread returns after depositing message
        FULLBUFFER(4),
        CHECKPOINT(5),
        WAKEUP(6),
        SHUTDOWN(7),
        DEADBUFFER(9);

        public final int msg;
        public final int bit;

        JfrMsg(int msg) {
            this.msg = msg;
            this.bit = 1 << msg;
        }

        private static final int synchronousMsgs = CLONE_IN_MEMORY.bit | START.bit | STOP.bit
                | ROTATE.bit | VM_ERROR.bit | FLUSHPOINT.bit;

        public boolean isSynchronous() {
            return (bit & synchronousMsgs) != 0;
        }

        public boolean in(int messages) {
            return (messages & bit) == bit;
        }
    }

    private static JfrPostBox instance;
    // Must read/write in thread-safe manner
    protected final ReentrantLock lock = new ReentrantLock();
    protected final Condition processing = lock.newCondition();
    private final AtomicInteger messages = new AtomicInteger(0);
    private final AtomicInteger messagesRead = new AtomicInteger(0);
    private final AtomicInteger messagesHandled = new AtomicInteger(0);
    private volatile boolean hasWaiters = false;

    private JfrPostBox() {
    }

    public static JfrPostBox create() {
        assert instance == null : "invariant";
        instance = new JfrPostBox();
        return instance;
    }

    protected boolean isThreadLockAversive() {
        // JFR.TODO: Figure out if this is something we have to deal with on SVM
        // Thread* const thread = Thread::current();
        // return (thread->is_Java_thread() && ((JavaThread*)thread)->thread_state() !=
        // _thread_in_vm) || thread->is_VM_thread();
        return false;
    }

    private boolean isSynchronous(int msg) {
        return (msg & JfrMsg.synchronousMsgs) != 0;
    }

    public void post(JfrMsg msg) {
        if (isThreadLockAversive()) {
            deposit(msg);
        } else if (!msg.isSynchronous()) {
            asyncPost(msg);
        } else {
            syncPost(msg);
        }
    }

    private void deposit(JfrMsg msg) {
        while (true) {
            int currentMsgs = messages.get();
            int newValue = currentMsgs | msg.bit;

            assert (msg.in(newValue));

            int result = messages.compareAndExchange(currentMsgs, newValue);
            if (result == currentMsgs) {
                return;
            }
            /* Some other thread just set exactly what this thread wanted */
            if ((result & newValue) == newValue) {
                return;
            }
        }
    }

    private void asyncPost(JfrMsg msg) {
        assert !msg.isSynchronous() : "invariant";
        deposit(msg);
        if (lock.tryLock()) {
            try {
                processing.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    private void syncPost(JfrMsg msg) {
        assert msg.isSynchronous() : "invariant";
        assert !lock.isHeldByCurrentThread() : "should not hold postboxLock here!";
        lock.lock();
        try {
            deposit(msg);
            // serialId is used to check when what we send in has been processed.
            // messagesRead is read under postboxLock protection.
            int serialId = messagesRead.get() + 1;
            processing.signalAll();
            while (!isMessageProcessed(serialId)) {
                try {
                    processing.await();
                } catch (InterruptedException e) {
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check if a synchronous message has been processed.
     * We avoid racing on _msg_handled_serial by ensuring
     * that we are holding the JfrMsg_lock when checking
     * completion status.
     */
    private boolean isMessageProcessed(int serial) {
        assert lock.isHeldByCurrentThread() : "messagesHandled must be read under postboxLock protection";
        return serial <= messagesHandled.get();
    }

    protected boolean isEmpty() {
        assert lock.isHeldByCurrentThread() : "not holding postboxLock!";
        return messages.get() == 0;
    }

    protected int collect() {
        // get pending and reset to 0
        int currentMsgs = messages.getAndSet(0);
        if (checkWaiters(currentMsgs)) {
            hasWaiters = true;
            assert lock.isHeldByCurrentThread() : "incrementing _msg_read_serial is protected by JfrMsg_lock";
            messagesRead.incrementAndGet();
        }
        return currentMsgs;
    }

    protected boolean checkWaiters(int messages) {
        assert lock.isHeldByCurrentThread() : "not holding postboxLock!";
        assert !hasWaiters : "invariant";
        return isSynchronous(messages);
    }

    protected void notifyWaiters() {
        if (hasWaiters) {
            hasWaiters = false;
            assert lock.isHeldByCurrentThread() : "incrementing messagesHandled must be protected by postboxLock.";
            messagesHandled.incrementAndGet();
            processing.signal();
        }
    }

    protected void notifyCollectionStop() {
        lock.lock();
        try {
            processing.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
