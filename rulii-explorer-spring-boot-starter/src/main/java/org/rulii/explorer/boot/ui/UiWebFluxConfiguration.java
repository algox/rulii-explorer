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

import org.rulii.explorer.boot.RuliiDescriptorEndpoint;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.web.server.ConditionalOnManagementPort;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * The WebFlux twin of {@link UiMvcConfiguration}: the same page and assets on a reactive stack
 * (NFR-11), when Actuator shares the application's port.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass({WebFluxConfigurer.class, RouterFunction.class})
@ConditionalOnProperty(prefix = "rulii.explorer.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnManagementPort(ManagementPortType.SAME)
public class UiWebFluxConfiguration implements WebFluxConfigurer {

    private final UiPage page;

    public UiWebFluxConfiguration(UiPage page) {
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
    public RouterFunction<ServerResponse> ruliiExplorerUiRoutes(WebEndpointProperties endpoints) {
        return indexRoutes(page, endpoints);
    }

    /** The index routes, shared with the management-context configuration. */
    static RouterFunction<ServerResponse> indexRoutes(UiPage page, WebEndpointProperties endpoints) {
        HandlerFunction<ServerResponse> index = request -> {
            String contextPath = request.requestPath().contextPath().value();
            String descriptor = contextPath + endpoints.getBasePath() + "/" + RuliiDescriptorEndpoint.ID;
            return ServerResponse.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .cacheControl(CacheControl.noCache())
                    .bodyValue(page.render(contextPath, descriptor));
        };
        return RouterFunctions.route(RequestPredicates.GET(page.path()), index)
                .andRoute(RequestPredicates.GET(page.path() + "/"), index);
    }
}
