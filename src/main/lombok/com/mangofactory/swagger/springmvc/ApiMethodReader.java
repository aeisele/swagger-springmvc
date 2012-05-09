package com.mangofactory.swagger.springmvc;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.wordnik.swagger.core.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang.StringUtils;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Slf4j
public class ApiMethodReader {

    private static final Set<Class<?>> ignoreParameters =
            ImmutableSet.of(HttpServletRequest.class, HttpServletResponse.class);

    private final HandlerMethod handlerMethod;
    @Getter
    private String summary;
    @Getter
    private String notes;
    @Getter
    private Class<?> responseClass;
    @Getter
    private String tags;
    private String nickname;

    private boolean deprecated;
    @Getter
    private final List<DocumentationError> errors = Lists.newArrayList();
    private final List<DocumentationParameter> parameters = Lists.newArrayList();

    public ApiMethodReader(HandlerMethod handlerMethod) {
        this.handlerMethod = handlerMethod;
        documentOperation();
        documentParameters();
        documentExceptions();
    }

    private void documentOperation() {
        ApiOperation apiOperation = handlerMethod.getMethodAnnotation(ApiOperation.class);
        if (apiOperation != null) {
            summary = apiOperation.value();
            notes = apiOperation.notes();
            tags = apiOperation.tags();
        }
        nickname = handlerMethod.getMethod().getName();
        deprecated = handlerMethod.getMethodAnnotation(Deprecated.class) != null;
    }

    public DocumentationOperation getOperation(RequestMethod requestMethod) {
        DocumentationOperation operation = new DocumentationOperation(requestMethod.name(), summary, notes);
        operation.setDeprecated(deprecated);
        operation.setNickname(nickname);
        for (DocumentationParameter parameter : parameters)
            operation.addParameter(parameter);
        setTags(operation);

        for (DocumentationError error : errors)
            operation.addErrorResponse(error);
        return operation;
    }

    private void setTags(DocumentationOperation operation) {
        if (tags != null)
            operation.setTags(Arrays.asList(tags.split(",")));
    }

    private void documentParameters() {
        for (MethodParameter methodParameter : handlerMethod.getMethodParameters()) {
            ApiParam apiParam = methodParameter.getParameterAnnotation(ApiParam.class);
            if (apiParam == null) {
                ApiMethodReader.log.warn("{} is missing @ApiParam annotation - so generating default documentation");
                generateDefaultParameterDocumentation(methodParameter);
                continue;
            }
            String name = selectBestParameterName(methodParameter);
            val allowableValues = convertToAllowableValues(apiParam.allowableValues());
            String description = apiParam.value();
            if (StringUtils.isEmpty(name))
                name = methodParameter.getParameterName();
            String paramType = "path";
            String dataType = methodParameter.getParameterType().getSimpleName();
            DocumentationParameter documentationParameter = new DocumentationParameter(name, description, apiParam.internalDescription(),
                    paramType, apiParam.defaultValue(), allowableValues, apiParam.required(), apiParam.allowMultiple());
            documentationParameter.setDataType(dataType);
            parameters.add(documentationParameter);

        }
    }

    private String selectBestParameterName(MethodParameter methodParameter) {
        ApiParam apiParam = methodParameter.getParameterAnnotation(ApiParam.class);
        if (apiParam != null && !StringUtils.isEmpty(apiParam.name()))
            return apiParam.name();
        PathVariable pathVariable = methodParameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null && !StringUtils.isEmpty(pathVariable.value()))
            return pathVariable.value();
        ModelAttribute modelAttribute = methodParameter.getParameterAnnotation(ModelAttribute.class);
        if (modelAttribute != null && !StringUtils.isEmpty(modelAttribute.value()))
            return modelAttribute.value();
        RequestParam requestParamAnnotation = methodParameter.getParameterAnnotation(RequestParam.class);
        if (requestParamAnnotation != null && !StringUtils.isEmpty(requestParamAnnotation.value()))
            return requestParamAnnotation.value();
        // Default
        return methodParameter.getParameterName();

    }

    private void generateDefaultParameterDocumentation(MethodParameter methodParameter) {
        Class<?> parameterType = methodParameter.getParameterType();
        if (ignoreParameters.contains(parameterType)) {
            return;
        }

        String name = selectBestParameterName(methodParameter);
        String dataType = parameterType.getSimpleName();
        String paramType = null;
        String defaultValue = null;
        boolean required = false;

        PathVariable pathVariableAnnotation = methodParameter.getParameterAnnotation(PathVariable.class);
        if (pathVariableAnnotation != null) {
            paramType = "path";
            required = true;
        } else {
            RequestParam requestParamAnnotation = methodParameter.getParameterAnnotation(RequestParam.class);
            if (requestParamAnnotation != null) {
                paramType = "query";
                required = requestParamAnnotation.required();
                if (!ValueConstants.DEFAULT_NONE.equals(requestParamAnnotation.defaultValue())) {
                    defaultValue = requestParamAnnotation.defaultValue();
                }
            }
        }

        DocumentationParameter documentationParameter = new DocumentationParameter(name, "", "", paramType, defaultValue, null, true, required);
        documentationParameter.setDataType(dataType);
        parameters.add(documentationParameter);
    }

    protected DocumentationAllowableValues convertToAllowableValues(String csvString) {
        if (csvString.toLowerCase().startsWith("range[")) {
            val ranges = csvString.substring(6, csvString.length() - 1).split(",");
            return AllowableRangesParser.buildAllowableRangeValues(ranges, csvString);
        } else if (csvString.toLowerCase().startsWith("rangeexclusive[")) {
            val ranges = csvString.substring(15, csvString.length() - 1).split(",");
            return AllowableRangesParser.buildAllowableRangeValues(ranges, csvString);
        }
        // else..
        if (csvString == null || csvString.length() == 0)
            return null;
        val params = Arrays.asList(csvString.split(","));
        return new DocumentationAllowableListValues(params);
    }

    private void documentExceptions() {
        discoverSwaggerAnnotatedExceptions();
        discoverSpringMvcExceptions();
        discoverThrowsExceptions();
    }

    private void discoverThrowsExceptions() {
        Class<?>[] exceptionTypes = handlerMethod.getMethod().getExceptionTypes();
        for (Class<?> exceptionType : exceptionTypes) {
            appendErrorFromClass((Class<? extends Throwable>) exceptionType);
        }
    }

    private void discoverSpringMvcExceptions() {
        com.mangofactory.swagger.ApiErrors apiErrors = handlerMethod.getMethodAnnotation(com.mangofactory.swagger.ApiErrors.class);
        if (apiErrors == null)
            return;
        for (Class<? extends Throwable> exceptionClass : apiErrors.value()) {
            appendErrorFromClass(exceptionClass);
        }

    }

    void appendErrorFromClass(Class<? extends Throwable> exceptionClass) {
        com.mangofactory.swagger.ApiError apiError = exceptionClass.getAnnotation(com.mangofactory.swagger.ApiError.class);
        if (apiError == null)
            return;
        errors.add(new DocumentationError(apiError.code(), apiError.reason()));
    }

    private void discoverSwaggerAnnotatedExceptions() {
        ApiErrors apiErrors = handlerMethod.getMethodAnnotation(ApiErrors.class);
        if (apiErrors == null)
            return;
        for (ApiError apiError : apiErrors.value()) {
            errors.add(new DocumentationError(apiError.code(), apiError.reason()));
        }
    }
}
