/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as cp from 'child_process';
import * as fs from 'fs';
import * as net from 'net';
import * as path from 'path';
import { pathToFileURL } from 'url';
import * as utils from './utils';
import { LSPORT, connectToLanguageServer, stopLanguageServer, lspArgs, hasLSClient, setLSPID } from './graalVMLanguageServer';
import { StreamInfo } from 'vscode-languageclient';
import { ILaunchRequestArguments, IGraalVMLaunchInfo } from './graalVMDebugInterfaces';
import { getGVMConfig, getConf, getGVMHome } from './graalVMConfiguration';

const DEBUG_TERMINAL_NAME = 'GraalVM Debug Console';
const NODE: string = "node";
const POLYGLOT: string = "polyglot";

let rTermArgs: string[] | undefined;

export class GraalVMDebugAdapterTracker implements vscode.DebugAdapterTrackerFactory {

	createDebugAdapterTracker(session: vscode.DebugSession): vscode.ProviderResult<vscode.DebugAdapterTracker> {
		const inProcessServer = getGVMConfig().get('languageServer.inProcessServer') as boolean;
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
					getConf('r').update('rterm.option', rTermArgs, true);
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
				const conf = getConf('r');
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
				const gvmc = getGVMConfig()
				const inProcessServer = gvmc.get('languageServer.inProcessServer') as boolean;
				const graalVMHome = getGVMHome(gvmc);
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
        return getLaunchInfo(config, getGVMHome()).then(launchInfo => {
			config.graalVMLaunchInfo = launchInfo;
			if (config.program) {
				if (!getGVMConfig().get('languageServer.inProcessServer') as boolean) {
					vscode.commands.getCommands().then((commands: string[]) => {
						if (commands.includes('dry_run')) {
							vscode.commands.executeCommand('dry_run', pathToFileURL(config.program));
						}
					});
				}
			}
			return config;
		});
	}
}

export class GraalVMDebugAdapterDescriptorFactory implements vscode.DebugAdapterDescriptorFactory {

	createDebugAdapterDescriptor(session: vscode.DebugSession, executable: vscode.DebugAdapterExecutable | undefined): vscode.ProviderResult<vscode.DebugAdapterDescriptor> {
		if (!session.configuration.protocol || session.configuration.protocol === 'debugAdapter') {
			if (session.configuration.request === 'attach') {
				return new vscode.DebugAdapterServer(session.configuration.port, session.configuration.address);
			} else if (session.configuration.request === 'launch') {
				if (session.configuration.console === 'integratedTerminal') {
					let terminal: vscode.Terminal | undefined = vscode.window.terminals.find(term => term.name === DEBUG_TERMINAL_NAME);
					if (terminal) {
						terminal.sendText(`cd ${session.configuration.graalVMLaunchInfo.cwd}`);
					} else {
						terminal = vscode.window.createTerminal({name: DEBUG_TERMINAL_NAME,	cwd: session.configuration.graalVMLaunchInfo.cwd});
					}
					terminal.sendText(`${session.configuration.graalVMLaunchInfo.exec.replace(/(\s+)/g, '\\$1')} ${session.configuration.graalVMLaunchInfo.args.join(' ')}`);
					terminal.show();
					return new Promise<vscode.DebugAdapterServer>(resolve => {
						setTimeout(() => {
							resolve(new vscode.DebugAdapterServer(session.configuration.graalVMLaunchInfo.port));
						}, session.configuration.timeout | 3000);
					});
				} else if (!session.configuration.console || session.configuration.console === 'internalConsole') {
					const spawnOpts: cp.SpawnOptions = {cwd: session.configuration.graalVMLaunchInfo.cwd, env: process.env, detached: true};
					const childProcess = cp.spawn(session.configuration.graalVMLaunchInfo.exec, session.configuration.graalVMLaunchInfo.args, spawnOpts);
					return new Promise<vscode.DebugAdapterServer>((resolve, reject) => {
						childProcess.on('error', (error) => {
							reject(new Error(`Cannot launch debug target (${error.toString()}).`));
						});
						const captureStdOutput: boolean = session.configuration.outputCapture === 'std';
						const noDebugMode = session.configuration.noDebug;
						if (childProcess.stderr) {
							childProcess.stderr.on('data', (data: string) => {
								if (noDebugMode || captureStdOutput) {
									let msg = data.toString();
									vscode.debug.activeDebugConsole.appendLine(msg);
								}
							});
						}
						let lastEarlyNodeMsgSeen: boolean = false;
						if (childProcess.stdout) {
							childProcess.stdout.on('data', (data: string) => {
								let msg = data.toString();
								if (!lastEarlyNodeMsgSeen && !noDebugMode) {
									let regExp = /^\s*\[Graal DAP\] Starting server and listening on \S*\s*$/m;
									if (msg.match(regExp)) {
										lastEarlyNodeMsgSeen = true;
										resolve(new vscode.DebugAdapterServer(session.configuration.graalVMLaunchInfo.port));
									}
								}
								if (noDebugMode || captureStdOutput) {
									vscode.debug.activeDebugConsole.appendLine(msg);
								}
							});
						}
					});
				} else {
					throw new Error(`Unknown console type '${session.configuration.console}'.`);
				}
			}
		}
		return executable;
	}

