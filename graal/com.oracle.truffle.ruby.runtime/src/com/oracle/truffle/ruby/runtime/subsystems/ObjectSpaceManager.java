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

import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Supports the Ruby {@code ObjectSpace} module. Object IDs are lazily allocated {@code long}
 * values, mapped to objects with a weak hash map. Finalizers are implemented with weak references
 * and reference queues, and are run in a dedicated Ruby thread (but not a dedicated Java thread).
 */
public class ObjectSpaceManager {

    private class FinalizerReference extends WeakReference<RubyBasicObject> {

        public List<RubyProc> finalizers = new LinkedList<>();

        public FinalizerReference(RubyBasicObject object, ReferenceQueue<? super RubyBasicObject> queue) {
            super(object, queue);
        }

        public void addFinalizer(RubyProc proc) {
            finalizers.add(proc);
        }

        public List<RubyProc> getFinalizers() {
            return finalizers;
        }

        public void clearFinalizers() {
            finalizers = new LinkedList<>();
        }

    }

    private final RubyContext context;

    // TODO(cs): this is wrong - WeakHashMap is not weak in the value
    private final WeakHashMap<Long, RubyBasicObject> objects = new WeakHashMap<>();

    private final Map<RubyBasicObject, FinalizerReference> finalizerReferences = new WeakHashMap<>();
    private final ReferenceQueue<RubyBasicObject> finalizerQueue = new ReferenceQueue<>();
    private RubyThread finalizerThread;
    private Thread finalizerJavaThread;
    private boolean stop;
    private CountDownLatch finished = new CountDownLatch(1);

    public ObjectSpaceManager(RubyContext context) {
        this.context = context;
    }

    public void add(RubyBasicObject object) {
        objects.put(object.getObjectID(), object);
    }

    public RubyBasicObject lookupId(long id) {
        return objects.get(id);
    }

    public void defineFinalizer(RubyBasicObject object, RubyProc proc) {
        // Record the finalizer against the object

        FinalizerReference finalizerReference = finalizerReferences.get(object);

        if (finalizerReference == null) {
            finalizerReference = new FinalizerReference(object, finalizerQueue);
            finalizerReferences.put(object, finalizerReference);
        }

        finalizerReference.addFinalizer(proc);

        // If there is no finalizer thread, start one

        if (finalizerThread == null) {
            finalizerThread = new RubyThread(context.getCoreLibrary().getThreadClass(), context.getThreadManager());

            finalizerThread.initialize(new Runnable() {

                @Override
                public void run() {
                    runFinalizers();
                }

            });
        }
    }

    public void undefineFinalizer(RubyBasicObject object) {
        final FinalizerReference finalizerReference = finalizerReferences.get(object);

        if (finalizerReference != null) {
            finalizerReference.clearFinalizers();
        }
    }

    private void runFinalizers() {
        // Run in a loop

        while (true) {
            // Is there a finalizer ready to immediately run?

            FinalizerReference finalizerReference = (FinalizerReference) finalizerQueue.poll();

            if (finalizerReference != null) {
                runFinalizers(finalizerReference);
                continue;
            }

            // Check if we've been asked to stop

            if (stop) {
                break;
            }

            // Leave the global lock and wait on the finalizer queue

            final RubyThread runningThread = context.getThreadManager().leaveGlobalLock();
            finalizerJavaThread = Thread.currentThread();

            try {
                finalizerReference = (FinalizerReference) finalizerQueue.remove();
            } catch (InterruptedException e) {
                continue;
            } finally {
                context.getThreadManager().enterGlobalLock(runningThread);
            }

            runFinalizers(finalizerReference);
        }

        finished.countDown();
    }

    private static void runFinalizers(FinalizerReference finalizerReference) {
        try {
            for (RubyProc proc : finalizerReference.getFinalizers()) {
                proc.call(null);
            }
        } catch (Exception e) {
            // MRI seems to silently ignore exceptions in finalizers
        }
    }

    public void shutdown() {
        context.getThreadManager().enterGlobalLock(finalizerThread);

        try {
            // Tell the finalizer thread to stop and wait for it to do so

            if (finalizerThread != null) {
                stop = true;

                if (finalizerJavaThread != null) {
                    finalizerJavaThread.interrupt();
                }

                context.getThreadManager().leaveGlobalLock();

                try {
                    finished.await();
                } catch (InterruptedException e) {
                } finally {
                    context.getThreadManager().enterGlobalLock(finalizerThread);
                }
            }

            // Run any finalizers for objects that are still live

            for (FinalizerReference finalizerReference : finalizerReferences.values()) {
                runFinalizers(finalizerReference);
            }
        } finally {
            context.getThreadManager().leaveGlobalLock();
        }
    }

    public Collection<RubyBasicObject> getObjects() {
        return objects.values();
    }
}
