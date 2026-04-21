package com.industry;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@MapperScan("com.industry.MachineDataService.mapper")
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class MachineDataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MachineDataServiceApplication.class, args);
    }
}
