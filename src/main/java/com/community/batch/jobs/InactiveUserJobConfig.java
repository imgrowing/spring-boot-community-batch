package com.community.batch.jobs;

import com.community.batch.domain.User;
import com.community.batch.domain.enums.UserStatus;
import com.community.batch.domain.repository.UserRepository;
import com.community.batch.jobs.listener.InactiveChunkListener;
import com.community.batch.jobs.listener.InactiveJobListener;
import com.community.batch.jobs.listener.InactiveProcessListener;
import com.community.batch.jobs.listener.InactiveStepListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import javax.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//@Configuration // InactiveUserPartitionJobConfig의 테스트를 위해 잠시 주석 처리함
// @ConditionalOnProperty를 사용하면 batch 실행시에는 원하는 Job만 실행할 수 있게 되지만,
// Batch IntegrationTest 에는 적합하지 않다(job의 bean이 생성되지 않으므로)
//@ConditionalOnProperty(name = "spring.batch.job.names", havingValue = "inactiveUserJob")
@Slf4j
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
			Step inactiveJobStep,
			InactiveJobListener jobListener
	) {
		return jobBuilderFactory.get("inactiveUserJob") // jobBuilder 인스턴스를 생성
			.preventRestart()                           // Job의 재실행을 막음
			.start(inactiveJobStep)						// inactiveJobStep을 실행하는 jobBuilder를 생성
			.listener(jobListener)
			.build();
	}

	@Bean
	public Step inactiveJobStep(StepBuilderFactory stepBuilderFactory,
			ListItemReader<User> inactiveUserReader,
			InactiveStepListener stepListener,
			InactiveChunkListener chunkListener,
			InactiveProcessListener processListener,
			TaskExecutor taskExecutor
	) {
		return stepBuilderFactory.get("inactiveUserStep") // StepBuilder를 생성
			.<User, User> chunk(CHUNK_SIZE)             // chunk 단위로 처리(commit)할 item 정보를 지정. Input/Output의 타입을 명시
			.reader(inactiveUserReader)
			.processor(inactiveUserProcessor())
			.writer(inactiveUserWriter())
			.listener(stepListener)
			.listener(chunkListener)
			.listener(processListener)
			.taskExecutor(taskExecutor)
			.throttleLimit(2)
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

			// taskExecutor 와 throttleLimit의 조합으로 사용할 경우 read()를 synchronized 처리해 주어야 함
			// 그렇지 않으면 여러 스레드가 read에 동시에 진입하여 같은 데이터를 읽어오는 상황이 발생함
			@Override
			public synchronized User read() throws Exception, UnexpectedInputException, ParseException {
				return super.read();
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
	public ListItemReader<User> inactiveUserReader(
		@Value("#{jobParameters[nowDate]}") Date nowDate
	) {
		log.info("=======================> Date nowDate: {}", nowDate);
		LocalDateTime now = LocalDateTime.ofInstant(nowDate.toInstant(), ZoneId.systemDefault());
		log.info("=======================> jobParameters[nowDate]: {}", now);
		List<User> oldUsers = userRepository.findByUpdatedDateBeforeAndStatus(now.minusYears(1), UserStatus.ACTIVE);
		ListItemReader<User> userListItemReader = new ListItemReader<User>(oldUsers){
			@Override
			public synchronized User read() {
				User user = super.read();
				if (user != null) {
					log.info("read: {}", user.getIdx());
				}
				return user;
			}
		};
		return userListItemReader; // 배치에서 사용할 data를 조회해서 reader를 생성한다.
	}

	public ItemProcessor<User, User> inactiveUserProcessor() {
		//return User::setInactive;
		///*
		return new ItemProcessor<User, User>() {
			@Override
			public User process(User user) throws Exception {
				log.info("process: {}", user.getIdx());
				return user.setInactive();
			}
		};
		//*/
	}

	public ItemWriter<User> inactiveUserWriter() {
		return users -> {
			log.info("write size: {}", users.size());
			userRepository.saveAll(users); // users는 chunk 단위인 10 개씩 전달된다.
		};
	}

	// JpaItemWriter를 사용하면 지정한 타입에 대해 chunk 단위의 writer를 처리해준다.
//	private JpaItemWriter<User> inactiveUserWriter() {
//		JpaItemWriter<User> jpaItemWriter = new JpaItemWriter<>();
//		jpaItemWriter.setEntityManagerFactory(entityManagerFactory);
//		return jpaItemWriter;
//	}

	@Bean
	public TaskExecutor taskExecutor() {
		return new SimpleAsyncTaskExecutor("batch-task-executor");
	}
}
