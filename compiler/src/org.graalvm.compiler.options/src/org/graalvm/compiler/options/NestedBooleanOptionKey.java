/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.options;

/**
 * A nested Boolean {@link OptionKey} that can be overridden by a {@link #parentOption parent
 * option}.
 * <p>
 * <li>If the option is present on the command line the specified value is used.
 * <li>Otherwise {@link #getValue} depends on the {@link #parentOption} and evaluates as follows:
 * <ul>
 * <li>If {@link #parentOption} is set, this value equals to {@link #initialValue}.
 * <li>Otherwise, if {@link #parentOption} is {@code false}, this option is {@code false}.
 */
public class NestedBooleanOptionKey extends OptionKey<Boolean> {
    private final OptionKey<Boolean> parentOption;
    private final Boolean initialValue;

    public NestedBooleanOptionKey(OptionKey<Boolean> parentOption, Boolean initialValue) {
        super(null);
        this.parentOption = parentOption;
        this.initialValue = initialValue;
    }

    public OptionKey<Boolean> getParentOption() {
        return parentOption;
    }

    @Override
    public Boolean getValue(OptionValues options) {
        Boolean v = super.getValue(options);
        if (v == null) {
            return initialValue && parentOption.getValue(options);
        }
        return v;
    }
}
