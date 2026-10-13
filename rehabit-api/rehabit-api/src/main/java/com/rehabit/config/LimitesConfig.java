package com.rehabit.config;

import com.rehabit.security.LimitePorIpInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Liga os limites por IP às rotas (a tabela de rotas fica no próprio interceptor). */
@Configuration
public class LimitesConfig implements WebMvcConfigurer {

    private final LimitePorIpInterceptor limitePorIp;

    public LimitesConfig(LimitePorIpInterceptor limitePorIp) {
        this.limitePorIp = limitePorIp;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(limitePorIp);
    }
}
