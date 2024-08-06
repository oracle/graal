/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.runtime.AbstractGraalTruffleRuntimeListener;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedCallTarget;

import org.junit.Assert;

public class DeoptInvalidateListener extends AbstractGraalTruffleRuntimeListener implements AutoCloseable {

    final OptimizedCallTarget focus;
    boolean deoptimized = false;
    boolean invalidated = false;

    @SuppressWarnings("this-escape")
    protected DeoptInvalidateListener(OptimizedTruffleRuntime runtime, OptimizedCallTarget focus) {
        super(runtime);
        this.focus = focus;
        runtime.addListener(this);
    }

    @Override
    public void onCompilationDeoptimized(OptimizedCallTarget target, Frame frame) {
        if (target == focus) {
            deoptimized = true;
        }
    }

    @Override
    public void onCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
        if (target == focus) {
            invalidated = true;
        }
    }

    public void assertValid() {
        Assert.assertFalse("the target was deoptimized", deoptimized);
        Assert.assertFalse("the target was invalidated", invalidated);
        Assert.assertTrue("the target has no attached machine code", focus.isValid());
    }

    @Override
    public void close() {
        runtime.removeListener(this);
    }
}
