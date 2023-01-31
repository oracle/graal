/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.processor.test;

import java.util.List;
import java.util.Map;

public interface OutParameterService {

    void singleOutParameterVoid(List<String> p1);

    int singleOutParameterPrimitive(List<String> p1);

    int[] singleOutParameterArray(List<String> p1);

    Map<String, String> singleOutParameterCustom(List<String> p1);

    void multipleOutParametersVoid(List<String> p1, List<String> p2);

    int multipleOutParametersPrimitive(List<String> p1, List<String> p2);

    int[] multipleOutParametersArray(List<String> p1, List<String> p2);

    Map<String, String> multipleOutParametersCustom(List<String> p1, List<String> p2);

    void mixedParametersVoid(List<String> p1, List<String> p2, List<String> p3, List<String> p4);

    int mixedParametersPrimitive(List<String> p1, List<String> p2, List<String> p3, List<String> p4);

    int[] mixedParametersArray(List<String> p1, List<String> p2, List<String> p3, List<String> p4);

    Map<String, String> mixedParametersCustom(List<String> p1, List<String> p2, List<String> p3, List<String> p4);
}
