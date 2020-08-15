/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as cp from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import { registerLanguageServer } from './graalVMLanguageServer';

export const RUBY_LANGUAGE_SERVER_GEM_NAME: string = 'solargraph';
const INSTALL_RUBY_LANGUAGE_SERVER: string = 'Install Ruby Language Server';

export function rubyConfig(graalVMHome: string) {
    const executable: string = path.join(graalVMHome, 'bin', 'ruby');
    if (fs.existsSync(executable)) {
		setConfig('interpreter.commandPath', executable);
		return true;
	}
	return false;
}

function setConfig(section: string, path:string) {
	const config = vscode.workspace.getConfiguration('ruby');
	const term = config.inspect(section);
	if (term) {
		config.update(section, path, true);
	}
	const startRLS = vscode.workspace.getConfiguration('graalvm').get('languageServer.startRubyLanguageServer') as boolean;
	if (startRLS) {
		if (!isRubyGemInstalled(RUBY_LANGUAGE_SERVER_GEM_NAME)) {
			vscode.window.showInformationMessage('Solargraph gem is not installed in your GraalVM Ruby.', INSTALL_RUBY_LANGUAGE_SERVER).then(value => {
				switch (value) {
					case INSTALL_RUBY_LANGUAGE_SERVER:
						installRubyGem(RUBY_LANGUAGE_SERVER_GEM_NAME);
						break;
				}
			});
		} else {
            registerLanguageServer(() => startRubyLanguageServer());
		}
	}
}

function isRubyGemInstalled(name: string): boolean {
	const graalVMHome = vscode.workspace.getConfiguration('graalvm').get('home') as string;
	if (graalVMHome) {
		const executable: string = path.join(graalVMHome, 'bin', 'gem');
		if (executable) {
			try {
				const out = cp.execFileSync(executable, ['list', '-i', name], { encoding: 'utf8' });
				if (out.includes('true')) {
					return true;
				}
			} catch (err) {
				return false;
			}
		}
	}
	return false;
}

export function installRubyGem(name: string) {
	const graalVMHome = vscode.workspace.getConfiguration('graalvm').get('home') as string;
	if (graalVMHome) {
		const executable: string = path.join(graalVMHome, 'bin', 'gem');
		if (executable) {
			let terminal: vscode.Terminal | undefined = vscode.window.activeTerminal;
			if (!terminal) {
				terminal = vscode.window.createTerminal();
			}
			terminal.show();
			terminal.sendText(`${executable.replace(/(\s+)/g, '\\$1')} install ${name}`);
		}
	}
	return false;
}

function startRubyLanguageServer(): Thenable<string> {
	return new Promise<string>((resolve, reject) => {
		const graalVMHome = vscode.workspace.getConfiguration('graalvm').get('home') as string;
		if (graalVMHome) {
			const executable: string = path.join(graalVMHome, 'bin', 'solargraph');
			if (executable) {
				const child = cp.spawn(executable, ['socket', '--port', '0']);
				child.stderr.on('data', data => {
					var match = data.toString().match(/PORT=([0-9]*)[\s]+PID=([0-9]*)/);
					if (match) {
						const port = parseInt(match[1]);
						resolve(`ruby@${port}`);
					}
				});
				child.on('error', (err) => {
					reject(err);
				});
				child.on('exit', () => {
					reject(new Error("Solargraph exited"));
				});
			} else {
				reject(new Error("Cannot find 'solagraph' within your GraalVM installation."));
			}
		} else {
			reject(new Error("Cannot find your GraalVM installation."));
		}
	});
}
