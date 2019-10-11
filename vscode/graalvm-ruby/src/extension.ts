/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';

const INSTALL_GRAALVM_RUBY_COMPONENT: string = 'Install GraalVM Ruby Component';

export function activate(context: vscode.ExtensionContext) {
	context.subscriptions.push(vscode.workspace.onDidChangeConfiguration(e => {
		if (e.affectsConfiguration('graalvm.home')) {
			config();
		}
	}));
	config();
}

export function deactivate() {}

function config() {
	const graalVMHome = vscode.workspace.getConfiguration('graalvm').get('home') as string;
	if (graalVMHome) {
		const executable: string = path.join(graalVMHome, 'bin', 'ruby');
		if (!fs.existsSync(executable)) {
			vscode.window.showInformationMessage('Ruby component is not installed in your GraalVM.', INSTALL_GRAALVM_RUBY_COMPONENT).then(value => {
				switch (value) {
					case INSTALL_GRAALVM_RUBY_COMPONENT:
						vscode.commands.executeCommand('extension.graalvm.installGraalVMComponent', 'ruby');
						const watcher:fs.FSWatcher = fs.watch(path.join(graalVMHome, 'bin'), () => {
							setConfig('interpreter.commandPath', executable);
							watcher.close();
						});
						break;
				}
			});
		} else {
			setConfig('interpreter.commandPath', executable);
		}
	}
}

function setConfig(section: string, path:string) {
	const config = vscode.workspace.getConfiguration('ruby');
	const term = config.inspect(section);
	if (term) {
		config.update(section, path, true);
	}
}
