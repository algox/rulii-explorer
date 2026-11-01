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
package org.rulii.explorer.boot;

import org.rulii.explorer.beans.ClassFileSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The class files of a package as Spring's resource resolver lists them: directories, jars and
 * the nested jars of a packaged Boot application, which a plain class loader's resources do not
 * reach. The same listing Spring's component scan uses.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public final class ResourceClassFileSource implements ClassFileSource {

    private final ResourcePatternResolver resolver;

    /**
     * @param resourceLoader the application's resource loader; must not be null.
     */
    public ResourceClassFileSource(ResourceLoader resourceLoader) {
        super();
        Objects.requireNonNull(resourceLoader, "resourceLoader cannot be null.");
        this.resolver = resourceLoader instanceof ResourcePatternResolver patternResolver ? patternResolver
                : ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
    }

    /**
     * A source over the given class loader, through a {@link PathMatchingResourcePatternResolver}.
     *
     * @param loader the loader; must not be null.
     */
    public ResourceClassFileSource(ClassLoader loader) {
        this(new PathMatchingResourcePatternResolver(Objects.requireNonNull(loader, "loader cannot be null.")));
    }

    @Override
    public void read(String basePackage, Consumer<byte[]> reader) throws IOException {
        String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + basePackage.replace('.', '/') + "/**/*.class";
        for (Resource resource : resolver.getResources(pattern)) {
            if (!resource.isReadable()) continue;
            try (InputStream in = resource.getInputStream()) {
                reader.accept(in.readAllBytes());
            }
        }
    }
}
