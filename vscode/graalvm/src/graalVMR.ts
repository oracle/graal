/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as cp from 'child_process';
import * as fs from 'fs';
import * as net from 'net';
import * as path from 'path';
import { TextEncoder } from 'util';
import { registerLanguageServer } from './graalVMLanguageServer';

export const R_LANGUAGE_SERVER_PACKAGE_NAME: string = 'languageserver';
const INSTALL_GRAALVM_R_COMPONENT: string = 'Install GraalVM R Component';
const INSTALL_R_LANGUAGE_SERVER: string = 'Install R Language Server';

export function rConfig(graalVMHome: string) {
	const executable: string = path.join(graalVMHome, 'bin', 'R');
	if (!fs.existsSync(executable)) {
		vscode.window.showInformationMessage('R component is not installed in your GraalVM.', INSTALL_GRAALVM_R_COMPONENT).then(value => {
			switch (value) {
				case INSTALL_GRAALVM_R_COMPONENT:
					vscode.commands.executeCommand('extension.graalvm.installGraalVMComponent', 'R');
					const watcher:fs.FSWatcher = fs.watch(path.join(graalVMHome, 'bin'), () => {
						setConfig(executable);
						watcher.close();
					});
					break;
			}
		});
	} else {
		setConfig(executable);
	}
}

function setConfig(path: string) {
	const config = vscode.workspace.getConfiguration('r');
	let section: string = '';
	if (process.platform === 'linux') {
		section = 'rterm.linux';
	} else if (process.platform === 'darwin') {
		section = 'rterm.mac';
	}
	const term = section ? config.inspect(section) : undefined;
	if (term) {
		config.update(section, path, true);
	}
	let termArgs = config.get('rterm.option') as string[];
	if (termArgs.indexOf('--inspect') < 0) {
		termArgs.push('--inspect');
		termArgs.push('--inspect.Suspend=false');
		config.update('rterm.option', termArgs, true);
	}
	const startRLS = vscode.workspace.getConfiguration('graalvm').get('languageServer.startRLanguageServer') as boolean;
	if (startRLS) {
		if (!isRPackageInstalled(R_LANGUAGE_SERVER_PACKAGE_NAME)) {
			vscode.window.showInformationMessage('Language Server package is not installed in your GraalVM R.', INSTALL_R_LANGUAGE_SERVER).then(value => {
				switch (value) {
					case INSTALL_R_LANGUAGE_SERVER:
						installRPackage(R_LANGUAGE_SERVER_PACKAGE_NAME);
						break;
				}
			});
		} else {
			registerLanguageServer(() => startRLanguageServer());
		}
	}
}

function isRPackageInstalled(name: string): boolean {
	const graalVMHome = vscode.workspace.getConfiguration('graalvm').get('home') as string;
	if (graalVMHome) {
		const executable: string = path.join(graalVMHome, 'bin', 'R');
		if (executable) {
			const out = cp.execFileSync(executable, ['--quiet', '--slave', '-e', `ip<-installed.packages();is.element("${name}",ip[,1])`], { encoding: 'utf8' });
			if (out.includes('TRUE')) {
				return true;
			}
		}
	}
	return false;
}

export function installRPackage(name: string) {
	const graalVMHome = vscode.workspace.getConfiguration('graalvm').get('home') as string;
	if (graalVMHome) {
		const executable: string = path.join(graalVMHome, 'bin', 'R');
		if (executable) {
			let terminal: vscode.Terminal | undefined = vscode.window.activeTerminal;
			if (!terminal) {
				terminal = vscode.window.createTerminal();
			}
			terminal.show();
			terminal.sendText(`${executable.replace(/(\s+)/g, '\\$1')} --quiet --slave -e 'install.packages("${name}")'`);
		}
	}
	return false;
}

function startRLanguageServer(): Thenable<string> {
	return new Promise<string>((resolve, reject) => {
		const graalVMHome = vscode.workspace.getConfiguration('graalvm').get('home') as string;
		if (graalVMHome) {
			const executable: string = path.join(graalVMHome, 'bin', 'R');
			if (executable) {
				let serverWorkDir: string | undefined = vscode.workspace.getConfiguration('graalvm').get('languageServer.currentWorkDir') as string;
				if (!serverWorkDir) {
					serverWorkDir = vscode.workspace.rootPath;
				}
				const rServer = net.createServer(rSocket => {
					rServer.close();
					const server = net.createServer(socket => {
						server.close();
						socket.pipe(rSocket);
						const utf8Encode = new TextEncoder();
						rSocket.on('data', function(serverData) {
							let lines = serverData.toString().split(/\r?\n/);
							let msgIdx = -1;
							let clIdx = -1;
							lines.forEach((line, idx) => {
								if (line.length === 0) {
									msgIdx = idx + 1;
								}
								if (line.startsWith('Content-Length:')) {
									clIdx = idx;
								}
							});
							if (msgIdx >= 0 && clIdx >= 0) {
								let msg = utf8Encode.encode(lines.slice(msgIdx).join('\r\n'));
								lines[clIdx] = `Content-Length: ${msg.length}`;
								let headers = utf8Encode.encode(`${lines.slice(0, msgIdx).join('\r\n')}\r\n`);
								let buff = new Uint8Array(headers.length + msg.length);
								buff.set(headers);
								buff.set(msg, headers.length);
								socket.write(buff);
							} else {
								socket.write(serverData);
							}
						});
					});
					server.listen(0, '127.0.0.1', () => {
						resolve(`R@${(server.address() as net.AddressInfo).port}`);
					});
				});
				rServer.listen(0, '127.0.0.1', () => {
					const port = (rServer.address() as net.AddressInfo).port;
					return cp.spawn(executable, ['--quiet', '--slave', '-e', `languageserver::run(port=${port})`], { cwd: serverWorkDir });
				});
			} else {
				reject(new Error("Cannot find 'R' within your GraalVM installation."));
			}
		} else {
			reject(new Error("Cannot find your GraalVM installation."));
		}
	});
}
