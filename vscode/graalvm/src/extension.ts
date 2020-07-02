/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import { toggleCodeCoverage, activeTextEditorChaged } from './graalVMCoverage';
import { GraalVMConfigurationProvider, GraalVMDebugAdapterTracker } from './graalVMDebug';
import { installGraalVM, installGraalVMComponent, selectInstalledGraalVM } from './graalVMInstall';
import { startLanguageServer, stopLanguageServer } from './graalVMLanguageServer';
import { installRPackage, rConfig, R_LANGUAGE_SERVER_PACKAGE_NAME } from './graalVMR';
import { installRubyGem, rubyConfig, RUBY_LANGUAGE_SERVER_GEM_NAME } from './graalVMRuby';
import { addNativeImageToPOM } from './graalVMNativeImage';
import { pythonConfig } from './graalVMPython';

const OPEN_SETTINGS: string = 'Open Settings';
const INSTALL_GRAALVM: string = 'Install GraalVM';
const SELECT_GRAALVM: string = 'Select GraalVM';
const INSTALL: string = 'Install ';
const OPTIONAL_COMPONENTS: string = 'Optional GraalVM Components';
const NATIVE_IMAGE_COMPONENT: string = 'GraalVM Native Image Component';
const PYTHON_COMPONENT: string = 'GraalVM Python Component';
const R_COMPONENT: string = 'GraalVM R Component';
const RUBY_COMPONENT: string = 'GraalVM Ruby Component';

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
	context.subscriptions.push(vscode.commands.registerCommand('extension.graalvm.toggleCodeCoverage', () => {
		toggleCodeCoverage(context);
	}));
	context.subscriptions.push(vscode.commands.registerCommand('extension.graalvm.installRLanguageServer', () => {
		installRPackage(R_LANGUAGE_SERVER_PACKAGE_NAME);
	}));
	context.subscriptions.push(vscode.commands.registerCommand('extension.graalvm.installRubyLanguageServer', () => {
		installRubyGem(RUBY_LANGUAGE_SERVER_GEM_NAME);
	}));
	context.subscriptions.push(vscode.window.onDidChangeActiveTextEditor(e => {
		if (e) {
			activeTextEditorChaged(e);
		}
	}));
	const configurationProvider = new GraalVMConfigurationProvider();
	context.subscriptions.push(vscode.debug.registerDebugConfigurationProvider('graalvm', configurationProvider));
	context.subscriptions.push(vscode.debug.registerDebugConfigurationProvider('node', configurationProvider));
	context.subscriptions.push(vscode.debug.registerDebugAdapterTrackerFactory('graalvm', new GraalVMDebugAdapterTracker()));
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
	vscode.window.setStatusBarMessage('GraalVM extension activated', 3000);
}

export function deactivate(): Thenable<void> {
	return stopLanguageServer();
}


function config() {
	const graalVMHome = vscode.workspace.getConfiguration('graalvm').get('home') as string;
	if (graalVMHome) {
		const termConfig = vscode.workspace.getConfiguration('terminal.integrated');
		let section: string = '';
		if (process.platform === 'linux') {
			section = 'env.linux';
		} else if (process.platform === 'darwin') {
			section = 'env.mac';
		}
		let env: any = termConfig.get(section);
		env.GRAALVM_HOME = graalVMHome;
		let envPath = process.env.PATH;
		if (envPath) {
			if (!envPath.includes(`${graalVMHome}/bin`)) {
				env.PATH = `${graalVMHome}/bin:${envPath}`;
			}
		} else {
			env.PATH = `${graalVMHome}/bin`;
		}
		termConfig.update(section, env, true);
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
		const missingComponents: string[] = [];
		let componentName;
		const executable: string = path.join(graalVMHome, 'bin', 'native-image');
		if (!fs.existsSync(executable)) {
			missingComponents.push('native-image');
			componentName = NATIVE_IMAGE_COMPONENT;
		}
		if (!pythonConfig(graalVMHome)) {
			missingComponents.push('python');
			componentName = PYTHON_COMPONENT;
		}
		if (!rConfig(graalVMHome)) {
			missingComponents.push('R');
			componentName = R_COMPONENT;
		}
		if (!rubyConfig(graalVMHome)) {
			missingComponents.push('ruby');
			componentName = RUBY_COMPONENT;
		}
		if (missingComponents.length > 0) {
			if (missingComponents.length > 1) {
				const itemText = INSTALL + OPTIONAL_COMPONENTS;
				vscode.window.showInformationMessage('Optional GraalVM components are not installed in your GraalVM.', itemText).then(value => {
					switch (value) {
						case itemText:
							vscode.commands.executeCommand('extension.graalvm.installGraalVMComponent');
							const watcher:fs.FSWatcher = fs.watch(path.join(graalVMHome, 'bin'), () => {
								pythonConfig(graalVMHome);
								rConfig(graalVMHome);
								rubyConfig(graalVMHome);
								watcher.close();
							});
							break;
					}
				});
			} else {
				const itemText = INSTALL + componentName;
				vscode.window.showInformationMessage(componentName + ' is not installed in your GraalVM.', itemText).then(value => {
					switch (value) {
						case itemText:
							vscode.commands.executeCommand('extension.graalvm.installGraalVMComponent', missingComponents[0]);
							const watcher:fs.FSWatcher = fs.watch(path.join(graalVMHome + 'bin'), () => {
								pythonConfig(graalVMHome);
								rConfig(graalVMHome);
								rubyConfig(graalVMHome);
								watcher.close();
							});
							break;
					}
				});
			}
		}
	}
}
