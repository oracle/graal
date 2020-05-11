/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.management.libgraal.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.graalvm.libgraal.jni.annotation.FromLibGraalId;

/**
 * Annotates methods associated with both ends of a libgraal to HotSpot call. This annotation
 * simplifies navigating between these methods in an IDE. The
 * {@code org.graalvm.compiler.hotspot.management.libgraal.processor.JMXFromLibGraalProcessor}
 * processor will produce a helper method for marshaling arguments and making the JNI call.
 */
@Repeatable(JMXFromLibGraalRepeated.class)
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface JMXFromLibGraal {
    /**
     * Gets the token identifying a call to HotSpot from libgraal.
     */
    Id value();

    /**
     * Identifier for a call to HotSpot from libgraal.
     */
    // Please keep sorted
    enum Id implements FromLibGraalId {
        GetFactory(Object.class),
        SignalRegistrationRequest(void.class, Object.class, long.class),
        Unregister(void.class, Object.class, long.class, String[].class);

        private final String signature;
        private final String methodName;
        private final Class<?> returnType;
        private final Class<?>[] parameterTypes;

        @Override
        public String getName() {
            return name();
        }

        @Override
        public String getSignature() {
            return signature;
        }

        @Override
        public String getMethodName() {
            return methodName;
        }

        @Override
        public Class<?>[] getParameterTypes() {
            return parameterTypes;
        }

        @Override
        public Class<?> getReturnType() {
            return returnType;
        }

        @Override
        public String toString() {
            return methodName + signature;
        }

        Id(Class<?> returnType, Class<?>... parameterTypes) {
            this.returnType = returnType;
            this.parameterTypes = parameterTypes;
            this.signature = FromLibGraalId.encodeMethodSignature(returnType, parameterTypes);
            this.methodName = Character.toLowerCase(name().charAt(0)) + name().substring(1);
        }
    }
}
