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
package com.acme.order.scale;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Map;

/**
 * The {@code scale} profile: registers a generated application of {@code rulii.scale.size}
 * artifacts (default 1,000) next to the real demo rules, for the scale tests and for looking at
 * the explorer with a big registry:
 *
 * <pre>
 * mvn -pl rulii-explorer-demo spring-boot:run -Dspring-boot.run.profiles=scale
 * </pre>
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
@Configuration
@Profile("scale")
@Import(ScaleConfig.Registrar.class)
public class ScaleConfig {

    public ScaleConfig() {
        super();
    }

    /**
     * Registers each planned artifact as a bean, so the registry sees them like any other. The
     * objects are generated lazily, on the first bean instantiation: scripts with {@code ${...}}
     * placeholders need the context's value resolver, which does not exist while bean definitions
     * are still being registered.
     */
    static class Registrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

        private Environment environment;
        private Map<String, Object> generated;

        private synchronized Object artifact(String name, int size, long seed) {
            if (generated == null) generated = ScaleGenerator.generate(size, seed);
            return generated.get(name);
        }

        Registrar() {
            super();
        }

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
            int size = environment.getProperty("rulii.scale.size", Integer.class, 1000);
            long seed = environment.getProperty("rulii.scale.seed", Long.class, 42L);
            for (Map.Entry<String, Class<?>> entry : ScaleGenerator.plan(size).entrySet()) {
                String name = entry.getKey();
                GenericBeanDefinition definition = new GenericBeanDefinition();
                definition.setBeanClass(entry.getValue());
                definition.setInstanceSupplier(() -> artifact(name, size, seed));
                definition.setDependsOn("rulii.scriptManager");
                registry.registerBeanDefinition(name, definition);
            }
        }
    }
}
