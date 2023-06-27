/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.common;

import org.graalvm.compiler.options.OptionDescriptor;

/**
 * Represents the description of a Truffle compiler options.
 */
public record TruffleCompilerOptionDescriptor(String name, Type type, boolean deprecated, String help, String deprecationMessage) {

    public TruffleCompilerOptionDescriptor(OptionDescriptor d) {
        this(d.getName(), matchGraalOptionType(d), d.isDeprecated(), d.getHelp(), d.getDeprecationMessage());
    }

    static Type matchGraalOptionType(OptionDescriptor d) {
        switch (d.getOptionType()) {
            case User:
                return Type.USER;
            case Expert:
                return Type.EXPERT;
            case Debug:
                return Type.DEBUG;
            default:
                return Type.DEBUG;
        }
    }

    public enum Type {
        /**
         * An option common for users to apply.
         */
        USER,

        /**
         * An option only relevant in corner cases and for fine-tuning.
         */
        EXPERT,

        /**
         * An option only relevant when debugging the compiler.
         */
        DEBUG
    }

}
