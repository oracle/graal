/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import * as cp from 'child_process';
import * as utils from './utils';
import * as net from 'net';
import { pathToFileURL } from 'url';
import { LanguageClient, LanguageClientOptions, StreamInfo } from 'vscode-languageclient';
import { installGraalVM, installGraalVMComponent, selectInstalledGraalVM } from './graalVMInstall';
import { addNativeImageToPOM } from './graalVMNativeImage';

const OPEN_SETTINGS: string = 'Open Settings';
const INSTALL_GRAALVM: string = 'Install GraalVM';
const SELECT_GRAALVM: string = 'Select GraalVM';
const INSTALL_GRAALVM_NATIVE_IMAGE_COMPONENT: string = 'Install GraalVM native-image Component';
const POLYGLOT: string = "polyglot";
const LSPORT: number = 8123;
const delegateLanguageServers: Set<() => Thenable<String>> = new Set();

let client: LanguageClient | undefined;
let languageServerPID: number = 0;

export function activate(context: vscode.ExtensionContext) {
	context.subscriptions.push(vscode.commands.registerCommand('extension.graalvm.selectGraalVMHome', () => {
		selectInstalledGraalVM(context.globalStoragePath);
	}));
	context.subscriptions.push(vscode.commands.registerCommand('extension.graalvm.installGraalVM', () => {
		installGraalVM(context.globalStoragePath);
	}));
	context.subscriptions.push(vscode.commands.registerCommand('extension.graalvm.installGraalVMComponent', (componentId: string) => {
		installGraalVMComponent(componentId);
	}));
	context.subscriptions.push(vscode.commands.registerCommand('extension.graalvm.addNativeImageToPOM', () => {
		addNativeImageToPOM();
	}));
	const configurationProvider = new GraalVMConfigurationProvider();
	context.subscriptions.push(vscode.debug.registerDebugConfigurationProvider('graalvm', configurationProvider));
	context.subscriptions.push(vscode.debug.registerDebugConfigurationProvider('node', configurationProvider));
	const inProcessServer = vscode.workspace.getConfiguration('graalvm').get('languageServer.inProcessServer') as boolean;
	if (inProcessServer) {
		context.subscriptions.push(vscode.debug.registerDebugAdapterTrackerFactory('graalvm', new GraalVMDebugAdapterTracker()));
	}
	context.subscriptions.push(vscode.workspace.onDidChangeConfiguration(e => {
		if (e.affectsConfiguration('graalvm.home')) {
			config();
			stopLanguageServer().then(() => startLanguageServer(vscode.workspace.getConfiguration('graalvm').get('home') as string));
		} else if (e.affectsConfiguration('graalvm.languageServer.currentWorkDir') || e.affectsConfiguration('graalvm.languageServer.inProcessServer')) {
			stopLanguageServer().then(() => startLanguageServer(vscode.workspace.getConfiguration('graalvm').get('home') as string));
		}
	}));
	const graalVMHome = vscode.workspace.getConfiguration('graalvm').get('home') as string;
	if (!graalVMHome) {
		vscode.window.showInformationMessage('No path to GraalVM home specified.', SELECT_GRAALVM, INSTALL_GRAALVM, OPEN_SETTINGS).then(value => {
			switch (value) {
				case SELECT_GRAALVM:
					vscode.commands.executeCommand('extension.graalvm.selectGraalVMHome');
					break;
				case INSTALL_GRAALVM:
					vscode.commands.executeCommand('extension.graalvm.installGraalVM');
					break;
				case OPEN_SETTINGS:
					vscode.commands.executeCommand('workbench.action.openSettings');
					break;
			}
		});
	} else {
		config();
		startLanguageServer(graalVMHome);
	}
	const api = {
		registerLanguageServer(server: (() => Thenable<string>)): void {
			delegateLanguageServers.add(server);
			stopLanguageServer().then(() => startLanguageServer(vscode.workspace.getConfiguration('graalvm').get('home') as string));
		}
	};
	return api;
}

export function deactivate(): Thenable<void> {
	return stopLanguageServer();
}

