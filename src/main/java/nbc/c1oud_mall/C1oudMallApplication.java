package nbc.c1oud_mall;

import nbc.c1oud_mall.point.domain.PointPolicy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PointPolicy.class)
public class C1oudMallApplication {

	public static void main(String[] args) {
		SpringApplication.run(C1oudMallApplication.class, args);
	}

}
