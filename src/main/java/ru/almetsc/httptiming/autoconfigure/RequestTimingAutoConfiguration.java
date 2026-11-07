package ru.almetsc.httptiming.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import ru.almetsc.httptiming.RequestTimingFilter;

@AutoConfiguration
@ConditionalOnWebApplication(
    type = ConditionalOnWebApplication.Type.SERVLET
)
@ConditionalOnProperty(
    prefix = "request-timing",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class RequestTimingAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(RequestTimingFilter.class)
  public RequestTimingFilter requestTimingFilter() {
    return new RequestTimingFilter();
  }

  @Bean
  @ConditionalOnMissingBean(RequestTimingStarter.class)
  public RequestTimingStarter requestTimingStarter(RequestTimingFilter filter) {
    return new RequestTimingStarter(filter);
  }
}