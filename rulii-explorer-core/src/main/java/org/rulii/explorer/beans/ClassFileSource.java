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
package org.rulii.explorer.beans;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Where {@link BeanDiscovery} reads the class files of a package from. The default lists what a
 * class loader's resources reach: directories and plain jars. The Spring Boot starter plugs in
 * Spring's resource resolver so nested Boot jars work too.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
@FunctionalInterface
public interface ClassFileSource {

    /**
     * Hands the bytes of every class file under the package, sub-packages included, to the reader.
     *
     * @param basePackage the package, {@code com.acme.order}.
     * @param reader      receives each file's bytes once.
     * @throws IOException when the listing cannot be read.
     */
    void read(String basePackage, Consumer<byte[]> reader) throws IOException;

    /**
     * The class files a class loader's resources reach.
     *
     * @param loader the loader; must not be null.
     * @return the source.
     */
    static ClassFileSource of(ClassLoader loader) {
        Objects.requireNonNull(loader, "loader cannot be null.");
        return new ClassLoaderClassFiles(loader);
    }

    /**
     * Directories and plain jars reachable through {@link ClassLoader#getResources(String)}.
     */
    final class ClassLoaderClassFiles implements ClassFileSource {

        private static final String CLASS = ".class";

        private final ClassLoader loader;

        private ClassLoaderClassFiles(ClassLoader loader) {
            super();
            this.loader = loader;
        }

        @Override
        public void read(String basePackage, Consumer<byte[]> reader) throws IOException {
            String path = basePackage.replace('.', '/');
            Enumeration<URL> roots = loader.getResources(path);

            while (roots.hasMoreElements()) {
                URL root = roots.nextElement();
                if ("file".equals(root.getProtocol())) {
                    directory(Path.of(toUri(root)), reader);
                } else if ("jar".equals(root.getProtocol())) {
                    jar(root, path, reader);
                }
            }
        }

        private static java.net.URI toUri(URL url) throws IOException {
            try {
                return url.toURI();
            } catch (java.net.URISyntaxException e) {
                throw new IOException("cannot read [" + url + "].", e);
            }
        }

        private static void directory(Path root, Consumer<byte[]> reader) throws IOException {
            if (!Files.isDirectory(root)) return;
            List<Path> files;
            try (Stream<Path> walk = Files.walk(root)) {
                files = walk.filter(p -> p.getFileName().toString().endsWith(CLASS)).sorted().toList();
            }
            for (Path file : files) {
                reader.accept(Files.readAllBytes(file));
            }
        }

        private static void jar(URL root, String path, Consumer<byte[]> reader) throws IOException {
            JarURLConnection connection = (JarURLConnection) root.openConnection();
            connection.setUseCaches(false);
            try (JarFile jar = connection.getJarFile()) {
                String prefix = path.endsWith("/") ? path : path + "/";
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().startsWith(prefix) || !entry.getName().endsWith(CLASS)) continue;
                    try (InputStream in = jar.getInputStream(entry)) {
                        reader.accept(in.readAllBytes());
                    }
                }
            }
        }
    }
}
