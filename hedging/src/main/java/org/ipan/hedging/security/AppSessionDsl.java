package org.ipan.hedging.security;

import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.SecurityContextConfigurer;
import org.springframework.security.web.authentication.*;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;

public class AppSessionDsl extends AbstractHttpConfigurer<AppSessionDsl, HttpSecurity> {

/*    @Override
    public void init(HttpSecurity http) throws Exception {
        // any method that adds another configurer
        // must be done in the init method
        http.csrf().disable();
    }*/

    @Override
    public void configure(HttpSecurity http) throws Exception {
        ApplicationContext context = http.getSharedObject(ApplicationContext.class);

        AppLoginFilter authFilter = context.getBean(AppLoginFilter.class);

        authFilter.setAuthenticationManager(http.getSharedObject(AuthenticationManager.class));
        authFilter.setAuthenticationSuccessHandler(new HttpMessageConverterAuthenticationSuccessHandler());
        authFilter.setAuthenticationFailureHandler(new SimpleUrlAuthenticationFailureHandler("/login"));
/*        if (authenticationDetailsSource != null) {
            authFilter.setAuthenticationDetailsSource(this.authenticationDetailsSource);
        }*/
        SessionAuthenticationStrategy sessionAuthenticationStrategy = http
                .getSharedObject(SessionAuthenticationStrategy.class);
        if (sessionAuthenticationStrategy != null) {
            authFilter.setSessionAuthenticationStrategy(sessionAuthenticationStrategy);
        }
        RememberMeServices rememberMeServices = http.getSharedObject(RememberMeServices.class);
        if (rememberMeServices != null) {
            authFilter.setRememberMeServices(rememberMeServices);
        }
//        SecurityContextConfigurer securityContextConfigurer = http.getConfigurer(SecurityContextConfigurer.class);
//        if (securityContextConfigurer != null && securityContextConfigurer.isRequireExplicitSave()) {
//            SecurityContextRepository securityContextRepository = securityContextConfigurer
//                    .getSecurityContextRepository();
//            authFilter.setSecurityContextRepository(securityContextRepository);
//        }
//        authFilter.setSecurityContextHolderStrategy(getSecurityContextHolderStrategy());

        http.addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class);
    }

    public static AppSessionDsl appSessionDsl() {
        return new AppSessionDsl();
    }
}
