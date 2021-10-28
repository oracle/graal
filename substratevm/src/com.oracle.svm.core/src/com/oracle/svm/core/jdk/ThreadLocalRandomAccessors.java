/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk;

import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;

@TargetClass(java.util.concurrent.ThreadLocalRandom.class)
final class Target_java_util_concurrent_ThreadLocalRandom {

    /**
     * The seed generator for default constructors is initialized at run time, on first access, to
     * prevent baking in an initial seed from the build system.
     */
    @Alias @InjectAccessors(ThreadLocalRandomAccessors.class)//
    private static AtomicLong seeder;

    @Alias
    @TargetElement(onlyWith = JDK11OrEarlier.class)
    static native long mix64(long z);

}

@TargetClass(className = "jdk.internal.util.random.RandomSupport", onlyWith = JDK17OrLater.class)
final class Target_jdk_internal_util_random_RandomSupport {
    @Alias
    static native long mixMurmur64(long z);
}

public class ThreadLocalRandomAccessors extends RandomAccessors {

    private static final ThreadLocalRandomAccessors SINGLETON = new ThreadLocalRandomAccessors();

    /** The get-accessor for ThreadLocalRandom.seeder. */
    public static AtomicLong getSeeder() {
        return SINGLETON.getOrInitializeSeeder();
    }

    /** The setter is necessary if ThreadLocalRandom is initialized at run time. */
    public static void setSeeder(AtomicLong value) {
        SINGLETON.seeder = value;
    }

    @Override
    long mix64(long l) {
        if (JavaVersionUtil.JAVA_SPEC <= 11) {
            return Target_java_util_concurrent_ThreadLocalRandom.mix64(l);
        } else {
            return Target_jdk_internal_util_random_RandomSupport.mixMurmur64(l);
        }
    }
}
