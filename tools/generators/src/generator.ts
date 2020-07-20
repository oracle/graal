/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'fs';
import { join } from 'path';
import * as minimist from 'minimist';
import * as seedrandom from 'seedrandom';
import {
    ArrayTypeNode,
    createSourceFile,
    DeclarationStatement,
    EnumDeclaration,
    EnumMember,
    ExpressionWithTypeArguments,
    FunctionDeclaration,
    HasJSDoc,
    HeritageClause,
    IndexSignatureDeclaration,
    InterfaceDeclaration,
    IntersectionTypeNode,
    JSDoc,
    LiteralTypeNode,
    MethodSignature,
    Modifier,
    ModuleBlock,
    ModuleDeclaration,
    NewExpression,
    Node,
    ParameterDeclaration,
    ParenthesizedTypeNode,
    PropertySignature,
    ScriptKind,
    ScriptTarget,
    SourceFile,
    Statement,
    SyntaxKind,
    TypeAliasDeclaration,
    TypeElement,
    TypeLiteralNode,
    TypeNode,
    TypeReferenceNode,
    UnionTypeNode,
    VariableStatement,
    VariableDeclaration,
    TypeParameterDeclaration,
    PrefixUnaryExpression,
} from 'typescript';

const args = minimist(process.argv.slice(2));
if (!args.source) {
    console.error(new Error("Need to provide source file with --source"));
    process.exit(1);
}
if (!args.target) {
    console.error(new Error("Need to provide target directory with --target"));
    process.exit(1);
}
const pkg: string | undefined = args.pkg;
const license: string | undefined = args.license ? readFileSync(args.license).toString() : undefined;

const indent: string = '    ';
const typeAliases = new Map<string, TypeAliasDeclaration>();
const modules = new Map<string, ModuleDeclaration>();
const interfaces = new Map<string, InterfaceDeclaration>();
const enums = new Map<string, EnumDeclaration>();
const enumTypes = new Map<string, Node>();
const typeLiteralNames = new Map<TypeLiteralNode, string>();
const intersected = new Map<string, (InterfaceDeclaration | TypeLiteralNode)[]>();
const unionTypes = new Set<string>();

const numberTypes = new Map<string, string>();
numberTypes.set('Color', 'double'); //TODO: make configurable

const ANY = 'any';

parseSource(args.source).then((ast:SourceFile) => {
    traverse([ast.getChildAt(0)], (node: Node) => {
        switch(node.kind) {
            case SyntaxKind.TypeAliasDeclaration:
                const taExported = (node as TypeAliasDeclaration).modifiers && (node as TypeAliasDeclaration).modifiers.find((mod: Modifier) => mod.kind === SyntaxKind.ExportKeyword);
                if (taExported) {
                    typeAliases.set((node as TypeAliasDeclaration).name.getText(), node as TypeAliasDeclaration);
                }
                break;
            case SyntaxKind.ModuleDeclaration:
                const mdExported = (node as ModuleDeclaration).modifiers && (node as ModuleDeclaration).modifiers.find((mod: Modifier) => mod.kind === SyntaxKind.ExportKeyword);
                if (mdExported && !modules.has((node as ModuleDeclaration).name.getText())) {
                    modules.set((node as ModuleDeclaration).name.getText(), <ModuleDeclaration>node);
                }
                break;
            case SyntaxKind.InterfaceDeclaration:
                const idExported = (node as InterfaceDeclaration).modifiers && (node as InterfaceDeclaration).modifiers.find((mod: Modifier) => mod.kind === SyntaxKind.ExportKeyword);
                if (idExported) {
                    interfaces.set((node as InterfaceDeclaration).name.getText(), <InterfaceDeclaration>node);
                }
                break;
            case SyntaxKind.EnumDeclaration:
                const edExported = (node as EnumDeclaration).modifiers && (node as EnumDeclaration).modifiers.find((mod: Modifier) => mod.kind === SyntaxKind.ExportKeyword);
                if (edExported) {
                    enums.set((node as EnumDeclaration).name.getText(), <EnumDeclaration>node);
                }
                break;
        }
    });
    generateJavaSourceFile('JSONBase', getJSONBaseText());
    enums.forEach((enumDecl: EnumDeclaration, name: string) => {
        const type: Node | undefined = getEnumValueType(enumDecl)
        generateJavaSourceFile(enumDecl.name.getText(), getEnumText(enumDecl, type));
        enumTypes.set(name, type);
    });
    modules.forEach((mod: ModuleDeclaration, name: string) => {
        try {
            if (SyntaxKind.ModuleBlock === mod.body.kind) {
                const type: Node | undefined = getPossibleEnumValueType(mod.body);
                if (type) {
                    generateJavaSourceFile(mod.name.getText(), getEnumText(mod, type));
                    enumTypes.set(name, type);
                }
            }
        } catch (e) {
            if (args.verbose) {
                console.log('Cannot generate: ' + mod.name.getText() + ' - ' + e.message);
            }
        }
    });
    typeAliases.forEach((alias: TypeAliasDeclaration, name: string) => {
        try {
            if (!enumTypes.has(name)) {
                const type: TypeNode = alias.type;
                switch (type.kind) {
                    case SyntaxKind.IntersectionType:
                        const intersectedArr: InterfaceDeclaration[] = [];
                        (type as IntersectionTypeNode).types.forEach((tn: TypeNode) => {
                            let decl: InterfaceDeclaration = getDeclForType(tn);
                            if (decl) {
                                intersectedArr.push(decl);
                            }
                        });
                        if (intersectedArr.length > 0) {
                            intersected.set(name, intersectedArr);
                            mergeIntersected(intersectedArr);
                            generateJavaSourceFile(alias.name.getText(), getClassText(alias, modules.get(name), intersectedArr));
                        }
                        break;
                    case SyntaxKind.UnionType:
                        const unionArr: TypeLiteralNode[] = [];
                        (type as UnionTypeNode).types.forEach((tn: TypeNode) => {
                            if (tn.kind === SyntaxKind.TypeLiteral) {
                                unionArr.push(tn as TypeLiteralNode);
                            }
                        });
                        if (unionArr.length > 0) {
                            if (unionArr.length === (type as UnionTypeNode).types.length) {
                                unionTypes.add(name);
                            }
                            generateJavaSourceFile(alias.name.getText(), getClassText(alias, modules.get(name), unionArr));
                        }
                        break;
                }
            }
        } catch (e) {
            if (args.verbose) {
                console.log('Cannot generate: ' + alias.name.getText() + ' - ' + e.message);
            }
        }
    });
    interfaces.forEach((iface: InterfaceDeclaration, name: string) => {
        try {
            if (!enumTypes.has(name)) {
                generateJavaSourceFile(iface.name.getText(), getClassText(iface, modules.get(name), intersected.get(name) || []));
            }
        } catch (e) {
            if (args.verbose) {
                console.log('Cannot generate: ' + iface.name.getText() + ' - ' + e.message);
            }
        }
    });
});

async function parseSource(filePath: string): Promise<SourceFile> {
    return await createSourceFile(filePath, readFileSync(filePath).toString(), ScriptTarget.ES2019, true, ScriptKind.TS);
}

function traverse(roots: Node[], visit: (node: Node) => void, skipContents?: (node: Node) => boolean): void {
    const stack = roots.slice();
    for (let node = stack.shift(); node !== undefined; node = stack.shift()) {
        visit(node);
        if (skipContents && skipContents(node)) {
            continue;
        }
        stack.unshift(...node.getChildren());
    }
}

function generateJavaSourceFile(name: string, content: string): void {
    let s: string = license || '';
    if (pkg) {
        s += 'package ' + pkg + ';\n\n';
    }
    s += content;
    if (!existsSync(args.target)) {
        mkdirSync(args.target);
    }
    writeFileSync(join(args.target, name + '.java'), s);
}

function getJSONBaseText() {
    let text = 'import com.oracle.truffle.tools.utils.json.JSONObject;\n\n';
    text += 'public abstract class JSONBase {\n\n';

    // jsonData field
    text += indent + 'final JSONObject jsonData;\n\n';

    // constructor
    text += indent + 'JSONBase(JSONObject jsonData) {\n';
    text += indent.repeat(2) + 'this.jsonData = jsonData;\n';
    text += indent + '}\n';

    text += '}\n';
    return text;
}

let toImport: {hasArray: boolean, hasMap: boolean, hasObjects: boolean};
let clsName: string;

function getClassText(declNode: DeclarationStatement, moduleNode: ModuleDeclaration, addedTypeNodes: (InterfaceDeclaration | TypeLiteralNode)[]): string {
    let text: string = '';

    const declNodes = addedTypeNodes.slice();
    if (declNode.kind === SyntaxKind.InterfaceDeclaration) {
        declNodes.unshift(declNode as InterfaceDeclaration);
    }

    let isAbstract: boolean = false;
    traverse(declNodes, (n: Node) => {
        switch (n.kind) {
            case SyntaxKind.MethodSignature:
            case SyntaxKind.MethodDeclaration:
                isAbstract = true;
                break;
        }
    });

    let comment = getComment(declNode as HasJSDoc, 0);
    if (!comment && addedTypeNodes.length > 0) {
        comment = getComment(addedTypeNodes[0] as HasJSDoc, 0);
    }
    if (comment) {
        text += comment;
        if (comment.includes('@deprecated')) {
            text += '@Deprecated\n';
        }
    }

    toImport = {hasArray: false, hasMap: false, hasObjects: false};
    clsName = declNode.name.getText();

    if (isAbstract) {
        text += 'public abstract class ' + clsName + ' extends JSONBase {\n';
    } else {
        let extendedType: InterfaceDeclaration;
        text += 'public class ' + clsName;
        if (declNodes[0].kind === SyntaxKind.InterfaceDeclaration && (declNodes[0] as InterfaceDeclaration).heritageClauses) {
            (declNodes[0] as InterfaceDeclaration).heritageClauses.forEach((clause: HeritageClause) => {
                if (clause.token === SyntaxKind.ExtendsKeyword) {
                    let max: number = 0;
                    clause.types.forEach((expr: ExpressionWithTypeArguments) => {
                        let extDecl: InterfaceDeclaration = interfaces.get(expr.expression.getText());
                        if (extDecl) {
                            let depth = getDepth(extDecl);
                            if (extendedType) {
                                if (depth > max) {
                                    declNodes.push(extendedType);
                                    extendedType = extDecl;
                                    max = depth;
                                } else {
                                    declNodes.push(extDecl);
                                }
                            } else {
                                extendedType = extDecl;
                                max = depth;
                            }
                        }
                    });
                    if (extendedType) {
                        text += ' extends ' + extendedType.name.getText();
                    }
                }
            });
        }
        if (!extendedType) {
            text += ' extends JSONBase';
        }

        text += ' {\n\n';

        // constructor
        text += indent + clsName + '(JSONObject jsonData) {\n';
        text += indent.repeat(2) + 'super(jsonData);\n';
        text += indent + '}\n';
    }

    const typeParams = new Map<string, TypeNode | string>();
    if ((declNode.kind === SyntaxKind.InterfaceDeclaration || declNode.kind === SyntaxKind.TypeAliasDeclaration) && (declNode as InterfaceDeclaration | TypeAliasDeclaration).typeParameters) {
        (declNode as InterfaceDeclaration | TypeAliasDeclaration).typeParameters.forEach((typeParam: TypeParameterDeclaration) => {
            typeParams.set(typeParam.name.getText(), typeParam.default ? typeParam.default : ANY);
        });
    }

    // members
    let optionalProperties = unionTypes.has(clsName) ? new Set<PropertySignature>() : undefined;
    text += getClassMembersText(clsName, getMembers(declNodes, optionalProperties), isAbstract, typeParams, optionalProperties);
    if (!isAbstract) {
        seedrandom(clsName, { global: true });
        const props = new Map<string, PropertySignature>();
        const nested = new Set<TypeLiteralNode>();
        findProperties(declNodes, props, nested);
        // equals
        text += getEqualsText(clsName, props);
        // hashCode
        text += getHashCodeText(props);
        // static members
        text += getStaticClassMembersText(clsName, moduleNode, props, optionalProperties);
        text += getNestedTypesText(nested, typeParams, 1);
    }

    text += '}\n';

    let importsText: string = '';
    const imports: string[] = [];
    if (toImport.hasArray) {
        imports.push('java.util.List');
        if (!isAbstract) {
            imports.push('java.util.ArrayList');
            imports.push('java.util.Collections');
            imports.push('com.oracle.truffle.tools.utils.json.JSONArray');
        }
    }
    if (toImport.hasMap) {
        imports.push('java.util.Map');
        if (!isAbstract) {
            imports.push('java.util.HashMap');
        }
    }
    if (!isAbstract) {
        imports.push('com.oracle.truffle.tools.utils.json.JSONObject');
        if (toImport.hasObjects) {
            imports.push('java.util.Objects');
        }
    }
    imports.sort().forEach((imp: string) => {
        importsText += 'import ' + imp + ';\n';

    });
    if (importsText) {
        importsText += '\n';
    }

    return importsText + text;
}

