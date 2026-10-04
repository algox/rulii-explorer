/*
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright (c) 1999-2026, Algorithmx Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rulii.explorer.builder;

import org.rulii.explorer.descriptor.ArtifactKind;
import org.rulii.explorer.descriptor.PackageKind;
import org.rulii.explorer.descriptor.Source;
import org.rulii.explorer.descriptor.SourceType;
import org.rulii.model.SourceDefinition;

/**
 * Turns a rulii {@link SourceDefinition} into the descriptor's {@link Source}, and works out
 * the package an artifact belongs to (FR-30).
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
final class Sources {

    private static final String NOT_AVAILABLE = "n/a";

    private Sources() {
        super();
    }

    /**
     * @param definition the rulii source; may be null.
     * @return the source, or null when nothing useful is known.
     */
    static Source of(SourceDefinition definition) {
        if (definition == null) return null;

        String className = clean(definition.getClassName());
        String methodName = clean(definition.getMethodName());
        String fileName = clean(definition.getFileName());
        Integer line = definition.getLineNumber();

        if (isXml(definition)) return new Source(SourceType.XML, fileName, line, null, null);
        if (className == null && fileName == null) return null;
        return new Source(SourceType.JAVA, fileName, line, className, methodName);
    }

    /** An XML source has a file and no class. */
    static boolean isXml(SourceDefinition definition) {
        return definition != null && clean(definition.getClassName()) == null && clean(definition.getFileName()) != null;
    }

    /**
     * The package of an artifact: the rule class package, the declaring class package, or the
     * XML resource folder.
     *
     * @param definition the rulii source; may be null.
     * @param ruleClass  the rule class of a class-based rule; may be null.
     * @return the package id, or null when unknown.
     */
    static String packageOf(SourceDefinition definition, Class<?> ruleClass) {
        if (ruleClass != null && !Rulii.isInternalClass(ruleClass)) return packageOf(ruleClass.getName());
        if (definition == null) return null;
        if (isXml(definition)) return folderOf(clean(definition.getFileName()));
        return packageOf(clean(definition.getClassName()));
    }

    static PackageKind packageKind(ArtifactKind kind) {
        return kind == ArtifactKind.XML || kind == ArtifactKind.XML_SCRIPT ? PackageKind.XML : PackageKind.JAVA;
    }

    private static String packageOf(String className) {
        if (className == null) return null;
        int dot = className.lastIndexOf('.');
        return dot > 0 ? className.substring(0, dot) : null;
    }

    /** {@code classpath:rules/order/validation.xml} becomes {@code rules/order}. */
    static String folderOf(String resource) {
        if (resource == null) return null;
        String path = resource;
        int colon = path.indexOf(':');
        if (colon >= 0) path = path.substring(colon + 1);
        while (path.startsWith("/")) path = path.substring(1);
        int slash = path.lastIndexOf('/');
        return slash > 0 ? path.substring(0, slash) : "(root)";
    }

    private static String clean(String value) {
        return value == null || value.isBlank() || NOT_AVAILABLE.equals(value) ? null : value;
    }
}
