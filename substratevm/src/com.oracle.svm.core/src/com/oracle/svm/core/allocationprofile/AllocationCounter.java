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
package com.oracle.svm.core.allocationprofile;

// Checkstyle: allow reflection

import java.lang.reflect.Field;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.util.VMError;

/**
 * Passed as an argument to the allocation snippet during lowering.
 */
public class AllocationCounter {
    /** Name of the method that increments this counter. */
    private final String name;
    /** Next allocation counter - this value is only written during compilation. */
    @UnknownObjectField(types = AllocationCounter.class) private AllocationCounter next;
    /** Number of allocations. */
    private long count;
    /** Size of allocations in bytes. */
    private long size;

    @Platforms(Platform.HOSTED_ONLY.class)//
    public static final Field COUNT_FIELD;
    @Platforms(Platform.HOSTED_ONLY.class)//
    public static final Field SIZE_FIELD;

    static {
        try {
            COUNT_FIELD = AllocationCounter.class.getDeclaredField("count");
            SIZE_FIELD = AllocationCounter.class.getDeclaredField("size");
        } catch (NoSuchFieldException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    protected AllocationCounter(String name, AllocationCounter next) {
        this.name = name;
        this.next = next;
    }

    public final void incrementCount() {
        this.count++;
    }

    public final void incrementSize(long inc) {
        this.size += inc;
    }

    public String getName() {
        return name;
    }

    public AllocationCounter getNext() {
        return next;
    }

    @NeverInline("field is written in allocation snippet, so must not be accessed in method that performs allocation")
    public long getCount() {
        return count;
    }

    @NeverInline("field is written in allocation snippet, so must not be accessed in method that performs allocation")
    public long getSize() {
        return size;
    }
}
