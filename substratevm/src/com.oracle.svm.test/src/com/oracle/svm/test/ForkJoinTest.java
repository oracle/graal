/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test;

import java.io.FileNotFoundException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

import org.junit.Assert;
import org.junit.Test;

public class ForkJoinTest {

    @SuppressWarnings("unused")
    @Test
    public void testForkJoinTaskGetWithException() {
        ForkJoinPool pool = ForkJoinPool.commonPool();
        ForkJoinTask<Object> task = pool.submit(() -> {
            throw new FileNotFoundException("no substitution required");
        });

        try {
            Object result = task.get();
            Assert.fail("should have thrown an exception");
        } catch (final ExecutionException e) {
            // find FileNotFoundException within the exception hierarchy
            FileNotFoundException expected = null;
            Throwable cause = e.getCause();
            while (cause != null) {
                if (cause instanceof FileNotFoundException) {
                    expected = (FileNotFoundException) cause;
                    break;
                }
                cause = cause.getCause();
            }
            Assert.assertNotNull("FileNotFoundException wasn't thrown", expected);
            Assert.assertEquals("Unexpected message in thrown exception", "no substitution required", expected.getMessage());
        } catch (Throwable t) {
            Assert.fail("expected a FileNotFoundException but got " + t);
        }
    }
}
