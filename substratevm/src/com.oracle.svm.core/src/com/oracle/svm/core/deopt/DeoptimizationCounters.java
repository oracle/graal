/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.deopt;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalLong;
import com.oracle.svm.core.util.Counter;

public class DeoptimizationCounters {

    public static class Options {
        @Option(help = "Print logging information during object file writing")//
        static final HostedOptionKey<Boolean> ProfileDeoptimization = new HostedOptionKey<>(false);
    }

    private Counter.Group deoptCounters = new Counter.Group(Options.ProfileDeoptimization, "Deoptimization counters");
    Counter deoptCount = new Counter(deoptCounters, "Number of deoptimizations", "Number of deoptimizations that happened during the execution");
    Counter virtualFrameCount = new Counter(deoptCounters, "Number of virtual frames", "Number of virtual frames copied");
    Counter stackValueCount = new Counter(deoptCounters, "Number of stack values", "Number of target values on the stack");
    Counter constantValueCount = new Counter(deoptCounters, "Number of constant values", "Number of target values that are constant");
    Counter virtualObjectsCount = new Counter(deoptCounters, "Number of virtual objects", "Number of virtual objects re-allocated");
    Counter timeSpentInDeopt = new Counter(deoptCounters, "Time spent in deoptimization", "Time (ns) spend in deoptimization");
    /**
     * Rewriting the stack pointer does not allow passing the start time properly through the call
     * stack.
     */
    static final FastThreadLocalLong startTime = FastThreadLocalFactory.createLong();

    @Fold
    public static DeoptimizationCounters counters() {
        return ImageSingletons.lookup(DeoptimizationCounters.class);
    }
}
