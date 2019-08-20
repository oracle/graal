/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as path from 'path';
import * as os from 'os';
import { ChromeDebugSession } from 'vscode-chrome-debug-core';
import { GraalVMDebugAdapter } from './graalVMDebugAdapter';

ChromeDebugSession.run(ChromeDebugSession.getSession({
    adapter: GraalVMDebugAdapter,
    extensionName: 'graalvm',
    logFilePath: path.resolve(os.tmpdir(), 'vscode-graalvm-debug.txt'),
}));
