/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

// Checkstyle: allow reflection

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.Counter.Group;

/**
 * A counter that can be {@linkplain #inc() incremented}. The counting code is only emitted when an
 * option is enabled. Counters are {@link Group grouped} for printing.
 *
 * Currently there is no shutdown hook in Substrate VM that is invoked automatically, so
 * {@link Counter#logValues()} needs to be called manually at the end of the application to print
 * counter values.
 *
 * Use this class in the following way:
 *
 * <pre>
 * &#064;Option(help = &quot;Count foo&quot;)//
 * &#064;RuntimeOption//
 * public static final StableOptionValue&lt;Boolean&gt; FooCounters = new StableOptionValue&lt;&gt;(false);
 *
 * private static final Counter.Group fooCounters = new Counter.Group(FooCounters, &quot;Foo Counters&quot;);
 * private static final Counter fooCount = new Counter(fooCounters, &quot;foo&quot;, &quot;number of times foo was invoked&quot;);
 *
 * void foo() {
 *     fooCount.inc();
 * }
 * </pre>
 */
public final class Counter {

    /**
     * A group of related counters.
     */
    public static final class Group {

        protected final String name;
        protected Counter[] counters = new Counter[0];

        @Platforms(Platform.HOSTED_ONLY.class)//
        final HostedOptionKey<Boolean> enabledOption;

        /**
         * The actual enabled value, set according to the value of the {@link #enabledOption} during
         * image generation. This field must not be written at run time, otherwise constant folding
         * in the {@link Counter#inc()} method is not possible.
         */
        protected boolean enabled;

        @Platforms(Platform.HOSTED_ONLY.class)
        public Group(HostedOptionKey<Boolean> enabledOption, String name) {
            this.name = name;
            this.enabledOption = enabledOption;

            ImageSingletons.lookup(CounterGroupList.class).value.add(this);
        }

        public Counter[] getCounters() {
            return counters;
        }

        /**
         * Resets the values of all counters in this group to 0.
         */
        public void reset() {
            for (Counter counter : counters) {
                counter.reset();
            }
        }

        /**
         * Prints all counters of this group to the {@link Log}.
         */
        public void logValues() {
            long total = 0;
            int maxNameLen = 0;
            for (Counter counter : counters) {
                total += counter.getValue();
                maxNameLen = Math.max(counter.name.length(), maxNameLen);
            }

            Log.log().string("=== ").string(name).string(" ===").newline();
            for (Counter counter : counters) {
                long counterValue = counter.getValue();
                long percent = total == 0 ? 0 : counterValue * 100 / total;
                Log.log().string("  ").string(counter.name, maxNameLen, Log.RIGHT_ALIGN).string(":");
                Log.log().unsigned(counterValue, 10, Log.RIGHT_ALIGN).unsigned(percent, 5, Log.RIGHT_ALIGN).string("%");
                if (!counter.description.isEmpty()) {
                    Log.log().string("  // ").string(counter.description);
                }
                Log.log().newline();
            }
            Log.log().string("  ").string("TOTAL", maxNameLen, Log.RIGHT_ALIGN).string(":");
            Log.log().unsigned(total, 10, Log.RIGHT_ALIGN).newline();
        }
    }

    protected final Group group;
    protected final String name;
    protected final String description;
    protected long value;

    @Platforms(Platform.HOSTED_ONLY.class)//
    public static final Field VALUE_FIELD;

    static {
        try {
            VALUE_FIELD = Counter.class.getDeclaredField("value");
        } catch (NoSuchFieldException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    /**
     * Creates a counter.
     *
     * @param group the group to which the counter belongs.
     * @param name the name of the counter
     * @param description a brief comment describing the metric represented by the counter
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public Counter(Group group, String name, String description) {
        this.group = group;
        this.name = name;
        this.description = description;

        group.counters = Arrays.copyOf(group.counters, group.counters.length + 1);
        group.counters[group.counters.length - 1] = this;
    }

    /**
     * Returns the value of this counter.
     */
    @NeverInline("Counters can be incremented in snippets, prevent wrong memory access reordering")
    public long getValue() {
        return value;
    }

    public String getName() {
        return name;
    }

    /**
     * Increments the value of this counter.
     */
    @AlwaysInline("Constant folding and dead code elimination remove code for disabled counters")
    @Uninterruptible(reason = "Gets always inlined", mayBeInlined = true)
    public void inc() {
        add(1);
    }

    /**
     * Increments the value of this counter.
     */
    @AlwaysInline("Constant folding and dead code elimination remove code for disabled counters")
    @Uninterruptible(reason = "Gets always inlined", mayBeInlined = true)
    public void add(long increment) {
        if (group.enabled) {
            value += increment;
        }
    }

    /**
     * Resets the value of this counter to 0.
     */
    @NeverInline("Counters can be incremented in snippets, prevent wrong memory access reordering")
    public void reset() {
        value = 0;
    }

    /**
     * Prints all counters of all enabled groups to the {@link Log}.
     */
    public static void logValues() {
        for (Counter.Group group : ImageSingletons.lookup(CounterSupport.class).enabledGroups) {
            group.logValues();
        }
    }
}

@TargetClass(com.oracle.svm.core.util.Counter.class)
final class Target_com_oracle_svm_core_util_Counter {

    /*
     * Ensure that the counter value is 0 when execution starts at run time. We do not want to start
     * with counter values that were incremented during image generation.
     */
    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private long value;
}

class CounterGroupList {
    final List<Group> value = new ArrayList<>();
}

class CounterSupport {

    final Counter.Group[] enabledGroups;

    CounterSupport(Counter.Group[] enabledGroups) {
        this.enabledGroups = enabledGroups;
    }

}
