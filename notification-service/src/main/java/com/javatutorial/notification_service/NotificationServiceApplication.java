package com.javatutorial.notification_service;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class NotificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationServiceApplication.class, args);
	}

	// Same converter order-service registers (RabbitConfig.java there) - without it the
	// listener can only receive byte[]/String, not a typed OrderCreatedEvent parameter.
	// Kept here instead of a separate config class: this whole app is meant to stay small.
	@Bean
	MessageConverter jsonMessageConverter() {
		return new JacksonJsonMessageConverter();
	}

}
