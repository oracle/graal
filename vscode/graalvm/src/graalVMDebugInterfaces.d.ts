/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
 
import * as Core from 'vscode-chrome-debug-core';

type ConsoleType = 'internalConsole' | 'integratedTerminal';
type OutputCaptureType = 'console' | 'std';

export interface ICommonRequestArgs extends Core.ICommonRequestArgs {
    /** TCP/IP address of process to be debugged. Default is 'localhost'. */
    address?: string;
    /** Optional timeout to connect */
    timeout?: number;
}

export interface ILaunchRequestArguments extends Core.ILaunchRequestArgs, ICommonRequestArgs {
    /** An absolute path to the program to debug. */
    program: string;
    /** Optional arguments passed to the debuggee. */
    args?: string[];
    /** Launch the debuggee in this working directory (specified as an absolute path). If omitted the debuggee is lauched in its own directory. */
    cwd: string;
    /** Absolute path to the runtime executable to be used. */
    runtimeExecutable?: string;
    /** Optional arguments passed to the runtime executable. */
    runtimeArgs?: string[];
    /** Where to launch the debug target. */
    console?: ConsoleType;
    /** Manually selected debugging port */
    port?: number;
    /** Source of the debug output */
    outputCapture?: OutputCaptureType;
    /** Optional path to GraalVM home directory. */
    graalVMHome?: string;
}

export interface IAttachRequestArguments extends Core.IAttachRequestArgs, ICommonRequestArgs {
    /** Node's root directory. */
    remoteRoot?: string;
    /** VS Code's root directory. */
    localRoot?: string;
}
