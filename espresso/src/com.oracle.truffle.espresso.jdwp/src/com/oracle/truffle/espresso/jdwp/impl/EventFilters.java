/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp.impl;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class EventFilters {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private RequestFilter[] requestFilters = new RequestFilter[0];

    public void addFilter(RequestFilter filter) {
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            RequestFilter[] temp = new RequestFilter[requestFilters.length + 1];
            System.arraycopy(requestFilters, 0, temp, 0, requestFilters.length);
            temp[requestFilters.length] = filter;
            requestFilters = temp;
        } finally {
            writeLock.unlock();
        }
    }

    public RequestFilter getRequestFilter(int requestId) {
        Lock readLock = lock.readLock();
        readLock.lock();
        try {
            // likely the filters are required from last inserted
            for (int i = requestFilters.length - 1; i >= 0; i--) {
                RequestFilter filter = requestFilters[i];
                if (filter != null) {
                    if (filter.getRequestId() == requestId) {
                        return filter;
                    }
                }
            }
            return null;
        } finally {
            readLock.unlock();
        }
    }

    public RequestFilter removeRequestFilter(int requestId) {
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            // likely the filters are required from last inserted
            for (int i = requestFilters.length - 1; i >= 0; i--) {
                RequestFilter filter = requestFilters[i];
                if (filter != null) {
                    if (filter.getRequestId() == requestId) {
                        RequestFilter[] temp = new RequestFilter[requestFilters.length - 1];
                        // Copy with `i` index removed.
                        if (i > 0) {
                            System.arraycopy(requestFilters, 0, temp, 0, i);
                        }
                        if (i < requestFilters.length - 1) {
                            System.arraycopy(requestFilters, i + 1, temp, i, requestFilters.length - i - 1);
                        }
                        requestFilters = temp;
                        return filter;
                    }
                }
            }
            return null;
        } finally {
            writeLock.unlock();
        }
    }

    public void clearAll() {
        try {
            lock.writeLock().lock();
            // traverse all filters and clear all registered breakpoint information
            for (RequestFilter requestFilter : requestFilters) {
                BreakpointInfo breakpointInfo = requestFilter.getBreakpointInfo();
                if (breakpointInfo != null) {
                    breakpointInfo.dispose();
                }
            }
            requestFilters = new RequestFilter[0];
        } finally {
            lock.writeLock().unlock();
        }
    }
}
