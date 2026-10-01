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

import org.rulii.explorer.expression.ExpressionAnalyzer;
import org.rulii.explorer.expression.ExpressionAnalyzers;
import org.rulii.explorer.problem.ProblemCheck;
import org.rulii.explorer.problem.ProblemChecks;
import org.rulii.registry.RuleRegistry;
import org.rulii.spring.config.RuleConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * Wires the explorer into a Spring Boot application (SOLUTION §8.1). Applies after rulii-spring's
 * {@link RuleConfig}, only when a {@link RuleRegistry} bean exists and {@code rulii.explorer.enabled}
 * is not false. Every bean is overridable.
 *
 * <ul>
 *   <li>{@link DescriptorService}: builds and caches the descriptor on first request.</li>
 *   <li>{@link RuliiDescriptorEndpoint}: the {@code rulii} Actuator endpoint, subject to exposure.</li>
 * </ul>
 *
 * <p>Applications add analyzers for other script languages or extra problem checks by declaring
 * {@link ExpressionAnalyzer} and {@link ProblemCheck} beans; they run before the defaults.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
@AutoConfiguration(after = RuleConfig.class)
@ConditionalOnBean(RuleRegistry.class)
@ConditionalOnProperty(prefix = "rulii.explorer", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RuliiExplorerProperties.class)
public class RuliiExplorerAutoConfiguration {

    public RuliiExplorerAutoConfiguration() {
        super();
    }

    @Bean
    @ConditionalOnMissingBean
    public DescriptorService ruliiDescriptorService(RuleRegistry ruleRegistry, RuliiExplorerProperties properties,
                                                    Environment environment, ObjectProvider<ExpressionAnalyzer> analyzers,
                                                    ObjectProvider<ProblemCheck> checks) {
        String applicationName = properties.getApplicationName() != null
                ? properties.getApplicationName() : environment.getProperty("spring.application.name");

        List<ExpressionAnalyzer> allAnalyzers = new ArrayList<>(analyzers.orderedStream().toList());
        allAnalyzers.addAll(ExpressionAnalyzers.defaults().list());

        List<ProblemCheck> allChecks = new ArrayList<>(checks.orderedStream().toList());
        allChecks.addAll(ProblemChecks.defaults());

        return new DescriptorService(ruleRegistry, applicationName, properties.isIncludeSources(), allAnalyzers, allChecks);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnAvailableEndpoint(RuliiDescriptorEndpoint.class)
    public RuliiDescriptorEndpoint ruliiDescriptorEndpoint(DescriptorService service) {
        return new RuliiDescriptorEndpoint(service);
    }
}
