/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pltgot;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMethod;

public class GOTEntryAllocator {
    public static final int GOT_NO_ENTRY = -2;

    private final ConcurrentHashMap<SharedMethod, Integer> gotMap = new ConcurrentHashMap<>();
    private SharedMethod[] got = null;

    private final AtomicInteger currentFreeEntry = new AtomicInteger(0);

    public int getMethodGotEntry(SharedMethod method) {
        return gotMap.computeIfAbsent(method, m -> currentFreeEntry.getAndIncrement());
    }

    public void reserveMethodGotEntry(SharedMethod method) {
        getMethodGotEntry(method);
    }

    public int queryGotEntry(SharedMethod method) {
        assert hasGOTLayout();
        return gotMap.getOrDefault(method, GOT_NO_ENTRY);
    }

    public void reserveAndLayout(Set<HostedMethod> methods, MethodAddressResolutionSupport resolver) {
        assert !hasGOTLayout();

        methods.stream()
                        .filter(resolver::shouldCallViaPLTGOT)
                        .forEach(this::reserveMethodGotEntry);

        VMError.guarantee(got == null, "Can layout the GOT only once.");
        got = new SharedMethod[gotMap.keySet().size()];
        for (Map.Entry<SharedMethod, Integer> entry : gotMap.entrySet()) {
            got[entry.getValue()] = entry.getKey();
        }
    }

    public boolean hasGOTLayout() {
        return got != null;
    }

    public SharedMethod[] getGOT() {
        VMError.guarantee(got != null, "Must layout the GOT first before use.");
        return got;
    }
}
