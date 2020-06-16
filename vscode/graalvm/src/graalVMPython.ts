/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';

const INSTALL_GRAALVM_PYTHON_COMPONENT: string = 'Install GraalVM Python Component';

export function pythonConfig(graalVMHome: string) {
    const executable: string = path.join(graalVMHome, 'bin', 'graalpython');
    if (!fs.existsSync(executable)) {
        vscode.window.showInformationMessage('Python component is not installed in your GraalVM.', INSTALL_GRAALVM_PYTHON_COMPONENT).then(value => {
            switch (value) {
                case INSTALL_GRAALVM_PYTHON_COMPONENT:
                    vscode.commands.executeCommand('extension.graalvm.installGraalVMComponent', 'python');
                    const watcher:fs.FSWatcher = fs.watch(path.join(graalVMHome + 'bin'), () => {
                        setConfig('pythonPath', executable);
                        watcher.close();
                    });
                    break;
            }
        });
    } else {
        setConfig('pythonPath', executable);
    }
}

function setConfig(section: string, path:string) {
	const config = vscode.workspace.getConfiguration('python');
	const term = config.inspect(section);
	if (term) {
		config.update(section, path, true);
	}
}