function getClassMembersText(typeName: string, members: TypeElement[], isAbstract: boolean, typeParams: Map<string, TypeNode | string>, optionalProperties?:Set<PropertySignature>): string {
    let text: string = '';
    members.forEach((element: TypeElement) => {
        switch(element.kind) {
            case SyntaxKind.PropertySignature:
                text += getPropertyText(typeName, element as PropertySignature, isAbstract, typeParams, optionalProperties);
                break;
            case SyntaxKind.MethodSignature:
                text += getMethodSignatureText(<MethodSignature>element);
                break;
            case SyntaxKind.IndexSignature:
                text += getIndexSignatureText(typeName, <IndexSignatureDeclaration>element, isAbstract);
                break;
            default:
                throw new Error('Unexpected type: ' + SyntaxKind[element.kind]);
        }
    })
    return text;
}

function getEqualsText(typeName: string, props: Map<string, PropertySignature>): string {
    let text: string = '\n';
    text += indent + '@Override\n';
    text += indent + 'public boolean equals(Object obj) {\n';
    text += indent.repeat(2) + 'if (this == obj) {\n';
    text += indent.repeat(3) + 'return true;\n';
    text += indent.repeat(2) + '}\n';
    text += indent.repeat(2) + 'if (obj == null) {\n';
    text += indent.repeat(3) + 'return false;\n';
    text += indent.repeat(2) + '}\n';
    text += indent.repeat(2) + 'if (this.getClass() != obj.getClass()) {\n';
    text += indent.repeat(3) + 'return false;\n';
    text += indent.repeat(2) + '}\n';

    if (props.size > 0) {
        text += indent.repeat(2) + typeName + ' other = (' + typeName + ') obj;\n';

        props.forEach((prop: PropertySignature, propName: string) => {
            let typeNode: Node = findType(prop.type);
            let hasNull: boolean = false;
            if (typeNode.kind === SyntaxKind.UnionType) {
                let common: Node = commonType(<UnionTypeNode>typeNode);
                if (common) {
                    (typeNode as UnionTypeNode).types.forEach((tn: TypeNode) => {
                        if (tn.kind === SyntaxKind.NullKeyword) {
                            hasNull = true;
                        }
                    });
                    typeNode = common;
                }
            }
            let getter = getterNameFor(propName, prop.questionToken || hasNull ? getBoxedType(typeNode) : getJavaType(typeNode));
            let equals = getEquals(typeNode, hasNull || prop.questionToken !== undefined);
            if (equals) {
                text += indent.repeat(2) + 'if (!' + equals + '(this.' + getter + '(), other.' + getter + '())) {\n';
            } else {
                text += indent.repeat(2) + 'if (this.' + getter + '() != other.' + getter + '()) {\n';
            }
            text += indent.repeat(3) + 'return false;\n';
            text += indent.repeat(2) + '}\n';
        });
    }

    text += indent.repeat(2) + 'return true;\n';

    text += indent + '}\n';

    return text;
}

function getHashCodeText(props: Map<string, PropertySignature>): string {
    let text: string = '\n';
    text += indent + '@Override\n';
    text += indent + 'public int hashCode() {\n';

    const startPrime = generatePrimeNumber(2, 10);
    const multiplyPrime = generatePrimeNumber(10, 100);
    text += indent.repeat(2) + 'int hash = ' + startPrime + ';\n';
    props.forEach((prop: PropertySignature, propName: string) => {
        let typeNode: Node = findType(prop.type);
        let hasNull: boolean = false;
        if (typeNode.kind === SyntaxKind.UnionType) {
            let common: Node = commonType(<UnionTypeNode>typeNode);
            if (common) {
                (typeNode as UnionTypeNode).types.forEach((tn: TypeNode) => {
                    if (tn.kind === SyntaxKind.NullKeyword) {
                        hasNull = true;
                    }
                });
                typeNode = common;
            }
        }
        if (prop.questionToken || hasNull) {
            text += indent.repeat(2) + 'if (this.' + getterNameFor(propName, getBoxedType(typeNode)) + '() != null) {\n';
        }
        text += indent.repeat(prop.questionToken || hasNull ? 3 : 2) + 'hash = ' + multiplyPrime + ' * hash + ' + getHash(typeNode) + '(this.' + getterNameFor(propName, prop.questionToken || hasNull ? getBoxedType(typeNode) : getJavaType(typeNode)) + '());\n';
        if (prop.questionToken || hasNull) {
            text += indent.repeat(2) + '}\n';
        }
    });
    text += indent.repeat(2) + 'return hash;\n';

    text += indent + '}\n';

    return text;
}

function getStaticClassMembersText(typeName: string, node: ModuleDeclaration, props: Map<string, PropertySignature>, optionalProperties?:Set<PropertySignature>): string {
    let text: string = '';
    if (node && node.body.kind === SyntaxKind.ModuleBlock) {
        node.body.statements.forEach((stat: Statement) => {
            switch(stat.kind) {
                case SyntaxKind.FunctionDeclaration:
                    if ((stat as FunctionDeclaration).type.kind === SyntaxKind.TypeReference && node.name.getText() === getJavaType(findType((stat as FunctionDeclaration).type))) {
                        text += getCreatorText(<FunctionDeclaration>stat, props, optionalProperties);
                    }
                    break;
                case SyntaxKind.VariableStatement:
                case SyntaxKind.EmptyStatement:
                    break;
                default:
                    throw new Error('Unexpected type: ' + SyntaxKind[stat.kind]);
            }
        });
    }
    if (!text) {
        text += getDefaultCreatorText(typeName, props, optionalProperties);
    }
    return text;
}

function getNestedTypesText(types: Set<TypeLiteralNode>, typeParams: Map<string, TypeNode | string>, indentLevel: number): string {
    let text: string = '';
    types.forEach((type: TypeLiteralNode) => {
        const name = typeLiteralNames.get(type);
        if (name) {
            text += '\n' + indent.repeat(indentLevel) + 'public static class ' + name + ' extends JSONBase {\n\n';
            text += indent.repeat(indentLevel + 1) + name + '(JSONObject jsonData) {\n';
            text += indent.repeat(indentLevel + 2) + 'super(jsonData);\n';
            text += indent.repeat(indentLevel + 1) + '}\n';
            const nested = new Set<TypeLiteralNode>();
            const props = new Map<string, PropertySignature>();
            type.members.forEach((element: TypeElement) => {
                switch(element.kind) {
                    case SyntaxKind.PropertySignature:
                        text += '\n' + indent;
                        getPropertyText(name, element as PropertySignature, false, typeParams).trim().split('\n').forEach((line: string) => {
                            text += (line.length > 0 ? indent.repeat(indentLevel) + line : line) + '\n';
                        });
                        let elementName: string = element.name.getText();
                        props.set(elementName, element as PropertySignature);
                        let elementType = (element as PropertySignature).type;
                        if (elementType && elementType.kind === SyntaxKind.TypeLiteral && !checkMap(<TypeLiteralNode>elementType)) {
                            nested.add(<TypeLiteralNode>elementType);
                        }
                        break;
                    default:
                        throw new Error('Unexpected type: ' + SyntaxKind[element.kind]);
                }
            });
            if (props.size > 0) {
                text += '\n' + indent;
                getEqualsText(name, props).trim().split('\n').forEach((line: string) => {
                    text += (line.length > 0 ? indent.repeat(indentLevel) + line : line) + '\n';
                });
                text += '\n' + indent;
                getHashCodeText(props).trim().split('\n').forEach((line: string) => {
                    text += (line.length > 0 ? indent.repeat(indentLevel) + line : line) + '\n';
                });
            }
            if (nested.size > 0) {
                text += getNestedTypesText(nested, typeParams, indentLevel + 1);
            }
            text += indent.repeat(indentLevel) + '}\n';
        }
    });
    return text;
}

