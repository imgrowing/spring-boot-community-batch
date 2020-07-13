package com.community.batch.jobs;

import com.community.batch.domain.User;
import com.community.batch.domain.enums.Grade;
import com.community.batch.domain.enums.UserStatus;
import com.community.batch.domain.repository.UserRepository;
import com.community.batch.jobs.listener.InactiveJobListener;
import com.community.batch.jobs.partitioner.InactiveUserPartitioner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import javax.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.List;

@Configuration
@Slf4j
public class InactiveUserPartitionJobConfig {

	private final static int CHUNK_SIZE = 5;

	private final EntityManagerFactory entityManagerFactory;
	private final UserRepository userRepository;

	public InactiveUserPartitionJobConfig(EntityManagerFactory entityManagerFactory, UserRepository userRepository) {
		this.entityManagerFactory = entityManagerFactory;
		this.userRepository = userRepository;
	}

	@Bean
	public Job inactiveUserPartitionJob(
			JobBuilderFactory jobBuilderFactory,
			InactiveJobListener jobListener,
			Step partitionerStep
	) {
		return jobBuilderFactory.get("inactiveUserPartitionJob")
			.preventRestart()
			.listener(jobListener)
			.start(partitionerStep)
			.build();
	}

	@Bean
	@JobScope
	public Step partitionerStep(StepBuilderFactory stepBuilderFactory,
		Step inactiveJobStep,
		TaskExecutor taskExecutor
	) {
		return stepBuilderFactory.get("partitionerStep")
			.partitioner("partitionerStep", new InactiveUserPartitioner())
			.gridSize(5)
			.step(inactiveJobStep)
			.taskExecutor(taskExecutor)
			.build();
	}

	@Bean
	public Step inactiveJobStep(StepBuilderFactory stepBuilderFactory,
			ListItemReader<User> inactiveUserReader
	) {
		return stepBuilderFactory.get("inactiveUserStep")
			.<User, User> chunk(CHUNK_SIZE)
			.reader(inactiveUserReader)
			.processor(inactiveUserProcessor())
			.writer(inactiveUserWriter())
			.build();
	}

	@StepScope
	@Bean
	public ListItemReader<User> inactiveUserReader(
		@Value("#{stepExecutionContext[grade]}") String grade,
		UserRepository userRepository
	) {
		log.warn("create reader bean (GRADE: {})", grade);
		List<User> oldUsers = userRepository.findByUpdatedDateBeforeAndStatusAndGrade(
			LocalDateTime.now().minusYears(1), UserStatus.ACTIVE, Grade.valueOf(grade)
		);
		return new ListItemReader<User>(oldUsers) {
			@Override
			public User read() {
				User user = super.read();
				if (user != null) {
					delay100ms();
					log.info("read: {} - grade.{}", user.getIdx(), user.getGrade());
				}
				return user;
			}
		};
	}

	private void delay100ms() {
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public ItemProcessor<User, User> inactiveUserProcessor() {
		return user -> {
			delay100ms();
			log.info("process: {} - grade.{}", user.getIdx(), user.getGrade());
			return user.setInactive();
		};
	}

	public ItemWriter<User> inactiveUserWriter() {
		return users -> {
			delay100ms();
			log.info("write size: {}, grade.{}", users.size(), users.get(0).getGrade());
			userRepository.saveAll(users);
		};
	}

	@Bean
	public TaskExecutor taskExecutor() {
		return new SimpleAsyncTaskExecutor("batch-task-executor");
	}
}
