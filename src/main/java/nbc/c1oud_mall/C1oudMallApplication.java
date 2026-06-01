package nbc.c1oud_mall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class C1oudMallApplication {

	public static void main(String[] args) {
		SpringApplication.run(C1oudMallApplication.class, args);
	}

}
