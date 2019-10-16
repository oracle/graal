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
import { Socket } from 'net';
import { LanguageClient, LanguageClientOptions, StreamInfo } from 'vscode-languageclient';
import { installGraalVM, installGraalVMComponent, selectInstalledGraalVM } from './graalVMInstall';
import { addNativeImageToPOM } from './graalVMNativeImage';

const OPEN_SETTINGS: string = 'Open Settings';
const INSTALL_GRAALVM: string = 'Install GraalVM';
const SELECT_GRAALVM: string = 'Select GraalVM';
const INSTALL_GRAALVM_NATIVE_IMAGE_COMPONENT: string = 'Install GraalVM native-image Component';
const POLYGLOT: string = "polyglot";
const LSPORT: number = 8123;

let client: LanguageClient;
let serverProcess: cp.ChildProcess;

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
	context.subscriptions.push(vscode.workspace.onDidChangeConfiguration(e => {
		if (e.affectsConfiguration('graalvm.home')) {
			config();
			startLanguageServer(vscode.workspace.getConfiguration('graalvm').get('home') as string);
		} else if (e.affectsConfiguration('graalvm.languageServer.currentWorkDir')) {
			stopLanguageServer().then(() => startLanguageServer(vscode.workspace.getConfiguration('graalvm').get('home') as string));
		}
	}));
        context.subscriptions.push(vscode.workspace.onDidChangeConfiguration(e => {
                if (e.affectsConfiguration('graalvm.home')) {
                        startLanguageServer(vscode.workspace.getConfiguration('graalvm').get('home') as string);
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
}

export function deactivate(): Thenable<void> {
	return stopLanguageServer();
}

function startLanguageServer(graalVMHome: string) {
	const re = utils.findExecutable(POLYGLOT, graalVMHome);
	if (re) {
		let serverWorkDir: string | undefined = vscode.workspace.getConfiguration('graalvm').get('languageServer.currentWorkDir') as string;
		if (!serverWorkDir) {
			serverWorkDir = vscode.workspace.rootPath;
		}
		serverProcess = cp.spawn(re, ['--jvm', '--lsp', '--experimental-options', '--shell'], { cwd: serverWorkDir });
		if (!serverProcess || !serverProcess.pid) {
			vscode.window.showErrorMessage(`Launching server using command ${re} failed.`);
		} else {
			serverProcess.stdout.once('data', () => {
				const connection = () => new Promise<StreamInfo>((resolve, reject) => {
					const socket = new Socket();
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
				let clientOptions: LanguageClientOptions = {
					documentSelector: [
						{ scheme: 'file', language: 'javascript' },
						{ scheme: 'file', language: 'sl' },
						{ scheme: 'file', language: 'python' },
						{ scheme: 'file', language: 'r' },
						{ scheme: 'file', language: 'ruby' }
					]
				};
				client = new LanguageClient('GraalVM Language Client', connection, clientOptions);
				serverProcess.stderr.on('data', data => client.outputChannel.append(data.toString('utf8')));
				let prepareStatus = vscode.window.setStatusBarMessage("Graal Language Client: Connecting to GraalLS");
				client.onReady().then(() => {
					prepareStatus.dispose();
					vscode.window.setStatusBarMessage('GraalLS is ready.', 3000);
				}).catch(() => {
					prepareStatus.dispose();
					vscode.window.setStatusBarMessage('GraalLS failed to initialize.', 3000);
				});
				client.start();
			});
		}
	} else {
		vscode.window.showErrorMessage('Cannot find runtime ' + POLYGLOT + ' within your GraalVM installation.');
	}
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
			if (serverProcess) {
				serverProcess.kill('SIGINT');
			}
		});
	}
	if (serverProcess) {
		serverProcess.kill('SIGINT');
	}
	return Promise.resolve();
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

class GraalVMConfigurationProvider implements vscode.DebugConfigurationProvider {

	resolveDebugConfiguration(_folder: vscode.WorkspaceFolder | undefined, config: vscode.DebugConfiguration, _token?: vscode.CancellationToken): vscode.ProviderResult<vscode.DebugConfiguration> {
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
		return config;
	}
}
