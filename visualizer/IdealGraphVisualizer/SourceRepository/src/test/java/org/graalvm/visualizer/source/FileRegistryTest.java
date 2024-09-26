/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.source;


import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.graalvm.visualizer.source.FileRegistry.FileRegistryListener;
import org.junit.Ignore;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.platform.JavaPlatform;
import org.netbeans.junit.NbTestCase;
import org.openide.util.RequestProcessor;

import jdk.graal.compiler.graphio.parsing.model.InputGraph;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * @author sdedic
 */
public class FileRegistryTest extends NbTestCase {
    public FileRegistryTest(String name) {
        super(name);
    }

    private JavaPlatform platform;
    private ClassPath sourcePath;
    private FileRegistry fregistry;
    private InputGraph gr1;
    private InputGraph gr2;
    private FileRegistryListener listener;

    protected void setUp() throws Exception {
        super.setUp();
        fregistry = FileRegistry.getInstance();
        gr1 = InputGraph.createTestGraph("firstGraph");
        gr2 = InputGraph.createTestGraph("secondGraph");
        platform = JavaPlatform.getDefault();
        sourcePath = platform.getSourceFolders();

        FileRegistry._testReset();
        PlatformLocationResolver.enabled = false;
    }

    @Override
    protected void tearDown() throws Exception {
        PlatformLocationResolver.enabled = false;
        FileRegistry.getInstance().removeFileRegistryListener(listener);
        super.tearDown();
    }

    /**
     * Checks basic FileKey creation / registration
     */
    @Ignore("Unresolved test dependencies")
    public void testEnterFileKey() throws Exception {
        FileKey fk = new FileKey("text/x-java", "java/lang/String.java");
        FileKey registered = fregistry.enter(fk, gr1);

        assertFalse(fk.isResolved());
        assertSame(fk, registered);

        FileKey fk2 = new FileKey("text/x-java", "java/lang/String.java");
        registered = fregistry.enter(fk2, gr2);

        assertNotSame(registered, fk2);
        assertSame(fk, registered);

        FileKey fk3 = FileKey.fromFile(sourcePath.findResource("java/lang/String.java"));
        assertTrue(fk3.isResolved());
        assertEquals("text/x-java", fk3.getMime());
        assertNotNull(fk3.getResolvedFile());

        registered = fregistry.enter(fk3, gr1);

        FileKey fkx = new FileKey("text/x-xjava", "java/lang/String.java");
        registered = fregistry.enter(fkx, gr1);

        assertNotSame(fkx, fk);
    }

    @Ignore("Unresolved test dependencies")
    public void testEnterResolvedKey() throws Exception {
        FileKey fk = FileKey.fromFile(sourcePath.findResource("java/lang/String.java"));
        assertTrue(fk.isResolved());
        FileKey registered = fregistry.enter(fk, gr1);
        assertSame(registered, fk);

        FileKey fk2 = new FileKey("text/x-java", "java.base/java/lang/String.java");
        registered = fregistry.enter(fk2, gr1);
        assertTrue(registered.isResolved());
        assertSame(registered, fk);

        FileKey fk3 = new FileKey("text/x-java", "java.base/java/lang/String.java");
    }


    class L implements FileRegistryListener {
        Collection<FileKey> keys = new ArrayList<>();

        @Override
        public void filesResolved(FileRegistry.FileRegistryEvent ev) {
            synchronized (FileRegistryTest.this) {
                keys.addAll(ev.getResolvedKeys());
            }
        }
    }

    L l = new L();

    {
        listener = l;
    }

    @Ignore("Unresolved test dependencies")
    public void testResolvedReplaces() throws Exception {
        listener = l;
        fregistry.addFileRegistryListener(l);
        FileKey fk = new FileKey("text/x-java", "java.base/java/lang/String.java");
        FileKey registered = fregistry.enter(fk, gr1);
        assertSame(fk, registered);
        assertFalse(fk.isResolved());
        fregistry.EVENT_TASK.waitFinished();
        assertTrue(l.keys.isEmpty());

        FileKey fk3 = FileKey.fromFile(sourcePath.findResource("java/lang/String.java"));
        synchronized (this) {
            registered = fregistry.enter(fk3, gr2);
            // check that events are fired in other thread
            assertTrue(l.keys.isEmpty());
        }

        assertNotSame(fk3, registered);

        assertTrue(fk3.isResolved());
        assertTrue(registered.isResolved());
        assertTrue(fk.isResolved());
        fregistry.EVENT_TASK.waitFinished();
        assertTrue(!l.keys.isEmpty());
    }

