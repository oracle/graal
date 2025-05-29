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

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.pltgot.MethodAddressResolver;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;

class CollectPLTGOTCallSitesResolutionSupport implements MethodAddressResolutionSupport {
    private final MethodAddressResolutionSupport resolver;
    private final ConcurrentMap<HostedMethod, Set<HostedMethod>> callerCalleesMap;
    private final Set<HostedMethod> calleesWithUnknownCaller;

    CollectPLTGOTCallSitesResolutionSupport(MethodAddressResolutionSupport resolver) {
        this.resolver = resolver;
        this.callerCalleesMap = new ConcurrentSkipListMap<>(HostedUniverse.METHOD_COMPARATOR);
        this.calleesWithUnknownCaller = new ConcurrentSkipListSet<>(HostedUniverse.METHOD_COMPARATOR);
    }

    @Override
    public boolean shouldCallViaPLTGOT(SharedMethod caller, SharedMethod callee) {
        boolean shouldCall = resolver.shouldCallViaPLTGOT(caller, callee);
        if (shouldCall) {
            var callees = callerCalleesMap.computeIfAbsent((HostedMethod) caller, k -> ConcurrentHashMap.newKeySet());
            callees.add((HostedMethod) callee);
        }
        return shouldCall;
    }

    @Override
    public boolean shouldCallViaPLTGOT(SharedMethod callee) {
        boolean shouldCall = resolver.shouldCallViaPLTGOT(callee);
        if (shouldCall) {
            calleesWithUnknownCaller.add((HostedMethod) callee);
        }
        return shouldCall;
    }

    @Override
    public void augmentImageObjectFile(ObjectFile imageObjectFile) {
        resolver.augmentImageObjectFile(imageObjectFile);
    }

    @Override
    public MethodAddressResolver createMethodAddressResolver() {
        return resolver.createMethodAddressResolver();
    }

    public Map<HostedMethod, Set<HostedMethod>> getCallerCalleesMap() {
        return Collections.unmodifiableMap(callerCalleesMap);
    }

    public Set<HostedMethod> getCalleesWithUnknownCaller() {
        return Collections.unmodifiableSet(calleesWithUnknownCaller);
    }
}
