/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.util;

public class StandaloneAnalysisException extends RuntimeException {
    private static final long serialVersionUID = 7859772432412904885L;

    public StandaloneAnalysisException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public StandaloneAnalysisException(String msg) {
        super(msg);
    }

    public static class DependencyNotFoundException extends StandaloneAnalysisException {

        private static final long serialVersionUID = -5325530793566369709L;

        public DependencyNotFoundException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    public static class HideJDKException extends StandaloneAnalysisException {

        private static final long serialVersionUID = -7641422021105371120L;

        public HideJDKException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    @Override
    public String toString() {
        return getMessage() + ":" + getCause().toString();
    }

    public static DependencyNotFoundException notFoundDependency(Throwable cause, String msg) {
        return new DependencyNotFoundException(msg, cause);
    }

    public static HideJDKException hideJDK(Throwable cause, String msg) {
        return new HideJDKException(msg, cause);
    }
}
