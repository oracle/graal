/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.java;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.collections.EconomicMap;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Formats method names so that different compilations of a method can be correlated. If the
 * method's holder is a hidden class, the formatter creates a stable name for the holder.
 */
public class StableMethodNameFormatter implements Function<ResolvedJavaMethod, String> {

    /**
     * Separates method names and multi-method keys.
     *
     * For example, consider method {@code java.util.HashMap.size()}. A specialized variant of the
     * method may be created for different compilation scenarios. When a variant of the method is
     * created, it is named {@code java.util.HashMap.size%%key()}. The sequence after the separator
     * ({@code "key"} in this case) is the multi-method key of the variant.
     */
    public static final String MULTI_METHOD_KEY_SEPARATOR = "%%";

    /**
     * The prefix of the unqualified name part of a hidden class name returned by
     * {@link Class#getName()}, as defined by <a href="https://openjdk.org/jeps/371">JEP 371</a>.
     */
    private static final String UNQUALIFIED_NAME_PREFIX = "/0x";

    /**
     * A pattern matching the unqualified name part of a hidden class returned by
     * {@link Class#getName()}, as defined by <a href="https://openjdk.org/jeps/371">JEP 371</a>.
     */
    private static final Pattern UNQUALIFIED_NAME_PATTERN = Pattern.compile("/0x[0-9a-f]+");

    /**
     * The format of the methods passed to {@link ResolvedJavaMethod#format(String)}.
     */
    public static final String METHOD_FORMAT = "%H.%n(%p)";

    /**
     * Cached stable method names.
     */
    private final EconomicMap<ResolvedJavaMethod, String> methodName = EconomicMap.create();

    /**
     * Returns a stable method name, caching the result. If the method's holder is a hidden class
     * (i.e., lambda proxy, method handle), the unqualified name is replaced with a stable
     * {@link LambdaUtils#getSignature}.
     *
     * @param method the method to be formatted
     * @return a stable method name
     */
    @Override
    public String apply(ResolvedJavaMethod method) {
        String result = methodName.get(method);
        if (result != null) {
            return result;
        }
        result = findMethodName(method);
        methodName.put(method, result);
        return result;
    }

    /**
     * Returns a stable method name.
     *
     * @param method the method to be formatted
     * @return a stable method name
     */
    public static String findMethodName(ResolvedJavaMethod method) {
        String name = method.format(METHOD_FORMAT);
        Matcher matcher = UNQUALIFIED_NAME_PATTERN.matcher(name);
        if (matcher.find()) {
            String signature = LambdaUtils.getSignature(method.getDeclaringClass());
            if (signature == null) {
                return name;
            }
            return name.substring(0, matcher.start()) + UNQUALIFIED_NAME_PREFIX + signature + name.substring(matcher.end());
        }
        return name;
    }
}
