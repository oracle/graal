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
package com.oracle.graal.graphbuilderconf;

import com.oracle.graal.api.meta.*;

/**
 * {@link ResolvedJavaMethod}s that can assign themselves an identifier for use in a side table
 * mapping methods to {@link InvocationPlugin}s.
 */
public interface InvocationPluginIdHolder extends ResolvedJavaMethod {
    /**
     * Sets the unique, positive, non-zero identifier for this method.
     */
    void setInvocationPluginId(int id);

    /**
     * Gets the identifier set by {@link #setInvocationPluginId(int)} or 0 if no
     * {@link InvocationPlugin} identifier was assigned to this method.
     */
    int getInvocationPluginId();
}
