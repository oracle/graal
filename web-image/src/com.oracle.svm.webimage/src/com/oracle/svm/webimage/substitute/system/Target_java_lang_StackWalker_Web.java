/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.substitute.system;

import java.lang.StackWalker.StackFrame;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(value = java.lang.StackWalker.class)
@SuppressWarnings({"static-method", "unused"})
final class Target_java_lang_StackWalker_Web {

    @Substitute
    private void forEach(Consumer<? super StackFrame> action) {
        throw new UnsupportedOperationException("StackWalker.forEach");
    }

    @Substitute
    private Class<?> getCallerClass() {
        throw new UnsupportedOperationException("StackWalker.getCallerClass");
    }

    @Substitute
    private <T> T walk(Function<? super Stream<StackFrame>, ? extends T> function) {
        if (SyntheticStackSupport.MAIN_STACK_FRAME == null) {
            throw new UnsupportedOperationException("StackWalker.walk");
        }
        return function.apply(Stream.of(SyntheticStackSupport.MAIN_STACK_FRAME));
    }
}

final class SyntheticStackSupport {
    public static final StackTraceElement MAIN_STACK_TRACE_ELEMENT;
    static final StackFrame MAIN_STACK_FRAME;

    static {
        String mainClassName = SubstrateOptions.Class.getValue();
        String mainMethodName = SubstrateOptions.Method.getValue();
        if (mainClassName.isEmpty() || mainMethodName.isEmpty()) {
            MAIN_STACK_TRACE_ELEMENT = null;
            MAIN_STACK_FRAME = null;
        } else {
            MAIN_STACK_TRACE_ELEMENT = new StackTraceElement(mainClassName, mainMethodName, "", 0);
            MAIN_STACK_FRAME = new StackFrame() {
                @Override
                public String getClassName() {
                    return mainClassName;
                }

                @Override
                public String getMethodName() {
                    return mainMethodName;
                }

                @Override
                public Class<?> getDeclaringClass() {
                    try {
                        return Class.forName(mainClassName);
                    } catch (ClassNotFoundException e) {
                        throw new UnsupportedOperationException("getDeclaringClass in StackWalker.walk");
                    }
                }

                @Override
                public int getByteCodeIndex() {
                    return 0;
                }

                @Override
                public String getFileName() {
                    return MAIN_STACK_TRACE_ELEMENT.getFileName();
                }

                @Override
                public int getLineNumber() {
                    return MAIN_STACK_TRACE_ELEMENT.getLineNumber();
                }

                @Override
                public boolean isNativeMethod() {
                    return false;
                }

                @Override
                public StackTraceElement toStackTraceElement() {
                    return MAIN_STACK_TRACE_ELEMENT;
                }
            };
        }
    }
}
