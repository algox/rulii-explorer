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

import org.rulii.explorer.boot.ui.UiMvcConfiguration;
import org.rulii.explorer.boot.ui.UiPage;
import org.rulii.explorer.boot.ui.UiWebFluxConfiguration;
import org.rulii.explorer.builder.PlaceholderFilter;
import org.rulii.explorer.expression.ExpressionAnalyzer;
import org.rulii.explorer.expression.ExpressionAnalyzers;
import org.rulii.explorer.problem.ProblemCheck;
import org.rulii.explorer.problem.ProblemChecks;
import org.rulii.registry.RuleRegistry;
import org.rulii.spring.config.RuleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.Sanitizer;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Wires the explorer into a Spring Boot application (SOLUTION §8.1). Applies after rulii-spring's
 * {@link RuleConfig}, only when a {@link RuleRegistry} bean exists and {@code rulii.explorer.enabled}
 * is true: the explorer is off by default, so a production deployment never shows its rules unless
 * someone turned it on. Every bean is overridable.
 *
 * <ul>
 *   <li>{@link DescriptorService}: builds and caches the descriptor on first request.</li>
 *   <li>{@link RuliiDescriptorEndpoint}: the {@code rulii} Actuator endpoint, subject to exposure.</li>
 *   <li>{@link UiPage} plus {@link UiMvcConfiguration} or {@link UiWebFluxConfiguration}: the UI at
 *   {@code rulii.explorer.ui.path}, unless {@code rulii.explorer.ui.enabled} is false. On a separate
 *   management port the UI moves there (see {@code org.rulii.explorer.boot.ui}).</li>
 * </ul>
 *
 * <p>Applications add analyzers for other script languages or extra problem checks by declaring
 * {@link ExpressionAnalyzer} and {@link ProblemCheck} beans; they run before the defaults.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */

@AutoConfiguration(after = RuleConfig.class)
@ConditionalOnBean(RuleRegistry.class)
@ConditionalOnProperty(prefix = "rulii.explorer", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RuliiExplorerProperties.class)
@Import({UiMvcConfiguration.class, UiWebFluxConfiguration.class})
public class RuliiExplorerAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuliiExplorerAutoConfiguration.class);

    public RuliiExplorerAutoConfiguration() {
        super();
    }

    @Bean
    @ConditionalOnMissingBean
    public DescriptorService ruliiDescriptorService(RuleRegistry ruleRegistry, RuliiExplorerProperties properties,
                                                    Environment environment, ObjectProvider<ExpressionAnalyzer> analyzers,
                                                    ObjectProvider<ProblemCheck> checks,
                                                    ObjectProvider<SanitizingFunction> sanitizingFunctions) {
        String applicationName = properties.getApplicationName() != null
                ? properties.getApplicationName() : environment.getProperty("spring.application.name");

        List<ExpressionAnalyzer> allAnalyzers = new ArrayList<>(analyzers.orderedStream().toList());
        allAnalyzers.addAll(ExpressionAnalyzers.defaults().list());

        List<ProblemCheck> allChecks = new ArrayList<>(checks.orderedStream().toList());
        allChecks.addAll(ProblemChecks.defaults());

        PlaceholderFilter placeholderValues = placeholderFilter(properties.getPlaceholders(), sanitizingFunctions.orderedStream().toList());

        return new DescriptorService(ruleRegistry, applicationName, properties.isIncludeSources(), placeholderValues,
                allAnalyzers, allChecks);
    }

    /**
     * The filter behind {@code rulii.explorer.placeholders}: null when values are off; otherwise the
     * excluded key globs, and the application's {@link SanitizingFunction} beans (the ones its
     * {@code env} endpoint already uses) so a value they would mask stays hidden here too.
     */
    static PlaceholderFilter placeholderFilter(RuliiExplorerProperties.Placeholders settings, List<SanitizingFunction> sanitizingFunctions) {
        if (settings.getShowValues() != RuliiExplorerProperties.ShowValues.ALWAYS) return null;

        List<String> excludes = settings.allExcludes();
        PlaceholderFilter filter = PlaceholderFilter.excluding(excludes);
        if (!sanitizingFunctions.isEmpty()) {
            Sanitizer sanitizer = new Sanitizer(sanitizingFunctions);
            filter = filter.and((key, value) -> Objects.equals(value, sanitizer.sanitize(new SanitizableData(null, key, value), true)));
        }
        LOGGER.info("rulii explorer shows the values placeholders compiled with; keys matching {} stay hidden{}", excludes,
                sanitizingFunctions.isEmpty() ? "" : ", and so does anything the application's " + sanitizingFunctions.size() + " sanitizing function(s) would mask");
        return filter;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnAvailableEndpoint(RuliiDescriptorEndpoint.class)
    public RuliiDescriptorEndpoint ruliiDescriptorEndpoint(DescriptorService service) {
        return new RuliiDescriptorEndpoint(service);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "rulii.explorer.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
    public UiPage ruliiExplorerUiPage(RuliiExplorerProperties properties) {
        return UiPage.forVersion(properties.getUi().getPath(), properties.getUi().isExternalSources());
    }
}
