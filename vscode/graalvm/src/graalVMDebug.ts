/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as net from 'net';
import * as path from 'path';
import { pathToFileURL } from 'url';
import { LSPORT, connectToLanguageServer, stopLanguageServer, lspArgs, hasLSClient, setLSPID } from './graalVMLanguageServer';
import { StreamInfo } from 'vscode-languageclient';

const POLYGLOT: string = "polyglot";

let rTermArgs: string[] | undefined;

export class GraalVMDebugAdapterTracker implements vscode.DebugAdapterTrackerFactory {

	createDebugAdapterTracker(session: vscode.DebugSession): vscode.ProviderResult<vscode.DebugAdapterTracker> {
		const inProcessServer = vscode.workspace.getConfiguration('graalvm').get('languageServer.inProcessServer') as boolean;
		return {
			onDidSendMessage(message: any) {
				if (message.type === 'event' && !hasLSClient() && session.configuration.request === 'launch' && inProcessServer) {
					if (message.event === 'output' && message.body.category === 'telemetry' && message.body.output === 'childProcessID') {
						setLSPID(message.body.data.pid);
					}
					if (message.event === 'initialized') {
						connectToLanguageServer(() => new Promise<StreamInfo>((resolve, reject) => {
							const socket = new net.Socket();
							socket.once('error', (e) => {
								reject(e);
							});
							socket.connect(session.configuration._lsPort ? session.configuration._lsPort : LSPORT, '127.0.0.1', () => {
								resolve({
									reader: socket,
									writer: socket
								});
							});
						}));
					}
				}
			},
			onWillStopSession() {
				if (rTermArgs) {
					const conf = vscode.workspace.getConfiguration('r');
					conf.update('rterm.option', rTermArgs, true);
				}
			}
		};
	}
}

export class GraalVMConfigurationProvider implements vscode.DebugConfigurationProvider {

	resolveDebugConfiguration(_folder: vscode.WorkspaceFolder | undefined, config: vscode.DebugConfiguration, _token?: vscode.CancellationToken): vscode.ProviderResult<vscode.DebugConfiguration> {
		return new Promise<vscode.DebugConfiguration>(resolve => {
			if (config.request === 'launch' && config.name === 'Launch R Term') {
				config.request = 'attach';
				const conf = vscode.workspace.getConfiguration('r');
				rTermArgs = conf.get('rterm.option') as string[];
				let args = config.runtimeArgs ? rTermArgs.slice().concat(config.runtimeArgs) : rTermArgs.slice();
				if (!args.find((arg: string) => arg.startsWith('--inspect'))) {
					args.push('--inspect.Suspend=false');
				}
				conf.update('rterm.option', args, true);
				setTimeout(() => {
					vscode.commands.executeCommand('r.createRTerm');
					setTimeout(() => {
						resolve(config);
					}, config.timeout | 3000);
				}, 1000);
			} else {
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
					if (config.request === 'launch' && inProcessServer) {
						stopLanguageServer().then(() => {
							lspArgs().then(args => {
								const lspArg = args.find(arg => arg.startsWith('--lsp='));
								if (lspArg) {
									config._lsPort = parseInt(lspArg.substring(6));
								}
								if (config.runtimeArgs) {
									config.runtimeArgs = config.runtimeArgs.filter((arg: string) => !arg.startsWith('--lsp'));
									config.runtimeArgs = config.runtimeArgs.concat(args);
									let idx = config.runtimeArgs.indexOf('--experimental-options');
									if (idx < 0) {
										config.runtimeArgs = config.runtimeArgs.concat('--experimental-options');
									}
									if (config.runtimeExecutable !== POLYGLOT) {
										let idx = config.runtimeArgs.indexOf('--polyglot');
										if (idx < 0) {
											config.runtimeArgs = config.runtimeArgs.concat('--polyglot');
										}
									}
								} else {
									args = args.concat('--experimental-options');
									if (config.runtimeExecutable !== POLYGLOT) {
										args = args.concat('--polyglot');
									}
									config.runtimeArgs = args;
								}
								resolve(config);
							});
						});
					} else {
						resolve(config);
					}
				} else {
					resolve(config);
				}
			}
		});
	}

	resolveDebugConfigurationWithSubstitutedVariables?(_folder: vscode.WorkspaceFolder | undefined, config: vscode.DebugConfiguration, _token?: vscode.CancellationToken): vscode.ProviderResult<vscode.DebugConfiguration> {
        if (config.program) {
            if (!vscode.workspace.getConfiguration('graalvm').get('languageServer.inProcessServer') as boolean) {
                vscode.commands.getCommands().then((commands: string[]) => {
                    if (commands.includes('dry_run')) {
                        vscode.commands.executeCommand('dry_run', pathToFileURL(config.program));
                    }
                });
            }
        }
        return config;
	}
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
