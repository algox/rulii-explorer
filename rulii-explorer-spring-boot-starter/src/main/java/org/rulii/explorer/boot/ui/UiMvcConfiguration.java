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
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.time.Duration;

/**
 * Serves the UI on Spring MVC when Actuator shares the application's port: the page at the UI
 * path (with and without a trailing slash) and the assets under the versioned path, cached for a
 * year (SOLUTION §8.1). With a separate management port, {@link UiServletManagementContextConfiguration}
 * serves it there instead.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({WebMvcConfigurer.class, RouterFunction.class})
@ConditionalOnProperty(prefix = "rulii.explorer.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnManagementPort(ManagementPortType.SAME)
public class UiMvcConfiguration implements WebMvcConfigurer {

    static final CacheControl ASSET_CACHE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final UiPage page;

    public UiMvcConfiguration(UiPage page) {
        super();
        this.page = page;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(page.assetPattern())
                .addResourceLocations(UiPage.ASSET_LOCATION)
                .setCacheControl(ASSET_CACHE);
    }

    @Bean
    public RouterFunction<ServerResponse> ruliiExplorerUiRoutes(WebEndpointProperties endpoints) {
        return indexRoutes(page, endpoints);
    }

    /** The index routes, shared with the management-context configuration. */
    static RouterFunction<ServerResponse> indexRoutes(UiPage page, WebEndpointProperties endpoints) {
        HandlerFunction<ServerResponse> index = request -> {
            String contextPath = request.servletRequest().getContextPath();
            String descriptor = contextPath + endpoints.getBasePath() + "/" + RuliiDescriptorEndpoint.ID;
            return ServerResponse.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .cacheControl(CacheControl.noCache())
                    .body(page.render(contextPath, descriptor));
        };
        return RouterFunctions.route(RequestPredicates.GET(page.path()), index)
                .andRoute(RequestPredicates.GET(page.path() + "/"), index);
    }
}
