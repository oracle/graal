/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jfr.utils;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.concurrent.ThreadLocalRandom;

import com.oracle.svm.core.Uninterruptible;

/**
 * This class is based on the JDK 23+8 version of the HotSpot class {@code JfrPRNG} (see
 * hotspot/share/jfr/utilities/jfrRandom.inline.hpp).
 */
public class JfrRandom {
    private static final long PrngMult = 25214903917L;
    private static final long PrngAdd = 11;
    private static final long PrngModPower = 48;
    private static final long PrngModMask = (1L << PrngModPower) - 1;
    private static final double PrngDivisor = 67108864;

    private long random;

    public JfrRandom() {
        random = ThreadLocalRandom.current().nextLong();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public double nextUniform() {
        long rnd = (PrngMult * random + PrngAdd) & PrngModMask;
        random = rnd;

        int value = (int) (rnd >> (PrngModPower - 26));
        return value / PrngDivisor;
    }
}
