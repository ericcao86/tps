package com.iflytek.tps.foun.web;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.common.auth.Credentials;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.model.PolicyConditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import com.iflytek.tps.foun.authority.AuthorityResolver;
import com.iflytek.tps.foun.authority.AuthoritySession;
import com.iflytek.tps.foun.cluster.JGroupsCluster;
import com.iflytek.tps.foun.dto.AppException;
import com.iflytek.tps.foun.dto.AppResponse;
import com.iflytek.tps.foun.dto.SrvInspectInfo;
import com.iflytek.tps.foun.dto.VerifyImage;
import com.iflytek.tps.foun.helper.ExceptionHelper;
import com.iflytek.tps.foun.helper.RestPerformsHelper;
import com.iflytek.tps.foun.helper.SpringBeanHelper;
import com.iflytek.tps.foun.util.CollectionUtils;
import com.iflytek.tps.foun.util.EncryptUtils;
import com.iflytek.tps.foun.util.MathUtils;
import com.iflytek.tps.foun.within.DesAlgorithm;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.redis.RedisHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.context.annotation.Bean;
import org.springframework.core.MethodParameter;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;

@EnableSwagger2
public abstract class WebConfigurationSupport extends WebMvcConfigurationSupport {
    private static final Logger LOG = LoggerFactory.getLogger(WebConfigurationSupport.class);

    @PostConstruct
    public void initConfiguration() {
    }

    /** 需要 Gzip 压缩的路径映射 **/
    protected void addGzipUri(Set<String> uriSet){

    }

    /** Swagger API 映射路径 **/
    protected Predicate<String> predicate() {
        //return PathSelectors.any();
        return PathSelectors.regex("/api/.*");
    }

    /** Swagger 忽略类型 **/
    protected Class[] apiIgnoreParamTypes() {
        return new Class[]{AuthoritySession.class};
    }

