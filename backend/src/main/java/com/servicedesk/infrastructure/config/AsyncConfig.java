package com.servicedesk.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Lets slow outbound work (email) run off the request thread. */
@Configuration
@EnableAsync
public class AsyncConfig {}
