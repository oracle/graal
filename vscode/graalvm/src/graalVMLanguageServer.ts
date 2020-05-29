/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as cp from 'child_process';
import * as utils from './utils';
import * as net from 'net';
import { LanguageClient, LanguageClientOptions, StreamInfo } from 'vscode-languageclient';

export const LSPORT: number = 8123;
const POLYGLOT: string = 'polyglot';
const delegateLanguageServers: Set<() => Thenable<String>> = new Set();

let languageClient: Promise<LanguageClient> | undefined;
let languageServerPID: number = 0;

export function registerLanguageServer(server: (() => Thenable<string>)): void {
    delegateLanguageServers.add(server);
    stopLanguageServer().then(() => startLanguageServer(vscode.workspace.getConfiguration('graalvm').get('home') as string));
}

export function startLanguageServer(graalVMHome: string) {
	const inProcessServer = vscode.workspace.getConfiguration('graalvm').get('languageServer.inProcessServer') as boolean;
	if (!inProcessServer || delegateLanguageServers.size > 0) {
		const re = utils.findExecutable(POLYGLOT, graalVMHome);
		if (re) {
			let serverWorkDir: string | undefined = vscode.workspace.getConfiguration('graalvm').get('languageServer.currentWorkDir') as string;
			if (!serverWorkDir) {
				serverWorkDir = vscode.workspace.rootPath;
			}
			connectToLanguageServer(() => new Promise<StreamInfo>((resolve, reject) => {
                lspArg().then((arg) => {
					const serverProcess = cp.spawn(re, [arg, '--experimental-options', '--shell'], { cwd: serverWorkDir });
					if (!serverProcess || !serverProcess.pid) {
						reject(`Launching server using command ${re} failed.`);
					} else {
						languageServerPID = serverProcess.pid;
						serverProcess.stderr.once('data', data => {
							reject(data);
						});
						serverProcess.stdout.once('data', () => {
							const socket = new net.Socket();
							socket.once('error', (e) => {
								reject(e);
							});
							socket.connect(LSPORT, '127.0.0.1', () => {
								resolve({
									reader: socket,
									writer: socket
								});
							});
						});
					}
				});
			}));
		} else {
			vscode.window.showErrorMessage('Cannot find runtime ' + POLYGLOT + ' within your GraalVM installation.');
		}
	}
}

export function connectToLanguageServer(connection: (() => Thenable<StreamInfo>)) {
	const clientOptions: LanguageClientOptions = {
		documentSelector: [
			{ scheme: 'file', language: 'javascript' },
			{ scheme: 'file', language: 'sl' },
			{ scheme: 'file', language: 'python' },
			{ scheme: 'file', language: 'r' },
			{ scheme: 'file', language: 'ruby' }
		]
	};

	languageClient = new Promise<LanguageClient>((resolve) => {
		let client = new LanguageClient('GraalVM Language Client', connection, clientOptions);
		let prepareStatus = vscode.window.setStatusBarMessage("Graal Language Client: Connecting to GraalLS");
		client.onReady().then(() => {
			prepareStatus.dispose();
			vscode.window.setStatusBarMessage('GraalLS is ready.', 3000);
			resolve(client);
		}).catch(() => {
			prepareStatus.dispose();
			vscode.window.setStatusBarMessage('GraalLS failed to initialize.', 3000);
			resolve(client);
		});
		client.start();
	});
}

export function stopLanguageServer(): Thenable<void> {
	if (languageClient) {
		return languageClient.then((client) => client.stop().then(() => {
			languageClient = undefined;
			if (languageServerPID > 0) {
				terminateLanguageServer();
			}
		}));
	}
	if (languageServerPID > 0) {
		terminateLanguageServer();
	}
	return Promise.resolve();
}

export function lspArg(): Thenable<string> {
    let delegateServers: string | undefined = vscode.workspace.getConfiguration('graalvm').get('languageServer.delegateServers') as string;
    const s = Array.from(delegateLanguageServers).map(server => server());
    return Promise.all(s).then((servers) => {
        delegateServers = delegateServers ? delegateServers.concat(',', servers.join()) : servers.join();
        return delegateServers ? '--lsp.Delegates=' + delegateServers : '--lsp';
    });
}

export function setLSPID(pid: number) {
    languageServerPID = pid;
}

export function hasLSClient(): boolean {
    return languageClient !== undefined;
}

function terminateLanguageServer() {
	const groupPID = -languageServerPID;
	try {
		process.kill(groupPID, 'SIGKILL');
	} catch (e) {
		if (e.message === 'kill ESRCH') {
			try {
				process.kill(languageServerPID, 'SIGKILL');
			} catch (e) {}
		}
	}
	languageServerPID = 0;
}

