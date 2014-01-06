/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.subsystems;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

/**
 * Manages Ruby {@code Thread} objects.
 */
public class ThreadManager {

    private final ReentrantLock globalLock = new ReentrantLock();

    private final RubyThread rootThread;
    private RubyThread currentThread;

    private final Set<RubyThread> runningThreads = Collections.newSetFromMap(new ConcurrentHashMap<RubyThread, Boolean>());

    public ThreadManager(RubyContext context) {
        rootThread = new RubyThread(context.getCoreLibrary().getFiberClass(), this);
        runningThreads.add(rootThread);
        enterGlobalLock(rootThread);
    }

    /**
     * Enters the global lock. Reentrant, but be aware that Ruby threads are not one-to-one with
     * Java threads. Needs to be told which Ruby thread is becoming active as it can't work this out
     * from the current Java thread. Remember to call {@link #leaveGlobalLock} again before
     * blocking.
     */
    public void enterGlobalLock(RubyThread thread) {
        globalLock.lock();
        currentThread = thread;
    }

    /**
     * Leaves the global lock, returning the Ruby thread which has just stopped being the current
     * thread. Remember to call {@link #enterGlobalLock} again with that returned thread before
     * executing any Ruby code. You probably want to use this with a {@code finally} statement to
     * make sure that happens
     */
    public RubyThread leaveGlobalLock() {
        if (!globalLock.isHeldByCurrentThread()) {
            throw new RuntimeException("You don't own this lock!");
        }

        final RubyThread result = currentThread;
        globalLock.unlock();
        return result;
    }

    public RubyThread getCurrentThread() {
        return currentThread;
    }

    public void registerThread(RubyThread thread) {
        runningThreads.add(thread);
    }

    public void unregisterThread(RubyThread thread) {
        runningThreads.remove(thread);
    }

    public void shutdown() {
        for (RubyThread thread : runningThreads) {
            thread.shutdown();
        }
    }

}
