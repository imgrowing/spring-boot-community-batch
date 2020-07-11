package com.community.batch.jobs;

import com.community.batch.domain.User;
import com.community.batch.domain.enums.UserStatus;
import com.community.batch.domain.repository.UserRepository;
import com.community.batch.jobs.readers.QueueItemReader;
import lombok.AllArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;

@Configuration
@AllArgsConstructor
public class InactiveUserJobConfig {

	@Autowired
	private UserRepository userRepository;

	@Bean
	public Job inactiveUserJob(
			JobBuilderFactory jobBuilderFactory, // JobBuilderFactory를 injection 받음
			Step inactiveJobStep
	) {
		return jobBuilderFactory.get("inactiveUserJob") // jobBuilder 인스턴스를 생성
			.preventRestart()                           // Job의 재실행을 막음
			.start(inactiveJobStep)						// inactiveJobStep을 실행하는 jobBuilder를 생성
			.build();
	}

	@Bean
	public Step inactiveJobStep(StepBuilderFactory stepBuilderFactory) {
		return stepBuilderFactory.get("inactiveUserStep") // StepBuilder를 생성
			.<User, User> chunk(10)             // chunk 단위로 처리(commit)할 item 정보를 지정. Input/Output의 타입을 명시
			.reader(inactiveUserReader())
			.processor(inactiveUserProcessor())
			.writer(inactiveUserWriter())
			.build();
	}

	// Step의 Scope에 따라 새로운 빈을 생성한다(각 Step이 실행될 때 마다 새로 빈을 만든다 -> 지연 생성됨)
	// @StepScope는 proxyMode가 TARGET_CLASS로 되어 있기 때문에 반드시 구현된 반환 타입을 명시해야 한다(ItemReader 로 명시하면 안됨).
	@StepScope
	@Bean
	public QueueItemReader<User> inactiveUserReader() {
		List<User> oldUsers = userRepository.findByUpdatedDateBeforeAndStatus(LocalDateTime.now().minusYears(1), UserStatus.ACTIVE);
		return new QueueItemReader<>(oldUsers); // 배치에서 사용할 data를 조회해서 reader를 생성한다.
	}

	public ItemProcessor<User, User> inactiveUserProcessor() {
		return User::setInactive;
		/*
		return new ItemProcessor<User, User>() {
			@Override
			public User process(User user) throws Exception {
				return user.setInactive();
			}
		};
		*/
	}

	public ItemWriter<User> inactiveUserWriter() {
		return users -> userRepository.saveAll(users); // users는 chunk 단위인 10 개씩 전달된다.
	}
}
