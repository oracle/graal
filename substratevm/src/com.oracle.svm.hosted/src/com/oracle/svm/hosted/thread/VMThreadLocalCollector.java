/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.thread;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;

/**
 * Collects all {@link FastThreadLocal} instances that are actually used by the application.
 */
class VMThreadLocalCollector implements Function<Object, Object> {

    final Map<FastThreadLocal, VMThreadLocalInfo> threadLocals = new ConcurrentHashMap<>();
    private boolean sealed;

    @Override
    public Object apply(Object source) {
        if (source instanceof FastThreadLocal) {
            FastThreadLocal threadLocal = (FastThreadLocal) source;
            if (sealed) {
                assert threadLocals.containsKey(threadLocal) : "VMThreadLocal must have been discovered during static analysis";
            } else {
                threadLocals.putIfAbsent(threadLocal, new VMThreadLocalInfo(threadLocal));
            }
        }
        /*
         * We want to collect all instances without actually replacing them, so we always return the
         * source object.
         */
        return source;
    }

    public VMThreadLocalInfo getInfo(FastThreadLocal threadLocal) {
        VMThreadLocalInfo result = threadLocals.get(threadLocal);
        assert result != null;
        return result;
    }

    public VMThreadLocalInfo findInfo(GraphBuilderContext b, ValueNode threadLocalNode) {
        if (!threadLocalNode.isConstant()) {
            throw shouldNotReachHere("Accessed VMThreadLocal is not a compile time constant: " + b.getMethod().asStackTraceElement(b.bci()) + " - node " + unPi(threadLocalNode));
        }

        FastThreadLocal threadLocal = (FastThreadLocal) SubstrateObjectConstant.asObject(threadLocalNode.asConstant());
        VMThreadLocalInfo result = threadLocals.get(threadLocal);
        assert result != null;
        return result;
    }

    public List<VMThreadLocalInfo> sortThreadLocals() {
        sealed = true;
        for (VMThreadLocalInfo info : threadLocals.values()) {
            assert info.sizeInBytes == -1;
            if (info.sizeSupplier != null) {
                int unalignedSize = info.sizeSupplier.getAsInt();
                assert unalignedSize > 0;
                info.sizeInBytes = NumUtil.roundUp(unalignedSize, 8);
            } else {
                info.sizeInBytes = ConfigurationValues.getObjectLayout().sizeInBytes(info.storageKind);
            }
        }

        List<VMThreadLocalInfo> sortedThreadLocals = new ArrayList<>(threadLocals.values());
        sortedThreadLocals.sort(VMThreadLocalCollector::compareThreadLocal);
        return sortedThreadLocals;
    }

    private static int compareThreadLocal(VMThreadLocalInfo info1, VMThreadLocalInfo info2) {
        if (info1 == info2) {
            return 0;
        }

        /* Order by priority: lower maximum offsets first. */
        int result = Integer.compare(info1.maxOffset, info2.maxOffset);
        if (result == 0) {
            /* Order by size to avoid padding. */
            result = -Integer.compare(info1.sizeInBytes, info2.sizeInBytes);
            if (result == 0) {
                /* Ensure that all objects are contiguous. */
                result = -Boolean.compare(info1.isObject, info2.isObject);
                if (result == 0) {
                    /*
                     * Make the order deterministic by sorting by name. This is arbitrary, we can
                     * come up with any better ordering.
                     */
                    result = info1.name.compareTo(info2.name);
                }
            }
        }
        return result;
    }

    private static ValueNode unPi(ValueNode n) {
        ValueNode cur = n;
        while (cur instanceof PiNode) {
            cur = ((PiNode) cur).object();
        }
        return cur;
    }
}
