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

import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.SpringAsmInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Finds the classes under a package that rulii-bean-validation would read something from
 * (section 17.3): each class file is read once with a byte-level visitor, so the JVM loads
 * nothing while scanning. Only the candidates are then loaded, without initialization, and
 * handed back; they are the classes the validator would load on their first validation anyway.
 *
 * <p>A class is a candidate when it, or a supertype, carries a constraint (an annotation itself
 * annotated {@code @Constraint}, or a container of such), {@code @Valid}, {@code @Validate},
 * {@code @Expr} or {@code @Bind}: on the type, a field, a record component, a method, a
 * parameter, a return value or a type use. Whether an annotation is a constraint is read from
 * its own class file, once per annotation.
 *
 * <p>Plain Java over a class loader, so the build-time descriptor and tests use it too; the
 * class-file listing is pluggable through {@link ClassFileSource}.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public final class BeanDiscovery {

    private static final String CONSTRAINT = "Ljakarta/validation/Constraint;";
    private static final Set<String> DECLARATIONS = Set.of("Ljakarta/validation/Valid;",
            "Lorg/rulii/validation/beans/Validate;", "Lorg/rulii/validation/beans/Expr;", "Lorg/rulii/validation/beans/Expr$List;",
            "Lorg/rulii/validation/beans/Bind;", "Lorg/rulii/validation/beans/Bind$List;");
    private static final List<String> SKIPPED_PREFIXES = List.of("java/", "javax/", "jakarta/", "jdk/", "sun/", "kotlin/", "scala/");
    /** Anonymous and local classes: {@code Outer$1}, {@code Outer$1Local}. */
    private static final Pattern SYNTHETIC = Pattern.compile(".*\\$\\d.*");

    private final ClassLoader loader;
    private final ClassFileSource files;

    /** What was read of each class, by internal name; null for a class file that cannot be found. */
    private final Map<String, CandidateVisitor> classes = new HashMap<>();
    private final Map<String, Boolean> candidates = new HashMap<>();
    private final Map<String, Boolean> constraints = new HashMap<>();

    /**
     * @param loader the loader that loads the candidates and resolves supertypes and annotations; must not be null.
     * @param files  where the class files of a package come from; must not be null.
     */
    public BeanDiscovery(ClassLoader loader, ClassFileSource files) {
        super();
        this.loader = Objects.requireNonNull(loader, "loader cannot be null.");
        this.files = Objects.requireNonNull(files, "files cannot be null.");
    }

    /**
     * A discovery over a class loader's own resources (directories and plain jars).
     *
     * @param loader the loader; must not be null.
     * @return the discovery.
     */
    public static BeanDiscovery of(ClassLoader loader) {
        return new BeanDiscovery(loader, ClassFileSource.of(loader));
    }

    /**
     * Scans the packages and loads the candidates.
     *
     * @param packages base packages, sub-packages included.
     * @return the candidate classes, by name, sorted; a class that cannot be loaded is left out.
     * @throws UncheckedIOException when a listing cannot be read.
     */
    public List<Class<?>> scan(Collection<String> packages) {
        Map<String, Class<?>> result = new TreeMap<>();

        for (String basePackage : packages) {
            List<String> names = new ArrayList<>();
            try {
                files.read(basePackage, bytes -> {
                    CandidateVisitor visitor = CandidateVisitor.read(bytes, this::isDeclaration);
                    classes.put(visitor.className(), visitor);
                    names.add(visitor.className());
                });
            } catch (IOException e) {
                throw new UncheckedIOException("cannot list the class files of package [" + basePackage + "].", e);
            }

            for (String name : names) {
                if (SYNTHETIC.matcher(name).matches() || name.endsWith("package-info") || name.endsWith("module-info")) continue;
                if (classes.get(name).annotation() || !isCandidate(name)) continue;
                Class<?> type = load(name);
                if (type != null) result.put(type.getName(), type);
            }
        }

        return new ArrayList<>(result.values());
    }

    /**
     * Whether a class, by its internal or binary name, is a candidate: read from its class file,
     * and its supertypes', without loading it.
     *
     * @param name {@code com.acme.order.Order} or {@code com/acme/order/Order}.
     * @return true if the class or a supertype carries a declaration; false when it does not, or
     *         when no class file is found for it.
     */
    public boolean isCandidate(String name) {
        String internal = name.replace('.', '/');
        Boolean known = candidates.get(internal);
        if (known != null) return known;
        candidates.put(internal, Boolean.FALSE); // guards against a cycle while the supertypes are read

        CandidateVisitor info = info(internal);
        boolean candidate = false;
        if (info != null) {
            candidate = info.declares();
            for (String supertype : info.supertypes()) {
                if (candidate) break;
                if (skipped(supertype)) continue;
                candidate = isCandidate(supertype);
            }
        }

        candidates.put(internal, candidate);
        return candidate;
    }

    private CandidateVisitor info(String internal) {
        if (classes.containsKey(internal)) return classes.get(internal);
        byte[] bytes = bytes(internal);
        CandidateVisitor info = bytes == null ? null : CandidateVisitor.read(bytes, this::isDeclaration);
        classes.put(internal, info);
        return info;
    }

    private Class<?> load(String internal) {
        try {
            return Class.forName(internal.replace('/', '.'), false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static boolean skipped(String internal) {
        for (String prefix : SKIPPED_PREFIXES) {
            if (internal.startsWith(prefix)) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------------------------
    // Annotations
    // -------------------------------------------------------------------------------------------

    /** Whether an annotation descriptor is a declaration the library reads. */
    private boolean isDeclaration(String descriptor) {
        if (DECLARATIONS.contains(descriptor)) return true;
        if (descriptor == null || descriptor.length() < 3 || descriptor.charAt(0) != 'L') return false;
        return isConstraint(descriptor.substring(1, descriptor.length() - 1));
    }

    /**
     * Whether an annotation type, by internal name, is a constraint or a container of repeated
     * constraints: read from the annotation's class file, once.
     */
    private boolean isConstraint(String internal) {
        Boolean known = constraints.get(internal);
        if (known != null) return known;
        constraints.put(internal, Boolean.FALSE);

        boolean constraint = false;
        byte[] bytes = skipped(internal) && !internal.startsWith("jakarta/validation/constraints/") ? null : bytes(internal);
        if (bytes != null) {
            AnnotationFacts facts = new AnnotationFacts();
            new ClassReader(bytes).accept(facts, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            constraint = facts.constraint || (facts.valueComponent != null && isConstraint(facts.valueComponent));
        }

        constraints.put(internal, constraint);
        return constraint;
    }

    private byte[] bytes(String internal) {
        try (InputStream in = loader.getResourceAsStream(internal + ".class")) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /** Of an annotation's class file: is it meta-annotated {@code @Constraint}; what does {@code value()} return an array of. */
    private static final class AnnotationFacts extends ClassVisitor {

        private boolean constraint;
        private String valueComponent;

        private AnnotationFacts() {
            super(SpringAsmInfo.ASM_VERSION);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (CONSTRAINT.equals(descriptor)) constraint = true;
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            // A container's value() returns an array of the repeated annotation: ()[Lcom/acme/Code;
            if ("value".equals(name) && descriptor.startsWith("()[L") && descriptor.endsWith(";")) {
                valueComponent = descriptor.substring(4, descriptor.length() - 1);
            }
            return null;
        }
    }
}
