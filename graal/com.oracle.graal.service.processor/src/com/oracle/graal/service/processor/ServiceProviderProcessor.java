/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.service.processor;

import java.io.*;
import java.util.*;

import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import javax.tools.Diagnostic.Kind;
import javax.tools.*;

import com.oracle.graal.api.runtime.*;

@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedAnnotationTypes("com.oracle.graal.api.runtime.ServiceProvider")
public class ServiceProviderProcessor extends AbstractProcessor {

    private Map<String, Set<TypeElement>> serviceMap;

    public ServiceProviderProcessor() {
        serviceMap = new HashMap<>();
    }

    private void addProvider(String serviceName, TypeElement serviceProvider) {
        Set<TypeElement> providers = serviceMap.get(serviceName);
        if (providers == null) {
            providers = new HashSet<>();
            serviceMap.put(serviceName, providers);
        }
        providers.add(serviceProvider);
    }

    private void generateServicesFiles() {
        Filer filer = processingEnv.getFiler();
        for (Map.Entry<String, Set<TypeElement>> entry : serviceMap.entrySet()) {
            String filename = "META-INF/services/" + entry.getKey();
            TypeElement[] providers = entry.getValue().toArray(new TypeElement[0]);
            try {
                FileObject servicesFile = filer.createResource(StandardLocation.CLASS_OUTPUT, "", filename, providers);
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(servicesFile.openOutputStream(), "UTF-8"));
                for (TypeElement provider : providers) {
                    writer.println(provider.getQualifiedName());
                }
                writer.close();
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(Kind.ERROR, e.getMessage());
            }
        }
    }

    private boolean verifyAnnotation(TypeMirror serviceInterface, TypeElement serviceProvider) {
        if (!processingEnv.getTypeUtils().isSubtype(serviceProvider.asType(), serviceInterface)) {
            String msg = String.format("Service provider class %s doesn't implement service interface %s", serviceProvider.getSimpleName(), serviceInterface);
            processingEnv.getMessager().printMessage(Kind.ERROR, msg, serviceProvider);
            return false;
        }

        return true;
    }

    private void processElement(TypeElement serviceProvider) {
        ServiceProvider annotation = serviceProvider.getAnnotation(ServiceProvider.class);
        if (annotation != null) {
            try {
                annotation.value();
            } catch (MirroredTypeException ex) {
                TypeMirror serviceInterface = ex.getTypeMirror();
                if (verifyAnnotation(serviceInterface, serviceProvider)) {
                    String interfaceName = ex.getTypeMirror().toString();
                    addProvider(interfaceName, serviceProvider);
                }
            }
        }
    }

    private void processOldElements() {
        Filer filer = processingEnv.getFiler();
        Elements elements = processingEnv.getElementUtils();
        for (String key : serviceMap.keySet()) {
            String filename = "META-INF/services/" + key;
            try {
                FileObject servicesFile = filer.getResource(StandardLocation.CLASS_OUTPUT, "", filename);
                BufferedReader reader = new BufferedReader(new InputStreamReader(servicesFile.openInputStream(), "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) {
                    TypeElement serviceProvider = elements.getTypeElement(line);
                    if (serviceProvider != null) {
                        processElement(serviceProvider);
                    }
                }
                reader.close();
            } catch (IOException e) {
                // old services file not found: do nothing
            }
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            processOldElements();
            generateServicesFiles();
            return true;
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(ServiceProvider.class)) {
            assert element.getKind().isClass();
            processElement((TypeElement) element);
        }

        return true;
    }
}
