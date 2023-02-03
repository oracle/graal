/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.resources;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;

import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.ExitStatus;

public final class MissingResourceMetadataException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public static class Options {
        @Option(help = "Enable termination caused by missing metadata.")//
        public static final HostedOptionKey<Boolean> ExitOnMissingMetadata = new HostedOptionKey<>(false);

        @Option(help = "Simulate exiting the program with an exception instead of calling System.exit() (for testing)")//
        public static final HostedOptionKey<Boolean> ExitWithException = new HostedOptionKey<>(false);

        @Option(help = "Throw Native Image-specific exceptions when encountering an unregistered reflection call.", type = OptionType.User)//
        public static final HostedOptionKey<Boolean> ThrowMissingMetadataExceptions = new HostedOptionKey<>(false);
    }

    private MissingResourceMetadataException(String message) {
        super(message);
    }

    public static MissingResourceMetadataException missingResource(String resourcePath) {
        MissingResourceMetadataException exception = new MissingResourceMetadataException(
                        "Resource at path " + resourcePath + " has not been registered as reachable. To ensure this resource is available at run time, you need to add it to the resource metadata.");
        if (MissingResourceMetadataException.Options.ExitOnMissingMetadata.getValue()) {
            exitOnMissingMetadata(exception);
        }
        return exception;
    }

    private static void exitOnMissingMetadata(MissingResourceMetadataException exception) {
        if (Options.ExitWithException.getValue()) {
            throw new ExitException(exception);
        } else {
            exception.printStackTrace(System.out);
            System.exit(ExitStatus.MISSING_METADATA.getValue());
        }
    }

    public static final class ExitException extends Error {
        private static final long serialVersionUID = 1L;

        private ExitException(Throwable cause) {
            super(cause);
        }
    }
}