function getPropertyText(clsName: string, node: PropertySignature, isAbstract: boolean, typeParams: Map<string, TypeNode | string>, optionalProperties?:Set<PropertySignature>): string {
    let text: string = '\n';
    let comment = getComment(node, 1);
    if (comment) {
        text += comment;
        if (comment.includes('@deprecated')) {
            text += indent + '@Deprecated\n';
        }
    }

    let typeNode: Node = findType(node.type);
    if (typeNode.kind === SyntaxKind.TypeReference) {
        let typeParam = typeParams.get((typeNode as TypeReferenceNode).typeName.getText());
        if (typeParam) {
            typeNode = typeParam === ANY ? undefined : typeParam as TypeNode;
        }
    } else if (typeNode.kind === SyntaxKind.TypeLiteral && !checkMap(<TypeLiteralNode>typeNode)) {
        let name: string = node.name.getText();
        name = name.charAt(0).toUpperCase() + name.slice(1);
        if (args.prefixNested) {
            name = suffixFor(clsName) + name;
        } else if (args.suffixNested) {
            name = name + suffixFor(clsName);
        }
        typeLiteralNames.set(<TypeLiteralNode>typeNode, name);
    }

    const optional: boolean = optionalProperties && optionalProperties.has(node) || node.questionToken !== undefined;
    const type: string = typeNode ? optional ? getBoxedType(typeNode) : getJavaType(typeNode) : 'Object';
    let hasNull: boolean = false;
    if (typeNode && typeNode.kind === SyntaxKind.UnionType) {
        let common: Node = commonType(<UnionTypeNode>typeNode);
        if (common) {
            (typeNode as UnionTypeNode).types.forEach((tn: TypeNode) => {
                if (tn.kind === SyntaxKind.NullKeyword) {
                    hasNull = true;
                }
            });
            typeNode = common;
        }
    }
    if (type === 'Boolean') {
        text += indent + '@SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")\n';
    }
    const key: string = '"' + node.name.getText() + '"';
    if (isAbstract) {
        text += indent + 'public abstract ' + type + ' ' + getterNameFor(node.name.getText(), type) + '();\n';
    } else {
        text += indent + 'public ' + type + ' ' + getterNameFor(node.name.getText(), type) + '() {\n';
        if (typeNode) {
            switch(typeNode.kind) {
                case SyntaxKind.ArrayType:
                    text += indent.repeat(2) + 'final JSONArray json = ' + jsonGetter('jsonData', key, typeNode, optional || hasNull) + ';\n';
                    if (optional || hasNull) {
                        text += indent.repeat(2) + 'if (json == null) {\n';
                        text += indent.repeat(3) + 'return null;\n';
                        text += indent.repeat(2) + '}\n';
                    }
                    text += indent.repeat(2) + 'final ' + type + ' list = new ArrayList<>(json.length());\n';
                    text += indent.repeat(2) + 'for (int i = 0; i < json.length(); i++) {\n';
                    let elemType: Node = findType((typeNode as ArrayTypeNode).elementType);
                    let uInfo = elemType.kind === SyntaxKind.UnionType ? checkUnion(elemType as UnionTypeNode) : null;
                    if (uInfo && (uInfo.typeRef || uInfo.enumRef || uInfo.arrType)) {
                        text += indent.repeat(3) + 'Object obj = ' + jsonGetter('json', 'i', elemType, false) + ';\n';
                        text += unionInfoGetter(uInfo, 'obj', 'list.add(', ')', false, 3);
                    } else {
                        text += indent.repeat(3) + 'list.add(' + jsonGetter('json', 'i', elemType, false) + ');\n';
                    }
                    text += indent.repeat(2) + '}\n';
                    text += indent.repeat(2) + 'return Collections.unmodifiableList(list);\n';
                    break;
                case SyntaxKind.UnionType:
                    let unionInfo = checkUnion(<UnionTypeNode>typeNode);
                    if (unionInfo.typeRef || unionInfo.enumRef || unionInfo.arrType) {
                        text += indent.repeat(2) + 'Object obj = ' + jsonGetter('jsonData', key, typeNode, optional || hasNull) + ';\n';
                        text += unionInfoGetter(unionInfo, 'obj', 'return ', '', hasNull, 2);
                    } else if (unionInfo.typeRef === undefined && unionInfo.arrType === undefined && unionInfo.enumRef === undefined) {
                        if (hasNull) {
                            text += indent.repeat(2) + 'Object obj = jsonData' + (optional ? '.opt(' : '.get(') + key + ');\n';
                            text += indent.repeat(2) + 'return JSONObject.NULL.equals(obj) ? null : ' + (type !== 'Object' ? '(' + type + ') ' : '') + 'obj;\n';
                        } else {
                            text += indent.repeat(2) + 'return ' + jsonGetter('jsonData', key, typeNode, optional || hasNull) + ';\n';
                        }
                    } else if (args.verbose) {
                        console.log('Cannot generate: ' + clsName + '.' + getterNameFor(node.name.getText(), type));
                    }
                    break;
                case SyntaxKind.TypeLiteral:
                    let mapType = checkMap(<TypeLiteralNode>typeNode);
                    if (mapType && getJavaType(mapType.keyType) === 'String') {
                        text += indent.repeat(2) + 'final JSONObject json = ' + jsonGetter('jsonData', key, typeNode, optional || hasNull) + ';\n';
                        if (optional || hasNull) {
                            text += indent.repeat(2) + 'if (json == null) {\n';
                            text += indent.repeat(3) + 'return null;\n';
                            text += indent.repeat(2) + '}\n';
                        }
                        text += indent.repeat(2) + 'final ' + type + ' map = new HashMap<>(json.length());\n';
                        text += indent.repeat(2) + 'for (String key : json.keySet()) {\n';
                        if (mapType.valueType.kind === SyntaxKind.ArrayType) {
                            text += indent.repeat(3) + 'final JSONArray jsonArr = json.getJSONArray(key);\n';
                            text += indent.repeat(3) + 'final ' + getJavaType(mapType.valueType) + ' list = new ArrayList<>(jsonArr.length());\n';
                            text += indent.repeat(3) + 'for (int i = 0; i < json.length(); i++) {\n';
                            let elemType: Node = findType((mapType.valueType as ArrayTypeNode).elementType);
                            text += indent.repeat(4) + 'list.add(' + jsonGetter('jsonArr', 'i', elemType, false) + ');\n';
                            text += indent.repeat(3) + '}\n';
                            text += indent.repeat(3) + 'map.put(key, Collections.unmodifiableList(list));\n'
                        } else {
                            text += indent.repeat(3) + 'map.put(key, ' + jsonGetter('json', 'key', mapType.valueType, false) + ');\n';
                        }
                        text += indent.repeat(2) + '}\n';
                        text += indent.repeat(2) + 'return map;\n';
                    } else if (hasNull) {
                        text += indent.repeat(2) + 'Object obj = jsonData' + (optional ? '.opt(' : '.get(') + key + ');\n';
                        text += indent.repeat(2) + 'return JSONObject.NULL.equals(obj) ? null : ' + (type !== 'Object' ? '(' + type + ') ' : '') + 'obj;\n';
                    } else {
                        text += indent.repeat(2) + 'return ' + jsonGetter('jsonData', key, typeNode, optional) + ';\n';
                    }
                    break;
                default:
                    if (hasNull) {
                        text += indent.repeat(2) + 'Object obj = jsonData' + (optional ? '.opt(' : '.get(') + key + ');\n';
                        text += indent.repeat(2) + 'return JSONObject.NULL.equals(obj) ? null : ' + (type !== 'Object' ? '(' + type + ') ' : '') + 'obj;\n';
                    } else {
                        text += indent.repeat(2) + 'return ' + jsonGetter('jsonData', key, typeNode, optional) + ';\n';
                    }
            }
        } else {
            text += indent.repeat(2) + 'return ' + jsonGetter('jsonData', key, typeNode, optional) + ';\n';
        }
        text += indent + '}\n';
    }

    let readOnly = node.modifiers && node.modifiers.find((mod: Modifier) => mod.kind === SyntaxKind.ReadonlyKeyword);

    if (!readOnly) {
        const varName: string = varNameFor(node.name.getText());
        if (isAbstract) {
            text += '\n' + indent + 'public abstract ' + clsName + ' ' + setterNameFor(node.name.getText(), type) + '(' + type + ' ' + varName + ');\n';
        } else {
            text += '\n' + indent + 'public ' + clsName + ' ' + setterNameFor(node.name.getText(), type) + '(' + type + ' ' + varName + ') {\n';
            if (typeNode) {
                switch (typeNode.kind) {
                    case SyntaxKind.ArrayType:
                        if (optional || hasNull) {
                            text += indent.repeat(2) + 'if (' + varName + ' != null) {\n';
                        }
                        text += indent.repeat(optional || hasNull ? 3 : 2) + 'final JSONArray json = new JSONArray();\n';
                        let elemType: Node = findType((typeNode as ArrayTypeNode).elementType);
                        let elemTypeName: string = getJavaType(elemType);
                        text += indent.repeat(optional || hasNull ? 3 : 2) + 'for (' + elemTypeName + ' ' + varNameFor(elemTypeName) +  ': ' + varName + ') {\n';
                        let uInfo = elemType.kind === SyntaxKind.UnionType ? checkUnion(elemType as UnionTypeNode) : null;
                        if (uInfo && (uInfo.typeRef || uInfo.enumRef || uInfo.arrType)) {
                            text += unionInfoSetter(uInfo, 'json', varNameFor(elemTypeName), null, elemType as UnionTypeNode, false, optional || hasNull ? 4 : 3);
                        } else {
                            text += indent.repeat(optional || hasNull ? 4 : 3) + jsonSetter('json', null, varNameFor(elemTypeName), elemType, false) + ";\n";
                        }
                        text += indent.repeat(optional || hasNull ? 3 : 2) + '}\n';
                        text += indent.repeat(optional || hasNull ? 3 : 2) + jsonSetter('jsonData', key, 'json', typeNode, false) + ';\n';
                        if (optional) {
                            text += indent.repeat(2) + '}\n';
                        } else if (hasNull) {
                            text += indent.repeat(2) + '} else {\n';
                            text += indent.repeat(3) + 'jsonData.put(' + key +', JSONObject.NULL);\n';
                            text += indent.repeat(2) + '}\n';
                        }
                        break;
                    case SyntaxKind.UnionType:
                        let unionInfo = checkUnion(<UnionTypeNode>typeNode);
                        if (unionInfo.typeRef || unionInfo.enumRef || unionInfo.arrType) {
                            text += unionInfoSetter(unionInfo, 'jsonData', varName, key, typeNode as UnionTypeNode, hasNull, 2);
                        } else if (unionInfo.typeRef === undefined && unionInfo.arrType === undefined && unionInfo.enumRef === undefined) {
                            if (hasNull) {
                                text += indent.repeat(2) + 'jsonData.put(' + key +', ' + varName + ' == null ? JSONObject.NULL : ' + varName + ');\n';
                            } else {
                                text += indent.repeat(2) + jsonSetter('jsonData', key, varName, typeNode, optional) + ';\n';
                            }
                        } else if (args.verbose) {
                            console.log('Cannot generate: ' + clsName + '.' + setterNameFor(node.name.getText(), type));
                        }
                        break;
                    case SyntaxKind.TypeLiteral:
                        let mapType = checkMap(<TypeLiteralNode>typeNode);
                        if (mapType && getJavaType(mapType.keyType) === 'String') {
                            if (optional || hasNull) {
                                text += indent.repeat(2) + 'if (' + varName + ' != null) {\n';
                            }
                            text += indent.repeat(optional || hasNull ? 3 : 2) + 'final JSONObject json = new JSONObject();\n';
                            text += indent.repeat(optional || hasNull ? 3 : 2) + 'for(Map.Entry<String, ' + getJavaType(mapType.valueType) + '> entry : ' + varName + '.entrySet()) {\n';
                            if (mapType.valueType.kind === SyntaxKind.ArrayType) {
                                text += indent.repeat(optional || hasNull ? 4 : 3) + 'final JSONArray jsonArr = new JSONArray();\n';
                                let elemType: Node = findType((mapType.valueType as ArrayTypeNode).elementType);
                                let elemTypeName: string = getJavaType(elemType);
                                text += indent.repeat(optional || hasNull ? 4 : 3) + 'for (' + elemTypeName + ' ' + varNameFor(elemTypeName) + ' : entry.getValue()) {\n';
                                text += indent.repeat(optional || hasNull ? 5 : 4) + jsonSetter('jsonArr', null, varNameFor(elemTypeName), elemType, false) + ';\n';
                                text += indent.repeat(optional || hasNull ? 4 : 3) + '}\n';
                                text += indent.repeat(optional || hasNull ? 4 : 3) + jsonSetter('json', 'entry.getKey()', 'jsonArr', mapType.valueType, false) + ';\n';
                            } else {
                                text += indent.repeat(optional || hasNull ? 4 : 3) + jsonSetter('json', 'entry.getKey()', 'entry.getValue()', mapType.valueType, false) + ';\n';
                            }
                            text += indent.repeat(optional || hasNull ? 3 : 2) + '}\n';
                            text += indent.repeat(optional || hasNull ? 3 : 2) + jsonSetter('jsonData', key, 'json', typeNode, false) + ';\n';
                            if (optional) {
                                text += indent.repeat(2) + '}\n';
                            } else if (hasNull) {
                                text += indent.repeat(2) + '} else {\n';
                                text += indent.repeat(3) + 'jsonData.put(' + key +', JSONObject.NULL);\n';
                                text += indent.repeat(2) + '}\n';
                            }
                        } else if (hasNull) {
                            text += indent.repeat(2) + 'jsonData.put(' + key +', ' + varName + ' == null ? JSONObject.NULL : ' + varName + ');\n';
                        } else {
                            text += indent.repeat(2) + jsonSetter('jsonData', key, varName, typeNode, optional) + ';\n';
                        }
                        break;
                    default:
                        if (hasNull) {
                            text += indent.repeat(2) + 'jsonData.put(' + key +', ' + varName + ' == null ? JSONObject.NULL : ' + varName + ');\n';
                        } else {
                            text += indent.repeat(2) + jsonSetter('jsonData', key, varName, typeNode, optional) + ';\n';
                        }
                }
            } else {
                text += indent.repeat(2) + jsonSetter('jsonData', key, varName, typeNode, optional) + ';\n';
            }
            text += indent.repeat(2) + 'return this;\n';
            text += indent + '}\n';
        }
    }

    return text;
}

