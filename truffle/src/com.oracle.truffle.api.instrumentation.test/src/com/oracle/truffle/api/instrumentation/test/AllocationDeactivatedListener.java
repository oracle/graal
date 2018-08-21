/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.Assert;

import com.oracle.truffle.api.instrumentation.AllocationReporter;

final class AllocationDeactivatedListener implements Consumer<Boolean> {

    private final AtomicInteger listenerCalls;
    private final AllocationReporter source;

    private AllocationDeactivatedListener(AllocationReporter source, AtomicInteger listenerCalls) {
        this.listenerCalls = listenerCalls;
        this.source = source;
    }

    @Override
    public void accept(Boolean active) {
        Assert.assertEquals(false, active);
        listenerCalls.incrementAndGet();
    }

    static AllocationDeactivatedListener register(AtomicInteger listenerCalls, AllocationReporter reporter) {
        AllocationDeactivatedListener deactivatedListener = new AllocationDeactivatedListener(reporter, listenerCalls);
        reporter.addActiveListener(deactivatedListener);
        return deactivatedListener;
    }

    void unregister() {
        source.removeActiveListener(this);

    }

}
