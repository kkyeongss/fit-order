package kr.fitorder.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DemoModeConfig implements WebMvcConfigurer {

    private final DemoProperties demo;

    public DemoModeConfig(DemoProperties demo) {
        this.demo = demo;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new DemoModeInterceptor(demo.enabled())).addPathPatterns("/api/**");
    }
}
