package com.community.batch;

import com.community.batch.domain.enums.UserStatus;
import com.community.batch.domain.repository.UserRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest
public class InactiveUserJobTest {

	@Autowired
	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	private UserRepository userRepository;

	@Test
	public void 휴면_회원_전환_테스트() throws Exception {
		// JobLauncherTestUtils.launchJob()은  Job을 실행시키고, 실행 결과를 담고 있는 JobExecution 이 반환한다.
		JobExecution jobExecution = jobLauncherTestUtils.launchJob();

		// Job의 실행 여부는 COMPLETE (성공)이어야 한다.
		assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());
		// 회원 중 1년 이상 업데이트 되지 않았고 ACTIVE 상태인 경우는 없어야 한다.
		int userCount = userRepository.findByUpdatedDateBeforeAndStatus(LocalDateTime.now().minusYears(1), UserStatus.ACTIVE).size();
		assertEquals(0, userCount);
	}
}
