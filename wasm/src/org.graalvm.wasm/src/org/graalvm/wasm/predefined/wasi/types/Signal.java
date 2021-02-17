/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This file has been automatically generated from wasi_snapshot_preview1.witx.
 */

package org.graalvm.wasm.predefined.wasi.types;

/** Signal condition. */
public enum Signal {

    /**
     * 0: No signal. Note that POSIX has special semantics for {@code kill(pid, 0)}, so this value
     * is reserved.
     */
    None,

    /** 1: Hangup. Action: Terminates the process. */
    Hup,

    /** 2: Terminate interrupt signal. Action: Terminates the process. */
    Int,

    /** 3: Terminal quit signal. Action: Terminates the process. */
    Quit,

    /** 4: Illegal instruction. Action: Terminates the process. */
    Ill,

    /** 5: Trace/breakpoint trap. Action: Terminates the process. */
    Trap,

    /** 6: Process abort signal. Action: Terminates the process. */
    Abrt,

    /** 7: Access to an undefined portion of a memory object. Action: Terminates the process. */
    Bus,

    /** 8: Erroneous arithmetic operation. Action: Terminates the process. */
    Fpe,

    /** 9: Kill. Action: Terminates the process. */
    Kill,

    /** 10: User-defined signal 1. Action: Terminates the process. */
    Usr1,

    /** 11: Invalid memory reference. Action: Terminates the process. */
    Segv,

    /** 12: User-defined signal 2. Action: Terminates the process. */
    Usr2,

    /** 13: Write on a pipe with no one to read it. Action: Ignored. */
    Pipe,

    /** 14: Alarm clock. Action: Terminates the process. */
    Alrm,

    /** 15: Termination signal. Action: Terminates the process. */
    Term,

    /** 16: Child process terminated, stopped, or continued. Action: Ignored. */
    Chld,

    /** 17: Continue executing, if stopped. Action: Continues executing, if stopped. */
    Cont,

    /** 18: Stop executing. Action: Stops executing. */
    Stop,

    /** 19: Terminal stop signal. Action: Stops executing. */
    Tstp,

    /** 20: Background process attempting read. Action: Stops executing. */
    Ttin,

    /** 21: Background process attempting write. Action: Stops executing. */
    Ttou,

    /** 22: High bandwidth data is available at a socket. Action: Ignored. */
    Urg,

    /** 23: CPU time limit exceeded. Action: Terminates the process. */
    Xcpu,

    /** 24: File size limit exceeded. Action: Terminates the process. */
    Xfsz,

    /** 25: Virtual timer expired. Action: Terminates the process. */
    Vtalrm,

    /** 26: Profiling timer expired. Action: Terminates the process. */
    Prof,

    /** 27: Window changed. Action: Ignored. */
    Winch,

    /** 28: I/O possible. Action: Terminates the process. */
    Poll,

    /** 29: Power failure. Action: Terminates the process. */
    Pwr,

    /** 30: Bad system call. Action: Terminates the process. */
    Sys;

    /** Converts enum item to primitive. */
    public byte toValue() {
        return (byte) this.ordinal();
    }

    /** Converts primitive to enum item. */
    public static Signal fromValue(byte value) {
        return Signal.values()[value];
    }

}
