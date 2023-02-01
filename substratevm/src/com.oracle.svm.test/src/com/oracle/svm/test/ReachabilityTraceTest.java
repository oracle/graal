/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.hosted.Feature;
import org.junit.Test;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisElement;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.util.ReflectionUtil;

class ReachableTypesFeature implements Feature {
    @Override
    public void afterAnalysis(AfterAnalysisAccess a) {
        FeatureImpl.AfterAnalysisAccessImpl access = (FeatureImpl.AfterAnalysisAccessImpl) a;
        AnalysisMetaAccess metaAccess = access.getMetaAccess();
        BigBang bb = access.getBigBang();

        List<Class<?>> classes = new ArrayList<>();
        classes.add(ReflectionUtil.lookupClass(false, "jdk.internal.org.xml.sax.ErrorHandler"));
        classes.add(ReflectionUtil.lookupClass(false, "javax.xml.stream.FactoryFinder"));
        classes.add(java.util.StringJoiner.class);
        classes.add(ReflectionUtil.lookupClass(false, "jdk.internal.math.FDBigInteger"));
        metaAccess.lookupJavaType(classes.get(0));

        for (Class<?> clazz : classes) {
            AnalysisType type = metaAccess.lookupJavaType(clazz);
            if (type.isAllocated()) {
                String header = "Type " + type.toJavaName() + " is marked as allocated";
                String trace = AnalysisElement.ReachabilityTraceBuilder.buildReachabilityTrace(bb, type.getAllocatedReason(), header);
                System.out.println(trace);
            } else if (type.isInHeap()) {
                String header = "Type " + type.toJavaName() + " is marked as in-heap";
                String trace = AnalysisElement.ReachabilityTraceBuilder.buildReachabilityTrace(bb, type.getInHeapReason(), header);
                System.out.println(trace);
            } else if (type.isReachable()) {
                String header = "Type " + type.toJavaName() + " is marked as reachable";
                String trace = AnalysisElement.ReachabilityTraceBuilder.buildReachabilityTrace(bb, type.getReachableReason(), header);
                System.out.println(trace);
            } else {
                continue;
            }
        }
    }
}

public class ReachabilityTraceTest {

    @Test
    public void test() {
        System.out.format("Hello %s", "World");
    }
}
