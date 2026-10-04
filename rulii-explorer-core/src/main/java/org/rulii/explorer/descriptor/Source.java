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
package org.rulii.explorer.descriptor;

/**
 * Where an artifact was declared. Omitted from the descriptor when sources are hidden (NFR-4).
 *
 * @param type       xml or java.
 * @param resource   the XML file ({@code classpath:rules/order.xml}) or the Java source file name; null when unknown.
 * @param line       1-based line; null when unknown.
 * @param className  the declaring class (java only); null when unknown.
 * @param methodName the declaring method, such as a {@code @Bean} method (java only); null when unknown.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public record Source(SourceType type, String resource, Integer line, String className, String methodName) {
}
