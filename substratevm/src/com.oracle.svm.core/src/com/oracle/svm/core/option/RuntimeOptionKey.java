/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.option;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;

/**
 * Defines a runtime {@link Option}, in contrast to a {@link HostedOptionKey hosted option}.
 *
 * @see com.oracle.svm.core.option
 */
public class RuntimeOptionKey<T> extends OptionKey<T> {

    public RuntimeOptionKey(T defaultValue) {
        super(defaultValue);
    }

    /**
     * Returns the value of this option in the {@link RuntimeOptionValues}.
     */
    public T getValue() {
        return getValue(RuntimeOptionValues.singleton());
    }

    @Fold
    public T getHostedValue() {
        return getValue(RuntimeOptionValues.singleton());
    }
}
