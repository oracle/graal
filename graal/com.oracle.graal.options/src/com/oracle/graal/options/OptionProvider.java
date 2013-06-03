/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.options;

import java.util.*;

/**
 * Describes the attributes of an {@linkplain Option option} and provides access to its
 * {@linkplain OptionValue value}. The {@link OptionProcessor} auto-generates instances of this
 * interface that are accessible as a {@linkplain ServiceLoader service}.
 */
public interface OptionProvider {

    /**
     * Gets the type of values stored in the option.
     */
    Class getType();

    /**
     * Gets a descriptive help message for the option.
     */
    String getHelp();

    /**
     * Gets the name of the option. It's up to the client of this object how to use the name to get
     * a user specified value for the option from the environment.
     */
    String getName();

    /**
     * Gets the boxed option value.
     */
    OptionValue<?> getOptionValue();
}
