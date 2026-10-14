package org.quark.misc.choresweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChoresWebApplication {
	public static void main(String[] args) {
		SpringApplication.run(ChoresWebApplication.class, args);
	}
}
