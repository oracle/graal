/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes.profiling;

import jdk.vm.ci.meta.ResolvedJavaMethod;

import static com.oracle.graal.nodeinfo.NodeCycles.CYCLES_10;
import static com.oracle.graal.nodeinfo.NodeSize.SIZE_50;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;

@NodeInfo(cycles = CYCLES_10, size = SIZE_50)
public class ProfileWithNotificationNode extends ProfileNode {
    public static final NodeClass<ProfileWithNotificationNode> TYPE = NodeClass.create(ProfileWithNotificationNode.class);

    protected int freqLog;

    protected ProfileWithNotificationNode(NodeClass<? extends ProfileNode> c, ResolvedJavaMethod method, int freqLog, int probabilityLog) {
        super(c, method, probabilityLog);
        this.freqLog = freqLog;
    }

    public ProfileWithNotificationNode(ResolvedJavaMethod method, int freqLog, int probabilityLog) {
        super(TYPE, method, probabilityLog);
        this.freqLog = freqLog;
    }

    /**
     * Get the logarithm base 2 of the notification frequency.
     */
    public int getNotificationFreqLog() {
        return freqLog;
    }

    /**
     * Set the logarithm base 2 of the notification frequency.
     */
    public void setNotificationFreqLog(int freqLog) {
        assert freqLog < 32;
        this.freqLog = freqLog;
    }

    public void setNotificationOff() {
        setNotificationFreqLog(-1);
    }

}
