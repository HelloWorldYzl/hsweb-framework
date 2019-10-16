package org.hswebframework.web.authorization.basic.aop;

import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.aop.MethodInterceptorContext;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.DataAccess;
import org.hswebframework.web.authorization.annotation.Dimension;
import org.hswebframework.web.authorization.basic.define.DefaultBasicAuthorizeDefinition;
import org.hswebframework.web.authorization.basic.define.EmptyAuthorizeDefinition;
import org.hswebframework.web.authorization.define.AuthorizeDefinition;
import org.hswebframework.web.utils.AnnotationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注解权限控制定义解析器,通过判断方法上的注解来获取权限控制的方式
 *
 * @author zhouhao
 * @see AopMethodAuthorizeDefinitionParser
 * @see AuthorizeDefinition
 */
@Slf4j
public class DefaultAopMethodAuthorizeDefinitionParser implements AopMethodAuthorizeDefinitionParser {

    private Map<CacheKey, AuthorizeDefinition> cache = new ConcurrentHashMap<>();

    private List<AopMethodAuthorizeDefinitionCustomizerParser> parserCustomizers;

    private static Set<String> excludeMethodName = new HashSet<>(Arrays.asList("toString", "clone", "hashCode", "getClass"));

    @Autowired(required = false)
    public void setParserCustomizers(List<AopMethodAuthorizeDefinitionCustomizerParser> parserCustomizers) {
        this.parserCustomizers = parserCustomizers;
    }

    @Override
    public List<AuthorizeDefinition> getAllParsed() {
        return new ArrayList<>(cache.values());
    }

    @Override
    @SuppressWarnings("all")
    public AuthorizeDefinition parse(Class target, Method method, MethodInterceptorContext context) {
        if (excludeMethodName.contains(method.getName())) {
            return null;
        }
        CacheKey key = buildCacheKey(target, method);

        AuthorizeDefinition definition = cache.get(key);
        if (definition instanceof EmptyAuthorizeDefinition) {
            return null;
        }
        if (null != definition) {
            return definition;
        }
        //使用自定义
        if (!CollectionUtils.isEmpty(parserCustomizers)) {
            definition = parserCustomizers.stream()
                    .map(customizer -> customizer.parse(target, method, context))
                    .filter(Objects::nonNull)
                    .findAny().orElse(null);
            if (definition instanceof EmptyAuthorizeDefinition) {
                return null;
            }
            if (definition != null) {
                return definition;
            }
        }

        Authorize annotation = AnnotationUtils.findAnnotation(target, method, Authorize.class);

        if (annotation != null && annotation.ignore()) {
            cache.put(key, EmptyAuthorizeDefinition.instance);
            return null;
        }
        synchronized (cache) {
            return cache.computeIfAbsent(key, (__) -> {
                return DefaultBasicAuthorizeDefinition.from(target, method);
            });
        }
    }

    public CacheKey buildCacheKey(Class target, Method method) {
        return new CacheKey(ClassUtils.getUserClass(target), method);
    }

    class CacheKey {
        private Class type;
        private Method method;

        public CacheKey(Class type, Method method) {
            this.type = type;
            this.method = method;
        }

        @Override
        public int hashCode() {
            return Arrays.asList(type, method).hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && this.hashCode() == obj.hashCode();
        }
    }

    public void destroy() {
        cache.clear();
    }

}