    /** 配置 HttpMessageConverters **/
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.addAll(Lists.newArrayList(StringMessageConverter.INSTANCE,
                FormMessageConverter.INSTANCE,
                FastJsonMessageConverter.INSTANCE));
        super.addDefaultHttpMessageConverters(converters);
    }

    /** 静态资源配置 **/
    @Override
    protected void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static/");
        registry.addResourceHandler("/pages/**").addResourceLocations("classpath:/pages/");
        registry.addResourceHandler("/swagger/**").addResourceLocations("classpath:/swagger/");
    }

    /** HTTP 参数注入 **/
    @Override
    protected void addArgumentResolvers(List<HandlerMethodArgumentResolver> argResolvers) {
        argResolvers.add(new AuthorityResolver());
    }

    /** 异步 Controller 支持 **/
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(120 * 1000);
    }

    /** 是否忽略检测 Redis **/
    @Bean
    @Autowired(required = false)
    public HealthIndicator redisHealthIndicator(StringRedisTemplate redis, Environment env){
        String rhc = env.getProperty("redis.health.check.ignore", "false");
        if("true".equals(rhc)){
            return () -> Health.up().build();
        }
        return null == redis ? () -> Health.down().build() : new RedisHealthIndicator(redis.getConnectionFactory());
    }

    /** 访问日志及请求压缩处理 **/
    @Bean
    @ConditionalOnProperty(prefix = "access.compression", name = "enable", matchIfMissing = true)
    public FilterRegistrationBean accessCompressionFilter(){
        Set<String> uriSet = Sets.newHashSet();
        addGzipUri(uriSet);
        FilterRegistrationBean frBean = new FilterRegistrationBean();
        frBean.setFilter(new AccessCompressionFilter(uriSet));
        frBean.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
        frBean.setOrder(Integer.MIN_VALUE);
        return frBean;
    }

    @Bean
    @ConditionalOnProperty(prefix = "jgroups.cluster", name = "enable", havingValue = "true")
    public JGroupsCluster jGroupsCluster(){
        return new JGroupsCluster();
    }

    /** 支持 Swagger2 **/
    @Bean
    @Autowired
    public Docket apiList(Environment env) {
        Docket docket = new Docket(DocumentationType.SWAGGER_2).forCodeGeneration(true);
        Class[] ignoreParamTypes = apiIgnoreParamTypes();
        if (!CollectionUtils.isNullOrEmpty(ignoreParamTypes)) {
            docket.ignoredParameterTypes(ignoreParamTypes);
        }
        String basePath = env.getProperty("server.context-path");
        docket.genericModelSubstitutes(ResponseEntity.class)
                .useDefaultResponseMessages(false)
                .pathMapping(StringUtils.isBlank(basePath) ? "" : basePath)
                .select()
                .apis(handler -> {
                    assert handler != null;
                    Class<?> ch = handler.declaringClass();
                    return ch.isAnnotationPresent(RestController.class) || ch.isAnnotationPresent(Controller.class);
                })
                .paths(predicate())
                .build();
        return docket;
    }

    /** 全局 Controller **/
    @ControllerAdvice
    @ConditionalOnProperty(prefix = "global.handler", name = "enable", matchIfMissing=true)
    public static class GlobalControllerHandler implements ResponseBodyAdvice<Object> {
        @InitBinder
        public void initRequestBind(HttpServletResponse response) {
            RestPerformsHelper.requestTime(response);
        }

        @ExceptionHandler(Exception.class)
        public @ResponseBody
        AppResponse<Void> errorHandler(HttpServletResponse response, Throwable cause) {
            RestPerformsHelper.responseTime(response);
            if (cause instanceof AppException) {
                AppException ye = (AppException) cause;
                if (null != ye.cause) {
                    LOG.error("process business error: ", ye.cause);
                }
                return AppResponse.failed(ye.code);
            }
            LOG.error("process business error: ", null != cause.getCause() ? cause.getCause() : cause);
            return ExceptionHelper.response(response.getStatus());
        }

        @Override
        public boolean supports(MethodParameter rt, Class<? extends HttpMessageConverter<?>> hmc) {
            return true;
        }

        @Override
        public Object beforeBodyWrite(Object body,
                                      MethodParameter rt,
                                      MediaType mt,
                                      Class<? extends HttpMessageConverter<?>> hmc,
                                      ServerHttpRequest request,
                                      ServerHttpResponse response) {
            RestPerformsHelper.responseTime(response);
            return body;
        }
    }

    /** 系统概要信息 **/
    @Controller
    @ConditionalOnProperty(prefix = "service.inspects", name = "enable", matchIfMissing=true)
    public static class InspectsApiController implements ErrorController {
        /** 系统概要信息页面 **/
        @RequestMapping(path = "/inspects", method = RequestMethod.GET)
        public String inspects(Model model) {
            model.addAttribute("BASE_PATH", SpringBeanHelper.contextPath());
            return "srv.inspects";
        }

        /** 服务构建信息 **/
        @RequestMapping(path = "/inspects/release.info", method = RequestMethod.GET)
        public @ResponseBody
        SrvInspectInfo inspectVersion() {
            return SrvInspectInfo.info();
        }

        /** API 展示页面 **/
        @RequestMapping(path = "/inspects/service.api", method = RequestMethod.GET)
        public String yupaopaoApi(Model model) {
            model.addAttribute("BASE_PATH", SpringBeanHelper.contextPath());
            return "service.api";
        }

        /** 错误处理 Controller **/
        private static final String ERROR = "/error";
        @RequestMapping(ERROR)
        public String error(@RequestParam(required = false) Integer status,
                            HttpServletResponse response,
                            Model mode) {
            status = null == status ? response.getStatus() : status;
            mode.addAttribute("status", status);
            switch (status) {
                case 404:
                    return "error.404";
                default:
                    return "error.50X";
            }
        }

        @Override
        public String getErrorPath() {
            return ERROR;
        }
    }

    /** 图片验证码 **/
    @Controller
    @Api(description = "图片验证码")
    @ConditionalOnProperty(prefix = "verification.image", name = "enable", havingValue = "true")
    public static class VerificationImageController {
        @RequestMapping(path = "/api/verification/img", method = RequestMethod.GET)
        public @ResponseBody
        VerifyImage verificationCreator(HttpServletResponse response) throws IOException {
            response.setHeader("Pragma", "No-cache");
            response.setHeader("Cache-Control", "no-cache");
            response.setDateHeader("Expires", 0);
            String id = MathUtils.shuffleNum(4);
            ByteArrayOutputStream os = null;
            try {
                os = new ByteArrayOutputStream();
                ImageIO.write(new VerificationImageGenerator(new Random()).image(id), VerifyImage.IMG_EXT, os);
                String img = EncryptUtils.encode64(os.toByteArray());
                if(LOG.isDebugEnabled()){
                    LOG.info("image base64: {}", img);
                }
                return VerifyImage.create(DesAlgorithm.create(id + VerifyImage.IMG_EXT), VerifyImage.IMG_EXT, img);
            } finally {
                IOUtils.closeQuietly(os);
            }
        }

        private static final class VerificationImageGenerator {
            private static final int WIDTH = 60;
            private static final int HEIGHT = 20;
            private static final Color IMG_BACK = new Color(0, 163, 254);
            private static final int FONT_SIZE = 15;

            private final Random random;

            VerificationImageGenerator(Random random) {
                this.random = random;
            }

            protected BufferedImage image(String identifying) {
                BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics g = image.getGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, WIDTH, HEIGHT);
                g.setFont(new Font("Times New Roman", Font.PLAIN, FONT_SIZE));
                for(int i = 0; i < identifying.length(); i++){
                    g.setColor(IMG_BACK);
                    g.drawString(String.valueOf(identifying.charAt(i)), 13 * i + 6, 16);
                }
                g.drawOval(0, 12, 60, 11);
                for (int i = 0; i < 100; i++) {
                    int x = random.nextInt(WIDTH);
                    int y = random.nextInt(HEIGHT);
                    g.drawLine(x, y, x, y);
                }
                g.dispose();
                return image;
            }
        }
    }