function getMethodSignatureText(node: MethodSignature): string {
    let text: string = '\n';
    let comment = getComment(node, 1);
    if (comment) {
        text += comment;
        if (comment.includes('@deprecated')) {
            text += indent + '@Deprecated\n';
        }
    }

    let typeNode: Node = findType(node.type);
    if (typeNode.kind === SyntaxKind.UnionType) {
        let common: Node = commonType(<UnionTypeNode>typeNode);
        if (common) {
            typeNode = common;
        }
    }
    const type: string = getJavaType(typeNode);

    text += indent  + 'public abstract ' + type + ' ' + node.name.getText() + '(';
    node.parameters.forEach((param: ParameterDeclaration, index: number) => {
        if (index > 0) {
            text += ', ';
        }
        text += getJavaType(findType(param.type)) + ' ' + param.name.getText();
    });
    text += ');\n';
    return text;
}

function getIndexSignatureText(clsName: string, node: IndexSignatureDeclaration, isAbstract: boolean): string {
    if (node.parameters.length !== 1) {
        return '';
    }
    let keyType: string;
    node.parameters.forEach((param: ParameterDeclaration) => {
        keyType = getJavaType(findType(param.type));
    });
    if (keyType !== 'String') {
        return '';
    }
    let text: string = '\n';
    let comment = getComment(node, 1);
    if (comment) {
        text += comment;
        if (comment.includes('@deprecated')) {
            text += indent + '@Deprecated\n';
        }
    }

    let typeNode: Node = findType(node.type);
    if (typeNode.kind === SyntaxKind.UnionType) {
        let common: Node = commonType(<UnionTypeNode>typeNode);
        if (common) {
            typeNode = common;
        }
    }
    const type: string = getJavaType(typeNode);

    if (isAbstract) {
        text += indent + 'public abstract ' + type + ' get(String key);\n';
    } else {
        text += indent + 'public ' + type + ' get(String key) {\n';
        switch(typeNode.kind) {
            case SyntaxKind.ArrayType:
                text += indent.repeat(2) + 'final JSONArray json = ' + jsonGetter('jsonData', 'key', typeNode, false) + ';\n';
                text += indent.repeat(2) + 'final ' + type + ' list = new ArrayList<>(json.length());\n';
                text += indent.repeat(2) + 'for (int i = 0; i < json.length(); i++) {\n';
                let elemType: Node = findType((typeNode as ArrayTypeNode).elementType);
                let uInfo = elemType.kind === SyntaxKind.UnionType ? checkUnion(elemType as UnionTypeNode) : null;
                if (uInfo && (uInfo.typeRef || uInfo.enumRef || uInfo.arrType)) {
                    text += indent.repeat(3) + 'Object obj = ' + jsonGetter('json', 'i', elemType, false) + ';\n';
                    text += unionInfoGetter(uInfo, 'obj', 'list.add(', ')', false, 3);
                } else {
                    text += indent.repeat(3) + 'list.add(' + jsonGetter('json', 'i', elemType, false) + ');\n';
                }
                text += indent.repeat(2) + '}\n';
                text += indent.repeat(2) + 'return Collections.unmodifiableList(list);\n';
                break;
            case SyntaxKind.UnionType:
                let unionInfo = checkUnion(<UnionTypeNode>typeNode)
                if (unionInfo.typeRef || unionInfo.arrType || unionInfo.enumRef) {
                    text += indent.repeat(2) + 'Object obj = ' + jsonGetter('jsonData', 'key', typeNode, false) + ';\n';
                    text += unionInfoGetter(unionInfo, 'obj', 'return ', '', false, 2);
                } else if (unionInfo.typeRef === undefined && unionInfo.arrType === undefined && unionInfo.enumRef === undefined) {
                    text += indent.repeat(2) + 'return ' + jsonGetter('jsonData', 'key', typeNode, false) + ';\n';
                } else if (args.verbose) {
                    console.log('Cannot generate: ' + clsName + '.get');
                }
                break;
            default:
                text += indent.repeat(2) + 'return ' + jsonGetter('jsonData', 'key', typeNode, false) + ';\n';
        }
        text += indent + '}\n';
    }

    let readOnly = node.modifiers && node.modifiers.find((mod: Modifier) => mod.kind === SyntaxKind.ReadonlyKeyword);

    if (!readOnly) {
        if (isAbstract) {
            text += '\n' + indent + 'public abstract ' + clsName + ' set(String key, ' + type + ' value);\n';
        } else {
            text += '\n' + indent + 'public ' + clsName + ' set(String key, ' + type + ' value) {\n';
            switch (typeNode.kind) {
                case SyntaxKind.ArrayType:
                    text += indent.repeat(2) + 'final JSONArray json = new JSONArray();\n';
                    let elemType: Node = findType((typeNode as ArrayTypeNode).elementType);
                    let elemTypeName: string = getJavaType(elemType);
                    text += indent.repeat(2) + 'for (' + elemTypeName + ' ' + varNameFor(elemTypeName) +  ': value) {\n';
                    let uInfo = elemType.kind === SyntaxKind.UnionType ? checkUnion(elemType as UnionTypeNode) : null;
                    if (uInfo && (uInfo.typeRef || uInfo.enumRef || uInfo.arrType)) {
                        text += unionInfoSetter(uInfo, 'json', varNameFor(elemTypeName), null, elemType as UnionTypeNode, false, 3);
                    } else {
                        text += indent.repeat(3) + jsonSetter('json', null, varNameFor(elemTypeName), elemType, false) + ";\n";
                    }
                    text += indent.repeat(2) + '}\n';
                    text += indent.repeat(2) + jsonSetter('jsonData', 'key', 'json', typeNode, false) + ';\n';
                    break;
                case SyntaxKind.UnionType:
                    let unionInfo = checkUnion(<UnionTypeNode>typeNode);
                    if (unionInfo.typeRef || unionInfo.arrType || unionInfo.enumRef) {
                        text += unionInfoSetter(unionInfo, 'jsonData', 'value', 'key', typeNode as UnionTypeNode, false, 2);
                    } else if (unionInfo.typeRef === undefined && unionInfo.arrType === undefined && unionInfo.enumRef === undefined) {
                        text += indent.repeat(2) + jsonSetter('jsonData', 'key', 'value', typeNode, false) + ';\n';
                    } else if (args.verbose) {
                        console.log('Cannot generate: ' + clsName + '.set');
                    }
                    break;
                case SyntaxKind.TypeLiteral:
                    let mapType = checkMap(<TypeLiteralNode>typeNode);
                    if (mapType && getJavaType(mapType.keyType) === 'String') {
                        text += indent.repeat(2) + 'final JSONObject json = new JSONObject();\n';
                        text += indent.repeat(2) + 'for(Map.Entry<String, ' + getJavaType(mapType.valueType) + '> entry : value.entrySet()) {\n';
                        if (mapType.valueType.kind === SyntaxKind.ArrayType) {
                            text += indent.repeat(3) + 'final JSONArray jsonArr = new JSONArray();\n';
                            let elemType: Node = findType((mapType.valueType as ArrayTypeNode).elementType);
                            let elemTypeName: string = getJavaType(elemType);
                            text += indent.repeat(3) + 'for (' + elemTypeName + ' ' + varNameFor(elemTypeName) + ' : entry.getValue()) {\n';
                            text += indent.repeat(4) + jsonSetter('jsonArr', null, varNameFor(elemTypeName), elemType, false) + ';\n';
                            text += indent.repeat(3) + '}\n';
                            text += indent.repeat(3) + jsonSetter('json', 'entry.getKey()', 'jsonArr', mapType.valueType, false) + ';\n';
                        } else {
                            text += indent.repeat(3) + jsonSetter('json', 'entry.getKey()', 'entry.getValue()', mapType.valueType, false) + ';\n';
                        }
                        text += indent.repeat(2) + '}\n';
                        text += indent.repeat(2) + jsonSetter('jsonData', 'key', 'json', typeNode, false) + ';\n';
                    } else if (args.verbose) {
                        console.log('Cannot generate: ' + clsName + '.set');
                    }
                    break;
                default:
                    text += indent.repeat(2) + jsonSetter('jsonData', 'key', 'value', typeNode, false) + ';\n';
            }
        }
        text += indent.repeat(2) + 'return this;\n';
        text += indent + '}\n';
    }

    return text;
}

function getCreatorText(node: FunctionDeclaration, props: Map<string, PropertySignature>, optionalProperties?:Set<PropertySignature>): string {
    let text: string = '\n';
    let comment = getComment(node, 1);
    if (comment) {
        text += comment;
        if (comment.includes('@deprecated')) {
            text += indent + '@Deprecated\n';
        }
    }

    const typeNode: Node = findType(node.type);
    const type: string = getJavaType(typeNode);
    text += indent + 'public static ' + type + ' ' + node.name.getText() + '(';
    const params = new Map<string, ParameterDeclaration>();
    node.parameters.forEach((param: ParameterDeclaration, index: number) => {
        if (index > 0) {
            text += ', ';
        }
        let prop: PropertySignature = props.get(param.name.getText());
        let optional = prop && prop.questionToken || optionalProperties && optionalProperties.has(prop);
        if (param.dotDotDotToken && param.type.kind === SyntaxKind.ArrayType) {
            text += getBoxedType(findType((param.type as ArrayTypeNode).elementType)) + '... ' + param.name.getText();
        } else {
            text += (prop && !optional ? getJavaType(findType(param.type)) : getBoxedType(findType(param.type))) + ' ' + param.name.getText();
        }
        params.set(param.name.getText(), param);
    });
    text += ') {\n';

    if (match(props, params)) {
        text += indent.repeat(2) + 'final JSONObject json = new JSONObject();\n';
        props.forEach((prop: PropertySignature, propName: string) => {
            let optional = prop.questionToken || optionalProperties && optionalProperties.has(prop);
            let propType: Node = findType(prop.type);
            let param: ParameterDeclaration = params.get(propName);
            if (!param && !optional) {
                params.forEach((p: ParameterDeclaration) => {
                    if (!param && sameType(propType, findType(p.type))) {
                        param = p;
                    }
                });
            }
            if (param) {
                text += creatorPropInit(prop, propName, param.name.getText(), findType(param.type), type + '.' + node.name.getText());
            } else if (propType.kind === SyntaxKind.LiteralType) {
                text += indent.repeat(2) + (optional ? 'json.putOpt("' : 'json.put("') + propName + '", ' + (propType as LiteralTypeNode).getText().split("'").join('"') + ');\n';
            } else if (!optional && args.verbose) {
                console.log('Cannot generate: ' + type + '.' + node.name.getText());
            }
        });
        text += indent.repeat(2) + 'return new ' + type + '(json);\n';
    } else if (args.verbose) {
        console.log('Cannot generate: ' + type + '.' + node.name.getText());
    }

    text += indent + '}\n';
    return text;
}

