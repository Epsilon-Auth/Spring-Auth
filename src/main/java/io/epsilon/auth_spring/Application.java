package io.epsilon.auth_spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration; // Add this line

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class AuthSpringApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthSpringApplication.class, args);
	}

}