function startLanguageServer(graalVMHome: string) {
	const inProcessServer = vscode.workspace.getConfiguration('graalvm').get('languageServer.inProcessServer') as boolean;
	if (!inProcessServer || delegateLanguageServers.size > 0) {
		const re = utils.findExecutable(POLYGLOT, graalVMHome);
		if (re) {
			let serverWorkDir: string | undefined = vscode.workspace.getConfiguration('graalvm').get('languageServer.currentWorkDir') as string;
			if (!serverWorkDir) {
				serverWorkDir = vscode.workspace.rootPath;
			}
			connectToLanguageServer(() => new Promise<StreamInfo>((resolve, reject) => {
				let delegateServers: string | undefined = vscode.workspace.getConfiguration('graalvm').get('languageServer.delegateServers') as string;
				const s = Array.from(delegateLanguageServers).map(server => server());
				Promise.all(s).then((servers) => {
					delegateServers = delegateServers ? delegateServers.concat(',', servers.join()) : servers.join();
					const lspOpt = delegateServers ? '--lsp.Delegates=' + delegateServers : '--lsp';
					const serverProcess = cp.spawn(re, ['--jvm', lspOpt, '--experimental-options', '--shell'], { cwd: serverWorkDir });
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

function connectToLanguageServer(connection: (() => Thenable<StreamInfo>)) {
	const clientOptions: LanguageClientOptions = {
		documentSelector: [
			{ scheme: 'file', language: 'javascript' },
			{ scheme: 'file', language: 'sl' },
			{ scheme: 'file', language: 'python' },
			{ scheme: 'file', language: 'r' },
			{ scheme: 'file', language: 'ruby' }
		]
	};
	client = new LanguageClient('GraalVM Language Client', connection, clientOptions);
	let prepareStatus = vscode.window.setStatusBarMessage("Graal Language Client: Connecting to GraalLS");
	client.onReady().then(() => {
		prepareStatus.dispose();
		vscode.window.setStatusBarMessage('GraalLS is ready.', 3000);
	}).catch(() => {
		prepareStatus.dispose();
		vscode.window.setStatusBarMessage('GraalLS failed to initialize.', 3000);
	});
	client.start();
}

function config() {
	const graalVMHome = vscode.workspace.getConfiguration('graalvm').get('home') as string;
	if (graalVMHome) {
		const javaConfig = vscode.workspace.getConfiguration('java');
		if (javaConfig) {
			const home = javaConfig.inspect('home');
			if (home) {
				javaConfig.update('home', graalVMHome, true);
			}
		}
		const mvnConfig = vscode.workspace.getConfiguration('maven');
		if (mvnConfig) {
			const terminalEnv = javaConfig.inspect('terminal.customEnv');
			if (terminalEnv) {
				mvnConfig.update('terminal.customEnv', [{"environmentVariable": "JAVA_HOME", "value": graalVMHome}], true);
			}
		}
		const executable: string = path.join(graalVMHome, 'bin', 'native-image');
		if (!fs.existsSync(executable)) {
			vscode.window.showInformationMessage('Native-image component is not installed in your GraalVM.', INSTALL_GRAALVM_NATIVE_IMAGE_COMPONENT).then(value => {
				switch (value) {
					case INSTALL_GRAALVM_NATIVE_IMAGE_COMPONENT:
						vscode.commands.executeCommand('extension.graalvm.installGraalVMComponent', 'native-image');
						break;
				}
			});
		}
	}
}

function stopLanguageServer(): Thenable<void> {
	if (client) {
		return client.stop().then(() => {
			client = undefined;
			if (languageServerPID > 0) {
				terminateLanguageServer();
			}
		});
	}
	if (languageServerPID > 0) {
		terminateLanguageServer();
	}
	return Promise.resolve();
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

function updatePath(path: string | undefined, graalVMBin: string): string {
	if (!path) {
		return graalVMBin;
	}
	let pathItems = path.split(':');
	let idx = pathItems.indexOf(graalVMBin);
	if (idx < 0) {
		pathItems.unshift(graalVMBin);
	}
	return pathItems.join(':');
}

class GraalVMDebugAdapterTracker implements vscode.DebugAdapterTrackerFactory {

	createDebugAdapterTracker(_session: vscode.DebugSession): vscode.ProviderResult<vscode.DebugAdapterTracker> {
		return {
			onDidSendMessage(message: any) {
				if (!client && message.type === 'event') {
					if (message.event === 'output' && message.body.category === 'telemetry' && message.body.output === 'childProcessID') {
						languageServerPID = message.body.data.pid;
					}
					if (message.event === 'initialized') {
						connectToLanguageServer(() => new Promise<StreamInfo>((resolve, reject) => {
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
						}));
					}
				}
			}
		};
	}
}

class GraalVMConfigurationProvider implements vscode.DebugConfigurationProvider {

	resolveDebugConfiguration(_folder: vscode.WorkspaceFolder | undefined, config: vscode.DebugConfiguration, _token?: vscode.CancellationToken): vscode.ProviderResult<vscode.DebugConfiguration> {
		return new Promise<vscode.DebugConfiguration>(resolve => {
			const inProcessServer = vscode.workspace.getConfiguration('graalvm').get('languageServer.inProcessServer') as boolean;
			const graalVMHome = vscode.workspace.getConfiguration('graalvm').get('home') as string;
			if (graalVMHome) {
				config.graalVMHome = graalVMHome;
				const graalVMBin = path.join(graalVMHome, 'bin');
				if (config.env) {
					config.env['PATH'] = updatePath(config.env['PATH'], graalVMBin);
				} else {
					config.env = { 'PATH': graalVMBin };
				}
			}
			if (inProcessServer) {
				stopLanguageServer().then(() => {
					let delegateServers: string | undefined = vscode.workspace.getConfiguration('graalvm').get('languageServer.delegateServers') as string;
					const s = Array.from(delegateLanguageServers).map(server => server());
					Promise.all(s).then((servers) => {
						delegateServers = delegateServers ? delegateServers.concat(',', servers.join()) : servers.join();
						const lspOpt = delegateServers ? '--lsp.Delegates=' + delegateServers : '--lsp';
						if (config.runtimeArgs) {
							let idx = config.runtimeArgs.indexOf('--jvm');
							if (idx < 0) {
								config.runtimeArgs.unshift('--jvm');
							}
							idx = config.runtimeArgs.indexOf('--lsp');
							if (idx < 0) {
								config.runtimeArgs.unshift(lspOpt);
							}
							idx = config.runtimeArgs.indexOf('--experimental-options');
							if (idx < 0) {
								config.runtimeArgs.unshift('--experimental-options');
							}
						} else {
							config.runtimeArgs = ['--jvm', lspOpt, '--experimental-options'];
						}
						resolve(config);
					});
				});
			} else if (config.program) {
				vscode.commands.executeCommand('dry_run', pathToFileURL(this.resolveVarRefs(config.program)));
				resolve(config);
			}
		});
	}

	resolveVarRefs(programPath: string): string {
		let re = /\${([\w:]+)}/ig;
		let idx = 0;
		let result = '';
		let match;
		while ((match = re.exec(programPath)) !== null) {
			result += programPath.slice(idx, match.index);
			idx = re.lastIndex;
			switch (match[1]) {
				case 'workspaceRoot':
				case 'workspaceFolder':
					if (vscode.workspace.rootPath) {
						result += vscode.workspace.rootPath;
					}
					break;
				case 'workspaceRootFolderName':
				case 'workspaceFolderBasename':
					if (vscode.workspace.rootPath) {
						result += path.basename(vscode.workspace.rootPath);
					}
					break;
				case 'file':
					if (vscode.window.activeTextEditor) {
						result += vscode.window.activeTextEditor.document.uri.fsPath;
					}
					break;
				case 'relativeFile':
					if (vscode.window.activeTextEditor) {
						let filename = vscode.window.activeTextEditor.document.uri.fsPath;
						result += vscode.workspace.rootPath ? path.normalize(path.relative(vscode.workspace.rootPath, filename)) : filename;
					}
					break;
				case 'relativeFileDirname':
					if (vscode.window.activeTextEditor) {
						let dirname = path.dirname(vscode.window.activeTextEditor.document.uri.fsPath);
						result += vscode.workspace.rootPath ?  path.normalize(path.relative(vscode.workspace.rootPath, dirname)) : dirname;
					}
					break;
				case 'fileBasename':
					if (vscode.window.activeTextEditor) {
						result += path.basename(vscode.window.activeTextEditor.document.uri.fsPath);
					}
					break;
				case 'fileBasenameNoExtension':
					if (vscode.window.activeTextEditor) {
						let basename = path.basename(vscode.window.activeTextEditor.document.uri.fsPath);
						result += basename.slice(0, basename.length - path.extname(basename).length);
					}
					break;
				case 'fileDirname':
					if (vscode.window.activeTextEditor) {
						result += path.dirname(vscode.window.activeTextEditor.document.uri.fsPath);
					}
					break;
				case 'fileExtname':
					if (vscode.window.activeTextEditor) {
						result += path.extname(vscode.window.activeTextEditor.document.uri.fsPath);
					}
					break;
				case 'cwd':
					result += process.cwd;
					break;
				case 'lineNumber':
					if (vscode.window.activeTextEditor) {
						result += vscode.window.activeTextEditor.selection.active.line;
					}
					break;
				case 'selectedText':
					if (vscode.window.activeTextEditor && vscode.window.activeTextEditor.selection) {
						result += vscode.window.activeTextEditor.document.getText(new vscode.Range(vscode.window.activeTextEditor.selection.start, vscode.window.activeTextEditor.selection.end));
					}
					break;
			}
		}
		result += programPath.slice(idx);
		return result;
	}
}
