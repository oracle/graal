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
package com.oracle.svm.core.jdk;

import java.util.SplittableRandom;

import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;

public final class IdentityHashCodeSupport {

    private static final FastThreadLocalObject<SplittableRandom> hashCodeGeneratorTL = FastThreadLocalFactory.createObject(SplittableRandom.class);

    protected static int generateHashCode() {
        SplittableRandom hashCodeGenerator = hashCodeGeneratorTL.get();
        if (hashCodeGenerator == null) {
            /*
             * Create a new thread-local random number generator. SplittableRandom ensures that
             * values created by different random number generator instances are random as a whole.
             */
            hashCodeGenerator = new SplittableRandom();
            hashCodeGeneratorTL.set(hashCodeGenerator);
        }

        /*
         * The range of nextInt(MAX_INT) includes 0 and excludes MAX_INT, so adding 1 gives us the
         * range [1, MAX_INT] that we want.
         */
        int hashCode = hashCodeGenerator.nextInt(Integer.MAX_VALUE) + 1;

        assert hashCode != 0 : "Must not return 0 because it means 'hash code not computed yet' in the field that stores the hash code";
        assert hashCode > 0 : "The Java HotSpot VM only returns positive numbers for the identity hash code, so we want to have the same restriction on Substrate VM in order to not surprise users";

        return hashCode;
    }
}
