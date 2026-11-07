package ru.almetsc.httptiming.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import ru.almetsc.httptiming.RequestTimingFilter;

public class RequestTimingStarter implements DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(RequestTimingStarter.class);

  private final RequestTimingFilter filter;

  public RequestTimingStarter(RequestTimingFilter filter) {
    this.filter = filter;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void startup() {
    log.info("Request timing is ready. Filter: {}", filter.getClass().getSimpleName());
  }

  @Override
  public void destroy() {
    log.info("Request timing application context is shutting down");
  }
}