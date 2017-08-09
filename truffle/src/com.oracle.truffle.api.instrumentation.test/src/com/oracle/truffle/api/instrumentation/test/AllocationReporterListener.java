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

import com.oracle.truffle.api.instrumentation.AllocationReporter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;

final class AllocationReporterListener implements PropertyChangeListener {

    private final AtomicInteger listenerCalls;
    private final AllocationReporter source;

    private AllocationReporterListener(AllocationReporter source, AtomicInteger listenerCalls) {
        this.source = source;
        this.listenerCalls = listenerCalls;
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        Assert.assertEquals(AllocationReporter.PROPERTY_ACTIVE, event.getPropertyName());
        Assert.assertEquals(Boolean.FALSE, event.getOldValue());
        Assert.assertEquals(Boolean.TRUE, event.getNewValue());
        listenerCalls.incrementAndGet();
    }

    static AllocationReporterListener register(AtomicInteger listenerCalls, AllocationReporter reporter) {
        AllocationReporterListener activatedListener = new AllocationReporterListener(reporter, listenerCalls);
        reporter.addPropertyChangeListener(activatedListener);
        return activatedListener;
    }

    void unregister() {
        source.removePropertyChangeListener(this);
    }
}