function getDefaultCreatorText(type: string, props: Map<string, PropertySignature>, optionalProperties?:Set<PropertySignature>): string {
    let text: string = '\n';
    let params: string = '';
    let body: string = indent.repeat(2) + 'final JSONObject json = new JSONObject();\n';

    props.forEach((prop: PropertySignature, propName: string) => {
        if (!prop.questionToken && (!optionalProperties || !optionalProperties.has(prop))) {
            let propType: Node = findType(prop.type != null ? prop.type : prop.initializer);
            if (propType) {
                if (propType.kind === SyntaxKind.LiteralType) {
                    body += indent.repeat(2) + (prop.questionToken ? 'json.putOpt("' : 'json.put("') + propName + '", ' + (propType as LiteralTypeNode).getText().split("'").join('"') + ');\n';
                } else {
                    if (params) {
                        params += ', ';
                    }
                    params += getBoxedType(propType) + ' ' + propName;
                    body += creatorPropInit(prop, propName, propName, propType, type + '.create');
                }
            }
        }
    });
    body += indent.repeat(2) + 'return new ' + type + '(json);\n';

    text += indent + 'public static ' + type + ' create(' + params + ') {\n';
    text += body;
    text += indent + '}\n';
    return text;
}

function creatorPropInit(prop: PropertySignature, propName: string, paramName: string, paramType: Node, parentInfo: string): string {
    let text: string = '';
    let hasNull: boolean = false;
    if (paramType.kind === SyntaxKind.UnionType) {
        let common: Node = commonType(<UnionTypeNode>paramType);
        if (common) {
            (paramType as UnionTypeNode).types.forEach((tn: TypeNode) => {
                if (tn.kind === SyntaxKind.NullKeyword) {
                    hasNull = true;
                }
            });
            paramType = common;
        }
    }
    const key: string = '"' + propName + '"';
    switch(paramType.kind) {
        case SyntaxKind.NumberKeyword:
        case SyntaxKind.NumericLiteral:
        case SyntaxKind.BooleanKeyword:
        case SyntaxKind.TrueKeyword:
        case SyntaxKind.FalseKeyword:
            if (hasNull) {
                text += indent.repeat(2) + 'json.put(' + key +', ' + paramName + ' == null ? JSONObject.NULL : ' + paramName + ');\n';
            } else {
                if (prop.questionToken) {
                    text += indent.repeat(2) + 'if (' + paramName + ' != null) {\n';
                }
                text += indent.repeat(prop.questionToken ? 3 : 2) + jsonSetter('json', key, paramName, paramType, false) + ';\n';
                if (prop.questionToken) {
                    text += indent.repeat(2) + '}\n';
                }
            }
            break;
        case SyntaxKind.ArrayType:
            if (prop.questionToken || hasNull) {
                text += indent.repeat(2) + 'if (' + paramName + ' != null) {\n';
            }
            text += indent.repeat(prop.questionToken || hasNull ? 3 : 2) + 'JSONArray ' + paramName + 'JsonArr = new JSONArray();\n';
            let elemType: Node = findType((paramType as ArrayTypeNode).elementType);
            let elemTypeName = getJavaType(elemType);
            text += indent.repeat(prop.questionToken || hasNull ? 3 : 2) + 'for(' + elemTypeName + ' ' + varNameFor(elemTypeName) + ': ' + paramName + ') {\n';
            let uInfo = elemType.kind === SyntaxKind.UnionType ? checkUnion(elemType as UnionTypeNode) : null;
            if (uInfo && (uInfo.typeRef || uInfo.enumRef || uInfo.arrType)) {
                text += unionInfoSetter(uInfo, paramName + 'JsonArr', varNameFor(elemTypeName), null, elemType as UnionTypeNode, false, prop.questionToken || hasNull ? 4 : 3);
            } else {
                text += indent.repeat(prop.questionToken || hasNull ? 4 : 3) + jsonSetter(paramName + 'JsonArr', null, varNameFor(elemTypeName), elemType, false) + ";\n";
            }
            text += indent.repeat(prop.questionToken || hasNull ? 3 : 2) + '}\n';
            text += indent.repeat(prop.questionToken || hasNull ? 3 : 2) + jsonSetter('json', key, paramName + 'JsonArr', paramType, false) + ';\n';
            if (prop.questionToken) {
                text += indent.repeat(2) + '}\n';
            } else if (hasNull) {
                text += indent.repeat(2) + '} else {\n';
                text += indent.repeat(3) + 'json.put(' + key +', JSONObject.NULL);\n';
                text += indent.repeat(2) + '}\n';
            }
            break;
        case SyntaxKind.UnionType:
            let unionInfo = checkUnion(<UnionTypeNode>paramType);
            if (unionInfo.typeRef || unionInfo.arrType || unionInfo.enumRef) {
                text += unionInfoSetter(unionInfo, 'json', paramName, key, paramType as UnionTypeNode, hasNull, 2);
            } else if (unionInfo.typeRef === undefined && unionInfo.arrType === undefined && unionInfo.enumRef === undefined) {
                if (hasNull) {
                    text += indent.repeat(2) + 'json.put(' + key +', ' + paramName + ' == null ? JSONObject.NULL : ' + paramName + ');\n';
                } else {
                    text += indent.repeat(2) + jsonSetter('json', key, paramName, paramType, prop.questionToken !== undefined) + ';\n';
                }
            } else if (args.verbose) {
                console.log('Cannot generate: ' + parentInfo);
            }
            break;
        case SyntaxKind.TypeLiteral:
            let mapType = checkMap(<TypeLiteralNode>paramType);
            if (mapType && getJavaType(mapType.keyType) === 'String') {
                if (prop.questionToken || hasNull) {
                    text += indent.repeat(2) + 'if (' + paramName + ' != null) {\n';
                }
                text += indent.repeat(prop.questionToken || hasNull ? 3 : 2) + 'final JSONObject ' + paramName + 'Json = new JSONObject();\n';
                text += indent.repeat(prop.questionToken || hasNull ? 3 : 2) + 'for(Map.Entry<String, ' + getJavaType(mapType.valueType) + '> entry : ' +  paramName + '.entrySet()) {\n';
                if (mapType.valueType.kind === SyntaxKind.ArrayType) {
                    text += indent.repeat(prop.questionToken || hasNull ? 3 : 4) + 'final JSONArray jsonArr = new JSONArray();\n';
                    let elemType: Node = findType((mapType.valueType as ArrayTypeNode).elementType);
                    let elemTypeName: string = getJavaType(elemType);
                    text += indent.repeat(prop.questionToken || hasNull ? 3 : 4) + 'for (' + elemTypeName + ' ' + varNameFor(elemTypeName) + ' : entry.getValue()) {\n';
                    text += indent.repeat(prop.questionToken || hasNull ? 4 : 5) + jsonSetter('jsonArr', null, varNameFor(elemTypeName), elemType, false) + ';\n';
                    text += indent.repeat(prop.questionToken || hasNull ? 3 : 4) + '}\n';
                    text += indent.repeat(prop.questionToken || hasNull ? 3 : 4) + jsonSetter(paramName + 'Json', 'entry.getKey()', 'jsonArr', mapType.valueType, false) + ';\n';
                } else {
                    text += indent.repeat(prop.questionToken || hasNull ? 3 : 4) + jsonSetter(paramName + 'Json', 'entry.getKey()', 'entry.getValue()', mapType.valueType, false) + ';\n';
                }
                text += indent.repeat(prop.questionToken || hasNull ? 3 : 2) + '}\n';
                text += indent.repeat(prop.questionToken || hasNull ? 3 : 2) + jsonSetter('json', key, paramName + 'Json', paramType, false) + ';\n';
                if (prop.questionToken) {
                    text += indent.repeat(2) + '}\n';
                } else if (hasNull) {
                    text += indent.repeat(2) + '} else {\n';
                    text += indent.repeat(3) + 'json.put(' + key +', JSONObject.NULL);\n';
                    text += indent.repeat(2) + '}\n';
                }
            } else if (hasNull) {
                text += indent.repeat(2) + 'json.put(' + key +', ' + paramName + ' == null ? JSONObject.NULL : ' + paramName + ');\n';
            } else {
                text += indent.repeat(2) + jsonSetter('json', key, paramName, paramType, prop.questionToken !== undefined) + ';\n';
            }
            break;
        default:
            if (hasNull) {
                text += indent.repeat(2) + 'json.put(' + key +', ' + paramName + ' == null ? JSONObject.NULL : ' + paramName + ');\n';
            } else {
                text += indent.repeat(2) + jsonSetter('json', key, paramName, paramType, prop.questionToken !== undefined) + ';\n';
            }
    }
    return text;
}

function getEnumText(node: ModuleDeclaration | EnumDeclaration, typeNode: Node): string {
    let text: string = 'import java.util.HashMap;\nimport java.util.Map;\n\n';

    const comment = getComment(node, 0);
    if (comment) {
        text += comment;
        if (comment.includes('@deprecated')) {
            text += '@Deprecated\n';
        }
    }

    let enumName = node.name.getText();
    text += 'public enum ' + enumName + ' {\n\n';

    // enum members
    switch (node.kind) {
        case SyntaxKind.EnumDeclaration:
            text += getEnumMembersText(node, typeNode);
            break;
        case SyntaxKind.ModuleDeclaration:
            if (SyntaxKind.ModuleBlock === node.body.kind) {
                text += getEnumConstantsText(node.body, typeNode);
            }
            break;
    }

    if (typeNode) {
        const type: string = getJavaType(typeNode);

        // value field
        text += indent + 'private final ' + type + ' ' + valueNameFor(type) + ';\n\n';

        // constructor
        text += indent + enumName + '(' + type + ' ' + valueNameFor(type) + ') {\n';
        text += indent.repeat(2) + 'this.' + valueNameFor(type) +' = ' + valueNameFor(type) + ';\n';
        text += indent + '}\n\n';

        // value getter
        text += indent + 'public ' + type + ' ' + getterNameFor(valueNameFor(type), type) + '() {\n';
        text += indent.repeat(2) + 'return ' + valueNameFor(type) + ';\n';
        text += indent + '}\n\n';

        // reverse lookup
        text += indent + 'private static final Map<' + getBoxedType(typeNode) + ', ' + enumName + '> lookup = new HashMap<>();\n\n';
        text += indent + 'static {\n';
        text += indent.repeat(2) + 'for (' + enumName + ' value : ' + enumName + '.values()) {\n';
        text += indent.repeat(3) + 'lookup.put(value.' + getterNameFor(valueNameFor(type), type) + '(), value);\n';
        text += indent.repeat(2) + '}\n';
        text += indent + '}\n\n';
        text += indent + 'public static ' + enumName + ' get(' + getBoxedType(typeNode) + ' ' + valueNameFor(type) + ') {\n';
        text += indent.repeat(2) + 'return lookup.get(' + valueNameFor(type) + ');\n';
        text += indent + '}\n';
    }

    text += '}\n';
    return text;
}

function getEnumMembersText(node: EnumDeclaration, type: Node): string {
    let text: string = '';
    node.members.forEach((member: EnumMember, index: number) => {
        if (index > 0) {
            text += ',\n';
        }
        let comment = getComment(member, 1);
        if (comment) {
            text += comment;
            if (comment.includes('@deprecated')) {
                text += indent + '@Deprecated\n';
            }
        }
        if (type) {
            let value: string;
            if (member.initializer && getJavaTypeForKind(member.initializer.kind) === getJavaType(type)) {
                value = member.initializer.getText().split("'").join('"');
            }
            text += indent + member.name.getText() + '(' + (value ? value : getDefaultValue(type)) + ')';
        } else {
            text += indent + member.name.getText();
        }
    });
    if (text && type) {
        text += ';\n\n';
    }
    return text;
}

