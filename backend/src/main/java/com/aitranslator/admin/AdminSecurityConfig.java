package com.aitranslator.admin;
import com.aitranslator.config.AppConfig.Settings;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class AdminSecurityConfig {
 @Bean org.springframework.security.crypto.password.PasswordEncoder adminPasswordEncoder(){return new BCryptPasswordEncoder();}
 @Bean UserDetailsService adminUsers(Settings settings){
  String password=settings.get("app.admin.password","");
  if(password.isBlank())return new InMemoryUserDetailsManager();
  if(password.length()<12)throw new IllegalStateException("Admin password must contain at least 12 characters");
  return new InMemoryUserDetailsManager(User.withUsername(settings.get("app.admin.username","admin")).password(new BCryptPasswordEncoder().encode(password)).roles("ADMIN").build());
 }
 @Bean @Order(1) SecurityFilterChain adminSecurity(HttpSecurity http)throws Exception {
  return http.securityMatcher("/admin","/admin/**","/api/admin/**")
   .authorizeHttpRequests(a->a.requestMatchers("/admin","/admin/**","/api/admin/csrf","/api/admin/login").permitAll().anyRequest().hasRole("ADMIN"))
   .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
   .requestCache(c->c.disable())
   .formLogin(f->f.loginPage("/admin/").loginProcessingUrl("/api/admin/login")
    .successHandler((request,response,auth)->{response.setContentType("application/json");response.getWriter().write("{\"authenticated\":true}");})
    .failureHandler((request,response,error)->response.setStatus(401)))
   .logout(l->l.logoutUrl("/api/admin/logout").invalidateHttpSession(true).deleteCookies("JSESSIONID").logoutSuccessHandler((request,response,auth)->response.setStatus(204)))
   .exceptionHandling(e->e.authenticationEntryPoint((request,response,error)->response.setStatus(401)))
   .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'")))
   .build();
 }
}
