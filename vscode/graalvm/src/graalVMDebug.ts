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
import { LSPORT, connectToLanguageServer, stopLanguageServer, lspArg, hasLSClient, setLSPID } from './graalVMLanguageServer';
import { StreamInfo } from 'vscode-languageclient';

export class GraalVMDebugAdapterTracker implements vscode.DebugAdapterTrackerFactory {

	createDebugAdapterTracker(_session: vscode.DebugSession): vscode.ProviderResult<vscode.DebugAdapterTracker> {
		const inProcessServer = vscode.workspace.getConfiguration('graalvm').get('languageServer.inProcessServer') as boolean;
		return {
			onDidSendMessage(message: any) {
				if (message.type === 'event' && !hasLSClient() && inProcessServer) {
					if (message.event === 'output' && message.body.category === 'telemetry' && message.body.output === 'childProcessID') {
						setLSPID(message.body.data.pid);
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

export class GraalVMConfigurationProvider implements vscode.DebugConfigurationProvider {

	resolveDebugConfiguration(_folder: vscode.WorkspaceFolder | undefined, config: vscode.DebugConfiguration, _token?: vscode.CancellationToken): vscode.ProviderResult<vscode.DebugConfiguration> {
		return new Promise<vscode.DebugConfiguration>(resolve => {
			if (config.request === 'launch' && config.name === 'Launch R Term') {
				vscode.commands.executeCommand('r.createRTerm');
				config.request = 'attach';
			}
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
				if (inProcessServer) {
					stopLanguageServer().then(() => {
                        lspArg().then((arg: string) => {
							if (config.runtimeArgs) {
								let idx = config.runtimeArgs.indexOf('--lsp');
								if (idx < 0) {
									config.runtimeArgs.unshift(arg);
								}
								idx = config.runtimeArgs.indexOf('--experimental-options');
								if (idx < 0) {
									config.runtimeArgs.unshift('--experimental-options');
								}
							} else {
								config.runtimeArgs = [arg, '--experimental-options'];
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
