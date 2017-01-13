package com.bb.thrift.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by bob on 17/1/11.
 */
@SpringBootApplication
@RestController
@EnableDiscoveryClient
public class ThriftApp {

    public static void main(String[] args) {
        SpringApplication.run(ThriftApp.class, args);
    }

    @RequestMapping("/")
    public String home() {
        return "Hello World";
    }
}