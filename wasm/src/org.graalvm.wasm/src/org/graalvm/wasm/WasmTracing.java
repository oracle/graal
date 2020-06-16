/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm;

import com.oracle.truffle.api.TruffleLogger;
import org.graalvm.wasm.constants.Debug;

public final class WasmTracing {
    private static final TruffleLogger logger = TruffleLogger.getLogger("wasm");

    private WasmTracing() {
    }

    public static void trace(String message) {
        if (Debug.TRACING) {
            logger.finest(message);
        }
    }

    public static void trace(String message, int value) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, value));
        }
    }

    public static void trace(String message, long value) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, value));
        }
    }

    public static void trace(String message, float value) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, value));
        }
    }

    public static void trace(String message, double value) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, value));
        }
    }

    public static void trace(String message, Object value) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, value));
        }
    }

    public static void trace(String message, int v0, int v1) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1));
        }
    }

    public static void trace(String message, long v0, long v1) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1));
        }
    }

    public static void trace(String message, float v0, float v1) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1));
        }
    }

    public static void trace(String message, double v0, double v1) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1));
        }
    }

    public static void trace(String message, int v0, float v1) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1));
        }
    }

    public static void trace(String message, int v0, double v1) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1));
        }
    }

    public static void trace(String message, Object v0, int v1) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1));
        }
    }

    public static void trace(String message, Object v0, Object v1) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1));
        }
    }

    public static void trace(String message, int v0, int v1, int v2) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2));
        }
    }

    public static void trace(String message, int v0, long v1, long v2) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2));
        }
    }

    public static void trace(String message, long v0, long v1, long v2) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2));
        }
    }

    public static void trace(String message, float v0, float v1, float v2) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2));
        }
    }

    public static void trace(String message, long v0, int v1, float v2) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2));
        }
    }

    public static void trace(String message, long v0, int v1, int v2) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2));
        }
    }

    public static void trace(String message, double v0, double v1, double v2) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2));
        }
    }

    public static void trace(String message, long v0, long v1, double v2) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2));
        }
    }

    public static void trace(String message, int v0, int v1, int v2, int v3) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2, v3));
        }
    }

    public static void trace(String message, int v0, long v1, long v2, long v3) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2, v3));
        }
    }

    public static void trace(String message, long v0, long v1, long v2, long v3) {
        if (Debug.TRACING) {
            logger.finest(String.format(message, v0, v1, v2, v3));
        }
    }
}
