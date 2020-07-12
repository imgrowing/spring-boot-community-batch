package com.community.batch.jobs;

import com.community.batch.domain.User;
import com.community.batch.domain.enums.UserStatus;
import com.community.batch.domain.repository.UserRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class InactiveUserJobConfig {

	private final static int CHUNK_SIZE = 5;

	private final EntityManagerFactory entityManagerFactory;
	private final UserRepository userRepository;

	public InactiveUserJobConfig(EntityManagerFactory entityManagerFactory, UserRepository userRepository) {
		this.entityManagerFactory = entityManagerFactory;
		this.userRepository = userRepository;
	}

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
	public Step inactiveJobStep(StepBuilderFactory stepBuilderFactory,
			JpaPagingItemReader<User> inactiveUserJpaReader) {
		return stepBuilderFactory.get("inactiveUserStep") // StepBuilder를 생성
			.<User, User> chunk(CHUNK_SIZE)             // chunk 단위로 처리(commit)할 item 정보를 지정. Input/Output의 타입을 명시
			.reader(inactiveUserJpaReader)
			.processor(inactiveUserProcessor())
			.writer(inactiveUserWriter())
			.build();
	}

	// close() 호출시 에러가 발생하는데, close() 호출을를 막아준다.
	// destroyMethod 값을 지정하지 않으면 기본적으로 자동 추정에 따라 close, shutdown 메소드를 호출한다.
	@Bean(destroyMethod = "")
	@StepScope
	public JpaPagingItemReader<User> inactiveUserJpaReader() {
		// JpaPagingItemReader는 read() 호출시 내부에서 페이지 단위로 진행하면서 item을 DB에서 읽어오게 된다.
		// 하지만 이미 처리된(process & write) Page(chunk)는 조회 SQL의 조건에서 제외되는 경우가 많으므로
		// 페이지 번호를 항상 0으로 가져오도록 하면 항상 정상적으로 동작하는 것이 보장된다.
		JpaPagingItemReader<User> jpaPagingItemReader = new JpaPagingItemReader<User>() {
			@Override
			public int getPage() {
				return 0;
			}
		};
		String jpqlQuery = "select u from User as u where u.updatedDate < :updatedDate and u.status= :status";
		jpaPagingItemReader.setQueryString(jpqlQuery); // JPQL 로 지정해야 한다.

		Map<String, Object> map = new HashMap<>();
		LocalDateTime now = LocalDateTime.now();
		map.put("updatedDate", now.minusYears(1));
		map.put("status", UserStatus.ACTIVE);

		jpaPagingItemReader.setParameterValues(map);
		jpaPagingItemReader.setEntityManagerFactory(entityManagerFactory);
		jpaPagingItemReader.setPageSize(CHUNK_SIZE);
		return jpaPagingItemReader;
	}

	// Step의 Scope에 따라 새로운 빈을 생성한다(각 Step이 실행될 때 마다 새로 빈을 만든다 -> 지연 생성됨)
	// @StepScope는 proxyMode가 TARGET_CLASS로 되어 있기 때문에 반드시 구현된 반환 타입을 명시해야 한다(ItemReader 로 명시하면 안됨).
	@StepScope
	@Bean
	public ListItemReader<User> inactiveUserReader() {
		List<User> oldUsers = userRepository.findByUpdatedDateBeforeAndStatus(LocalDateTime.now().minusYears(1), UserStatus.ACTIVE);
		return new ListItemReader<>(oldUsers); // 배치에서 사용할 data를 조회해서 reader를 생성한다.
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
