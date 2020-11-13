/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';

export async function micronautProjectExists(): Promise<boolean> {
	return (await vscode.workspace.findFiles('**/micronaut-cli.yml', '**/node_modules/**')).length > 0;
}

export function getMicronautHome(): string {
	let micronautHome: string = vscode.workspace.getConfiguration('micronaut').get('home') as string;
	if (micronautHome) {
		return micronautHome;
	}
	micronautHome = process.env['MICRONAUT_HOME'] as string;
	return micronautHome;
}

export function getJavaHome(): string {
	let javaHome: string = vscode.workspace.getConfiguration('graalvm').get('home') as string;
	if (javaHome) {
		return javaHome;
	}
	javaHome = process.env['GRAALVM_HOME'] as string;
	if (javaHome) {
		return javaHome;
	}
	javaHome = vscode.workspace.getConfiguration('java').get('home') as string;
	if (javaHome) {
		return javaHome;
	}
	javaHome = process.env['JAVA_HOME'] as string;
	return javaHome;
}

export function findExecutable(program: string, home: string): string | undefined {
    if (home) {
        let executablePath = path.join(home, 'bin', program);
        if (process.platform === 'win32') {
            if (fs.existsSync(executablePath + '.cmd')) {
                return executablePath + '.cmd';
            }
            if (fs.existsSync(executablePath + '.exe')) {
                return executablePath + '.exe';
            }
        } else if (fs.existsSync(executablePath)) {
            return executablePath;
        }
    }
    return undefined;
}

class InputFlowAction {
	static back = new InputFlowAction();
	static cancel = new InputFlowAction();
	static resume = new InputFlowAction();
}

type InputStep = (input: MultiStepInput) => Thenable<InputStep | void>;

interface QuickPickParameters<T extends vscode.QuickPickItem> {
	title: string;
	step: number;
	totalSteps: number;
	items: T[];
	activeItems?: T | T[];
	placeholder: string;
	canSelectMany?: boolean;
	buttons?: vscode.QuickInputButton[];
	shouldResume: () => Thenable<boolean>;
}

interface InputBoxParameters {
	title: string;
	step: number;
	totalSteps: number;
	value: string;
	prompt: string;
	validate: (value: string) => Promise<string | undefined>;
	buttons?: vscode.QuickInputButton[];
	shouldResume: () => Thenable<boolean>;
}

export class MultiStepInput {

	static async run(start: InputStep) {
		const input = new MultiStepInput();
		return input.stepThrough(start);
	}

	private current?: vscode.QuickInput;
	private steps: InputStep[] = [];

	private async stepThrough(start: InputStep) {
		let step: InputStep | void = start;
		while (step) {
			this.steps.push(step);
			if (this.current) {
				this.current.enabled = false;
				this.current.busy = true;
			}
			try {
				step = await step(this);
			} catch (err) {
				if (err === InputFlowAction.back) {
					this.steps.pop();
					step = this.steps.pop();
				} else if (err === InputFlowAction.resume) {
					step = this.steps.pop();
				} else if (err === InputFlowAction.cancel) {
					step = undefined;
				} else {
					throw err;
				}
			}
		}
		if (this.current) {
			this.current.dispose();
		}
	}

	async showQuickPick<T extends vscode.QuickPickItem, P extends QuickPickParameters<T>>({ title, step, totalSteps, items, activeItems, placeholder, canSelectMany, buttons, shouldResume }: P) {
		const disposables: vscode.Disposable[] = [];
		try {
			return await new Promise<T | readonly T[] | (P extends { buttons: (infer I)[] } ? I : never)>((resolve, reject) => {
				const input = vscode.window.createQuickPick<T>();
				input.title = title;
				input.step = step;
				input.totalSteps = totalSteps;
				input.placeholder = placeholder;
				input.items = items;
				if (canSelectMany) {
					input.canSelectMany = canSelectMany;
				}
				if (activeItems) {
					input.activeItems = Array.isArray(activeItems) ? activeItems : [activeItems];
				}
				input.buttons = [
					...(this.steps.length > 1 ? [vscode.QuickInputButtons.Back] : []),
					...(buttons || [])
				];
				input.ignoreFocusOut = true;
				disposables.push(
					input.onDidTriggerButton(item => {
						if (item === vscode.QuickInputButtons.Back) {
							reject(InputFlowAction.back);
						} else {
							resolve(<any>item);
						}
					}),
					input.onDidAccept(() => {
						resolve(canSelectMany ? input.selectedItems : input.selectedItems[0]);
					}),
					input.onDidHide(() => {
						(async () => {
							reject(shouldResume && await shouldResume() ? InputFlowAction.resume : InputFlowAction.cancel);
						})()
							.catch(reject);
					})
				);
				if (this.current) {
					this.current.dispose();
				}
				this.current = input;
				this.current.show();
			});
		} finally {
			disposables.forEach(d => d.dispose());
		}
	}

	async showInputBox<P extends InputBoxParameters>({ title, step, totalSteps, value, prompt, validate, buttons, shouldResume }: P) {
		const disposables: vscode.Disposable[] = [];
		try {
			return await new Promise<string | (P extends { buttons: (infer I)[] } ? I : never)>((resolve, reject) => {
				const input = vscode.window.createInputBox();
				input.title = title;
				input.step = step;
				input.totalSteps = totalSteps;
				input.value = value || '';
				input.prompt = prompt;
				input.buttons = [
					...(this.steps.length > 1 ? [vscode.QuickInputButtons.Back] : []),
					...(buttons || [])
				];
				input.ignoreFocusOut = true;
				let validating = validate('');
				disposables.push(
					input.onDidTriggerButton(item => {
						if (item === vscode.QuickInputButtons.Back) {
							reject(InputFlowAction.back);
						} else {
							resolve(<any>item);
						}
					}),
					input.onDidAccept(async () => {
						const value = input.value;
						input.enabled = false;
						input.busy = true;
						if (!(await validate(value))) {
							resolve(value);
						}
						input.enabled = true;
						input.busy = false;
					}),
					input.onDidChangeValue(async text => {
						const current = validate(text);
						validating = current;
						const validationMessage = await current;
						if (current === validating) {
							input.validationMessage = validationMessage;
						}
					}),
					input.onDidHide(() => {
						(async () => {
							reject(shouldResume && await shouldResume() ? InputFlowAction.resume : InputFlowAction.cancel);
						})()
							.catch(reject);
					})
				);
				if (this.current) {
					this.current.dispose();
				}
				this.current = input;
				this.current.show();
			});
		} finally {
			disposables.forEach(d => d.dispose());
		}
	}
}
