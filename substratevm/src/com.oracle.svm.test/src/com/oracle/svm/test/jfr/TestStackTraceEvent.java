/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.test.jfr;

import org.graalvm.word.WordFactory;
import org.junit.Test;

import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.sampler.SamplerBuffer;
import com.oracle.svm.core.sampler.SamplerBufferAccess;
import com.oracle.svm.core.sampler.SamplerBuffersAccess;
import com.oracle.svm.core.sampler.SamplerThreadLocal;
import com.oracle.svm.test.jfr.events.StackTraceEvent;

/**
 * Test if event ({@link StackTraceEvent}) with stacktrace payload is working.
 */
public class TestStackTraceEvent extends JfrTest {
    private static final int LOCAL_BUFFER_SIZE = 1024;

    @Override
    public String[] getTestedEvents() {
        return new String[]{
                        StackTraceEvent.class.getName()
        };
    }

    @Test
    public void test() throws Exception {
        if (!HasJfrSupport.get()) {
            /*
             * The static analysis will find reachable the com.oracle.svm.core.jfr.SubstrateJVM via
             * processSamplerBuffer call. Since we are not supporting JFR on Windows yet, JfrFeature
             * will not add the SubstrateJVM to the list of all image singletons and therefore
             * InvocationPlugin will throw an exception while folding the SubstrateJVM (see
             * SubstrateJVM.get).
             *
             * Note that although we are building this JFR test for Windows as well, it will not be
             * executed because of guard in com.oracle.svm.test.jfr.JfrTest.checkForJFR.
             *
             * Once we have support for Windows, this check will become obsolete.
             */
            return;
        }

        /* Set thread-local buffer before stack walk. */
        SamplerBuffer buffer = SamplerBufferAccess.allocate(WordFactory.unsigned(LOCAL_BUFFER_SIZE));
        SamplerThreadLocal.setThreadLocalBuffer(buffer);

        /*
         * Create and commit an event. This will trigger
         * com.oracle.svm.core.jfr.JfrStackTraceRepository.getStackTraceId(int) call and stack walk.
         */
        StackTraceEvent event = new StackTraceEvent();
        event.commit();

        /* Call manually buffer processing. */
        SamplerBuffersAccess.processSamplerBuffer(buffer);

        /* We need to free memory manually as well afterward. */
        SamplerBufferAccess.free(buffer);
    }
}