	dispose() {
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

async function getLaunchInfo(config: vscode.DebugConfiguration | ILaunchRequestArguments, graalVMHome: string | undefined): Promise<IGraalVMLaunchInfo> {
	const port = config.port || utils.random(3000, 50000);
	let runtimeExecutable = config.runtimeExecutable;
	if (runtimeExecutable) {
		if (path.isAbsolute(runtimeExecutable)) {
			if (!fs.existsSync(runtimeExecutable)) {
				return Promise.reject(new Error(`Attribute 'runtimeExecutable' does not exist ('${runtimeExecutable}').`));
			}
		} else {
			const re = utils.findExecutable(runtimeExecutable, graalVMHome);
			if (!re) {
				return Promise.reject(new Error(`Cannot find runtime '${runtimeExecutable}' within your GraalVM installation. Make sure to have GraalVM '${runtimeExecutable}' installed.`));
			}
			runtimeExecutable = re;
		}
	} else {
		const re = utils.findExecutable(NODE, graalVMHome);
		if (!re) {
			return Promise.reject(new Error(`Cannot find runtime '${NODE}' within your GraalVM installation. Make sure to have GraalVM '${NODE}' installed.`));
		}
		runtimeExecutable = re;
	}
	let programPath = config.program;
	if (programPath) {
		if (!path.isAbsolute(programPath)) {
			return Promise.reject(new Error(`Attribute 'program' is not absolute ('${programPath}'); consider adding '\${workspaceFolder}/' as a prefix to make it absolute.`));
		}
		if (!fs.existsSync(programPath)) {
			if (fs.existsSync(programPath + '.js')) {
				programPath += '.js';
			} else {
				return Promise.reject(new Error(`Attribute 'program' does not exist ('${programPath}').`));
			}
		}
		programPath = path.normalize(programPath);
	}
	let program: string | undefined;
	let cwd = config.cwd;
	if (cwd) {
		if (!path.isAbsolute(cwd)) {
			return Promise.reject(new Error(`Attribute 'cwd' is not absolute ('${cwd}'); consider adding '\${workspaceFolder}/' as a prefix to make it absolute.`));
		}
		if (!fs.existsSync(cwd)) {
			return Promise.reject(new Error(`Attribute 'cwd' does not exist ('${cwd}').`));
		}
		if (programPath) {
			program = await utils.isSymlinked(cwd) ? programPath : path.relative(cwd, programPath);
		}
	} else if (programPath) {
		cwd = path.dirname(programPath);
		program = await utils.isSymlinked(cwd) ? programPath : path.basename(programPath);
	}
	const runtimeArgs = config.runtimeArgs || [];
	const programArgs = config.args || [];
	let launchArgs = [];
	if (!config.noDebug) {
		if (!config.protocol || config.protocol === 'debugAdapter') {
			launchArgs.push(`--dap=${port}`);
		} else if (config.protocol === 'chromeDevTools') {
			if (path.basename(runtimeExecutable) === NODE) {
				launchArgs.push(`--inspect-brk=${port}`);
			} else {
				launchArgs.push(`--inspect=${port}`);
			}
		}
	}
	return Promise.resolve({exec: runtimeExecutable, args: runtimeArgs.concat(launchArgs, program ? [program] : [], programArgs), cwd: cwd, port: port});
}
