/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.processor;

import java.util.Collections;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * passed around during espresso annotation processing. It is meant to be subclassed to serve as
 * storage for the data required during processing.
 * 
 * @see NativeEnvProcessor.IntrinsincsHelper
 * @see com.oracle.truffle.espresso.processor.SubstitutionProcessor.SubstitutorHelper
 */
public class SubstitutionHelper {
    List<GuestCall> guestCalls;
    boolean hasMetaInjection;
    boolean hasProfileInjection;
    TypeElement node;

    public SubstitutionHelper(EspressoProcessor processor, ExecutableElement method) {
        this.guestCalls = processor.getGuestCalls(method);
        this.hasMetaInjection = processor.hasMetaInjection(method);
        this.hasProfileInjection = processor.hasProfileInjection(method);
    }

    public SubstitutionHelper(EspressoProcessor processor, TypeElement node) {
        this.guestCalls = Collections.emptyList();
        this.hasMetaInjection = false;
        this.hasProfileInjection = false;
        this.node = node;
    }

    public static final class GuestCall {
        public final String target;
        public final boolean original;

        public GuestCall(String target, boolean original) {
            this.target = target;
            this.original = original;
        }

        @Override
        public String toString() {
            return target;
        }
    }
}
