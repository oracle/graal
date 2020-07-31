/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.svm.util;

import org.graalvm.word.WordBase;

/**
 * This class keeps the serialization checksum calculation algorithm at one place. The calculation
 * is employed from JVMTI Agent and SerializationSupport. The algorithm is identical but the code
 * cannot be reused because JVMTI Agent uses <code>org.graalvm.word.WordBase</code> as its base
 * class which is incompatible with regular Java world base class--<code>java.lang.Object</code>.
 *
 * So we prepare two inner classes for JVMCI Agent usage and regular Java usage respectively. The
 * two <code>calculateChecksum</code> methods should be modified together.
 */
public class SerializationChecksumCalculator {

    /**
     * This class is for JVMCI Agent.
     */
    public abstract static class JVMCIAgentCalculator {
        public long calculateChecksum(String targetConstructorClassName,
                        String serializationClassName,
                        WordBase serializationClass) {
            long checksum = 0L;
            if (isClassAbstract(serializationClass)) {
                return checksum;
            }
            if (targetConstructorClassName != null && targetConstructorClassName.length() > 0) {
                String currentClassName = serializationClassName;
                WordBase currentClass = serializationClass;
                while (!targetConstructorClassName.equals(currentClassName)) {
                    long classSUID = calculateFromComputeDefaultSUID(currentClass);
                    checksum = checksum * 31 + classSUID;
                    currentClass = getSuperClass(currentClass);
                    currentClassName = getClassName(currentClass);
                }
                checksum = checksum * 31 + targetConstructorClassName.hashCode();
            }
            return checksum;
        }

        protected abstract String getClassName(WordBase clazz);

        protected abstract WordBase getSuperClass(WordBase clazz);

        protected abstract Long calculateFromComputeDefaultSUID(WordBase clazz);

        protected abstract boolean isClassAbstract(WordBase clazz);

    }

    /**
     * This class is for regular Java usage.
     */
    public abstract static class JavaCalculator {
        public long calculateChecksum(String targetConstructorClassName,
                        String serializationClassName,
                        Class<?> serializationClass) {
            long checksum = 0L;
            if (isClassAbstract(serializationClass)) {
                return checksum;
            }
            if (targetConstructorClassName != null && targetConstructorClassName.length() > 0) {
                String currentClassName = serializationClassName;
                Class<?> currentClass = serializationClass;
                while (!targetConstructorClassName.equals(currentClassName)) {
                    long classSUID = calculateFromComputeDefaultSUID(currentClass);
                    checksum = checksum * 31 + classSUID;
                    currentClass = getSuperClass(currentClass);
                    currentClassName = getClassName(currentClass);
                }
                checksum = checksum * 31 + targetConstructorClassName.hashCode();
            }
            return checksum;
        }

        protected abstract String getClassName(Class<?> clazz);

        protected abstract Class<?> getSuperClass(Class<?> clazz);

        protected abstract Long calculateFromComputeDefaultSUID(Class<?> clazz);

        protected abstract boolean isClassAbstract(Class<?> clazz);
    }

}
