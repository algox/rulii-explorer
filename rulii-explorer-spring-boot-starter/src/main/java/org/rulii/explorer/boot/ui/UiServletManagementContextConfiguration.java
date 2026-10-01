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
package org.rulii.explorer.boot.ui;

import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Serves the UI from the management context when Actuator runs on its own port, so the page
 * stays on the same origin as the descriptor and needs no CORS (SOLUTION §8.1). Registered in
 * {@code META-INF/spring/...ManagementContextConfiguration.imports}; the child context has
 * {@code @EnableWebMvc}, so its {@link WebMvcConfigurer}s and router functions apply there.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
@ManagementContextConfiguration(value = ManagementContextType.CHILD, proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({WebMvcConfigurer.class, RouterFunction.class})
@ConditionalOnBean(UiPage.class)
public class UiServletManagementContextConfiguration implements WebMvcConfigurer {

    private final UiPage page;

    public UiServletManagementContextConfiguration(UiPage page) {
        super();
        this.page = page;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(page.assetPattern())
                .addResourceLocations(UiPage.ASSET_LOCATION)
                .setCacheControl(UiMvcConfiguration.ASSET_CACHE);
    }

    @Bean
    public RouterFunction<ServerResponse> ruliiExplorerManagementUiRoutes(WebEndpointProperties endpoints) {
        return UiMvcConfiguration.indexRoutes(page, endpoints);
    }
}