function getEnumConstantsText(node: ModuleBlock, type: Node): string {
    let text: string = '';
    node.statements.forEach((stat: Statement, index: number) => {
        if (index > 0) {
            text += ',\n';
        }
        if (SyntaxKind.VariableStatement === stat.kind) {
            let comment = getComment(<VariableStatement>stat, 1);
            if (comment) {
                text += comment;
                if (comment.includes('@deprecated')) {
                    text += indent + '@Deprecated\n';
                }
            }
            (stat as VariableStatement).declarationList.declarations.forEach((varDecl: VariableDeclaration) => {
                let value: string;
                if (varDecl.initializer && (!varDecl.type || getJavaType(varDecl.initializer) === getJavaType(findType(varDecl.type)))) {
                    value = varDecl.initializer.getText().split("'").join('"');
                }
                text += indent + varDecl.name.getText() + '(' + (value ? value : getDefaultValue(findType(varDecl.type ? varDecl.type : varDecl.initializer))) + ')';
            });
        }
    });
    if (text && type) {
        text += ';\n\n';
    }
    return text;
}

function getEnumValueType(node: EnumDeclaration): Node | undefined {
    let type: Node;
    let ok: boolean = true;
    node.members.forEach((member: EnumMember) => {
        if (member.initializer) {
            let declType = findType(member.initializer);
            if (type) {
                if (type !== declType) {
                    if (declType && type.kind === declType.kind) {
                        if (SyntaxKind.LiteralType === declType.kind && (type as LiteralTypeNode).literal.kind !== (declType as LiteralTypeNode).literal.kind) {
                            ok = false;
                        }
                    } else {
                        ok = false;
                    }
                }
            } else if (ok) {
                type = declType;
            }
        }
    });
    return ok ? type : undefined;
}

function getPossibleEnumValueType(node: ModuleBlock): Node | undefined {
    let type: Node;
    let ok: boolean = true;
    node.statements.forEach((stat: Statement) => {
        if (SyntaxKind.VariableStatement === stat.kind) {
            (stat as VariableStatement).declarationList.declarations.forEach((varDecl: VariableDeclaration) => {
                let declType = findType(varDecl.type ? varDecl.type : varDecl.initializer);
                if (type) {
                    if (type !== declType) {
                        if (declType && type.kind === declType.kind) {
                            switch (type.kind) {
                                case SyntaxKind.StringKeyword:
                                case SyntaxKind.StringLiteral:
                                case SyntaxKind.NumberKeyword:
                                case SyntaxKind.NumericLiteral:
                                case SyntaxKind.BooleanKeyword:
                                case SyntaxKind.TrueKeyword:
                                case SyntaxKind.FalseKeyword:
                                    break;
                                case SyntaxKind.LiteralType:
                                    if ((type as LiteralTypeNode).literal.kind !== (declType as LiteralTypeNode).literal.kind) {
                                        ok = false;
                                    }
                                    break;
                                default:
                                    ok = false;
                            }
                        } else {
                            ok = false;
                        }
                    }
                } else if (ok) {
                    type = declType;
                }
            });
        } else {
            ok = false;
        }
    });
    return ok ? type : undefined;
}

function getComment(node: HasJSDoc, indentLevel: number): string | undefined {
    let comment: string | undefined;
    const doc = (node as any).jsDoc;
    if (Array.isArray(doc)) {
        doc.forEach((n: Node) => {
            if (SyntaxKind.JSDocComment === n.kind) {
                let commentText = (n as JSDoc).getText();
                if (commentText) {
                    if (!commentText.includes('.')) {
                        const trimmed = commentText.slice(0, commentText.length - 2).trim();
                        commentText = commentText.slice(0, trimmed.length) + '.' + commentText.slice(trimmed.length);
                    }
                    comment = '';
                    commentText.split('\n').forEach((line: string) => {
                        let trimmedLine = line.trim();
                        comment += indent.repeat(indentLevel) + (trimmedLine.startsWith('*') ? ' ' : '') + trimmedLine + '\n';
                    });
                }
            }
        });
    }
    return comment;
}

function findType(node: Node): Node {
    if (node) {
        switch (node.kind) {
            case SyntaxKind.TypeReference:
                const typeName = (node as TypeReferenceNode).typeName.getText();
                if (!enumTypes.has(typeName) && !unionTypes.has(typeName) && !intersected.has(typeName)) {
                    let alias: TypeAliasDeclaration = typeAliases.get(typeName);
                    if (alias) {
                        return findType(alias.type);
                    }
                }
                break;
            case SyntaxKind.ParenthesizedType:
                return findType((node as ParenthesizedTypeNode).type);
        }
    }
    return node;
}

function commonType(node: UnionTypeNode): Node {
    let common: Node;
    node.types.forEach((typeNode: TypeNode) => {
        let type: Node = findType(typeNode);
        if (type.kind === SyntaxKind.UnionType) {
            type = commonType(<UnionTypeNode>type);
        }
        if (type === null) {
            common = null;
        } else if (common === undefined) {
            common = type;
        } else if (common != null && SyntaxKind.UndefinedKeyword !== type.kind && SyntaxKind.NullKeyword !== type.kind && !sameType(common, type)) {
            common = null;
        }
    });
    return common;
}

function checkUnion(node: UnionTypeNode): {typeRef: TypeReferenceNode; enumRef: TypeReferenceNode; arrType: ArrayTypeNode} {
    const ret = {typeRef: undefined, enumRef: undefined, arrType: undefined};
    node.types.forEach((tn: TypeNode) => {
        let t: Node = findType(tn);
        switch (t.kind) {
            case SyntaxKind.TypeReference:
                if (enumTypes.has((t as TypeReferenceNode).typeName.getText())) {
                    if (ret.enumRef === undefined) {
                        ret.enumRef = <TypeReferenceNode>t;
                    } else if (ret.enumRef != null && !sameType(ret.enumRef, t)) {
                        ret.enumRef = null;
                    }
                } else {
                    if (ret.typeRef === undefined) {
                        ret.typeRef = <TypeReferenceNode>t;
                    } else if (ret.typeRef != null && !sameType(ret.typeRef, t)) {
                        ret.typeRef = null;
                    }
                }
                break;
            case SyntaxKind.ArrayType:
                if (ret.arrType === undefined) {
                    ret.arrType = <ArrayTypeNode>t;
                } else if (ret.arrType != null && !sameType(ret.arrType.elementType, (t as ArrayTypeNode).elementType)) {
                    ret.arrType = null;
                }
                break;
            case SyntaxKind.UnionType:
                let nested = commonType(<UnionTypeNode>t);
                if (nested) {
                    if (nested.kind === SyntaxKind.TypeReference) {
                        if (enumTypes.has((nested as TypeReferenceNode).typeName.getText())) {
                            if (ret.enumRef === undefined) {
                                ret.enumRef = nested;
                            } else if (ret.enumRef != null && !sameType(ret.enumRef, nested)) {
                                ret.enumRef = null;
                            }
                        } else {
                            if (ret.typeRef === undefined) {
                                ret.typeRef = nested;
                            } else if (ret.typeRef != null && !sameType(ret.typeRef, nested)) {
                                ret.typeRef = null;
                            }
                        }
                    } else if (nested.kind === SyntaxKind.UnionType) {
                        if (ret.arrType === undefined) {
                            ret.arrType = nested;
                        } else if (ret.arrType != null && !sameType(ret.arrType.elemType, (nested as ArrayTypeNode).elementType)) {
                            ret.arrType = null;
                        }
                    }
                } else {
                    ret.typeRef = null;
                    ret.enumRef = null;
                    ret.arrType = null;
                }
                break;
        }
    });
    return ret;
}

function getDefaultValue(node: Node): string {
    switch(node.kind) {
        case SyntaxKind.StringKeyword:
            return 'null';
        case SyntaxKind.LiteralType:
            return (node as LiteralTypeNode).literal.getText().split("'").join('"');
        default:
            throw new Error('Unexpected type: ' + SyntaxKind[node.kind]);
    }
}

function getJavaType(node: Node): string {
    switch(node.kind) {
        case SyntaxKind.TypeReference:
            return (node as TypeReferenceNode).typeName.getText();
        case SyntaxKind.ParenthesizedType:
            return getJavaType((node as ParenthesizedTypeNode).type);
        case SyntaxKind.ArrayType:
            toImport.hasArray = true;
            return 'List<' + getBoxedType(findType((node as ArrayTypeNode).elementType)) + '>';
        case SyntaxKind.UnionType:
            let type: Node = commonType(<UnionTypeNode>node);
            let hasNull: boolean = false;
            (node as UnionTypeNode).types.forEach((tn: TypeNode) => {
                if (tn.kind === SyntaxKind.NullKeyword) {
                    hasNull = true;
                }
            })
            return type ? hasNull ? getBoxedType(type) : getJavaType(type) : 'Object';
        case SyntaxKind.LiteralType:
            return getJavaTypeForKind((node as LiteralTypeNode).literal.kind);
        case SyntaxKind.NewExpression:
            return (node as NewExpression).expression.getText();
        case SyntaxKind.PrefixUnaryExpression:
            return getJavaType((node as PrefixUnaryExpression).operand);
        case SyntaxKind.IntersectionType:
            let firstType: Node;
            (node as IntersectionTypeNode).types.forEach((tn: TypeNode) => {
                if (!firstType) {
                    firstType = tn;
                }
            });
            return getJavaType(firstType,);
        case SyntaxKind.TypeLiteral:
            let mapType = checkMap(<TypeLiteralNode>node);
            if (mapType) {
                toImport.hasMap = true;
                return 'Map<' + getBoxedType(mapType.keyType) + ', ' + getBoxedType(mapType.valueType) + '>';
            } else if (node.parent.kind === SyntaxKind.PropertySignature) {
                let name = typeLiteralNames.get(<TypeLiteralNode>node);
                if (!name) {
                    throw new Error('Unexpected type literal!');
                }
                return name;
            }
        default:
            return getJavaTypeForKind(node.kind);
    }
}

function getJavaTypeForKind(kind: SyntaxKind): string {
    switch (kind) {
        case SyntaxKind.StringKeyword:
        case SyntaxKind.StringLiteral:
            return 'String';
        case SyntaxKind.NumberKeyword:
        case SyntaxKind.NumericLiteral:
            let nt = numberTypes.get(clsName);
            switch(nt) {
                case 'long':
                case 'float':
                case 'double':
                    return nt;
            }
            return 'int';
        case SyntaxKind.BooleanKeyword:
        case SyntaxKind.TrueKeyword:
        case SyntaxKind.FalseKeyword:
            return 'boolean';
        case SyntaxKind.VoidKeyword:
            return 'void';
        case SyntaxKind.AnyKeyword:
        case SyntaxKind.ObjectKeyword:
            return 'Object';
        default:
            throw new Error('Unexpected type: ' + SyntaxKind[kind]);
        }
}

function getBoxedType(node: Node): string {
    const type = getBoxedTypeForKind(node.kind === SyntaxKind.LiteralType ? (node as LiteralTypeNode).literal.kind : node.kind);
    return type ? type : getJavaType(node);
}

function getBoxedTypeForKind(kind: SyntaxKind): string | undefined {
    switch (kind) {
        case SyntaxKind.NumberKeyword:
        case SyntaxKind.NumericLiteral:
            let nt = numberTypes.get(clsName);
            switch(nt) {
                case 'long':
                    return 'Long';
                case 'float':
                    return 'Float';
                case 'double':
                    return 'Double';
            }
            return 'Integer';
        case SyntaxKind.TypePredicate:
        case SyntaxKind.BooleanKeyword:
        case SyntaxKind.TrueKeyword:
        case SyntaxKind.FalseKeyword:
            return 'Boolean';
        case SyntaxKind.VoidKeyword:
            return 'Void';
    }
}

