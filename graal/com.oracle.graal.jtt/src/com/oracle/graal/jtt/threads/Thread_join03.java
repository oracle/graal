/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
/*
 *
 * This test sleeps the joining thread, which should enure that the joinee is
 * terminated by the time the join occurs.
 */
package com.oracle.graal.jtt.threads;

import org.junit.*;

import com.oracle.graal.jtt.*;

public class Thread_join03 extends JTTTest implements Runnable {

    static volatile boolean cont;

    public static boolean test() throws InterruptedException {
        cont = true;
        final Thread thread = new Thread(new Thread_join03());
        thread.start();
        Thread.sleep(200);
        thread.join();
        return cont;
    }

    public void run() {
        cont = false;
    }

    @Test(timeout = 20000)
    public void run0() throws Throwable {
        runTest("test");
    }

}