//    @RestController
//    @RequestMapping("/api/ali/oss")
//    @Api(description = "OSS 应用")
//    @ConditionalOnProperty(prefix = "oss.operations", name = "enable", havingValue = "true")
//    public static class OssApiController {
//        private final Long signatureTime = 5 * 60 *1000L;
//        private final OSSClient client;
//        private final AliOssOperations aliOssOperations;
//        @Autowired
//        public OssApiController(AliOssOperations aliOssOperations) {
//            this.client = aliOssOperations.getClient();
//            this.aliOssOperations = aliOssOperations;
//        }
//
//        @ApiOperation("文件上传签名")
//        @RequestMapping(path = "/signature", method = RequestMethod.POST)
//        public @ResponseBody AppResponse<OssSignatureResponse> signature(@RequestBody OssObjectOperationRequest request) throws Exception {
//            Credentials credentials = client.getCredentialsProvider().getCredentials();
//            OssSignatureResponse dto = new OssSignatureResponse(credentials.getAccessKeyId());
//            dto.upLocation = "//" + request.bucket + "." + client.getEndpoint().getHost();
//            PolicyConditions conditions = new PolicyConditions();
//            conditions.addConditionItem(PolicyConditions.COND_CONTENT_LENGTH_RANGE, 0, 5 * 1024 * 1024);
//            dto.expire = System.currentTimeMillis() + signatureTime;
//            String postPolicy = client.generatePostPolicy(new Date(dto.expire), conditions);
//            dto.policy = BinaryUtil.toBase64String(postPolicy.getBytes(com.alibaba.fastjson.util.IOUtils.UTF8));
//            dto.signature = client.calculatePostSignature(postPolicy);
//            dto.fileKey = createFileKey(request);
//
//            LOG.info("alioss signature info: {}", dto);
//            return AppResponse.success(dto);
//        }
//
//        @ApiOperation("预览")
//        @RequestMapping(path = "/preview", method = RequestMethod.POST)
//        public void preview(@RequestBody OssObjectOperationRequest request, HttpServletResponse response) throws IOException {
//            InputStream is = aliOssOperations.read(request.bucket, request.fileKey);
//            IOUtils.copy(is, response.getOutputStream());
//            IOUtils.closeQuietly(is);
//        }
//
//        private String createFileKey(@RequestBody OssObjectOperationRequest request) {
//            if(StringUtils.isBlank(request.fileKey)){
//                return SpringBeanHelper.applicationEnv() + "/upload/" + DateUtils.now(DateFormat.NumDate) + "/" + IdWorker.unique();
//            }
//            return StringUtils.trim(request.fileKey);
//        }
//    }
}
