/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';

export function pythonConfig(graalVMHome: string): boolean {
    const executable: string = path.join(graalVMHome, 'bin', 'graalpython');
    if (fs.existsSync(executable)) {
        setConfig('pythonPath', executable);
        return true;
    }
    return false;
}

function setConfig(section: string, path:string) {
	const config = vscode.workspace.getConfiguration('python');
	const term = config.inspect(section);
	if (term) {
		config.update(section, path, true);
	}
}
