package com.kisf.sqlquery;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan("com.kisf.sqlquery.core.entity")
@EnableJpaRepositories("com.kisf.sqlquery.core.repo")
@ComponentScan(basePackages = {
    "com.kisf.sqlquery.api.controller",
    "com.kisf.sqlquery.api.exception"
})
public class TestApplication {
}
