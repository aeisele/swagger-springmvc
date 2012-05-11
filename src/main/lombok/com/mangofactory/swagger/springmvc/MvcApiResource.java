package com.mangofactory.swagger.springmvc;

import com.mangofactory.swagger.ControllerDocumentation;
import com.mangofactory.swagger.SwaggerConfiguration;
import com.mangofactory.swagger.springmvc.controller.DocumentationController;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.DocumentationEndPoint;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

/**
 * Generates a Resource listing for a given Api class.
 *
 * @author martypitt
 */
@Slf4j
public class MvcApiResource {

    @Getter
    private final HandlerMethod handlerMethod;
    private final Class<?> controllerClass;
    private final SwaggerConfiguration configuration;

    public MvcApiResource(HandlerMethod handlerMethod, SwaggerConfiguration configuration) {
        this.handlerMethod = handlerMethod;
        this.configuration = configuration;
        this.controllerClass = handlerMethod.getBeanType();
    }

    public DocumentationEndPoint describeAsEndpoint() {
        return new DocumentationEndPoint(getControllerUri(), getApiDescription());
    }

    public ControllerDocumentation createEmptyApiDocumentation() {
        String resourcePath = getControllerUri();
        if (resourcePath == null)
            return null;

        return configuration.newDocumentation(this);
    }

    private String getApiDescription() {
        Api apiAnnotation = controllerClass.getAnnotation(Api.class);
        if (apiAnnotation == null)
            return null;
        return apiAnnotation.description();

    }

    public String getControllerUri() {
        RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
        if (requestMapping == null) {
            log.warn("Class {} has handler methods, but no class-level @RequestMapping.  No documentation will be generated", controllerClass.getName());
            return null;
        }
        String[] requestUris = requestMapping.value();
        if (requestUris == null || requestUris.length == 0) {
            log.warn("Class {} contains a @RequestMapping, but could not resolve the uri.  No documentation will be generated", controllerClass.getName());
            return null;
        }
        if (requestUris.length > 1) {
            log.warn("Class {} contains a @RequestMapping with multiple uri's.  Only the first one will be documented.");
        }
        String requestUri = requestUris[0];
        Api apiAnnotation = controllerClass.getAnnotation(Api.class);
        if (apiAnnotation != null && !StringUtils.isEmpty(apiAnnotation.listingPath())) {
            requestUri = apiAnnotation.listingPath();
        }
        return removeIntermediateSlashes(requestUri);
    }

    private String removeIntermediateSlashes(String requestUri) {
        String replaced = requestUri.replaceAll("/", "_");
        return (requestUri.charAt(0) == '/') ? "/" + replaced.substring(1) : replaced;
    }

    @Override
    public String toString() {
        return "ApiResource for " + controllerClass.getSimpleName() + " at " + getControllerUri();
    }

    public Class<?> getControllerClass() {
        return controllerClass;
    }

    public boolean isInternalResource() {
        return controllerClass == DocumentationController.class;
    }

}