function getEquals(node: Node, optional: boolean): string {
    switch (node.kind) {
        case SyntaxKind.NumberKeyword:
        case SyntaxKind.NumericLiteral:
            if (optional) {
                toImport.hasObjects = true;
                return 'Objects.equals';
            }
            return undefined;
        case SyntaxKind.TypePredicate:
        case SyntaxKind.BooleanKeyword:
        case SyntaxKind.TrueKeyword:
        case SyntaxKind.FalseKeyword:
            if (optional) {
                toImport.hasObjects = true;
                return 'Objects.equals';
            }
            return undefined;
        case SyntaxKind.TypeReference:
            if (enumTypes.has(getJavaType(node))) {
                return undefined;
            }
        default:
            toImport.hasObjects = true;
            return 'Objects.equals';
    }
}

function getHash(node: Node): string {
    switch (node.kind) {
        case SyntaxKind.NumberKeyword:
        case SyntaxKind.NumericLiteral:
            let nt = numberTypes.get(clsName);
            switch(nt) {
                case 'long':
                    return 'Long.hashCode';
                case 'float':
                    return 'Float.hashCode';
                case 'double':
                    return 'Double.hashCode';
            }
            return 'Integer.hashCode';
        case SyntaxKind.TypePredicate:
        case SyntaxKind.BooleanKeyword:
        case SyntaxKind.TrueKeyword:
        case SyntaxKind.FalseKeyword:
            return 'Boolean.hashCode';
        default:
            toImport.hasObjects = true;
            return 'Objects.hashCode';
    }
}

function varNameFor(type: string): string {
    return type.charAt(0).toLowerCase() + type.slice(1);
}

function valueNameFor(type: string): string {
    return varNameFor(type) + 'Value';
}

function unionInfoGetter(unionInfo:{typeRef: TypeReferenceNode; enumRef: TypeReferenceNode; arrType: ArrayTypeNode}, varName: string, resultPrefix: string, resultSuffix:string, hasNull: boolean, baseIndent: number): string {
    let text: string = '';
    if (unionInfo.typeRef) {
        text += indent.repeat(baseIndent) + 'if (' + varName + ' instanceof JSONObject) {\n';
        text += indent.repeat(baseIndent + 1) + resultPrefix + 'new ' + getJavaType(unionInfo.typeRef) + '((JSONObject) ' + varName + ')' + resultSuffix + ';\n';
        text += indent.repeat(baseIndent) + '}\n';
    }
    if (unionInfo.arrType) {
        text += indent.repeat(baseIndent) + 'if (' + varName + ' instanceof JSONArray) {\n';
        text += indent.repeat(baseIndent + 1) + 'final ' + getBoxedType(unionInfo.arrType) + ' list = new ArrayList<>(((JSONArray)' + varName + ').length());\n';
        text += indent.repeat(baseIndent + 1) + 'for (int i = 0; i < ((JSONArray)' + varName + ').length(); i++) {\n';
        let elemType: Node = findType(unionInfo.arrType.elementType);
        text += indent.repeat(baseIndent + 2) + 'list.add(' + jsonGetter('((JSONArray)' + varName + ')', 'i', elemType, false) + ');\n';
        text += indent.repeat(baseIndent + 1) + '}\n';
        text += indent.repeat(baseIndent + 1) + resultPrefix + 'Collections.unmodifiableList(list)' + resultSuffix + ';\n';
        text += indent.repeat(baseIndent) + '}\n';
    }
    if (unionInfo.enumRef) {
        let enumTypeName: string = getJavaType(unionInfo.enumRef);
        let enumVarName: string = varNameFor(enumTypeName);
        let enumType = getBoxedType(enumTypes.get(enumTypeName));
        text += indent.repeat(baseIndent) + enumTypeName + ' ' + enumVarName + ' = ' + varName + ' instanceof ' + enumType + ' ? ' + enumTypeName + '.get((' + enumType + ') ' + varName + ') : null;\n';
        text += indent.repeat(baseIndent) + resultPrefix + enumVarName + ' != null ? ' + enumVarName + ' : ' + varName + resultSuffix + ';\n';
    } else if (hasNull) {
        text += indent.repeat(baseIndent) + resultPrefix + 'JSONObject.NULL.equals(' + varName + ') ? null : ' + varName + resultSuffix + ';\n';
    } else {
        text += indent.repeat(baseIndent) + resultPrefix + varName + resultSuffix + ';\n';
    }
    return text;
}

function unionInfoSetter(unionInfo:{typeRef: TypeReferenceNode; enumRef: TypeReferenceNode; arrType: ArrayTypeNode}, jsonVarName: string, varName: string, key: string, typeNode: UnionTypeNode, hasNull: boolean, baseIndent: number): string {
    let text: string = '';
    if (hasNull) {
        text += indent.repeat(baseIndent) + 'if (' + varName + ' == null) {\n';
        text += indent.repeat(baseIndent + 1) + jsonVarName + '.put(' + key +', JSONObject.NULL);\n';
        text += indent.repeat(baseIndent) + '} ';
    }
    if (unionInfo.typeRef) {
        text += (hasNull ? 'else ' : indent.repeat(baseIndent)) + 'if (' + varName + ' instanceof ' + getJavaType(unionInfo.typeRef) + ') {\n';
        text += indent.repeat(baseIndent + 1) + jsonSetter(jsonVarName, key, '((' + getJavaType(unionInfo.typeRef) + ') ' + varName + ')', unionInfo.typeRef, false) + ';\n';
        text += indent.repeat(baseIndent) + '} ';
    }
    if (unionInfo.arrType) {
        text += (hasNull || unionInfo.typeRef ? 'else ' : indent.repeat(baseIndent)) + 'if (' + varName + ' instanceof List) {\n';
        text += indent.repeat(baseIndent + 1) + 'final JSONArray json = new JSONArray();\n';
        let elemType: Node = findType(unionInfo.arrType.elementType);
        let elemTypeName: string = getJavaType(elemType);
        text += indent.repeat(baseIndent + 1) + 'for (' + elemTypeName + ' ' + varNameFor(elemTypeName) +  ': (List<' + elemTypeName + '>) ' + varName + ') {\n';
        text += indent.repeat(baseIndent + 2) + jsonSetter('json', null, varNameFor(elemTypeName), elemType, false) + ';\n';
        text += indent.repeat(baseIndent + 1) + '}\n';
        text += indent.repeat(baseIndent + 1) + jsonSetter(jsonVarName, key, 'json', unionInfo.arrType, false) + ';\n';
        text += indent.repeat(baseIndent) + '} ';
    }
    if (unionInfo.enumRef) {
        text += (hasNull || unionInfo.typeRef || unionInfo.arrType ? 'else ' : indent.repeat(baseIndent)) + 'if (' + varName + ' instanceof ' + getJavaType(unionInfo.enumRef) + ') {\n';
        text += indent.repeat(baseIndent + 1) + jsonSetter(jsonVarName, key, '((' + getJavaType(unionInfo.enumRef) + ') ' + varName + ')', unionInfo.enumRef, false) + ';\n';
        text += indent.repeat(baseIndent) + '} ';
    }
    text += 'else {\n';
    text += indent.repeat(baseIndent + 1) + jsonSetter(jsonVarName, key, varName, typeNode, false) + ';\n';
    text += indent.repeat(baseIndent) + '}\n';
    return text;
}

function getterNameFor(name: string, type: string): string {
    if (type === 'boolean') {
        return name.startsWith('is') ? name : 'is' + name.charAt(0).toUpperCase() + name.slice(1);
    }
    return 'get' + name.charAt(0).toUpperCase() + name.slice(1);
}

function setterNameFor(name: string, type: string): string {
    if (type === 'boolean' && name.startsWith('is') && name.length > 2) {
        return 'set' + name.charAt(2).toUpperCase() + name.slice(3);
    }
    return 'set' + name.charAt(0).toUpperCase() + name.slice(1);
}

function suffixFor(name: string): string {
    for (let i = name.length - 1; i >= 0; i--) {
        if (name.charAt(i).toUpperCase() === name.charAt(i)) {
            return name.slice(i);
        }
    }
    return name.charAt(0).toUpperCase() + name.slice(1);
}

function jsonGetter(jsonVar:string, jsonKey:string, node: Node, optional: boolean): string {
    if (node) {
        switch (node.kind) {
            case SyntaxKind.ParenthesizedType:
                return jsonGetter(jsonVar, jsonKey, (node as ParenthesizedTypeNode).type, optional);
            case SyntaxKind.LiteralType:
                return jsonGetterForKind(jsonVar, jsonKey, (node as LiteralTypeNode).literal.kind, optional);
            case SyntaxKind.TypeLiteral:
                if (checkMap(<TypeLiteralNode>node)) {
                    return jsonVar + (optional ? '.optJSONObject(' : '.getJSONObject(') + jsonKey + ')';
                }
            case SyntaxKind.TypeReference:
                let enumType: Node = enumTypes.get(getJavaType(node));
                if (enumType) {
                    return getJavaType(node) + '.get(' + jsonGetter(jsonVar, jsonKey, enumType, optional) + ')';
                } else {
                    return (optional ? jsonVar + '.has(' + jsonKey +') ? ' : '') + 'new ' + getJavaType(node) + '(' + jsonGetterForKind(jsonVar, jsonKey, node.kind, optional) + ')' + (optional ? ' : null' : '');
                }
            case SyntaxKind.IntersectionType:
                let firstType: Node;
                (node as IntersectionTypeNode).types.forEach((tn: TypeNode) => {
                    if (!firstType) {
                        firstType = findType(tn);
                    }
                });
                return jsonGetter(jsonVar, jsonKey, firstType, optional);
            case SyntaxKind.UnionType:
                let type: Node = commonType(<UnionTypeNode>node);
                if (type != null) {
                    return jsonGetter(jsonVar, jsonKey, type, optional);
                }
                return jsonGetterForKind(jsonVar, jsonKey, node.kind, optional);
            default:
                return jsonGetterForKind(jsonVar, jsonKey, node.kind, optional);
        }
    }
    return jsonVar + (optional ? '.opt(' : '.get(') + jsonKey + ')';
}

function jsonGetterForKind(jsonVar:string, jsonKey:string, kind: SyntaxKind, optional: boolean): string {
    switch(kind) {
        case SyntaxKind.NumberKeyword:
        case SyntaxKind.NumericLiteral:
            let nt = numberTypes.get(clsName);
            switch(nt) {
                case 'long':
                    return optional ? jsonVar + '.has(' + jsonKey +') ? ' + jsonVar +'.getLong(' + jsonKey + ') : null'
                                    : jsonVar +'.getLong(' + jsonKey + ')';
                case 'float':
                    return optional ? jsonVar + '.has(' + jsonKey +') ? ' + jsonVar +'.getFloat(' + jsonKey + ') : null'
                                    : jsonVar +'.getFloat(' + jsonKey + ')';
                case 'double':
                    return optional ? jsonVar + '.has(' + jsonKey +') ? ' + jsonVar +'.getDouble(' + jsonKey + ') : null'
                                    : jsonVar +'.getDouble(' + jsonKey + ')';
            }
            return optional ? jsonVar + '.has(' + jsonKey +') ? ' + jsonVar +'.getInt(' + jsonKey + ') : null'
                            : jsonVar +'.getInt(' + jsonKey + ')';
        case SyntaxKind.BooleanKeyword:
        case SyntaxKind.TrueKeyword:
        case SyntaxKind.FalseKeyword:
            return optional ? jsonVar + '.has(' + jsonKey +') ? ' + jsonVar +'.getBoolean(' + jsonKey + ') : null'
                            : jsonVar +'.getBoolean(' + jsonKey + ')';
        case SyntaxKind.StringKeyword:
        case SyntaxKind.StringLiteral:
            return optional ? jsonVar + '.optString(' + jsonKey + ', null)'
                            : jsonVar + '.getString(' + jsonKey + ')';
        case SyntaxKind.ArrayType:
            return jsonVar + (optional ? '.optJSONArray(' : '.getJSONArray(') + jsonKey + ')';
        case SyntaxKind.TypeLiteral:
        case SyntaxKind.TypeReference:
            return jsonVar + (optional ? '.optJSONObject(' : '.getJSONObject(') + jsonKey + ')';
        case SyntaxKind.AnyKeyword:
        case SyntaxKind.ObjectKeyword:
        case SyntaxKind.UnionType:
            return jsonVar + (optional ? '.opt(' : '.get(') + jsonKey + ')';
        default:
            throw new Error('Unexpected type: ' + SyntaxKind[kind]);
    }
}


