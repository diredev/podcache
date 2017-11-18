package at.dire.podcache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * SpringBoot application.
 * 
 * @author diredev
 */
@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
public class Application {
	/**
	 * Main method.
	 * 
	 * @param args command line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
