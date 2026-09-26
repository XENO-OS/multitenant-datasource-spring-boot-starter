package com.xenotech.multitenant.datasource;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.xenotech.multitenant.datasource")
@Import(TestDataSourceConfig.class)
public class TestApplication {
}