function jsonSetter(jsonVar:string, jsonKey:string, varName: string, node: Node, optional: boolean): string {
    if (node) {
        switch(node.kind) {
            case SyntaxKind.ParenthesizedType:
                return jsonSetter(jsonVar, jsonKey, varName, (node as ParenthesizedTypeNode).type, optional);
            case SyntaxKind.TypeLiteral:
                if (checkMap(<TypeLiteralNode>node)) {
                    return jsonVar + (optional ? '.putOpt(' : '.put(') + (jsonKey ? jsonKey + ', ' : '')  + varName + ')';
                }
            case SyntaxKind.TypeReference:
                let enumType: Node = enumTypes.get(getJavaType(node));
                if (enumType) {
                    if (optional) {
                        return jsonVar + '.putOpt(' + (jsonKey ? jsonKey + ', ' : '') + varName + ' != null ? ' + varName + '.' + getterNameFor(valueNameFor(getJavaType(enumType)), getJavaType(enumType)) + '() : null)';
                    }
                    return jsonVar + '.put(' + (jsonKey ? jsonKey + ', ' : '') + varName + '.' + getterNameFor(valueNameFor(getJavaType(enumType)), getJavaType(enumType)) + '())';
                }
                if (optional) {
                    return jsonVar + '.putOpt(' + (jsonKey ? jsonKey + ', ' : '') + varName + ' != null ? ' + varName + '.jsonData : null)';
                }
                return jsonVar + '.put(' + (jsonKey ? jsonKey + ', ' : '') + varName + '.jsonData)';
            case SyntaxKind.IntersectionType:
                let firstType: Node;
                (node as IntersectionTypeNode).types.forEach((tn: TypeNode) => {
                    if (!firstType) {
                        firstType = findType(tn);
                    }
                });
                return jsonSetter(jsonVar, jsonKey, varName, firstType, optional);
            case SyntaxKind.UnionType:
                let type: Node = commonType(<UnionTypeNode>node);
                if (type != null) {
                    return jsonSetter(jsonVar, jsonKey, varName, type, optional);
                }
            default:
                return jsonVar + (optional ? '.putOpt(' : '.put(') + (jsonKey ? jsonKey + ', ' : '') + varName + ')';
        }
    }
    return jsonVar + (optional ? '.putOpt(' : '.put(') + (jsonKey ? jsonKey + ', ' : '') + varName + ')';
}

function match(props: Map<string, PropertySignature>, params: Map<string, ParameterDeclaration>): boolean {
    let ret = true;
    props.forEach((prop: PropertySignature, propName: string) => {
        let propType = findType(prop.type);
        let param: ParameterDeclaration = params.get(propName);
        if (param) {
            if (!assignable(findType(param.type), propType)) {
                ret = false;
            }
        } else if (propType.kind !== SyntaxKind.LiteralType && !prop.questionToken) {
            let cnt = 0;
            params.forEach((p: ParameterDeclaration) => {
                if (sameType(propType, findType(p.type))) {
                    cnt++;
                }
            });
            if (cnt !== 1) {
                ret = false;
            }
        }
    });
    return ret;
}

function sameType(typeA: Node, typeB: Node): boolean {
    if (typeA.kind !== typeB.kind) {
        return false;
    }
    switch (typeA.kind) {
        case SyntaxKind.TypeReference:
            if ((typeA as TypeReferenceNode).typeName.getText() !== (typeB as TypeReferenceNode).typeName.getText()) {
                return false;
            }
            break;
    }
    return true;
}

function assignable(from: Node, to: Node): boolean {
    if (from.kind === SyntaxKind.UnionType) {
        let type: Node = commonType(<UnionTypeNode>from);
        if (type) {
            from = type;
        }
    }
    if (sameType(from, to)) {
        return true;
    }
    let ret: boolean = false;
    if (to.kind === SyntaxKind.UnionType) {
        (to as UnionTypeNode).types.forEach((type: TypeNode) => {
            if (sameType(from, type)) {
                ret = true;
            }
        });
    }
    return ret;
}

function checkMap(node: TypeLiteralNode): {keyType: Node, valueType: Node} {
    let mapType: {keyType: Node, valueType: Node};
    if (node.members.length === 1) {
        node.members.forEach((member: TypeElement) => {
            if (member.kind === SyntaxKind.IndexSignature) {
                if ((member as IndexSignatureDeclaration).parameters.length === 1) {
                    (member as IndexSignatureDeclaration).parameters.forEach((param: ParameterDeclaration) => {
                        mapType = {keyType: findType(param.type), valueType: findType((member as IndexSignatureDeclaration).type)};
                    });
                }
            }
        });
    }
    return mapType;
}

function getDeclForType(node: TypeNode): InterfaceDeclaration {
    if (node.kind === SyntaxKind.TypeReference) {
        const refName: string = (node as TypeReferenceNode).typeName.getText();
        const refIntersected: (InterfaceDeclaration | TypeLiteralNode)[] = intersected.get(refName);
        if (refIntersected) {
            return refIntersected[0] as InterfaceDeclaration;
        }
        const ref = interfaces.get(refName);
        if (ref) {
            interfaces.delete(refName);
            return ref;
        }
    }
    return undefined;
}

function mergeIntersected(arr: InterfaceDeclaration[]): void {
    const members: TypeElement[] = [];
    arr.forEach((decl: InterfaceDeclaration) => {
        decl.members.forEach((el: TypeElement) => {
            if (el.kind === SyntaxKind.PropertySignature) {
                let m = members.find((m: TypeElement) => m.name && el.name && m.name.getText() === el.name.getText());
                if (m) {
                    if (m.kind === SyntaxKind.PropertySignature) {
                        if ((m as PropertySignature).type.kind === SyntaxKind.TypeReference) {
                            const refName: string = ((m as PropertySignature).type as TypeReferenceNode).typeName.getText();
                            const mDecl = interfaces.get(refName);
                            if (mDecl) {
                                let mDeclName: string = mDecl.name.getText();
                                let iArr = intersected.get(mDeclName);
                                if (!iArr) {
                                    iArr = [];
                                    intersected.set(mDeclName, iArr);
                                }
                                let t: TypeNode = (el as PropertySignature).type;
                                if (t.kind === SyntaxKind.TypeLiteral) {
                                    iArr.push(t as TypeLiteralNode);
                                }
                            }
                        }
                    }
                } else {
                    members.push(el);
                }
            }
        });
    });
}

function getMembers(decls: (InterfaceDeclaration | TypeLiteralNode)[], optionalProperties?:Set<PropertySignature>): TypeElement[] {
    const members: TypeElement[] = [];
    let optionalNames = optionalProperties ? new Set<string>() : undefined;
    let previousNames: Set<string> = undefined;
    let currentNames: Set<string> = undefined;
    decls.forEach((decl: InterfaceDeclaration | TypeLiteralNode) => {
        if (optionalNames) {
            currentNames = new Set<string>();
        }
        decl.members.forEach((el: TypeElement) => {
            if (el.kind === SyntaxKind.PropertySignature) {
                if (optionalNames) {
                    currentNames.add(el.name.getText());
                    if (previousNames) {
                        previousNames.delete(el.name.getText());
                    }
                    if (el.questionToken !== undefined) {
                        optionalNames.add(el.name.getText());
                    }
                }
                let idx = members.findIndex((m: TypeElement) => m.name && m.name.getText() === el.name.getText());
                if (members[idx] && members[idx].kind === SyntaxKind.PropertySignature) {
                    if ((members[idx] as PropertySignature).type.kind === SyntaxKind.ObjectKeyword || (members[idx] as PropertySignature).type.kind === SyntaxKind.AnyKeyword) {
                        members[idx] = el;
                    }
                } else {
                    members.push(el);
                }
            } else {
                members.push(el);
            }
        });
        if (optionalNames) {
            if (previousNames) {
                optionalNames = new Set([...optionalNames, ...previousNames]);
            }
            previousNames = currentNames;
        }
    });
    if (optionalNames) {
        members.forEach((member: TypeElement) => {
            if (member.kind === SyntaxKind.PropertySignature && optionalNames.has(member.name.getText())) {
                optionalProperties.add(member as PropertySignature);
            }
        });
    }
    return members;
}

function findProperties(decls: (InterfaceDeclaration | TypeLiteralNode)[], props: Map<string, PropertySignature>, nested: Set<TypeLiteralNode>): void {
    getMembers(decls).forEach((member: TypeElement) => {
        if (member.kind === SyntaxKind.PropertySignature) {
            let memberName: string = member.name.getText();
            if (!props.has(memberName)) {
                props.set(memberName, member as PropertySignature);
            }
            if (nested) {
                let memberType = (member as PropertySignature).type;
                if (memberType && memberType.kind === SyntaxKind.TypeLiteral && !checkMap(<TypeLiteralNode>memberType)) {
                    nested.add(<TypeLiteralNode>memberType);
                }
            }
        }
    });
    if ((decls[0] as InterfaceDeclaration).heritageClauses) {
        (decls[0] as InterfaceDeclaration).heritageClauses.forEach((clause: HeritageClause) => {
            if (clause.token === SyntaxKind.ExtendsKeyword) {
                clause.types.forEach((expr: ExpressionWithTypeArguments) => {
                    const extName = expr.expression.getText();
                    let extDecl: InterfaceDeclaration = interfaces.get(extName);
                    if (extDecl) {
                        let hasMethods: boolean = false;
                        traverse([extDecl], (n: Node) => {
                            switch (n.kind) {
                                case SyntaxKind.MethodDeclaration:
                                case SyntaxKind.MethodSignature:
                                    hasMethods = true;
                            }
                        });
                        if (!hasMethods) {
                            findProperties([extDecl], props, null);
                        }
                    }
                });
            }
        });
    }
}

function getDepth(decl: InterfaceDeclaration): number {
    let max: number = 0;
    if (decl.heritageClauses) {
        decl.heritageClauses.forEach((clause: HeritageClause) => {
            if (clause.token === SyntaxKind.ExtendsKeyword) {
                clause.types.forEach((expr: ExpressionWithTypeArguments) => {
                    let extDecl: InterfaceDeclaration = interfaces.get(expr.expression.getText());
                    if (extDecl) {
                        let depth = getDepth(extDecl) + 1;
                        if (depth > max) {
                            max = depth;
                        }
                    }
                });
            }
        });
    }
    return max;
}

function generatePrimeNumber(min: number, max: number): number {
    let proposed: number = getRandomInt(min, max);
    while (!isPrime(proposed)) {
        proposed++;
    }
    if (proposed > max) {
        proposed--;
        while (!isPrime(proposed)) {
            proposed--;
        }
    }
    return proposed;
}

function getRandomInt(min: number, max: number): number {
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min)) + min;
}

function isPrime(num: number): boolean {
    for (let i = 2, s = Math.sqrt(num); i <= s; i++) {
        if (num % i === 0) {
            return false;
        }
    }
    return num > 1;
}