    @Ignore("Unresolved test dependencies")
    public void testAttemptResolve() throws Exception {
        PlatformLocationResolver.enabled = true;
        FileKey fk = new FileKey("text/x-java", "java/lang/String.java");
        FileKey registered = fregistry.enter(fk, gr1);

        assertSame(fk, registered);
        assertFalse(fk.isResolved());

        // should post a revalidate task
        fregistry.attemptResolve(fk);

        synchronized (this) {
            FileRegistry.RP.post(() -> {
            }).waitFinished();
        }

        fregistry.EVENT_TASK.waitFinished();
        assertTrue(fk.isResolved());
    }

    @Ignore("Unresolved test dependencies")
    public void testForcedRevalidation() throws Exception {
        FileKey fk = new FileKey("text/x-java", "java/lang/String.java");
        FileKey registered = fregistry.enter(fk, gr1);
        fregistry.addFileRegistryListener(listener);

        assertSame(fk, registered);
        assertFalse(fk.isResolved());

        fregistry.resolve(fk, sourcePath.findResource("java/lang/String.java"));
        assertTrue(fk.isResolved());
        FileRegistry.RP.post(() -> {
        }, 500).waitFinished();
        fregistry.EVENT_TASK.waitFinished();
        assertTrue(l.keys.contains(fk));
    }

    @Ignore("Unresolved test dependencies")
    public void testValidFileForcesRevalidationOfUnknowns() throws Exception {
        FileKey fk = new FileKey("text/x-java", "java/lang/String.java");
        FileKey registered = fregistry.enter(fk, gr1);
        fregistry.addFileRegistryListener(listener);

        assertSame(fk, registered);
        assertFalse(fk.isResolved());

        FileKey fk2 = new FileKey("text/x-java", "java/lang/Object.java");
        registered = fregistry.enter(fk2, gr2);

        PlatformLocationResolver.enabled = true;
        fregistry.resolve(fk2, sourcePath.findResource("java/lang/Object.java"));
        assertTrue(fk2.isResolved());
        // wait until the resolve task settles
        FileRegistry.RP.post(() -> {
        }, 500).waitFinished();
        fregistry.EVENT_TASK.waitFinished();
        assertTrue(fk.isResolved());
        assertTrue(l.keys.contains(fk));
    }

    /**
     * Checks that unresolved keys entered in the registry eventually expire.
     *
     * @throws Exception
     */
    public void testUnresolvedKeysExpire() throws Exception {
        FileKey fk = new FileKey("text/x-java", "java/lang/String.java");
        FileKey registered = fregistry.enter(fk, gr1);

        assertFalse(fk.isResolved());
        assertSame(fk, registered);

        Reference<FileKey> refK = new WeakReference<>(fk);
        fk = null;
        registered = null;
        assertGC("Reference must be freed", refK);
    }

    public static Future waitForRevalidation() {
        class FirstWait implements Runnable {
            RequestProcessor.Task eventWait;
            boolean phase;

            @Override
            public void run() {
                if (!phase) {
                    eventWait = FileRegistry.getInstance().EVENT_TASK;
                }
                phase = true;
            }
        }
        final FirstWait fw = new FirstWait();
        RequestProcessor.Task t = FileRegistry.RP.post(fw, 200);

        return new Future() {
            private boolean done;

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                if (done) {
                    return true;
                }
                return t.isFinished() && fw.eventWait.isFinished();
            }

            @Override
            public Object get() throws InterruptedException, ExecutionException {
                t.waitFinished();
                fw.eventWait.waitFinished();
                done = true;
                return null;
            }

            @Override
            public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                t.waitFinished(unit.toMillis(timeout));
                fw.eventWait.waitFinished(unit.toMillis(timeout));
                done = true;
                return null;
            }
        };
    }
}
