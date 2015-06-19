/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

public class FilterTypes {

    public static void main(String... args) throws Exception {
        Class<?> jvmciServiceInterface = Class.forName(args[0]);
        StringBuilder buf = new StringBuilder();
        for (int i = 1; i < args.length; ++i) {
            String[] e = args[i].split("=");
            String serviceName = e[0];
            String implNames = e[1];

            StringBuilder impls = new StringBuilder();
            for (String implName : implNames.split(",")) {
                Class<?> impl = lookup(implName);
                if (impl != null && jvmciServiceInterface.isAssignableFrom(impl)) {
                    if (impls.length() != 0) {
                        impls.append(',');
                    }
                    impls.append(implName);
                }
            }
            if (impls.length() != 0) {
                if (buf.length() != 0) {
                    buf.append(' ');
                }
                buf.append(serviceName).append('=').append(impls);
            }
        }
        System.out.print(buf);
    }

    private static Class<?> lookup(String name) {
        try {
            // This can fail in the case of running against a JDK
            // with out of date JVMCI jars. In that case, just print
            // a warning since the expectation is that the jars will be
            // updated later on.
            return Class.forName(name, false, FilterTypes.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            // Must be stderr to avoid polluting the result being
            // written to stdout.
            System.err.println(e);
            return null;
        }
    }
}
