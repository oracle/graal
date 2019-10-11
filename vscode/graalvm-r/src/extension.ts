/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';

const INSTALL_GRAALVM_R_COMPONENT: string = 'Install GraalVM R Component';

export function activate(context: vscode.ExtensionContext) {
	context.subscriptions.push(vscode.debug.registerDebugConfigurationProvider('graalvm', new GraalVMRConfigurationProvider()));
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
		const executable: string = path.join(graalVMHome, 'bin', 'R');
		if (!fs.existsSync(executable)) {
			vscode.window.showInformationMessage('R component is not installed in your GraalVM.', INSTALL_GRAALVM_R_COMPONENT).then(value => {
				switch (value) {
					case INSTALL_GRAALVM_R_COMPONENT:
						vscode.commands.executeCommand('extension.graalvm.installGraalVMComponent', 'R');
						const watcher:fs.FSWatcher = fs.watch(path.join(graalVMHome, 'bin'), () => {
							setConfig('rterm.linux', executable);
							watcher.close();
						});
						break;
				}
			});	
		} else {
			setConfig('rterm.linux', executable);
		}
	}
}

function setConfig(section: string, path:string) {
	const config = vscode.workspace.getConfiguration('r');
	const term = config.inspect(section);
	if (term) {
		config.update(section, path, true);
	}
	let termArgs = config.get('rterm.option') as string[];
	if (termArgs.indexOf('--inspect') < 0) {
		termArgs.push('--inspect');
		termArgs.push('--inspect.Suspend=false');
		config.update('rterm.option', termArgs, true);
	}
}

class GraalVMRConfigurationProvider implements vscode.DebugConfigurationProvider {

	resolveDebugConfiguration(_folder: vscode.WorkspaceFolder | undefined, config: vscode.DebugConfiguration, _token?: vscode.CancellationToken): vscode.ProviderResult<vscode.DebugConfiguration> {
		if (config.request === 'launch' && config.name === 'Launch R Term') {
			vscode.commands.executeCommand('r.createRTerm');
			config.request = 'attach';
		}
		return config;
	}
}
