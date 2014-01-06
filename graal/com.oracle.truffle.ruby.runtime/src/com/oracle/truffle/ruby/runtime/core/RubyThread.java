/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.core;

import java.util.*;
import java.util.concurrent.*;

import com.oracle.truffle.ruby.runtime.objects.*;
import com.oracle.truffle.ruby.runtime.subsystems.*;

/**
 * Represents the Ruby {@code Thread} class. Implemented using Java threads, but note that there is
 * not a one-to-one mapping between Ruby threads and Java threads - specifically in combination with
 * fibers as they are currently implemented as their own Java threads.
 */
public class RubyThread extends RubyObject {

    /**
     * The class from which we create the object that is {@code Thread}. A subclass of
     * {@link RubyClass} so that we can override {@link #newInstance} and allocate a
     * {@link RubyThread} rather than a normal {@link RubyBasicObject}.
     */
    public static class RubyThreadClass extends RubyClass {

        public RubyThreadClass(RubyClass objectClass) {
            super(null, objectClass, "Thread");
        }

        @Override
        public RubyBasicObject newInstance() {
            return new RubyThread(this, getContext().getThreadManager());
        }

    }

    private final ThreadManager manager;

    private final CountDownLatch finished = new CountDownLatch(1);

    private final int hashCode = new Random().nextInt();

    public RubyThread(RubyClass rubyClass, ThreadManager manager) {
        super(rubyClass);
        this.manager = manager;
    }

    public void initialize(RubyProc block) {
        final RubyProc finalBlock = block;

        initialize(new Runnable() {

            @Override
            public void run() {
                finalBlock.call(null);
            }

        });
    }

    public void initialize(Runnable runnable) {
        final RubyThread finalThread = this;
        final Runnable finalRunnable = runnable;

        new Thread(new Runnable() {

            @Override
            public void run() {
                finalThread.manager.registerThread(finalThread);
                finalThread.manager.enterGlobalLock(finalThread);

                try {
                    finalRunnable.run();
                } finally {
                    finalThread.manager.leaveGlobalLock();
                    finalThread.manager.unregisterThread(finalThread);
                    finalThread.finished.countDown();
                }
            }

        }).start();
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public void shutdown() {
    }

    public void join() {
        final RubyThread runningThread = getRubyClass().getContext().getThreadManager().leaveGlobalLock();

        try {
            while (true) {
                try {
                    finished.await();
                    break;
                } catch (InterruptedException e) {
                    // Await again
                }
            }
        } finally {
            runningThread.manager.enterGlobalLock(runningThread);
        }
    }

}
