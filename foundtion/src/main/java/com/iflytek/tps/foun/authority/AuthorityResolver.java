package com.iflytek.tps.foun.authority;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class AuthorityResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterAnnotation(RequestBody.class) == null
                && (parameter.getParameterType().equals(AuthorityContext.class)
                || parameter.getParameterType().equals(AuthoritySession.class));
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {
        if(parameter.getParameterType().equals(AuthorityContext.class)){
            return AuthorityContext.get();
        } else if (parameter.getParameterType().equals(AuthoritySession.class)) {
            return AuthorityContext.get().getAuthoritySession();
        }
        return new AuthoritySession();
    }
}
