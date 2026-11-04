package com.connectone;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class ConnectOneApplication {

	public static void main(String[] args) {
		// Timestamps are stored as UTC wall-clock values (TIMESTAMP without time zone).
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(ConnectOneApplication.class, args);
	}

}
