package io.github.lvcongzheng520.springdoc.autoconfigure;

import io.github.lvcongzheng520.springdoc.autoconfigure.config.properties.SpringDocProperties;
import io.github.lvcongzheng520.springdoc.autoconfigure.handler.OpenApiHandler;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.*;
import org.springdoc.core.customizers.OpenApiBuilderCustomizer;
import org.springdoc.core.customizers.OpenApiCustomiser;
import org.springdoc.core.customizers.ServerBaseUrlCustomizer;
import org.springdoc.core.providers.JavadocProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Swagger 文档配置
 *
 * @author lcz
 */
@RequiredArgsConstructor
@Configuration
@EnableConfigurationProperties(SpringDocProperties.class)
@AutoConfigureBefore(SpringDocConfiguration.class)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class SpringDocAutoConfiguration implements ApplicationListener<WebServerInitializedEvent> {

    private final ServerProperties serverProperties;

    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI openApi(SpringDocProperties properties) {
        // 配置校验
        Assert.notNull(properties.getInfo(),"SpringDoc属性配置缺失 -> 文件基本信息[标题、描述、版本、作者、作者信息...]");
        Assert.notNull(properties.getComponents(),"SpringDoc属性配置缺失 -> 组件[例如鉴权方式]");

        OpenAPI openApi = new OpenAPI();
        // 文档基本信息
        SpringDocProperties.InfoProperties infoProperties = properties.getInfo();
        Info info = convertInfo(infoProperties);
        openApi.info(info);
        // 扩展文档信息
        openApi.externalDocs(properties.getExternalDocs());
        openApi.tags(properties.getTags());
        openApi.paths(properties.getPaths());
        openApi.components(properties.getComponents());
        Set<String> keySet = properties.getComponents().getSecuritySchemes().keySet();
        List<SecurityRequirement> list = new ArrayList<>();
        SecurityRequirement securityRequirement = new SecurityRequirement();
        keySet.forEach(securityRequirement::addList);
        list.add(securityRequirement);
        openApi.security(list);
        return openApi;
    }

    private Info convertInfo(SpringDocProperties.InfoProperties infoProperties) {
        Info info = new Info();
        info.setTitle(infoProperties.getTitle());
        info.setDescription(infoProperties.getDescription());
        info.setContact(infoProperties.getContact());
        info.setLicense(infoProperties.getLicense());
        info.setVersion(infoProperties.getVersion());
        return info;
    }

    /**
     * 自定义 openapi 处理器
     */
    @Bean
    public OpenAPIService openApiBuilder(Optional<OpenAPI> openAPI,
                                         SecurityService securityParser,
                                         SpringDocConfigProperties springDocConfigProperties, PropertyResolverUtils propertyResolverUtils,
                                         Optional<List<OpenApiBuilderCustomizer>> openApiBuilderCustomisers,
                                         Optional<List<ServerBaseUrlCustomizer>> serverBaseUrlCustomisers, Optional<JavadocProvider> javadocProvider) {
        return new OpenApiHandler(openAPI, securityParser, springDocConfigProperties, propertyResolverUtils, openApiBuilderCustomisers, serverBaseUrlCustomisers, javadocProvider);
    }

    /**
     * 对已经生成好的 OpenApi 进行自定义操作
     */
    @Bean
    public OpenApiCustomiser openApiCustomiser() {
        String contextPath = serverProperties.getServlet().getContextPath();
        String finalContextPath;
        if (StringUtils.isBlank(contextPath) || "/".equals(contextPath)) {
            finalContextPath = "";
        } else {
            finalContextPath = contextPath;
        }
        // 对所有路径增加前置上下文路径
        return openApi -> {
            Paths oldPaths = openApi.getPaths();
            if (oldPaths instanceof PlusPaths) {
                return;
            }
            PlusPaths newPaths = new PlusPaths();
            oldPaths.forEach((k,v) -> newPaths.addPathItem(finalContextPath + k, v));
            openApi.setPaths(newPaths);
        };
    }

    /**
     * 单独使用一个类便于判断 解决springdoc路径拼接重复问题
     *
     * @author lcz
     */
    static class PlusPaths extends Paths {

        public PlusPaths() {
            super();
        }

    }


    /**
     * 当Web服务器初始化完成后执行的事件处理方法。
     *
     * @param event 表示Web服务器初始化事件的对象，通过该对象可以获取Web服务器的信息。
     *              其中，getWebServer()方法可以获取到Web服务器实例，进一步可以获取到服务器的端口号。
     *              该事件是在Spring Boot启动完成后，Web服务器（如Tomcat）完全初始化后触发的。
     *
     *              本方法不返回任何内容。
     */
    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        // 获取服务器端口号
        int port = event.getWebServer().getPort();
        // 构建访问URL
        String url = "http://127.0.0.1:" + port + "/doc.html";
        // 打印SpringDoc启用信息及访问地址
        System.out.println("SpringDoc is Enable ! Access Address:" + url);
    }

}
