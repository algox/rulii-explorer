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
import org.springframework.asm.FieldVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.RecordComponentVisitor;
import org.springframework.asm.SpringAsmInfo;
import org.springframework.asm.TypePath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Reads one class file and answers the scan's question without loading the class: does the type,
 * a field, a record component, a method, a parameter, a return value or a type use carry a
 * declaration rulii-bean-validation reads (a constraint, {@code @Valid}, {@code @Validate},
 * {@code @Expr}, {@code @Bind})? It also keeps the supertypes, so a class whose declarations are
 * all inherited is found through them.
 *
 * <p>The only use of Spring's repackaged ASM ({@code org.springframework.asm}, reached through
 * {@code spring-expression}): isolated here so it can be swapped for another byte reader.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
final class CandidateVisitor extends ClassVisitor {

    private final Predicate<String> declaration;

    private String className;
    private String superName;
    private final List<String> interfaces = new ArrayList<>();
    private boolean declares;
    private boolean annotation;

    /**
     * @param declaration whether an annotation descriptor ({@code Ljakarta/validation/Valid;}) is a declaration.
     */
    CandidateVisitor(Predicate<String> declaration) {
        super(SpringAsmInfo.ASM_VERSION);
        this.declaration = declaration;
    }

    /** Reads the class file; no code, debug info or frames. */
    static CandidateVisitor read(byte[] classFile, Predicate<String> declaration) {
        CandidateVisitor visitor = new CandidateVisitor(declaration);
        new ClassReader(classFile).accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return visitor;
    }

    /** The internal name, {@code com/acme/order/Order}. */
    String className() {
        return className;
    }

    /** The internal names of the superclass and the interfaces; {@code java/lang/Object} left out. */
    List<String> supertypes() {
        List<String> result = new ArrayList<>(interfaces);
        if (superName != null && !"java/lang/Object".equals(superName)) result.add(0, superName);
        return result;
    }

    /** Whether the class file is an annotation type: never a bean, whatever it carries. */
    boolean annotation() {
        return annotation;
    }

    /** Whether the class itself carries a declaration. */
    boolean declares() {
        return declares;
    }

    private void check(String descriptor) {
        if (!declares && declaration.test(descriptor)) declares = true;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        this.superName = superName;
        this.annotation = (access & Opcodes.ACC_ANNOTATION) != 0;
        if (interfaces != null) this.interfaces.addAll(List.of(interfaces));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        check(descriptor);
        return null;
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        check(descriptor);
        return null;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        return declares ? null : new FieldVisitor(api) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                check(descriptor);
                return null;
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                check(descriptor);
                return null;
            }
        };
    }

    @Override
    public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
        return declares ? null : new RecordComponentVisitor(api) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                check(descriptor);
                return null;
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                check(descriptor);
                return null;
            }
        };
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return declares ? null : new MethodVisitor(api) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                check(descriptor);
                return null;
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                check(descriptor);
                return null;
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                check(descriptor);
                return null;
            }
        };
    }
}
