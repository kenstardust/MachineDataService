package com.industry;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.industry.MachineDataService.mapper")
@SpringBootApplication
public class MachineDataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MachineDataServiceApplication.class, args);
    }
}
