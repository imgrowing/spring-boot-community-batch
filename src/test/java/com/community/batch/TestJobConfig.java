package com.community.batch;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/* @EnableBatchProcessing
   - 스프링 부트 배치 스타터에 미리 정의된 기본 배치 configuration을 제공한다/
   - JobBuilderFactory, JobBuilder, StepBuilder, JobRepository, JobLauncher 등의 bean이 생성된다.
 */
@EnableBatchProcessing
@Configuration
public class TestJobConfig {
	@Bean
	public JobLauncherTestUtils jobLauncherTestUtils() {
		// Job 실행에 필요한 JobLauncher를 내부 필드로 가지는 JobLauncherTestUtils를 빈으로 등록한다.
		return new JobLauncherTestUtils();
	}
}
