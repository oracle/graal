/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot.libgraal.processor;

import javax.annotation.processing.SupportedAnnotationTypes;

import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal;
import org.graalvm.libgraal.jni.processor.AbstractFromLibGraalProcessor;

/**
 * Processor for the {@link TruffleFromLibGraal} annotation that generates code to push JNI
 * arguments to the stack and make a JNI call corresponding to a
 * {@link org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id}. This helps
 * mitigate bugs where incorrect arguments are pushed for a JNI call. Given the low level nature of
 * {@code org.graalvm.nativeimage.StackValue}, it's very hard to use runtime assertion checking.
 */
@SupportedAnnotationTypes({
                "org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal",
                "org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraalRepeated"})
public class TruffleFromLibGraalProcessor extends AbstractFromLibGraalProcessor<Id> {

    public TruffleFromLibGraalProcessor() {
        super(Id.class);
    }
}
