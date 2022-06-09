/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver;

import java.util.regex.Pattern;

import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.driver.NativeImage.ArgumentQueue;

class EarlyOptionHandler extends NativeImage.OptionHandler<NativeImage> {

    EarlyOptionHandler(NativeImage nativeImage) {
        super(nativeImage);
    }

    @Override
    boolean consume(ArgumentQueue args) {
        String headArg = args.peek();
        boolean consumed = consume(args, headArg);
        OptionOrigin origin = OptionOrigin.from(args.argumentOrigin);
        if (consumed && !origin.commandLineLike()) {
            String msg = String.format("Using '%s' provided by %s is only allowed on command line.", headArg, origin);
            throw NativeImage.showError(msg);
        }
        return consumed;
    }

    private boolean consume(ArgumentQueue args, String headArg) {
        switch (headArg) {
            case "--exclude-config":
                args.poll();
                String excludeJar = args.poll();
                if (excludeJar == null) {
                    NativeImage.showError(headArg + " requires two arguments: a jar regular expression and a resource regular expression");
                }
                String excludeConfig = args.poll();
                if (excludeConfig == null) {
                    NativeImage.showError(headArg + " requires resource regular expression");
                }
                nativeImage.addExcludeConfig(Pattern.compile(excludeJar), Pattern.compile(excludeConfig));
                return true;
        }
        return false;
    }
}
